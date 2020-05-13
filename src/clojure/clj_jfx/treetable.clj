; Copyright (c) Gunnar Völkel. All rights reserved.
; The use and distribution terms for this software are covered by the
; Eclipse Public License 2.0 (http://www.eclipse.org/legal/epl-v20.html)
; which can be found in the file LICENSE at the root of this distribution.
; By using this software in any fashion, you are agreeing to be bound by
; the terms of this license.
; You must not remove this notice, or any other, from this software.

(ns clj-jfx.treetable
  (:require
    [clj-jfx.core :as jfx]
    [clj-jfx.properties :as props]
    [clj-jfx.util :as u])
  (:import
    (clj_jfx.treetable CustomTreeTableCell)
    (javafx.scene.control CheckBox TreeTableView TreeTableColumn$CellDataFeatures TreeTableColumn TreeItem Label)
    (java.util List)
    (javafx.collections ObservableList)))



(declare tree-item)


(defn update-tree-item-children
  [dialog-state, item-path, ^ObservableList observable-list, old-children, new-children]
  (let [new-child-count (count new-children),
        old-child-count (count old-children),
        delta (- new-child-count old-child-count)]
    (when-not (zero? delta)
      (if (pos? delta)
        ; add rows
        (u/iteration-indexed delta,
          (fn [i, ^List observable-list]
            (doto observable-list
              (.add (tree-item dialog-state, (conj item-path :children (+ old-child-count i))))))
          observable-list)
        ; remove rows with a previous data-position larger than or equal to new-row-count
        (.remove observable-list new-child-count, old-child-count)))))


(defn tree-item
  [dialog-state, path]
  (let [properties-prop (props/entry-property dialog-state, (conj path :properties))
        children-prop (props/entry-property dialog-state, (conj path :children))
        tree-item (TreeItem.)]
    (jfx/bind-list-updating (.getChildren tree-item), children-prop
      (partial update-tree-item-children dialog-state, path))
    ; bind tree-item value
    (jfx/bind-bidirectional (props/property tree-item, :value) properties-prop)
    tree-item))


(defn setup-columns
  [^TreeTableView treetable, column-spec-list]
  (let [columns (.getColumns treetable)
        delta (- (count column-spec-list) (count columns))]
    ; add missing columns
    (when (pos? delta)
      (u/iteration delta
        (fn [^List columns]
          (doto columns
            (.add (TreeTableColumn.))))
        columns))
    ; configure columns
    (u/for-each-indexed!
      (fn [column-index, {:keys [name, attribute, cell-factory, comparator, sortable?]}]
        (let [^TreeTableColumn column (nth columns column-index)]
          (when name
            (.setText column name))
          (when attribute
            (.setCellValueFactory column
              (jfx/callback [cdfs]
                (jfx/map-attribute-property
                  attribute
                  (-> ^TreeTableColumn$CellDataFeatures cdfs .getValue (props/property :value))))))
          (doto column
            (.setSortable (boolean sortable?)))
          (when cell-factory
            (.setCellFactory column
              (jfx/->callback cell-factory)))
          ; set comparator
          (props/set-property column :comparator (or comparator u/universal-compare))))
      column-spec-list)))


(declare children-update)


(defn some-deselected-or-indeterminate?
  [old-children, new-children]
  (let [n (count new-children)
        safe-nth (fn [children, index]
                   (when (< index n)
                     (nth children index)))]
    (some
      (fn [index]
        (let [{{old-checked? :checked?} :properties} (safe-nth old-children index),
              {{new-checked? :checked?} :properties} (safe-nth new-children index)]
          #_(println old-checked? new-checked?)
          (and
            (not= old-checked? new-checked?)
            (or (false? new-checked?) (= new-checked? :indeterminate)))))
      (range n))))


(defn all-checked?-equals
  [value, children]
  (every?
    (fn [{{:keys [checked?]} :properties}]
      (= checked? value))
    children))


(defn update-tree-item
  [{old-children :children
    {old-checked? :checked?} :properties
    :as old-item},
   {new-children :children
    {new-checked? :checked?} :properties
    :as new-item}]
  (if (seq new-children)
    (let [checked?-changed? (and (not= old-checked? new-checked?) (not= new-checked? :indeterminate))
          updated-children (children-update checked?-changed?, new-checked?, old-children, new-children)]
      (cond-> new-item

        ; switch node from checked to :indeterminate or unchecked?
        (and
          (true? old-checked?)
          (some-deselected-or-indeterminate? old-children, updated-children))
        (assoc-in [:properties, :checked?] (if (all-checked?-equals false updated-children) false :indeterminate))

        ; switch node from :indeterminate to checked?
        (and
          (= old-checked? :indeterminate)
          (all-checked?-equals true updated-children))
        (assoc-in [:properties, :checked?] true)

        true
        (assoc :children updated-children)))
    ; return item unmodified
    new-item))


(defn children-update
  [modify-checked?, checked?, old-children, new-children]
  (let [n (count new-children)
        safe-nth (fn [children, index]
                   (when (< index n)
                     (nth children index)))]
    ; do not care about deleted items
    (mapv
      (fn [index]
        (let [old-child (safe-nth old-children index)
              new-child (cond-> (safe-nth new-children index)
                          modify-checked?
                          (assoc-in [:properties, :checked?] checked?))]
          (cond->> new-child
            (not= old-child new-child)
            (update-tree-item old-child))))
      (range n))))


(defn on-tree-changed
  [tree-prop, old-tree, new-tree]
  ; indeterminate könnte machbar sein, indem man eine virtuelle property baut, welche auf checked? und indeterminate? dispatched
  #_(inspect {:old-tree old-tree, :new-tree new-tree})
  (let [old-tree-children (:children old-tree)
        new-tree-children (:children new-tree)
        n (count new-tree-children)]
    (props/set tree-prop
      (assoc new-tree
        :children
        (children-update false, nil, old-tree-children, new-tree-children)))))


(defn setup-treetable
  [^TreeTableView treetable, dialog-state, column-spec]
  (let [root-node (doto (tree-item dialog-state, [:tree])
                    (props/set-property :expanded true))
        tree-prop (props/entry-property dialog-state, [:tree])]
    (setup-columns treetable, column-spec)
    (jfx/change-listener! tree-prop
      (fn [_, old-tree, new-tree]
        (when-not (= old-tree new-tree)
          (jfx/run-later
            (on-tree-changed tree-prop, old-tree, new-tree)))))
    (doto treetable
      (props/set-property :root root-node))))


(defn leaves
  [{:keys [children] :as node}]
  (if (seq children)
    (mapcat leaves children)
    [(:properties node)]))


(defn selected-leaves-property
  [dialog-state]
  (props/fn-property
    (fn [tree]
      (filterv #(true? (:checked? %)) (mapcat leaves (:children tree))))
    (props/entry-property dialog-state, [:tree])))


(defn checkbox-cell
  []
  (let [cb (doto (CheckBox.)
             (.setFocusTraversable false))
        proxy-prop (jfx/object-property false)
        selected-prop (.selectedProperty cb)
        indeterminate-prop (.indeterminateProperty cb)]
    (jfx/listen-to
      (fn [state]
        (if (= state :indeterminate)
          (do
            (jfx/value! selected-prop true)
            (jfx/value! indeterminate-prop true))
          (do
            (jfx/value! selected-prop state)
            (jfx/value! indeterminate-prop false))))
      proxy-prop)
    (jfx/listen-to
      (fn [selected?, indeterminate?]
        (jfx/value! proxy-prop
          (if indeterminate? :indeterminate selected?)))
      selected-prop
      indeterminate-prop)
    (CustomTreeTableCell. cb, proxy-prop)))


(defn number-cell
  [fmt]
  (let [label (Label.)]
    (doto (CustomTreeTableCell. label, (.textProperty label), (fn [x] (some->> x (format fmt))))
      (jfx/alignment! :center-right))))
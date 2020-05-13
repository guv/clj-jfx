; Copyright (c) Gunnar Völkel. All rights reserved.
; The use and distribution terms for this software are covered by the
; Eclipse Public License 2.0 (http://www.eclipse.org/legal/epl-v20.html)
; which can be found in the file LICENSE at the root of this distribution.
; By using this software in any fashion, you are agreeing to be bound by
; the terms of this license.
; You must not remove this notice, or any other, from this software.

(ns clj-jfx.table
  (:require [clj-jfx.core :as jfx]
            [clj-jfx.util :as u]
            [clj-jfx.properties :as props]
            [clj-jfx.user-data :as ud]
            [clj-jfx.colorchooser :as cc]
            [clojure.string :as str]
            [clojure.pprint :as pp]
            [clojure.stacktrace :as st])
  (:import (javafx.scene.control TableView TableColumn TableColumn$CellDataFeatures Label ComboBox TableCell)
           (java.util List Collection)
           (javafx.collections ObservableList ListChangeListener$Change)
           (java.lang.ref WeakReference)
           (javafx.collections.transformation FilteredList SortedList)
           (clj_jfx.table CustomTableCell MultiFunctionalTableCell TableCellWrapper)
           (javafx.scene.text Text FontSmoothingType)
           (javafx.scene.layout VBox)
           (javafx.beans.property Property SimpleObjectProperty)
           (javafx.scene.control.skin TableViewSkinBase)
           (javafx.geometry Insets)
           (javafx.beans InvalidationListener)
           (javafx.beans.value ObservableValue)))



(defn column-index
  ^long [^TableColumn table-column]
  (some-> table-column .getTableView .getColumns (.indexOf table-column)))


(defn column-index-property
  [^TableCell cell]
  (let [columns-prop (props/property-via-fn
                       #(when %
                          (props/observable-list->property (.getColumns ^TableView %)))
                       (props/property cell, :table-view))]
    (props/fn-property
      (fn [^ObservableList column-list, column]
        (when (and column-list column)
          (.indexOf column-list column)))
      columns-prop
      (props/property cell, :table-column))))


(defn cell-position-property
  [cell]
  (props/fn-property
    (fn [row-index, column-index]
      (when (and row-index column-index)
        {:row row-index
         :column column-index}))
    (props/property cell, :index)
    (column-index-property cell)))


(defn cell-observable-value-property
  [cell]
  (props/property-via-fn
    (fn [^TableColumn column, column-index, row-index]
      (when (and column row-index)
        (.getCellObservableValue column, (int row-index))))
    (props/property cell, :table-column)
    (column-index-property cell)
    (props/property cell, :index)))


(defn update-table-model
  [create-row, ^ObservableList observable-row-list, old-rows, new-rows]
  (let [new-row-count (count new-rows),
        old-row-count (count old-rows),
        delta (- new-row-count old-row-count)]
    (cond
      ; add rows?
      (pos? delta)
      (u/iteration-indexed delta,
        (fn [i, ^List observable-list]
          (doto observable-list
            (.add (create-row (+ old-row-count i)))))
        observable-row-list)
      ; remove rows?
      (neg? delta)
      ; remove rows with a previous data-position larger than or equal to new-row-count
      (.remove observable-row-list new-row-count, old-row-count))))


(defn table-model
  ^ObservableList [data-prop]
  (let [rows-prop (props/entry-property data-prop, [:rows])
        create-row (fn [row-index] (props/entry-property rows-prop, [row-index]))
        observable-list (jfx/observable-array-list)]
    (props/list-property-binding observable-list, rows-prop,
      :update-list-fn (partial update-table-model create-row)
      :update-property-fn (fn [& args] #_uni-directional)
      :initialize :list)
    observable-list))


(defn setup-filtering
  [^ObservableList table-model, filter-text-property]
  (let [filtered-model (FilteredList. table-model, (jfx/predicate (constantly true)))]
    (props/bind (props/property filtered-model, :predicate)
      (props/fn-property
        (fn [filter-text]
          (let [filter-text-vec (->> (str/split filter-text #"\s")
                                  (mapv (comp str/lower-case str/trim)))]
            (reduce
              (fn [combined-pred, filter-text]
                (jfx/and-predicate combined-pred, (jfx/filter-predicate filter-text)))
              (jfx/predicate (constantly true))
              filter-text-vec)))
        filter-text-property))
    filtered-model))


(defn setup-sorting
  [^ObservableList table-model, ^TableView table, data-prop]
  (let [sorted-model (SortedList. table-model)]
    (props/bind (props/property sorted-model :comparator) (props/property table :comparator))
    (props/list-property-binding sorted-model, (props/entry-property data-prop, [:sorted-rows])
      :update-list-fn
      (fn [& args] #_DO-NOTHING)
      :update-property-fn
      (fn [_, _, new-value]
        (mapv props/get new-value))
      :initialize :property)
    sorted-model))


(defn column-attribute
  [^TableColumn column]
  (:attribute (ud/user-data column)))


(defn column-id
  [^TableColumn column]
  (:id (ud/user-data column)))


(defn set-width-prop
  [control, property-kw, value, default]
  (when-let [value (or value default)]
    (props/set-property control, property-kw, value))
  control)


(defn into!
  [transient-coll, coll]
  (reduce conj! transient-coll coll))


(defn modified-column-attributes
  [old-column-spec, new-column-spec]
  (let [ks (into #{} (concat (keys old-column-spec) (keys new-column-spec)))]
    (not-empty
      (persistent!
        (reduce
          (fn [diff-map, k]
            (let [v-old (get old-column-spec k)
                  v-new (get new-column-spec k)]
              (cond-> diff-map
                (not= v-old v-new)
                (conj! k))))
          (transient #{})
          ks)))))


(defn determine-column-modifications
  [columns, old-column-spec-list, new-column-spec-list]
  (let [n-old (count old-column-spec-list)
        n-new (count new-column-spec-list)
        n (min n-old n-new)
        columns-added? (< n-old n-new)]
    (loop [i 0, modified-columns (transient [])]
      (if (< i n)
        (let [old-column-spec (nth old-column-spec-list i)
              new-column-spec (nth new-column-spec-list i)
              modified-attributes (modified-column-attributes old-column-spec, new-column-spec)]
          (recur
            (unchecked-inc i)
            (cond-> modified-columns
              modified-attributes
              (conj! {:index i, :column (nth columns i), :modified-attributes modified-attributes, :column-spec new-column-spec}))))
        (persistent!
          (cond-> modified-columns
            columns-added?
            (into!
              (mapv
                (fn [index, column, column-spec]
                  {:index (+ n-old index)
                   :column column
                   :modified-attributes (set (keys column-spec))
                   :column-spec column-spec})
                (range)
                (subvec columns n-old)
                (subvec new-column-spec-list n-old)))))))))


(defn some-modified?
  [modified-keys-set, kw, & kws]
  (some
    #(contains? modified-keys-set %)
    (list* kw, kws)))


(defn update-column-caption
  [{:keys [^TableColumn column, modified-attributes, column-spec]}]
  (when (some-modified? modified-attributes, :name, :click-handler, :vertical?, :font)
    (let [{:keys [name, click-handler, vertical?, font]} column-spec]
      (doto column
        (.setText nil)
        (.setSortNode (doto (VBox.)
                        (jfx/min-size! 0, 0)
                        (jfx/preferred-size! 0, 0)
                        (jfx/max-size! 0, 0)))
        (.setGraphic (doto (VBox.)
                       (jfx/use-computed-size-and-grow!)
                       (-> (props/property :pref-width) (props/bind (props/property column, :width)))
                       (jfx/alignment! :center)
                       (cond-> click-handler (jfx/handle-event! :mouse-clicked click-handler))
                       (jfx/add-child
                         (doto (Text. name)
                           (.setRotate (if vertical? -90 0))
                           (.setFont (jfx/font font))
                           (.setFontSmoothingType FontSmoothingType/LCD)))))))))


(defn update-column-cell-value-factory
  [{:keys [^TableColumn column, modified-attributes, column-spec]}]
  (when (some-modified? modified-attributes :attribute, :value->display, :display->value)
    (let [{:keys [attribute, value->display, display->value]} column-spec]
      (doto column
        (.setCellValueFactory
          ; either attribute or both conversion functions must be given for a cell value factory
          (when (or attribute (and value->display display->value))
            (jfx/callback [cdfs]
              ; create cell property if not cached already
              (when-let [row-prop (.getValue ^TableColumn$CellDataFeatures cdfs)]
                (let [prop (if attribute
                             (props/entry-property row-prop, [attribute])
                             row-prop)]
                  (if (and value->display display->value)
                    (doto (SimpleObjectProperty.)
                      (props/bind-bidi display->value, prop, value->display))
                    prop))))))))))


(defn update-column-width
  [{:keys [^TableColumn column, modified-attributes, column-spec]}]
  (let [{:keys [min-width, width, max-width]} column-spec]
    (when (some-modified? modified-attributes, :min-width, :width)
      (set-width-prop column, :min-width, min-width, width))
    (when (some-modified? modified-attributes :width)
      (set-width-prop column, :pref-width, width, nil))
    (when (some-modified? modified-attributes, :max-width, :width)
      (set-width-prop column, :max-width, max-width, width))))


(defn update-column-cell-factory
  [{:keys [^TableColumn column, modified-attributes, column-spec]}]
  (when (some-modified? modified-attributes, :cell-factory)
    (let [{:keys [cell-factory]} column-spec]
      (.setCellFactory column
        (if cell-factory
          (jfx/->callback cell-factory)
          TableColumn/DEFAULT_CELL_FACTORY)))))


(defn update-column-user-data
  [{:keys [^TableColumn column, modified-attributes, column-spec]}]
  (when (some-modified? modified-attributes, :attribute, :id)
    (let [{:keys [attribute, id]} column-spec]
      (ud/update-user-data! column #(assoc %, :attribute attribute, :id id)))))


(defn update-properties
  [property-list, {:keys [column, modified-attributes, column-spec]}]
  (u/for-each!
    (fn [{:keys [attribute, property, cast]}]
      (when (contains? modified-attributes attribute)
        (props/set-property column
          property
          (cond-> (get column-spec attribute)
            cast
            cast))))
    property-list))


(defn apply-column-update
  [fn-list, column-modification-info]
  (u/for-each!
    (fn [f]
      (f column-modification-info))
    fn-list))


(defn update-columns
  [^ObservableList observable-list, old-column-list, new-column-list]
  (let [columns (vec observable-list)
        new-column-count (count new-column-list)
        old-column-count (count old-column-list)
        delta (- new-column-count old-column-count)
        additional-columns (when (pos? delta)
                             (vec (repeatedly delta #(TableColumn.))))
        columns (cond-> columns
                  (neg? delta) (subvec 0, new-column-count)
                  (pos? delta) (into additional-columns))
        column-modifications (determine-column-modifications columns, old-column-list, new-column-list)]
    ; apply modifications to columns (side effects)
    (u/for-each!
      (partial apply-column-update
        [update-column-caption
         update-column-cell-value-factory
         update-column-width
         update-column-cell-factory
         update-column-user-data
         (partial update-properties
           [{:attribute :editable?, :property :editable, :cast boolean}
            {:attribute :sortable?, :property :sortable, :cast boolean}
            {:attribute :reorderable?, :property :reorderable, :cast boolean}
            {:attribute :comparator, :property :comparator, :cast #(or % u/universal-compare)}])])
      column-modifications)
    ; update column instances in observable list
    (cond
      (seq additional-columns)
      (.addAll observable-list ^Collection additional-columns)

      (neg? delta)
      (.remove observable-list new-column-count, old-column-count))
    ; we need to set modified columns in the list, to ensure updates within the TableView :(
    (when (seq column-modifications)
      (u/for-each!
        (fn [{:keys [index, column, modified-attributes]}]
          ; trigger update only when :id or :attribute changed to avoid losing focus on editing cell values
          (when (some modified-attributes [:id, :attribute])
            (.set observable-list index, column)))
        column-modifications))))


(defn update-column-data
  [old-column-data, ^ListChangeListener$Change change, new-columns]
  (let [old-attributes (mapv :attribute old-column-data)
        new-attributes (mapv column-attribute new-columns)]
    (if (= old-attributes new-attributes)
      old-column-data
      (let [order (zipmap new-attributes (range))]
        (vec (sort-by (comp order :attribute) old-column-data))))))


(defn setup-column-binding
  [^TableView table, data-prop]
  (props/list-property-binding (.getColumns table), (props/entry-property data-prop, [:columns])
    :update-list-fn update-columns
    :update-property-fn update-column-data
    :initialize :list)
  (-> table
    .getColumns
    (.addListener
      (reify InvalidationListener
        (invalidated [this observable]
          (.refresh table))))))


(defn setup-table
  [^TableView table, column-spec, {:keys [filter-text-property, sort?, placeholder]}]
  (let [data-ref (atom {:rows []
                        :sorted-rows []
                        :columns []})
        data-prop (props/data-property data-ref)
        model (cond-> (table-model data-prop)
                filter-text-property (setup-filtering filter-text-property)
                sort? (setup-sorting table, data-prop))]
    (setup-column-binding table, data-prop)
    (swap! data-ref assoc :columns (vec column-spec))
    (doto table
      (ud/user-data! data-ref)
      (.setItems model)
      (cond-> placeholder (.setPlaceholder (Label. placeholder))))))


(defn data-property
  [table]
  (if-let [user-data (ud/user-data table)]
    (do
      (assert (instance? clojure.lang.Atom user-data) "user data of a table must be an atom")
      (props/data-property user-data))
    (u/illegal-argument "Table has no user data atom. Did you setup the table correctly?")))


(defn columns-property
  [table]
  (props/entry-property (data-property table), [:columns]))


(defn rows-property
  [table]
  (props/entry-property (data-property table), [:rows]))


(defn sorted-rows-property
  [table]
  (props/entry-property (data-property table), [:sorted-rows]))


(defn column-data-property
  [cell]
  (props/fn-property
    (fn [columns-vec, column-index]
      (when (and column-index (<= 0 column-index) (< column-index (count columns-vec)))
        (nth columns-vec column-index)))
    (props/property-via-fn #(some-> % columns-property), (props/property cell, :table-view))
    (column-index-property cell)))


(defn number-cell
  [fmt]
  (let [label (Label.)]
    (doto (CustomTableCell. label, (.textProperty label), (fn [x] (when (number? x) (format fmt x))))
      (jfx/alignment! :center-right))))


(defn number-cell-custom-font
  [fmt, font-prop]
  (let [label (Label.)]
    (props/bind (props/property label, :font) (props/fn-property jfx/font font-prop))
    (doto (CustomTableCell. label, (.textProperty label),
            (fn [x]
              (when (number? x)
                (if (and (float? x) (Double/isNaN x))
                  "N/A"
                  (format fmt x)))))
      (jfx/alignment! :center-right))))


(defn text-cell-custom-font
  [alignment, font-prop]
  (let [label (Label.)]
    (props/bind (props/property label, :font) (props/fn-property jfx/font font-prop))
    (doto (CustomTableCell. label, (.textProperty label), str)
      (jfx/alignment! alignment))))


(defn aligned-text-cell
  [alignment]
  (let [label (Label.)]
    (doto (CustomTableCell. label, (.textProperty label), str)
      (jfx/alignment! alignment))))


(defn color-cell
  [color-fn]
  (let [proxy-prop (jfx/object-property nil)
        cell (CustomTableCell. nil, proxy-prop)]
    (props/bind (props/property cell, :background)
      (props/fn-property
        (fn [value]
          (when value
            (jfx/background {:fills [{:color "white"} {:color (color-fn value) :insets 1.0}]})))
        proxy-prop))
    cell))


(defn color-chooser-cell
  []
  (let [color-chooser (doto (cc/color-chooser)
                        (jfx/max-size! Double/MAX_VALUE, Double/MAX_VALUE))]
    (doto (CustomTableCell. color-chooser, (props/property color-chooser, :value))
      (jfx/padding! Insets/EMPTY))))


(defn multi-functional-cell
  [cell-factory-fn]
  (MultiFunctionalTableCell. cell-factory-fn))


(defn table-cell
  ([]
   (TableCellWrapper. nil))
  ([update-item-fn]
   (TableCellWrapper. update-item-fn)))


(defn cell-observable-value
  [^TableCell cell]
  (-> cell .getTableColumn (.getCellObservableValue (.getIndex cell))))


(defn property?
  [x]
  (instance? Property x))


(defn combobox-cell
  [choice-values-prop, renderer-fn, prompt]
  (let [converter (jfx/string-converter (or renderer-fn str))
        ^ComboBox
        cb (doto (ComboBox.)
             (jfx/combobox-converter! converter)
             (jfx/combobox-cell-factory! converter)
             (jfx/max-size! Double/MAX_VALUE, Double/MAX_VALUE)
             (props/set-property :prompt-text prompt))
        selected-item-prop (props/selected-item-property cb)
        prev-observable-atom (atom nil)]
    (props/list-property-binding (props/items cb), choice-values-prop, :initialize :list)
    (doto (TableCellWrapper.
            (fn [^TableCell cell, value, empty?]
              (if empty?
                ; hide combobox
                (do
                  (when-let [prev-observable (deref prev-observable-atom)]
                    (props/unbind-bidi selected-item-prop, prev-observable)
                    (reset! prev-observable-atom nil))
                  (.setGraphic cell nil)
                  (-> cb .getSelectionModel (.select -1)))
                ; setup bindings if needed
                (let [observable (cell-observable-value cell)
                      prev-observable (deref prev-observable-atom)]
                  (when-not (= prev-observable observable)
                    ; unbind
                    (when prev-observable
                      (props/unbind-bidi selected-item-prop, prev-observable))
                    ; bind
                    (props/bind-bidi selected-item-prop, observable)
                    (reset! prev-observable-atom observable)
                    ; show combobox
                    (.setGraphic cell cb))))))
      (jfx/padding! 0)
      (.setGraphic nil)
      (.setText nil))))


(defn colored-text-cell
  [content-fn]
  (let [proxy-prop (jfx/object-property nil)
        label (Label.),
        cell (CustomTableCell. label, proxy-prop)
        content-prop (props/fn-property content-fn proxy-prop)]
    ; background color
    (props/bind (props/property cell, :background)
      (props/fn-property
        (fn [{:keys [color]}]
          (when color
            (jfx/background {:fills [{:color color, :insets 1.0}]})))
        content-prop))
    ; text
    (props/bind (props/property label, :text)
      (props/fn-property :text content-prop))
    cell))


(defn clear-sort-order
  [^TableView table]
  (doto table
    (-> .getSortOrder .clear)))


(defn disable-column-reordering
  [^TableView table]
  (doseq [^TableColumn column (.getColumns table)]
    (.setReorderable column false))
  table)


(let [{:keys [get]} (u/private-field TableViewSkinBase, "tableHeaderRow")]
  (defn get-table-header-row
    [table]
    (get table)))


(defn hide-table-header
  [^TableView table]
  (jfx/change-listener! (props/property table, :skin)
    (fn [_, _, ^TableViewSkinBase skin]
      (let [header (get-table-header-row skin)]
        (doto header
          (jfx/min-height! 0)
          (jfx/max-height! 0)
          (jfx/pref-height! 0)
          (props/set-property :visible false)))))
  table)


(defn bind-table-header-height!
  [^TableView table, height-prop]
  (jfx/change-listener! (props/property table, :skin)
    (fn [_, _, ^TableViewSkinBase skin]
      (let [header (get-table-header-row skin)]
        (doseq [prop-kw [:min-height, :pref-height, :max-height]]
          (props/bind (props/property header, prop-kw), height-prop)))))
  table)

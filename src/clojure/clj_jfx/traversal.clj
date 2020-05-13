; Copyright (c) Gunnar Völkel. All rights reserved.
; The use and distribution terms for this software are covered by the
; Eclipse Public License 2.0 (http://www.eclipse.org/legal/epl-v20.html)
; which can be found in the file LICENSE at the root of this distribution.
; By using this software in any fashion, you are agreeing to be bound by
; the terms of this license.
; You must not remove this notice, or any other, from this software.

(ns clj-jfx.traversal
  (:require
    [clj-jfx.control :as ctrl])
  (:import (javafx.stage Stage)
           (javafx.scene Scene)
           (javafx.scene.layout Pane)
           (javafx.scene.control ToolBar SplitPane TitledPane MenuBar Menu TabPane Tab MenuButton ScrollPane Accordion)
           (clojure.lang IDeref)))




(defmulti children "Determines the child nodes of a given node." (fn [node] (class node)))


(defmethod children :default
  [_]
  nil)


(defmethod children Stage
  [^Stage stage]
  (vector (.getScene stage)))


(defmethod children Scene
  [^Scene scene]
  (vector (.getRoot scene)))


(defmethod children Pane
  [^Pane parent]
  (vec (.getChildren parent)))


(defmethod children ToolBar
  [^ToolBar toolbar]
  (vec (.getItems toolbar)))


(defmethod children SplitPane
  [^SplitPane split-pane]
  (vec (.getItems split-pane)))


(defmethod children TitledPane
  [^TitledPane titled-pane]
  (vector (.getContent titled-pane)))


(defmethod children Accordion
  [^Accordion accordion]
  (vec (.getPanes accordion)))


(defmethod children MenuBar
  [^MenuBar menu-bar]
  (vec (.getMenus menu-bar)))


(defmethod children Menu
  [^Menu menu]
  (vec (.getItems menu)))


(defmethod children TabPane
  [^TabPane tabpane]
  (vec (.getTabs tabpane)))


(defmethod children Tab
  [^Tab tab]
  (vector (.getContent tab)))


(defmethod children MenuButton
  [^MenuButton menu-button]
  (vec (.getItems menu-button)))


(defmethod children ScrollPane
  [^ScrollPane scrollpane]
  (vector (.getContent scrollpane)))


(deftype Traversed [value]
  IDeref
  (deref [_] value))


(defn traversed
  [value]
  (Traversed. value))


(defn traversed?
  [x]
  (instance? Traversed x))


(defn traverse
  [order-fn, combine-fn, transform-fn, node]
  (let [node (cond-> node (ctrl/control? node) ctrl/control-node)]
    (reduce
      (fn [result, child-node]
        (if (traversed? result)
          result
          (let [value (if (identical? node child-node)
                        (transform-fn node)
                        (traverse order-fn, combine-fn, transform-fn, child-node))]
            (if (traversed? value)
              value
              (combine-fn result, value)))))
      (combine-fn)
      (order-fn node (children node)))))


(defn traverse-pre-order
  [combine-fn, transform-fn, node]
  (let [result (traverse list*, combine-fn, transform-fn, node)]
    (cond-> result (traversed? result) deref)))


(defn traverse-post-order
  [combine-fn, transform-fn, node]
  (let [result (traverse (fn [node, children] (concat children [node])), combine-fn, transform-fn, node)]
    (cond-> result (traversed? result) deref)))






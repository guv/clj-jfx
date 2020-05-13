; Copyright (c) Gunnar Völkel. All rights reserved.
; The use and distribution terms for this software are covered by the
; Eclipse Public License 2.0 (http://www.eclipse.org/legal/epl-v20.html)
; which can be found in the file LICENSE at the root of this distribution.
; By using this software in any fashion, you are agreeing to be bound by
; the terms of this license.
; You must not remove this notice, or any other, from this software.

(ns clj-jfx.selection
  (:require
    [clj-jfx.core :as jfx])
  (:import
    (javafx.scene.control ComboBox TabPane TableView SelectionModel MultipleSelectionModel ListView SelectionMode)
    (javafx.beans.property SimpleObjectProperty)))



(defn selection-model-property
  [component]
  (cond
    (instance? ComboBox component)
    (.selectionModelProperty ^ComboBox component)

    (instance? TabPane component)
    (.selectionModelProperty ^TabPane component)

    (instance? TableView component)
    (.selectionModelProperty ^TableView component)

    (instance? ListView component)
    (.selectionModelProperty ^ListView component)))


(defn selected-item-property
  [component]
  (let [selected-item-property (SimpleObjectProperty.)
        set-item-property (fn [^SelectionModel selection-model]
                            (jfx/unbind selected-item-property)
                            (jfx/bind selected-item-property (.selectedItemProperty selection-model)))
        selection-model-prop (selection-model-property component)]
    (set-item-property (jfx/value selection-model-prop))
    (jfx/listen-to set-item-property selection-model-prop)
    selected-item-property))


(defn selected-index-property
  [component]
  (let [selected-index-property (SimpleObjectProperty.)
        set-index-property (fn [^SelectionModel selection-model]
                            (jfx/unbind selected-index-property)
                            (jfx/bind selected-index-property (.selectedIndexProperty selection-model)))
        selection-model-prop (selection-model-property component)]
    (set-index-property (jfx/value selection-model-prop))
    (jfx/listen-to set-index-property selection-model-prop)
    selected-index-property))


(defn observable-selected-indices
  [component]
  (let [selection-model (jfx/value (selection-model-property component))]
    (assert (instance? MultipleSelectionModel selection-model) "component must have a MultipleSelectionModel")
    (.getSelectedIndices ^MultipleSelectionModel selection-model)))


(defn observable-selected-indices
  [component]
  (let [selection-model (jfx/value (selection-model-property component))]
    (assert (instance? MultipleSelectionModel selection-model) "component must have a MultipleSelectionModel")
    (.getSelectedItems ^MultipleSelectionModel selection-model)))


(defn enable-multiple-selection!
  [component]
  (let [selection-model (jfx/value (selection-model-property component))]
    (assert (instance? MultipleSelectionModel selection-model) "component must have a MultipleSelectionModel")
    (jfx/property-value! ^MultipleSelectionModel selection-model, :selection-mode SelectionMode/MULTIPLE)))


(defn clear-selection
  [component]
  (let [^SelectionModel selection-model (jfx/value (selection-model-property component))]
    (.clearSelection selection-model)
    component))
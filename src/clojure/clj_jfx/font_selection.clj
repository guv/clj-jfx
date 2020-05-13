; Copyright (c) Gunnar Völkel. All rights reserved.
; The use and distribution terms for this software are covered by the
; Eclipse Public License 2.0 (http://www.eclipse.org/legal/epl-v20.html)
; which can be found in the file LICENSE at the root of this distribution.
; By using this software in any fashion, you are agreeing to be bound by
; the terms of this license.
; You must not remove this notice, or any other, from this software.

(ns clj-jfx.font-selection
  (:require
    [clj-jfx.core :as jfx]
    [clj-jfx.control :as ctrl]
    [clojure.string :as str])
  (:import (javafx.scene.text Font FontPosture FontWeight)
           (com.sun.javafx.font.freetype FTFontFile)
           (com.sun.javafx.font PrismFontFactory)
           (javafx.scene.control ListCell ListView Label)))



(defn true-type-font?
  [font-name-fn, font-family]
  (boolean
    (some->> (jfx/font {:family font-family})
      font-name-fn
      (re-matches #".*\.ttf"))))


(defn font-filename
  [font-map, ^Font font]
  (when-let [^FTFontFile font (get font-map (str/lower-case (.getName font)))]
    (.getFileName font)))


(defn font-map
  []
  (let [field (.getDeclaredField com.sun.javafx.font.PrismFontFactory "fontResourceMap")]
    (.setAccessible field true)
    (.get field (PrismFontFactory/getFontFactory))))


(defn true-type-font-families
  []
  (->> (Font/getFamilies)
    (filter (partial true-type-font? (partial font-filename (font-map))))
    sort
    vec))


(defn default-true-type-font
  []
  (Font/font "Arial", FontWeight/NORMAL, FontPosture/REGULAR, (.getSize (Font/getDefault))))


(defn set-item-font
  [^ListCell list-cell, item, empty]
  (.setFont list-cell (Font/font ^String item)))


(defn bind-selected-item
  [^ListView listview, item-list-prop, selected-item-prop]
  (jfx/bind selected-item-prop
    (jfx/functional-property
      (fn [item-list, selected-index]
        (when (and (<= 0 selected-index) (< selected-index (count item-list)))
          (nth item-list selected-index)))
      item-list-prop
      (-> listview .getSelectionModel (jfx/property :selected-index)))))


(defn weight+posture
  [font-style-str]
  (when-let [styles (some-> font-style-str
                 str/trim
                 str/upper-case
                 (str/split #" "))]
    {:weight (or (some #(FontWeight/findByName %) styles) FontWeight/NORMAL),
     :posture (or (some #(FontPosture/findByName %) styles) FontPosture/REGULAR)}))


(defn font-weight->str
  [x]
  (if (instance? clojure.lang.Named x)
    (name x)
    (str x)))


(defn map->font-style
  [{:keys [weight, posture] :as style-map}]
  (str/join " " (keep #(some->> % (get style-map) font-weight->str str/capitalize) [:weight, :posture])))


(defn determine-font-style
  [font-family, font-name]
  (let [style-map (weight+posture (str/replace font-name, (re-pattern font-family), ""))]
    (map->font-style style-map)))


(defn font-styles
  [font-family]
  (when (string? font-family)
    (->> (Font/getFontNames font-family)
      (mapv (partial determine-font-style font-family))
      distinct
      sort
      vec)))


(defn find-font
  [{:keys [selected-family, selected-style, selected-size]}]
  (when (and selected-family selected-style selected-size)
    (let [{:keys [weight, posture]} (weight+posture selected-style)]
      (Font/font selected-family, weight, posture, (double selected-size)))))


(defn update-sample-lable-font
  [font-property, font-data-map]
  (when-let [font (find-font font-data-map)]
    (jfx/value! font-property, font)))


(defn ensure-font-map
  [font-or-map]
  (if (map? font-or-map)
    font-or-map
    (jfx/font-map font-or-map)))


(defn select-font
  [{:keys [initial-font, title]}]
  (jfx/run-now
    (let [select-font-control (jfx/create-control "clj_jfx/FontSelectionDialog.fxml")
          select-font-node (ctrl/control-node select-font-control),
          {:keys [font-listview,
                  style-listview,
                  size-listview,
                  sample-label,
                  select-button,
                  cancel-button] :as children} (ctrl/control-children select-font-control),
          font-data-atom (atom {:family-list (true-type-font-families),
                                :style-list nil
                                :size-list (vec (concat
                                                  (range 8 21)
                                                  (range 22 41 2)
                                                  (range 44 101 4)))})
          result-atom (atom nil)
          family-list-prop (jfx/map-entry-property font-data-atom :family-list)
          style-list-prop  (jfx/map-entry-property font-data-atom :style-list)
          size-list-prop   (jfx/map-entry-property font-data-atom :size-list)
          selected-family-prop (bind-selected-item font-listview, family-list-prop, (jfx/map-entry-property font-data-atom, :selected-family)),
          style-list-prop (jfx/bind style-list-prop (jfx/functional-property font-styles, selected-family-prop))
          _ (bind-selected-item style-listview, style-list-prop, (jfx/map-entry-property font-data-atom, :selected-style)),
          _ (bind-selected-item size-listview, size-list-prop, (jfx/map-entry-property font-data-atom, :selected-size))
          window (jfx/modal-window (or title "Select font") select-font-node)]
      ; list view items
      (jfx/setup-listview! font-listview, (jfx/property->observable-list family-list-prop),
        :cell-update-fn set-item-font)
      (jfx/setup-listview! style-listview, (jfx/property->observable-list style-list-prop))
      (jfx/setup-listview! size-listview, (jfx/property->observable-list size-list-prop))
      ; update font
      (add-watch font-data-atom :update-sample-lable
        (fn [_, _, _, new-state]
          (update-sample-lable-font (jfx/property ^Label sample-label, :font), new-state)))
      ; buttons
      (jfx/handle-event! cancel-button :action
        (fn [_]
          (jfx/close window)))
      (jfx/handle-event! select-button :action
        (fn [_]
          (reset! result-atom (some-> (deref font-data-atom) find-font))
          (jfx/close window)))
      ; select first style
      (jfx/change-listener! style-list-prop
        (fn [_, _, style-list]
          (when (seq style-list)
            (jfx/select-first! style-listview))))
      ; select initial font
      (jfx/handle-event! window, :window-shown,
        (fn [_]
          (let [default-font (ensure-font-map (default-true-type-font)),
                {:keys [family, size] :as selected-font} (ensure-font-map (or initial-font default-font)),
                style (map->font-style selected-font)]
            (jfx/select-item! font-listview, (or family (:family default-font)))
            (jfx/select-item! style-listview, (or style (map->font-style default-font)))
            (jfx/select-item! size-listview, (long (or size (:size default-font)))))))
      (jfx/show-and-wait window)
      (deref result-atom))))
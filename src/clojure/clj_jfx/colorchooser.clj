; Copyright (c) Gunnar Völkel. All rights reserved.
; The use and distribution terms for this software are covered by the
; Eclipse Public License 2.0 (http://www.eclipse.org/legal/epl-v20.html)
; which can be found in the file LICENSE at the root of this distribution.
; By using this software in any fashion, you are agreeing to be bound by
; the terms of this license.
; You must not remove this notice, or any other, from this software.

(ns clj-jfx.colorchooser
  (:require [clj-jfx.properties :as props]
            [clj-jfx.core :as jfx]
            [clj-jfx.colors :as col]
            [clj-jfx.util :as u])
  (:import (javafx.scene.paint Color)
           (javafx.scene.layout StackPane GridPane Region VBox HBox Pane)
           (javafx.scene.shape Rectangle StrokeType)
           (javafx.geometry NodeOrientation)
           (javafx.scene.input MouseEvent MouseButton)
           (clj_jfx.combobox CustomComboBox CustomComboBoxSkin PickerColorBox)
           (javafx.scene.control Label PopupControl ComboBoxBase OverrunStyle)
           (javafx.scene.control.skin ColorPickerSkin$PickerColorBox)
           (javafx.scene Node)))



(def standard-colors
  (mapv col/to-color
    [:aqua, :teal, :blue, :navy, :fuchsia, :purple, :red, :maroon, :yellow, :olive, :green, :lime]))


(def grid-colors
  (mapv col/to-color
    (partition 3
      [255, 255, 255, ; first row
       242, 242, 242,
       230, 230, 230,
       204, 204, 204,
       179, 179, 179,
       153, 153, 153,
       128, 128, 128,
       102, 102, 102,
       77, 77, 77,
       51, 51, 51,
       26, 26, 26,
       0, 0, 0,
       0, 51, 51, ; second row
       0, 26, 128,
       26, 0, 104,
       51, 0, 51,
       77, 0, 26,
       153, 0, 0,
       153, 51, 0,
       153, 77, 0,
       153, 102, 0,
       153, 153, 0,
       102, 102, 0,
       0, 51, 0,
       26, 77, 77, ; third row
       26, 51, 153,
       51, 26, 128,
       77, 26, 77,
       102, 26, 51,
       179, 26, 26,
       179, 77, 26,
       179, 102, 26,
       179, 128, 26,
       179, 179, 26,
       128, 128, 26,
       26, 77, 26,
       51, 102, 102, ; fourth row
       51, 77, 179,
       77, 51, 153,
       102, 51, 102,
       128, 51, 77,
       204, 51, 51,
       204, 102, 51,
       204, 128, 51,
       204, 153, 51,
       204, 204, 51,
       153, 153, 51,
       51, 102, 51,
       77, 128, 128, ; fifth row
       77, 102, 204,
       102, 77, 179,
       128, 77, 128,
       153, 77, 102,
       230, 77, 77,
       230, 128, 77,
       230, 153, 77,
       230, 179, 77,
       230, 230, 77,
       179, 179, 77,
       77, 128, 77,
       102, 153, 153, ; sixth row
       102, 128, 230,
       128, 102, 204,
       153, 102, 153,
       179, 102, 128,
       255, 102, 102,
       255, 153, 102,
       255, 179, 102,
       255, 204, 102,
       255, 255, 77,
       204, 204, 102,
       102, 153, 102,
       128, 179, 179, ; seventh row
       128, 153, 255,
       153, 128, 230,
       179, 128, 179,
       204, 128, 153,
       255, 128, 128,
       255, 153, 128,
       255, 204, 128,
       255, 230, 102,
       255, 255, 102,
       230, 230, 128,
       128, 179, 128,
       153, 204, 204, ; eigth row
       153, 179, 255,
       179, 153, 255,
       204, 153, 204,
       230, 153, 179,
       255, 153, 153,
       255, 179, 128,
       255, 204, 153,
       255, 230, 128,
       255, 255, 128,
       230, 230, 153,
       153, 204, 153,
       179, 230, 230, ; ninth row
       179, 204, 255,
       204, 179, 255,
       230, 179, 230,
       230, 179, 204,
       255, 179, 179,
       255, 179, 153,
       255, 230, 179,
       255, 230, 153,
       255, 255, 153,
       230, 230, 179,
       179, 230, 179,
       204, 255, 255, ; tenth row
       204, 230, 255,
       230, 204, 255,
       255, 204, 255,
       255, 204, 230,
       255, 204, 204,
       255, 204, 179,
       255, 230, 204,
       255, 255, 179,
       255, 255, 204,
       230, 230, 204,
       204, 255, 204])))

(def column-number 12)


(defn color-cell
  [color]
  (doto (StackPane.)
    (jfx/add-style-class "color-square")
    (->
      .getChildren
      (.add
        (doto (Rectangle. 15, 15, color)
          (.setStrokeType StrokeType/INSIDE)
          (jfx/add-style-class "color-rect"))))))


(defn fill-color-cell
  [cell, color]
  (-> (jfx/children cell)
    first
    (jfx/shape-fill! color)))


(defn color-of-cell
  [cell]
  (-> cell
    jfx/children
    first
    jfx/shape-fill))


(defn color-grid
  [focused-cell-prop, selected-color-prop, color-vec]
  (let [grid-pane (doto (GridPane.)
                    (jfx/add-style-class "color-picker-grid"))]
    (dotimes [i (count color-vec)]
      (let [column (mod i column-number)
            row (quot i column-number)
            color (nth color-vec i)
            cell (color-cell color)]
        ; track focused cell
        (doto cell
          (jfx/handle-event! :mouse-entered
            (fn [_] (props/set focused-cell-prop cell)))
          (jfx/handle-event! :mouse-exited
            (fn [_] (props/set focused-cell-prop nil)))
          (jfx/handle-event! :mouse-released
            (fn [^MouseEvent e]
              (when (and
                      (= (.getButton e) MouseButton/PRIMARY)
                      (== (.getClickCount e) 1))
                (props/set selected-color-prop, color)))))
        ; add to grid
        (.add grid-pane cell, column, row)))
    ; return
    grid-pane))



(defn scaled-width
  ^double [^StackPane cell]
  (* (.getScaleX cell) (.getWidth cell)))


(defn scaled-height
  ^double [^StackPane cell]
  (* (.getScaleY cell) (.getHeight cell)))


(defn position
  [^Pane color-palette, ^StackPane hover-cell, ^StackPane focused-cell]
  (let [bounds (.localToScene focused-cell (.getLayoutBounds focused-cell))
        x (.getMinX bounds)
        y (.getMinY bounds)
        cp-bounds (.localToScene color-palette (.getLayoutBounds color-palette))
        cx (.getMinX cp-bounds)
        cy (.getMinY cp-bounds)]
    (doto hover-cell
      (.setLayoutX (.snapPositionX color-palette (- x cx)))
      (.setLayoutY (.snapPositionY color-palette (- y cy))))))


(defn color-palette
  [state-prop]
  (let [
        focused-cell-prop (props/entry-property state-prop [:focused-cell])
        selected-color-prop (props/entry-property state-prop [:selected-color])
        palette-box (doto (VBox.)
                      (jfx/add-style-class "color-palette")
                      (jfx/add-children
                        [(color-grid focused-cell-prop, selected-color-prop, standard-colors)
                         (color-grid focused-cell-prop, selected-color-prop, grid-colors)]))
        hover-cell (doto (color-cell (col/to-color :white))
                     (jfx/mouse-transparent! true)
                     (jfx/add-style-class "hover-square")
                     (jfx/visible! false))
        color-palette (doto (Pane.)
                        (jfx/add-style-class "color-palette-region")
                        (jfx/add-child palette-box)
                        (jfx/add-child hover-cell))]
    (props/listen-to
      (fn [[prev-cell], [next-cell]]
        (when-not (identical? prev-cell, next-cell)
          (jfx/visible! hover-cell, (some? next-cell))
          (when next-cell
            (fill-color-cell hover-cell, (color-of-cell next-cell))
            (when-not (props/get-property next-cell :focused)
              (jfx/request-focus next-cell))
            (position color-palette, hover-cell, next-cell))))
      focused-cell-prop)
    ; return
    color-palette))


(defn create-skin
  [^ComboBoxBase combobox]
  (let [state-atom (atom {})
        state-prop (props/data-property state-atom)
        selected-color-prop (props/entry-property state-prop [:selected-color])
        chosen-color-prop (props/fn-property
                            (fn [color]
                              (or color Color/WHITE))
                            selected-color-prop)
        color-rect (doto (Rectangle. 12, 12)
                     (jfx/add-style-class "picker-color-rect")
                     (->
                       (props/property :fill)
                       (props/bind chosen-color-prop)))
        picker-color-box (doto (PickerColorBox. color-rect)
                           (jfx/add-style-class "picker-color")
                           (jfx/add-child color-rect))
        display-node (doto (Label.)
                       (jfx/add-style-class "color-picker-label")
                       (.setManaged false)
                       (props/set-property :text-overrun OverrunStyle/CLIP)
                       (jfx/padding! [0 10 0 10])
                       (.setGraphic picker-color-box)
                       (->
                         (props/property :text)
                         (props/bind
                           (props/fn-property col/color-name chosen-color-prop))))
        color-palette (color-palette state-prop)
        skin (CustomComboBoxSkin. combobox,
               (fn get-popup-content []
                 color-palette)
               (fn get-display-node []
                 display-node)
               (fn compute-pref-width [height, top-inset, right-inset, bottom-inset, left-inset]
                 150.0)
               (fn compute-pref-height [height, top-inset, right-inset, bottom-inset, left-inset]
                 26.0)
               {:auto-hide
                (fn [super-fn, ^ComboBoxBase node, ^PopupControl popup-control]
                  (when (and
                          (not (.isShowing popup-control))
                          (.isShowing node))
                    (.hide node))
                  (when-not (.isShowing node)
                    (super-fn popup-control)))})]
    (props/listen-to
      (fn [_, [new-color]]
        (.hide combobox))
      selected-color-prop)
    (props/bind-bidi selected-color-prop (props/property combobox, :value))
    skin))


(defn color-chooser
  []
  (let [combobox (doto (CustomComboBox. create-skin)
                   (jfx/add-style-class "button"))]
    (when (.isShowing combobox)
      (.show combobox))
    combobox))
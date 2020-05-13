; Copyright (c) Gunnar Völkel. All rights reserved.
; The use and distribution terms for this software are covered by the
; Eclipse Public License 2.0 (http://www.eclipse.org/legal/epl-v20.html)
; which can be found in the file LICENSE at the root of this distribution.
; By using this software in any fashion, you are agreeing to be bound by
; the terms of this license.
; You must not remove this notice, or any other, from this software.

(ns clj-jfx.colors
  (:require
    [clj-jfx.util :as u]
    [clojure.string :as str])
  (:import
    (javafx.scene.paint Color Paint)
    (java.lang.reflect Field Modifier)
    (com.sun.javafx.scene.control.skin Utils)))



(defn color-long
  ^long [^double color-value]
  (long (* color-value 255)))


(defn color->str
  [color]
  (when color
    (cond
      (string? color) color
      (instance? Color color) (let [^Color color color]
                                (format "rgb(%d,%d,%d)"
                                  (color-long (.getRed color)),
                                  (color-long (.getGreen color)),
                                  (color-long (.getBlue color))))
      :else (u/illegal-argument "Unsupported argument type: %s" (type color)))))


(defn color
  ^Color [color, ^double opacity]
  (Color/web (color->str color), opacity))


(defn to-color
  ^Color [color-spec]
  (when color-spec
    (cond
      (instance? Paint color-spec)
      color-spec
      (or (string? color-spec) (keyword? color-spec))
      (Color/web (name color-spec))
      (map? color-spec)
      (let [{:keys [color, opacity]} color-spec]
        (Color/web color, (or opacity 1.0)))
      (and (sequential? color-spec) (#{3 4} (count color-spec) ))
      (let [[red, green, blue, opacity] color-spec]
        (Color. (/ red 255.0), (/ green 255.0), (/ blue 255.0), (or opacity 1.0)))
      :else (u/illegal-argument "Unsupported argument for color specification - type: %s" (type color-spec)))))


(defn lighter
  [color-spec, ^double ratio]
  (.interpolate (to-color color-spec) Color/WHITE ratio))


(defn darker
  [color-spec, ^double ratio]
  (.interpolate (to-color color-spec) Color/BLACK ratio))


(defn color-constants-map
  []
  (into {}
    (comp
      (filter
        (fn [^Field f]
          (and (Modifier/isStatic (.getModifiers f)) (= (.getType f) Color))))
      (map
        (fn [^Field f]
          (let [color-name (str/lower-case (.getName f))]
            [(.get f, nil)
             color-name]))))
    (.getDeclaredFields Color)))

(defonce color->name-map (color-constants-map))

(defn color-name
  [color]
  (or
    (get color->name-map color)
    (Utils/formatHexString color)))
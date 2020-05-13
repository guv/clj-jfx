; Copyright (c) Gunnar Völkel. All rights reserved.
; The use and distribution terms for this software are covered by the
; Eclipse Public License 2.0 (http://www.eclipse.org/legal/epl-v20.html)
; which can be found in the file LICENSE at the root of this distribution.
; By using this software in any fashion, you are agreeing to be bound by
; the terms of this license.
; You must not remove this notice, or any other, from this software.

(ns clj-jfx.control)



(deftype JFXControl [node, child-nodes])


(defn create
  [node, child-nodes]
  (JFXControl. node, child-nodes))


(defn control?
  [x]
  (instance? JFXControl x))


(defn control-node
  {:inline (fn [control]
             `(let [^JFXControl control# ~control]
                (.node control#)))}
  [^JFXControl control]
  (.node control))


(defn control-children
  {:inline (fn [control]
             `(let [^JFXControl control# ~control]
                (.child-nodes control#)))}
  [^JFXControl control]
  (.child-nodes control))
; Copyright (c) Gunnar Völkel. All rights reserved.
; The use and distribution terms for this software are covered by the
; Eclipse Public License 2.0 (http://www.eclipse.org/legal/epl-v20.html)
; which can be found in the file LICENSE at the root of this distribution.
; By using this software in any fashion, you are agreeing to be bound by
; the terms of this license.
; You must not remove this notice, or any other, from this software.

(ns clj-jfx.splashscreen
  (:require [clojure.java.io :as io])
  (:import (javafx.fxml FXMLLoader)
           (javafx.stage Stage StageStyle Modality)
           (javafx.scene Scene)
           (javafx.application Platform)))



; some methods from core are reimplemented here to avoid the dependency and avoid additional loading duration
(defn load-splashscreen
  [fxml-file]
  (if-let [res (io/resource fxml-file)]
    (let [loader (doto (FXMLLoader.)
                   (.setLocation res))]
      (.load loader))
    (throw (IllegalArgumentException. (format "Splash screen resource \"%s\" not found!" fxml-file)))))


(defn splashscreen-window
  ^Stage [content-node]
  (doto (Stage. StageStyle/UNDECORATED)
    ;(.setTitle title)
    (.setScene (Scene. content-node))
    (.initModality Modality/APPLICATION_MODAL)))


(defn show-splashscreen
  [fxml-file]
  (let [result (promise)]
    (Platform/runLater
      (fn []
        (try
          (let [control (load-splashscreen fxml-file)
                window (splashscreen-window control)]
            (.show window)
            (deliver result window))
          (catch Throwable t
            (deliver result t)))))
    (let [value (deref result)]
      (if (instance? Throwable value)
        (throw (RuntimeException. "Failed to create and show splash screen.", value))
        value))))
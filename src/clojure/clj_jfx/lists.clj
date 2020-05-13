; Copyright (c) Gunnar Völkel. All rights reserved.
; The use and distribution terms for this software are covered by the
; Eclipse Public License 2.0 (http://www.eclipse.org/legal/epl-v20.html)
; which can be found in the file LICENSE at the root of this distribution.
; By using this software in any fashion, you are agreeing to be bound by
; the terms of this license.
; You must not remove this notice, or any other, from this software.

(ns clj-jfx.lists
  (:require [clj-jfx.core :as jfx]
            [clj-jfx.properties :as props])
  (:import (javafx.collections ObservableList)
           (javafx.collections.transformation FilteredList SortedList)))



(defn filtered-list
  [^ObservableList data-list, predicate-prop]
  (let [filtered-lst (FilteredList. data-list, (jfx/predicate (constantly true)))]
    (props/bind (props/property filtered-lst, :predicate)
      (props/fn-property
        jfx/predicate
        predicate-prop))
    filtered-lst))


(defn sorted-list
  [^ObservableList data-list, comparator-prop]
  (let [sorted-lst (SortedList. data-list)]
    (props/bind (props/property sorted-lst :comparator) comparator-prop)
    sorted-lst))
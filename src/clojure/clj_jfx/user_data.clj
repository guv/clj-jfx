; Copyright (c) Gunnar Völkel. All rights reserved.
; The use and distribution terms for this software are covered by the
; Eclipse Public License 2.0 (http://www.eclipse.org/legal/epl-v20.html)
; which can be found in the file LICENSE at the root of this distribution.
; By using this software in any fashion, you are agreeing to be bound by
; the terms of this license.
; You must not remove this notice, or any other, from this software.

(ns clj-jfx.user-data
  (:require [clj-jfx.properties :as props]
            [clj-jfx.util :as u])
  (:import (java.lang.reflect Method)))


(defn has-get-user-data?
  [node-or-class]
  (props/has-parameter-less-method? node-or-class, "getUserData"))


(defn create-get-user-data
  [class]
  (let [node-symbol (with-meta 'node {:tag (props/class-name class)}),
        fn-name (props/fn-name-symbol class, "get-user-data")]
    (eval `(fn ~fn-name [~node-symbol] (.getUserData ~node-symbol)))))


(defmulti get-user-data "Get the user data of the node." (fn [node] (class node)))

(defmethod get-user-data :default
  [node]
  (if (has-get-user-data? node)
    (let [c (class node)
          f (create-get-user-data c)]
      (props/add-method get-user-data, c, f)
      (f node))
    (u/illegal-argument "Node of class \"%s\" does not have method \"getUserData\"!" (class node))))


(defn has-set-user-data?
  [node-or-class]
  (let [^Class node-class (cond-> node-or-class
                            (not (class? node-or-class))
                            class)
        ^Method
        method (->> node-class
                 .getMethods
                 (filterv
                   (fn [^Method method]
                     (= (.getName method) "setUserData")))
                 first)]
    (boolean
      (and
        method
        (== 1 (count (.getParameters method)))))))


(defn create-set-user-data
  [class]
  (let [node-symbol (with-meta 'node {:tag (props/class-name class)}),
        fn-name (props/fn-name-symbol class, "set-user-data")]
    (eval `(fn ~fn-name [~node-symbol, data#] (doto ~node-symbol (.setUserData data#))))))


(defmulti set-user-data "Set the user data of the node." (fn [node, data] (class node)))

(defmethod set-user-data :default
  [node, data]
  (if (has-set-user-data? node)
    (let [c (class node)
          f (create-set-user-data c)]
      (props/add-method set-user-data, c, f)
      (f node, data))
    (u/illegal-argument "Node of class \"%s\" does not have method \"setUserData\"!" (class node))))



(defn user-data
  "Returns the user data of the given node."
  [node]
  (if node
    (get-user-data node)
    (u/illegal-argument "No node given for user-data!")))


(defn user-data!
  "Sets the user data of the given node."
  [node, data]
  (set-user-data node, data))


(defn update-user-data!
  "Updates the user data of the given node by the result of the given function `f` on the current user data and the specified args."
  [node, f, & args]
  (set-user-data node, (apply f (get-user-data node), args)))
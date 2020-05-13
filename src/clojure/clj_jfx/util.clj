; Copyright (c) Gunnar Völkel. All rights reserved.
; The use and distribution terms for this software are covered by the
; Eclipse Public License 2.0 (http://www.eclipse.org/legal/epl-v20.html)
; which can be found in the file LICENSE at the root of this distribution.
; By using this software in any fashion, you are agreeing to be bound by
; the terms of this license.
; You must not remove this notice, or any other, from this software.

(ns clj-jfx.util
  (:require
    [clojure.stacktrace :as st]
    [clojure.tools.logging :as log]
    [clojure.java.io :as io]
    [clojure.set :as set])
  (:import (java.util UUID)
           (clojure.lang Numbers)))



(defn illegal-argument
  [fmt, & args]
  (throw (IllegalArgumentException. ^String (apply format fmt args))))


(defn for-each!
  [f, coll]
  (reduce
    (fn [_, x]
      (f x)
      nil)
    nil
    coll))


(defn for-each-indexed!
  [f, coll]
  (reduce
    (fn [i, x]
      (f i, x)
      (inc i))
    0
    coll))


(defn iteration
  [^long k, f, x]
  (loop [i 0, x x]
    (if (< i k)
      (recur (unchecked-inc i), (f x))
      x)))


(defn iteration-indexed
  [^long k, f, x]
  (loop [i 0, x x]
    (if (< i k)
      (recur (unchecked-inc i), (f i, x))
      x)))


(defn println-err
  [& msg]
  (binding [*out* *err*]
    (apply println msg)
    (flush)))


(defonce ^:dynamic *exceptions-on-stderr* true)


(defn log-exception
  [message, e]
  (let [log-msg (str message "\n" (with-out-str (st/print-cause-trace e)))]
    (log/error log-msg)
    (when *exceptions-on-stderr*
      (println-err log-msg))))


(defmacro safe
  [& body]
  (let [message (str "Uncaught exception in " (pr-str &form))]
    `(try
       ~@body
       (catch Throwable t#
         (log-exception ~message, t#)))))


(defmacro safe-future
  "A Clojure future that prints exceptions to stderr."
  [& body]
  `(future (safe ~@body)))


(defn file-extension
  [file]
  (some->> file io/file .getName (re-matches #".*\.(.*)$") second))


(defn file-name
  [file]
  (-> file io/file .getAbsolutePath))


(defn overlapping-keys
  [m-1, m-2]
  (set/intersection (set (keys m-1)) (set (keys m-2))))


(defn uuid-str
  []
  (str (UUID/randomUUID)))


(defn inverse-map
  "Creates a map that maps every value to its key.
  The result will not contain some keys that mapped to the same key."
  [m]
  (persistent!
    (reduce-kv
      (fn [result-map, k, v]
        (assoc! result-map v k))
      (transient {})
      m)))


(defn class-name
  [^Class c]
  (when c
    (.getCanonicalName c)))

(defn universal-compare
  [a, b]
  (let [ca (class a),
        cb (class b)]
    (cond
      (= ca cb) (compare a b)
      (and (number? ca) (number? cb)) (Numbers/compare a b)
      :else (compare
              (class-name ca)
              (class-name cb)))))


(defn ensure-vector
  [x]
  (when x
    (cond
      (vector? x) x
      (seqable? x) (vec x)
      :else [x])))


(defn private-field
  [^Class class, field-name]
  (let [field (doto (.getDeclaredField class, (name field-name))
                (.setAccessible true))
        field-type (.getType field)
        cast-fn (when (.isPrimitive field-type)
                  (-> (.getName field-type)
                    symbol
                    resolve))]
    {:get (fn [obj]
            (.get field obj))
     :set (if cast-fn
            (fn [obj, value]
              (.set field obj, (cast-fn value))
              obj)
            (fn [obj, value]
              (.set field obj, value)
              obj))}))
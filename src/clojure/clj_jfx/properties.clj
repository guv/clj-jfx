; Copyright (c) Gunnar Völkel. All rights reserved.
; The use and distribution terms for this software are covered by the
; Eclipse Public License 2.0 (http://www.eclipse.org/legal/epl-v20.html)
; which can be found in the file LICENSE at the root of this distribution.
; By using this software in any fashion, you are agreeing to be bound by
; the terms of this license.
; You must not remove this notice, or any other, from this software.

(ns
  ^{:doc "Property access via multi-methods inspired by \"fn-fx\" library."}
  clj-jfx.properties
  (:refer-clojure :exclude [set, get])
  (:require [clojure.string :as str]
            [clj-jfx.util :as u]
            [clj-jfx.core :as jfx])
  (:import (java.lang.reflect Method)
           (clojure.lang MultiFn)
           (javafx.beans.value WritableValue ObservableValue ChangeListener)
           (javafx.collections ObservableList ListChangeListener FXCollections)
           (java.util Collection)
           (javafx.beans.property Property SimpleObjectProperty)
           (javafx.scene.control SingleSelectionModel SelectionModel)
           (clj_jfx.properties ClojureProperty IClojureProperty PropertyTrace)))



(defn property?
  [x]
  (instance? Property x))


(defn observable-value?
  [x]
  (instance? ObservableValue x))


(defn property-name->symbol
  [property-name]
  (let [segments (-> property-name name (str/split #"-")),
        camel-case (->> segments
                     rest
                     (map str/capitalize)
                     (list* (first segments))
                     (apply str))]
    (symbol (str camel-case "Property"))))


(defn add-method
  [^MultiFn multifn, dispatch-val, f]
  (.addMethod multifn dispatch-val f))


(defn property-dispatch
  [node, property-name]
  [(class node) property-name])

(defmulti property "Get the property with the given name of the node." property-dispatch)


(defn has-parameter-less-method?
  [node-or-class, method-name]
  (let [^Class node-class (cond-> node-or-class
                            (not (class? node-or-class))
                            class)
        ^Method
        method (->> node-class
                 .getMethods
                 (filterv
                   (fn [^Method method]
                     (= (.getName method) method-name)))
                 first)]
    (boolean
      (and
        method
        (zero? (count (.getParameters method)))))))


(defn has-property?
  [node-or-class, property-name]
  (has-parameter-less-method? node-or-class, (name (property-name->symbol property-name))))


(defn class-name
  [^Class class]
  (.getName class))


(defn fn-name-symbol
  [^Class class, suffix]
  (symbol (str (.getSimpleName class) "-" suffix)))


(defn create-property-fn
  [class, property-name]
  (let [property-symbol (property-name->symbol property-name)
        node-symbol (with-meta 'node {:tag (class-name class)}),
        fn-name (fn-name-symbol class, (name property-name))]
    (eval `(fn ~fn-name [~node-symbol, not-used#] (. ~node-symbol ~property-symbol)))))


(defmethod property :default
  [node, property-name]
  (if (has-property? node, property-name)
    (let [f (create-property-fn (class node), property-name)]
      (add-method property, (property-dispatch node, property-name), f)
      (f node, property-name))
    (u/illegal-argument "Node of class \"%s\" does not have property \"%s\"!" (class node) property-name)))


(defn observable-array-list
  ([]
   (FXCollections/observableArrayList))
  ([^Collection coll]
   (FXCollections/observableArrayList coll)))


(defn create-property
  [value]
  (SimpleObjectProperty. value))


(defn set
  [^WritableValue property, value]
  (locking property
    (doto property
      (.setValue value))))


(defn get
  [^ObservableValue property]
  (locking property
    (.getValue property)))


(defn set-property
  [node, property-name, value]
  (doto node
    (-> (property property-name) (set value))))


(defn get-property
  [node, property-name]
  (-> node (property property-name) (get)))


(deftype Binding [^Property target-property, ^ObservableValue source-observable-value]

  ChangeListener
  (changed [this, _, old-value, new-value]
    (when-not (= old-value new-value)
      (try
        (set target-property new-value)
        (catch Throwable t
          (u/log-exception "Failed to update target property of binding.", t)))))

  Object
  (hashCode [this]
    (.hashCode source-observable-value))

  (equals [this obj]
    (boolean
      (when-not (nil? obj)
        (or
          (identical? this obj)
          (when (instance? Binding obj)
            (= (.source-observable-value ^Binding obj) source-observable-value)))))))


(defn bind
  [^Property property, ^ObservableValue observable-value]
  (let [binding (Binding. property, observable-value)]
    (.addListener observable-value, binding))
  (doto property
    (set (get observable-value))))


(defn unbind
  [^Property property, ^ObservableValue observable-value]
    (let [binding (Binding. property, observable-value)]
    (.removeListener observable-value, binding))
  property)


(deftype BidirectionalBinding [^Property left-property, ^Property right-property, left->right, right->left, updating?-atom]

  ChangeListener
  (changed [this, modified-property, old-value, new-value]
    (when-let [[target-prop, conversion-fn] (cond
                                              (= modified-property left-property) [right-property, left->right]
                                              (= modified-property right-property) [left-property, right->left])]
      (when-not (or (deref updating?-atom) (= old-value new-value))
        (reset! updating?-atom true)
        (try
          (set target-prop (cond-> new-value conversion-fn conversion-fn))
          (catch Throwable t
            (u/log-exception "Failed to update target property of bidirectional binding.", t))
          (finally
            (reset! updating?-atom false))))))

  Object
  (hashCode [this]
    (unchecked-int (unchecked-multiply (.hashCode left-property) (.hashCode right-property))))

  (equals [this obj]
    (boolean
      (when-not (nil? obj)
        (or
          (identical? this obj)
          (when (instance? BidirectionalBinding obj)
            (let [other ^BidirectionalBinding obj]
              (and
                (= (.left-property other) left-property)
                (= (.right-property other) right-property)))))))))


(defn bind-bidi
  ([property-1, property-2]
   (bind-bidi property-1, nil, property-2, nil))
  ([^Property property-1, fn-1->2, ^Property property-2, fn-2->1]
   (let [binding (BidirectionalBinding. property-1, property-2, fn-1->2, fn-2->1, (atom false))]
     (set property-1 (cond-> (get property-2) fn-2->1 fn-2->1))
     (.addListener property-2 binding)
     (.addListener property-1 binding))
    ; return property 1
   property-1))


(defn unbind-bidi
  [^Property property-1, ^Property property-2]
  (let [binding (BidirectionalBinding. property-1, property-2, nil, nil, (atom false))]
    (.removeListener property-2 binding)
    (.removeListener property-1 binding))
  ; return property 1
  property-1)


(defn items-dispatch
  [node]
  (class node))

(defmulti items "Get the items of the specified node." items-dispatch)


(defn has-items?
  [node-or-class]
  (has-parameter-less-method? node-or-class, "getItems"))


(defn create-items-fn
  [class]
  (let [cname (class-name class),
        node-symbol (with-meta 'node {:tag cname}),
        fn-name (fn-name-symbol class, "items")]
    (eval `(fn ~fn-name [~node-symbol] (.getItems ~node-symbol)))))


(defmethod items :default
  [node]
  (if (has-items? node)
    (let [f (create-items-fn (class node))]
      (add-method property, (items-dispatch node), f)
      (f node))
    (u/illegal-argument "Node of class \"%s\" does not have items!" (class node))))


(defn add-items
  [node-or-list, item-coll]
  (let [observable-list (cond-> node-or-list
                          (not (instance? ObservableList node-or-list))
                          (items))]
    (.addAll ^ObservableList observable-list ^Collection item-coll)
    node-or-list))



(defn data-property
  [atom]
  (if-let [prop (-> atom meta ::property)]
    prop
    (let [prop (ClojureProperty. atom)]
      (alter-meta! atom assoc ::property prop)
      prop)))


(defn entry-property
  [^ClojureProperty prop, path, & {:keys [keys]}]
  (.entryProperty prop, (u/ensure-vector path), keys))


(defn selected-keys-property
  [^ClojureProperty prop, keys]
  (.limitToKeys prop, keys))


(defn cp-swap
  "Similar to swap! for atoms but for clojure properties."
  [^ClojureProperty prop, f, & args]
  (.swap prop (^:once fn* [value] (apply f value, args))))


(defn jfx-swap
  [property, f, & args]
  (locking property
    (try
      (let [result (apply f (get property) args)]
        (set property result)
        result)
      (catch Throwable t
        (u/log-exception "Failed to swap!" t)
        property))))


(defn dispatch-swap
  [property, & _]
  (class property))

(defmulti swap #'dispatch-swap)

(defmethod swap IClojureProperty
  [prop, f, & args]
  (apply cp-swap prop, f, args))


(defmethod swap Property
  [prop, f, & args]
  (apply jfx-swap prop, f, args))

(prefer-method swap IClojureProperty Property)



(defn enable-tracing
  []
  (ClojureProperty/enabledTracing))


(defn disable-tracing
  []
  (ClojureProperty/disabledTracing))


(defn current-property-trace
  ^PropertyTrace []
  (PropertyTrace/currentTrace))


(defn trace
  {:inline (fn [property-trace]
             `(let [^PropertyTrace pt# ~property-trace]
                (.getPropertyTrace pt#)))}
  [^PropertyTrace property-trace]
  (.getPropertyTrace property-trace))


(defn trace-string
  {:inline (fn [property-trace]
             `(let [^PropertyTrace pt# ~property-trace]
                (.traceString pt#)))}
  [^PropertyTrace property-trace]
  (.traceString property-trace))


(defn print-trace
  {:inline (fn [property-trace]
             `(let [^PropertyTrace pt# ~property-trace]
                (.printTrace pt#)))}
  [^PropertyTrace property-trace]
  (.printTrace property-trace))


(deftype ListPropertyBinding [^ObservableList observable-list, ^Property property,
                              update-list-fn, update-property-fn, ^int hashcode, fire?]

  ChangeListener
  (changed [_, _, old-value new-value]
    (when (and (deref fire?) (not= old-value new-value))
      (reset! fire? false)
      (try
        (update-list-fn observable-list, old-value, new-value)
        (catch Throwable t
          (u/log-exception "Failed to update list property of list property binding.", t))
        (finally
          (reset! fire? true)))))

  ListChangeListener
  (onChanged [this change]
    (let [old-value (get property)
          new-value (vec observable-list)]
      (when (and (deref fire?) (not= old-value new-value))
        (reset! fire? false)
        (try
          (set property (update-property-fn old-value, change, new-value))
          (catch Throwable t
            (u/log-exception "Failed to update value property of list property binding.", t))
          (finally
            (reset! fire? true))))))

  Object
  (hashCode [this]
    hashcode)
  (equals [this obj]
    (cond
      (identical? this, obj)
      true

      (or (nil? observable-list) (nil? property))
      false

      (not (instance? ListPropertyBinding obj))
      false

      :else (let [^ListPropertyBinding binding obj
                  other-list (.observable-list binding)
                  other-prop (.property binding)]
              (if (or (nil? other-list) (nil? other-prop))
                false
                (and
                  (identical? observable-list other-list)
                  (identical? property other-prop)))))))


(defn default-list-update
  [^ObservableList observable-list, old-value, new-value]
  (doto observable-list
    (.setAll ^Collection new-value)))


(defn clear+add-list-update
  [^ObservableList observable-list, old-value, new-value]
  (doto observable-list
    (.clear)
    (.setAll ^Collection new-value)))


(defn element-wise-list-update
  [^ObservableList observable-list, old-list, new-list]
  (let [old-count (count old-list)
        new-count (count new-list)
        n (min old-count new-count)]
    ; replace elements
    (dotimes [i n]
      (let [new-element (nth new-list i)
            old-element (.get observable-list i)]
        (when-not (= old-element new-element)
          (.set observable-list i new-element))))
    (cond
      ; add remaining new elements
      (> new-count old-count)
      (dotimes [j (- new-count old-count)]
        (.add observable-list (nth new-list (+ j old-count))))

      ; remove superfluous old element
      (< new-count old-count)
      (.remove observable-list, new-count, old-count)))
  nil)


(defn default-property-update
  [_, _, new-value]
  new-value)


(defn list-property-binding
  [^ObservableList observable-list, ^Property property
   & {:keys [update-list-fn, update-property-fn, initialize]}]
  (let [hashcode (unchecked-int (unchecked-multiply (.hashCode observable-list) (.hashCode property)))
        update-list-fn (or update-list-fn default-list-update)
        update-property-fn (or update-property-fn default-property-update)
        binding (ListPropertyBinding. observable-list, property, update-list-fn, update-property-fn, hashcode, (atom true))]
    (.addListener property binding)
    (.addListener observable-list binding)
    (cond
      (= initialize :list) (update-list-fn observable-list, nil, (get property))
      (= initialize :property) (update-property-fn nil, nil, (vec observable-list)))
    binding))


(defn observable-list->property
  [^ObservableList observable-list, & {:keys [update-list-fn, update-property-fn]}]
  (let [property (SimpleObjectProperty. (vec observable-list))
        binding (list-property-binding observable-list, property,
                  :update-list-fn update-list-fn, :update-property-fn update-property-fn
                  :initialize :property)]
    property))


(defn property->observable-list
  [prop, & {:keys [update-list-fn, update-property-fn]}]
  (let [observable-list (FXCollections/observableArrayList ^Collection (get prop))
        binding (list-property-binding observable-list, prop,
                  :update-list-fn update-list-fn, :update-property-fn update-property-fn
                  :initialize :list)]
    observable-list))


(defn fn-property
  ^Property [f, & properties]
  (let [func-prop (SimpleObjectProperty. (apply f (mapv get properties)))]
    (u/for-each-indexed!
      (fn [index, property]
        (jfx/change-listener! property
          (fn [_, old-value, new-value]
            (when-not (= old-value new-value)
              (try
                (set func-prop (apply f (assoc (mapv get properties) index new-value)))
                (catch Throwable t
                  (u/log-exception "Failed to update functional property.", t)))))))
      properties)
    func-prop))


(deftype CombinedCallback [callback-fn, dependencies, old-values-atom]

  ChangeListener
  (changed [this, observable, _, new-value]
    ; find changed observable index
    (let [observable-index (reduce
                             (fn [result-index, dep]
                               (if (identical? dep observable)
                                 (reduced result-index)
                                 (inc result-index)))
                             0
                             dependencies)
          _ (assert (< observable-index (count dependencies)))
          ; determine old and new values
          old-values (deref old-values-atom)
          new-values (assoc old-values observable-index new-value)]
      ; update old values
      (reset! old-values-atom new-values)
      ; call callback when changed
      (when-not (= old-values new-values)
        (try
          (callback-fn old-values, new-values)
          (catch Throwable t
            (u/log-exception "Failed to execute callback function of combined callback.", t)))))))


(defn bind-updating
  [^Property property, update-fn, & observables]
  (when (zero? (count observables))
    (throw (NullPointerException. "At least one observable is required.")))

  (when (.isBound property)
    (u/illegal-argument "Property is alread bound!"))

  (let [update-value (fn [old-observable-values, new-observable-values]
                       (.setValue property
                         (update-fn (.getValue property), old-observable-values, new-observable-values)))
        callback (CombinedCallback. update-value, (vec observables), (atom (mapv get observables)))]
    ; configure listener
    (u/for-each! #(jfx/change-listener! %, callback) observables)
    ; update value
    (update-value (vec (repeatedly (count observables) (constantly nil))), (mapv get observables)))
  ; return property
  property)


(defn listen-to
  [f & observables]
  (when (zero? (count observables))
    (throw (NullPointerException. "At least one observable is required.")))

  (let [init-old-values (mapv get observables)
        callback (CombinedCallback. f, (vec observables), (atom init-old-values))]
    ; configure listener
    (u/for-each! #(jfx/change-listener! %, callback) observables)
    callback))


(defn callback-ignore-old-values
  "Wraps around the given function and calls it providing only the new values of the callback."
  [f]
  (fn [old-values, new-values]
    (f new-values)))



(defn selected-*-property
  [property-kw, node]
  (let [selection-model-prop (property node, :selection-model)
        ; initialized property
        item-prop (SimpleObjectProperty. (get-property (get selection-model-prop) property-kw))
        updating?-atom (atom false)
        change-listener (reify ChangeListener
                          (changed [this, observable, old-value, new-value]
                            (when-not (or (deref updating?-atom) (= old-value new-value))
                              (reset! updating?-atom true)
                              (try
                                (set item-prop new-value)
                                (finally
                                  (reset! updating?-atom false))))))]
    ; set initial listener
    (.addListener ^Property (property (get selection-model-prop) property-kw) change-listener)
    ; listen to selection model changes
    (jfx/change-listener! selection-model-prop
      (fn [_, old-model, new-model]
        (when old-model
          (.removeListener ^ObservableValue (property old-model, property-kw) change-listener))
        (when new-model
          (.addListener ^ObservableValue (property new-model, property-kw), change-listener))))
    ; listen to property changes
    (jfx/change-listener! item-prop
      (fn [_, old-item, new-item]
        (when-not (or (deref updating?-atom) (= old-item new-item))
          (reset! updating?-atom true)
          (try
            (.select ^SingleSelectionModel (get selection-model-prop) new-item)
            (finally
              (reset! updating?-atom false))))))
    ; return property
    item-prop))


(defn selected-item-property
  [node]
  (selected-*-property :selected-item, node))


(defn selected-index-property
  [node]
  (selected-*-property :selected-index, node))


(defn clear-selection
  [node]
  (.clearSelection ^SelectionModel (get-property node, :selection-model))
  node)


(defn property-via-fn
  "Returns a property that is determined via the `property-lookup-fn` based on the specified properties."
  [property-lookup-fn, property, & more-properties]
  (let [looked-up-prop (apply fn-property property-lookup-fn (list* property, more-properties))
        result-prop (SimpleObjectProperty.
                      (when-let [prop (get looked-up-prop)]
                        (get prop)))]
    (jfx/change-listener! looked-up-prop
      (fn [_, old-observable, new-observable]
        (when old-observable
          (if (property? old-observable)
            (unbind-bidi result-prop, old-observable)
            (unbind result-prop, old-observable)))
        (when new-observable
          (if (property? new-observable)
            (bind-bidi result-prop, new-observable)
            (bind result-prop, new-observable)))))
    ; return observable
    result-prop))

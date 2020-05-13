// Copyright (c) Gunnar Völkel. All rights reserved.
// The use and distribution terms for this software are covered by the
// Eclipse Public License 2.0 (http://www.eclipse.org/legal/epl-v20.html)
// which can be found in the file LICENSE at the root of this distribution.
// By using this software in any fashion, you are agreeing to be bound by
// the terms of this license.
// You must not remove this notice, or any other, from this software.

package clj_jfx.properties;

import clojure.java.api.Clojure;
import clojure.lang.*;
import javafx.beans.InvalidationListener;
import javafx.beans.Observable;
import javafx.beans.WeakListener;
import javafx.beans.binding.Bindings;
import javafx.beans.property.Property;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;

import java.lang.ref.WeakReference;
import java.text.Format;
import java.util.HashMap;
import java.util.Vector;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;


public class ClojureProperty implements IClojureProperty {


    static private final IFn getin = Clojure.var("clojure.core/get-in");
    static private final IFn swap = Clojure.var("clojure.core/swap!");
    static private final IFn reset = Clojure.var("clojure.core/reset!");
    static private final IFn assoc_in = Clojure.var("clojure.core/assoc-in");
    static private final IFn update = Clojure.var("clojure.core/update");
    static private final IFn update_in = Clojure.var("clojure.core/update-in");
    static private final IFn merge = Clojure.var("clojure.core/merge");
    static private final IFn into = Clojure.var("clojure.core/into");
    static private final IFn select_keys = Clojure.var("clojure.core/select-keys");
    static private final IFn set = Clojure.var("clojure.core/set");
    static private final IFn vec = Clojure.var("clojure.core/vec");
    static private final IFn intersection = Clojure.var("clojure.set/intersection");
    static private final IFn str_join = Clojure.var("clojure.string/join");


    static private IPersistentSet set(Seqable s) {
        if (s == null)
            return null;

        return (IPersistentSet) set.invoke(s);
    }

    static private IPersistentVector vec(Seqable s) {
        if (s == null)
            return null;

        return (IPersistentVector) vec.invoke(s);
    }


    private static volatile boolean tracing = false;

    public static synchronized void enabledTracing(){
        tracing = true;
    }

    public static synchronized void disabledTracing(){
        tracing = false;
    }


    private Atom dataAtom;
    private IPersistentVector propertyPath;
    private IPersistentSet keySet;
    private final ClojureProperty parent;

    private ObservableValue<Object> boundToObservable = null;
    private Listener listener = null;

    private Vector<InvalidationListener> invalidationListeners = new Vector<>();
    private Vector<ChangeListener<Object>> changeListeners = new Vector<>();


    private enum UpdateAuthority {NOBODY, PROPERTY, ATOM}

    ;
    private AtomicReference<UpdateAuthority> currentUpdateAuthority = new AtomicReference<>(UpdateAuthority.NOBODY);

    public ClojureProperty(Atom dataAtom) {
        this(dataAtom, null, null, null);
    }

    private ClojureProperty(Atom dataAtom, Seqable path, Seqable keys, ClojureProperty parent) {
        if (dataAtom == null)
            throw new NullPointerException("atom must be non-null");

        this.dataAtom = dataAtom;
        this.propertyPath = vec(nonEmptyOrNull(path));
        this.keySet = set(nonEmptyOrNull(keys));
        this.parent = parent;

        // add watch that handles notification on value changes of this property
        this.dataAtom.addWatch(this,
                new AFn() {
                    @Override
                    public Object invoke(Object key, Object atom, Object oldRoot, Object newRoot) {
                        if (currentUpdateAuthority.get() == UpdateAuthority.NOBODY) {
                            if (oldRoot != newRoot) {
                                Object oldValue = lookupValue(oldRoot, propertyPath, keySet);
                                Object newValue = lookupValue(newRoot, propertyPath, keySet);

                                if (oldValue != newValue && unequal(oldValue, newValue)) {
                                    currentUpdateAuthority.set(UpdateAuthority.ATOM);
                                    try {
                                        ClojureProperty.this.fireValueChangedEvent(oldValue, newValue);
                                    } finally {
                                        currentUpdateAuthority.set( UpdateAuthority.NOBODY );
                                    }
                                }
                            }
                        }
                        return null;
                    }
                });
    }


    private static <T extends Seqable> T nonEmptyOrNull(T obj) {
        return obj != null && obj.seq() != null ? obj : null;
    }

    @Override
    public Object getBean() {
        return dataAtom;
    }

    @Override
    public String getName() {
        return propertyPath != null ? propertyPath.toString() : "";
    }

    @Override
    public void bind(ObservableValue<?> observable) {
        if (observable == null) {
            throw new NullPointerException("Cannot bind to null");
        }

        if (!observable.equals(boundToObservable)) {
            unbind();
            boundToObservable = (ObservableValue<Object>) observable;
            if (listener == null)
                listener = new Listener(this);
            setValue(boundToObservable.getValue());
            boundToObservable.addListener(listener);
        }
    }

    @Override
    public void unbind() {
        if (boundToObservable != null) {
            setValue(boundToObservable.getValue());
            boundToObservable.removeListener(listener);
            boundToObservable = null;
        }
    }

    @Override
    public boolean isBound() {
        return boundToObservable != null;
    }

    @Override
    public void bindBidirectional(Property<Object> other) {
        Bindings.bindBidirectional(this, other);
    }

    @Override
    public void unbindBidirectional(Property<Object> other) {
        Bindings.unbindBidirectional(this, other);
    }

    @Override
    public void addListener(ChangeListener<? super Object> listener) {
        changeListeners.add(listener);
    }

    @Override
    public void removeListener(ChangeListener<? super Object> listener) {
        changeListeners.remove(listener);
    }

    @Override
    public void addListener(InvalidationListener listener) {
        invalidationListeners.add(listener);
    }

    @Override
    public void removeListener(InvalidationListener listener) {
        invalidationListeners.remove(listener);
    }


    public static Object select(Object map, IPersistentVector path) {
        if (path == null)
            return map;

        return getin.invoke(map, path);
    }

    public static Object project(Object map, IPersistentSet keys) {
        if (keys == null)
            return map;

        return select_keys.invoke(map, keys);
    }

    public static Object lookupValue(Object map, IPersistentVector path, IPersistentSet keys) {
        return project(select(map, path), keys);
    }

    @Override
    public Object getValue() {
        Object data = dataAtom.deref();

        return lookupValue(data, propertyPath, keySet);
    }


    private static final IFn merge_with_keys = new AFn() {
        @Override
        public Object invoke(Object map, Object keys, Object new_map) {
            return merge.invoke(map, select_keys.invoke(new_map, keys));
        }
    };


    @Override
    public void setValue(Object value) {
        if (currentUpdateAuthority.get() != UpdateAuthority.PROPERTY) {
            currentUpdateAuthority.set( UpdateAuthority.PROPERTY );
            boolean doTracing = tracing;
            if( doTracing )
                PropertyTrace.push( this );
            try {
                var oldValue = getValue();

                if (propertyPath == null) {
                    if (keySet == null)
                        // set value at root
                        reset.invoke(dataAtom, value);
                    else
                        // merge value at root, but only the specified keys
                        swap.invoke(dataAtom, merge_with_keys, keySet, value);
                } else {
                    if (keySet == null)
                        // set value at path
                        swap.invoke(dataAtom, assoc_in, propertyPath, value);
                    else
                        // merge value under path, but only the specified keys
                        swap.invoke(dataAtom, update_in, propertyPath, merge_with_keys, keySet, value);
                }

                fireValueChangedEvent(oldValue, value);
            } catch (StackOverflowError t) {
                System.err.println(
                        String.format("Cycle while setting %s to %s", toString(), (value == null) ? "null" : value.toString())
                );
                throw t;
            }finally {
                if( doTracing )
                    PropertyTrace.pop( this );
                currentUpdateAuthority.set( UpdateAuthority.NOBODY );
            }
        }
    }

    private void updateBinding() {
        if (boundToObservable != null)
            setValue(boundToObservable.getValue());
    }

    private static boolean unequal(Object oldValue, Object newValue) {
        return (oldValue == null || newValue == null) ? oldValue != newValue : !oldValue.equals(newValue);
    }

    private static boolean equal(Object oldValue, Object newValue) {
        return (oldValue == null || newValue == null) ? oldValue == newValue : oldValue.equals(newValue);
    }

    private void fireValueChangedEvent(Object oldValue, Object newValue) {
        if (unequal(oldValue, newValue)) {
            // invalidation first
            var invalidationEnum = invalidationListeners.elements();
            while(invalidationEnum.hasMoreElements()){
                invalidationEnum.nextElement().invalidated(this);
            }
            // change second
            var changeEnum = changeListeners.elements();
            while(changeEnum.hasMoreElements()){
                changeEnum.nextElement().changed(this, oldValue, newValue);
            }
        }
    }

    @Override
    public String toString() {
        return "ClojureProperty " + Integer.toHexString(System.identityHashCode(this))
                + (propertyPath != null || keySet != null ? " " : "")
                + (propertyPath != null ? "[" + str_join.invoke(", ", propertyPath) + "]" : "")
                + (propertyPath != null && keySet != null ? " " : "")
                + (keySet != null ? "#{" + str_join.invoke(", ", keySet) + "}" : "");
    }


    private ClojureProperty getParent() {
        return parent;
    }

    private ClojureProperty getRootParent() {
        if (getParent() == null)
            return this;

        ClojureProperty prop = this;
        do {
            prop = prop.getParent();
        } while (prop.getParent() != null);

        return prop;
    }

    private class CacheKey {
        private final Seqable path;
        private final Seqable keys;

        private final int hashCode;

        CacheKey(Seqable path, Seqable keys) {
            this.path = path;
            this.keys = keys;

            hashCode = Util.hashCombine(Util.hasheq(path), Util.hasheq(keys));
        }

        @Override
        public int hashCode() {
            return hashCode;
        }


        @Override
        public boolean equals(Object obj) {
            if (obj == null)
                return false;

            if (this == obj)
                return true;

            if (!(obj instanceof CacheKey))
                return false;

            var otherKey = (CacheKey) obj;
            return equal(path, otherKey.path) && equal(keys, otherKey.keys);
        }
    }


    private final HashMap<CacheKey, WeakReference<IClojureProperty>> cache = new HashMap<>();

    private IClojureProperty lookupInCache(CacheKey key) {
        WeakReference<IClojureProperty> ref = cache.get(key);
        if (ref == null)
            return null;

        IClojureProperty prop = ref.get();

        if (prop == null)
            cache.remove(key);

        return prop;
    }

    private IClojureProperty lookupInRootCache(CacheKey key) {
        return getRootParent().lookupInCache(key);
    }

    private void storeInCache(CacheKey key, IClojureProperty prop) {
        cache.put(key, new WeakReference<>(prop));
    }

    private void storeInRootCache(CacheKey key, IClojureProperty prop) {
        getRootParent().storeInCache(key, prop);
    }


    public IClojureProperty newOrCachedProperty(IPersistentVector newPath, IPersistentSet newKeys) {
        var key = new CacheKey(newPath, newKeys);

        IClojureProperty newProp = lookupInRootCache(key);

        if (newProp == null) {
            newProp = new ClojureProperty(dataAtom, newPath, newKeys, this);
            storeInRootCache(key, newProp);
        }

        return newProp;
    }


    @Override
    public IClojureProperty entryProperty(Seqable entryPath, Seqable keys) {
        if (entryPath.seq() == null)
            throw new RuntimeException("a non-empty path must be specified");

        var newPath = vec((Seqable) into.invoke(propertyPath != null ? propertyPath : PersistentVector.EMPTY, entryPath)
        );
        // ignore old keys since a new path is chosen
        var newKeys = set(nonEmptyOrNull(keys));

        return newOrCachedProperty(newPath, newKeys);
    }

    @Override
    public IClojureProperty limitToKeys(Seqable keys) {
        if (keys.seq() == null)
            throw new RuntimeException("a non-empty key set must be specified");

        IPersistentSet newKeys = null;
        if (keySet == null)
            newKeys = set(keys);
        else {
            newKeys = set((Seqable) intersection.invoke(keySet, set(keys)));
            if (newKeys.seq() == null)
                throw new RuntimeException(
                        String.format("limiting %s to %s results in an empty set",
                                keySet.toString(), keys.toString()));
        }

        return newOrCachedProperty(propertyPath, newKeys);
    }

    public Seqable getPropertyPath() {
        return propertyPath;
    }

    public Seqable getKeySet() {
        return keySet;
    }

    private static final IFn apply_limited_to_keys = new AFn() {
        @Override
        public Object invoke(Object map, Object f, Object keys) {
            IFn fn = (IFn) f;
            Object input = select_keys.invoke(map, keys);
            Object output = fn.invoke(input);
            return merge.invoke(map, select_keys.invoke(output, keys));
        }
    };

    public Object swap(IFn f) {
        Object result = null;

        if (propertyPath == null) {
            if (keySet == null)
                result = swap.invoke(dataAtom, f);
            else
                result = swap.invoke(dataAtom, apply_limited_to_keys, f, keySet);
        } else {
            if (keySet == null)
                result = swap.invoke(dataAtom, update_in, propertyPath, f);
            else
                result = swap.invoke(dataAtom, update_in, propertyPath, apply_limited_to_keys, f, keySet);
        }

        return result;
    }


    private static class Listener implements InvalidationListener, WeakListener {

        private final WeakReference<ClojureProperty> propRef;

        public Listener(ClojureProperty prop) {
            this.propRef = new WeakReference<ClojureProperty>(prop);
        }

        @Override
        public void invalidated(Observable observable) {
            ClojureProperty prop = propRef.get();
            if (prop == null) {
                observable.removeListener(this);
            } else {
                prop.updateBinding();
            }
        }

        @Override
        public boolean wasGarbageCollected() {
            return propRef.get() == null;
        }
    }
}

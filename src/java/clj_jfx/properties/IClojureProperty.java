// Copyright (c) Gunnar Völkel. All rights reserved.
// The use and distribution terms for this software are covered by the
// Eclipse Public License 2.0 (http://www.eclipse.org/legal/epl-v20.html)
// which can be found in the file LICENSE at the root of this distribution.
// By using this software in any fashion, you are agreeing to be bound by
// the terms of this license.
// You must not remove this notice, or any other, from this software.

package clj_jfx.properties;

import clojure.lang.IFn;
import clojure.lang.IPersistentSet;
import clojure.lang.IPersistentVector;
import clojure.lang.Seqable;
import javafx.beans.property.Property;
import javafx.beans.value.ObservableValue;

public interface IClojureProperty extends ObservableValue<Object>, Property<Object> {

    IClojureProperty entryProperty(Seqable entryPath, Seqable keys);

    IClojureProperty limitToKeys(Seqable keys);

    Seqable getPropertyPath();
    Seqable getKeySet();

    Object swap(IFn f);
}

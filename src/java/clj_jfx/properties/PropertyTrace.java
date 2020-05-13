// Copyright (c) Gunnar Völkel. All rights reserved.
// The use and distribution terms for this software are covered by the
// Eclipse Public License 2.0 (http://www.eclipse.org/legal/epl-v20.html)
// which can be found in the file LICENSE at the root of this distribution.
// By using this software in any fashion, you are agreeing to be bound by
// the terms of this license.
// You must not remove this notice, or any other, from this software.

package clj_jfx.properties;

import clojure.lang.ISeq;
import clojure.lang.Seqable;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Vector;

public class PropertyTrace {

    private static ThreadLocal<LinkedList<ClojureProperty>> currentTrace = ThreadLocal.withInitial(LinkedList::new);

    public static synchronized void push(ClojureProperty property) {
        currentTrace.get().push(property);
    }

    public static synchronized ClojureProperty pop(ClojureProperty property) {
        if (currentTrace.get().peek() != property)
            throw new RuntimeException("Error while recording property currentTrace, tried to pop a property that is not ");

        return currentTrace.get().pop();
    }


    public static synchronized PropertyTrace currentTrace() {
        return new PropertyTrace(new Vector<>(currentTrace.get()));
    }


    private final Vector<ClojureProperty> propertyTrace;

    private PropertyTrace(Vector<ClojureProperty> propertyTrace) {
        this.propertyTrace = propertyTrace;
    }

    public List<ClojureProperty> getPropertyTrace() {
        return Collections.unmodifiableList(propertyTrace);
    }


    public String traceString() {
        StringBuilder sb = new StringBuilder();
        for (ClojureProperty prop : propertyTrace) {

            Seqable path = prop.getPropertyPath();
            sb.append( String.format("%X ", System.identityHashCode( prop.getBean() )) );
            sb.append("[");

            if (path != null && path.seq() != null ) {
                ISeq seq = path.seq();
                var segment = seq.first();
                sb.append(segment.toString());
                seq = seq.next();
                while (seq != null) {
                    segment = seq.first();
                    sb.append(", ");
                    sb.append(segment.toString());
                    seq = seq.next();
                }
            }
            sb.append(" ]\n");
        }
        return sb.toString();
    }

    public void printTrace() {
        System.out.println(traceString());
    }

}

// Copyright (c) Gunnar Völkel. All rights reserved.
// The use and distribution terms for this software are covered by the
// Eclipse Public License 2.0 (http://www.eclipse.org/legal/epl-v20.html)
// which can be found in the file LICENSE at the root of this distribution.
// By using this software in any fashion, you are agreeing to be bound by
// the terms of this license.
// You must not remove this notice, or any other, from this software.

package clj_jfx.table;

import clojure.lang.IFn;
import javafx.scene.control.TableCell;

public class TableCellWrapper<S, T> extends TableCell<S, T> {

    private final IFn updateItemFn;

    public TableCellWrapper(IFn updateItemFn) {
        this.updateItemFn = updateItemFn;

        setGraphic( null );
        setText( null );
    }

    @Override
    protected void updateItem(T item, boolean empty) {
        super.updateItem(item, empty);
        if( updateItemFn != null )
            updateItemFn.invoke(this, item, empty);
    }
}

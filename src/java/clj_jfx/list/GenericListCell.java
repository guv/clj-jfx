// Copyright (c) Gunnar Völkel. All rights reserved.
// The use and distribution terms for this software are covered by the
// Eclipse Public License 2.0 (http://www.eclipse.org/legal/epl-v20.html)
// which can be found in the file LICENSE at the root of this distribution.
// By using this software in any fashion, you are agreeing to be bound by
// the terms of this license.
// You must not remove this notice, or any other, from this software.

package clj_jfx.list;

import clojure.lang.IFn;
import javafx.event.EventHandler;
import javafx.scene.control.ListCell;
import javafx.scene.input.MouseEvent;
import javafx.util.StringConverter;


public class GenericListCell extends ListCell {


    private StringConverter converter;
    private IFn updateFn;
    private IFn clickHandler;

    public GenericListCell(StringConverter converter, IFn updateFn, IFn clickHandler) {
        this.converter = converter;
        this.updateFn = updateFn;
        this.clickHandler = clickHandler;
    }


    @Override
    protected void updateItem(Object item, boolean empty) {
        super.updateItem(item, empty);
        if( empty ) {
            setText(null);
            setOnMouseClicked(null);
        }else {
            setText(converter != null ? converter.toString(item) : item.toString());
            if( clickHandler != null )
                setOnMouseClicked(event -> clickHandler.invoke( event, item ));
        }

        if( updateFn != null )
            updateFn.invoke( this, item, empty );
    }
}

// Copyright (c) Gunnar Völkel. All rights reserved.
// The use and distribution terms for this software are covered by the
// Eclipse Public License 2.0 (http://www.eclipse.org/legal/epl-v20.html)
// which can be found in the file LICENSE at the root of this distribution.
// By using this software in any fashion, you are agreeing to be bound by
// the terms of this license.
// You must not remove this notice, or any other, from this software.

package clj_jfx.treetable;

import clojure.lang.IFn;
import javafx.beans.binding.Bindings;
import javafx.beans.property.Property;
import javafx.beans.value.ObservableValue;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.TreeTableCell;

import java.util.concurrent.Callable;

public class CustomTreeTableCell<S, T> extends TreeTableCell<S, T> {


    private Node cellNode;
    private Property cellNodeValueProp;
    private IFn valueConverter;

    private Property renderedProperty;
    private ObservableValue renderedObservable;


    public CustomTreeTableCell(Node cellNode, Property cellNodeValueProp) {
        this(cellNode, cellNodeValueProp, null);
    }


    public CustomTreeTableCell(Node cellNode, Property cellNodeValueProp, IFn valueConverter) {
        this.cellNode = cellNode;
        this.cellNodeValueProp = cellNodeValueProp;
        this.valueConverter = valueConverter;

        // do not show anything until the cell is non-empty
        setGraphic(null);
        setText(null);
        // align centered
        setAlignment(Pos.CENTER);
    }


    public Node getCellNode() {
        return cellNode;
    }


    @SuppressWarnings("unchecked")
    @Override
    public void updateItem(T item, boolean empty) {
        super.updateItem(item, empty);

        if (empty) {
            setText(null);
            setGraphic(null);
        } else {
            setText(null);
            setGraphic(cellNode);


            if (renderedProperty != null) {
                cellNodeValueProp.unbindBidirectional(renderedProperty);
                renderedProperty = null;
            }

            if (renderedObservable != null) {
                cellNodeValueProp.unbind();
                renderedObservable = null;
            }

            ObservableValue obsValue = getObservableValue();
            if (valueConverter != null) {
                ObservableValue source = obsValue;

                obsValue = Bindings.createObjectBinding(
                        (Callable) () -> valueConverter.invoke(source.getValue()),
                        obsValue);
            }

            if (obsValue instanceof Property) {
                renderedProperty = (Property<T>) obsValue;
                cellNodeValueProp.bindBidirectional(renderedProperty);
            } else {
                renderedObservable = obsValue;
                cellNodeValueProp.bind(renderedObservable);
            }


            if (cellNode!=null && !(cellNode instanceof Label))
                cellNode.disableProperty().bind(
                        Bindings.not(
                                getTreeTableView().editableProperty().and(
                                        getTableColumn().editableProperty()).and(
                                        editableProperty())
                        ));
        }
    }

    private ObservableValue getObservableValue() {
        return getTableColumn().getCellObservableValue(getIndex());
    }

}

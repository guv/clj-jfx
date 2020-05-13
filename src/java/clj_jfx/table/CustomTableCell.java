// Copyright (c) Gunnar Völkel. All rights reserved.
// The use and distribution terms for this software are covered by the
// Eclipse Public License 2.0 (http://www.eclipse.org/legal/epl-v20.html)
// which can be found in the file LICENSE at the root of this distribution.
// By using this software in any fashion, you are agreeing to be bound by
// the terms of this license.
// You must not remove this notice, or any other, from this software.

package clj_jfx.table;

import clojure.lang.IFn;
import javafx.beans.binding.Bindings;
import javafx.beans.property.Property;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ObservableValue;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;

import java.util.concurrent.Callable;

public class CustomTableCell<S, T> extends TableCell<S, T> {


    private Node cellNode;
    private Property cellNodeValueProp;
    private IFn valueConverter;

    private Property renderedProperty;
    private ObservableValue renderedObservable;

    private SimpleObjectProperty<Object> myValue = new SimpleObjectProperty<>(null);

    public CustomTableCell(Node cellNode, Property cellNodeValueProp) {
        this(cellNode, cellNodeValueProp, null);
    }


    public CustomTableCell(Node cellNode, Property cellNodeValueProp, IFn valueConverter) {
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
    private void undbindPrevious() {
        // unbind previous property (if any)
        if (renderedProperty != null) {
            cellNodeValueProp.unbindBidirectional(renderedProperty);
            renderedProperty = null;
        }
        // unbind previous observable (if any)
        if (renderedObservable != null) {
            cellNodeValueProp.unbind();
            renderedObservable = null;
        }
        // unbind value
        myValue.unbind();
        myValue.set(null);
    }

    @SuppressWarnings("unchecked")
    @Override
    public void updateItem(T item, boolean empty) {
        super.updateItem(item, empty);

        if (cellNode != null && !(cellNode instanceof Label) && !cellNode.disableProperty().isBound())
            cellNode.disableProperty().bind(
                    Bindings.not(
                            getTableView().editableProperty().and(
                                    getTableColumn().editableProperty()).and(
                                    editableProperty())
                    ));

        if (empty) {
            setText(null);
            setGraphic(null);
        } else {
            setText(null);
            setGraphic(cellNode);


            ObservableValue newRenderedObservable = getObservableValue();

            if (newRenderedObservable instanceof Property) {
                Property newRenderedProperty = (Property) newRenderedObservable;
                // need to adjust to property switch
                if (newRenderedProperty != renderedProperty) {
                    undbindPrevious();

                    // for value converter: setup conversion and bind unidirectional
                    // otherwise: bind bidirectional
                    if (valueConverter != null) {
                        var convertedObservable = Bindings.createObjectBinding(
                                (Callable) () -> valueConverter.invoke(newRenderedProperty.getValue()),
                                newRenderedProperty);

                        cellNodeValueProp.bind(convertedObservable);
                        myValue.bind(convertedObservable);
                        renderedObservable = convertedObservable;
                    } else {
                        cellNodeValueProp.bindBidirectional(newRenderedProperty);
                        myValue.bind(newRenderedProperty);
                        renderedProperty = newRenderedProperty;
                    }
                }
            }else{
                // bind to observable if needed
                if( newRenderedObservable != renderedObservable ) {
                    undbindPrevious();

                    if( valueConverter != null ) {
                        var sourceObservable = newRenderedObservable;
                        newRenderedObservable =  Bindings.createObjectBinding(
                                (Callable) () -> valueConverter.invoke(sourceObservable.getValue()),
                                newRenderedObservable);
                    }
                    cellNodeValueProp.bind(newRenderedObservable);
                    myValue.bind(newRenderedObservable);
                    renderedObservable = newRenderedObservable;
                }
            }
        }
    }

    private ObservableValue getObservableValue() {
        var column = getTableColumn();

        if( column == null)
            return null;

        return column.getCellObservableValue(getIndex());
    }


    public ObservableValue<Object> valueProperty() {
        return myValue;
    }

}

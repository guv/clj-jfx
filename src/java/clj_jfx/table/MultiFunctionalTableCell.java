// Copyright (c) Gunnar Völkel. All rights reserved.
// The use and distribution terms for this software are covered by the
// Eclipse Public License 2.0 (http://www.eclipse.org/legal/epl-v20.html)
// which can be found in the file LICENSE at the root of this distribution.
// By using this software in any fashion, you are agreeing to be bound by
// the terms of this license.
// You must not remove this notice, or any other, from this software.

package clj_jfx.table;

import clojure.java.api.Clojure;
import clojure.lang.IFn;
import clojure.lang.Keyword;
import javafx.beans.Observable;
import javafx.beans.binding.Bindings;
import javafx.beans.property.Property;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;

import java.io.PrintStream;
import java.util.Map;

public class MultiFunctionalTableCell<S, T> extends TableCell<S, T> {

    private IFn controlFactory;

    private int rowIndex = -1;
    private Node node;
    private Property nodeProperty;
    private Property renderedProperty;

    private final Keyword nodeKW = Keyword.intern("node");
    private final Keyword propertyKW = Keyword.intern("property");


    public MultiFunctionalTableCell(IFn controlFactory) {
        this.controlFactory = controlFactory;

        // do not show anything until the cell is non-empty
        setGraphic(null);
        setText(null);
        // align centered
        setAlignment(Pos.CENTER);
    }

    public int getRowIndex() {
        return getIndex();
    }

    public int getColumnIndex() {
        var column = getTableColumn();
        var tableView = getTableView();

        if (column != null && tableView != null) {
            return tableView.getColumns().indexOf(column);
        }
        // either no column or no tableview assigned
        return -2;
    }

    @SuppressWarnings("unchecked")
    @Override
    public void updateItem(T item, boolean empty) {
        super.updateItem(item, empty);

        if (empty) {
            Property newRenderedProperty = getProperty();
            if( newRenderedProperty == null ) {
                setText(null);
                setGraphic(null);
            }
        } else {
            int newRowIndex = getRowIndex();

            Property newRenderedProperty = getProperty();

            // row index changed?
            if (newRowIndex != rowIndex) {
                Map controlMap = (Map) controlFactory.invoke(newRowIndex, item);
                rowIndex = newRowIndex;

                Node newNode = (Node) controlMap.get(nodeKW);
                Property newNodeProperty = (Property) controlMap.get(propertyKW);

                // when node changed
                if (node != newNode) {
                    // clean up when node changed
                    if (node != null)
                        node.disableProperty().unbind();
                    if (renderedProperty != null)
                        nodeProperty.unbindBidirectional(renderedProperty);

                    // handle new rendered property
                    if (renderedProperty != newRenderedProperty)
                        renderedProperty = newRenderedProperty;

                    // assign new values
                    node = newNode;
                    nodeProperty = newNodeProperty;

                    // set node and bind property
                    setGraphic(node);
                    nodeProperty.bindBidirectional(renderedProperty);
                }
            }else{
                setGraphic(node);
            }

            // update rendered property on change
            if (renderedProperty != newRenderedProperty) {
                if (renderedProperty != null && nodeProperty != null)
                    nodeProperty.unbindBidirectional(renderedProperty);

                nodeProperty.bindBidirectional(newRenderedProperty);
                renderedProperty = newRenderedProperty;
            }

            // bind disable property if not done already
            if (node != null && !(node instanceof Label) && !node.disableProperty().isBound())
                node.disableProperty().bind(
                        Bindings.not(
                                getTableView().editableProperty().and(
                                        getTableColumn().editableProperty()).and(
                                        editableProperty())
                        ));
        }
    }

    private Property getProperty() {
        ObservableValue obs = getTableColumn().getCellObservableValue(getIndex());

        if ( obs != null && !(obs instanceof Property))
            throw new RuntimeException("The MultiFunctionalTableCell supports only properties (not observables) due to bidirectional binding.");

        return (Property) obs;
    }

}

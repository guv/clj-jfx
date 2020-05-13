// Copyright (c) Gunnar Völkel. All rights reserved.
// The use and distribution terms for this software are covered by the
// Eclipse Public License 2.0 (http://www.eclipse.org/legal/epl-v20.html)
// which can be found in the file LICENSE at the root of this distribution.
// By using this software in any fashion, you are agreeing to be bound by
// the terms of this license.
// You must not remove this notice, or any other, from this software.

package clj_jfx.table;

import clojure.lang.IPersistentMap;
import clojure.lang.IPersistentVector;
import clojure.lang.Keyword;
import clojure.lang.PersistentHashMap;
import javafx.beans.InvalidationListener;
import javafx.beans.binding.Bindings;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.Property;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.ColorPicker;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TableCell;
import javafx.scene.paint.Color;

import java.util.List;
import java.util.Map;

public class ComboBoxSelectionTableCell<S,T> extends TableCell<S,T> implements ChangeListener<IPersistentMap> {

    private final Keyword valueListKey;
    // private final Keyword selectedIndexKey;

    private final ComboBox<String> comboBox;

    private Property<IPersistentMap> renderedProperty;

    private Keyword selectionEnabledKey;

    public ComboBoxSelectionTableCell(Keyword valueListKey, Keyword selectedIndexKey, String selectionPrompt) {

        this.valueListKey = valueListKey;
        // this.selectedIndexKey = selectedIndexKey;

        comboBox = new ComboBox<String>();
        comboBox.setMaxSize( Double.MAX_VALUE, Double.MAX_VALUE );

        if( selectionPrompt != null )
            comboBox.setPromptText( selectionPrompt );

        comboBox.getSelectionModel().selectedIndexProperty().addListener(
                (observable, oldValue, newValue) -> {
                    // selected index changed
                    // is there a rendered property?
                    if( renderedProperty != null ) {
                        // update selected index in property value
                        IPersistentMap map = renderedProperty.getValue();
                        renderedProperty.setValue( map.assoc( selectedIndexKey, newValue ));
                    }
                }
        );

        this.setPadding( Insets.EMPTY );


        // do not show anything until the cell is non-empty
        setGraphic(null);
        setText(null);

        setAlignment( Pos.CENTER_LEFT );
    }


    public void setSelectionEnabledKey(Keyword selectionEnabledKey) {
        this.selectionEnabledKey = selectionEnabledKey;
    }



    @Override
    public void updateItem(T item, boolean empty) {
        super.updateItem(item, empty);

        if (empty) {
            setText(null);
            setGraphic(null);
        } else {
//            setGraphic(colorPicker);
//
//
//            if ( renderedProperty != null ) {
//                colorPicker.valueProperty().unbindBidirectional(renderedProperty);
//            }
        }

        if( renderedProperty != null ) {
            // remove this cell from listeners of the old property
            renderedProperty.removeListener( this );
        }

        ObservableValue obsValue = getCellProperty();
        if (obsValue instanceof Property) {

            renderedProperty = (Property<IPersistentMap>)obsValue;

            renderedProperty.addListener( this );

            IPersistentMap value = renderedProperty.getValue();

            changed( renderedProperty, null, value);
        }

    }


    private ObservableValue getCellProperty() {
        return getTableColumn().getCellObservableValue(getIndex());
    }

    @Override
    public void changed(ObservableValue<? extends IPersistentMap> observable, IPersistentMap oldValue, IPersistentMap newValue) {
        List<String> values = (List<String>)newValue.valAt( valueListKey );

        if( values.size() == 1 ) {
            setGraphic( null );
            setText( values.get(0) );
        }else{
            comboBox.setItems( FXCollections.observableArrayList( values ) );

            if( selectionEnabledKey != null ) {
                Object obj = newValue.valAt( selectionEnabledKey );
                boolean enabled = obj == Boolean.TRUE;

                comboBox.setDisable( !enabled );
            }

            setGraphic( comboBox );
            setText( null );
        }
    }
}

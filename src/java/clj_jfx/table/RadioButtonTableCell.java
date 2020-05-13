// Copyright (c) Gunnar Völkel. All rights reserved.
// The use and distribution terms for this software are covered by the
// Eclipse Public License 2.0 (http://www.eclipse.org/legal/epl-v20.html)
// which can be found in the file LICENSE at the root of this distribution.
// By using this software in any fashion, you are agreeing to be bound by
// the terms of this license.
// You must not remove this notice, or any other, from this software.

package clj_jfx.table;

import javafx.beans.property.Property;
import javafx.beans.binding.Bindings;
import javafx.beans.value.ObservableValue;
import javafx.geometry.Pos;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.RadioButton;
import javafx.scene.control.TableCell;
import javafx.util.Callback;
import javafx.util.StringConverter;

public class RadioButtonTableCell<S,T> extends TableCell<S,T> {

    private final RadioButton radioButton;
    
    private Property<Boolean> renderedProperty;


    public RadioButtonTableCell() {

        radioButton = new RadioButton();
        radioButton.setContentDisplay( ContentDisplay.GRAPHIC_ONLY );

        // do not show anything until the cell is non-empty
        setGraphic(null);
        setText(null);

        setAlignment( Pos.CENTER );
    }

    public RadioButton getRadioButton(){
        return radioButton;
    }

    @Override
    public void updateItem(T item, boolean empty) {
        super.updateItem(item, empty);
        
        if (empty) {
            setText(null);
            setGraphic(null);
        } else {
            setGraphic(radioButton);


            if ( renderedProperty != null ) {
                radioButton.selectedProperty().unbindBidirectional(renderedProperty);
            }

            ObservableValue obsValue = getSelectedProperty();
            if (obsValue instanceof Property) {
                renderedProperty = (Property)obsValue;
                radioButton.selectedProperty().bindBidirectional(renderedProperty);
            }
            
            radioButton.disableProperty().bind(
                    Bindings.not(getTableView().editableProperty().and(
                    getTableColumn().editableProperty()).and(
                    editableProperty())
                ));
        }
    }
    
    private ObservableValue getSelectedProperty() {
        return getTableColumn().getCellObservableValue(getIndex());
    }
}

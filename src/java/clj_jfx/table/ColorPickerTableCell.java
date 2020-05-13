// Copyright (c) Gunnar Völkel. All rights reserved.
// The use and distribution terms for this software are covered by the
// Eclipse Public License 2.0 (http://www.eclipse.org/legal/epl-v20.html)
// which can be found in the file LICENSE at the root of this distribution.
// By using this software in any fashion, you are agreeing to be bound by
// the terms of this license.
// You must not remove this notice, or any other, from this software.

package clj_jfx.table;

import javafx.beans.binding.Bindings;
import javafx.beans.property.Property;
import javafx.beans.value.ObservableValue;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.ColorPicker;
import javafx.scene.control.TableCell;
import javafx.scene.paint.Color;


public class ColorPickerTableCell<S> extends TableCell<S, Color> {

    private final ColorPicker colorPicker;

    private Property<Color> renderedProperty;

    public ColorPickerTableCell() {

        colorPicker = new ColorPicker();
        colorPicker.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        this.setPadding(Insets.EMPTY);

        // do not show anything until the cell is non-empty
        setGraphic(null);
        setText(null);

        setAlignment(Pos.CENTER);
    }


    @Override
    public void updateItem(Color item, boolean empty) {
        super.updateItem(item, empty);

        if (empty) {
            setText(null);
            setGraphic(null);
        } else {
            setGraphic(colorPicker);

            if (renderedProperty != null) {
                colorPicker.valueProperty().unbindBidirectional(renderedProperty);
            }
        }

        ObservableValue<Color> obsValue = getCellProperty();
        if (obsValue instanceof Property) {
            renderedProperty = (Property<Color>) obsValue;
            colorPicker.valueProperty().bindBidirectional(renderedProperty);
        }

        colorPicker.disableProperty().bind(
                Bindings.not(getTableView().editableProperty().and(
                        getTableColumn().editableProperty()).and(
                        editableProperty())
                ));
    }


    private ObservableValue<Color> getCellProperty() {
        return getTableColumn().getCellObservableValue(getIndex());
    }

}

// Copyright (c) Gunnar Völkel. All rights reserved.
// The use and distribution terms for this software are covered by the
// Eclipse Public License 2.0 (http://www.eclipse.org/legal/epl-v20.html)
// which can be found in the file LICENSE at the root of this distribution.
// By using this software in any fashion, you are agreeing to be bound by
// the terms of this license.
// You must not remove this notice, or any other, from this software.

package clj_jfx.combobox;

import clojure.lang.IFn;
import clojure.lang.IPersistentMap;
import com.sun.javafx.scene.control.behavior.ComboBoxBaseBehavior;
import javafx.scene.Node;
import javafx.scene.control.ComboBoxBase;
import javafx.scene.control.TextField;
import clj_jfx.combobox.internal.JFXComboBoxPopupControl;
import javafx.util.StringConverter;

public class CustomComboBoxSkin extends JFXComboBoxPopupControl<Object> {

    private final IFn getPopupContentFn;
    private final IFn getDisplayNodeFn;
    private final IFn computePrefWidthFn;
    private final IFn computePrefHeightFn;

    private final CustomComboBoxBehavior behavior;

    public CustomComboBoxSkin(ComboBoxBase<Object> comboBox, IFn getPopupContentFn, IFn getDisplayNodeFn, IFn computePrefWidthFn, IFn computePrefHeightFn, IPersistentMap handlerMap) {
        super(comboBox);
        this.getPopupContentFn = getPopupContentFn;
        this.getDisplayNodeFn = getDisplayNodeFn;
        this.computePrefWidthFn = computePrefWidthFn;
        this.computePrefHeightFn = computePrefHeightFn;
        this.behavior = new CustomComboBoxBehavior(comboBox, handlerMap);
    }

    @Override
    protected double computePrefWidth(double height, double topInset, double rightInset, double bottomInset, double leftInset) {
        return (Double) computePrefWidthFn.invoke( height, topInset, rightInset, bottomInset, leftInset );
    }

    @Override
    protected double computePrefHeight(double width, double topInset, double rightInset, double bottomInset, double leftInset) {
        return (Double) computePrefHeightFn.invoke( width, topInset, rightInset, bottomInset, leftInset );
    }

    @Override
    protected Node getPopupContent() {
        return (Node) getPopupContentFn.invoke();
    }

    @Override
    public Node getDisplayNode() {
        return (Node) getDisplayNodeFn.invoke();
    }

    @Override
    protected TextField getEditor() {
        return null;
    }

    @Override
    protected StringConverter<Object> getConverter() {
        return null;
    }

    @Override
    protected ComboBoxBaseBehavior getMyBehavior() {
        return behavior;
    }


}

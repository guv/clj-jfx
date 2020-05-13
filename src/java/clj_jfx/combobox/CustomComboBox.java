// Copyright (c) Gunnar Völkel. All rights reserved.
// The use and distribution terms for this software are covered by the
// Eclipse Public License 2.0 (http://www.eclipse.org/legal/epl-v20.html)
// which can be found in the file LICENSE at the root of this distribution.
// By using this software in any fashion, you are agreeing to be bound by
// the terms of this license.
// You must not remove this notice, or any other, from this software.

package clj_jfx.combobox;

import clojure.lang.IFn;
import javafx.scene.control.ComboBoxBase;
import javafx.scene.control.Skin;

public class CustomComboBox extends ComboBoxBase<Object> {

    private final IFn createDefaultSkinFn;

    public CustomComboBox(IFn createDefaultSkinFn) {
        this.createDefaultSkinFn = createDefaultSkinFn;
    }

    @Override
    protected Skin<?> createDefaultSkin() {
        return (Skin<?>) createDefaultSkinFn.invoke(this);
    }
}

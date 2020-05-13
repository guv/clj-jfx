// Copyright (c) Gunnar Völkel. All rights reserved.
// The use and distribution terms for this software are covered by the
// Eclipse Public License 2.0 (http://www.eclipse.org/legal/epl-v20.html)
// which can be found in the file LICENSE at the root of this distribution.
// By using this software in any fashion, you are agreeing to be bound by
// the terms of this license.
// You must not remove this notice, or any other, from this software.

package clj_jfx.combobox;

import clojure.lang.AFn;
import clojure.lang.IFn;
import clojure.lang.IPersistentMap;
import clojure.lang.Keyword;
import com.sun.javafx.scene.control.behavior.ComboBoxBaseBehavior;
import javafx.scene.control.ComboBoxBase;
import javafx.scene.control.PopupControl;
import javafx.scene.input.MouseEvent;

public class CustomComboBoxBehavior extends ComboBoxBaseBehavior<Object> {


    private final IFn mousePressedHandler;
    private final IFn mouseReleasedHandler;
    private final IFn mouseEnteredHandler;
    private final IFn mouseExitedHandler;
    private final IFn autoHideHandler;

    private final IFn defaultMousePressedHandler = new AFn() {
        @Override
        public Object invoke(Object e) {
            CustomComboBoxBehavior.super.mousePressed((MouseEvent) e);
            return null;
        }
    };

    private final IFn defaultMouseReleasedHandler = new AFn() {
        @Override
        public Object invoke(Object e) {
            CustomComboBoxBehavior.super.mouseReleased((MouseEvent) e);
            return null;
        }
    };

    private final IFn defaultMouseEnteredHandler = new AFn() {
        @Override
        public Object invoke(Object e) {
            CustomComboBoxBehavior.super.mouseEntered((MouseEvent) e);
            return null;
        }
    };

    private final IFn defaultMouseExitedHandler = new AFn() {
        @Override
        public Object invoke(Object e) {
            CustomComboBoxBehavior.super.mouseExited((MouseEvent) e);
            return null;
        }
    };

    private final IFn defaultAutoHideHandler = new AFn() {
        @Override
        public Object invoke(Object pc) {
            CustomComboBoxBehavior.super.onAutoHide((PopupControl) pc);
            return null;
        }
    };


    public CustomComboBoxBehavior(ComboBoxBase<Object> comboBox, IPersistentMap handlerMap) {
        super(comboBox);

        mousePressedHandler = (IFn) handlerMap.valAt(Keyword.intern("mouse-pressed"));
        mouseReleasedHandler = (IFn) handlerMap.valAt(Keyword.intern("mouse-released"));
        mouseEnteredHandler = (IFn) handlerMap.valAt(Keyword.intern("mouse-entered"));
        mouseExitedHandler = (IFn) handlerMap.valAt(Keyword.intern("mouse-exited"));
        autoHideHandler = (IFn) handlerMap.valAt(Keyword.intern("auto-hide"));
    }


    private static void callHandler(final IFn handler, final IFn defautHandler, Object arg) {
        if (handler != null)
            handler.invoke(defautHandler, arg);
        else
            defautHandler.invoke(arg);
    }

    @Override
    public void mousePressed(MouseEvent e) {
        callHandler(mousePressedHandler, defaultMousePressedHandler, e);
    }

    @Override
    public void mouseReleased(MouseEvent e) {
        callHandler(mouseReleasedHandler, defaultMouseReleasedHandler, e);
    }

    @Override
    public void mouseEntered(MouseEvent e) {
        callHandler(mouseEnteredHandler, defaultMouseEnteredHandler, e);
    }

    @Override
    public void mouseExited(MouseEvent e) {
        callHandler(mouseExitedHandler, defaultMouseExitedHandler, e);
    }

    @Override
    public void onAutoHide(PopupControl popup) {
        if (autoHideHandler != null)
            autoHideHandler.invoke(
                    defaultAutoHideHandler,
                    getNode(),
                    popup);
        else
            defaultAutoHideHandler.invoke(popup);
    }
}

/*
 * Copyright (c) 2010, 2018, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package clj_jfx.combobox;

import javafx.scene.shape.Rectangle;
import javafx.geometry.Pos;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;

public class PickerColorBox extends StackPane {

    private Rectangle colorRect;

    public PickerColorBox(Rectangle colorRect){
        this.colorRect = colorRect;;
    }

    @Override protected void layoutChildren() {
        final double top = snappedTopInset();
        final double left = snappedLeftInset();
        final double width = getWidth();
        final double height = getHeight();
        final double right = snappedRightInset();
        final double bottom = snappedBottomInset();
        colorRect.setX(snapPosition(colorRect.getX()));
        colorRect.setY(snapPosition(colorRect.getY()));
        colorRect.setWidth(snapSize(colorRect.getWidth()));
        colorRect.setHeight(snapSize(colorRect.getHeight()));
        if (getChildren().size() == 2) {
            final ImageView icon = (ImageView) getChildren().get(1);
            Pos childAlignment = StackPane.getAlignment(icon);
            layoutInArea(icon, left, top,
                    width - left - right, height - top - bottom,
                    0, getMargin(icon),
                    childAlignment != null? childAlignment.getHpos() : getAlignment().getHpos(),
                    childAlignment != null? childAlignment.getVpos() : getAlignment().getVpos());
            colorRect.setLayoutX(icon.getLayoutX());
            colorRect.setLayoutY(icon.getLayoutY());
        } else {
            Pos childAlignment = StackPane.getAlignment(colorRect);
            layoutInArea(colorRect, left, top,
                    width - left - right, height - top - bottom,
                    0, getMargin(colorRect),
                    childAlignment != null? childAlignment.getHpos() : getAlignment().getHpos(),
                    childAlignment != null? childAlignment.getVpos() : getAlignment().getVpos());
        }
    }
}

/*
 * Copyright (c) 2016 Vivid Solutions.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * and Eclipse Distribution License v. 1.0 which accompanies this distribution.
 * The Eclipse Public License is available at http://www.eclipse.org/legal/epl-v20.html
 * and the Eclipse Distribution License is available at
 *
 * http://www.eclipse.org/org/documents/edl-v10.php.
 */

package org.locationtech.jtstest.testbuilder;

import java.awt.Cursor;
import java.awt.GraphicsEnvironment;
import java.awt.Point;
import java.awt.Toolkit;

import javax.swing.ImageIcon;


public class AppCursors
{
  public static Cursor DRAW_GEOM = customOrDefault(
      "DrawCursor.png", new Point(4, 26), "Draw");

  public static Cursor EDIT_VERTEX = customOrDefault(
      "MoveVertexCursor.gif", new Point(16, 16), "MoveVertex");

  public static Cursor HAND = customOrDefault(
      "Hand.gif", new Point(7, 7), "Pan");

  public static Cursor ZOOM = customOrDefault(
      "MagnifyCursor.gif", new Point(16, 16), "Zoom In");

  /**
   * Headless CI (and other no-display hosts) cannot create custom
   * cursors; fall back to the default arrow so tool singletons still
   * construct for existence / wiring tests.
   */
  private static Cursor customOrDefault(String icon, Point hotSpot, String name) {
    if (GraphicsEnvironment.isHeadless()) {
      return Cursor.getDefaultCursor();
    }
    ImageIcon img = AppIcons.load(icon);
    return Toolkit.getDefaultToolkit().createCustomCursor(
        img.getImage(), hotSpot, name);
  }

}

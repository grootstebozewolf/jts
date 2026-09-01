/*
 * Copyright (c) 2026 grootstebozewolf
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * and Eclipse Distribution License v. 1.0 which accompanies this distribution.
 * The Eclipse Public License is available at http://www.eclipse.org/legal/epl-v20.html
 * and the Eclipse Distribution License is available at
 *
 * http://www.eclipse.org/org/documents/edl-v10.php.
 */
package org.locationtech.jts.geom.util;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.curve.CircularString;
import org.locationtech.jts.geom.curve.CurveGeometryFactory;
import org.locationtech.jts.io.ParseException;
import org.locationtech.jts.io.curve.CurveWKTReader;

import junit.textui.TestRunner;
import test.jts.GeometryTestCase;

/**
 * {@link GeometryEditor.CoordinateOperation} must rebuild
 * {@link CircularString} via {@code createCircularString}, not
 * {@code createLineString}. {@code CircularString extends LineString},
 * so the old instanceof path silently flattened.
 */
public class GeometryEditorCircularStringTest extends GeometryTestCase {

  public static void main(String[] args) {
    TestRunner.run(GeometryEditorCircularStringTest.class);
  }

  public GeometryEditorCircularStringTest(String name) {
    super(name);
  }

  public void testCoordinateOperationKeepsCircularString() throws ParseException {
    Geometry g = new CurveWKTReader(new CurveGeometryFactory()).read(
        "CIRCULARSTRING (60 380, 240 440, 404 326, 310 200, 634 185)");
    GeometryEditor editor = new GeometryEditor();
    Geometry moved = editor.edit(g, new GeometryEditor.CoordinateOperation() {
      public Coordinate[] edit(Coordinate[] coords, Geometry geometry) {
        Coordinate[] copy = new Coordinate[coords.length];
        for (int i = 0; i < coords.length; i++) {
          copy[i] = coords[i].copy();
        }
        copy[copy.length - 1] = new Coordinate(620, 210);
        return copy;
      }
    });
    assertTrue(moved instanceof CircularString);
    assertFalse(moved.getClass().equals(LineString.class));
    assertTrue(moved.getCoordinates()[4].equals2D(new Coordinate(620, 210)));
  }
}

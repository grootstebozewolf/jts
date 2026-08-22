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
import org.locationtech.jts.geom.curve.CompoundCurve;
import org.locationtech.jts.geom.curve.CurveGeometryFactory;
import org.locationtech.jts.io.ParseException;
import org.locationtech.jts.io.curve.CurveWKTReader;

import junit.textui.TestRunner;
import test.jts.GeometryTestCase;

/**
 * {@link GeometryEditor} must edit CompoundCurve members in place, not
 * rebuild the concatenated polyline as {@code LINESTRING}.
 */
public class GeometryEditorCompoundCurveTest extends GeometryTestCase {

  public static void main(String[] args) {
    TestRunner.run(GeometryEditorCompoundCurveTest.class);
  }

  public GeometryEditorCompoundCurveTest(String name) {
    super(name);
  }

  public void testCoordinateOperationKeepsCompoundCurveMembers() throws ParseException {
    Geometry g = new CurveWKTReader(new CurveGeometryFactory()).read(
        "COMPOUNDCURVE (CIRCULARSTRING (110 350, 110 363, 190 650), "
            + "CIRCULARSTRING (190 650, 200 652, 560 650), "
            + "CIRCULARSTRING (560 650, 545 342, 277 455))");
    GeometryEditor editor = new GeometryEditor();
    Geometry moved = editor.edit(g, new GeometryEditor.CoordinateOperation() {
      public Coordinate[] edit(Coordinate[] coords, Geometry geometry) {
        Coordinate[] copy = new Coordinate[coords.length];
        for (int i = 0; i < coords.length; i++) {
          copy[i] = coords[i].equals2D(new Coordinate(277, 455))
              ? new Coordinate(260, 440)
              : coords[i].copy();
        }
        return copy;
      }
    });
    assertTrue(moved instanceof CompoundCurve);
    assertFalse(moved.getClass().equals(LineString.class));
    CompoundCurve cc = (CompoundCurve) moved;
    assertEquals(3, cc.getNumMembers());
    assertTrue(cc.getMemberN(2) instanceof CircularString);
    assertTrue(cc.getMemberN(2).getCoordinates()[2].equals2D(new Coordinate(260, 440)));
  }
}

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
package org.locationtech.jtstest.testbuilder.geom;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.curve.CircularString;
import org.locationtech.jts.geom.curve.CurveGeometryFactory;
import org.locationtech.jts.io.ParseException;
import org.locationtech.jts.io.curve.CurveWKTReader;
import org.locationtech.jts.io.curve.CurveWKTWriter;
import org.locationtech.jtstest.testbuilder.model.GeometryEditModel;
import org.locationtech.jtstest.testbuilder.model.TestCaseEdit;

import junit.framework.TestCase;
import junit.textui.TestRunner;

/**
 * UX RC0: Move-vertex on a 5-control {@code CIRCULARSTRING} must not
 * flatten to {@code LINESTRING}. Same path as
 * {@code EditVertexTool.mouseReleased} → {@link GeometryEditModel#moveVertex}.
 * <p>
 * Witness from UX: last control {@code (634 185)} nudged to
 * {@code (620 210)}.
 */
public class GeometryVertexMoverCircularStringTest extends TestCase {

  private static final String INPUT =
      "CIRCULARSTRING (60 380, 240 440, 404 326, 310 200, 634 185)";
  private static final Coordinate FROM = new Coordinate(634, 185);
  private static final Coordinate TO = new Coordinate(620, 210);

  public GeometryVertexMoverCircularStringTest(String name) {
    super(name);
  }

  public static void main(String[] args) {
    TestRunner.run(GeometryVertexMoverCircularStringTest.class);
  }

  public void testMoveLastControlKeepsCircularString() throws ParseException {
    Geometry g = read(INPUT);
    assertTrue(g instanceof CircularString);

    Geometry moved = GeometryVertexMover.move(g, FROM, TO);

    assertTrue("EditVertex must not flatten CIRCULARSTRING to LINESTRING, got "
        + moved.getClass().getName() + " " + write(moved),
        moved instanceof CircularString);
    assertFalse(moved.getClass().equals(LineString.class));
    assertEquals(5, moved.getNumPoints());
    assertTrue(moved.getCoordinates()[4].equals2D(TO));
    String wkt = write(moved);
    assertTrue("must still emit CIRCULARSTRING, got " + wkt,
        wkt.startsWith("CIRCULARSTRING"));
    assertFalse(wkt.startsWith("LINESTRING"));
  }

  public void testGeometryEditModelMoveVertexKeepsCircularString() throws ParseException {
    GeometryEditModel model = new GeometryEditModel();
    model.setTestCase(new TestCaseEdit(new CurveGeometryFactory().getPrecisionModel()));
    model.setGeometry(read(INPUT));

    model.moveVertex(FROM, TO);

    Geometry moved = model.getGeometry();
    assertTrue(moved instanceof CircularString);
    assertFalse(moved.getClass().equals(LineString.class));
    assertTrue(moved.getCoordinates()[4].equals2D(TO));
  }

  private static Geometry read(String wkt) throws ParseException {
    return new CurveWKTReader(new CurveGeometryFactory()).read(wkt);
  }

  private static String write(Geometry g) {
    return new CurveWKTWriter().write(g);
  }
}

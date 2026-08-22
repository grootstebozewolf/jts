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
import org.locationtech.jts.geom.curve.CompoundCurve;
import org.locationtech.jts.geom.curve.CurveGeometryFactory;
import org.locationtech.jts.io.ParseException;
import org.locationtech.jts.io.curve.CurveWKTReader;
import org.locationtech.jts.io.curve.CurveWKTWriter;
import org.locationtech.jtstest.testbuilder.model.GeometryEditModel;
import org.locationtech.jtstest.testbuilder.model.TestCaseEdit;

import junit.framework.TestCase;
import junit.textui.TestRunner;

/**
 * UX RC1: Move-vertex on a COMPOUNDCURVE of CircularString members
 * must not flatten to LINESTRING (issue #80). Same path as
 * {@code EditVertexTool.mouseReleased} → {@link GeometryEditModel#moveVertex}.
 */
public class GeometryVertexMoverCompoundCurveTest extends TestCase {

  private static final String INPUT =
      "COMPOUNDCURVE (CIRCULARSTRING (110 350, 110 363, 190 650), "
          + "CIRCULARSTRING (190 650, 200 652, 560 650), "
          + "CIRCULARSTRING (560 650, 545 342, 277 455))";
  private static final Coordinate FROM = new Coordinate(277, 455);
  private static final Coordinate TO = new Coordinate(260, 440);

  public GeometryVertexMoverCompoundCurveTest(String name) {
    super(name);
  }

  public static void main(String[] args) {
    TestRunner.run(GeometryVertexMoverCompoundCurveTest.class);
  }

  public void testMoveLastControlKeepsCompoundCurveMembers() throws ParseException {
    Geometry g = read(INPUT);
    assertTrue(g instanceof CompoundCurve);

    Geometry moved = GeometryVertexMover.move(g, FROM, TO);

    assertTrue("EditVertex must not flatten COMPOUNDCURVE to LINESTRING, got "
        + moved.getClass().getName() + " " + write(moved),
        moved instanceof CompoundCurve);
    assertFalse(moved.getClass().equals(LineString.class));
    CompoundCurve cc = (CompoundCurve) moved;
    assertEquals(3, cc.getNumMembers());
    assertTrue(cc.getMemberN(0) instanceof CircularString);
    assertTrue(cc.getMemberN(1) instanceof CircularString);
    assertTrue(cc.getMemberN(2) instanceof CircularString);
    assertTrue(cc.getMemberN(2).getCoordinates()[2].equals2D(TO));
    String wkt = write(moved);
    assertTrue("got " + wkt, wkt.startsWith("COMPOUNDCURVE (CIRCULARSTRING"));
    assertFalse(wkt.startsWith("LINESTRING"));
  }

  public void testGeometryEditModelMoveVertexKeepsCompoundCurve() throws ParseException {
    GeometryEditModel model = new GeometryEditModel();
    model.setTestCase(new TestCaseEdit(new CurveGeometryFactory().getPrecisionModel()));
    model.setGeometry(read(INPUT));
    model.moveVertex(FROM, TO);
    Geometry moved = model.getGeometry();
    assertTrue(moved instanceof CompoundCurve);
    assertFalse(moved.getClass().equals(LineString.class));
    assertTrue(((CompoundCurve) moved).getMemberN(2).getCoordinates()[2].equals2D(TO));
  }

  private static Geometry read(String wkt) throws ParseException {
    return new CurveWKTReader(new CurveGeometryFactory()).read(wkt);
  }

  private static String write(Geometry g) {
    return new CurveWKTWriter().write(g);
  }
}

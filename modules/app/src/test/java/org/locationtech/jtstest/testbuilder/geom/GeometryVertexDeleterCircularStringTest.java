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
import org.locationtech.jts.geom.GeometryCollection;
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
 * UX issue #97: Ctrl-right-click delete on a CircularString must
 * remove two controls so the count stays odd (inverse of insert +2).
 * A one-point delete of a 5-control CS becomes 4 and draws a dangling
 * leftover vertex — the phantom in RC3 screenshot
 * {@code CIRCULARSTRING (114 286, 300 400, 520 60, 790 180, 740 320, 584 294)}.
 */
public class GeometryVertexDeleterCircularStringTest extends TestCase {

  /** 5-control two-arc CS. */
  private static final String FIVE =
      "CIRCULARSTRING (0 0, 1 1, 2 0, 3 -1, 4 0)";

  public GeometryVertexDeleterCircularStringTest(String name) {
    super(name);
  }

  public static void main(String[] args) {
    TestRunner.run(GeometryVertexDeleterCircularStringTest.class);
  }

  public void testDeleteLastControlStaysOddThree() throws ParseException {
    Geometry g = read(FIVE);
    Geometry result = deleteAt(g, new Coordinate(4, 0));
    assertCircularOdd(result, 3);
    assertFalse(contains(result, new Coordinate(4, 0)));
    assertFalse(contains(result, new Coordinate(3, -1)));
  }

  public void testDeleteJoinStaysOddThree() throws ParseException {
    Geometry g = read(FIVE);
    Geometry result = deleteAt(g, new Coordinate(2, 0));
    assertCircularOdd(result, 3);
    assertFalse(contains(result, new Coordinate(2, 0)));
  }

  public void testDeleteMidStaysOddThree() throws ParseException {
    Geometry g = read(FIVE);
    Geometry result = deleteAt(g, new Coordinate(1, 1));
    assertCircularOdd(result, 3);
    assertFalse(contains(result, new Coordinate(1, 1)));
  }

  public void testDeleteOnThreePointArcRefuses() throws ParseException {
    Geometry g = read("CIRCULARSTRING (0 0, 1 1, 2 0)");
    Geometry result = deleteAt(g, new Coordinate(1, 1));
    assertCircularOdd(result, 3);
    assertTrue(contains(result, new Coordinate(1, 1)));
  }

  /**
   * RC3 witness shape class: 7 controls → delete last → 5, not 6.
   */
  public void testDeleteLastOfSevenIsFiveNotSix() throws ParseException {
    Geometry g = read(
        "CIRCULARSTRING (114 286, 300 400, 520 60, 790 180, 740 320, 650 250, 584 294)");
    Geometry result = deleteAt(g, new Coordinate(584, 294));
    assertCircularOdd(result, 5);
    assertFalse("phantom leftover 584 294 must be gone",
        contains(result, new Coordinate(584, 294)));
    String wkt = write(result);
    assertFalse("must not keep even leftover, got " + wkt, wkt.contains("584 294"));
  }

  public void testGeometryCollectionDeleteKeepsWrapper() throws ParseException {
    Geometry g = read("GEOMETRYCOLLECTION (" + FIVE + ")");
    GeometryLocation loc = GeometryPointLocater.locateVertex(g,
        new Coordinate(4, 0), 0.01);
    assertNotNull(loc);
    Geometry result = loc.delete();
    assertTrue(result instanceof GeometryCollection
        && !(result instanceof LineString));
    assertTrue(result.getGeometryN(0) instanceof CircularString);
    assertEquals(3, result.getGeometryN(0).getNumPoints());
  }

  public void testGeometryEditModelDelete() throws ParseException {
    GeometryEditModel model = new GeometryEditModel();
    model.setTestCase(new TestCaseEdit(new CurveGeometryFactory().getPrecisionModel()));
    model.setGeometry(read(FIVE));
    GeometryLocation loc = model.locateVertex(new Coordinate(4, 0), 0.01);
    assertNotNull(loc);
    model.setGeometry(loc.delete());
    Geometry result = model.getGeometry();
    assertCircularOdd(result, 3);
  }

  private static Geometry deleteAt(Geometry g, Coordinate vertex)
      throws ParseException {
    GeometryLocation loc = GeometryPointLocater.locateVertex(g, vertex, 0.01);
    assertNotNull("missed vertex " + vertex, loc);
    return loc.delete();
  }

  private static void assertCircularOdd(Geometry g, int n) {
    assertTrue("got " + g.getClass().getName() + " " + write(g),
        g instanceof CircularString);
    assertFalse(g.getClass().equals(LineString.class));
    assertEquals("control count must stay odd, got " + g.getNumPoints()
        + " " + write(g), n, g.getNumPoints());
    assertEquals(1, g.getNumPoints() % 2);
  }

  private static boolean contains(Geometry g, Coordinate pt) {
    Coordinate[] coords = g.getCoordinates();
    for (int i = 0; i < coords.length; i++) {
      if (coords[i].equals2D(pt)) {
        return true;
      }
    }
    return false;
  }

  private static Geometry read(String wkt) throws ParseException {
    return new CurveWKTReader(new CurveGeometryFactory()).read(wkt);
  }

  private static String write(Geometry g) {
    return new CurveWKTWriter().write(g);
  }
}

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
 * UX issue #82: two-click insert on a {@code GEOMETRYCOLLECTION} of one
 * {@code CIRCULARSTRING}. ISO/IEC 13249-3: control count (WKT tokens)
 * stays odd and &ge; 3. First click does not write. Second click
 * commits the two click controls (+2). Not #83 chord-mid. Never even,
 * never flatten, never coincident consecutive.
 */
public class GeometryVertexInserterCircularStringTest extends TestCase {

  private static final String INPUT =
      "GEOMETRYCOLLECTION (CIRCULARSTRING (172 410, 180 398, 190 380, 196 370, "
          + "210 349, 225 329, 237 314, 258 284, 279 264, 299 245, 311 237, "
          + "330 225, 361 215, 371 221, 387 232, 395 238, 406 248, 413 256, "
          + "510 330))";

  /** Midpoint of the last control chord (413 256)–(510 330). First click. */
  private static final Coordinate FIRST = new Coordinate(461.5, 293);

  /** Second click — a distinct pair partner, not a #83 invented mid. */
  private static final Coordinate SECOND = new Coordinate(430, 310);

  public GeometryVertexInserterCircularStringTest(String name) {
    super(name);
  }

  public static void main(String[] args) {
    TestRunner.run(GeometryVertexInserterCircularStringTest.class);
  }

  public void testOneClickInsertDoesNotWriteCircularString() throws ParseException {
    Geometry g = read(INPUT);
    GeometryLocation loc = GeometryPointLocater.locateNonVertexPoint(g, FIRST, 5.0);
    assertNotNull(loc);
    assertTrue(loc.isCircularStringComponent());

    Geometry result = loc.insert();
    assertSame("first click must not write A", g, result);
    assertEquals(19, g.getGeometryN(0).getNumPoints());
    assertEquals(1, g.getGeometryN(0).getNumPoints() % 2);
  }

  public void testTwoClickInsertKeepsGeometryCollectionCircularString()
      throws ParseException {
    Geometry g = read(INPUT);
    assertTrue(g instanceof GeometryCollection);
    assertTrue(g.getGeometryN(0) instanceof CircularString);
    assertEquals(19, g.getGeometryN(0).getNumPoints());

    GeometryLocation loc = GeometryPointLocater.locateNonVertexPoint(g, FIRST, 5.0);
    assertNotNull("first click locater missed chord " + FIRST, loc);

    Geometry afterFirst = loc.insert();
    assertSame(g, afterFirst);

    Geometry result = loc.insertPair(SECOND);
    assertNotNull(result);
    String wkt = write(result);

    assertFalse("must not flatten to LINESTRING, got " + wkt,
        result.getClass().equals(LineString.class));
    assertTrue("must keep GEOMETRYCOLLECTION wrapper, got "
        + result.getClass().getName() + " " + wkt,
        result instanceof GeometryCollection
            && !(result instanceof LineString));
    Geometry child = result.getGeometryN(0);
    assertTrue("child must stay CircularString, got " + child.getClass().getName()
        + " " + wkt, child instanceof CircularString);
    assertEquals("two-click commit is net +2 (19 → 21), got " + child.getNumPoints()
        + " " + wkt, 21, child.getNumPoints());
    assertEquals("ISO/IEC 13249-3 WKT tokens must stay odd, got "
        + child.getNumPoints() + " " + wkt,
        1, child.getNumPoints() % 2);
    assertTrue("first click missing, got " + wkt, contains(child, FIRST));
    assertTrue("second click missing, got " + wkt, contains(child, SECOND));
    assertTrue("last control must stay (510 330), got " + wkt,
        child.getCoordinates()[child.getNumPoints() - 1]
            .equals2D(new Coordinate(510, 330)));
    assertFalse("must not invent #83 chord-mid mid(A,C)",
        contains(child, GeometryVertexInserter.chordMidpoint(
            new Coordinate(406, 248), FIRST)));
    assertFalse("must not invent #83 chord-mid mid(C,B)",
        contains(child, GeometryVertexInserter.chordMidpoint(
            FIRST, new Coordinate(510, 330))));

    Geometry roundTrip = read(wkt);
    assertTrue(roundTrip instanceof GeometryCollection);
    assertTrue(roundTrip.getGeometryN(0) instanceof CircularString);
    assertEquals(21, roundTrip.getGeometryN(0).getNumPoints());
    assertEquals(1, wktTokenCount(child) % 2);
    assertTrue(wktTokenCount(child) >= 3);
  }

  public void testGeometryEditModelTwoClickInsertWritesAOnly() throws ParseException {
    GeometryEditModel model = new GeometryEditModel();
    model.setTestCase(new TestCaseEdit(new CurveGeometryFactory().getPrecisionModel()));
    Geometry input = read(INPUT);
    model.setGeometry(0, input);
    model.setGeometry(1, null);

    GeometryLocation loc = model.locateNonVertexPoint(FIRST, 5.0);
    assertNotNull(loc);
    assertSame(input, loc.insert());
    assertEquals("first click must not write A", 19,
        model.getGeometry(0).getGeometryN(0).getNumPoints());
    assertNull("overlay must not write B", model.getGeometry(1));

    Geometry committed = loc.insertPair(SECOND);
    assertNotNull(committed);
    model.setGeometry(0, committed);

    Geometry result = model.getGeometry(0);
    assertTrue(result instanceof GeometryCollection);
    assertTrue(result.getGeometryN(0) instanceof CircularString);
    assertEquals(21, result.getGeometryN(0).getNumPoints());
    assertEquals(1, result.getGeometryN(0).getNumPoints() % 2);
    assertNull("commit writes A, not B", model.getGeometry(1));
  }

  public void testBareCircularStringTwoClickStaysOdd() throws ParseException {
    Geometry g = read("CIRCULARSTRING (0 0, 1 1, 2 0)");
    Coordinate first = new Coordinate(1.5, 0.5);
    Coordinate second = new Coordinate(1.2, -0.4);
    GeometryLocation loc = GeometryPointLocater.locateNonVertexPoint(g, first, 0.2);
    assertNotNull(loc);
    assertSame(g, loc.insert());
    Geometry result = loc.insertPair(second);
    assertTrue(result instanceof CircularString);
    assertEquals(5, result.getNumPoints());
    assertTrue(contains(result, first));
    assertTrue(contains(result, second));
    assertFalse(result.getClass().equals(LineString.class));
    assertNotNull(read(write(result)));
  }

  public void testRefuseCoincidentConsecutive() throws ParseException {
    Geometry g = read("CIRCULARSTRING (0 0, 1 1, 2 0)");
    Coordinate first = new Coordinate(1.5, 0.5);
    GeometryLocation loc = GeometryPointLocater.locateNonVertexPoint(g, first, 0.2);
    assertNotNull(loc);
    assertNull("first == second is coincident consecutive",
        loc.insertPair(first));
    assertNull("second == next control is coincident consecutive",
        loc.insertPair(new Coordinate(2, 0)));
    assertEquals(3, g.getNumPoints());
    assertTrue(g instanceof CircularString);
  }

  public void testLineStringOneClickInsertUnchanged() throws ParseException {
    Geometry g = read("LINESTRING (0 0, 2 0)");
    Coordinate click = new Coordinate(1, 0);
    GeometryLocation loc = GeometryPointLocater.locateNonVertexPoint(g, click, 0.2);
    assertNotNull(loc);
    Geometry result = loc.insert();
    assertTrue(result instanceof LineString);
    assertFalse(result instanceof CircularString);
    assertEquals(3, result.getNumPoints());
  }

  public void testInsertPairOnLineStringRefuses() throws ParseException {
    Geometry g = read("LINESTRING (0 0, 2 0)");
    Coordinate click = new Coordinate(1, 0);
    GeometryLocation loc = GeometryPointLocater.locateNonVertexPoint(g, click, 0.2);
    assertNotNull(loc);
    assertNull(loc.insertPair(new Coordinate(1, 1)));
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

  /** ISO/IEC 13249-3 odd &ge; 3 is WKT control tokens. */
  private static int wktTokenCount(Geometry g) {
    return g.getNumPoints();
  }

  private static Geometry read(String wkt) throws ParseException {
    return new CurveWKTReader(new CurveGeometryFactory()).read(wkt);
  }

  private static String write(Geometry g) {
    return new CurveWKTWriter().write(g);
  }
}

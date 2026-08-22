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
 * UX issue #82: right-click add-vertex (Move/add/delete vertex) on a
 * {@code GEOMETRYCOLLECTION} of one {@code CIRCULARSTRING} must keep the
 * collection wrapper, keep the child a CircularString, and keep an odd
 * control count (split the clicked arc, net +2).
 * <p>
 * Same path as {@code EditVertexTool.mouseClicked} right-click →
 * {@link GeometryLocation#insert} → {@link GeometryVertexInserter}.
 */
public class GeometryVertexInserterCircularStringTest extends TestCase {

  private static final String INPUT =
      "GEOMETRYCOLLECTION (CIRCULARSTRING (172 410, 180 398, 190 380, 196 370, "
          + "210 349, 225 329, 237 314, 258 284, 279 264, 299 245, 311 237, "
          + "330 225, 361 215, 371 221, 387 232, 395 238, 406 248, 413 256, "
          + "510 330))";

  /** Midpoint of the last control chord (413 256)–(510 330). */
  private static final Coordinate CLICK = new Coordinate(461.5, 293);

  public GeometryVertexInserterCircularStringTest(String name) {
    super(name);
  }

  public static void main(String[] args) {
    TestRunner.run(GeometryVertexInserterCircularStringTest.class);
  }

  public void testRightClickInsertKeepsGeometryCollectionCircularString()
      throws ParseException {
    Geometry g = read(INPUT);
    assertTrue(g instanceof GeometryCollection);
    assertTrue(g.getGeometryN(0) instanceof CircularString);
    assertEquals(19, g.getGeometryN(0).getNumPoints());

    GeometryLocation loc = GeometryPointLocater.locateNonVertexPoint(g, CLICK, 5.0);
    assertNotNull("right-click locater missed chord midpoint " + CLICK, loc);

    Geometry result = loc.insert();
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
    assertEquals("arc split is net +2 (19 → 21), got " + child.getNumPoints()
        + " " + wkt, 21, child.getNumPoints());
    assertEquals("CircularString control count must stay odd, got "
        + child.getNumPoints() + " " + wkt,
        1, child.getNumPoints() % 2);
    assertTrue("inserted click point missing, got " + wkt,
        contains(child, CLICK));
    assertTrue("last control must stay (510 330), got " + wkt,
        child.getCoordinates()[child.getNumPoints() - 1]
            .equals2D(new Coordinate(510, 330)));

    Geometry roundTrip = read(wkt);
    assertTrue(roundTrip instanceof GeometryCollection);
    assertTrue(roundTrip.getGeometryN(0) instanceof CircularString);
    assertEquals(21, roundTrip.getGeometryN(0).getNumPoints());
  }

  public void testGeometryEditModelRightClickInsert() throws ParseException {
    GeometryEditModel model = new GeometryEditModel();
    model.setTestCase(new TestCaseEdit(new CurveGeometryFactory().getPrecisionModel()));
    model.setGeometry(read(INPUT));

    GeometryLocation loc = model.locateNonVertexPoint(CLICK, 5.0);
    assertNotNull(loc);
    model.setGeometry(loc.insert());

    Geometry result = model.getGeometry();
    assertTrue(result instanceof GeometryCollection);
    assertTrue(result.getGeometryN(0) instanceof CircularString);
    assertEquals(21, result.getGeometryN(0).getNumPoints());
    assertEquals(1, result.getGeometryN(0).getNumPoints() % 2);
  }

  public void testBareCircularStringInsertStaysOdd() throws ParseException {
    Geometry g = read("CIRCULARSTRING (0 0, 1 1, 2 0)");
    Coordinate click = new Coordinate(1.5, 0.5);
    GeometryLocation loc = GeometryPointLocater.locateNonVertexPoint(g, click, 0.2);
    assertNotNull(loc);
    Geometry result = loc.insert();
    assertTrue(result instanceof CircularString);
    assertEquals(5, result.getNumPoints());
    assertTrue(contains(result, click));
    assertNotNull(read(write(result)));
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

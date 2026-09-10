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

import org.locationtech.jts.algorithm.exactcurve.ExactCircularArc;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryCollection;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.curve.CircularString;
import org.locationtech.jts.geom.curve.CurveGeometryFactory;
import org.locationtech.jts.io.ParseException;
import org.locationtech.jts.io.curve.CurveWKTReader;
import org.locationtech.jts.io.curve.CurveWKTWriter;

import junit.framework.TestCase;
import junit.textui.TestRunner;

/**
 * UX issue #99: right-click add-vertex must locate against the
 * circular arc, not the control chord. Same path as
 * {@code EditVertexTool.mouseClicked} →
 * {@link GeometryPointLocater#locateNonVertexPoint}.
 * <p>
 * Witness: upper semicircle {@code CIRCULARSTRING (0 0, 1 1, 2 0)}.
 * Circumcentre is {@code (1 0)}, radius 1. A point at arc-length
 * fraction 0.25 sits ~0.707 above the chord; the chord midpoint is
 * the centre, 1.0 off the arc.
 */
public class GeometryPointLocaterCircularStringTest extends TestCase {

  private static final String ARC = "CIRCULARSTRING (0 0, 1 1, 2 0)";
  private static final Coordinate P0 = new Coordinate(0, 0);
  private static final Coordinate P1 = new Coordinate(1, 1);
  private static final Coordinate P2 = new Coordinate(2, 0);
  /** Chord midpoint = circumcentre; 1.0 from the painted arc. */
  private static final Coordinate CHORD_MID = new Coordinate(1, 0);
  private static final double TOL = 0.2;

  public GeometryPointLocaterCircularStringTest(String name) {
    super(name);
  }

  public static void main(String[] args) {
    TestRunner.run(GeometryPointLocaterCircularStringTest.class);
  }

  public void testLocateHitsPointOnArcFarFromChord() throws ParseException {
    Geometry g = read(ARC);
    Coordinate onArc = pointOnArc(0.25);
    assertTrue("oracle point must sit well off the chord, got " + onArc,
        onArc.y > 0.5);

    GeometryLocation loc = GeometryPointLocater.locateNonVertexPoint(g, onArc, TOL);
    assertNotNull("right-click locater missed arc point " + onArc
        + " (still hitting chords?)", loc);
    assertFalse(loc.isVertex());
    assertTrue(loc.getCoordinate().distance(onArc) < TOL);
  }

  public void testLocateMissesChordMidpointFarFromArc() throws ParseException {
    Geometry g = read(ARC);
    GeometryLocation loc = GeometryPointLocater.locateNonVertexPoint(
        g, CHORD_MID, TOL);
    assertNull("chord midpoint " + CHORD_MID
        + " is 1.0 off the arc and must not insert", loc);
  }

  public void testInsertOnArcKeepsOddCircularString() throws ParseException {
    Geometry g = read(ARC);
    Coordinate onArc = pointOnArc(0.25);
    GeometryLocation loc = GeometryPointLocater.locateNonVertexPoint(g, onArc, TOL);
    assertNotNull(loc);

    assertSame("first click must not write A (#82)", g, loc.insert());
    Coordinate second = new Coordinate(1.2, -0.4);
    Geometry result = loc.insertPair(second);
    String wkt = write(result);
    assertTrue("must stay CircularString, got " + result.getClass().getName()
        + " " + wkt, result instanceof CircularString);
    assertFalse(result.getClass().equals(LineString.class));
    assertEquals("two-click commit is net +2 (3 → 5), got " + result.getNumPoints()
        + " " + wkt, 5, result.getNumPoints());
    assertEquals(1, result.getNumPoints() % 2);
    assertNotNull(read(wkt));
  }

  public void testGeometryCollectionArcHit() throws ParseException {
    Geometry g = read("GEOMETRYCOLLECTION (" + ARC + ")");
    Coordinate onArc = pointOnArc(0.25);
    GeometryLocation loc = GeometryPointLocater.locateNonVertexPoint(g, onArc, TOL);
    assertNotNull("GC of CIRCULARSTRING must hit the arc, missed " + onArc, loc);

    assertSame("first click must not write A (#82)", g, loc.insert());
    Geometry result = loc.insertPair(new Coordinate(1.2, -0.4));
    assertTrue(result instanceof GeometryCollection);
    Geometry child = result.getGeometryN(0);
    assertTrue(child instanceof CircularString);
    assertEquals(5, child.getNumPoints());
  }

  private static Coordinate pointOnArc(double t) {
    return new ExactCircularArc(P0, P1, P2).pointAt(t);
  }

  private static Geometry read(String wkt) throws ParseException {
    return new CurveWKTReader(new CurveGeometryFactory()).read(wkt);
  }

  private static String write(Geometry g) {
    return new CurveWKTWriter().write(g);
  }
}

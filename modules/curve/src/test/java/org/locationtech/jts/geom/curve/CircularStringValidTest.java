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
package org.locationtech.jts.geom.curve;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.io.ParseException;
import org.locationtech.jts.io.curve.CurveWKTReader;

import junit.framework.Test;
import junit.framework.TestSuite;
import junit.textui.TestRunner;
import test.jts.GeometryTestCase;

/**
 * #120 retip: four-item {@code CIRCULARSTRING (A, B, C, A)} is rejected.
 * EX-CS-4 / ADR min ring is out. ISO/IEC 13249-3 wants odd ≥ 3.
 * Not #86 / #87 draw. Not #114 paint.
 */
public class CircularStringValidTest extends GeometryTestCase {

  private static final String RING_4 =
      "CIRCULARSTRING (-5 0, 0 5, 5 0, -5 0)";
  private static final String ODD_CLOSED =
      "CIRCULARSTRING (-5 0, 0 5, 5 0, 0 -5, -5 0)";

  public static void main(String[] args) { TestRunner.run(suite()); }
  public static Test suite() { return new TestSuite(CircularStringValidTest.class); }
  public CircularStringValidTest(String name) { super(name); }

  private static Geometry readCurve(String wkt) throws Exception {
    return new CurveWKTReader().read(wkt);
  }

  public void testClosedFourPointCircularStringIsRejected() {
    GeometryFactory gf = new CurveGeometryFactory();
    Coordinate[] pts = {
        new Coordinate(-5, 0),
        new Coordinate(0, 5),
        new Coordinate(5, 0),
        new Coordinate(-5, 0)
    };
    CircularString ring4 = new CircularString(gf.getCoordinateSequenceFactory().create(pts), gf);
    assertEquals(4, ring4.getNumPoints());
    assertFalse("EX-CS-4 / ADR min ring (A, B, C, A) is out", ring4.isValid());
    assertFalse(CircularString.isValidControlCount(ring4.getCoordinateSequence()));
  }

  public void testWktFourItemCircularStringIsRejected() throws Exception {
    try {
      readCurve(RING_4);
      fail("Expected parse failure for 4-item CIRCULARSTRING (A, B, C, A)");
    } catch (ParseException e) {
      assertTrue(e.getMessage().indexOf("odd number") >= 0
          || e.getMessage().indexOf("Four-item") >= 0);
    }
  }

  public void testOpenFourPointControlIsInvalid() {
    GeometryFactory gf = new CurveGeometryFactory();
    Coordinate[] pts = {
        new Coordinate(0, 0),
        new Coordinate(1, 1),
        new Coordinate(2, 0),
        new Coordinate(3, 1)
    };
    CircularString open = new CircularString(gf.getCoordinateSequenceFactory().create(pts), gf);
    assertFalse("open even leftover is not a valid CircularString", open.isValid());
    assertFalse(CircularString.isValidControlCount(open.getCoordinateSequence()));
  }

  public void testOddClosedCircularStringStillValid() throws Exception {
    Geometry g = readCurve(ODD_CLOSED);
    assertTrue(g.isValid());
    assertEquals(5, g.getNumPoints());
  }
}

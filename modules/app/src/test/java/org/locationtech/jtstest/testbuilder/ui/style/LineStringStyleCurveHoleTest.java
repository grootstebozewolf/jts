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
package org.locationtech.jtstest.testbuilder.ui.style;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.LinearRing;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.geom.curve.CircularString;
import org.locationtech.jts.geom.curve.CurveGeometryFactory;
import org.locationtech.jts.geom.curve.CurvePolygon;
import org.locationtech.jts.io.ParseException;
import org.locationtech.jts.io.curve.CurveWKTReader;
import org.locationtech.jtstest.testbuilder.geom.GeometryCombiner;

import junit.framework.TestCase;
import junit.textui.TestRunner;

/**
 * #114: hole overlay must walk the structural curve, not the flat
 * LinearRing. {@code getInteriorRingN} is the 3-point chord triangle.
 * ISO/IEC 13249-3: the hole stays {@code CIRCULARSTRING}.
 * <p>
 * The PO-attached witness WKT used 4-control {@code (A,B,C,A)} rings.
 * That form is rejected on I/O (do not invent a fifth control on read).
 * Style pins construct the 5-token first=last circle instead.
 */
public class LineStringStyleCurveHoleTest extends TestCase {

  /** Illegal I/O fixture: 4-control closed CIRCULARSTRING rings. */
  private static final String ILLEGAL_FOUR_CONTROL_WITNESS =
      "CURVEPOLYGON (CIRCULARSTRING (60 380, 240 440, 404 326, 60 380), "
      + "CIRCULARSTRING (141 84, 270 28, 170 290, 141 84))";

  public static void main(String[] args) {
    TestRunner.run(LineStringStyleCurveHoleTest.class);
  }

  public LineStringStyleCurveHoleTest(String name) { super(name); }

  public void testIllegalFourControlWitnessWktIsParseException() {
    try {
      new CurveWKTReader().read(ILLEGAL_FOUR_CONTROL_WITNESS);
      fail("ISO/IEC 13249-3: CIRCULARSTRING(A,B,C,A) must not parse");
    } catch (ParseException e) {
      assertTrue(e.getMessage(), e.getMessage().indexOf("13249-3") >= 0);
    }
  }

  public void testWitnessInteriorRingIsStructuralCircularString() {
    CurvePolygon g = constructFiveTokenWitness();
    LineString painted = LineStringStyle.interiorRing(g, 0);
    assertTrue("canvas hole must be the CircularString, got "
        + painted.getGeometryType(), painted instanceof CircularString);
    assertEquals(5, painted.getNumPoints());
    assertFalse("must not hand the style the flat chord triangle",
        painted instanceof LinearRing);
  }

  public void testWitnessExteriorRingIsStructuralCircularString() {
    CurvePolygon g = constructFiveTokenWitness();
    LineString painted = LineStringStyle.exteriorRing(g);
    assertTrue(painted instanceof CircularString);
    assertEquals(5, painted.getNumPoints());
  }

  /** Guard: a plain polygon still uses the LinearRing views. */
  public void testPlainPolygonUnchanged() {
    org.locationtech.jts.geom.GeometryFactory factory =
        new org.locationtech.jts.geom.GeometryFactory();
    LinearRing shell = factory.createLinearRing(new Coordinate[] {
        new Coordinate(-5, -5), new Coordinate(5, -5), new Coordinate(5, 5),
        new Coordinate(-5, 5), new Coordinate(-5, -5)
    });
    LinearRing hole = factory.createLinearRing(new Coordinate[] {
        new Coordinate(-1, -1), new Coordinate(1, -1), new Coordinate(1, 1),
        new Coordinate(-1, 1), new Coordinate(-1, -1)
    });
    Polygon p = factory.createPolygon(shell, new LinearRing[] { hole });
    assertSame(shell, LineStringStyle.exteriorRing(p));
    assertSame(hole, LineStringStyle.interiorRing(p, 0));
  }

  /**
   * Construct-only 5-token rings from the PO witness controls.
   * Type stays CIRCULARSTRING.
   */
  private static CurvePolygon constructFiveTokenWitness() {
    CurveGeometryFactory gf = new CurveGeometryFactory();
    CircularString shell = circularRing(gf, new Coordinate[] {
        new Coordinate(60, 380), new Coordinate(240, 440),
        new Coordinate(404, 326), new Coordinate(60, 380)
    });
    CircularString hole = circularRing(gf, new Coordinate[] {
        new Coordinate(141, 84), new Coordinate(270, 28),
        new Coordinate(170, 290), new Coordinate(141, 84)
    });
    return gf.createCurvePolygon(shell, new LineString[] { hole });
  }

  private static CircularString circularRing(CurveGeometryFactory gf,
      Coordinate[] closedFour) {
    Coordinate[] five = GeometryCombiner.expandConstructCircle(closedFour);
    if (five == null || five.length != 5) {
      throw new IllegalStateException(
          "construct mid must exist for the witness circumcircle");
    }
    return gf.createCircularString(gf.getCoordinateSequenceFactory().create(five));
  }
}

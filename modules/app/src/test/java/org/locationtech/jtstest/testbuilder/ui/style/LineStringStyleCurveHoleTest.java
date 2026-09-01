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
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.LinearRing;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.geom.curve.CircularString;
import org.locationtech.jts.geom.curve.CurvePolygon;
import org.locationtech.jts.io.curve.CurveWKTReader;

import junit.framework.TestCase;
import junit.textui.TestRunner;

/**
 * #114: hole overlay must walk the structural curve, not the flat
 * LinearRing. {@code getInteriorRingN} is the 3-point chord triangle.
 * ISO/IEC 13249-3: the hole stays {@code CIRCULARSTRING}.
 * <p>
 * Witness is the PO-attached WKT (do not invent another).
 */
public class LineStringStyleCurveHoleTest extends TestCase {

  private static final String WITNESS =
      "CURVEPOLYGON (CIRCULARSTRING (60 380, 240 440, 404 326, 60 380), "
      + "CIRCULARSTRING (141 84, 270 28, 170 290, 141 84))";

  public static void main(String[] args) {
    TestRunner.run(LineStringStyleCurveHoleTest.class);
  }

  public LineStringStyleCurveHoleTest(String name) { super(name); }

  public void testWitnessInteriorRingIsStructuralCircularString() throws Exception {
    Geometry g = new CurveWKTReader().read(WITNESS);
    assertTrue(g instanceof CurvePolygon);
    LineString painted = LineStringStyle.interiorRing((Polygon) g, 0);
    assertTrue("canvas hole must be the CircularString, got "
        + painted.getGeometryType(), painted instanceof CircularString);
    assertEquals(4, painted.getNumPoints());
    assertFalse("must not hand the style the flat chord triangle",
        painted instanceof LinearRing);
  }

  public void testWitnessExteriorRingIsStructuralCircularString() throws Exception {
    Geometry g = new CurveWKTReader().read(WITNESS);
    LineString painted = LineStringStyle.exteriorRing((Polygon) g);
    assertTrue(painted instanceof CircularString);
    assertEquals(4, painted.getNumPoints());
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
}

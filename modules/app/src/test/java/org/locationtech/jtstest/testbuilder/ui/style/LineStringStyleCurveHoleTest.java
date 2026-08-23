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
import org.locationtech.jts.geom.curve.CurveGeometryFactory;
import org.locationtech.jts.geom.curve.CurvePolygon;
import org.locationtech.jts.io.curve.CurveWKTReader;
import org.locationtech.jts.io.curve.CurveWKTWriter;

import junit.framework.TestCase;
import junit.textui.TestRunner;

/**
 * #114: hole overlay must walk the structural curve, not the flat
 * LinearRing. {@code getInteriorRingN} is the 3-point chord triangle.
 * ISO/IEC 13249-3: the hole stays {@code CIRCULARSTRING}.
 * Factory fixtures only; no UX ring was attached.
 */
public class LineStringStyleCurveHoleTest extends TestCase {

  private final CurveGeometryFactory factory = new CurveGeometryFactory();

  public static void main(String[] args) {
    TestRunner.run(LineStringStyleCurveHoleTest.class);
  }

  public LineStringStyleCurveHoleTest(String name) { super(name); }

  private CircularString threePointOpenArc() {
    return (CircularString) factory.createCircularString(
        factory.getCoordinateSequenceFactory().create(new Coordinate[] {
            new Coordinate(-3, 0), new Coordinate(0, 3), new Coordinate(3, 0)
        }));
  }

  private CircularString fiveTokenCircle(double r) {
    return (CircularString) factory.createCircularString(
        factory.getCoordinateSequenceFactory().create(new Coordinate[] {
            new Coordinate(-r, 0), new Coordinate(0, r), new Coordinate(r, 0),
            new Coordinate(0, -r), new Coordinate(-r, 0)
        }));
  }

  public void testInteriorRingIsStructuralCircularString() {
    CurvePolygon cp = factory.createCurvePolygon(fiveTokenCircle(10),
        new LineString[] { threePointOpenArc() });
    LineString painted = LineStringStyle.interiorRing(cp, 0);
    assertTrue("canvas hole must be the CircularString, got "
        + painted.getGeometryType(), painted instanceof CircularString);
    assertEquals(3, painted.getNumPoints());
    assertFalse("must not hand the style the flat chord triangle",
        painted instanceof LinearRing);
  }

  public void testExteriorRingIsStructuralCircularString() {
    CurvePolygon cp = factory.createCurvePolygon(fiveTokenCircle(10),
        new LineString[] { threePointOpenArc() });
    LineString painted = LineStringStyle.exteriorRing(cp);
    assertTrue(painted instanceof CircularString);
    assertEquals(5, painted.getNumPoints());
  }

  /**
   * PO ring: overlay walks the attached 4-control CircularString hole.
   * A pane WKT is unchanged.
   */
  public void testPoRingOverlayKeepsAttachedCircularString() throws Exception {
    String po =
        "CURVEPOLYGON (CIRCULARSTRING (60 380, 240 440, 404 326, 60 380), CIRCULARSTRING (141 84, 270 28, 170 290, 141 84))";
    Geometry g = new CurveWKTReader().read(po);
    assertEquals(po, new CurveWKTWriter().write(g));
    CurvePolygon cp = (CurvePolygon) g;
    LineString painted = LineStringStyle.interiorRing(cp, 0);
    assertTrue(painted instanceof CircularString);
    assertEquals(4, painted.getNumPoints());
    assertFalse(painted instanceof LinearRing);
  }

  /** Guard: a plain polygon still uses the LinearRing views. */
  public void testPlainPolygonUnchanged() {
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

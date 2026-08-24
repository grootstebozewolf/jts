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

import java.awt.Shape;
import java.awt.geom.PathIterator;

import org.locationtech.jts.awt.curve.CurveShapeWriter;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.io.ParseException;
import org.locationtech.jts.io.WKBReader;
import org.locationtech.jts.io.WKBWriter;
import org.locationtech.jts.io.curve.CurveWKBReader;
import org.locationtech.jts.io.curve.CurveWKBWriter;
import org.locationtech.jts.io.curve.CurveWKTReader;
import org.locationtech.jts.io.curve.CurveWKTWriter;

import junit.framework.Test;
import junit.framework.TestSuite;
import junit.textui.TestRunner;
import test.jts.GeometryTestCase;

/**
 * JTS on-ramp: {@code CIRCULARSTRING (A, B, A)} rewrites on add/read to
 * the 5-token circle {@code (A, C, B, D, A)}. Not the ISO/IEC 13249-3
 * full-circle form. Four-item {@code (A, B, C, A)} is rejected.
 * No GUI. No DOI.
 */
public class CircularStringDiameterOnRampTest extends GeometryTestCase {

  private static final double EPS = 1e-12;
  private static final String ON_RAMP = "CIRCULARSTRING (0 0, 2 0, 0 0)";
  private static final String FOUR_ITEM = "CIRCULARSTRING (-5 0, 0 5, 5 0, -5 0)";
  private static final String A_EQ_B = "CIRCULARSTRING (0 0, 0 0, 0 0)";

  public static void main(String[] args) { TestRunner.run(suite()); }
  public static Test suite() { return new TestSuite(CircularStringDiameterOnRampTest.class); }
  public CircularStringDiameterOnRampTest(String name) { super(name); }

  private static Geometry readCurve(String wkt) throws Exception {
    return new CurveWKTReader().read(wkt);
  }

  public void testReadRewritesAbaToFiveTokenCircle() throws Exception {
    Geometry g = readCurve(ON_RAMP);
    assertTrue(g instanceof CircularString);
    assertEquals("CircularString", g.getGeometryType());
    assertEquals(5, g.getNumPoints());
    assertTrue(((CircularString) g).isClosed());
    assertTrue(g.isValid());
    Coordinate[] pts = g.getCoordinates();
    assertEquals(0.0, pts[0].x, EPS);
    assertEquals(0.0, pts[0].y, EPS);
    assertEquals(1.0, pts[1].x, EPS);
    assertEquals(-1.0, pts[1].y, EPS);
    assertEquals(2.0, pts[2].x, EPS);
    assertEquals(0.0, pts[2].y, EPS);
    assertEquals(1.0, pts[3].x, EPS);
    assertEquals(1.0, pts[3].y, EPS);
    assertEquals(0.0, pts[4].x, EPS);
    assertEquals(0.0, pts[4].y, EPS);
  }

  public void testFactoryRewritesAbaToFiveTokenCircle() {
    CurveGeometryFactory gf = new CurveGeometryFactory();
    Coordinate[] in = {
        new Coordinate(0, 0),
        new Coordinate(0, 2),
        new Coordinate(0, 0)
    };
    CircularString g = gf.createCircularString(
        gf.getCoordinateSequenceFactory().create(in));
    assertEquals("CircularString", g.getGeometryType());
    assertEquals(5, g.getNumPoints());
    Coordinate[] pts = g.getCoordinates();
    assertEquals(0.0, pts[0].x, EPS);
    assertEquals(0.0, pts[0].y, EPS);
    assertEquals(1.0, pts[1].x, EPS);
    assertEquals(1.0, pts[1].y, EPS);
    assertEquals(0.0, pts[2].x, EPS);
    assertEquals(2.0, pts[2].y, EPS);
    assertEquals(-1.0, pts[3].x, EPS);
    assertEquals(1.0, pts[3].y, EPS);
    assertEquals(0.0, pts[4].x, EPS);
    assertEquals(0.0, pts[4].y, EPS);
  }

  public void testWktAndWkbIdentityIsFiveTokenCircularString() throws Exception {
    Geometry g = readCurve(ON_RAMP);
    String wkt = new CurveWKTWriter().write(g);
    assertTrue(wkt.toUpperCase().startsWith("CIRCULARSTRING"));
    assertFalse(wkt.toUpperCase().startsWith("LINESTRING"));
    assertEquals(5, g.getNumPoints());
    Geometry again = new CurveWKTReader().read(wkt);
    assertEquals("CircularString", again.getGeometryType());
    assertEquals(5, again.getNumPoints());
    checkEqual(g, again);

    byte[] wkb = new CurveWKBWriter().write(g);
    Geometry fromWkb = new CurveWKBReader(g.getFactory()).read(wkb);
    assertEquals("CircularString", fromWkb.getGeometryType());
    assertEquals(5, fromWkb.getNumPoints());
    checkEqual(g, fromWkb);
  }

  public void testWkbReadOfThreeTokenOnRampRewrites() throws Exception {
    CurveGeometryFactory gf = new CurveGeometryFactory();
    CircularString raw = new CircularString(gf.getCoordinateSequenceFactory().create(
        new Coordinate[] {
            new Coordinate(0, 0),
            new Coordinate(2, 0),
            new Coordinate(0, 0)
        }), gf);
    assertEquals(3, raw.getNumPoints());
    byte[] wkb = new CurveWKBWriter().write(raw);
    Geometry back = new CurveWKBReader(gf).read(wkb);
    assertEquals("CircularString", back.getGeometryType());
    assertEquals(5, back.getNumPoints());
    assertEquals(1.0, back.getCoordinates()[1].x, EPS);
    assertEquals(-1.0, back.getCoordinates()[1].y, EPS);
  }

  public void testRefuseAEqualsBOnRead() throws Exception {
    try {
      readCurve(A_EQ_B);
      fail("Expected refuse A = B");
    } catch (ParseException e) {
      assertTrue(e.getMessage().indexOf("A = B") >= 0
          || e.getMessage().indexOf("distinct") >= 0);
      assertTrue(e.getMessage().indexOf("ISO/IEC 13249-3") >= 0);
    }
  }

  public void testRefuseAEqualsBOnFactory() {
    CurveGeometryFactory gf = new CurveGeometryFactory();
    Coordinate[] pts = {
        new Coordinate(1, 1),
        new Coordinate(1, 1),
        new Coordinate(1, 1)
    };
    try {
      gf.createCircularString(gf.getCoordinateSequenceFactory().create(pts));
      fail("Expected refuse A = B");
    } catch (IllegalArgumentException e) {
      assertTrue(e.getMessage().indexOf("ISO/IEC 13249-3") >= 0);
    }
  }

  public void testRejectFourItemOnRead() throws Exception {
    try {
      readCurve(FOUR_ITEM);
      fail("Expected reject 4-item CIRCULARSTRING");
    } catch (ParseException e) {
      assertTrue(e.getMessage().indexOf("odd") >= 0
          || e.getMessage().indexOf("Four-item") >= 0);
    }
  }

  public void testNeverFlattenToLineString() throws Exception {
    Geometry g = readCurve(ON_RAMP);
    assertEquals(CircularString.class, g.getClass());
    assertFalse(g.getClass().getName().endsWith(".LineString"));
    String wkt = g.toText();
    assertTrue(wkt.toUpperCase().contains("CIRCULARSTRING"));
    assertFalse(wkt.toUpperCase().startsWith("LINESTRING"));
  }

  /**
   * After add/read the stored list is the 5-token circle, so canvas
   * paints two arcs, not a single-arc (A, B, A) and not a 4-token ring.
   */
  public void testShapeWriterPaintsTwoArcsNotSingleArcOrFourToken() throws Exception {
    Geometry g = readCurve(ON_RAMP);
    assertEquals(5, g.getNumPoints());
    Shape s = new CurveShapeWriter().toShape(g);
    int cubic = 0;
    double[] coords = new double[6];
    for (PathIterator it = s.getPathIterator(null); !it.isDone(); it.next()) {
      if (it.currentSegment(coords) == PathIterator.SEG_CUBICTO) {
        cubic++;
      }
    }
    assertTrue("5-token circle paints two arcs (cubic segments), got " + cubic,
        cubic >= 2);
  }

  public void testWkbHexOfRewrittenCircleHasFivePoints() throws Exception {
    Geometry g = readCurve(ON_RAMP);
    String hex = WKBWriter.toHex(new CurveWKBWriter().write(g));
    Geometry back = new CurveWKBReader(g.getFactory())
        .read(WKBReader.hexToBytes(hex));
    assertEquals(5, back.getNumPoints());
    assertEquals("CircularString", back.getGeometryType());
  }
}

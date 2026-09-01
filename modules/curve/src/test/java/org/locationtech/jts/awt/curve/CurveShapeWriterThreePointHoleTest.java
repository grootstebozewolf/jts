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
package org.locationtech.jts.awt.curve;

import java.awt.Shape;
import java.awt.geom.PathIterator;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.curve.CircularArcDensifier;
import org.locationtech.jts.geom.curve.CircularString;
import org.locationtech.jts.geom.curve.CurveGeometryFactory;
import org.locationtech.jts.geom.curve.CurvePolygon;
import org.locationtech.jts.io.curve.CurveWKTReader;
import org.locationtech.jts.io.curve.CurveWKTWriter;

import junit.textui.TestRunner;
import test.jts.GeometryTestCase;

/**
 * #114 / tb-h-1: a 3-point {@code CIRCULARSTRING} hole stays a curve
 * member and the fill path subtracts it as circular arcs, not the
 * control-point chord triangle.
 * <p>
 * PO witness (do not invent another), TestBuilder RC4 @ {@code 65d02ef9}:
 * {@code CURVEPOLYGON (CIRCULARSTRING (60 380, 240 440, 404 326, 60 380),
 * CIRCULARSTRING (141 84, 270 28, 170 290, 141 84))}. Both rings are
 * four-control {@code (A, B, C, A)} — the same close #87 already paints
 * as a full circumcircle for the shell. The hole specified arc is the
 * major side (~269°). Complementary close through the antipode of B
 * retraces that major arc, so {@code Graphics2D.fill} never punches the
 * hole and the stroke looks like an open spiral on a solid disc.
 * <p>
 * ISO/IEC 13249-3: a {@code CURVEPOLYGON} keeps {@code CIRCULARSTRING}
 * shell and hole members. Do not flatten to {@code POLYGON}.
 * <p>
 * Not FCP-H / #4 (type already stays CircularString). Not #86 3-click
 * shell. Not #56. Not #76. CurvePolygon draw tool still has no holes;
 * this is load/WKT.
 */
public class CurveShapeWriterThreePointHoleTest extends GeometryTestCase {

  /** PO-attached witness. Do not substitute another ring. */
  private static final String WITNESS =
      "CURVEPOLYGON (CIRCULARSTRING (60 380, 240 440, 404 326, 60 380), "
      + "CIRCULARSTRING (141 84, 270 28, 170 290, 141 84))";

  /** #86/#87 guard: specified arc is a semicircle, antipode happened to work. */
  private static final String SEMICIRCLE_HOLE =
      "CURVEPOLYGON (CIRCULARSTRING (-10 0, 0 10, 10 0, -10 0), "
      + "CIRCULARSTRING (-3 0, 0 3, 3 0, -3 0))";

  /**
   * Compact major-arc hole: A=(-3,0), B=(0,-3), C=(0,3) on r=3.
   * Specified sweep A→B→C is 270°, so the antipode of B retraces.
   */
  private static final String MAJOR_ARC_HOLE =
      "CURVEPOLYGON (CIRCULARSTRING (-10 0, 0 10, 10 0, 0 -10, -10 0), "
      + "CIRCULARSTRING (-3 0, 0 -3, 0 3, -3 0))";

  public static void main(String[] args) {
    TestRunner.run(CurveShapeWriterThreePointHoleTest.class);
  }

  public CurveShapeWriterThreePointHoleTest(String name) { super(name); }

  private static CircularString fourControl(double... xy) {
    CurveGeometryFactory gf = new CurveGeometryFactory();
    Coordinate[] pts = new Coordinate[xy.length / 2];
    for (int i = 0; i < pts.length; i++) {
      pts[i] = new Coordinate(xy[2 * i], xy[2 * i + 1]);
    }
    return new CircularString(gf.getCoordinateSequenceFactory().create(pts), gf);
  }

  private static CurvePolygon annulus(CircularString shell, CircularString hole) {
    CurveGeometryFactory gf = new CurveGeometryFactory();
    return new CurvePolygon(shell, new LineString[] { hole }, gf);
  }

  /**
   * Overlay/render 4-control witness. Not SQL/MM WKT (odd count);
   * constructed, not parsed.
   */
  private static Geometry readWitness() {
    return annulus(
        fourControl(60, 380, 240, 440, 404, 326, 60, 380),
        fourControl(141, 84, 270, 28, 170, 290, 141, 84));
  }

  private static Geometry semicircleHole() {
    return annulus(
        fourControl(-10, 0, 0, 10, 10, 0, -10, 0),
        fourControl(-3, 0, 0, 3, 3, 0, -3, 0));
  }

  private static Geometry majorArcHole() throws Exception {
    CircularString shell = (CircularString) new CurveWKTReader().read(
        "CIRCULARSTRING (-10 0, 0 10, 10 0, 0 -10, -10 0)");
    return annulus(shell, fourControl(-3, 0, 0, -3, 0, 3, -3, 0));
  }

  private static Shape shapeOf(Geometry g) {
    return new CurveShapeWriter().toShape(g);
  }

  private static SegCounts segs(Shape s) {
    SegCounts c = new SegCounts();
    double[] coords = new double[6];
    PathIterator it = s.getPathIterator(null);
    while (!it.isDone()) {
      switch (it.currentSegment(coords)) {
        case PathIterator.SEG_MOVETO:  c.moveTo++; break;
        case PathIterator.SEG_LINETO:  c.lineTo++; break;
        case PathIterator.SEG_CUBICTO: c.cubicTo++; break;
        case PathIterator.SEG_CLOSE:   c.close++; break;
        default: break;
      }
      it.next();
    }
    return c;
  }

  /** Type lock: A stays CURVEPOLYGON of CIRCULARSTRING + CIRCULARSTRING. */
  public void testWitnessKeepsCurvePolygonOfCircularStrings() throws Exception {
    Geometry g = readWitness();
    assertTrue("must stay CurvePolygon, got " + g.getGeometryType(),
        g instanceof CurvePolygon);
    CurvePolygon cp = (CurvePolygon) g;
    assertEquals(1, cp.getNumInteriorRing());
    assertTrue("shell must stay CircularString, got "
        + cp.getExteriorCurve().getGeometryType(),
        cp.getExteriorCurve() instanceof CircularString);
    LineString hole = cp.getInteriorCurveN(0);
    assertTrue("hole must stay CircularString, got " + hole.getGeometryType(),
        hole instanceof CircularString);
    assertEquals("hole stays four-control (A,B,C,A); do not invent a 5th",
        4, hole.getNumPoints());
    assertTrue(hole.isClosed());
  }

  /**
   * Witness still serializes as CURVEPOLYGON (CIRCULARSTRING …, CIRCULARSTRING …).
   * Paint must not flatten to POLYGON or write a complementary mid into WKT.
   */
  public void testWitnessSerializesAsCurvePolygonOfCircularStrings() throws Exception {
    Geometry g = readWitness();
    String wkt = new CurveWKTWriter().write(g);
    String u = wkt.toUpperCase().replaceAll("\\s+", " ");
    assertTrue("must stay CURVEPOLYGON, got " + wkt, u.startsWith("CURVEPOLYGON"));
    assertFalse("must not flatten to POLYGON, got " + wkt,
        u.startsWith("POLYGON"));
    int first = u.indexOf("CIRCULARSTRING");
    int second = u.indexOf("CIRCULARSTRING", first + 1);
    assertTrue("shell CIRCULARSTRING missing: " + wkt, first >= 0);
    assertTrue("hole CIRCULARSTRING missing: " + wkt, second > first);
    assertTrue(u.contains("141 84, 270 28, 170 290, 141 84"));
  }

  /** Fill/path subtracts the hole. Complementary-side interior is punched. */
  public void testWitnessFillSubtractsHole() throws Exception {
    Shape s = shapeOf(readWitness());
    // Hole circumcircle ~ centre (256.21, 172.82) r=145.48
    assertFalse("hole centre must not be filled", s.contains(256.21, 172.82));
    // Shell centre sits inside the hole disc, so it is also exterior.
    assertFalse("shell centre is inside the hole disc", s.contains(211.83, 224.51));
    // Left annulus: inside shell, outside hole.
    assertTrue("annulus body left of both centres must stay filled",
        s.contains(80, 224));
  }

  /** Canvas: bezier arcs, not the 3-point chord triangle (lineTo only). */
  public void testWitnessRendersArcsNotChordTriangle() throws Exception {
    SegCounts c = segs(shapeOf(readWitness()));
    assertEquals("shell + hole", 2, c.moveTo);
    assertEquals("each ring is its own closed subpath", 2, c.close);
    assertTrue("both rings must emit cubic arcs, got cubic=" + c.cubicTo
        + " line=" + c.lineTo, c.cubicTo >= 4);
    assertEquals("chord triangle would be lineTo through the three controls",
        0, c.lineTo);
  }

  /**
   * Complementary close mid of the witness hole must lie on the missing
   * minor arc, not at the antipode of B (which retraces the major arc).
   */
  public void testWitnessHoleCloseMidIsOnComplementaryArc() throws Exception {
    CurvePolygon cp = (CurvePolygon) readWitness();
    LineString hole = cp.getInteriorCurveN(0);
    Coordinate closeMid = CircularArcDensifier.threePointCircleCloseMid(
        hole.getCoordinateSequence());
    assertNotNull(closeMid);
    Coordinate b = hole.getCoordinateN(1);
    double[] circ = CircularArcDensifier.circumcircle(
        hole.getCoordinateN(0), b, hole.getCoordinateN(2));
    assertNotNull(circ);
    Coordinate antipode = new Coordinate(2.0 * circ[0] - b.x, 2.0 * circ[1] - b.y);
    assertTrue("major-arc hole must not close through the antipode of B",
        closeMid.distance(antipode) > 1.0);
    // Close mid is on the hole circumcircle.
    double r = Math.hypot(closeMid.x - circ[0], closeMid.y - circ[1]);
    assertEquals(circ[2], r, 1.0e-6);
    // And length is one full hole circumference, not 2× the major arc.
    // SQL/MM length is the stored arc only. Close mid is a render kit.
    assertTrue("stored hole length is one arc, not an invented full circle",
        hole.getLength() < 2.0 * Math.PI * circ[2] - 1.0e-3);
  }

  /** Guard: semicircle (A,B,C,A) hole from #87 still punches. */
  public void testSemicircleHoleStillPunches() throws Exception {
    Shape s = shapeOf(semicircleHole());
    assertFalse(s.contains(0, 0));
    assertFalse(s.contains(0, -1.5));
    assertFalse(s.contains(0, 1.5));
    assertTrue(s.contains(6, 0));
    assertTrue(s.contains(0, -6));
  }

  /** Major-arc 3-point hole on the unit circle: punch, not retrace. */
  public void testMajorArcThreePointHolePunchesCircumcircle() throws Exception {
    Shape s = shapeOf(majorArcHole());
    assertFalse("hole centre must not be filled", s.contains(0, 0));
    // Complementary close of (-3,0)→(0,3) is the NW quadrant; (-2,2) is inside.
    assertFalse("complementary-side interior is still a hole", s.contains(-2, 2));
    assertFalse("specified-arc side is still a hole", s.contains(0, -2));
    assertTrue("annulus body stays filled", s.contains(6, 0));
    SegCounts c = segs(s);
    assertEquals(0, c.lineTo);
    assertTrue(c.cubicTo >= 4);
  }

  private static final class SegCounts {
    int moveTo;
    int lineTo;
    int cubicTo;
    int close;
  }
}

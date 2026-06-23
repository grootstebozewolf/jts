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
package org.locationtech.jts.geom.curved;

import java.util.Random;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.CoordinateSequence;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.geom.impl.CoordinateArraySequence;

import junit.framework.TestCase;
import junit.textui.TestRunner;

/**
 * BUF-1 / BUF-NEG (#1195) — arc-aware buffer of a convex curved ring. Pinned to
 * the oracle {@code BUFFER_REGION} signed-area certificate (committed values, from
 * the canonical build) plus the analytic buffer-area formula, a boundary
 * parallel-distance check, an EMPTY-on-collapse check, and a densified cross-check
 * against core {@code BufferOp}.
 */
public class CurvedBufferTest extends TestCase {

  public static void main(String[] args) { TestRunner.run(CurvedBufferTest.class); }
  public CurvedBufferTest(String name) { super(name); }

  private static final CurvedGeometryFactory GF = new CurvedGeometryFactory();

  private static CoordinateSequence seq(double[][] xy) {
    Coordinate[] c = new Coordinate[xy.length];
    for (int i = 0; i < xy.length; i++) c[i] = new Coordinate(xy[i][0], xy[i][1]);
    return new CoordinateArraySequence(c);
  }
  private static CircularString cs(double[][] xy) { return GF.createCircularString(seq(xy)); }

  // CCW rings, control points (arc triples; collinear triple = chord)
  private static CircularString disk(double cx, double cy, double r) {
    return cs(new double[][]{ {cx+r,cy},{cx,cy+r},{cx-r,cy},{cx,cy-r},{cx+r,cy} });
  }
  private static CircularString stadium() {  // caps r=2 at x=±3, straight sides y=±2
    return cs(new double[][]{ {3,-2},{5,0},{3,2}, {0,2},{-3,2}, {-5,0},{-3,-2}, {0,-2},{3,-2} });
  }
  private static CircularString square10() {
    return cs(new double[][]{ {0,0},{5,0},{10,0}, {10,5},{10,10}, {5,10},{0,10}, {0,5},{0,0} });
  }
  private static CircularString triangle() {
    return cs(new double[][]{ {0,0},{4,0},{8,0}, {6,3.5},{4,7}, {2,3.5},{0,0} });
  }
  private static CurvePolygon cp(CircularString shell) { return GF.createCurvePolygon(shell); }

  private static CircularString shapeByLabel(String label) {
    switch (label) {
      case "disk5":    return disk(0, 0, 5);
      case "stadium":  return stadium();
      case "square10": return square10();
      case "triangle": return triangle();
      default: throw new IllegalArgumentException("unknown shape label: " + label);
    }
  }

  // ---- oracle BUFFER_REGION signed-area certificates ----

  /**
   * Pins {@link CurvedBuffer} to the exact buffer-region areas certified by the
   * NetTopologySuite.Proofs extracted oracle (BUFFER_REGION mode). The committed
   * {@code curve_buffer_region_vectors.txt} are the oracle's AREA outputs for the
   * source ring fed as A (arc) / C (chord) boundary segments plus a signed
   * distance.
   */
  public void testOraclePinnedAreas() throws Exception {
    java.io.InputStream in = getClass().getResourceAsStream(
        "/org/locationtech/jts/geom/curved/rocqref/curve_buffer_region_vectors.txt");
    assertNotNull("buffer vectors resource", in);
    java.io.BufferedReader r = new java.io.BufferedReader(
        new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8));
    String line; int checked = 0;
    while ((line = r.readLine()) != null) {
      String s = line.trim();
      if (s.isEmpty() || s.startsWith("#")) continue;
      String[] t = s.split("\\s+");
      String label = t[0];
      double d = Double.parseDouble(t[1]);
      double expected = Double.parseDouble(t[2]);
      assertArcArea(label + " d=" + d, CurvedBuffer.buffer(cp(shapeByLabel(label)), d), expected);
      checked++;
    }
    r.close();
    assertTrue("should have checked oracle buffer vectors", checked >= 6);
  }

  /** For a circle: area of buffer = π*(r+d)². */
  public void testAnalyticCircle() {
    for (double d : new double[]{ 0.5, 1, 2, 3 }) {
      Geometry b = CurvedBuffer.buffer(cp(disk(0,0,5)), d);
      assertArcArea("circle d="+d, b, Math.PI * (5+d) * (5+d));
    }
  }

  /** CircularString.buffer(d) delegates to CurvedBuffer, returns CurvePolygon. */
  public void testCircularStringBufferDelegates() {
    CircularString arc = disk(0, 0, 5);
    Geometry buf = arc.buffer(2.0);
    assertTrue("buffer of CircularString should be CurvePolygon", buf instanceof CurvePolygon);
    assertArcArea("circle arc.buffer", buf, Math.PI * 7 * 7);
  }

  /** Negative buffer collapses when d <= -r. */
  public void testNegativeBufferCollapsesEmpty() {
    assertTrue("d=-5 -> EMPTY", CurvedBuffer.buffer(cp(disk(0,0,5)), -5.0).isEmpty());
    assertTrue("d=-7 -> EMPTY", CurvedBuffer.buffer(cp(disk(0,0,5)), -7.0).isEmpty());
    assertFalse("d=-4 -> non-empty", CurvedBuffer.buffer(cp(disk(0,0,5)), -4.0).isEmpty());
  }

  /** Every point on the buffer boundary is at distance ~|d| from the source ring boundary. */
  public void testBoundaryParallelDistance() {
    double d = 1.5;
    CircularString src = stadium();
    Geometry buf = CurvedBuffer.buffer(cp(src), d);
    CoordinateSequence out = ((CircularString) ((CurvePolygon) buf).getExteriorCurve()).getCoordinateSequence();
    Coordinate[] srcPoly = densify(src, 4000);
    int n = out.size();
    for (int i = 0; 2 * i + 2 < n; i++) {
      double[] a = piece(out, i);
      for (int k = 0; k <= 6; k++) {
        double[] p = pointOnPiece(a, k / 6.0);
        double dist = minDistToPolyline(p[0], p[1], srcPoly);
        assertEquals("parallel dist", d, dist, 5e-3);
      }
    }
  }

  /** Curved buffer area matches core BufferOp on the densified source (convex d>0). */
  public void testDensifiedCrossCheck() {
    Random rnd = new Random(7L);
    int checked = 0;
    CircularString[] shapes = { disk(0,0,5), stadium(), square10(), triangle() };
    for (CircularString s : shapes) {
      for (int it = 0; it < 12; it++) {
        double d = 0.3 + rnd.nextDouble() * 3.0;
        Geometry buf = CurvedBuffer.buffer(cp(s), d);
        double arc = arcArea(((CircularString) ((CurvePolygon) buf).getExteriorCurve()).getCoordinateSequence());
        Polygon src = (Polygon) cp(s).toLinear(0.002);
        double ref = src.buffer(d, 256).getArea();
        assertEquals("densified d="+d, ref, arc, ref * 2e-3);
        checked++;
      }
    }
    assertTrue(checked > 40);
  }

  /** Reflex/inward-cornered buffer throws UnsupportedOperationException. */
  public void testReflexAndInwardCornerUnsupported() {
    CircularString arrow = cs(new double[][]{ {0,0},{5,-1},{10,0}, {7,4},{4,8}, {3,4},{0,0} });
    try { CurvedBuffer.buffer(cp(arrow), 1.0); fail("reflex should be unsupported"); }
    catch (UnsupportedOperationException ok) { }
    try { CurvedBuffer.buffer(cp(square10()), -1.0); fail("inward cornered should be unsupported"); }
    catch (UnsupportedOperationException ok) { }
  }

  // ---- helpers: arc-aware area + parallel-distance ----

  private void assertArcArea(String msg, Geometry buf, double expected) {
    assertFalse(msg + " non-empty", buf.isEmpty());
    CoordinateSequence s = ((CircularString) ((CurvePolygon) buf).getExteriorCurve()).getCoordinateSequence();
    assertEquals(msg, expected, arcArea(s), 1e-6);
  }

  /** Arc-aware unsigned area of a closed curved ring (shoelace + circular-segment corrections). */
  private static double arcArea(CoordinateSequence ring) {
    int n = ring.size();
    double area = 0.0;
    for (int i = 0; i + 2 < n; i += 2)
      area += ring.getX(i) * ring.getY(i + 2) - ring.getX(i + 2) * ring.getY(i);
    area *= 0.5;
    for (int i = 0; i + 2 < n; i += 2) {
      double sx=ring.getX(i),sy=ring.getY(i),mx=ring.getX(i+1),my=ring.getY(i+1),ex=ring.getX(i+2),ey=ring.getY(i+2);
      double det=2*(sx*(my-ey)+mx*(ey-sy)+ex*(sy-my));
      if (det==0.0) continue;
      double s2=sx*sx+sy*sy,m2=mx*mx+my*my,e2=ex*ex+ey*ey;
      double cx=(s2*(my-ey)+m2*(ey-sy)+e2*(sy-my))/det, cy=(s2*(ex-mx)+m2*(sx-ex)+e2*(mx-sx))/det;
      double r=Math.hypot(sx-cx,sy-cy);
      double a0=Math.atan2(sy-cy,sx-cx),am=Math.atan2(my-cy,mx-cx),ae=Math.atan2(ey-cy,ex-cx);
      boolean ccw=det>0;
      double theta=sweep(a0,am,ccw)+sweep(am,ae,ccw);
      double segm=0.5*r*r*(theta-Math.sin(theta));
      area += ccw ? segm : -segm;
    }
    return Math.abs(area);
  }

  private static double[] piece(CoordinateSequence s, int i) {
    int b=2*i; return new double[]{ s.getX(b),s.getY(b),s.getX(b+1),s.getY(b+1),s.getX(b+2),s.getY(b+2) };
  }
  private static double[] pointOnPiece(double[] p, double t) {
    double det=2*(p[0]*(p[3]-p[5])+p[2]*(p[5]-p[1])+p[4]*(p[1]-p[3]));
    if (det==0.0) return new double[]{ p[0]+t*(p[4]-p[0]), p[1]+t*(p[5]-p[1]) };
    double s2=p[0]*p[0]+p[1]*p[1],m2=p[2]*p[2]+p[3]*p[3],e2=p[4]*p[4]+p[5]*p[5];
    double cx=(s2*(p[3]-p[5])+m2*(p[5]-p[1])+e2*(p[1]-p[3]))/det, cy=(s2*(p[4]-p[2])+m2*(p[0]-p[4])+e2*(p[2]-p[0]))/det;
    double r=Math.hypot(p[0]-cx,p[1]-cy);
    boolean ccw=det>0; double a0=Math.atan2(p[1]-cy,p[0]-cx);
    double am=Math.atan2(p[3]-cy,p[2]-cx),ae=Math.atan2(p[5]-cy,p[4]-cx);
    double theta=sweep(a0,am,ccw)+sweep(am,ae,ccw);
    double ang=a0+(ccw?1:-1)*theta*t;
    return new double[]{ cx+r*Math.cos(ang), cy+r*Math.sin(ang) };
  }
  private static Coordinate[] densify(CircularString s, int per) {
    CoordinateSequence q=s.getCoordinateSequence(); int n=q.size();
    java.util.List<Coordinate> out=new java.util.ArrayList<Coordinate>();
    for (int i=0;2*i+2<n;i++){ double[] a=piece(q,i); int steps=per; for(int k=0;k<steps;k++) { double[] p=pointOnPiece(a,(double)k/steps); out.add(new Coordinate(p[0],p[1])); } }
    out.add(out.get(0));
    return out.toArray(new Coordinate[0]);
  }
  private static double minDistToPolyline(double px, double py, Coordinate[] poly) {
    double best=Double.MAX_VALUE;
    for (int i=0;i+1<poly.length;i++) best=Math.min(best, distToSeg(px,py,poly[i].x,poly[i].y,poly[i+1].x,poly[i+1].y));
    return best;
  }
  private static double distToSeg(double px,double py,double x1,double y1,double x2,double y2){
    double dx=x2-x1,dy=y2-y1,l2=dx*dx+dy*dy; if(l2==0)return Math.hypot(px-x1,py-y1);
    double t=Math.max(0,Math.min(1,((px-x1)*dx+(py-y1)*dy)/l2));
    return Math.hypot(px-(x1+t*dx),py-(y1+t*dy));
  }
  private static double sweep(double f,double t,boolean ccw){ double x=ccw?(t-f):(f-t); x%=2*Math.PI; if(x<0)x+=2*Math.PI; return x; }
}

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

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.CoordinateSequence;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.geom.impl.CoordinateArraySequence;

import junit.framework.TestCase;
import junit.textui.TestRunner;

/**
 * R-PR (#1195) — arc-aware relate predicates for two simple curved areal rings.
 * <p>
 * Pinned to the oracle {@code CURVE_RELATE_MATRIX} mode: the curated configs use
 * the oracle's TRUE-OGC DE-9IM matrices (independently confirmed against the
 * oracle binary) reduced to the named predicates. Breadth comes from an analytic
 * disk ground-truth battery (circle geometry) and a densified cross-check against
 * JTS core {@code RelateOp} on tessellated polygons.
 */
public class ArcRelateTest extends TestCase {

  public static void main(String[] args) { TestRunner.run(ArcRelateTest.class); }
  public ArcRelateTest(String name) { super(name); }

  private static final GeometryFactory GF = new GeometryFactory();

  // ---------- ring builders (closed control-point sequences) ----------

  /** Full circle as a CCW ring of two semicircle arcs. */
  private static CoordinateSequence disk(double cx, double cy, double r) {
    return seq(new double[][]{
        {cx + r, cy}, {cx, cy + r}, {cx - r, cy}, {cx, cy - r}, {cx + r, cy} });
  }

  /** Axis-aligned square as a CCW ring of four chords (collinear-triple pieces). */
  private static CoordinateSequence square(double cx, double cy, double h) {
    double[][] v = { {cx-h,cy-h}, {cx+h,cy-h}, {cx+h,cy+h}, {cx-h,cy+h}, {cx-h,cy-h} };
    List<double[]> pts = new ArrayList<double[]>();
    for (int i = 0; i + 1 < v.length; i++) {
      pts.add(v[i]);
      pts.add(new double[]{ 0.5*(v[i][0]+v[i+1][0]), 0.5*(v[i][1]+v[i+1][1]) });  // collinear mid
    }
    pts.add(v[v.length - 1]);
    return seq(pts.toArray(new double[0][]));
  }

  private static CoordinateSequence seq(double[][] xy) {
    Coordinate[] c = new Coordinate[xy.length];
    for (int i = 0; i < xy.length; i++) c[i] = new Coordinate(xy[i][0], xy[i][1]);
    return new CoordinateArraySequence(c);
  }

  // ---------- curated oracle anchors (CURVE_RELATE_MATRIX, TRUE OGC) ----------

  /** [intersects, disjoint, contains, within, overlaps, touches, equals, covers, coveredBy]. */
  private boolean[] preds(CoordinateSequence a, CoordinateSequence b) {
    return new boolean[]{
        ArcRelate.intersects(a, b), ArcRelate.disjoint(a, b),
        ArcRelate.contains(a, b),   ArcRelate.within(a, b),
        ArcRelate.overlaps(a, b),   ArcRelate.touches(a, b),
        ArcRelate.equals(a, b),     ArcRelate.covers(a, b), ArcRelate.coveredBy(a, b) };
  }

  private void assertPreds(String msg, boolean[] got, boolean[] exp) {
    String[] nm = {"intersects","disjoint","contains","within","overlaps","touches","equals","covers","coveredBy"};
    for (int i = 0; i < exp.length; i++)
      assertEquals(msg + " [" + nm[i] + "]", exp[i], got[i]);
  }

  // exp helper: I=intersects D=disjoint C=contains W=within O=overlaps T=touches E=equals Cv=covers CvB=coveredBy
  private static boolean[] e(boolean I,boolean D,boolean C,boolean W,boolean O,boolean T,boolean E,boolean Cv,boolean CvB){
    return new boolean[]{I,D,C,W,O,T,E,Cv,CvB};
  }

  public void testCuratedOracleConfigs() {
    // disjoint disks  FF2FF1212
    assertPreds("disjoint disks", preds(disk(0,0,2), disk(10,0,2)),
        e(false,true,  false,false, false,false, false, false,false));
    // A contains B  212FF1FF2
    assertPreds("A contains B", preds(disk(0,0,5), disk(0,0,1)),
        e(true,false,  true,false,  false,false, false, true,false));
    // B within A -> A within B is the swap: 2FF1FF212 is "B within A" i.e. relate(small,big)
    assertPreds("A within B", preds(disk(0,0,1), disk(0,0,5)),
        e(true,false,  false,true,  false,false, false, false,true));
    // overlapping disks  212101212
    assertPreds("overlap", preds(disk(0,0,3), disk(4,0,3)),
        e(true,false,  false,false, true,false,  false, false,false));
    // externally tangent  FF2F01212
    assertPreds("externally tangent", preds(disk(0,0,2), disk(4,0,2)),
        e(true,false,  false,false, false,true,  false, false,false));
    // equal disks  2FFF1FFF2  (contains/within/covers all reflexively true)
    assertPreds("equal", preds(disk(0,0,2), disk(0,0,2)),
        e(true,false,  true,true,   false,false, true,  true,true));
    // internal tangency: B within A, boundaries touch  212F01FF2 (contains true, not overlap/touch)
    assertPreds("internal tangency contains", preds(disk(0,0,5), disk(2,0,3)),
        e(true,false,  true,false,  false,false, false, true,false));
    // square contains disk  212FF1FF2
    assertPreds("square contains disk", preds(square(0,0,5), disk(0,0,1)),
        e(true,false,  true,false,  false,false, false, true,false));
    // disk in annulus is out of single-ring MVP scope; disk vs far square = disjoint
    assertPreds("square disjoint disk", preds(square(0,0,2), disk(10,0,1)),
        e(false,true,  false,false, false,false, false, false,false));
  }

  public void testSymmetryLaws() {
    CoordinateSequence[] gs = {
        disk(0,0,5), disk(0,0,1), disk(4,0,3), disk(10,0,2), disk(2,0,3), square(0,0,5) };
    for (CoordinateSequence a : gs) for (CoordinateSequence b : gs) {
      assertEquals("contains==within^T", ArcRelate.contains(a,b), ArcRelate.within(b,a));
      assertEquals("covers==coveredBy^T", ArcRelate.covers(a,b), ArcRelate.coveredBy(b,a));
      assertEquals("intersects symmetric", ArcRelate.intersects(a,b), ArcRelate.intersects(b,a));
      assertEquals("disjoint symmetric", ArcRelate.disjoint(a,b), ArcRelate.disjoint(b,a));
      assertEquals("overlaps symmetric", ArcRelate.overlaps(a,b), ArcRelate.overlaps(b,a));
      assertEquals("touches symmetric", ArcRelate.touches(a,b), ArcRelate.touches(b,a));
      assertEquals("equals symmetric", ArcRelate.equals(a,b), ArcRelate.equals(b,a));
      assertEquals("disjoint == !intersects", ArcRelate.disjoint(a,b), !ArcRelate.intersects(a,b));
    }
  }

  /** Random disk pairs: ground-truth relation from circle geometry (general position only). */
  public void testRandomDisksAnalyticTruth() {
    Random rnd = new Random(20260618L);
    int checked = 0;
    for (int it = 0; it < 2000; it++) {
      double ax = rnd.nextDouble()*20-10, ay = rnd.nextDouble()*20-10, rA = 1+rnd.nextDouble()*5;
      double bx = rnd.nextDouble()*20-10, by = rnd.nextDouble()*20-10, rB = 1+rnd.nextDouble()*5;
      double d = Math.hypot(bx-ax, by-ay);
      final double M = 0.25;                          // general-position margin (avoid tangency)
      Boolean rel = null;  // "DISJOINT","OVERLAP","A_CONTAINS_B","B_CONTAINS_A"
      String r;
      if (d > rA + rB + M) r = "DISJOINT";
      else if (d + rB + M < rA) r = "A_CONTAINS_B";
      else if (d + rA + M < rB) r = "B_CONTAINS_A";
      else if (d > Math.abs(rA-rB) + M && d < rA + rB - M) r = "OVERLAP";
      else continue;                                  // near a degenerate boundary: skip
      checked++;
      CoordinateSequence A = disk(ax,ay,rA), B = disk(bx,by,rB);
      boolean[] g = preds(A, B);
      // [I,D,C,W,O,T,E,Cv,CvB]
      if (r.equals("DISJOINT"))     assertPreds("rnd disjoint",     g, e(false,true,  false,false, false,false, false, false,false));
      if (r.equals("OVERLAP"))      assertPreds("rnd overlap",      g, e(true,false,  false,false, true,false,  false, false,false));
      if (r.equals("A_CONTAINS_B")) assertPreds("rnd A contains B", g, e(true,false,  true,false,  false,false, false, true,false));
      if (r.equals("B_CONTAINS_A")) assertPreds("rnd B contains A", g, e(true,false,  false,true,  false,false, false, false,true));
    }
    assertTrue("enough general-position cases (" + checked + ")", checked > 300);
  }

  /** Densified cross-check vs JTS core on tessellated polygons (general-position curved rings). */
  public void testDensifiedCrossCheckCurved() {
    Random rnd = new Random(99L);
    int checked = 0;
    for (int it = 0; it < 1500; it++) {
      double ax = rnd.nextDouble()*16-8, ay = rnd.nextDouble()*16-8, rA = 1.5+rnd.nextDouble()*4;
      double bx = rnd.nextDouble()*16-8, by = rnd.nextDouble()*16-8, rB = 1.5+rnd.nextDouble()*4;
      double d = Math.hypot(bx-ax, by-ay);
      final double M = 0.4;
      boolean general = d > rA+rB+M || d+rB+M < rA || d+rA+M < rB
          || (d > Math.abs(rA-rB)+M && d < rA+rB-M);
      if (!general) continue;
      checked++;
      CoordinateSequence A = disk(ax,ay,rA), B = disk(bx,by,rB);
      Polygon pa = GF.createPolygon(densify(A, 720));
      Polygon pb = GF.createPolygon(densify(B, 720));
      String cfg = String.format("A(%.4f,%.4f,r%.4f) B(%.4f,%.4f,r%.4f) d=%.4f",ax,ay,rA,bx,by,rB,d);
      assertEquals("intersects "+cfg, pa.intersects(pb), ArcRelate.intersects(A,B));
      assertEquals("disjoint "+cfg,   pa.disjoint(pb),   ArcRelate.disjoint(A,B));
      assertEquals("contains "+cfg,   pa.contains(pb),   ArcRelate.contains(A,B));
      assertEquals("within "+cfg,     pa.within(pb),     ArcRelate.within(A,B));
      assertEquals("overlaps "+cfg,   pa.overlaps(pb),   ArcRelate.overlaps(A,B));
      assertEquals("covers "+cfg,     pa.covers(pb),     ArcRelate.covers(A,B));
    }
    assertTrue("enough cross-checked cases (" + checked + ")", checked > 200);
  }

  // ---------- arc tessellation for the densified cross-check ----------

  private static Coordinate[] densify(CoordinateSequence ring, int perCircle) {
    List<Coordinate> out = new ArrayList<Coordinate>();
    int n = ring.size();
    for (int i = 0; i + 2 < n; i += 2) {
      double sx=ring.getX(i), sy=ring.getY(i), mx=ring.getX(i+1), my=ring.getY(i+1),
             ex=ring.getX(i+2), ey=ring.getY(i+2);
      double det = 2*(sx*(my-ey)+mx*(ey-sy)+ex*(sy-my));
      if (det == 0.0) { out.add(new Coordinate(sx,sy)); continue; }   // chord: emit start only
      double s2=sx*sx+sy*sy, m2=mx*mx+my*my, e2=ex*ex+ey*ey;
      double cx=(s2*(my-ey)+m2*(ey-sy)+e2*(sy-my))/det;
      double cy=(s2*(ex-mx)+m2*(sx-ex)+e2*(mx-sx))/det;
      double r=Math.hypot(sx-cx,sy-cy);
      double a0=Math.atan2(sy-cy,sx-cx), am=Math.atan2(my-cy,mx-cx), ae=Math.atan2(ey-cy,ex-cx);
      boolean ccw=det>0;
      double theta=sweep(a0,am,ccw)+sweep(am,ae,ccw);
      int steps = Math.max(2, (int)Math.ceil(theta/(2*Math.PI)*perCircle));
      for (int k=0; k<steps; k++) {
        double ang = a0 + (ccw?1:-1)*theta*k/steps;
        out.add(new Coordinate(cx+r*Math.cos(ang), cy+r*Math.sin(ang)));
      }
    }
    out.add(out.get(0));   // close
    return out.toArray(new Coordinate[0]);
  }

  private static double sweep(double from, double to, boolean ccw) {
    double t = ccw ? (to - from) : (from - to);
    t %= 2 * Math.PI;
    if (t < 0) t += 2 * Math.PI;
    return t;
  }
}

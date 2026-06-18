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
import java.util.Arrays;
import java.util.List;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.CoordinateSequence;
import org.locationtech.jts.geom.impl.CoordinateArraySequence;

import junit.framework.TestCase;
import junit.textui.TestRunner;

/**
 * N-SS (#1195): {@link ArcNoder} nodes arc strings at their crossings using the
 * oracle-pinned {@link CircularArcs} primitives, and {@link ArcSegmentString}
 * splits each at its nodes into sub-arcs on the same circle. Verified with
 * geometric anchors, split-reconstruction checks, and an independent densified
 * brute-force cross-check.
 */
public class ArcNoderTest extends TestCase {

  public static void main(String args[]) {
    TestRunner.run(ArcNoderTest.class);
  }

  public ArcNoderTest(String name) { super(name); }

  private static ArcSegmentString arcStr(double... xy) {
    Coordinate[] p = new Coordinate[xy.length / 2];
    for (int i = 0; i < p.length; i++) p[i] = new Coordinate(xy[2*i], xy[2*i+1]);
    return new ArcSegmentString(new CoordinateArraySequence(p), null);
  }

  // circle A: centre (0,0) r5, right semicircle (open) from (0,5) to (0,-5)
  private static ArcSegmentString circAright() { return arcStr(0,5, 5,0, 0,-5); }
  // circle B: centre (6,0) r5, left semicircle (open) from (6,5) to (6,-5) (covers x<=6)
  private static ArcSegmentString circBleft()  { return arcStr(6,5, 1,0, 6,-5); }

  /** Two open arcs of circles (0,0)r5 and (6,0)r5 cross at (3,+/-4). */
  public void testTwoCrossingArcs() {
    ArcNoder n = new ArcNoder();
    n.computeNodes(Arrays.asList(circAright(), circBleft()));
    List<double[]> nodes = n.getNodePoints();
    assertEquals(2, nodes.size());
    assertTrue(hasPt(nodes, 3, 4));
    assertTrue(hasPt(nodes, 3, -4));
    // each open arc with 2 interior nodes -> 3 sub-strings
    long fromA = 0, fromB = 0;
    for (ArcSegmentString s : n.getNodedSubstrings()) {
      // classify by which circle the sub-arc lies on (centre (0,0) vs (6,0))
      double[] a = s.arc(0);
      if (onCircle(a, 0, 0, 5)) fromA++; else if (onCircle(a, 6, 0, 5)) fromB++;
    }
    assertEquals(3, fromA);
    assertEquals(3, fromB);
  }

  /** Splitting a semicircle at one crossing yields two sub-arcs on the same circle whose lengths sum to the whole. */
  public void testSplitReconstructsArc() {
    ArcSegmentString arc = arcStr(5,0, 0,5, -5,0);          // upper semicircle, centre 0, r5
    ArcSegmentString chord = arcStr(3,-10, 3,0, 3,10);      // vertical chord x=3 (collinear triple)
    ArcNoder n = new ArcNoder();
    n.computeNodes(Arrays.asList(arc, chord));
    // the chord crosses the upper arc at (3,4)
    assertTrue(hasPt(n.getNodePoints(), 3, 4));
    List<ArcSegmentString> subs = new ArrayList<ArcSegmentString>();
    for (ArcSegmentString s : n.getNodedSubstrings()) if (onCircle(s.arc(0), 0, 0, 5)) subs.add(s);
    assertEquals(2, subs.size());
    double total = 0;
    for (ArcSegmentString s : subs) {
      double[] a = s.arc(0);
      assertEquals("mid on circle", 5.0, Math.hypot(a[2], a[3]), 1e-9);
      total += CircularArcs.arcLength(a[0],a[1],a[2],a[3],a[4],a[5]);
    }
    assertEquals("sub-arc lengths reconstruct the semicircle", Math.PI * 5, total, 1e-7);
  }

  /** Disjoint arcs are not noded (returned unchanged). */
  public void testDisjointUnchanged() {
    ArcNoder n = new ArcNoder();
    n.computeNodes(Arrays.asList(circAright(), arcStr(26,5, 21,0, 26,-5)));   // circle (20,0) r5 far away
    assertEquals(0, n.getNodePoints().size());
    assertEquals(2, n.getNodedSubstrings().size());
  }

  /** Independent densified brute-force cross-check of the node points. */
  public void testMatchesDensifiedIntersections() {
    ArcSegmentString a = circAright(), b = circBleft();
    ArcNoder n = new ArcNoder();
    n.computeNodes(Arrays.asList(a, b));
    List<double[]> ref = densifiedCrossings(circAright(), circBleft(), 3000);
    assertEquals("crossing count", ref.size(), n.getNodePoints().size());
    for (double[] r : ref) {
      boolean matched = false;
      for (double[] node : n.getNodePoints())
        if (Math.hypot(node[0]-r[0], node[1]-r[1]) < 1e-2) { matched = true; break; }
      assertTrue("node near densified crossing (" + r[0] + "," + r[1] + ")", matched);
    }
  }

  // ---- helpers ----

  private static boolean hasPt(List<double[]> pts, double x, double y) {
    for (double[] p : pts) if (Math.hypot(p[0]-x, p[1]-y) < 1e-7) return true;
    return false;
  }

  private static boolean onCircle(double[] arc, double cx, double cy, double r) {
    // classify by the mid point: the crossing endpoints lie on both circles, but the
    // mid uniquely identifies the sub-arc's own circle.
    return Math.abs(Math.hypot(arc[2]-cx, arc[3]-cy) - r) < 1e-6;
  }

  /** Densify both arc strings to fine polylines and brute-force their segment intersections (clustered). */
  private static List<double[]> densifiedCrossings(ArcSegmentString a, ArcSegmentString b, int nPerArc) {
    Coordinate[] pa = densify(a, nPerArc), pb = densify(b, nPerArc);
    List<double[]> out = new ArrayList<double[]>();
    for (int i = 0; i + 1 < pa.length; i++) {
      for (int j = 0; j + 1 < pb.length; j++) {
        double[] x = seg(pa[i], pa[i+1], pb[j], pb[j+1]);
        if (x == null) continue;
        boolean dup = false;
        for (double[] o : out) if (Math.hypot(o[0]-x[0], o[1]-x[1]) < 1e-2) { dup = true; break; }
        if (!dup) out.add(x);
      }
    }
    return out;
  }

  private static Coordinate[] densify(ArcSegmentString s, int n) {
    CoordinateSequence seq = s.getCoordinateSequence();
    List<Coordinate> out = new ArrayList<Coordinate>();
    for (int i = 0; i + 2 < seq.size(); i += 2) {
      double sx=seq.getX(i),sy=seq.getY(i),mx=seq.getX(i+1),my=seq.getY(i+1),ex=seq.getX(i+2),ey=seq.getY(i+2);
      double d=2*(sx*(my-ey)+mx*(ey-sy)+ex*(sy-my));
      double s2=sx*sx+sy*sy,m2=mx*mx+my*my,e2=ex*ex+ey*ey;
      double cx=(s2*(my-ey)+m2*(ey-sy)+e2*(sy-my))/d, cy=(s2*(ex-mx)+m2*(sx-ex)+e2*(mx-sx))/d;
      double r=Math.hypot(sx-cx,sy-cy);
      double a0=Math.atan2(sy-cy,sx-cx), am=Math.atan2(my-cy,mx-cx), ae=Math.atan2(ey-cy,ex-cx);
      boolean ccw=d>0;
      double th=sweep(a0,am,ccw)+sweep(am,ae,ccw);
      int dir=ccw?1:-1, start=(i==0)?0:1;
      for (int k=start;k<=n;k++){ double ang=a0+dir*th*k/n; out.add(new Coordinate(cx+r*Math.cos(ang),cy+r*Math.sin(ang))); }
    }
    return out.toArray(new Coordinate[0]);
  }

  private static double sweep(double f,double t,boolean ccw){ double x=ccw?(t-f):(f-t); x%=2*Math.PI; if(x<0)x+=2*Math.PI; return x; }

  private static double[] seg(Coordinate p1, Coordinate p2, Coordinate p3, Coordinate p4) {
    double d=(p2.x-p1.x)*(p4.y-p3.y)-(p2.y-p1.y)*(p4.x-p3.x);
    if (Math.abs(d)<1e-15) return null;
    double t=((p3.x-p1.x)*(p4.y-p3.y)-(p3.y-p1.y)*(p4.x-p3.x))/d;
    double u=((p3.x-p1.x)*(p2.y-p1.y)-(p3.y-p1.y)*(p2.x-p1.x))/d;
    if (t<0||t>1||u<0||u>1) return null;
    return new double[]{ p1.x+t*(p2.x-p1.x), p1.y+t*(p2.y-p1.y) };
  }
}

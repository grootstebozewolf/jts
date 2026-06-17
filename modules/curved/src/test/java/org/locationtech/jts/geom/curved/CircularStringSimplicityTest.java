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
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.impl.CoordinateArraySequence;

import junit.framework.TestCase;
import junit.textui.TestRunner;

/**
 * V-CS (#1195): {@link CircularString#isSimple()} is arc-aware — the curve is
 * simple iff its circular arcs do not cross, touch tangentially, or overlap,
 * except at shared adjacency endpoints (and the closing endpoint when closed) —
 * rather than testing the chord polyline inherited from {@link LineString}. The
 * pairwise crossings reuse the oracle-pinned {@code CircularArcs.intersectArc} /
 * {@code intersectSegment}; this test adds geometric anchors and an independent
 * densified {@code isSimple} cross-check.
 */
public class CircularStringSimplicityTest extends TestCase {

  public static void main(String args[]) {
    TestRunner.run(CircularStringSimplicityTest.class);
  }

  public CircularStringSimplicityTest(String name) { super(name); }

  private final CurvedGeometryFactory gf = new CurvedGeometryFactory();

  private CircularString cs(double... xy) {
    Coordinate[] pts = new Coordinate[xy.length / 2];
    for (int i = 0; i < pts.length; i++)
      pts[i] = new Coordinate(xy[2 * i], xy[2 * i + 1]);
    return gf.createCircularString(new CoordinateArraySequence(pts));
  }

  // ---- simple cases ----

  public void testSingleArcIsSimple() {
    assertTrue(cs(5,0, 0,5, -5,0).isSimple());
  }

  public void testSCurveIsSimple() {
    // two arcs bumping opposite ways, meeting only at the shared joint (2,0)
    assertTrue(cs(0,0, 1,1, 2,0, 3,-1, 4,0).isSimple());
  }

  public void testClosedCircleFromTwoSemisIsSimple() {
    // a full circle as two semicircular arcs: shares both endpoints, no overlap
    assertTrue(cs(5,0, 0,5, -5,0, 0,-5, 5,0).isSimple());
  }

  public void testCollinearChainIsSimple() {
    assertTrue(cs(0,0, 1,0, 2,0, 3,0, 4,0).isSimple());
  }

  // ---- non-simple cases ----

  public void testCrossingArcsNotSimple() {
    // arcs share joint (4,0) but their circles also cross at ~(1.351,1.892)
    // (confirmed against the ARC_ARC_XY oracle)
    assertFalse(cs(0,0, 2,2, 4,0, 1,3, 2,1).isSimple());
  }

  public void testCollinearRetraceNotSimple() {
    // straight out to (2,0) then back over (1,2): a positive-length overlap
    assertFalse(cs(0,0, 1,0, 2,0, 1.5,0, 1,0).isSimple());
  }

  public void testArcAwareDiffersFromChordPolyline() {
    // The chord polyline (0,0)-(2,2)-(4,0)-(1,3)-(2,1) and the true arcs can give
    // different simplicity verdicts; isSimple() must reflect the arcs.
    CircularString c = cs(0,0, 2,2, 4,0, 1,3, 2,1);
    assertFalse(c.isSimple());
  }

  // ---- independent densified cross-check ----

  /**
   * For random chains of arcs, the arc-aware verdict must agree with the verdict
   * of a finely densified polyline (the chord-tessellation of the same arcs).
   */
  public void testMatchesDensifiedIsSimple() {
    Random rnd = new Random(20260617L);
    int simple = 0, nonSimple = 0;
    for (int t = 0; t < 60; t++) {
      CircularString c = randomChain(rnd, 2 + rnd.nextInt(3));
      LineString dense = gf.createLineString(densify(c, 400));
      boolean arc = c.isSimple();
      boolean poly = dense.isSimple();
      assertEquals("verdict mismatch for " + c, poly, arc);
      if (arc) simple++; else nonSimple++;
    }
    // the battery should exercise both verdicts, or the cross-check is vacuous
    assertTrue("expected some simple chains", simple > 0);
    assertTrue("expected some non-simple chains", nonSimple > 0);
  }

  private CircularString randomChain(Random rnd, int nArcs) {
    List<Coordinate> pts = new ArrayList<Coordinate>();
    double x = rnd.nextInt(11) - 5, y = rnd.nextInt(11) - 5;
    pts.add(new Coordinate(x, y));
    for (int a = 0; a < nArcs; a++) {
      pts.add(new Coordinate(rnd.nextInt(11) - 5, rnd.nextInt(11) - 5));   // mid
      pts.add(new Coordinate(rnd.nextInt(11) - 5, rnd.nextInt(11) - 5));   // end
    }
    return gf.createCircularString(new CoordinateArraySequence(pts.toArray(new Coordinate[0])));
  }

  private Coordinate[] densify(CircularString c, int nPerArc) {
    CoordinateSequence seq = c.getCoordinateSequence();
    int n = seq.size();
    List<Coordinate> out = new ArrayList<Coordinate>();
    for (int i = 0; i + 2 < n; i += 2) {
      double sx = seq.getX(i),     sy = seq.getY(i);
      double mx = seq.getX(i + 1), my = seq.getY(i + 1);
      double ex = seq.getX(i + 2), ey = seq.getY(i + 2);
      double d = 2 * (sx*(my-ey) + mx*(ey-sy) + ex*(sy-my));
      int kstart = (i == 0) ? 0 : 1;
      if (d == 0.0) {                                  // collinear: straight chord
        if (kstart == 0) out.add(new Coordinate(sx, sy));
        out.add(new Coordinate(ex, ey));
        continue;
      }
      double s2 = sx*sx+sy*sy, m2 = mx*mx+my*my, e2 = ex*ex+ey*ey;
      double cx = (s2*(my-ey) + m2*(ey-sy) + e2*(sy-my)) / d;
      double cy = (s2*(ex-mx) + m2*(sx-ex) + e2*(mx-sx)) / d;
      double r = Math.hypot(sx-cx, sy-cy);
      double a0 = Math.atan2(sy-cy, sx-cx);
      double am = Math.atan2(my-cy, mx-cx);
      double ae = Math.atan2(ey-cy, ex-cx);
      boolean ccw = d > 0;
      double theta = sweep(a0, am, ccw) + sweep(am, ae, ccw);
      int dir = ccw ? 1 : -1;
      for (int kk = kstart; kk <= nPerArc; kk++) {
        double ang = a0 + dir * theta * kk / nPerArc;
        out.add(new Coordinate(cx + r*Math.cos(ang), cy + r*Math.sin(ang)));
      }
    }
    return out.toArray(new Coordinate[0]);
  }

  private static double sweep(double from, double to, boolean ccw) {
    double t = ccw ? (to - from) : (from - to);
    t %= 2 * Math.PI;
    if (t < 0) t += 2 * Math.PI;
    return t;
  }
}

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

import org.locationtech.jts.densify.Densifier;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.impl.CoordinateArraySequence;

import junit.framework.TestCase;
import junit.textui.TestRunner;

/**
 * DSF (#1195): densifying a curved geometry must sample the actual arcs, not
 * subdivide the control-point chords. {@link CircularString#toLinear(double)}
 * now tessellates each arc to a sagitta tolerance (so the points lie on the
 * curve), and {@link CurvedDensifier} routes curved inputs through it while
 * delegating everything else to the core {@link Densifier}.
 */
public class CurvedDensifierTest extends TestCase {

  public static void main(String args[]) {
    TestRunner.run(CurvedDensifierTest.class);
  }

  public CurvedDensifierTest(String name) { super(name); }

  private final CurvedGeometryFactory gf = new CurvedGeometryFactory();

  private CircularString cs(double... xy) {
    Coordinate[] pts = new Coordinate[xy.length / 2];
    for (int i = 0; i < pts.length; i++)
      pts[i] = new Coordinate(xy[2 * i], xy[2 * i + 1]);
    return gf.createCircularString(new CoordinateArraySequence(pts));
  }

  /** tolerance <= 0 keeps the bare control points (phase-1 toLinear(0) contract). */
  public void testZeroToleranceIsControlPoints() {
    LineString lin = (LineString) cs(5,0, 0,5, -5,0).toLinear(0.0);
    assertEquals(3, lin.getNumPoints());
    assertEquals(new Coordinate(5,0), lin.getCoordinateN(0));
    assertEquals(new Coordinate(0,5), lin.getCoordinateN(1));
    assertEquals(new Coordinate(-5,0), lin.getCoordinateN(2));
  }

  /** Tessellated points lie on the arc's circle, with exact endpoints. */
  public void testSampledPointsLieOnArc() {
    LineString lin = (LineString) cs(5,0, 0,5, -5,0).toLinear(0.05);
    Coordinate[] c = lin.getCoordinates();
    for (Coordinate p : c)
      assertEquals("on circle r=5", 5.0, Math.hypot(p.x, p.y), 1e-9);
    assertEquals(new Coordinate(5,0), c[0]);
    assertEquals(new Coordinate(-5,0), c[c.length - 1]);
    assertTrue("should add points beyond the 3 control points", c.length > 3);
  }

  /** The chord error (sagitta) is within tolerance, checked against a fine reference arc. */
  public void testSagittaWithinTolerance() {
    double tol = 0.02;
    LineString lin = (LineString) cs(5,0, 0,5, -5,0).toLinear(tol);
    Coordinate[] poly = lin.getCoordinates();
    int N = 4000;
    double maxDev = 0;
    for (int k = 0; k <= N; k++) {
      double ang = Math.PI * k / N;                 // true upper semicircle
      Coordinate ref = new Coordinate(5 * Math.cos(ang), 5 * Math.sin(ang));
      maxDev = Math.max(maxDev, distToPolyline(ref, poly));
    }
    assertTrue("max deviation " + maxDev + " should be <= tol " + tol, maxDev <= tol + 1e-9);
  }

  /** Pins the EPIC §2 figure: a half-circle at 1% sagitta needs ~12 chords (13 points). */
  public void testHalfCircleChordCountMatchesEpic() {
    LineString lin = (LineString) cs(5,0, 0,5, -5,0).toLinear(0.05);   // 1% of r=5
    assertEquals(13, lin.getNumPoints());
  }

  /** A finer tolerance yields more points. */
  public void testFinerToleranceMorePoints() {
    int coarse = ((LineString) cs(5,0, 0,5, -5,0).toLinear(0.1)).getNumPoints();
    int fine   = ((LineString) cs(5,0, 0,5, -5,0).toLinear(0.001)).getNumPoints();
    assertTrue(fine > coarse);
  }

  /** Multi-arc string tessellates every arc and shares joints once. */
  public void testMultiArcTessellation() {
    LineString lin = (LineString) cs(5,0, 0,5, -5,0, 0,-5, 5,0).toLinear(0.05);
    for (Coordinate p : lin.getCoordinates())
      assertEquals(5.0, Math.hypot(p.x, p.y), 1e-9);
    // closed full circle: first == last
    assertEquals(lin.getCoordinateN(0), lin.getCoordinateN(lin.getNumPoints() - 1));
  }

  /** CurvedDensifier routes a curved input through toLinear (on-arc points). */
  public void testDensifierUsesToLinearForArc() {
    CircularString c = cs(5,0, 0,5, -5,0);
    Geometry dens = CurvedDensifier.densify(c, 0.05);
    assertEquals(c.toLinear(0.05), dens);
    for (Coordinate p : dens.getCoordinates())
      assertEquals(5.0, Math.hypot(p.x, p.y), 1e-9);
  }

  /** A plain (non-curved) geometry is delegated to the core Densifier unchanged. */
  public void testDensifierDelegatesPlainGeometry() {
    LineString line = gf.createLineString(new Coordinate[]{ new Coordinate(0,0), new Coordinate(10,0) });
    Geometry viaCurved = CurvedDensifier.densify(line, 2.5);
    Geometry viaCore = Densifier.densify(line, 2.5);
    assertEquals(viaCore, viaCurved);
  }

  private static double distToPolyline(Coordinate p, Coordinate[] poly) {
    double best = Double.MAX_VALUE;
    for (int i = 0; i + 1 < poly.length; i++)
      best = Math.min(best, distToSegment(p, poly[i], poly[i + 1]));
    return best;
  }

  private static double distToSegment(Coordinate p, Coordinate a, Coordinate b) {
    double dx = b.x - a.x, dy = b.y - a.y;
    double l2 = dx * dx + dy * dy;
    if (l2 == 0) return p.distance(a);
    double t = ((p.x - a.x) * dx + (p.y - a.y) * dy) / l2;
    t = Math.max(0, Math.min(1, t));
    return Math.hypot(p.x - (a.x + t * dx), p.y - (a.y + t * dy));
  }
}

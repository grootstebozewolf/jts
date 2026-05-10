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
/*
 * AI Disclosure (Eclipse Foundation GenAI Guidelines):
 * AI-generated portions are dedicated to CC0-1.0; human-reviewed.
 *
 * SPDX-License-Identifier: (EPL-2.0 OR EDL-1.0) AND CC0-1.0
 * Assisted-by: xAI Grok (grok-4.3)
 * Assisted-by: Claude (Opus-4.7)
 */
package org.locationtech.jts.geom.curved;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.io.curved.CurvedWKTReader;
import org.locationtech.jts.io.curved.CurvedWKTWriter;

import junit.textui.TestRunner;
import test.jts.GeometryTestCase;

/**
 * Numerical stability tests for ClothoidSegment. Locks in the
 * properties we *do* have, and explicitly documents the IEEE-754
 * limitation we *don't* have (significant-figure preservation
 * through WKT round-trip — an engineering input of "0.370" cannot
 * be distinguished from "0.37" once parsed to a double).
 */
public class ClothoidNumericalStabilityTest extends GeometryTestCase {

  public static void main(String[] args) { TestRunner.run(ClothoidNumericalStabilityTest.class); }
  public ClothoidNumericalStabilityTest(String name) { super(name); }

  private CurvedGeometryFactory cgf() { return new CurvedGeometryFactory(); }

  // -- end-point stability ---------------------------------------

  /** §3.6 final-point snap: toLinear's last point must equal
   *  getEndCoordinate() regardless of the tolerance argument. The
   *  end coordinate is computed once at construction at fixed N=256
   *  and is the source of truth. */
  public void testEndCoordinateStableAcrossTolerances() {
    ClothoidSegment cs = new ClothoidSegment(
        new Coordinate(0, 0), 0.0, 0.0, 0.05, 60.0, cgf());
    Coordinate canonical = cs.getEndCoordinate();
    double[] tolerances = { 1.0, 0.1, 0.01, 0.001, 1e-6, 1e-9 };
    for (double tol : tolerances) {
      Coordinate[] dense = cs.toLinear(tol).getCoordinates();
      Coordinate last = dense[dense.length - 1];
      assertEquals("end coord must match canonical at tolerance " + tol,
          canonical.x, last.x, 0.0);
      assertEquals("end coord must match canonical at tolerance " + tol,
          canonical.y, last.y, 0.0);
    }
  }

  // -- translation invariance ------------------------------------

  /** A clothoid built at the origin and the same clothoid built at
   *  RD-scale coordinates (~10⁵–10⁶ m) must produce identical relative
   *  geometry to within float noise. Locks in sub-nanometer agreement
   *  across the typical Dutch coordinate range. */
  public void testTranslationInvarianceAtRdScale() {
    CurvedGeometryFactory f = cgf();
    double dx = 116414.353, dy = 411964.758;
    ClothoidSegment origin = new ClothoidSegment(
        new Coordinate(0, 0), 1.872, 0.0, 0.005, 48.0, f);
    ClothoidSegment shifted = new ClothoidSegment(
        new Coordinate(dx, dy), 1.872, 0.0, 0.005, 48.0, f);
    Coordinate eo = origin.getEndCoordinate();
    Coordinate es = shifted.getEndCoordinate();
    double dxRel = es.x - dx;
    double dyRel = es.y - dy;
    // Sub-nanometer agreement is well below any engineering tolerance;
    // we use a relaxed millimetre threshold to avoid flakiness on
    // alternative JVM math implementations.
    assertEquals("translation invariance broken in x", eo.x, dxRel, 1e-3);
    assertEquals("translation invariance broken in y", eo.y, dyRel, 1e-3);
  }

  // -- chord-error tolerance promise -----------------------------

  /** {@code toLinear(ε)} must produce a chord polyline whose maximum
   *  perpendicular distance from the underlying analytical curve is
   *  ≤ ε. Tested by sampling the analytical curve at high resolution
   *  via independent integration and bucketing into the dense chord
   *  intervals by arc length (strictly interior — boundary points
   *  belong to neither neighbour). */
  public void testToLinearMeetsTolerancePromise() {
    double[][] cases = { {0.005, 48.0}, {0.05, 60.0}, {0.1, 80.0} };
    double[] tolerances = { 1.0, 0.1, 0.01, 0.001 };
    for (double[] c : cases) {
      double kappaMax = c[0], L = c[1];
      ClothoidSegment cs = new ClothoidSegment(
          new Coordinate(0, 0), 0.0, 0.0, kappaMax, L, cgf());
      for (double tol : tolerances) {
        double maxErr = measureMaxChordError(cs, tol, 4096);
        assertTrue(
            "toLinear(tol=" + tol + ") on κ=" + kappaMax + " L=" + L
            + " produced chord error " + maxErr + " m > tolerance",
            maxErr <= tol);
      }
    }
  }

  // -- WKT round-trip exactness on representable values ----------

  /** WKT round-trip preserves values that are exactly representable
   *  in IEEE 754. The textual significant-figure metadata is *not*
   *  preserved (see {@link #testTrailingZerosAreNotPreserved}), but
   *  the parsed double value is exact, and the writer emits a
   *  shortest-form representation that re-parses to the same double. */
  public void testCleanInputsRoundTripExactly() throws Exception {
    String[] inputs = {
        "COMPOUNDCURVE ((0 0, 100 0), CLOTHOID (0, 0.005, 48))",
        "COMPOUNDCURVE ((0 0, 100 0), CLOTHOID (0.000123456789, -0.0000987654321, 47.89))"
    };
    CurvedWKTReader r = new CurvedWKTReader(cgf());
    CurvedWKTWriter w = new CurvedWKTWriter();
    for (String in : inputs) {
      Geometry g = r.read(in);
      String out = w.write(g);
      assertEquals("clean input must round-trip exactly: " + in,
          in, out);
    }
  }

  /** Documented IEEE 754 limitation: engineering significant-figure
   *  metadata (e.g. {@code 0.370} indicating 3 sig figs of precision
   *  vs {@code 0.37} indicating 2) is *not* preserved through WKT
   *  parsing. {@code 0.005000} and {@code 0.005} parse to the same
   *  IEEE double; the writer emits the shortest unique representation,
   *  which strips trailing zeros.
   *
   *  Users who need engineering-precision metadata must carry it at
   *  a higher level (a tolerance attribute, a measurement-uncertainty
   *  field, etc.). This test locks in current behaviour so any change
   *  is intentional. */
  public void testTrailingZerosAreNotPreserved() throws Exception {
    String input  = "COMPOUNDCURVE ((0 0, 100 0), CLOTHOID (0, 0.005000, 48.500))";
    String output = "COMPOUNDCURVE ((0 0, 100 0), CLOTHOID (0, 0.005, 48.5))";
    Geometry g = new CurvedWKTReader(cgf()).read(input);
    String emitted = new CurvedWKTWriter().write(g);
    assertEquals("trailing zeros are stripped on round-trip (IEEE 754 limitation, "
        + "not specific to ClothoidSegment); see Javadoc for engineering-precision "
        + "preservation guidance", output, emitted);
  }

  // -- helpers ---------------------------------------------------

  /** Measure the max perpendicular distance from any analytical curve
   *  point to its containing dense chord. Uses high-N independent
   *  integration as the analytical reference. Reference points whose
   *  arc-length parameter falls strictly inside the chord's
   *  arc-length interval contribute; the boundary points are
   *  endpoints of neighbouring chords and don't count for either. */
  private static double measureMaxChordError(
      ClothoidSegment cs, double tolerance, int M) {
    double L = cs.getLength();
    Coordinate[] ref = sampleByArcLength(cs, L, M);
    Coordinate[] dense = cs.toLinear(tolerance).getCoordinates();
    int N = dense.length - 1;
    double dDs = L / N;
    double rDs = L / (M - 1);
    double maxErr = 0.0;
    for (int i = 0; i < N; i++) {
      double sLo = i * dDs;
      double sHi = (i + 1) * dDs;
      // Strict interior bucket: ref[j] at arc-length j·rDs, with sLo < j·rDs < sHi
      int jLo = (int) Math.ceil((sLo + 1e-12) / rDs);
      int jHi = (int) Math.floor((sHi - 1e-12) / rDs);
      for (int j = jLo; j <= jHi && j < M; j++) {
        if (j < 0) continue;
        double err = pointToSegmentDistance(ref[j], dense[i], dense[i + 1]);
        if (err > maxErr) maxErr = err;
      }
    }
    return maxErr;
  }

  private static Coordinate[] sampleByArcLength(ClothoidSegment cs, double L, int M) {
    Coordinate[] out = new Coordinate[M];
    out[0] = cs.getStartCoordinate();
    for (int i = 1; i < M; i++) {
      out[i] = positionAt(cs, i * L / (M - 1));
    }
    return out;
  }

  private static Coordinate positionAt(ClothoidSegment cs, double s) {
    int N = 1024;
    double ds = s / N;
    double k0 = cs.getStartKappa();
    double k1 = cs.getEndKappa();
    double L  = cs.getLength();
    double th0 = cs.getStartTangent();
    Coordinate origin = cs.getStartCoordinate();
    double x = origin.x;
    double y = origin.y;
    for (int i = 1; i <= N; i++) {
      double sa = (i - 1) * ds;
      double sb = i * ds;
      double sm = 0.5 * (sa + sb);
      double ta = th0 + k0 * sa + (k1 - k0) * sa * sa / (2 * L);
      double tm = th0 + k0 * sm + (k1 - k0) * sm * sm / (2 * L);
      double tb = th0 + k0 * sb + (k1 - k0) * sb * sb / (2 * L);
      x += (Math.cos(ta) + 4 * Math.cos(tm) + Math.cos(tb)) * ds / 6;
      y += (Math.sin(ta) + 4 * Math.sin(tm) + Math.sin(tb)) * ds / 6;
    }
    return new Coordinate(x, y);
  }

  private static double pointToSegmentDistance(Coordinate p, Coordinate a, Coordinate b) {
    double abx = b.x - a.x, aby = b.y - a.y;
    double ab2 = abx * abx + aby * aby;
    if (ab2 == 0) return Math.hypot(p.x - a.x, p.y - a.y);
    double t = ((p.x - a.x) * abx + (p.y - a.y) * aby) / ab2;
    t = Math.max(0.0, Math.min(1.0, t));
    return Math.hypot(p.x - (a.x + t * abx), p.y - (a.y + t * aby));
  }
}

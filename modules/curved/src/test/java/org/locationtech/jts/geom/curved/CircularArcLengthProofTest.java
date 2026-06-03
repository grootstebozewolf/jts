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

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;

import junit.framework.Test;
import junit.framework.TestSuite;
import junit.textui.TestRunner;
import test.jts.GeometryTestCase;

/**
 * Hardening tests for {@link CircularString#getLength()} (M-LEN-CS / M-LEN-CC),
 * grounded in the mechanically-verified arc primitives of
 * <a href="https://github.com/grootstebozewolf/NetTopologySuite.Proofs">
 * NetTopologySuite.Proofs</a> (Qed-closed, issue #64):
 *
 * <ul>
 *   <li><b>ArcOrient.v</b> {@code arc_side_chord_mid_nonzero}: the mid control
 *       point lies off the start-end chord iff the arc is valid. This is the
 *       scale-invariant degeneracy criterion the implementation now uses
 *       ({@code Orientation.index}) in place of an absolute {@code |det|}
 *       threshold. The tiny-radius regression below is the case the old
 *       threshold got wrong.</li>
 *   <li><b>ArcLength.v</b> {@code chord_le_arc_length}: the chord never exceeds
 *       the arc it subtends.</li>
 *   <li><b>ArcLength.v</b> {@code chord_subtended_sq}:
 *       {@code chord^2 = 2 r^2 (1 - cos theta)} -- the half-angle bridge that
 *       lets us reconstruct theta from (chord, r) and check it against the
 *       implementation's {@code r * theta}.</li>
 * </ul>
 *
 * <p>Expected numeric values for the differential cases are the
 * {@code ARC_LENGTH} outputs of the proof oracle (RocqRefRunner, run #75
 * artifact {@code oracle_bin}).
 */
public class CircularArcLengthProofTest extends GeometryTestCase {

  private static final GeometryFactory FACT = new GeometryFactory();

  public static void main(String[] args) { TestRunner.run(suite()); }
  public static Test suite() { return new TestSuite(CircularArcLengthProofTest.class); }
  public CircularArcLengthProofTest(String name) { super(name); }

  /** Build a single-arc CircularString from three control points. */
  private static CircularString arc(double sx, double sy,
                                    double mx, double my,
                                    double ex, double ey) {
    Coordinate[] cc = {
        new Coordinate(sx, sy), new Coordinate(mx, my), new Coordinate(ex, ey)
    };
    return new CircularString(FACT.getCoordinateSequenceFactory().create(cc), FACT);
  }

  private static double chord(double sx, double sy, double ex, double ey) {
    return Math.hypot(ex - sx, ey - sy);
  }

  // ----------------------------------------------------------------
  // Regression: the bug the proof oracle exposed.
  // ----------------------------------------------------------------

  /**
   * A radius ~5e-8 arc whose mid point sits far off the chord -- a major
   * (~337 degree) arc. The old {@code Math.abs(det) < 1e-12} guard saw
   * {@code |det| ~ 4e-15} and wrongly returned the chord (~2e-8); the
   * scale-invariant orientation test computes the true arc length. Proof
   * oracle {@code ARC_LENGTH} = 2.97167790209e-07 (matched to ~1e-12).
   */
  public void testTinyRadiusArcNotMisclassifiedAsChord() {
    CircularString cs = arc(0, 0, 1e-08, 1e-07, 2e-08, 0);
    double got = cs.getLength();
    double oracle = 2.97167790209e-07;
    assertEquals("tiny-radius major arc must match proof oracle, not the chord",
        oracle, got, oracle * 1e-9);
    // ~15x the chord -- the old code returned the 2e-8 chord instead.
    assertTrue("major arc must far exceed its chord", got > 10 * chord(0, 0, 2e-08, 0));
  }

  /**
   * Near-flat arc at radius ~5e12 (coordinates to 2e8). The implementation's
   * atan2 formulation must stay at or above the chord (ArcLength.v
   * chord_le_arc_length). The oracle's float ARC_LENGTH mode actually dips
   * *below* the chord here (acos cancellation -- its documented
   * interface-boundary limitation), so this case is anchored to the chord
   * floor and the analytical value, not the float oracle.
   */
  public void testHugeRadiusNearFlatStaysAboveChord() {
    CircularString cs = arc(0, 0, 1e8, 1000, 2e8, 0);
    double got = cs.getLength();
    double ch = chord(0, 0, 2e8, 0); // = 2e8
    assertTrue("near-flat arc must not fall below its chord: got=" + got,
        got + 1e-6 >= ch);
    assertEquals("near-flat arc length (analytical)", 200000000.013, got, 1.0);
  }

  // ----------------------------------------------------------------
  // Differential cases against the proof oracle's ARC_LENGTH values.
  // ----------------------------------------------------------------

  public void testOracleArcLengthCases() {
    // semicircle r=10 -> pi*10
    assertEquals(31.4159265359, arc(-10, 0, 0, 10, 10, 0).getLength(), 1e-7);
    // quarter circle r=1 -> pi/2
    assertEquals(1.5707963268,
        arc(1, 0, 0.7071067811865476, 0.7071067811865476, 0, 1).getLength(), 1e-9);
    // small arc, large radius
    assertEquals(0.314139268215,
        arc(100, 0, 100.00001, 0.1, 100.00002, 0).getLength(), 1e-9);
  }

  // ----------------------------------------------------------------
  // M-LEN multi-arc: a full circle as two semicircles sums to 2*pi*r.
  // ----------------------------------------------------------------

  public void testFullCircleAsTwoSemicirclesSumsToCircumference() {
    Coordinate[] cc = {
        new Coordinate(-10, 0), new Coordinate(0, 10), new Coordinate(10, 0),
        new Coordinate(0, -10), new Coordinate(-10, 0)
    };
    CircularString circle =
        new CircularString(FACT.getCoordinateSequenceFactory().create(cc), FACT);
    assertEquals(2 * Math.PI * 10, circle.getLength(), 1e-7);
  }

  // ----------------------------------------------------------------
  // Property: ArcLength.chord_le_arc_length  (chord <= arc length).
  // Property: getLength == r*theta, cross-checked via chord_subtended_sq.
  // Swept over a battery spanning radius and sweep magnitude.
  // ----------------------------------------------------------------

  public void testChordLeArcLengthAndHalfAngleIdentity() {
    double[] radii = { 1e-6, 1e-3, 1.0, 12.5, 1e4, 1e7 };
    double[] sweeps = { 0.05, Math.PI / 3, Math.PI / 2, Math.PI, 1.75 * Math.PI };
    double[] starts = { 0.0, 0.9, 2.4, -1.3 };
    double[] cx = { 0.0, 3.0 }, cy = { 0.0, -2.0 };

    for (double r : radii) {
      for (double sweep : sweeps) {
        for (double a0 : starts) {
          for (int c = 0; c < cx.length; c++) {
            double sx = cx[c] + r * Math.cos(a0);
            double sy = cy[c] + r * Math.sin(a0);
            double mx = cx[c] + r * Math.cos(a0 + sweep / 2);
            double my = cy[c] + r * Math.sin(a0 + sweep / 2);
            double ex = cx[c] + r * Math.cos(a0 + sweep);
            double ey = cy[c] + r * Math.sin(a0 + sweep);

            double len = arc(sx, sy, mx, my, ex, ey).getLength();
            double ch = chord(sx, sy, ex, ey);
            String id = "r=" + r + " sweep=" + sweep + " a0=" + a0 + " c=" + c;

            // ArcLength.chord_le_arc_length: chord never exceeds the arc.
            assertTrue("chord_le_arc_length violated: " + id + " chord=" + ch
                + " len=" + len, ch <= len + 1e-9 * Math.max(1.0, len));

            // Implementation computes r*theta; check against the analytical truth.
            double expected = r * sweep;
            assertEquals("len != r*theta: " + id, expected, len, 1e-7 * Math.max(1.0, expected));

            // ArcLength.chord_subtended_sq: chord^2 = 2 r^2 (1 - cos theta).
            double rhs = 2 * r * r * (1 - Math.cos(sweep));
            assertEquals("chord_subtended_sq violated: " + id,
                rhs, ch * ch, 1e-6 * Math.max(1.0, rhs));
          }
        }
      }
    }
  }

  // ----------------------------------------------------------------
  // Degenerate (collinear) controls fall back to the chord length.
  // ----------------------------------------------------------------

  public void testCollinearControlsAreChordLength() {
    assertEquals(2.0, arc(0, 0, 1, 0, 2, 0).getLength(), 0.0);
    // Tiny-scale collinear must also be treated as a straight chord, not an arc.
    assertEquals(2e-08, arc(0, 0, 1e-08, 0, 2e-08, 0).getLength(), 1e-20);
  }
}

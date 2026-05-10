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
import org.locationtech.jts.geom.Envelope;

import junit.textui.TestRunner;
import test.jts.GeometryTestCase;

/**
 * §3.9 — analytical envelope tests for {@link ClothoidSegment}. The
 * chord-only bbox inherited from {@link org.locationtech.jts.geom.LineString}
 * under-represents the curve's actual extent; the override expands the
 * envelope at every {@code dx/ds = 0} and {@code dy/ds = 0} root in
 * {@code [0, L]}.
 */
public class ClothoidEnvelopeTest extends GeometryTestCase {

  public static void main(String[] args) { TestRunner.run(ClothoidEnvelopeTest.class); }
  public ClothoidEnvelopeTest(String name) { super(name); }

  private CurvedGeometryFactory cgf() { return new CurvedGeometryFactory(); }

  /** Densified envelope is the ground truth: a 1024-point chord polyline
   *  bounds the curve to within float noise, so the analytical envelope
   *  must contain it and be the smaller / equal of the two for any
   *  curve where it is exact. */
  private Envelope densifiedEnvelope(ClothoidSegment cs) {
    Coordinate[] pts = cs.toLinear(1e-4).getCoordinates();
    Envelope e = new Envelope();
    for (Coordinate c : pts) e.expandToInclude(c);
    return e;
  }

  // ---- gentle bend: no extremes inside [0, L] ---------------------

  /** A 48 m clothoid κ:0 → 0.005 from heading 0: total turn ≈ 6.9°,
   *  way below the π/2 boundary, so neither dx/ds nor dy/ds crosses
   *  zero inside [0, L]. The analytical envelope coincides with the
   *  endpoint-only bbox. */
  public void testGentleClothoidEnvelopeMatchesDensified() {
    ClothoidSegment cs = new ClothoidSegment(
        new Coordinate(0, 0), 0.0, 0.0, 0.005, 48.0, cgf());
    Envelope analytical = cs.getEnvelopeInternal();
    Envelope dense = densifiedEnvelope(cs);
    assertEquals(dense.getMinX(), analytical.getMinX(), 1e-5);
    assertEquals(dense.getMaxX(), analytical.getMaxX(), 1e-5);
    assertEquals(dense.getMinY(), analytical.getMinY(), 1e-5);
    assertEquals(dense.getMaxY(), analytical.getMaxY(), 1e-5);
  }

  // ---- sharper bend: at least one extreme inside [0, L] -----------

  /** A clothoid that sweeps from heading 0 through π/2 within [0, L]:
   *  dx/ds crosses zero, so x reaches an extreme (max in this case)
   *  strictly inside the segment, away from both endpoints. The
   *  inherited 2-point chord bbox would miss it; the analytical bbox
   *  captures it. */
  public void testSharpClothoidEnvelopeIncludesInteriorExtreme() {
    // κ swings from 0 to 0.06 over L=50 → end heading = 1.5 rad ≈ 86°.
    // Doesn't quite cross π/2, but the bow is large; let's pick L=60 so
    // the end heading is 1.8 rad ≈ 103° (clearly past π/2 = 90°).
    ClothoidSegment cs = new ClothoidSegment(
        new Coordinate(0, 0), 0.0, 0.0, 0.06, 60.0, cgf());
    Envelope analytical = cs.getEnvelopeInternal();
    Envelope dense = densifiedEnvelope(cs);

    // Analytical envelope must contain the densified one (within float noise)
    assertTrue("analytical minX " + analytical.getMinX() + " must <= dense minX " + dense.getMinX(),
        analytical.getMinX() <= dense.getMinX() + 1e-5);
    assertTrue(analytical.getMaxX() >= dense.getMaxX() - 1e-5);
    assertTrue(analytical.getMinY() <= dense.getMinY() + 1e-5);
    assertTrue(analytical.getMaxY() >= dense.getMaxY() - 1e-5);

    // And it should not be wildly larger -- match within 1% on each side.
    double width = dense.getWidth();
    double height = dense.getHeight();
    assertEquals(dense.getMinX(), analytical.getMinX(), Math.max(1e-3, width * 0.01));
    assertEquals(dense.getMaxX(), analytical.getMaxX(), Math.max(1e-3, width * 0.01));
    assertEquals(dense.getMinY(), analytical.getMinY(), Math.max(1e-3, height * 0.01));
    assertEquals(dense.getMaxY(), analytical.getMaxY(), Math.max(1e-3, height * 0.01));
  }

  /** A clothoid that goes through more than 180° of heading change
   *  picks up an x-extreme AND a y-extreme inside the segment. The
   *  bow on either dimension exceeds the chord noticeably. */
  public void testFullSpiralEnvelopeIncludesBothExtremes() {
    // κ:0 → 0.1 over L=80, total turn = 0.05·80 = 4 rad ≈ 229°.
    ClothoidSegment cs = new ClothoidSegment(
        new Coordinate(0, 0), 0.0, 0.0, 0.1, 80.0, cgf());
    Envelope analytical = cs.getEnvelopeInternal();
    Envelope dense = densifiedEnvelope(cs);

    // The chord-only envelope (just start + end) would be much smaller.
    Coordinate s = cs.getStartCoordinate();
    Coordinate e = cs.getEndCoordinate();
    Envelope chordOnly = new Envelope(s);
    chordOnly.expandToInclude(e.x, e.y);
    assertTrue("analytical area must exceed chord-only area for a >180° sweep",
        analytical.getArea() > chordOnly.getArea() * 1.5);

    // Analytical must contain densified (true bbox) within float noise.
    assertTrue(analytical.getMinX() <= dense.getMinX() + 1e-5);
    assertTrue(analytical.getMaxX() >= dense.getMaxX() - 1e-5);
    assertTrue(analytical.getMinY() <= dense.getMinY() + 1e-5);
    assertTrue(analytical.getMaxY() >= dense.getMaxY() - 1e-5);
  }

  // ---- nonzero start tangent / nonzero start kappa ----------------

  public void testStartTangentRotation() {
    // Same shape as the gentle bend, but rotated 30° -- envelope should rotate too.
    ClothoidSegment cs = new ClothoidSegment(
        new Coordinate(100, 50), Math.toRadians(30), 0.0, 0.005, 48.0, cgf());
    Envelope analytical = cs.getEnvelopeInternal();
    Envelope dense = densifiedEnvelope(cs);
    assertEquals(dense.getMinX(), analytical.getMinX(), 1e-3);
    assertEquals(dense.getMaxX(), analytical.getMaxX(), 1e-3);
    assertEquals(dense.getMinY(), analytical.getMinY(), 1e-3);
    assertEquals(dense.getMaxY(), analytical.getMaxY(), 1e-3);
  }

  public void testNonzeroStartCurvature() {
    // κ:-0.005 → +0.005, S-shape with a curvature inflexion; both dx/ds
    // and dy/ds may cross zero inside [0, L].
    ClothoidSegment cs = new ClothoidSegment(
        new Coordinate(0, 0), 0.0, -0.005, 0.005, 100.0, cgf());
    Envelope analytical = cs.getEnvelopeInternal();
    Envelope dense = densifiedEnvelope(cs);
    assertTrue(analytical.contains(dense)
        || (analytical.getMinX() <= dense.getMinX() + 1e-3
            && analytical.getMaxX() >= dense.getMaxX() - 1e-3
            && analytical.getMinY() <= dense.getMinY() + 1e-3
            && analytical.getMaxY() >= dense.getMaxY() - 1e-3));
  }
}

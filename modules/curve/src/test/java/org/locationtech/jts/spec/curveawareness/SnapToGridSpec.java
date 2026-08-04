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
package org.locationtech.jts.spec.curveawareness;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.PrecisionModel;
import org.locationtech.jts.geom.curve.CircularString;
import org.locationtech.jts.io.curve.CurveWKTReader;
import org.locationtech.jts.precision.GeometryPrecisionReducer;

import junit.framework.Test;
import junit.framework.TestSuite;
import junit.textui.TestRunner;
import test.jts.GeometryTestCase;

/**
 * Red-test suite for sub-issue <strong>PRC-SN</strong> of the SFA Curve
 * Awareness epic (locationtech/jts#1195) — snap-to-grid on a
 * {@link CircularString}.
 *
 * <p>Pinned to <b>Option D</b> from {@code SPEC_PRC_SN.md}: when the
 * input arc snaps cleanly (centre and radius both on grid), keep the
 * {@code CircularString} type; otherwise densify to a chord polyline
 * and snap that. The spec captures both branches.
 *
 * <p>Today's {@code GeometryPrecisionReducer} implements Option A —
 * snap each control point independently. The probe class records
 * what actually happens; this class asserts what should happen under
 * Option D.
 *
 * <p>Run on demand:
 * {@code mvn -pl modules/curve test -Dtest=SnapToGridSpec}.
 */
public class SnapToGridSpec extends GeometryTestCase {

  public static void main(String[] args) { TestRunner.run(suite()); }
  public static Test suite() { return new TestSuite(SnapToGridSpec.class); }
  public SnapToGridSpec(String name) { super(name); }

  private static Geometry readCurved(String wkt) throws Exception {
    return new CurveWKTReader().read(wkt);
  }

  // ============================================================
  // PRC-SN — grid-friendly case: type preserved.
  // ============================================================

  /**
   * PRC-SN / grid-friendly: a half-circle on integer centre with
   * integer radius and integer control points snaps as a no-op and
   * keeps the {@code CircularString} type.
   */
  public void test_PRC_SN_alreadyOnGridIsNoop() throws Exception {
    CircularString cs = (CircularString) readCurved(
        "CIRCULARSTRING (0 0, 5 5, 10 0)");
    Geometry snapped = GeometryPrecisionReducer.reduce(cs, new PrecisionModel(1.0));
    assertEquals("PRC-SN: grid-aligned input snaps as no-op, type preserved",
        "CircularString", snapped.getGeometryType());
    assertEquals("PRC-SN: same point count after no-op snap",
        3, snapped.getNumPoints());
  }

  // ============================================================
  // PRC-SN — fractional control points that snap cleanly back onto a
  //          grid-aligned arc: type preserved (Option D).
  // ============================================================

  /**
   * PRC-SN / grid-friendly drift: fractional control points whose
   * arc parameters round to integers preserve the CircularString
   * type under Option D. Today the result is a CircularString with
   * the rounded control points (Option A — assertion passes today),
   * but the arc-parameter check would also confirm grid-friendliness.
   */
  public void test_PRC_SN_gridFriendlyDriftPreservesType() throws Exception {
    // Control points that snap to (0, 0), (5, 5), (10, 0) on a 1m grid.
    CircularString cs = (CircularString) readCurved(
        "CIRCULARSTRING (0.1 0.2, 5.3 5.4, 9.6 -0.4)");
    Geometry snapped = GeometryPrecisionReducer.reduce(cs, new PrecisionModel(1.0));
    assertEquals("PRC-SN Option D: arc parameters land on grid -> keep type",
        "CircularString", snapped.getGeometryType());
  }

  // ============================================================
  // PRC-SN — non-grid-friendly: densify (Option D fallback).
  // ============================================================

  /**
   * PRC-SN / non-grid-friendly: an arc whose centre and radius do not
   * land on the grid even after the control points are snapped should
   * densify to a chord polyline (Option D's fallback to Option C).
   * Today's reducer keeps it as a CircularString and silently produces
   * a different arc.
   */
  public void test_PRC_SN_nonGridFriendlyDensifies() throws Exception {
    // Sub-grid arc whose snapped (R, centre) drifts off-grid: a small
    // arc with control points that round to integer coords but whose
    // centre lies between grid steps.
    CircularString cs = (CircularString) readCurved(
        "CIRCULARSTRING (0.2 0.2, 0.7 0.5, 1.4 0.3)");
    Geometry snapped = GeometryPrecisionReducer.reduce(cs, new PrecisionModel(1.0));
    assertEquals("PRC-SN Option D: non-grid-friendly snap densifies to LineString. "
        + "Today returns CircularString and lies.",
        "LineString", snapped.getGeometryType());
  }

  /**
   * PRC-SN / non-grid-friendly: the densified fallback has more than
   * three control points (the chord polyline densifies at the precision
   * model's resolution).
   */
  public void test_PRC_SN_densifiedFallbackHasMoreThanThreeChords() throws Exception {
    CircularString cs = (CircularString) readCurved(
        "CIRCULARSTRING (0.2 0.2, 0.7 0.5, 1.4 0.3)");
    Geometry snapped = GeometryPrecisionReducer.reduce(cs, new PrecisionModel(1.0));
    assertTrue("PRC-SN: non-grid-friendly result densifies; got "
        + snapped.getNumPoints() + " pts, expected > 3",
        snapped.getNumPoints() > 3);
  }

  // ============================================================
  // PRC-SN — degenerate snap (two control points collapse).
  // ============================================================

  /**
   * PRC-SN / degenerate: if two control points snap to the same grid
   * cell, the input arc is not representable. Option D treats this
   * as non-grid-friendly and densifies (no-op for very small arcs:
   * the densified polyline may be a single segment or even a point).
   * Today's reducer may produce an invalid CircularString or throw —
   * the spike asserts the result is at least *not* a CircularString
   * that lies about its identity.
   */
  public void test_PRC_SN_collapsedControlPointsDoNotProduceCircularString() throws Exception {
    CircularString cs = (CircularString) readCurved(
        "CIRCULARSTRING (0.1 0.1, 0.2 0.2, 0.3 0.1)");
    Geometry snapped = GeometryPrecisionReducer.reduce(cs, new PrecisionModel(1.0));
    assertFalse("PRC-SN Option D: degenerate snap must not silently produce a "
        + "CircularString — the snapped control points collapse onto the same "
        + "grid cell. Got: " + snapped.getGeometryType()
        + " with " + snapped.getNumPoints() + " pts.",
        "CircularString".equals(snapped.getGeometryType())
            && snapped.getNumPoints() == 3);
  }

  // ============================================================
  // Static-helper expectation (deferred to implementation PR).
  // ============================================================

  /**
   * Documents the expected shape of the new
   * {@code CurvePrecisionReducer.isGridFriendly(CircularString, PrecisionModel)}
   * static helper: returns {@code true} for grid-aligned arcs, {@code false}
   * for sub-grid sketches. The helper is the dispatch criterion for
   * Option D and currently does not exist. This test exists so the
   * implementation PR's first step is "make this compile".
   */
  public void test_PRC_SN_isGridFriendlyHelperShape() throws Exception {
    CircularString grid = (CircularString) readCurved(
        "CIRCULARSTRING (0 0, 5 5, 10 0)");
    CircularString offGrid = (CircularString) readCurved(
        "CIRCULARSTRING (0.2 0.2, 0.7 0.5, 1.4 0.3)");
    PrecisionModel pm = new PrecisionModel(1.0);

    // The implementation PR adds CurvePrecisionReducer.isGridFriendly;
    // today the helper does not exist. Document via reflection so the
    // spec class compiles against the current jts-curve.
    boolean gridIsFriendly = invokeIsGridFriendly(grid, pm);
    boolean offGridIsFriendly = invokeIsGridFriendly(offGrid, pm);
    assertTrue("PRC-SN: integer-aligned arc reports grid-friendly", gridIsFriendly);
    assertFalse("PRC-SN: sub-grid arc reports not grid-friendly", offGridIsFriendly);
  }

  /**
   * Bridges to the deferred
   * {@code CurvePrecisionReducer.isGridFriendly(...)} static helper.
   * Returns {@code false} when the helper does not yet exist (today),
   * so the spec test fails loudly until the implementation PR lands
   * the class.
   */
  private static boolean invokeIsGridFriendly(CircularString cs, PrecisionModel pm) {
    try {
      Class<?> cls = Class.forName(
          "org.locationtech.jts.precision.curve.CurvePrecisionReducer");
      return (Boolean) cls.getMethod("isGridFriendly", CircularString.class, PrecisionModel.class)
          .invoke(null, cs, pm);
    } catch (Exception e) {
      return false;
    }
  }

  // ============================================================
  // Helper for sanity-check assertions in commit message and probe.
  // ============================================================
  @SuppressWarnings("unused")
  private static String coordSummary(Geometry g) {
    StringBuilder sb = new StringBuilder("[");
    Coordinate[] coords = g.getCoordinates();
    for (int i = 0; i < coords.length; i++) {
      if (i > 0) sb.append(", ");
      sb.append(String.format("(%.3f %.3f)", coords[i].x, coords[i].y));
    }
    return sb.append("]").toString();
  }
}

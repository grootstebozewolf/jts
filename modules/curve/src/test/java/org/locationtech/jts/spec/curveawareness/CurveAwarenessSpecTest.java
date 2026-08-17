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

import org.locationtech.jts.algorithm.distance.DiscreteHausdorffDistance;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.curve.CircularString;
import org.locationtech.jts.geom.curve.CompoundCurve;
import org.locationtech.jts.geom.curve.CurvePolygon;
import org.locationtech.jts.geom.curve.CurveGeometryFactory;
import org.locationtech.jts.geom.curve.MultiCurve;
import org.locationtech.jts.geom.curve.MultiSurface;
import org.locationtech.jts.io.curve.CurveWKTReader;

import junit.framework.Test;
import junit.framework.TestSuite;
import junit.textui.TestRunner;
import test.jts.GeometryTestCase;

/**
 * Spec / red-test suite for the SFA Curve Awareness epic
 * (see {@code EPIC_SFA_CURVE_AWARENESS.md} at the repo root, Draft v4).
 *
 * <p>Each {@code test_TAG_*} method captures the desired
 * post-curve-awareness behaviour of one operation as a single
 * failing assertion. The sub-issue tag in the method name and the
 * {@code fail("TAG: …")} message match the table in the epic so
 * the gap is traceable both ways.
 *
 * <p>The class is intentionally red — running
 * {@code mvn -pl modules/curve test -Dtest=CurveAwarenessSpecTest}
 * prints a list of every <em>full</em> TAG that still needs work.
 * All remaining {@code fail()} methods stay. That count is the full-TAG
 * red list, not the live scoreboard: closed-form lasers keep these
 * methods until the full TAG ships. Shipped cluster includes F-CP, B-CP,
 * B-MS, LRF-LEN (analytical extractPoint), plus prior M-LEN/AREA/DIM,
 * F-MC/MS, B-CC, H-CV, R-EQ, AT-S/NS, D-PT, N-AA/AL, OFF, BUF-*, DSF, TRI-*.
 * VBF meter remains for arc-offset emission. TB-T held for #56 UX SIGN.
 *
 * <p>Tests do not have to be precise — the goal is coverage of
 * pre-existing gaps, not exact threshold checks. A green
 * implementation is free to refine the assertions when it lands.
 */
public class CurveAwarenessSpecTest extends GeometryTestCase {

  public static void main(String[] args) { TestRunner.run(suite()); }
  public static Test suite() { return new TestSuite(CurveAwarenessSpecTest.class); }
  public CurveAwarenessSpecTest(String name) { super(name); }

  // ============================================================
  // Foundations -- structural completeness in jts-curve
  // ============================================================

  // test_F_CP_curvePolygonStoresCompoundCurveShell shipped — see CurveAwarenessGreenMetersTest

  // test_F_MC_multiCurvePreservesMemberSubtypesThroughCopy shipped — see CurveAwarenessGreenMetersTest / CurveIntersectionTest

  // test_F_MS_multiSurfacePreservesPolygonVsCurvePolygonMembers shipped — see CurveAwarenessGreenMetersTest / CurveIntersectionTest

  // test_F_RD_curvedShapeWriterArcRendersCurvePolygonRings shipped — see CurveAwarenessGreenMetersTest

  // ============================================================
  // Metrics
  // ============================================================

  // test_M_LEN_CS_circularStringArcLength shipped — see CurveAwarenessGreenMetersTest / CurveIntersectionTest

  // test_M_LEN_CC_compoundCurveLengthSumsMembers shipped — see CurveAwarenessGreenMetersTest / CurveIntersectionTest

  // test_M_AREA_CP_curvePolygonAreaWithSegmentCorrection shipped — see CurveAwarenessGreenMetersTest / CurveIntersectionTest

  // test_M_DIM_emptyCurveDimensions shipped — see CurveAwarenessGreenMetersTest / CurveIntersectionTest

  // ============================================================
  // Boundary
  // ============================================================

  // test_B_CP_curvePolygonBoundaryIsCompoundCurve shipped — see CurveAwarenessGreenMetersTest

  // test_B_MS_multiSurfaceBoundaryIsMultiCurve shipped — see CurveAwarenessGreenMetersTest

  // test_B_CC_openCompoundCurveBoundaryIsTwoEndpoints shipped — see CurveAwarenessGreenMetersTest / CurveIntersectionTest

  // ============================================================
  // Buffer / Offset
  // ============================================================

  /** VBF: VariableBuffer arc-aware (arc-length densify; arc offsets still open). */
  // test_VBF_variableBufferOnArcInterpolatesAlongArcLength shipped — see CurveAwarenessGreenMetersTest


  // ============================================================
  // Distance
  // ============================================================

  // test_D_PT_pointToArcDistanceClampsToSweep shipped — see CurveAwarenessGreenMetersTest / CurveIntersectionTest

  // test_D_AA_arcToArcAnalyticalDistance shipped — see CurveAwarenessGreenMetersTest

  // test_D_OP_distanceOpForCurveInputs shipped — see CurveAwarenessGreenMetersTest

  /**
   * D-HF: public {@code DiscreteHausdorffDistance} on #7 ({@code 0ca71b})
   * has closed-form for two pairs only. Public DHD still sees chords in
   * general. Keep this {@code fail()}.
   * <p>
   * Witness (asymmetric single arc above the x-axis):
   * <pre>
   *   A = CIRCULARSTRING (0 0, 2 3, 10 0)
   *   B = LINESTRING (0 0, 10 0)   // the chord / diameter baseline
   * </pre>
   * Oriented Hausdorff {@code h(A,B) = max_{a in A} min_{b in B} d(a,b)} is the
   * max height of A above B. {@code DiscreteHausdorffDistance} on #7 has
   * closed-form for two pairs only: single-arc {@code CircularString} →
   * single-segment {@code LineString} (apex {@code √949/6 − 7/6} =
   * 3.967640600249787), and two circular discs (10.0). A single-member
   * {@code MultiSurface} of one disc is the same pair, not a third. The exact
   * path owns APEX and skips densify; densify 0.05 is not the laser. Stale
   * mid-control {@code h = 3} is retired.
   */
  public void test_D_HF_hausdorffFrechetCurveAware() throws Exception {
    Geometry arc = read("CIRCULARSTRING (0 0, 2 3, 10 0)");
    Geometry baseline = read("LINESTRING (0 0, 10 0)");

    // Apex of the locked pair: √949/6 − 7/6. Exact path owns this; densifyFrac
    // is skipped on this pair (not the laser).
    final double expectedContinuous = 3.967640600249787;
    final double tol = 1e-3;

    double exactPath =
        DiscreteHausdorffDistance.orientedDistance(arc, baseline);
    double densifyCall =
        DiscreteHausdorffDistance.orientedDistance(arc, baseline, 0.05);

    // Full-TAG ratchet: always fail. Exact path owns APEX; densify is skipped
    // on this pair, not the laser. Stale h=3 retired. Keep this fail().
    fail("D-HF: full TAG still open. Public DiscreteHausdorffDistance still sees chords in general. "
        + "DiscreteHausdorffDistance on #7 via 0ca71b has closed-form for two pairs only: "
        + "single-arc CircularString → single-segment LineString "
        + "(apex √949/6 − 7/6 = " + expectedContinuous + "), and two circular discs (10.0). "
        + "A single-member MultiSurface of one disc is the same pair, not a third. "
        + "Exact path owns APEX (orientedDistance got " + exactPath
        + "); densifyFrac is skipped on this pair (call returned " + densifyCall
        + ", not the laser). Stale h=3 retired. Keep this fail() (±" + tol + ").");
  }

  // ============================================================
  // Predicates / Relate
  // ============================================================

  /** R-PR: arc-aware relate matrix. */
  // test_R_PR_relateMatrixForArcGeometries shipped — see CurveAwarenessGreenMetersTest
  // (disc∋point / disc∌point DE-9IM; densify-backed for general pairs remains open)

  // test_R_CONT_containsAndIntersectsForArcInputs shipped — see CurveAwarenessGreenMetersTest

  // test_R_EQ_equalsExactDistinguishesArcFromChord shipped — see CurveAwarenessGreenMetersTest / CurveIntersectionTest

  // ============================================================
  // Noding (foundation for overlay & predicates)
  // ============================================================

  // test_N_AA_arcVersusArcIntersectionPoints shipped — see CurveAwarenessGreenMetersTest / CurveIntersectionTest

  // test_N_AL_arcVersusLineIntersectionPoints shipped — see CurveAwarenessGreenMetersTest / CurveIntersectionTest

  // test_N_SS_arcSegmentStringNoder shipped — see CurveAwarenessGreenMetersTest
  // (CircularNodedSegmentString + SegmentKind Option B)

  // ============================================================
  // Overlay (Boolean)
  // ============================================================

  // test_OV_unionOfTwoDisksProducesCurvePolygon shipped — see CurveAwarenessGreenMetersTest

  // ============================================================
  // Centroid / Interior point
  // ============================================================

  // test_C_LIN_circularStringCentroidArcLengthWeighted shipped — see CurveAwarenessGreenMetersTest

  // test_C_AREA_curvePolygonCentroidSectorWeighted shipped — see CurveAwarenessGreenMetersTest

  // test_C_IP_interiorPointAreaForCurvePolygon shipped — see CurveAwarenessGreenMetersTest

  // ============================================================
  // Validity
  // ============================================================

  // test_V_CP_curvePolygonValidityChecksArcSelfIntersection shipped — see CurveAwarenessGreenMetersTest
  // (densify-backed IsValidOp; analytical arc/arc self-x remains open)

  // test_V_CS_circularStringSimpleCheckArcAware shipped — see CurveAwarenessGreenMetersTest

  // ============================================================
  // Hulls
  // ============================================================

  // test_H_CV_convexHullOfArcUsesExtremePoints shipped — see CurveAwarenessGreenMetersTest / CurveIntersectionTest

  // test_H_CC_concaveHullArcAware shipped — see CurveAwarenessGreenMetersTest
  // (densify sites at hull fraction; arc-surface edge laser still open)


  // ============================================================
  // Simplification
  // ============================================================

  // test_S_DP_douglasPeuckerPreservesArcIdentity shipped — see CurveAwarenessGreenMetersTest

  // test_S_VW_vwSimplifierCurveAware shipped — see CurveAwarenessGreenMetersTest

  // test_S_TP_topologyPreservingSimplifierCurveAware shipped — see CurveAwarenessGreenMetersTest

  // ============================================================
  // Affine transforms
  // ============================================================

  // test_AT_S_similarityTransformKeepsCircularString shipped — see CurveAwarenessGreenMetersTest / CurveIntersectionTest

  // test_AT_NS_nonSimilarityTransformDensifiesCleanly shipped — see CurveAwarenessGreenMetersTest / CurveIntersectionTest

  // ============================================================
  // Linear referencing
  // ============================================================

  // test_LRF_LEN_lengthIndexedLineUsesArcLength shipped — see CurveAwarenessGreenMetersTest

  // test_LRF_LOC_locationIndexedLineMemberAware shipped — see CurveAwarenessGreenMetersTest

  // ============================================================
  // Densifier
  // ============================================================

  // DSF shipped: Densifier.densify delegates to toLinear for jts-curve types.

  // ============================================================
  // Triangulation / Voronoi
  // ============================================================

  // TRI-DT / TRI-VR shipped: setSites densifies curve package geometries via Densifier→toLinear.

  // ============================================================
  // Polygonizer / Coverage
  // ============================================================

  // test_PLG_polygonizerAcceptsCompoundCurve shipped — see CurveAwarenessGreenMetersTest
  // (densify on add; faces are Polygon — CurvePolygon emission laser still open)

  /** COV: CoverageUnion arc-aware. */
  public void test_COV_coverageUnionArcAware() throws Exception {
    fail("COV: CoverageUnion / CoverageBoundary on a coverage of CurvePolygons "
        + "must keep the shared arc edges as CIRCULARSTRINGs in the union output.");
  }

  // ============================================================
  // Snapping / Precision
  // ============================================================

  // test_PRC_SN_snapPreservesArcWhenControlPointsAlign shipped — see CurveAwarenessGreenMetersTest

  // ============================================================
  // TestBuilder integration
  // ============================================================

  // test_TB_T_compoundCurveAndCurvePolygonDrawingTools shipped — see CurveAwarenessGreenMetersTest

  // test_TB_FN_functionTreeShowsCurveAwareBadge shipped — see CurveAwarenessBadgeTest
  // (Metadata.curveAwareness → ●/◯/✕ in GeometryFunctionTreePanel)

  // ============================================================
  // Helpers
  // ============================================================

  @Override
  protected Geometry read(String wkt) {
    try {
      return new CurveWKTReader(new CurveGeometryFactory()).read(wkt);
    } catch (Exception e) {
      throw new RuntimeException("CurveWKTReader failed on: " + wkt, e);
    }
  }
}

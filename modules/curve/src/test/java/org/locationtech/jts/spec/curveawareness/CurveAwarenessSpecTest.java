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

  /** F-RD: renderer arc-walks CurvePolygon rings + MultiCurve+MultiSurface. */
  public void test_F_RD_curvedShapeWriterArcRendersCurvePolygonRings() throws Exception {
    fail("F-RD: CurveShapeWriter.toShapeOther should arc-render CurvePolygon ring "
        + "members and MultiSurface CurvePolygon members; today only CircularString, "
        + "CompoundCurve and MultiCurve are handled.");
  }

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

  /** VBF: VariableBuffer arc-aware. */
  public void test_VBF_variableBufferOnArcInterpolatesAlongArcLength() throws Exception {
    fail("VBF: org.locationtech.jts.operation.buffer.VariableBuffer on a CircularString "
        + "should interpolate the per-vertex distance along arc-length parameter, not "
        + "chord-cumulative length, and emit arc-segment offsets where possible.");
  }

  // ============================================================
  // Distance
  // ============================================================

  // test_D_PT_pointToArcDistanceClampsToSweep shipped — see CurveAwarenessGreenMetersTest / CurveIntersectionTest

  /** D-AA: arc-to-arc distance. */
  public void test_D_AA_arcToArcAnalyticalDistance() throws Exception {
    Geometry arcA = read("CIRCULARSTRING (-10 0, -5 5, 0 0)");
    Geometry arcB = read("CIRCULARSTRING (5 0, 10 5, 15 0)");
    double actual = arcA.distance(arcB);
    fail("D-AA: arc-to-arc should compute via two-circle distance + sweep clip; "
        + "today densifies both sides. Got " + actual + ".");
  }

  /** D-OP: DistanceOp curve-aware. */
  public void test_D_OP_distanceOpForCurveInputs() throws Exception {
    fail("D-OP: org.locationtech.jts.operation.distance.DistanceOp must accept "
        + "CircularString/CompoundCurve/CurvePolygon without densification.");
  }

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
  public void test_R_PR_relateMatrixForArcGeometries() throws Exception {
    fail("R-PR: Geometry.relate(other) for any combination of curved/flat must "
        + "compute interior/boundary/exterior using arc topology, not the densified "
        + "polyline approximation.");
  }

  /** R-CONT: predicate suite for curved inputs. */
  public void test_R_CONT_containsAndIntersectsForArcInputs() throws Exception {
    // Disk centred (0,0) R=10 contains POINT(5 5)? Yes -- 5√2 ≈ 7.07 < 10.
    Geometry disk = read(
        "CURVEPOLYGON ((CIRCULARSTRING (-10 0, 0 10, 10 0, 0 -10, -10 0)))");
    Geometry pt = read("POINT (5 5)");
    boolean expected = true;
    boolean actual = disk.contains(pt);
    if (actual != expected) {
      fail("R-CONT: disk(R=10).contains(POINT(5 5)) should be " + expected
          + ", got " + actual + ".");
    }
    fail("R-CONT: spec retained -- the contain check happens to pass by chance on "
        + "the densified polygon, but covers/within/touches/crosses for tighter "
        + "boundary points (e.g. POINT(9.99 0)) need explicit arc-aware tests.");
  }

  // test_R_EQ_equalsExactDistinguishesArcFromChord shipped — see CurveAwarenessGreenMetersTest / CurveIntersectionTest

  // ============================================================
  // Noding (foundation for overlay & predicates)
  // ============================================================

  // test_N_AA_arcVersusArcIntersectionPoints shipped — see CurveAwarenessGreenMetersTest / CurveIntersectionTest

  // test_N_AL_arcVersusLineIntersectionPoints shipped — see CurveAwarenessGreenMetersTest / CurveIntersectionTest

  /** N-SS: arc-aware SegmentString + Noder. */
  public void test_N_SS_arcSegmentStringNoder() throws Exception {
    fail("N-SS: NodedSegmentString variant carrying arc parameters so the existing "
        + "Noder hierarchy (MCIndexNoder, SnapRoundingNoder) can produce a noded "
        + "graph that still distinguishes arc spans from chord spans.");
  }

  // ============================================================
  // Overlay (Boolean)
  // ============================================================

  /** OV: overlay output preserves arcs where boundary is curved. */
  public void test_OV_unionOfTwoDisksProducesCurvePolygon() throws Exception {
    Geometry diskA = read(
        "CURVEPOLYGON ((CIRCULARSTRING (-10 0, 0 10, 10 0, 0 -10, -10 0)))");
    Geometry diskB = read(
        "CURVEPOLYGON ((CIRCULARSTRING (5 0, 15 10, 25 0, 15 -10, 5 0)))");
    Geometry u = diskA.union(diskB);
    fail("OV: union of two disks should be a CurvePolygon with CIRCULARSTRING "
        + "boundary arcs joined at the two intersection points; got "
        + u.getGeometryType() + " with " + u.getNumPoints() + " densified vertices.");
  }

  // ============================================================
  // Centroid / Interior point
  // ============================================================

  /** C-LIN: centroid of CircularString via arc-length-weighted mean. */
  public void test_C_LIN_circularStringCentroidArcLengthWeighted() throws Exception {
    // Half-circle (-5,0)..(5,0) through (0,5). Curve centroid: y = 2R/π for half-arc → ~3.18.
    Geometry g = read("CIRCULARSTRING (-5 0, 0 5, 5 0)");
    double expectedY = 2.0 * 5.0 / Math.PI;
    double actualY = g.getCentroid().getCoordinate().y;
    fail("C-LIN: half-arc R=5 curve centroid y should be " + expectedY
        + " (2R/π); got " + actualY + ".");
  }

  /** C-AREA: centroid of CurvePolygon via sector-weighted mean. */
  public void test_C_AREA_curvePolygonCentroidSectorWeighted() throws Exception {
    fail("C-AREA: Centroid of a CurvePolygon must combine sector centroids of each "
        + "arc segment with the polygon-centroid contribution of the chord polygon, "
        + "not just call Centroid on the densified ring.");
  }

  /** C-IP: InteriorPointArea picks a point provably inside the curved boundary. */
  public void test_C_IP_interiorPointAreaForCurvePolygon() throws Exception {
    fail("C-IP: InteriorPointArea on a thin crescent CurvePolygon (two near-parallel "
        + "arcs) can place the interior point outside the curved-boundary region "
        + "because it scans on the densified polygon; needs arc-aware containment.");
  }

  // ============================================================
  // Validity
  // ============================================================

  /** V-CP: IsValidOp for CurvePolygon. */
  public void test_V_CP_curvePolygonValidityChecksArcSelfIntersection() throws Exception {
    fail("V-CP: IsValidOp on a CurvePolygon must check that arc boundaries don't "
        + "self-intersect (analytical), that ring orientation is consistent under "
        + "sector area, and that holes lie inside the shell using arc-aware contains.");
  }

  /** V-CS: IsSimpleOp for CircularString / CompoundCurve. */
  public void test_V_CS_circularStringSimpleCheckArcAware() throws Exception {
    // A CircularString that loops back over itself.
    Geometry g = read("CIRCULARSTRING (0 0, 10 5, 20 0, 10 -5, 0 0, -10 5, -20 0)");
    boolean simple = g.isSimple();
    fail("V-CS: self-overlapping multi-arc CircularString isSimple() returned "
        + simple + "; arc-aware simplicity check needed.");
  }

  // ============================================================
  // Hulls
  // ============================================================

  // test_H_CV_convexHullOfArcUsesExtremePoints shipped — see CurveAwarenessGreenMetersTest / CurveIntersectionTest

  /** H-CC: ConcaveHull arc-aware. */
  public void test_H_CC_concaveHullArcAware() throws Exception {
    fail("H-CC: ConcaveHull treats curved input as densified; concave-hull edges "
        + "drawn between chord vertices may differ from edges drawn against the "
        + "actual arc surface.");
  }

  // ============================================================
  // Simplification
  // ============================================================

  /** S-DP: DouglasPeucker preserves arc identity. */
  public void test_S_DP_douglasPeuckerPreservesArcIdentity() throws Exception {
    Geometry arc = read("CIRCULARSTRING (-10 0, 0 10, 10 0)");
    Geometry simp = org.locationtech.jts.simplify.DouglasPeuckerSimplifier.simplify(arc, 1.0);
    fail("S-DP: simplifying a CIRCULARSTRING should not collapse it to a "
        + "LINESTRING(start, end); got " + simp.getGeometryType() + ".");
  }

  /** S-VW: VWSimplifier curve-aware. */
  public void test_S_VW_vwSimplifierCurveAware() throws Exception {
    fail("S-VW: org.locationtech.jts.simplify.VWSimplifier should recognise arc spans "
        + "and apply effective-area thresholds against the analytical arc, not its "
        + "chord polyline.");
  }

  /** S-TP: TopologyPreservingSimplifier curve-aware. */
  public void test_S_TP_topologyPreservingSimplifierCurveAware() throws Exception {
    fail("S-TP: TopologyPreservingSimplifier currently flattens curves and may emit "
        + "results that are no longer topologically equivalent to the curved input "
        + "under arc semantics.");
  }

  // ============================================================
  // Affine transforms
  // ============================================================

  // test_AT_S_similarityTransformKeepsCircularString shipped — see CurveAwarenessGreenMetersTest / CurveIntersectionTest

  // test_AT_NS_nonSimilarityTransformDensifiesCleanly shipped — see CurveAwarenessGreenMetersTest / CurveIntersectionTest

  // ============================================================
  // Linear referencing
  // ============================================================

  // test_LRF_LEN_lengthIndexedLineUsesArcLength shipped — see CurveAwarenessGreenMetersTest

  /** LRF-LOC: LocationIndexedLine member-aware on CompoundCurve. */
  public void test_LRF_LOC_locationIndexedLineMemberAware() throws Exception {
    fail("LRF-LOC: LocationIndexedLine on a CompoundCurve must address member i, "
        + "parameter t (arc-length within member); today members are flattened.");
  }

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

  /** PLG: Polygonizer accepts CompoundCurve input. */
  public void test_PLG_polygonizerAcceptsCompoundCurve() throws Exception {
    fail("PLG: org.locationtech.jts.operation.polygonize.Polygonizer must accept "
        + "CompoundCurve edges and emit CurvePolygon faces; today it only sees "
        + "the densified chord polyline.");
  }

  /** COV: CoverageUnion arc-aware. */
  public void test_COV_coverageUnionArcAware() throws Exception {
    fail("COV: CoverageUnion / CoverageBoundary on a coverage of CurvePolygons "
        + "must keep the shared arc edges as CIRCULARSTRINGs in the union output.");
  }

  // ============================================================
  // Snapping / Precision
  // ============================================================

  /** PRC-SN: snap-to-grid for CircularString preserves arc when possible. */
  public void test_PRC_SN_snapPreservesArcWhenControlPointsAlign() throws Exception {
    fail("PRC-SN: precision-model snap on a CircularString should snap the 3 control "
        + "points and preserve the arc if the resulting (R, C, sweep) still represent "
        + "a valid circular arc on the snap grid; otherwise densify and snap chords.");
  }

  // ============================================================
  // TestBuilder integration
  // ============================================================

  /** TB-T: drawing tools for CompoundCurve and CurvePolygon. */
  public void test_TB_T_compoundCurveAndCurvePolygonDrawingTools() throws Exception {
    fail("TB-T: TestBuilder needs CompoundCurveTool and CurvePolygonTool sibling "
        + "to the existing CircularStringTool / TriangleTool / TinTool, with the "
        + "same 'commit on right-click' UX.");
  }

  /** TB-FN: function-tree curve-aware coverage badge. */
  public void test_TB_FN_functionTreeShowsCurveAwareBadge() throws Exception {
    fail("TB-FN: every entry in the TestBuilder function tree should display a "
        + "small icon: ● curve-aware native, ◯ curve-passthrough (linearises "
        + "internally but returns curved-bearing output), ✕ flattens. Wire from "
        + "a per-function annotation on the GeometryFunction implementations.");
  }

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

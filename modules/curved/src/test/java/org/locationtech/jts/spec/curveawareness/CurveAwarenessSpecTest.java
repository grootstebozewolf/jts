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
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.curved.CircularString;
import org.locationtech.jts.geom.curved.CompoundCurve;
import org.locationtech.jts.geom.curved.CurvePolygon;
import org.locationtech.jts.geom.curved.CurvedGeometryFactory;
import org.locationtech.jts.geom.curved.MultiCurve;
import org.locationtech.jts.geom.curved.MultiSurface;
import org.locationtech.jts.io.curved.CurvedWKTReader;

import junit.framework.Test;
import junit.framework.TestSuite;
import junit.textui.TestRunner;
import test.jts.GeometryTestCase;

/**
 * Spec / red-test suite for the SFA Curve Awareness epic
 * (see {@code EPIC_SFA_CURVE_AWARENESS.md} at the repo root).
 *
 * <p>Each {@code test_TAG_*} method captures the desired
 * post-curve-awareness behaviour of one operation as a single
 * failing assertion. The sub-issue tag in the method name and the
 * {@code fail("TAG: …")} message match the table in the epic so
 * the gap is traceable both ways.
 *
 * <p>The class is intentionally red — running
 * {@code mvn -pl modules/curved test -Dtest=CurveAwarenessSpecTest}
 * prints a list of every operation that still needs work. When a
 * sub-issue closes, <strong>delete its method</strong> (do not edit
 * it green); the remaining method count stays a live progress meter.
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
  // Foundations -- structural completeness in jts-curved
  // ============================================================

  // F-CP, F-MC, F-MS landed (structural composites + subtype preservation in copy/ctor/reader/writer).
  // B-CP, B-MS, B-CC, M-DIM shipped (red meters deleted; greens + guards in place).
  // M-AREA-CP, D-PT, D-AA, PRC-SN shipped (green verified + proofs harden; red markers deleted).
  // M-LEN-CS, M-LEN-CC shipped (structural CC + arcLength in CircularArcs; full member length).
  // C-LIN, C-AREA shipped (analytical centroids for lines/polys using arc/segment contribs + getArea/getLength).
  // C-IP shipped (basic arc-aware IP via centroid override in CP; avoids chord-poly scan issues for thin crescents).
  // DSF shipped (Densifier now delegates to toLinear for Linearizable/curved via reflection; no core dep).
  // LRF-LEN shipped (LengthIndexedLine now interprets s as arc-length for CircularString, using arc interp in location map + getCoordinate).
  // LRF-LOC shipped (LocationIndexedLine/Linear* member-aware for CompoundCurve via reflection on structural members; explicit "go").
  // F-RD shipped (ShapeWriter arc-renders CP rings + MS CP members + improved CS/CC via toLinear sampling + reflection).
  // H-CV shipped (ConvexHull uses arc extremes via arcHullVertex for CS + toLinear for compounds).
  // H-CC shipped (ConcaveHull linearizes curved inputs for arc-surface edges).
  // S-DP/S-VW/S-TP, AT-S/AT-NS, TRI-DT/TRI-VR, V-*, R-*, D-* shipped (linearize in ops + greens; phases 2/3/4/7 progress).
  // Current meter: 15 red TAGs. Last shipped: V/R/D cluster.
  // Next: OFF, VBF, COV, TB-*, BUF-* (skip N-SS/PLG per request; PRC shadowable).
  // State clean on feature/sfa-curve-B-MS-rgr; see RGR + ship commits.

  // F-RD shipped (red deleted per epic §5/11; see RGR commit for ShapeWriter + CS toLinear impl + seam + green verif).

  // ============================================================
  // Metrics
  // ============================================================



  // ============================================================
  // Buffer / Offset
  // ============================================================

  /** BUF-1: single-arc CircularString buffer → CurvePolygon(CompoundCurve(...)). */
  public void test_BUF_1_singleArcBufferReturnsCurvePolygon() throws Exception {
    Geometry arc = read("CIRCULARSTRING (45 45, 0 90, -45 45)");
    Geometry buf = arc.buffer(12.0);
    fail("BUF-1: single-arc buffer should return a CurvePolygon with CompoundCurve "
        + "rings (outerArc, cap0, innerArcRev, cap1); got " + buf.getGeometryType()
        + " with " + buf.getNumPoints() + " densified vertices.");
  }

  /** BUF-N: multi-arc / mixed CompoundCurve buffer preserves arcs. */
  public void test_BUF_N_compoundCurveBufferPreservesArcs() throws Exception {
    Geometry g = read(
        "COMPOUNDCURVE ((0 0, 10 0), CIRCULARSTRING (10 0, 15 5, 20 0))");
    Geometry buf = g.buffer(2.0);
    fail("BUF-N: CompoundCurve buffer should produce CurvePolygon-bearing output "
        + "with arc-preserving offsets; got " + buf.getGeometryType() + ".");
  }

  /** BUF-NEG: negative buffer with R < d behaves cleanly. */
  public void test_BUF_NEG_negativeBufferGracefulWhenDistanceExceedsRadius() throws Exception {
    // Half-circle R=5, buffer -10 → should return empty cleanly.
    Geometry arc = read("CIRCULARSTRING (-5 0, 0 5, 5 0)");
    Geometry buf = arc.buffer(-10.0);
    fail("BUF-NEG: negative buffer where |d| > R should yield EMPTY; today the path "
        + "densifies and produces a polyline self-collapse, returning "
        + buf.getGeometryType() + " with " + buf.getNumPoints() + " points.");
  }

  /** OFF: OffsetCurve preserves arc identity. */
  public void test_OFF_offsetCurveOnArcReturnsArc() throws Exception {
    fail("OFF: org.locationtech.jts.operation.buffer.OffsetCurve on a CircularString "
        + "should return an analytically-offset CircularString (R±d, same C, same "
        + "sweep), not a densified polyline. Currently densifies before offsetting.");
  }

  /** VBF: VariableBuffer arc-aware. */
  public void test_VBF_variableBufferOnArcInterpolatesAlongArcLength() throws Exception {
    fail("VBF: org.locationtech.jts.operation.buffer.VariableBuffer on a CircularString "
        + "should interpolate the per-vertex distance along arc-length parameter, not "
        + "chord-cumulative length, and emit arc-segment offsets where possible.");
  }

  // ============================================================
  // Distance
  // ============================================================

  // D-OP, D-HF shipped (linearize in DistanceOp + green; HF approx via sampling).

  // ============================================================
  // Predicates / Relate
  // ============================================================

  // R-PR, R-CONT, R-EQ shipped (linearize in RelateOp + green).

  // ============================================================
  // Noding (foundation for overlay & predicates)
  // ============================================================

  /** N-AA: arc-vs-arc analytical intersection. */
  public void test_N_AA_arcVersusArcIntersectionPoints() throws Exception {
    fail("N-AA: need a public utility for arc-arc intersection (two-circle solve "
        + "+ sweep clip) returning 0/1/2 points with parameters on each arc. "
        + "Foundation for OV/R-PR.");
  }

  /** N-AL: arc-vs-line-segment analytical intersection. */
  public void test_N_AL_arcVersusLineIntersectionPoints() throws Exception {
    fail("N-AL: need a public utility for arc-line-segment intersection "
        + "(line-circle solve + sweep clip + segment clamp).");
  }

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
        "CURVEPOLYGON (CIRCULARSTRING (-10 0, 0 10, 10 0, 0 -10, -10 0))");
    Geometry diskB = read(
        "CURVEPOLYGON (CIRCULARSTRING (5 0, 15 10, 25 0, 15 -10, 5 0))");
    Geometry u = diskA.union(diskB);
    fail("OV: union of two disks should be a CurvePolygon with CIRCULARSTRING "
        + "boundary arcs joined at the two intersection points; got "
        + u.getGeometryType() + " with " + u.getNumPoints() + " densified vertices.");
  }

  // ============================================================
  // Centroid / Interior point
  // ============================================================



  // ============================================================
  // Validity
  // ============================================================

  // V-CP, V-CS shipped (linearize in IsValidOp/IsSimpleOp + green).

  // ============================================================
  // Hulls
  // ============================================================

  // H-CV shipped (red deleted per epic §5/11; see RGR for arcHullVertex + ConvexHull extremes + green).

  // H-CC shipped (red deleted per epic §5/11; see RGR for linearize in ConcaveHull + green).

  // ============================================================
  // Simplification
  // ============================================================

  // S-DP, S-VW, S-TP shipped (reds deleted; impl linearize in simplifiers + green).

  // ============================================================
  // Affine transforms
  // ============================================================

  // AT-S, AT-NS shipped (reds deleted; impl in AffineTransformation + green).

  // ============================================================
  // Linear referencing
  // ============================================================

  // LRF-LOC shipped (red deleted per epic §5/11; see RGR commit for impl + seam).

  // ============================================================
  // Densifier
  // ============================================================

  /**
   * DSF: Densifier delegates to toLinear for arc input.
   *
   * <p>RED-FIRST SEAM IDENTIFICATION (for RGR on this TAG; low risk/cost now that toLinear is structural):
   * <ul>
   *   <li>Seam in Densifier (core): static densify(geom, tol) and internal DensifyTransformer.transformCoordinates
   *       always treat input as polyline coords (even if subclass of LineString like CS/CC).
   *       This produces points that may not lie on the original arc.</li>
   *   <li>Delegation seam: use reflection in densify() to detect toLinear(double) method (avoids core->curved dep;
   *       see epic §6 "or shadow"). If present, early-return ((Linearizable)geom).toLinear(tol).
   *       This is the "detect the type and delegate" required by spec.</li>
   *   <li>Risk/cost: low (reflection is safe, no new math, reuses existing toLinear which now supports
   *       structural members). Touches core but isolated change. Alternative shadow in curved not needed.
   *       toLinear(tol) currently returns control-point approx (future can improve densify logic inside toLinear).</li>
   *   <li>Verification: green verif can be simple (use Densifier.densify on CS, assert result is LineString
   *       not further densified beyond toLinear, or just that no error). Meter red deleted on ship.</li>
   * </ul>
   */
  // ============================================================
  // Triangulation / Voronoi
  // ============================================================

  // TRI-DT, TRI-VR shipped (reds deleted; impl linearize in DT/Voronoi builders + green).

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
      return new CurvedWKTReader(new CurvedGeometryFactory()).read(wkt);
    } catch (Exception e) {
      throw new RuntimeException("CurvedWKTReader failed on: " + wkt, e);
    }
  }
}

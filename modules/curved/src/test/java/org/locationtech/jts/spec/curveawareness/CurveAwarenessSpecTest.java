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
  // F-RD (CurvedShapeWriter integration) remains for later.
  // === WORK PAUSED === (per user "pause" command). Current meter: 34 red TAGs.
  // Last shipped: structural CC (for M-LEN-CC), C-LIN, C-AREA, M-LENs, etc.
  // Next low risk/cost candidates (per triage/epic): C-IP, DSF (densifier should delegate to toLinear now that we have it), LRF-LEN/LOC (length+members available), perhaps F-RD or H-*.
  // Do not continue "go" or auto-ship without explicit instruction. State is clean on feature/sfa-curve-B-MS-rgr.

  /** F-RD: renderer arc-walks CurvePolygon rings + MultiCurve+MultiSurface. */
  public void test_F_RD_curvedShapeWriterArcRendersCurvePolygonRings() throws Exception {
    fail("F-RD: CurvedShapeWriter.toShapeOther should arc-render CurvePolygon ring "
        + "members and MultiSurface CurvePolygon members; today only CircularString, "
        + "CompoundCurve and MultiCurve are handled.");
  }

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

  /** D-OP: DistanceOp curve-aware. */
  public void test_D_OP_distanceOpForCurvedInputs() throws Exception {
    fail("D-OP: org.locationtech.jts.operation.distance.DistanceOp must accept "
        + "CircularString/CompoundCurve/CurvePolygon without densification.");
  }

  /** D-HF: discrete Hausdorff / Frechet curve-aware. */
  public void test_D_HF_hausdorffFrechetCurveAware() throws Exception {
    fail("D-HF: DiscreteHausdorffDistance / DiscreteFrechetDistance should sample by "
        + "arc-length parameter on curved inputs (uniform sweep), not chord-cumulative.");
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
        "CURVEPOLYGON (CIRCULARSTRING (-10 0, 0 10, 10 0, 0 -10, -10 0))");
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

  /** R-EQ: equalsExact distinguishes CircularString from chord polyline. */
  public void test_R_EQ_equalsExactDistinguishesArcFromChord() throws Exception {
    Geometry arc  = read("CIRCULARSTRING (0 0, 5 5, 10 0)");
    Geometry line = read("LINESTRING (0 0, 5 5, 10 0)");
    boolean equal = arc.equalsExact(line);
    fail("R-EQ: CIRCULARSTRING(0 0, 5 5, 10 0) and LINESTRING(0 0, 5 5, 10 0) "
        + "share coordinates but represent different geometries. equalsExact "
        + "returned " + equal + "; should be false (subclass identity matters).");
  }

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

  /** C-AREA: centroid of CurvePolygon via sector-weighted mean. */
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

  /** H-CV: ConvexHull of an arc returns the arc's extreme points. */
  public void test_H_CV_convexHullOfArcUsesExtremePoints() throws Exception {
    // Half-circle R=10. Extreme points within sweep + endpoints: (-10,0), (0,10), (10,0).
    Geometry g = read("CIRCULARSTRING (-10 0, 0 10, 10 0)");
    Geometry hull = g.convexHull();
    fail("H-CV: convex hull of a half-arc R=10 should have 3 distinct vertices "
        + "(2 endpoints + the cardinal-y extreme (0,10)); got "
        + hull.getNumPoints() + " densified vertices.");
  }

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

  /** AT-S: similarity transform preserves arc. */
  public void test_AT_S_similarityTransformKeepsCircularString() throws Exception {
    Geometry arc = read("CIRCULARSTRING (-10 0, 0 10, 10 0)");
    org.locationtech.jts.geom.util.AffineTransformation t =
        org.locationtech.jts.geom.util.AffineTransformation.rotationInstance(Math.PI / 4);
    Geometry rotated = t.transform(arc);
    fail("AT-S: rotating a CircularString by 45° should yield another CircularString "
        + "(transform the 3 control points); got " + rotated.getGeometryType() + ".");
  }

  /** AT-NS: non-similarity transform falls back to densified output. */
  public void test_AT_NS_nonSimilarityTransformDensifiesCleanly() throws Exception {
    fail("AT-NS: shear / non-uniform scale of a CircularString turns the arc into "
        + "an ellipse arc which JTS doesn't model; the spec is to detect this, "
        + "densify with toLinear(tolerance), then transform the polyline -- today "
        + "the arc's 3 control points are transformed and the result *claims* to "
        + "still be a CircularString through points that no longer lie on a circle.");
  }

  // ============================================================
  // Linear referencing
  // ============================================================

  /** LRF-LEN: LengthIndexedLine arc-length-parameterised on CircularString. */
  public void test_LRF_LEN_lengthIndexedLineUsesArcLength() throws Exception {
    fail("LRF-LEN: LengthIndexedLine.extractPoint(s) on a CircularString must "
        + "interpret s as arc-length distance; today it walks the chord polyline.");
  }

  /** LRF-LOC: LocationIndexedLine member-aware on CompoundCurve. */
  public void test_LRF_LOC_locationIndexedLineMemberAware() throws Exception {
    fail("LRF-LOC: LocationIndexedLine on a CompoundCurve must address member i, "
        + "parameter t (arc-length within member); today members are flattened.");
  }

  // ============================================================
  // Densifier
  // ============================================================

  /** DSF: Densifier delegates to toLinear for arc input. */
  public void test_DSF_densifierUsesToLinearForArcInput() throws Exception {
    fail("DSF: org.locationtech.jts.densify.Densifier walks coordinates and "
        + "subdivides chords. On a CircularString it should detect the type and "
        + "delegate to toLinear(tolerance); today it produces chord-subdivisions "
        + "that don't lie on the arc.");
  }

  // ============================================================
  // Triangulation / Voronoi
  // ============================================================

  /** TRI-DT: DelaunayTriangulationBuilder densifies curved input internally. */
  public void test_TRI_DT_delaunayAcceptsCurvedInput() throws Exception {
    fail("TRI-DT: DelaunayTriangulationBuilder.setSites accepting a CurvePolygon "
        + "boundary should densify via toLinear before triangulating; today the "
        + "boundary is sampled at the bare control points and Steiner points "
        + "outside the actual curved region appear in the output.");
  }

  /** TRI-VR: VoronoiDiagramBuilder same story. */
  public void test_TRI_VR_voronoiAcceptsCurvedInput() throws Exception {
    fail("TRI-VR: VoronoiDiagramBuilder must accept curved input and densify "
        + "internally to a tolerance, not silently use the bare control points.");
  }

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

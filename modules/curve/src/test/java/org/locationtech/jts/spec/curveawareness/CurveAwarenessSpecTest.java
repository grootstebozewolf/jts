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
 * All 49 {@code fail()} methods remain. That count is the full-TAG
 * red list, not the live scoreboard: closed-form lasers on #8
 * (OverlayNGCurve Phase 0, disc DE-9IM, WKB 8–12, …) keep these
 * methods. Delete a method only when the full TAG ships; do not
 * edit it green. Live progress is the green tests next to
 * production code on #8 and the epic §4.1 table.
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

  /** F-CP: CurvePolygon stores CompoundCurve shell + holes (today: flat LinearRing). */
  public void test_F_CP_curvePolygonStoresCompoundCurveShell() throws Exception {
    Geometry g = read(
        "CURVEPOLYGON ((CIRCULARSTRING (0 0, 5 5, 10 0), CIRCULARSTRING (10 0, 5 -5, 0 0)))");
    assertTrue(g instanceof CurvePolygon);
    fail("F-CP: CurvePolygon should expose its shell as a CompoundCurve; today the WKT "
        + "reader collapses the rings to a flat LinearRing in the parent Polygon.");
  }

  /** F-MC: MultiCurve preserves member subtypes through copy/WKT round-trip. */
  public void test_F_MC_multiCurvePreservesMemberSubtypesThroughCopy() throws Exception {
    Geometry g = read(
        "MULTICURVE ((0 0, 10 0), CIRCULARSTRING (10 0, 15 5, 20 0), "
        + "COMPOUNDCURVE ((20 0, 25 0), CIRCULARSTRING (25 0, 30 5, 35 0)))");
    Geometry copy = g.copy();
    fail("F-MC: copy of a MultiCurve must preserve member-subtype identity; today "
        + "members are reduced to plain LineStrings in copyInternal.");
  }

  /** F-MS: MultiSurface preserves Polygon vs CurvePolygon members. */
  public void test_F_MS_multiSurfacePreservesPolygonVsCurvePolygonMembers() throws Exception {
    Geometry g = read(
        "MULTISURFACE (((0 0, 10 0, 10 10, 0 10, 0 0)), "
        + "CURVEPOLYGON ((CIRCULARSTRING (20 0, 25 5, 30 0), (30 0, 20 0))))");
    fail("F-MS: MultiSurface(Polygon, CurvePolygon).copy() must keep the second member "
        + "as a CurvePolygon; today everything is collapsed to Polygon.");
  }

  /** F-RD: renderer arc-walks CurvePolygon rings + MultiCurve+MultiSurface. */
  public void test_F_RD_curvedShapeWriterArcRendersCurvePolygonRings() throws Exception {
    fail("F-RD: CurveShapeWriter.toShapeOther should arc-render CurvePolygon ring "
        + "members and MultiSurface CurvePolygon members; today only CircularString, "
        + "CompoundCurve and MultiCurve are handled.");
  }

  // ============================================================
  // Metrics
  // ============================================================

  /** M-LEN-CS: CircularString.getLength returns analytical arc length, not chord sum. */
  public void test_M_LEN_CS_circularStringArcLength() throws Exception {
    // Half-circle radius 10 — arc length = π · 10 ≈ 31.4159
    Geometry g = read("CIRCULARSTRING (-10 0, 0 10, 10 0)");
    double expectedArc = Math.PI * 10;
    double actual = g.getLength();
    fail("M-LEN-CS: half-circle (R=10) length should be ≈ " + expectedArc
        + " (π·R) but Geometry.getLength() returned " + actual
        + " (chord-sum of the 3 control points).");
  }

  /** M-LEN-CC: CompoundCurve.getLength sums analytical members. */
  public void test_M_LEN_CC_compoundCurveLengthSumsMembers() throws Exception {
    Geometry g = read(
        "COMPOUNDCURVE ((0 0, 10 0), CIRCULARSTRING (10 0, 15 5, 20 0))");
    // line: 10. half-circle R=5: π·5 ≈ 15.708. Total ≈ 25.708.
    double expected = 10.0 + Math.PI * 5.0;
    double actual = g.getLength();
    fail("M-LEN-CC: line(10)+halfArc(R=5) length should be ≈ " + expected
        + " but got " + actual + ".");
  }

  /** M-AREA-CP: CurvePolygon area uses circular-segment correction. */
  public void test_M_AREA_CP_curvePolygonAreaWithSegmentCorrection() throws Exception {
    // Disk of radius 10 expressed as CURVEPOLYGON of two half-arcs. Area = π · R² ≈ 314.159.
    Geometry g = read(
        "CURVEPOLYGON ((CIRCULARSTRING (-10 0, 0 10, 10 0, 0 -10, -10 0)))");
    double expected = Math.PI * 100;
    double actual = g.getArea();
    fail("M-AREA-CP: disk (R=10) area should be ≈ " + expected
        + " (π·R²) but Geometry.getArea() returned " + actual
        + " (treating control points as a flat polygon).");
  }

  /** M-DIM: dimension and coordinate dimension correct for empty curved subtypes. */
  public void test_M_DIM_emptyCurveDimensions() throws Exception {
    Geometry e1 = read("CIRCULARSTRING EMPTY");
    Geometry e2 = read("CURVEPOLYGON EMPTY");
    assertEquals(1, e1.getDimension());
    assertEquals(2, e2.getDimension());
    fail("M-DIM: smoke-tested today but spec needs an explicit guard so a future "
        + "refactor doesn't regress empty-curved dimension semantics.");
  }

  // ============================================================
  // Boundary
  // ============================================================

  /** B-CP: CurvePolygon.getBoundary() returns a CompoundCurve. */
  public void test_B_CP_curvePolygonBoundaryIsCompoundCurve() throws Exception {
    Geometry g = read(
        "CURVEPOLYGON ((CIRCULARSTRING (0 0, 5 5, 10 0), (10 0, 0 0)))");
    Geometry boundary = g.getBoundary();
    fail("B-CP: CurvePolygon.getBoundary() should be a CompoundCurve(CircularString, "
        + "LineString); got " + boundary.getGeometryType() + ".");
  }

  /** B-MS: MultiSurface.getBoundary() returns a MultiCurve. */
  public void test_B_MS_multiSurfaceBoundaryIsMultiCurve() throws Exception {
    Geometry g = read(
        "MULTISURFACE (((0 0, 10 0, 10 10, 0 10, 0 0)), "
        + "CURVEPOLYGON ((CIRCULARSTRING (20 0, 25 5, 30 0), (30 0, 20 0))))");
    Geometry boundary = g.getBoundary();
    fail("B-MS: MultiSurface.getBoundary() should be a MultiCurve preserving curved "
        + "ring members; got " + boundary.getGeometryType() + ".");
  }

  /** B-CC: open CompoundCurve boundary = its 2 endpoints; closed = empty. */
  public void test_B_CC_openCompoundCurveBoundaryIsTwoEndpoints() throws Exception {
    Geometry g = read("COMPOUNDCURVE ((0 0, 10 0), CIRCULARSTRING (10 0, 15 5, 20 0))");
    Geometry boundary = g.getBoundary();
    assertEquals("MultiPoint", boundary.getGeometryType());
    fail("B-CC: explicit guard needed -- existing LineString boundary semantics are "
        + "inherited but not asserted for the new structural CompoundCurve.");
  }

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

  /** D-PT: point-to-arc distance. */
  public void test_D_PT_pointToArcDistanceClampsToSweep() throws Exception {
    // Half-circle (-5,0)..(5,0) through (0,5). Centre (0,0). External point (0, 10).
    Geometry arc = read("CIRCULARSTRING (-5 0, 0 5, 5 0)");
    Geometry pt  = read("POINT (0 10)");
    double expected = 5.0;     // 10 - radius 5
    double actual   = arc.distance(pt);
    fail("D-PT: distance from POINT(0 10) to half-arc R=5 should be " + expected
        + ", got " + actual + " (chord-treated polyline distance).");
  }

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
   * D-HF: discrete / directed Hausdorff on curved inputs must sample the arc,
   * not the control-point polyline (or chord-fraction densify of that polyline).
   * <p>
   * Witness (asymmetric single arc above the x-axis):
   * <pre>
   *   A = CIRCULARSTRING (0 0, 2 3, 10 0)
   *   B = LINESTRING (0 0, 10 0)   // the chord / diameter baseline
   * </pre>
   * Oriented Hausdorff {@code h(A,B) = max_{a in A} min_{b in B} d(a,b)} is the
   * max height of A above B. {@code DiscreteHausdorffDistance} on #7 has
   * closed-form for two pairs only: single-arc {@code CircularString} →
   * single-segment {@code LineString} (apex {@code √949/6 − 7/6} ≈ 3.967641),
   * and two circular discs. A single-member {@code MultiSurface} of one disc
   * is the same pair, not a third. The exact path owns APEX and skips densify;
   * densify 0.05 is not the laser. Stale mid-control {@code h = 3} is retired.
   * That exception is not the full TAG: public DHD still sees chords in
   * general. Keep this {@code fail()}.
   */
  public void test_D_HF_hausdorffFrechetCurveAware() throws Exception {
    Geometry arc = read("CIRCULARSTRING (0 0, 2 3, 10 0)");
    Geometry baseline = read("LINESTRING (0 0, 10 0)");

    // Apex of the locked pair: √949/6 − 7/6. Exact path owns this; densifyFrac
    // is skipped on this pair (not the laser).
    final double expectedContinuous = 3.967641;
    final double tol = 1e-3;

    double exactPath =
        DiscreteHausdorffDistance.orientedDistance(arc, baseline);
    double densifyCall =
        DiscreteHausdorffDistance.orientedDistance(arc, baseline, 0.05);

    // Full-TAG ratchet: always fail. Exact path owns APEX; densify is skipped
    // on this pair, not the laser. Stale h=3 retired. Keep this fail().
    fail("D-HF: full TAG still open. Public DiscreteHausdorffDistance still sees chords in general. "
        + "DiscreteHausdorffDistance on #7 has closed-form for two pairs only: "
        + "single-arc CircularString → single-segment LineString "
        + "(apex √949/6 − 7/6 ≈ " + expectedContinuous + "), and two circular discs. "
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
  public void test_TRI_DT_delaunayAcceptsCurveInput() throws Exception {
    fail("TRI-DT: DelaunayTriangulationBuilder.setSites accepting a CurvePolygon "
        + "boundary should densify via toLinear before triangulating; today the "
        + "boundary is sampled at the bare control points and Steiner points "
        + "outside the actual curved region appear in the output.");
  }

  /** TRI-VR: VoronoiDiagramBuilder same story. */
  public void test_TRI_VR_voronoiAcceptsCurveInput() throws Exception {
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

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
package org.locationtech.jts.geom.curve;

import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.IntersectionMatrix;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.operation.overlayng.curve.OverlayNGCurve;

/**
 * Routes the inherited jts-core spatial operations through a densified copy of
 * a curve, so they see the arc instead of the chords through its control
 * points.
 * <p>
 * The operations themselves ({@code ConvexHull}, {@code DistanceOp},
 * {@code BufferOp}, {@code Centroid}, {@code InteriorPoint}) live in jts-core
 * and have no visibility of the curve types, since jts-curve depends on core
 * rather than the reverse. Densifying at the boundary is what lets them stay
 * untouched.
 * <p>
 * {@link #linearise(Geometry)} and {@link #TOLERANCE_FRACTION} are public because
 * {@code org.locationtech.jts.operation.overlayng.curve.OverlayNGCurve} needs the
 * same tolerance from another package; duplicating the constant would let the two
 * drift. The remaining methods stay package-private.
 * <p>
 * Results are therefore approximations bounded by {@link #TOLERANCE_FRACTION},
 * not exact arc answers. Where an exact closed form exists it is used directly
 * instead -- see {@code CircularString.getLength()},
 * {@code CurvePolygon.getArea()} and {@code CircularArcDensifier.expandEnvelope}.
 */
public final class CurveOps {

  /**
   * Densification tolerance as a fraction of the geometry's extent.
   * <p>
   * Tighter than {@code CircularArcDensifier.DEFAULT_TOLERANCE_FRACTION} (1%,
   * chosen for rendering) because these feed numeric predicates rather than
   * pixels. Scaling by extent keeps the relative accuracy uniform: a 1-unit arc
   * and a 10000-unit arc are approximated equally well.
   */
  public static final double TOLERANCE_FRACTION = 1.0e-6;

  private CurveOps() { }

  /**
   * A densified copy of {@code g} if it is a curve, otherwise {@code g}
   * unchanged. Applied to both operands so a curve-to-curve operation sees
   * arcs on both sides.
   */
  public static Geometry linearise(Geometry g) {
    if (!(g instanceof Linearizable)) return g;
    return ((Linearizable) g).toLinear(tolerance(g));
  }

  /**
   * The densification tolerance {@link #linearise(Geometry)} would use for this
   * geometry, and therefore the maximum distance by which its densified copy
   * deviates from the true arc. Zero for a geometry with no arc, whose linearised
   * form is itself.
   * <p>
   * Public so a caller deciding something on densified copies can tell how far
   * from the boundary of that decision it needs to be before the answer is safe.
   * The densified ring is inscribed, so it lies within this distance <em>inside</em>
   * the true arc, and a predicate evaluated on it can differ from the truth only
   * within that band.
   */
  public static double tolerance(Geometry g) {
    if (!(g instanceof Linearizable)) return 0.0;
    Envelope env = g.getEnvelopeInternal();
    double extent = Math.max(env.getWidth(), env.getHeight());
    return (extent > 0.0 ? extent : 1.0) * TOLERANCE_FRACTION;
  }

  static Geometry convexHull(Geometry curve) {
    return linearise(curve).convexHull();
  }

  static double distance(Geometry curve, Geometry other) {
    return linearise(curve).distance(linearise(other));
  }

  static boolean isWithinDistance(Geometry curve, Geometry other, double distance) {
    return linearise(curve).isWithinDistance(linearise(other), distance);
  }

  static Geometry buffer(Geometry curve, double distance) {
    return linearise(curve).buffer(distance);
  }

  static Geometry buffer(Geometry curve, double distance, int quadrantSegments) {
    return linearise(curve).buffer(distance, quadrantSegments);
  }

  static Geometry buffer(Geometry curve, double distance, int quadrantSegments,
      int endCapStyle) {
    return linearise(curve).buffer(distance, quadrantSegments, endCapStyle);
  }

  // -- Centroid / interior point (CRV-CTR) ----------------------------------
  //
  // Centroid and InteriorPoint walk getCoordinates(), weighing a curve by its
  // control polygon: the half-arc centroid sat at the control-triangle mean
  // (R/2) instead of 2R/pi, and InteriorPointArea scanning a thin crescent's
  // flat control ring returned a point outside the curved region -- the one
  // thing an interior point must never be. On the densified copy the centroid
  // is tolerance-bounded and the interior point genuinely interior: the scan
  // picks midpoints of interior spans, far from the boundary relative to
  // TOLERANCE_FRACTION.
  //
  // The lineal types keep the inherited getInteriorPoint: it returns a vertex,
  // and a curve's control points lie on the arc by definition, so the contract
  // already holds there. Exact sector-weighted centroid forms exist (the
  // epic's C-AREA names them) and may replace the densified walk later without
  // changing the contract; CircularArcDensifier.arcAreaContribution is the
  // precedent for exact area terms.

  static Point centroid(Geometry curve) {
    return linearise(curve).getCentroid();
  }

  static Point interiorPoint(Geometry curve) {
    return linearise(curve).getInteriorPoint();
  }

  // -- Simplicity / validity (CRV-SV) ----------------------------------------
  //
  // IsSimpleOp and IsValidOp node getCoordinates(), so their verdicts are
  // about the chords, and the error runs both ways: a segment piercing an
  // arc's bulge without touching any chord left a self-crossing curve
  // "simple", and a hole in the band between a chord and its arc -- inside
  // the true region, outside the flat control ring -- made a valid
  // CurvePolygon "invalid". Like the predicates above, these are booleans
  // with no tolerance to hide in: input within TOLERANCE_FRACTION of a
  // simplicity or validity transition is undecidable on the densified copy.
  //
  // isSimple is routed for the lineal types and MultiCurve only; polygonal
  // isSimple is definitionally true in core and needs no help. isValid is
  // routed for the areal types only; lineal validity does not depend on the
  // shape between control points. As with the predicates, only the instance
  // methods are interceptable: a caller invoking the IsSimpleOp / IsValidOp
  // statics directly still gets the chord verdict.

  static boolean isSimple(Geometry curve) {
    return linearise(curve).isSimple();
  }

  static boolean isValid(Geometry curve) {
    return linearise(curve).isValid();
  }

  // -- Overlay ------------------------------------------------------------
  //
  // Left to the inherited implementations, these node the chords through the
  // control points: two concentric circles of radius 5 and 3 intersected in 18
  // rather than 9*pi, and unioned to 50 rather than 25*pi.
  //
  // Delegated to OverlayNGCurve, the single overlay implementation, so the
  // instance methods inherit the ratchet: A.intersection(A) returns A itself,
  // a nested pair returns the inner operand untouched, a disjoint CUP returns a
  // MultiSurface with arcs intact, and only genuinely crossing operands are
  // densified and noded. An earlier version of this block densified
  // unconditionally and stated that "no tolerance choice returns a CurvePolygon
  // here" -- true of the noded path, which still cannot preserve an arc (that
  // needs arc-arc intersection and splitting, a subsystem not a shim), but not
  // of the algebra and retention stages in front of it.
  //
  // The no-argument Geometry.union() is deliberately NOT routed here: for a
  // single CurvePolygon it already returns the geometry itself, area exact, and
  // densifying would replace an exact answer with an approximate one.

  static Geometry intersection(Geometry curve, Geometry other) {
    return OverlayNGCurve.intersection(curve, other);
  }

  static Geometry union(Geometry curve, Geometry other) {
    return OverlayNGCurve.union(curve, other);
  }

  static Geometry difference(Geometry curve, Geometry other) {
    return OverlayNGCurve.difference(curve, other);
  }

  static Geometry symDifference(Geometry curve, Geometry other) {
    return OverlayNGCurve.symDifference(curve, other);
  }

  // -- Predicates (CRV-REL) -------------------------------------------------
  //
  // The inherited predicates evaluate getCoordinates(), judging a curve by its
  // control polygon: contains(POINT (3 3)) was false for the radius-5 circle
  // (the point is 4.243 from the centre), and a CircularString "intersected"
  // segments that only touch the chords. Booleans have no tolerance to hide in.
  //
  // Every predicate is overridden individually, because in this core almost
  // none of them route through this.relate(other): touches, intersects, within,
  // contains, overlaps, covers, coveredBy, relate and equalsTopo each call a
  // GeometryRelate static (the RelateNG entry points), which no override can
  // intercept. Only crosses still goes through this.relate(g). An earlier
  // version of this comment claimed one relate override would carry the family;
  // the three contains/covers failures that survived it are why each predicate
  // now has its own. disjoint is the exception -- core defines it as
  // !intersects(g), so it follows the intersects override.
  //
  // Verdicts are evaluated on inscribed copies, so input within TOLERANCE_FRACTION
  // of a boundary transition is undecidable here -- the same band the overlay
  // ratchet's margin gate refuses to decide in. The reverse direction
  // (plain.contains(curve)) dispatches on the plain type and remains chord-based.

  static IntersectionMatrix relate(Geometry curve, Geometry other) {
    return linearise(curve).relate(linearise(other));
  }

  static boolean intersects(Geometry curve, Geometry other) {
    return linearise(curve).intersects(linearise(other));
  }

  static boolean within(Geometry curve, Geometry other) {
    return linearise(curve).within(linearise(other));
  }

  static boolean coveredBy(Geometry curve, Geometry other) {
    return linearise(curve).coveredBy(linearise(other));
  }
  static boolean touches(Geometry curve, Geometry other) {
    return linearise(curve).touches(linearise(other));
  }

  static boolean contains(Geometry curve, Geometry other) {
    return linearise(curve).contains(linearise(other));
  }

  static boolean overlaps(Geometry curve, Geometry other) {
    return linearise(curve).overlaps(linearise(other));
  }

  static boolean covers(Geometry curve, Geometry other) {
    return linearise(curve).covers(linearise(other));
  }

  static boolean crosses(Geometry curve, Geometry other) {
    return linearise(curve).crosses(linearise(other));
  }

  static boolean relate(Geometry curve, Geometry other, String pattern) {
    return linearise(curve).relate(linearise(other), pattern);
  }

  static boolean equalsTopo(Geometry curve, Geometry other) {
    return linearise(curve).equalsTopo(linearise(other));
  }
}

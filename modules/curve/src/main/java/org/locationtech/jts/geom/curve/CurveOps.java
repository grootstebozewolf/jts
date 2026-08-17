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

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.IntersectionMatrix;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.operation.overlayng.curve.OverlayNGCurve;

/**
 * Routes the inherited jts-core spatial operations through a densified copy of
 * a curve, so they see the arc instead of the chords through its control
 * points.
 * <p>
 * Envelope filters run first (<b>PERF-GATE</b>). An envelope miss is exact --
 * curve envelopes cover the arc -- and cheaper than densifying. Distance,
 * buffer, convex hull, disc-vs-point PIP, and disc-vs-point / line /
 * polygon DE-9IM take a closed form when a cheap shape check can
 * answer (circular disc, single arc, circular-plus-straight hull,
 * point-vs-arc, disc-vs-line, disc-vs-polygon); see {@code CurveExact}.
 * Anything else falls through to the locationtech/jts chord baseline:
 * {@link #linearise(Geometry)}, then the core algorithm. Overlay is not
 * routed here; it goes to {@link OverlayNGCurve}, whose ratchet has the same
 * gate. The overlay ratchet is not a prescription for these ops.
 * <p>
 * The operations themselves ({@code ConvexHull}, {@code DistanceOp},
 * {@code BufferOp}) live in jts-core and have no visibility of the curve types,
 * since jts-curve depends on core rather than the reverse. Densifying at the
 * boundary is what lets them stay untouched.
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
   * <p>
   * Honour {@link CurveLinearizationStrategy}: {@link
   * CurveLinearizationStrategy#PRESERVE} returns {@code g} unchanged.
   * {@link CurveLinearizationStrategy#LINEARIZED} (default) densifies and
   * <b>always warns</b> — no silent flatten (#1195 MMF).
   */
  public static Geometry linearise(Geometry g) {
    if (!(g instanceof Linearizable)) return g;
    if (CurveLinearizationStrategy.current()
        == CurveLinearizationStrategy.PRESERVE) {
      return g;
    }
    CurveLinearizationStrategy.warnLinearized(g, "CurveOps.linearise");
    return ((Linearizable) g).toLinear(tolerance(g));
  }

  /**
   * Densify a lineal curve into a polyline whose vertices are spaced
   * by equal <em>arc length</em> (circular members) or equal chord
   * length (straight members). Used by VariableBuffer so distance
   * interpolation follows arc length rather than control-chord length.
   * Honours {@link CurveLinearizationStrategy} (warns on LINEARIZED).
   */
  public static Geometry lineariseArcLength(Geometry g, int samplesPerArc) {
    if (g == null || g.isEmpty()) {
      return g;
    }
    if (!(g instanceof Linearizable) && !(g instanceof LineString)) {
      return g;
    }
    if (CurveLinearizationStrategy.current()
        == CurveLinearizationStrategy.PRESERVE) {
      return g;
    }
    if (g instanceof CircularString || g instanceof CompoundCurve
        || g instanceof BezierCurve || g instanceof ClothoidSegment
        || g instanceof EllipseCurve || g instanceof NurbsCurve) {
      CurveLinearizationStrategy.warnLinearized(g, "CurveOps.lineariseArcLength");
    }
    int n = Math.max(1, samplesPerArc);
    if (g instanceof CircularString) {
      return lineariseCircularStringArcLength((CircularString) g, n);
    }
    if (g instanceof CompoundCurve) {
      CompoundCurve cc = (CompoundCurve) g;
      java.util.ArrayList<Coordinate> pts = new java.util.ArrayList<Coordinate>();
      for (int i = 0; i < cc.getNumMembers(); i++) {
        LineString m = cc.getMemberN(i);
        Geometry piece = lineariseArcLength(m, n);
        Coordinate[] c = piece.getCoordinates();
        int from = pts.isEmpty() ? 0 : 1;
        for (int k = from; k < c.length; k++) {
          pts.add(c[k]);
        }
      }
      return g.getFactory().createLineString(
          pts.toArray(new Coordinate[0]));
    }
    if (g instanceof Linearizable) {
      return ((Linearizable) g).toLinear(tolerance(g));
    }
    return g;
  }

  private static Geometry lineariseCircularStringArcLength(CircularString cs,
      int samplesPerArc) {
    org.locationtech.jts.geom.CoordinateSequence seq = cs.getCoordinateSequence();
    int n = seq.size();
    if (n < 3) {
      return cs.copy();
    }
    java.util.ArrayList<Coordinate> out = new java.util.ArrayList<Coordinate>();
    for (int i = 0; i + 2 < n; i += 2) {
      java.util.List<Coordinate> chord = CircularArcDensifier.densifyArcUniform(
          seq.getCoordinate(i), seq.getCoordinate(i + 1), seq.getCoordinate(i + 2),
          samplesPerArc);
      int from = out.isEmpty() ? 0 : 1;
      for (int k = from; k < chord.size(); k++) {
        out.add(chord.get(k));
      }
    }
    return cs.getFactory().createLineString(out.toArray(new Coordinate[0]));
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
    if (!hasCircularArc(g)) return 0.0;
    Envelope env = g.getEnvelopeInternal();
    double extent = Math.max(env.getWidth(), env.getHeight());
    return (extent > 0.0 ? extent : 1.0) * TOLERANCE_FRACTION;
  }

  /**
   * True when {@code g} contains a {@link CircularString}. An arc-free
   * {@link CurvePolygon} of plain rings is {@link Linearizable} but has
   * no arc, so {@link #tolerance(Geometry)} is zero.
   */
  static boolean hasCircularArc(Geometry g) {
    if (g == null || g.isEmpty()) return false;
    if (g instanceof CircularString) return true;
    if (g instanceof CompoundCurve) {
      CompoundCurve cc = (CompoundCurve) g;
      for (int i = 0; i < cc.getNumMembers(); i++) {
        if (hasCircularArc(cc.getMemberN(i))) return true;
      }
      return false;
    }
    if (g instanceof CurvePolygon) {
      CurvePolygon cp = (CurvePolygon) g;
      if (hasCircularArc(cp.getExteriorCurve())) return true;
      for (int i = 0; i < cp.getNumInteriorRing(); i++) {
        if (hasCircularArc(cp.getInteriorCurveN(i))) return true;
      }
      return false;
    }
    int n = g.getNumGeometries();
    if (n >= 1 && g.getGeometryN(0) != g) {
      for (int i = 0; i < n; i++) {
        if (hasCircularArc(g.getGeometryN(i))) return true;
      }
    }
    return false;
  }

  static Geometry convexHull(Geometry curve) {
    Geometry exact = CurveExact.convexHull(curve);
    if (exact != null) return exact;
    return linearise(curve).convexHull();
  }

  static double distance(Geometry curve, Geometry other) {
    Double exact = CurveExact.distance(curve, other);
    if (exact != null) return exact.doubleValue();
    return linearise(curve).distance(linearise(other));
  }

  static boolean isWithinDistance(Geometry curve, Geometry other, double distance) {
    // Envelope distance is a lower bound. If it already exceeds the threshold
    // the densified call cannot come back true, so skip the chords.
    if (curve.getEnvelopeInternal().distance(other.getEnvelopeInternal()) > distance) {
      return false;
    }
    return linearise(curve).isWithinDistance(linearise(other), distance);
  }

  static Geometry buffer(Geometry curve, double distance) {
    Geometry exact = CurveExact.buffer(curve, distance);
    if (exact != null) return exact;
    return linearise(curve).buffer(distance);
  }

  static Geometry buffer(Geometry curve, double distance, int quadrantSegments) {
    Geometry exact = CurveExact.buffer(curve, distance);
    if (exact != null) return exact;
    return linearise(curve).buffer(distance, quadrantSegments);
  }

  static Geometry buffer(Geometry curve, double distance, int quadrantSegments,
      int endCapStyle) {
    Geometry exact = CurveExact.buffer(curve, distance);
    if (exact != null) return exact;
    return linearise(curve).buffer(distance, quadrantSegments, endCapStyle);
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
  // A circular disc vs a Point or MultiPoint now takes CurveExact (d² vs r²)
  // after the envelope miss for contains/covers, and the same location for
  // relate / relate(pattern). A disc vs a LineString (or a single-member
  // MultiLineString) or a plain Polygon takes the line–circle nodes
  // (R1.6 / ARC_SEGMENT_XY) for relate / relate(pattern) and intersects.
  // Two circular discs take the radical-axis nodes for the same family.
  // contains / covers / intersects / overlaps / crosses / equalsTopo
  // follow that matrix. Two areas never cross.
  // The control-point diamond is no longer the answer. Other shapes
  // still linearise.
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
  // Envelope filters run before any densify: a miss is exact (curve envelopes
  // cover the arc) and is the laser that beats the chord call. Verdicts the
  // envelope cannot decide are evaluated on inscribed copies, so input within
  // TOLERANCE_FRACTION of a boundary transition is undecidable here -- the same
  // band the overlay ratchet's margin gate refuses to decide in. Reverse
  // direction (plain.contains(curve)) is flipped in Geometry onto the curve
  // receiver, so these methods run for both operand orders. Difference is
  // not symmetric: Geometry.difference routes (plain, curve) through
  // OverlayNGCurve so the ratchet sees the operands in that order.
  // MultiCurve / MultiSurface override the same family.

  static IntersectionMatrix relate(Geometry curve, Geometry other) {
    IntersectionMatrix exact = CurveExact.relate(curve, other);
    if (exact != null) return exact;
    return linearise(curve).relate(linearise(other));
  }

  static boolean intersects(Geometry curve, Geometry other) {
    if (!curve.getEnvelopeInternal().intersects(other.getEnvelopeInternal())) {
      return false;
    }
    IntersectionMatrix exact = CurveExact.relate(curve, other);
    if (exact != null) return exact.isIntersects();
    return linearise(curve).intersects(linearise(other));
  }

  static boolean within(Geometry curve, Geometry other) {
    if (!other.getEnvelopeInternal().covers(curve.getEnvelopeInternal())) {
      return false;
    }
    IntersectionMatrix exact = CurveExact.relate(curve, other);
    if (exact != null) return exact.isWithin();
    return linearise(curve).within(linearise(other));
  }

  static boolean coveredBy(Geometry curve, Geometry other) {
    if (!other.getEnvelopeInternal().covers(curve.getEnvelopeInternal())) {
      return false;
    }
    // A rectangle that covers the arc AABB covers the arc. Densifying
    // to re-ask the same question is the wrapper the nested multi-vs-
    // plain gate refused.
    if (other.isRectangle()) return true;
    IntersectionMatrix exact = CurveExact.relate(curve, other);
    if (exact != null) return exact.isCoveredBy();
    return linearise(curve).coveredBy(linearise(other));
  }
  static boolean touches(Geometry curve, Geometry other) {
    if (!curve.getEnvelopeInternal().intersects(other.getEnvelopeInternal())) {
      return false;
    }
    IntersectionMatrix exact = CurveExact.relate(curve, other);
    if (exact != null) return exact.isTouches(curve.getDimension(), other.getDimension());
    return linearise(curve).touches(linearise(other));
  }

  static boolean contains(Geometry curve, Geometry other) {
    if (!curve.getEnvelopeInternal().covers(other.getEnvelopeInternal())) {
      return false;
    }
    Boolean exact = CurveExact.contains(curve, other);
    if (exact != null) return exact.booleanValue();
    IntersectionMatrix im = CurveExact.relate(curve, other);
    if (im != null) return im.isContains();
    return linearise(curve).contains(linearise(other));
  }

  static boolean overlaps(Geometry curve, Geometry other) {
    if (!curve.getEnvelopeInternal().intersects(other.getEnvelopeInternal())) {
      return false;
    }
    IntersectionMatrix exact = CurveExact.relate(curve, other);
    if (exact != null) return exact.isOverlaps(curve.getDimension(), other.getDimension());
    return linearise(curve).overlaps(linearise(other));
  }

  static boolean covers(Geometry curve, Geometry other) {
    if (!curve.getEnvelopeInternal().covers(other.getEnvelopeInternal())) {
      return false;
    }
    Boolean exact = CurveExact.covers(curve, other);
    if (exact != null) return exact.booleanValue();
    IntersectionMatrix im = CurveExact.relate(curve, other);
    if (im != null) return im.isCovers();
    return linearise(curve).covers(linearise(other));
  }

  static boolean crosses(Geometry curve, Geometry other) {
    if (!curve.getEnvelopeInternal().intersects(other.getEnvelopeInternal())) {
      return false;
    }
    IntersectionMatrix exact = CurveExact.relate(curve, other);
    if (exact != null) return exact.isCrosses(curve.getDimension(), other.getDimension());
    return linearise(curve).crosses(linearise(other));
  }

  static boolean relate(Geometry curve, Geometry other, String pattern) {
    IntersectionMatrix exact = CurveExact.relate(curve, other);
    if (exact != null) return exact.matches(pattern);
    return linearise(curve).relate(linearise(other), pattern);
  }

  static boolean equalsTopo(Geometry curve, Geometry other) {
    IntersectionMatrix exact = CurveExact.relate(curve, other);
    if (exact != null) return exact.isEquals(curve.getDimension(), other.getDimension());
    return linearise(curve).equalsTopo(linearise(other));
  }
}

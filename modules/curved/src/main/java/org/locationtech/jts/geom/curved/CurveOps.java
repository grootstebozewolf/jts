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

import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;

/**
 * Routes the inherited jts-core spatial operations through a densified copy of
 * a curve, so they see the arc instead of the chords through its control
 * points.
 * <p>
 * The operations themselves ({@code ConvexHull}, {@code DistanceOp},
 * {@code BufferOp}) live in jts-core and have no visibility of the curve types,
 * since jts-curved depends on core rather than the reverse. Densifying at the
 * boundary is what lets them stay untouched.
 * <p>
 * <p>
 * {@link #linearise(Geometry)} and {@link #TOLERANCE_FRACTION} are public because
 * {@code org.locationtech.jts.operation.overlayng.curved.OverlayNGCurve} needs the
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
    Envelope env = g.getEnvelopeInternal();
    double extent = Math.max(env.getWidth(), env.getHeight());
    double tolerance = (extent > 0.0 ? extent : 1.0) * TOLERANCE_FRACTION;
    return ((Linearizable) g).toLinear(tolerance);
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

  // -- Overlay ------------------------------------------------------------
  //
  // Left to the inherited implementations, these node the chords through the
  // control points: two concentric circles of radius 5 and 3 intersected in 18
  // rather than 9*pi, and unioned to 50 rather than 25*pi.
  //
  // Unlike getArea() or getLength() there is no closed form to reach for, and
  // unlike addHole this is not structural. Overlay has to node the linework, and
  // noding arcs exactly would require arc-arc intersection and arc splitting --
  // a subsystem, not a shim -- so the result is a densified plain geometry and
  // the arc does not survive. That is a property of the operation, not of the
  // tolerance: no tolerance choice returns a CurvePolygon here.
  //
  // Densifying also removes an inconsistency that was breaking core outright.
  // OverlayNG cross-checks the result's area against the operands', and a
  // CurvePolygon reported an arc-aware getArea() of 78.54 while its
  // getCoordinates() enclosed 50, so UNION and DIFFERENCE threw
  // TopologyException("Result area inconsistent with overlay operation"). Handing
  // core a densified operand gives it one self-consistent geometry instead of a
  // geometry that disagreed with itself.
  //
  // The no-argument Geometry.union() is deliberately NOT routed here: for a
  // single CurvePolygon it already returns the geometry itself, area exact, and
  // densifying would replace an exact answer with an approximate one.

  static Geometry intersection(Geometry curve, Geometry other) {
    return linearise(curve).intersection(linearise(other));
  }

  static Geometry union(Geometry curve, Geometry other) {
    return linearise(curve).union(linearise(other));
  }

  static Geometry difference(Geometry curve, Geometry other) {
    return linearise(curve).difference(linearise(other));
  }

  static Geometry symDifference(Geometry curve, Geometry other) {
    return linearise(curve).symDifference(linearise(other));
  }
}

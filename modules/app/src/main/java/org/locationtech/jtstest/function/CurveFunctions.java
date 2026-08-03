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
package org.locationtech.jtstest.function;

import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryCollection;
import org.locationtech.jts.geom.curved.Linearizable;
import org.locationtech.jtstest.geomfunction.Metadata;

/**
 * Functions on the OGC SFA / ISO 19125-2 curve geometry types.
 * <p>
 * TestBuilder renders curves as true arcs, so the canvas looks correct without
 * any linearisation. That is exactly why {@link #toLinear} is worth exposing:
 * every legacy consumer of a curve receives the polyline, not the arc, and this
 * is the only way to see that polyline at a tolerance you choose.
 */
public class CurveFunctions {

  /**
   * Densification tolerance as a fraction of the geometry's extent, used when a
   * caller must linearise but has no tolerance of its own to offer -- notably
   * the static jts-core entry points, which take a {@code Geometry} and so
   * cannot dispatch to a curve override.
   * <p>
   * Deliberately the same value as {@code CurveOps.TOLERANCE_FRACTION} inside
   * jts-curved, so a function reached through a static entry point agrees with
   * the equivalent instance method. The constant is repeated rather than shared
   * because {@code CurveOps} is package-private; if it is ever promoted to
   * public API this should defer to it.
   */
  private static final double OPS_TOLERANCE_FRACTION = 1.0e-6;

  /**
   * Densification tolerance for the triangulation-based hulls, coarser than
   * {@link #OPS_TOLERANCE_FRACTION} by two orders of magnitude.
   * <p>
   * A concave hull is a function of the input <em>point set</em>, so sampling a
   * 1-D curve more finely changes the question rather than refining the answer:
   * the hull erodes down onto the curve and the result becomes a ribbon whose
   * width is one sampling step. At 1e-6 that ribbon was 0.02 wide on a 10-unit
   * geometry -- a hairline that renders as a bowtie -- and the area did not
   * converge with density. This is not arc-specific; core's {@code Densifier} on
   * a plain LineString pinches identically.
   * <p>
   * So the tolerance here matches what the hull can resolve rather than what a
   * distance predicate wants. Deliberately <em>not</em> the same constant as the
   * operations above: {@code convexHull} and {@code distance} converge as
   * sampling tightens, these do not. A pinch stays intrinsic at sufficient zoom;
   * this only keeps the default from being degenerate. Callers wanting a
   * specific sampling should linearise explicitly with {@link #toLinear} first.
   */
  private static final double HULL_TOLERANCE_FRACTION = 1.0e-4;

  /**
   * Linearises every curve in a geometry, replacing each arc with a polyline
   * whose deviation from the true arc is at most {@code tolerance}.
   * <p>
   * Geometries with no curve in them are returned unchanged, so this is safe to
   * apply to anything. Collections are walked recursively, since a curve may be
   * nested inside one.
   * <p>
   * A tolerance of 0 does not mean "exact" -- no polyline can be exact.
   * {@code CircularArcDensifier} reads it as a request for its default, one
   * hundredth of the arc radius.
   */
  @Metadata(description =
      "Replace arcs with polylines deviating by at most the given tolerance")
  public static Geometry toLinear(Geometry g,
      @Metadata(title = "Tolerance")
      double tolerance) {
    return linearize(g, tolerance);
  }

  /**
   * Linearises at {@link #OPS_TOLERANCE_FRACTION} of the geometry's extent, for
   * callers that must hand a curve to an operation which cannot see curve types.
   * <p>
   * Scaling by extent keeps the relative accuracy uniform, so a 1-unit arc and a
   * 10000-unit arc are approximated equally well. Geometries with no curve in
   * them are returned unchanged, so this is safe to apply unconditionally.
   */
  public static Geometry linearizeForOps(Geometry g) {
    return linearizeAtFraction(g, OPS_TOLERANCE_FRACTION);
  }

  /**
   * Linearises at {@link #HULL_TOLERANCE_FRACTION} of the geometry's extent, for
   * the triangulation-based hulls, which cannot resolve detail finer than their
   * own erosion threshold.
   */
  public static Geometry linearizeForHull(Geometry g) {
    return linearizeAtFraction(g, HULL_TOLERANCE_FRACTION);
  }

  private static Geometry linearizeAtFraction(Geometry g, double fraction) {
    if (g == null || g.isEmpty()) return g;
    Envelope env = g.getEnvelopeInternal();
    double extent = Math.max(env.getWidth(), env.getHeight());
    return linearize(g, (extent > 0.0 ? extent : 1.0) * fraction);
  }

  /**
   * Shared with {@link BufferFunctions}, which must linearise before handing a
   * curve to the chord-based buffer curve builder.
   */
  public static Geometry linearize(Geometry g, double tolerance) {
    if (g == null || g.isEmpty()) return g;
    if (g instanceof Linearizable) return ((Linearizable) g).toLinear(tolerance);
    if (g instanceof GeometryCollection) {
      int n = g.getNumGeometries();
      Geometry[] members = new Geometry[n];
      for (int i = 0; i < n; i++) {
        members[i] = linearize(g.getGeometryN(i), tolerance);
      }
      return g.getFactory().createGeometryCollection(members);
    }
    return g;
  }
}

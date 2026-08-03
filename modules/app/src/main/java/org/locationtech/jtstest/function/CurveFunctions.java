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

import java.util.List;

import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryCollection;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.LinearRing;
import org.locationtech.jts.geom.MultiLineString;
import org.locationtech.jts.geom.MultiPolygon;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.geom.curve.CircularString;
import org.locationtech.jts.geom.curve.CompoundCurve;
import org.locationtech.jts.geom.curve.CurvePolygon;
import org.locationtech.jts.geom.curve.Linearizable;
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
   * jts-curve, so a function reached through a static entry point agrees with
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

  /**
   * Linearises for algorithms whose cost is quadratic in the vertex count, at
   * {@link #HULL_TOLERANCE_FRACTION} of the extent.
   * <p>
   * {@code DiscreteFrechetDistance} fills an n-by-m table. At the operations
   * tolerance (1e-6 of extent) a circle becomes ~1570 vertices and a
   * curve-to-curve Frechet ran for 20 seconds in a visual-QA session; at 1e-4 it
   * becomes ~160 and completes instantly. The price is stated rather than
   * hidden: the discrete Frechet error is bounded by the sampling step, which
   * for chord tolerance t on radius r is about {@code sqrt(8*r*t)} -- 0.2 units
   * on a radius-5 circle here, against 1e-5-scale error elsewhere. For a
   * TestBuilder measure that is the right trade; a caller wanting tighter error
   * can linearise explicitly and pay the quadratic bill knowingly.
   */
  public static Geometry linearizeForQuadratic(Geometry g) {
    return linearizeAtFraction(g, HULL_TOLERANCE_FRACTION);
  }

  /**
   * Linearises curves for a directed-Hausdorff call that has an explicit
   * distance accuracy {@code distTol}.
   * <p>
   * Chord error is taken as {@code max(opsFraction·extent, distTol)} so a large
   * tolerance (e.g. TestBuilder {@code directedHausdorffLineTol(..., 10)}) does
   * not still densify at 1e-6 of extent and then pay for a dense segment queue.
   * Smaller than ops sampling is not requested: {@code distTol} of 0 falls back
   * to {@link #linearizeForOps}.
   * <p>
   * {@code distTol} is <em>not</em> a free-end clip and <em>not</em> a densify
   * fraction — it only caps how finely arcs are replaced by chords before the
   * core directed-Hausdorff search.
   */
  public static Geometry linearizeForDistanceTol(Geometry g, double distTol) {
    if (g == null || g.isEmpty()) return g;
    if (distTol < 0.0) {
      throw new IllegalArgumentException(
          "Distance tolerance must be non-negative (accuracy of the Hausdorff "
          + "approximation in coordinate units, not a densify fraction); got "
          + distTol);
    }
    Envelope env = g.getEnvelopeInternal();
    double extent = Math.max(env.getWidth(), env.getHeight());
    double opsTol = (extent > 0.0 ? extent : 1.0) * OPS_TOLERANCE_FRACTION;
    if (distTol == 0.0) {
      return linearize(g, opsTol);
    }
    // Coarser of the two: never sample denser than ops, and never denser than
    // the accuracy the caller asked for.
    return linearize(g, Math.max(opsTol, distTol));
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
   * <p>
   * A geometry with no curve anywhere in it is returned as <em>the same
   * object</em>, not a copy. That is what makes this safe to apply
   * unconditionally: a caller passing plain input cannot be affected at all, so
   * no operation and no writer needs to know whether the arc-awareness shim ran.
   */
  public static Geometry linearize(Geometry g, double tolerance) {
    if (g == null || g.isEmpty()) return g;
    if (g instanceof Linearizable) return ((Linearizable) g).toLinear(tolerance);
    if (g instanceof GeometryCollection) {
      int n = g.getNumGeometries();
      Geometry[] members = new Geometry[n];
      boolean changed = false;
      for (int i = 0; i < n; i++) {
        Geometry member = g.getGeometryN(i);
        members[i] = linearize(member, tolerance);
        changed |= members[i] != member;
      }
      if (!changed) return g;
      return rebuild((GeometryCollection) g, members);
    }
    return g;
  }

  /**
   * Rebuilds a collection whose members have been linearised, keeping the
   * collection's own type.
   * <p>
   * Rebuilding unconditionally with {@code createGeometryCollection} was a
   * defect: it downgraded {@code MULTIPOLYGON} and {@code MULTILINESTRING} even
   * when no member was curved, and {@code ConcaveHullOfPolygons} then rejected
   * the result with "Input must be polygonal". Only reached when a member
   * actually changed, so a curve-free collection never gets here.
   * <p>
   * The member-type checks are not redundant with the collection type: a
   * {@code CurveGeometryFactory} can put a {@code CircularString} inside a plain
   * {@code MULTILINESTRING}, and linearising it yields a {@code LineString}
   * again, but nothing guarantees that for every future curve type. If a member
   * no longer fits its collection, a GeometryCollection is the honest answer.
   * {@code MULTIPOINT} needs no branch: a point is never a curve, so a MultiPoint
   * can never report a change and never reaches this method.
   */
  private static Geometry rebuild(GeometryCollection original, Geometry[] members) {
    GeometryFactory f = original.getFactory();
    if (original instanceof MultiPolygon && allInstanceOf(members, Polygon.class)) {
      Polygon[] polygons = new Polygon[members.length];
      System.arraycopy(members, 0, polygons, 0, members.length);
      return f.createMultiPolygon(polygons);
    }
    if (original instanceof MultiLineString && allInstanceOf(members, LineString.class)) {
      LineString[] lines = new LineString[members.length];
      System.arraycopy(members, 0, lines, 0, members.length);
      return f.createMultiLineString(lines);
    }
    return f.createGeometryCollection(members);
  }

  private static boolean allInstanceOf(Geometry[] members, Class<?> type) {
    for (int i = 0; i < members.length; i++) {
      if (!type.isInstance(members[i])) return false;
    }
    return true;
  }

  // -- Structural ring access, for operations that need not sample the arc ----
  //
  // Linearising is the remedy when the consumer cannot represent a curve: the
  // core writers, the triangulation hulls, the segment extractors. It is the
  // wrong remedy for a purely structural operation whose result type is itself a
  // curve type -- assembling a polygon from rings evaluates nothing, so it need
  // approximate nothing. Densification is the fallback of last resort, not of
  // choice; these helpers are how a caller avoids it.

  /**
   * The structural exterior ring of a polygonal geometry -- the arc itself for a
   * {@link CurvePolygon}, rather than the flat control-point view
   * {@code getExteriorRing()} returns. A bare {@link LineString} is its own ring,
   * which is how a closed {@code CircularString} can serve as one.
   *
   * @return the ring, or {@code null} if the geometry has none
   */
  public static LineString structuralShell(Geometry g) {
    if (g instanceof CurvePolygon) return ((CurvePolygon) g).getExteriorCurve();
    if (g instanceof Polygon) return ((Polygon) g).getExteriorRing();
    if (g instanceof LineString) return (LineString) g;
    return null;
  }

  /** The structural interior rings, the counterpart to {@link #structuralShell}. */
  public static LineString[] structuralHoles(Geometry g) {
    if (g instanceof CurvePolygon) {
      CurvePolygon cp = (CurvePolygon) g;
      LineString[] holes = new LineString[cp.getNumInteriorRing()];
      for (int i = 0; i < holes.length; i++) {
        holes[i] = cp.getInteriorCurveN(i);
      }
      return holes;
    }
    if (g instanceof Polygon) {
      Polygon p = (Polygon) g;
      LineString[] holes = new LineString[p.getNumInteriorRing()];
      for (int i = 0; i < holes.length; i++) {
        holes[i] = p.getInteriorRingN(i);
      }
      return holes;
    }
    return new LineString[0];
  }

  /** True if this ring carries an arc, so a polygon built from it must be curved. */
  public static boolean isCurveRing(LineString ring) {
    return ring instanceof CircularString || ring instanceof CompoundCurve;
  }

  /**
   * Assembles a polygon from structural rings, choosing the narrowest type that
   * can hold them: a {@link CurvePolygon} when any ring is an arc, otherwise a
   * plain {@link Polygon}.
   * <p>
   * The plain branch matters as much as the curved one. A geometry that never had
   * an arc -- including an all-linear CurvePolygon, whose rings are LinearRings --
   * comes back as a plain Polygon with exactly its input vertices, so nothing that
   * did not involve a curve changes type or value.
   */
  public static Geometry buildPolygon(LineString shell, List<LineString> holes,
      GeometryFactory factory) {
    boolean anyCurve = isCurveRing(shell);
    for (int i = 0; i < holes.size() && !anyCurve; i++) {
      anyCurve = isCurveRing(holes.get(i));
    }
    if (anyCurve) {
      return new CurvePolygon(shell, holes.toArray(new LineString[holes.size()]),
          factory);
    }
    LinearRing[] linearHoles = new LinearRing[holes.size()];
    for (int i = 0; i < linearHoles.length; i++) {
      linearHoles[i] = toLinearRing(holes.get(i), factory);
    }
    return factory.createPolygon(toLinearRing(shell, factory), linearHoles);
  }

  private static LinearRing toLinearRing(LineString ring, GeometryFactory factory) {
    if (ring == null) return null;
    if (ring instanceof LinearRing) return (LinearRing) ring;
    return factory.createLinearRing(ring.getCoordinates());
  }
}

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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.CoordinateSequence;
import org.locationtech.jts.geom.CoordinateSequences;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.IntersectionMatrix;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Point;

/**
 * A connected sequence of circular arcs, where each consecutive triple of
 * control points (start, mid, end) defines one arc and the end point of one
 * arc is the start point of the next.
 * <p>
 * This is a phase-1 stand-in: the control points are stored as a single
 * {@link CoordinateSequence} (inherited via {@link LineString}) and spatial
 * operations fall through to the parent's polyline behaviour. Native
 * arc-aware algorithms are out of scope for this module today.
 */
public class CircularString extends LineString implements Linearizable {
  private static final long serialVersionUID = 1L;

  public CircularString(CoordinateSequence points, GeometryFactory factory) {
    super(points, factory);
  }

  @Override
  public Point getCentroid() {
    Coordinate c = arcLengthCentroid();
    if (c != null) {
      return getFactory().createPoint(c);
    }
    return super.getCentroid();
  }

  /**
   * Wire (arc-length) centroid of the CircularString, or {@code null}
   * if empty / too short. C-LIN (#1195).
   */
  Coordinate arcLengthCentroid() {
    CoordinateSequence seq = getCoordinateSequence();
    int n = seq.size();
    if (n < 3) {
      return null;
    }
    double sx = 0, sy = 0, total = 0;
    for (int i = 0; i + 2 < n; i += 2) {
      Coordinate a = seq.getCoordinate(i);
      Coordinate mid = seq.getCoordinate(i + 1);
      Coordinate b = seq.getCoordinate(i + 2);
      CircularArcDensifier.Circle cir = CircularArcDensifier.Circle.fromThreePoints(
          a, mid, b);
      double len = CircularArcDensifier.arcLength(a, mid, b);
      if (len <= 0.0) {
        continue;
      }
      if (cir == null) {
        sx += len * 0.5 * (a.x + b.x);
        sy += len * 0.5 * (a.y + b.y);
        total += len;
        continue;
      }
      double a0 = Math.atan2(a.y - cir.cy, a.x - cir.cx);
      double aMid = Math.atan2(mid.y - cir.cy, mid.x - cir.cx);
      double a1 = Math.atan2(b.y - cir.cy, b.x - cir.cx);
      boolean ccw = midInCcw(a0, aMid, a1);
      double sweep = ccw ? normPos(a1 - a0) : -normPos(a0 - a1);
      if (sweep == 0.0) {
        sweep = ccw ? 2 * Math.PI : -2 * Math.PI;
      }
      // ∫ r cos φ · r dφ / L  with L = r|θ|, dφ along direction of travel
      double cx;
      double cy;
      if (ccw) {
        cx = cir.cx + (cir.r / sweep) * (Math.sin(a1) - Math.sin(a0));
        cy = cir.cy + (cir.r / sweep) * (-Math.cos(a1) + Math.cos(a0));
      } else {
        cx = cir.cx + (cir.r / (-sweep)) * (Math.sin(a0) - Math.sin(a1));
        cy = cir.cy + (cir.r / (-sweep)) * (-Math.cos(a0) + Math.cos(a1));
      }
      sx += len * cx;
      sy += len * cy;
      total += len;
    }
    if (total <= 0.0) {
      return null;
    }
    return new Coordinate(sx / total, sy / total);
  }

  private static boolean midInCcw(double a0, double aMid, double a1) {
    return normPos(aMid - a0) <= normPos(a1 - a0) + 1.0e-15;
  }

  private static double normPos(double a) {
    double t = a % (2 * Math.PI);
    if (t < 0) {
      t += 2 * Math.PI;
    }
    return t;
  }

  @Override
  public String getGeometryType() {
    return "CircularString";
  }

  @Override
  protected CircularString copyInternal() {
    return new CircularString(getCoordinateSequence().copy(), getFactory());
  }

  /**
   * §3.7 — type identity is required. The inherited
   * {@code LineString.isEquivalentClass} accepts any LineString subclass,
   * which would let a plain LineString with the same 3-point coord seq
   * compare equal to a CircularString. The 3 points denote different
   * geometries (a polyline vs a circular arc through them).
   */
  @Override
  protected boolean isEquivalentClass(Geometry other) {
    return other instanceof CircularString;
  }

  /**
   * The envelope of the arc itself, not just its control points.
   * <p>
   * The inherited {@code LineString.computeEnvelopeInternal()} expands over the
   * coordinate sequence, which clips any arc sweeping past an axis extreme that
   * is not a control point. That produces an envelope smaller than the geometry
   * it bounds, which makes spatial indexes and the envelope short-circuits in
   * {@code intersects}/{@code distance} miss real hits.
   */
  @Override
  protected Envelope computeEnvelopeInternal() {
    CoordinateSequence seq = getCoordinateSequence();
    int n = seq.size();
    if (n == 0) return new Envelope();
    if (n < 3) return super.computeEnvelopeInternal();
    Envelope env = new Envelope();
    for (int i = 0; i + 2 < n; i += 2) {
      CircularArcDensifier.expandEnvelope(
          seq.getCoordinate(i), seq.getCoordinate(i + 1), seq.getCoordinate(i + 2), env);
    }
    return env;
  }

  /**
   * The true arc length, summed over the arcs formed by each overlapping
   * control-point triple.
   * <p>
   * The inherited {@code LineString.getLength()} walks the control points as
   * straight segments, which returns the inscribed chord length -- for a
   * semicircle {@code 2r*sqrt(2)} instead of {@code pi*r}, about 10% short.
   * Colinear triples contribute their straight-line distance.
   */
  @Override
  public double getLength() {
    CoordinateSequence seq = getCoordinateSequence();
    int n = seq.size();
    if (n < 3) return super.getLength();
    double total = 0.0;
    for (int i = 0; i + 2 < n; i += 2) {
      total += CircularArcDensifier.arcLength(
          seq.getCoordinate(i), seq.getCoordinate(i + 1), seq.getCoordinate(i + 2));
    }
    return total;
  }

  /**
   * Reverses the control-point sequence, staying a CircularString.
   * <p>
   * Without this the inherited {@code LineString.reverseInternal()} rebuilds a
   * plain {@link org.locationtech.jts.geom.LineString} and the arc identity is
   * lost. Reversing the control points of an arc traverses the same arc in the
   * opposite direction: the start and end swap and the interior point stays
   * interior, so the circle through them is unchanged.
   */
  @Override
  protected CircularString reverseInternal() {
    CoordinateSequence seq = getCoordinateSequence().copy();
    CoordinateSequences.reverse(seq);
    return new CircularString(seq, getFactory());
  }

  @Override
  public Geometry toLinear(double tolerance) {
    return toLinear(tolerance, Collections.<Coordinate>emptyList());
  }

  @Override
  public Geometry toLinear(double tolerance, List<Coordinate> mustInclude) {
    if (isEmpty()) {
      return getFactory().createLineString();
    }
    CoordinateSequence seq = getCoordinateSequence();
    int n = seq.size();
    if (n < 3) {
      // Degenerate: no arc structure available, return whatever points we have.
      return getFactory().createLineString(seq.copy());
    }
    List<Coordinate> include = mustInclude == null
        ? Collections.<Coordinate>emptyList() : mustInclude;

    List<Coordinate> out = new ArrayList<Coordinate>();
    for (int i = 0; i + 2 < n; i += 2) {
      Coordinate start = seq.getCoordinate(i);
      Coordinate mid   = seq.getCoordinate(i + 1);
      Coordinate end   = seq.getCoordinate(i + 2);
      // The arc's own mid control point is an anchor in its own right: the
      // caller supplied it exactly, so it must survive toLinear exactly. The
      // walk pins start and end by construction, but the mid was only ever
      // approximated -- visual QA found (0, 1) coming back as
      // (6.1e-17, 1) at one tolerance and missing entirely at others.
      List<Coordinate> anchors = new ArrayList<Coordinate>(include.size() + 1);
      anchors.addAll(include);
      anchors.add(mid);
      List<Coordinate> chord = CircularArcDensifier.densifyArc(start, mid, end, tolerance, anchors);
      // The first arc contributes its start; subsequent arcs share an
      // endpoint with the previous arc — drop the duplicate.
      int from = out.isEmpty() ? 0 : 1;
      for (int k = from; k < chord.size(); k++) {
        out.add(chord.get(k));
      }
    }
    return getFactory().createLineString(out.toArray(new Coordinate[0]));
  }

  // -- Arc-aware spatial operations (CRV-OPS) ------------------------------
  // The jts-core implementations walk getCoordinates(), which for a curve is
  // only the control points. Route them through a densified copy instead; see
  // CurveOps for the tolerance rationale and its limits.

  @Override
  public Geometry convexHull() {
    return CurveOps.convexHull(this);
  }

  @Override
  public double distance(Geometry g) {
    return CurveOps.distance(this, g);
  }

  @Override
  public boolean isWithinDistance(Geometry g, double distance) {
    return CurveOps.isWithinDistance(this, g, distance);
  }

  @Override
  public Geometry buffer(double distance) {
    return CurveOps.buffer(this, distance);
  }

  @Override
  public Geometry buffer(double distance, int quadrantSegments) {
    return CurveOps.buffer(this, distance, quadrantSegments);
  }

  @Override
  public Geometry buffer(double distance, int quadrantSegments, int endCapStyle) {
    return CurveOps.buffer(this, distance, quadrantSegments, endCapStyle);
  }

  // -- Overlay (OVL-OPS) ---------------------------------------------------
  // Densified on both sides: overlay must node the linework, and the inherited
  // implementations node the chords through the control points. See CurveOps for
  // why no tolerance returns a curve type here, and for the OverlayNG
  // TopologyException this also resolves.

  @Override
  public Geometry intersection(Geometry other) {
    return CurveOps.intersection(this, other);
  }

  @Override
  public Geometry union(Geometry other) {
    return CurveOps.union(this, other);
  }

  @Override
  public Geometry difference(Geometry other) {
    return CurveOps.difference(this, other);
  }

  @Override
  public Geometry symDifference(Geometry other) {
    return CurveOps.symDifference(this, other);
  }

  // -- Spatial predicates (CRV-REL) -----------------------------------------
  // Each predicate is overridden individually: in this core they dispatch to
  // GeometryRelate statics rather than through this.relate(g), so no single
  // override carries the family -- see CurveOps. disjoint is inherited as
  // !intersects(g), so it follows the intersects override.

  @Override
  public IntersectionMatrix relate(Geometry g) {
    return CurveOps.relate(this, g);
  }

  @Override
  public boolean relate(Geometry g, String intersectionPattern) {
    return CurveOps.relate(this, g, intersectionPattern);
  }

  @Override
  public boolean intersects(Geometry g) {
    return CurveOps.intersects(this, g);
  }

  @Override
  public boolean touches(Geometry g) {
    return CurveOps.touches(this, g);
  }

  @Override
  public boolean crosses(Geometry g) {
    return CurveOps.crosses(this, g);
  }

  @Override
  public boolean within(Geometry g) {
    return CurveOps.within(this, g);
  }

  @Override
  public boolean contains(Geometry g) {
    return CurveOps.contains(this, g);
  }

  @Override
  public boolean overlaps(Geometry g) {
    return CurveOps.overlaps(this, g);
  }

  @Override
  public boolean covers(Geometry g) {
    return CurveOps.covers(this, g);
  }

  @Override
  public boolean coveredBy(Geometry g) {
    return CurveOps.coveredBy(this, g);
  }

  @Override
  public boolean equalsTopo(Geometry g) {
    return CurveOps.equalsTopo(this, g);
  }
}

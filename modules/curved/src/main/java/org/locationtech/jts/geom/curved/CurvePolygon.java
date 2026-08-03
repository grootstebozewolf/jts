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

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.CoordinateSequence;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.LinearRing;
import org.locationtech.jts.geom.Polygon;

/**
 * A polygon whose rings may be straight, circular, or compound curves.
 *
 * <p><b>Option-A spike (F-CP / FCP-DOVE):</b> a structural shell can be
 * supplied via the {@code (LineString structuralShell, ...)} constructor;
 * the legacy {@code Polygon.getExteriorRing()} still returns a flat
 * {@link LinearRing} derived from {@code structuralShell.getCoordinates()}
 * so existing jts-core callers keep working, and curve-aware callers go
 * through {@link #getExteriorCurve()} to retrieve the structural shell.
 * This is the leaning option in {@code SPEC_F_CP.md}; the spike here is
 * the smallest implementation that lets the F-CP red tests assert against
 * a real accessor instead of a placeholder.
 */
public class CurvePolygon extends Polygon implements Linearizable {
  private static final long serialVersionUID = 1L;

  /**
   * The structural shell as supplied by the caller — may be a
   * {@link LineString}, {@link LinearRing}, {@code CircularString}, or
   * {@code CompoundCurve}. {@code null} for the legacy construction path
   * that takes a {@link LinearRing} directly.
   */
  private final LineString structuralShell;

  /**
   * The structural holes as supplied by the caller, in the same order as the
   * legacy {@link LinearRing} holes. Never {@code null} once constructed;
   * empty for a shell-only polygon.
   */
  private final LineString[] structuralHoles;

  public CurvePolygon(LinearRing shell, LinearRing[] holes, GeometryFactory factory) {
    super(shell, holes, factory);
    this.structuralShell = shell;
    this.structuralHoles = holes == null ? new LineString[0] : holes.clone();
  }

  public CurvePolygon(GeometryFactory factory) {
    super(null, null, factory);
    this.structuralShell = null;
    this.structuralHoles = new LineString[0];
  }

  /**
   * Option-A constructor: accepts a structural shell (any {@link LineString} —
   * typically a {@code CircularString} or {@code CompoundCurve}) and derives
   * the legacy {@link LinearRing} the {@link Polygon} supertype requires from
   * the shell's control points, or passes a {@link LinearRing} straight
   * through.
   */
  public CurvePolygon(LineString structuralShell, LineString[] structuralHoles,
      GeometryFactory factory) {
    super(deriveLinearShell(structuralShell, factory),
        deriveLinearHoles(structuralHoles, factory), factory);
    this.structuralShell = structuralShell;
    this.structuralHoles = structuralHoles == null
        ? new LineString[0] : structuralHoles.clone();
  }

  /**
   * Derives the legacy flat holes from the structural ones, on the same
   * control-point basis as {@link #deriveLinearShell}.
   */
  private static LinearRing[] deriveLinearHoles(LineString[] structuralHoles,
      GeometryFactory factory) {
    if (structuralHoles == null) return null;
    LinearRing[] flat = new LinearRing[structuralHoles.length];
    for (int i = 0; i < structuralHoles.length; i++) {
      flat[i] = deriveLinearShell(structuralHoles[i], factory);
    }
    return flat;
  }

  /**
   * Derives the legacy flat shell from the structural one.
   * <p>
   * Deliberately uses the shell's <em>control points</em> rather than
   * {@link Linearizable#toLinear(double) toLinear(0.0)}. Densifying here would
   * change the coordinates {@code getExteriorRing()} has always reported,
   * making Option A a behavioural break rather than a purely additive change.
   * It also breaks WKT round-tripping: densified arc coordinates are
   * irrational, the writer rounds them to decimal, and reading them back
   * yields doubles that differ in the low bits, so {@code equalsExact} at
   * tolerance 0 fails.
   * <p>
   * Choosing the tolerance for a densified legacy view is a separate open
   * question (see {@code SPEC_F_CP.md}); curve-aware callers that want the
   * true arc should use {@link #getExteriorCurve()} and densify themselves.
   */
  private static LinearRing deriveLinearShell(LineString structuralShell, GeometryFactory factory) {
    if (structuralShell == null) return null;
    if (structuralShell instanceof LinearRing) return (LinearRing) structuralShell;
    return factory.createLinearRing(structuralShell.getCoordinates());
  }

  /**
   * Option-A structural accessor: returns the structural shell the caller
   * supplied (may be a {@code CompoundCurve}, {@code CircularString},
   * {@link LinearRing}, or plain {@link LineString}). Returns {@code null}
   * for an empty CurvePolygon.
   */
  public LineString getExteriorCurve() {
    return structuralShell;
  }

  /**
   * Option-A structural accessor for interior rings, the counterpart to
   * {@link #getExteriorCurve()}. Returns the hole as supplied by the caller,
   * which may be a {@code CircularString}, {@code CompoundCurve},
   * {@link LinearRing}, or plain {@link LineString}.
   *
   * @param n the hole index, as used by {@link #getInteriorRingN(int)}
   */
  public LineString getInteriorCurveN(int n) {
    return structuralHoles[n];
  }

  @Override
  public String getGeometryType() {
    return "CurvePolygon";
  }

  @Override
  protected CurvePolygon copyInternal() {
    GeometryFactory f = getFactory();
    if (isEmpty()) return new CurvePolygon(f);
    int holeCount = getNumInteriorRing();
    boolean anyCurved = structuralShell != null && !(structuralShell instanceof LinearRing);
    for (int i = 0; i < structuralHoles.length && !anyCurved; i++) {
      anyCurved = !(structuralHoles[i] instanceof LinearRing);
    }
    if (anyCurved) {
      LineString shellCopy = structuralShell == null
          ? null : (LineString) structuralShell.copy();
      LineString[] holeCopies = new LineString[structuralHoles.length];
      for (int i = 0; i < structuralHoles.length; i++) {
        holeCopies[i] = (LineString) structuralHoles[i].copy();
      }
      return new CurvePolygon(shellCopy, holeCopies, f);
    }
    LinearRing[] holes = new LinearRing[holeCount];
    for (int i = 0; i < holeCount; i++) {
      holes[i] = (LinearRing) getInteriorRingN(i).copy();
    }
    LinearRing shell = (LinearRing) getExteriorRing().copy();
    return new CurvePolygon(shell, holes, f);
  }

  /**
   * The area enclosed by the structural rings, so arc rings contribute the
   * area they actually sweep rather than the polygon through their control
   * points.
   * <p>
   * The inherited {@code Polygon.getArea()} shoelaces the flat
   * {@code getExteriorRing()} view, which for a circle expressed as two
   * semicircular arcs is the inscribed quadrilateral -- 8 instead of
   * {@code 4*pi} for radius 2.
   * <p>
   * Follows {@code Polygon} semantics: the magnitude of the shell's area less
   * the magnitude of each hole's, so the result is independent of ring
   * orientation.
   */
  @Override
  public double getArea() {
    if (isEmpty()) return 0.0;
    double area = Math.abs(signedRingArea(structuralShell));
    for (int i = 0; i < structuralHoles.length; i++) {
      area -= Math.abs(signedRingArea(structuralHoles[i]));
    }
    return area;
  }

  /**
   * Signed area enclosed by one ring, via the contour integral
   * {@code 1/2 * integral(x dy - y dx)}. Arc pieces use the closed-form arc
   * term; straight pieces use the shoelace term. A CompoundCurve ring is the
   * sum over its members, which together close the loop.
   */
  private static double signedRingArea(LineString ring) {
    if (ring == null) return 0.0;
    if (ring instanceof CompoundCurve) {
      CompoundCurve cc = (CompoundCurve) ring;
      double area = 0.0;
      for (int i = 0; i < cc.getNumMembers(); i++) {
        area += signedRingArea(cc.getMemberN(i));
      }
      return area;
    }
    CoordinateSequence seq = ring.getCoordinateSequence();
    if (ring instanceof CircularString && seq.size() >= 3) {
      double area = 0.0;
      for (int i = 0; i + 2 < seq.size(); i += 2) {
        area += CircularArcDensifier.arcAreaContribution(
            seq.getCoordinate(i), seq.getCoordinate(i + 1), seq.getCoordinate(i + 2));
      }
      return area;
    }
    return shoelace(seq);
  }

  /**
   * Shoelace contour term over consecutive vertices. Over a closed ring this is
   * the signed area; over an open piece it is that piece's contribution to the
   * enclosing contour.
   */
  private static double shoelace(CoordinateSequence seq) {
    double sum = 0.0;
    for (int i = 0; i + 1 < seq.size(); i++) {
      Coordinate p = seq.getCoordinate(i);
      Coordinate q = seq.getCoordinate(i + 1);
      sum += p.x * q.y - q.x * p.y;
    }
    return 0.5 * sum;
  }

  /**
   * Linearises every structural ring at the given tolerance, so an arc ring
   * becomes a densified polyline rather than the chord through its control
   * points. Delegates per ring to {@link Linearizable#toLinear(double)},
   * which is where the arc geometry and the tolerance semantics live.
   *
   * @param tolerance maximum chord error; {@code 0} selects the densifier's
   *                  default (see {@code CircularArcDensifier})
   * @return a plain {@link Polygon}; curve identity is deliberately dropped
   */
  @Override
  public Geometry toLinear(double tolerance) {
    GeometryFactory f = getFactory();
    if (isEmpty()) return f.createPolygon();
    LinearRing shell = linearise(structuralShell, tolerance, f);
    LinearRing[] flatHoles = new LinearRing[structuralHoles.length];
    for (int i = 0; i < structuralHoles.length; i++) {
      flatHoles[i] = linearise(structuralHoles[i], tolerance, f);
    }
    return f.createPolygon(shell, flatHoles);
  }

  /** Linearises one ring, leaving already-linear rings untouched. */
  private static LinearRing linearise(LineString ring, double tolerance,
      GeometryFactory factory) {
    if (ring instanceof LinearRing) return (LinearRing) ring;
    if (ring instanceof Linearizable) {
      LineString flat = (LineString) ((Linearizable) ring).toLinear(tolerance);
      return factory.createLinearRing(flat.getCoordinates());
    }
    return factory.createLinearRing(ring.getCoordinates());
  }

}

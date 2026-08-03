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

  public CurvePolygon(LinearRing shell, LinearRing[] holes, GeometryFactory factory) {
    super(shell, holes, factory);
    this.structuralShell = shell;
  }

  public CurvePolygon(GeometryFactory factory) {
    super(null, null, factory);
    this.structuralShell = null;
  }

  /**
   * Option-A constructor: accepts a structural shell (any {@link LineString} —
   * typically a {@code CircularString} or {@code CompoundCurve}) and derives
   * the legacy {@link LinearRing} the {@link Polygon} supertype requires from
   * the shell's control points, or passes a {@link LinearRing} straight
   * through.
   */
  public CurvePolygon(LineString structuralShell, LinearRing[] holes, GeometryFactory factory) {
    super(deriveLinearShell(structuralShell, factory), holes, factory);
    this.structuralShell = structuralShell;
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

  @Override
  public String getGeometryType() {
    return "CurvePolygon";
  }

  @Override
  protected CurvePolygon copyInternal() {
    GeometryFactory f = getFactory();
    if (isEmpty()) return new CurvePolygon(f);
    int holeCount = getNumInteriorRing();
    LinearRing[] holes = new LinearRing[holeCount];
    for (int i = 0; i < holeCount; i++) {
      holes[i] = (LinearRing) getInteriorRingN(i).copy();
    }
    if (structuralShell != null && !(structuralShell instanceof LinearRing)) {
      LineString shellCopy = (LineString) structuralShell.copy();
      return new CurvePolygon(shellCopy, holes, f);
    }
    LinearRing shell = (LinearRing) getExteriorRing().copy();
    return new CurvePolygon(shell, holes, f);
  }

  @Override
  public Geometry toLinear(double tolerance) {
    GeometryFactory f = getFactory();
    if (isEmpty()) return f.createPolygon();
    LinearRing shell = (LinearRing) getExteriorRing().copy();
    int holeCount = getNumInteriorRing();
    LinearRing[] holes = new LinearRing[holeCount];
    for (int i = 0; i < holeCount; i++) {
      holes[i] = (LinearRing) getInteriorRingN(i).copy();
    }
    return f.createPolygon(shell, holes);
  }
}

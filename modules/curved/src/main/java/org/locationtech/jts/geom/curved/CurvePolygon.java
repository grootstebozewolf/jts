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
 * Phase-1 stand-in: rings are linearised to {@link LinearRing}s on read so
 * the existing polygon algorithm suite keeps working, but the original
 * curved rings are retained alongside them so arc-aware operations can use
 * the exact geometry. Today {@link #getArea()} is curve-aware; other
 * operations still fall through to the linearised parent.
 */
public class CurvePolygon extends Polygon implements Linearizable {
  private static final long serialVersionUID = 1L;

  /**
   * The original curved rings, index 0 the shell and the rest the holes,
   * parallel to the linearised rings held by the parent {@link Polygon}.
   * {@code null} when the polygon was built without curve information, in
   * which case it behaves exactly like a flat {@link Polygon}.
   */
  private final LineString[] curvedRings;

  public CurvePolygon(LinearRing shell, LinearRing[] holes, GeometryFactory factory) {
    super(shell, holes, factory);
    this.curvedRings = null;
  }

  /**
   * Constructs a CurvePolygon retaining the original curved rings.
   *
   * @param shell the linearised exterior ring (for parent-polygon behaviour)
   * @param holes the linearised interior rings
   * @param curvedRings the original curved rings, index 0 the shell and the
   *        remaining entries the holes in the same order as {@code holes};
   *        {@code null} to behave as a flat polygon
   * @param factory the geometry factory
   */
  public CurvePolygon(LinearRing shell, LinearRing[] holes, LineString[] curvedRings,
                      GeometryFactory factory) {
    super(shell, holes, factory);
    this.curvedRings = curvedRings == null ? null : curvedRings.clone();
  }

  public CurvePolygon(GeometryFactory factory) {
    super(null, null, factory);
    this.curvedRings = null;
  }

  /**
   * The original (possibly curved) exterior ring &mdash; a
   * {@link CircularString}, {@link CompoundCurve} or straight
   * {@link LineString} &mdash; or {@code null} when no curve information was
   * retained.
   */
  public LineString getExteriorRingCurve() {
    return (curvedRings == null || curvedRings.length == 0) ? null : curvedRings[0];
  }

  /**
   * Area of the curve-bounded polygon. When curved rings are retained the
   * area is computed analytically with the circular-segment correction
   * (so a disk expressed as arcs returns {@code &pi;R&sup2;}); otherwise it
   * falls back to the flat parent-polygon area.
   */
  @Override
  /**
   * @see #getArea() M-AREA-CP analytical implementation in CurvedArea (epic #1195).
   */
  public double getArea() {
    if (curvedRings == null || curvedRings.length == 0) {
      return super.getArea();
    }
    LineString shell = curvedRings[0];
    LineString[] holes = new LineString[curvedRings.length - 1];
    System.arraycopy(curvedRings, 1, holes, 0, holes.length);
    return CurvedArea.ofCurvePolygon(shell, holes);
  }

  @Override
  public String getGeometryType() {
    return "CurvePolygon";
  }

  /**
   * Explicit dimension guard for M-DIM (epic #1195).
   * Ensures empty (and non-empty) CurvePolygon report dimension 2 even if
   * parent Polygon semantics change in a refactor.
   */
  @Override
  public int getDimension() {
    return 2;
  }

  @Override
  protected CurvePolygon copyInternal() {
    GeometryFactory f = getFactory();
    if (isEmpty()) return new CurvePolygon(f);
    LinearRing shell = (LinearRing) getExteriorRing().copy();
    int holeCount = getNumInteriorRing();
    LinearRing[] holes = new LinearRing[holeCount];
    for (int i = 0; i < holeCount; i++) {
      holes[i] = (LinearRing) getInteriorRingN(i).copy();
    }
    if (curvedRings == null) {
      return new CurvePolygon(shell, holes, f);
    }
    LineString[] curvedCopy = new LineString[curvedRings.length];
    for (int i = 0; i < curvedRings.length; i++) {
      curvedCopy[i] = (LineString) curvedRings[i].copy();
    }
    return new CurvePolygon(shell, holes, curvedCopy, f);
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

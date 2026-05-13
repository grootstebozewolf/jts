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
 * <p><b>Option-C spike (F-CP / FCP-DOVE):</b> {@code getExteriorRing()}
 * and {@code getInteriorRingN(int)} throw {@link UnsupportedOperationException}
 * on a CurvePolygon. Curve-aware callers go through
 * {@link #getExteriorCurve()} and {@link #getInteriorCurveN(int)}; legacy
 * Polygon API callers get a loud, named failure rather than a silent
 * polyline approximation. The structural shell is stored in this class;
 * Polygon's internal {@code shell} field still holds a linearised
 * placeholder so the parent class's coordinate / area / length methods
 * keep working (they access {@code shell} via the field, not the
 * method).
 */
public class CurvePolygon extends Polygon implements Linearizable {
  private static final long serialVersionUID = 1L;

  private final LineString structuralShell;

  public CurvePolygon(LinearRing shell, LinearRing[] holes, GeometryFactory factory) {
    super(shell, holes, factory);
    this.structuralShell = shell;
  }

  public CurvePolygon(GeometryFactory factory) {
    super(null, null, factory);
    this.structuralShell = null;
  }

  /** Option-C structural accessor. */
  public LineString getExteriorCurve() {
    return structuralShell;
  }

  /** Option-C: fail-fast. The Polygon-API accessor is no longer the way
   *  to reach the shell on a CurvePolygon. */
  @Override
  public LinearRing getExteriorRing() {
    throw new UnsupportedOperationException(
        "Polygon.getExteriorRing() is not supported on a CurvePolygon (Option C). "
        + "Use CurvePolygon.getExteriorCurve() to retrieve the structural shell.");
  }

  /** Option-C: same fail-fast contract for interior rings. */
  @Override
  public LinearRing getInteriorRingN(int n) {
    throw new UnsupportedOperationException(
        "Polygon.getInteriorRingN(int) is not supported on a CurvePolygon (Option C). "
        + "Use CurvePolygon.getInteriorCurveN(int) to retrieve a structural hole.");
  }

  /** Option-C structural-hole accessor. Today's Phase-1 reader still produces
   *  LinearRing holes, so this returns the inherited Polygon hole through
   *  field access -- but the public name signals the curve-aware contract. */
  public LineString getInteriorCurveN(int n) {
    return super.getInteriorRingN(n);
  }

  @Override
  public String getGeometryType() {
    return "CurvePolygon";
  }

  @Override
  protected CurvePolygon copyInternal() {
    GeometryFactory f = getFactory();
    if (isEmpty()) return new CurvePolygon(f);
    // Option-C: bypass our throwing overrides via super-calls so internal
    // structural operations don't trip the public fail-fast contract.
    LinearRing shell = (LinearRing) super.getExteriorRing().copy();
    int holeCount = getNumInteriorRing();
    LinearRing[] holes = new LinearRing[holeCount];
    for (int i = 0; i < holeCount; i++) {
      holes[i] = (LinearRing) super.getInteriorRingN(i).copy();
    }
    return new CurvePolygon(shell, holes, f);
  }

  @Override
  public Geometry toLinear(double tolerance) {
    GeometryFactory f = getFactory();
    if (isEmpty()) return f.createPolygon();
    LinearRing shell = (LinearRing) super.getExteriorRing().copy();
    int holeCount = getNumInteriorRing();
    LinearRing[] holes = new LinearRing[holeCount];
    for (int i = 0; i < holeCount; i++) {
      holes[i] = (LinearRing) super.getInteriorRingN(i).copy();
    }
    return f.createPolygon(shell, holes);
  }
}

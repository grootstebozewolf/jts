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
  public String getGeometryType() {
    return "CircularString";
  }

  /**
   * M-DIM guard: empty curved lineals must report dimension 1.
   * <p>
   * Explicit override (in addition to inherited from LineString) so that
   * refactors to empty construction or future changes cannot regress the
   * empty-curve dimension semantics required by the spec. See
   * CurveAwarenessSpecTest#test_M_DIM_* (and its ship commit).
   */
  @Override
  public int getDimension() {
    return 1;
  }

  /**
   * M-LEN-CS: analytical arc length for CircularString (sum of r*theta for arcs).
   * Uses CircularArcs.arcLength on consecutive control triples.
   * Phase-1: for multi-arc CS (odd num points >=3), steps by 2.
   */
  @Override
  public double getLength() {
    CoordinateSequence pts = getCoordinateSequence();
    int n = pts.size();
    if (n < 2) return 0.0;
    double len = 0.0;
    for (int i = 0; i + 2 < n; i += 2) {
      len += CircularArcs.arcLength(
          pts.getCoordinate(i),
          pts.getCoordinate(i + 1),
          pts.getCoordinate(i + 2)
      );
    }
    return len;
  }

  @Override
  public Point getCentroid() {
    if (isEmpty()) {
      return getFactory().createPoint();
    }
    CoordinateSequence pts = getCoordinateSequence();
    int n = pts.size();
    if (n < 2) {
      return getFactory().createPoint(pts.getCoordinate(0));
    }
    double totalLen = getLength();
    if (totalLen == 0) {
      return getFactory().createPoint(pts.getCoordinate(0));
    }
    double sumX = 0, sumY = 0;
    for (int i = 0; i + 2 < n; i += 2) {
      Coordinate a = pts.getCoordinate(i);
      Coordinate b = pts.getCoordinate(i + 1);
      Coordinate c = pts.getCoordinate(i + 2);
      double alen = CircularArcs.arcLength(a, b, c);
      Coordinate ac = CircularArcs.arcCentroid(a, b, c);
      sumX += alen * ac.x;
      sumY += alen * ac.y;
    }
    double cx = sumX / totalLen;
    double cy = sumY / totalLen;
    return getFactory().createPoint(new Coordinate(cx, cy));
  }

  /**
   * Returns the boundary of this CircularString.
   *
   * <p>B-CC / lineal boundary (low risk/cost RGR): semantics identical to
   * LineString (open → 2-pt MultiPoint of control endpoints; closed → empty).
   * Delegates to super for the same reason as CompoundCurve: explicit guard
   * point for future changes and to document that arc interpolation does not
   * affect the boundary (only the two extreme control points matter).
   */
  @Override
  public Geometry getBoundary() {
    return super.getBoundary();
  }

  @Override
  protected CircularString copyInternal() {
    return new CircularString(getCoordinateSequence().copy(), getFactory());
  }

  @Override
  public Geometry toLinear(double tolerance) {
    return getFactory().createLineString(getCoordinateSequence().copy());
  }
}

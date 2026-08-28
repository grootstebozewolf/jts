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
import java.util.Arrays;
import java.util.List;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.CoordinateSequence;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;

/**
 * Preview NURBS: a NURBS curve of given degree with control
 * points, weights, and knot vector. HOLD JTS I/O 21 — not SIGNED I/O.
 * <p>
 * Evaluation uses Cox–de Boor; {@link #toLinear(double)} samples in
 * parameter space. Not a silent flatten of the control polygon.
 */
public class NurbsCurve extends LineString implements Linearizable {
  private static final long serialVersionUID = 1L;

  private final int degree;
  private final double[] weights;
  private final double[] knots;

  public NurbsCurve(CoordinateSequence controls, int degree, double[] weights,
      double[] knots, GeometryFactory factory) {
    super(controls, factory);
    int n = controls != null ? controls.size() : 0;
    if (n == 0) {
      this.degree = 0;
      this.weights = new double[0];
      this.knots = new double[0];
      return;
    }
    if (degree < 1 || degree >= n) {
      throw new IllegalArgumentException(
          "NURBS degree must satisfy 1 <= p < nControls (p=" + degree
              + ", n=" + n + ")");
    }
    if (weights == null || weights.length != n) {
      throw new IllegalArgumentException(
          "NURBS weights length must equal control count");
    }
    int expectedKnots = n + degree + 1;
    if (knots == null || knots.length != expectedKnots) {
      throw new IllegalArgumentException(
          "NURBS knot count must be n+p+1 (expected " + expectedKnots
              + ", got " + (knots == null ? 0 : knots.length) + ")");
    }
    this.degree = degree;
    this.weights = Arrays.copyOf(weights, weights.length);
    this.knots = Arrays.copyOf(knots, knots.length);
  }

  public int getDegree() { return degree; }
  public double[] getWeights() { return Arrays.copyOf(weights, weights.length); }
  public double[] getKnots() { return Arrays.copyOf(knots, knots.length); }

  @Override
  public String getGeometryType() {
    return "NurbsCurve";
  }

  @Override
  protected NurbsCurve copyInternal() {
    return new NurbsCurve(getCoordinateSequence().copy(), degree,
        weights, knots, getFactory());
  }

  @Override
  protected boolean isEquivalentClass(Geometry other) {
    return other instanceof NurbsCurve;
  }

  @Override
  public Geometry toLinear(double tolerance) {
    if (isEmpty()) {
      return getFactory().createLineString();
    }
    double u0 = knots[degree];
    double u1 = knots[knots.length - degree - 1];
    int n = samples(tolerance);
    List<Coordinate> pts = new ArrayList<Coordinate>(n + 1);
    for (int i = 0; i <= n; i++) {
      double u = u0 + (u1 - u0) * ((double) i / n);
      pts.add(evaluate(u));
    }
    return getFactory().createLineString(pts.toArray(new Coordinate[0]));
  }

  Coordinate evaluate(double u) {
    CoordinateSequence seq = getCoordinateSequence();
    int n = seq.size();
    double wx = 0, wy = 0, wz = 0, wsum = 0;
    boolean hasZ = false;
    for (int i = 0; i < n; i++) {
      double bi = basis(i, degree, u);
      if (bi == 0.0) {
        continue;
      }
      double w = weights[i] * bi;
      Coordinate p = seq.getCoordinate(i);
      wx += w * p.x;
      wy += w * p.y;
      if (!Double.isNaN(p.getZ())) {
        hasZ = true;
        wz += w * p.getZ();
      }
      wsum += w;
    }
    if (wsum == 0.0) {
      return new Coordinate(seq.getCoordinate(0));
    }
    Coordinate c = new Coordinate(wx / wsum, wy / wsum);
    if (hasZ) {
      c.setZ(wz / wsum);
    }
    return c;
  }

  /** Cox–de Boor basis. */
  double basis(int i, int p, double u) {
    if (p == 0) {
      boolean last = (i == knots.length - 2);
      if (u >= knots[i] && (u < knots[i + 1] || (last && u <= knots[i + 1]))) {
        return 1.0;
      }
      return 0.0;
    }
    double d1 = knots[i + p] - knots[i];
    double d2 = knots[i + p + 1] - knots[i + 1];
    double a = 0.0;
    double b = 0.0;
    if (d1 != 0.0) {
      a = (u - knots[i]) / d1 * basis(i, p - 1, u);
    }
    if (d2 != 0.0) {
      b = (knots[i + p + 1] - u) / d2 * basis(i + 1, p - 1, u);
    }
    return a + b;
  }

  private int samples(double tolerance) {
    if (!(tolerance > 0.0)) {
      return 32;
    }
    int n = (int) Math.ceil(1.0 / Math.sqrt(Math.max(tolerance, 1.0e-12)));
    if (n < 8) {
      return 8;
    }
    if (n > 128) {
      return 128;
    }
    return n;
  }
}

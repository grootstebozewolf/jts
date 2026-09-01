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
import java.util.List;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.CoordinateSequence;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;

/**
 * Named Bézier fallback: a cubic Bézier curve (or chain of cubics)
 * defined by control points. A single cubic uses 4 controls; a
 * chain of {@code k} cubics uses {@code 3k+1} controls (C0 join).
 * <p>
 * Not type 19. HOLD type 19 — not SIGNED I/O. Not
 * {@code shape.CubicBezierCurve} (polyline smoother) and not a
 * canvas draw path.
 */
public class BezierCurve extends LineString implements Linearizable {
  private static final long serialVersionUID = 1L;

  public BezierCurve(CoordinateSequence points, GeometryFactory factory) {
    super(points, factory);
    int n = points != null ? points.size() : 0;
    if (n > 0 && (n < 4 || (n - 1) % 3 != 0)) {
      throw new IllegalArgumentException(
          "BEZIER control count must be 3k+1 with k>=1 (got " + n + ")");
    }
  }

  @Override
  public String getGeometryType() {
    return "BezierCurve";
  }

  @Override
  protected BezierCurve copyInternal() {
    return new BezierCurve(getCoordinateSequence().copy(), getFactory());
  }

  @Override
  protected boolean isEquivalentClass(Geometry other) {
    return other instanceof BezierCurve;
  }

  @Override
  public Geometry toLinear(double tolerance) {
    if (isEmpty()) {
      return getFactory().createLineString();
    }
    CoordinateSequence seq = getCoordinateSequence();
    int n = seq.size();
    int segs = (n - 1) / 3;
    int samplesPer = samplesPerCubic(tolerance);
    List<Coordinate> out = new ArrayList<Coordinate>(segs * samplesPer + 1);
    for (int s = 0; s < segs; s++) {
      int i = 3 * s;
      Coordinate p0 = seq.getCoordinate(i);
      Coordinate p1 = seq.getCoordinate(i + 1);
      Coordinate p2 = seq.getCoordinate(i + 2);
      Coordinate p3 = seq.getCoordinate(i + 3);
      int from = (s == 0) ? 0 : 1;
      for (int t = from; t <= samplesPer; t++) {
        double u = (double) t / samplesPer;
        out.add(evalCubic(p0, p1, p2, p3, u));
      }
    }
    return getFactory().createLineString(out.toArray(new Coordinate[0]));
  }

  @Override
  public Envelope getEnvelopeInternal() {
    // Control hull contains the curve; densify expands if needed later.
    return super.getEnvelopeInternal();
  }

  private static int samplesPerCubic(double tolerance) {
    if (!(tolerance > 0.0)) {
      return 16;
    }
    int n = (int) Math.ceil(1.0 / Math.sqrt(Math.max(tolerance, 1.0e-12)));
    if (n < 4) {
      return 4;
    }
    if (n > 64) {
      return 64;
    }
    return n;
  }

  /** Bernstein cubic at parameter {@code u} in [0,1]. */
  static Coordinate evalCubic(Coordinate p0, Coordinate p1, Coordinate p2,
      Coordinate p3, double u) {
    double o = 1.0 - u;
    double o2 = o * o;
    double o3 = o2 * o;
    double u2 = u * u;
    double u3 = u2 * u;
    double x = o3 * p0.x + 3 * o2 * u * p1.x + 3 * o * u2 * p2.x + u3 * p3.x;
    double y = o3 * p0.y + 3 * o2 * u * p1.y + 3 * o * u2 * p2.y + u3 * p3.y;
    Coordinate c = new Coordinate(x, y);
    if (!Double.isNaN(p0.getZ()) || !Double.isNaN(p3.getZ())) {
      double z0 = Double.isNaN(p0.getZ()) ? 0.0 : p0.getZ();
      double z1 = Double.isNaN(p1.getZ()) ? z0 : p1.getZ();
      double z2 = Double.isNaN(p2.getZ()) ? z0 : p2.getZ();
      double z3 = Double.isNaN(p3.getZ()) ? z0 : p3.getZ();
      c.setZ(o3 * z0 + 3 * o2 * u * z1 + 3 * o * u2 * z2 + u3 * z3);
    }
    return c;
  }
}

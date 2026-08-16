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

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.CoordinateSequence;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;

/**
 * A CLOTHOID (Euler / Cornu spiral) segment per
 * antlr/grammars-v4 WKT proposal #4847 / PR #4848.
 *
 * <p>WKT form is {@code CLOTHOID ( startKappa , endKappa , length )}
 * as a <em>non-leading</em> {@link CompoundCurve} member only. The
 * segment inherits start point, tangent and curvature from the
 * previous member. The parent {@link LineString} coordinate sequence
 * is start+end only (§3.6); the interior is recovered via
 * {@link #toLinear(double)}.
 *
 * <p>{@code κ(s) = κ₀ + (κ₁ − κ₀)·(s/L)}. Positive κ is CCW in XY-up.
 * {@code length > 0} and {@code startKappa != endKappa}; the
 * degenerate case is a line or {@code CIRCULARSTRING}.
 */
public class ClothoidSegment extends LineString implements Linearizable {
  private static final long serialVersionUID = 1L;

  private final Coordinate startPoint;
  private final double startTangent;
  private final double startKappa;
  private final double endKappa;
  private final double length;

  private final Coordinate endPoint;
  private final double endTangent;

  public ClothoidSegment(Coordinate startPoint, double startTangent,
                         double startKappa, double endKappa, double length,
                         GeometryFactory factory) {
    this(startPoint, startTangent, startKappa, endKappa, length,
         integrateEndpoint(startPoint, startTangent, startKappa, endKappa, length),
         factory);
  }

  private ClothoidSegment(Coordinate startPoint, double startTangent,
                          double startKappa, double endKappa, double length,
                          double[] endXYTheta, GeometryFactory factory) {
    super(twoPointSeq(startPoint, new Coordinate(endXYTheta[0], endXYTheta[1]), factory),
          factory);
    if (length <= 0 || Double.isNaN(length) || Double.isInfinite(length)) {
      throw new IllegalArgumentException("CLOTHOID length must be finite > 0, got " + length);
    }
    if (Math.abs(endKappa - startKappa) < 1e-15) {
      throw new IllegalArgumentException(
          "CLOTHOID requires startKappa != endKappa (got " + startKappa + ", " + endKappa
          + "); use CIRCULARSTRING or LINESTRING for the degenerate case.");
    }
    this.startPoint = new Coordinate(startPoint);
    this.startTangent = startTangent;
    this.startKappa = startKappa;
    this.endKappa = endKappa;
    this.length = length;
    this.endPoint = new Coordinate(endXYTheta[0], endXYTheta[1]);
    this.endTangent = endXYTheta[2];
  }

  /**
   * Builds a clothoid from the IFC / LandXML constant
   * {@code A = √(L / |κ₁ − κ₀|)}. The sign of {@code A} is the sign of
   * {@code κ₁ − κ₀}.
   */
  public static ClothoidSegment fromAandLength(Coordinate startPoint,
      double startTangent, double startKappa, double A, double length,
      GeometryFactory factory) {
    if (A == 0.0 || Double.isNaN(A) || Double.isInfinite(A)) {
      throw new IllegalArgumentException("clothoid A must be finite and non-zero");
    }
    double dK = length / (A * A);
    if (A < 0) {
      dK = -dK;
    }
    return new ClothoidSegment(startPoint, startTangent, startKappa,
        startKappa + dK, length, factory);
  }

  public Coordinate getStartCoordinate() { return new Coordinate(startPoint); }
  public Coordinate getEndCoordinate()   { return new Coordinate(endPoint); }
  public double getStartTangent()        { return startTangent; }
  public double getEndTangent()          { return endTangent; }
  public double getStartKappa()          { return startKappa; }
  public double getEndKappa()            { return endKappa; }

  @Override
  public double getLength()            { return length; }

  /** {@code A = √(L / |κ₁ − κ₀|)}. */
  public double getClothoidConstantA() {
    return Math.sqrt(length / Math.abs(endKappa - startKappa));
  }

  @Override
  public String getGeometryType() {
    return "ClothoidSegment";
  }

  @Override
  protected ClothoidSegment copyInternal() {
    return new ClothoidSegment(new Coordinate(startPoint), startTangent,
        startKappa, endKappa, length,
        new double[] { endPoint.x, endPoint.y, endTangent }, getFactory());
  }

  @Override
  protected boolean isEquivalentClass(Geometry other) {
    return other instanceof ClothoidSegment;
  }

  @Override
  public boolean equalsExact(Geometry other, double tolerance) {
    if (this == other) return true;
    if (!isEquivalentClass(other)) return false;
    ClothoidSegment o = (ClothoidSegment) other;
    return Math.abs(length      - o.length)       <= tolerance
        && Math.abs(startKappa  - o.startKappa)   <= tolerance
        && Math.abs(endKappa    - o.endKappa)     <= tolerance
        && Math.abs(startTangent - o.startTangent) <= tolerance
        && Math.abs(startPoint.x - o.startPoint.x) <= tolerance
        && Math.abs(startPoint.y - o.startPoint.y) <= tolerance;
  }

  /**
   * Reverse: {@code CLOTHOID(-k1, -k0, L)} at the old end, tangent + π.
   */
  @Override
  protected ClothoidSegment reverseInternal() {
    Coordinate newStart = new Coordinate(endPoint);
    double newTangent = normaliseAngle(endTangent + Math.PI);
    return new ClothoidSegment(newStart, newTangent,
        -endKappa, -startKappa, length, getFactory());
  }

  @Override
  public Geometry toLinear(double tolerance) {
    if (tolerance < 0) {
      throw new IllegalArgumentException("tolerance must not be negative");
    }
    if (tolerance <= 0) tolerance = 1e-3;
    double maxKappa = Math.max(Math.abs(startKappa), Math.abs(endKappa));
    int n;
    if (maxKappa < 1e-12) {
      n = 1;
    }
    else {
      double err = Math.max(tolerance, 1e-9);
      n = (int) Math.ceil(length * Math.sqrt(maxKappa / (8.0 * err)));
      n = Math.max(16, Math.min(8192, n));
    }
    Coordinate[] pts = densifyByN(startPoint, startTangent, startKappa, endKappa, length, n);
    pts[pts.length - 1] = new Coordinate(endPoint);
    return getFactory().createLineString(pts);
  }

  @Override
  protected Envelope computeEnvelopeInternal() {
    if (isEmpty()) return new Envelope();
    Envelope env = new Envelope(startPoint);
    env.expandToInclude(endPoint.x, endPoint.y);
    expandByExtremes(env, Math.PI / 2.0);
    expandByExtremes(env, 0.0);
    return env;
  }

  /**
   * End curvature inherited by a following clothoid: 0 after a line,
   * signed 1/R after a circular arc, {@code endKappa} after a clothoid.
   */
  public static double endKappaOf(LineString member) {
    if (member instanceof ClothoidSegment) {
      return ((ClothoidSegment) member).getEndKappa();
    }
    if (member instanceof CircularString) {
      Coordinate[] c = member.getCoordinates();
      if (c.length < 3) return 0.0;
      return signedCurvature(c[c.length - 3], c[c.length - 2], c[c.length - 1]);
    }
    return 0.0;
  }

  /**
   * End tangent inherited by a following clothoid. CircularString uses
   * the analytical arc tangent at the last triple, not the chord.
   */
  public static double endTangentOf(LineString member) {
    if (member instanceof ClothoidSegment) {
      return ((ClothoidSegment) member).getEndTangent();
    }
    Coordinate[] c = member.getCoordinates();
    if (member instanceof CircularString && c.length >= 3) {
      return arcTangentAtEnd(c[c.length - 3], c[c.length - 2], c[c.length - 1]);
    }
    if (c.length >= 2) {
      Coordinate prev = c[c.length - 2];
      Coordinate last = c[c.length - 1];
      return Math.atan2(last.y - prev.y, last.x - prev.x);
    }
    return 0.0;
  }

  public static Coordinate endPointOf(LineString member) {
    if (member instanceof ClothoidSegment) {
      return ((ClothoidSegment) member).getEndCoordinate();
    }
    return new Coordinate(member.getCoordinateN(member.getNumPoints() - 1));
  }

  private void expandByExtremes(Envelope env, double phaseOffset) {
    double thetaMin = Math.min(startTangent, endTangent);
    double thetaMax = Math.max(startTangent, endTangent);
    int nMin = (int) Math.floor((thetaMin - phaseOffset) / Math.PI);
    int nMax = (int) Math.ceil((thetaMax - phaseOffset) / Math.PI);
    double alpha = (endKappa - startKappa) / (2.0 * length);
    for (int n = nMin; n <= nMax; n++) {
      double thetaTarget = phaseOffset + n * Math.PI;
      if (thetaTarget >= thetaMin - 1e-12 && thetaTarget <= thetaMax + 1e-12) {
        double[] roots = solveQuadratic(alpha, startKappa, startTangent - thetaTarget);
        for (int r = 0; r < roots.length; r++) {
          double s = roots[r];
          if (!Double.isNaN(s) && s >= 0 && s <= length) {
            double[] xy = integrateTo(s);
            env.expandToInclude(xy[0], xy[1]);
          }
        }
      }
    }
  }

  private double[] integrateTo(double s) {
    if (s <= 0) return new double[] { startPoint.x, startPoint.y };
    if (s >= length) return new double[] { endPoint.x, endPoint.y };
    int n = 128;
    double ds = s / n;
    double x = startPoint.x;
    double y = startPoint.y;
    for (int i = 1; i <= n; i++) {
      double sa = (i - 1) * ds;
      double sb = i * ds;
      double sm = 0.5 * (sa + sb);
      double ta = headingAt(startTangent, startKappa, endKappa, length, sa);
      double tm = headingAt(startTangent, startKappa, endKappa, length, sm);
      double tb = headingAt(startTangent, startKappa, endKappa, length, sb);
      x += (Math.cos(ta) + 4.0 * Math.cos(tm) + Math.cos(tb)) * ds / 6.0;
      y += (Math.sin(ta) + 4.0 * Math.sin(tm) + Math.sin(tb)) * ds / 6.0;
    }
    return new double[] { x, y };
  }

  private static double[] solveQuadratic(double a, double b, double c) {
    if (Math.abs(a) < 1e-15) {
      if (Math.abs(b) < 1e-15) return new double[0];
      return new double[] { -c / b };
    }
    double disc = b * b - 4.0 * a * c;
    if (disc < 0) return new double[0];
    double sq = Math.sqrt(disc);
    return new double[] { (-b + sq) / (2.0 * a), (-b - sq) / (2.0 * a) };
  }

  private static double headingAt(double startTheta, double k0, double k1,
                                  double L, double s) {
    return startTheta + k0 * s + (k1 - k0) * s * s / (2.0 * L);
  }

  private static double[] integrateEndpoint(Coordinate p0, double theta0,
                                            double k0, double k1, double L) {
    int n = 256;
    double ds = L / n;
    double x = p0.x;
    double y = p0.y;
    for (int i = 1; i <= n; i++) {
      double sa = (i - 1) * ds;
      double sb = i * ds;
      double sm = 0.5 * (sa + sb);
      double ta = headingAt(theta0, k0, k1, L, sa);
      double tm = headingAt(theta0, k0, k1, L, sm);
      double tb = headingAt(theta0, k0, k1, L, sb);
      x += (Math.cos(ta) + 4.0 * Math.cos(tm) + Math.cos(tb)) * ds / 6.0;
      y += (Math.sin(ta) + 4.0 * Math.sin(tm) + Math.sin(tb)) * ds / 6.0;
    }
    return new double[] { x, y, headingAt(theta0, k0, k1, L, L) };
  }

  private static Coordinate[] densifyByN(Coordinate p0, double theta0,
                                         double k0, double k1, double L, int n) {
    double ds = L / n;
    Coordinate[] pts = new Coordinate[n + 1];
    pts[0] = new Coordinate(p0);
    double x = p0.x;
    double y = p0.y;
    for (int i = 1; i <= n; i++) {
      double sa = (i - 1) * ds;
      double sb = i * ds;
      double sm = 0.5 * (sa + sb);
      double ta = headingAt(theta0, k0, k1, L, sa);
      double tm = headingAt(theta0, k0, k1, L, sm);
      double tb = headingAt(theta0, k0, k1, L, sb);
      x += (Math.cos(ta) + 4.0 * Math.cos(tm) + Math.cos(tb)) * ds / 6.0;
      y += (Math.sin(ta) + 4.0 * Math.sin(tm) + Math.sin(tb)) * ds / 6.0;
      pts[i] = new Coordinate(x, y);
    }
    return pts;
  }

  private static CoordinateSequence twoPointSeq(Coordinate a, Coordinate b,
                                                GeometryFactory factory) {
    return factory.getCoordinateSequenceFactory().create(new Coordinate[] {
        new Coordinate(a), new Coordinate(b)
    });
  }

  private static double normaliseAngle(double theta) {
    while (theta > Math.PI) {
      theta -= 2.0 * Math.PI;
    }
    while (theta <= -Math.PI) {
      theta += 2.0 * Math.PI;
    }
    return theta;
  }

  private static double signedCurvature(Coordinate p0, Coordinate p1, Coordinate p2) {
    double a = p0.distance(p1);
    double b = p1.distance(p2);
    double c = p0.distance(p2);
    double cross = (p1.x - p0.x) * (p2.y - p0.y) - (p1.y - p0.y) * (p2.x - p0.x);
    double fourK = Math.abs(cross);
    if (fourK < 1e-15 || a < 1e-15 || b < 1e-15 || c < 1e-15) return 0.0;
    double R = a * b * c / (2.0 * fourK);
    return (cross >= 0 ? 1.0 : -1.0) / R;
  }

  static double arcTangentAtEnd(Coordinate p0, Coordinate p1, Coordinate p2) {
    double ax = (p0.x + p1.x) * 0.5;
    double ay = (p0.y + p1.y) * 0.5;
    double bx = (p1.x + p2.x) * 0.5;
    double by = (p1.y + p2.y) * 0.5;
    double dax = p1.y - p0.y;
    double day = p0.x - p1.x;
    double dbx = p2.y - p1.y;
    double dby = p1.x - p2.x;
    double det = dax * dby - day * dbx;
    if (Math.abs(det) < 1e-12) {
      return Math.atan2(p2.y - p1.y, p2.x - p1.x);
    }
    double t = ((bx - ax) * dby - (by - ay) * dbx) / det;
    double cx = ax + t * dax;
    double cy = ay + t * day;
    double rx = p2.x - cx;
    double ry = p2.y - cy;
    double cross = (p1.x - p0.x) * (p2.y - p1.y) - (p1.y - p0.y) * (p2.x - p1.x);
    if (cross >= 0) {
      return Math.atan2(rx, -ry);
    }
    return Math.atan2(-rx, ry);
  }
}

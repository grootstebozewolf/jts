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
/*
 * AI Disclosure (Eclipse Foundation GenAI Guidelines):
 * AI-generated portions are dedicated to CC0-1.0 (public domain).
 * Human contributor reviewed and verified.
 *
 * SPDX-License-Identifier: (EPL-2.0 OR EDL-1.0) AND CC0-1.0
 * Assisted-by: xAI Grok (grok-4.3)
 * Assisted-by: Claude (Opus-4.7)
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
 * Playground implementation of a CLOTHOID (Euler / Cornu spiral) segment
 * per the proposal at <a href="https://github.com/antlr/grammars-v4/discussions/4847">grammars-v4 #4847</a>.
 *
 * <p>A clothoid has linearly varying curvature with arc length:
 * {@code κ(s) = κ₀ + (κ₁ − κ₀)·(s/L)}. Heading is the integral of
 * curvature, position is the integral of (cos(heading), sin(heading)).
 *
 * <p>This is a v1 playground — the segment is intended to live as a
 * non-leading member of a {@link CompoundCurve} and inherits its start
 * state (point, tangent, curvature) from the preceding member.
 *
 * <p>Sign convention: positive curvature is a counter-clockwise turn in
 * standard XY-up coordinates (per §3.1 of the proposal).
 *
 * <p>Densification (toLinear) uses composite Simpson's rule on the
 * heading integral directly, no Fresnel-canonical transformation.
 */
public class ClothoidSegment extends LineString implements Linearizable {
  private static final long serialVersionUID = 1L;

  private final Coordinate startPoint;
  private final double startTangent;     // radians, atan2-style (mathematical)
  private final double startKappa;       // signed curvature (1/R)
  private final double endKappa;
  private final double length;

  private final Coordinate endPoint;
  private final double endTangent;

  /**
   * Construct from explicit start state and (k0, k1, L). The end point
   * and end tangent are derived analytically; clients that need them
   * exact should call {@link #getEndPoint()} / {@link #getEndTangent()}
   * after construction rather than reading the parent's coord sequence
   * (per §3.6 of the proposal: the parent LineString carries only the
   * two endpoint coordinates, the interior is not represented).
   */
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

  public Coordinate getStartCoordinate() { return new Coordinate(startPoint); }
  public Coordinate getEndCoordinate()   { return new Coordinate(endPoint); }
  public double getStartTangent()        { return startTangent; }
  public double getEndTangent()          { return endTangent; }
  public double getStartKappa()          { return startKappa; }
  public double getEndKappa()            { return endKappa; }

  @Override
  public double getLength()            { return length; }

  /**
   * The IFC / LandXML "clothoid constant" {@code A = √(L / |κ₁ − κ₀|)}.
   * For an entry-spiral case ({@code κ₀ = 0}) this collapses to the
   * familiar {@code A = √(L / κ₁)}.
   */
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

  /**
   * §3.7 — type identity is required for {@code equalsExact}. Without this
   * override the inherited {@link LineString#isEquivalentClass(Geometry)}
   * accepts any {@code LineString} subclass, so a plain LineString with
   * the same start/end coords would compare equal to a ClothoidSegment.
   */
  @Override
  protected boolean isEquivalentClass(Geometry other) {
    return other instanceof ClothoidSegment;
  }

  /**
   * §3.7 — compare parameters and start state, not the parent's 2-point
   * coord sequence. Two ClothoidSegments with identical {@code (κ₀, κ₁, L)}
   * but different start state (point or tangent) are different geometries.
   * The end coordinate is derived analytically from the parameters and is
   * therefore not compared separately.
   *
   * <p><strong>Asymmetry caveat.</strong> {@code Geometry.equalsExact} is
   * single-dispatched on the receiver. {@code clothoid.equalsExact(plain)}
   * correctly returns false because of this override, but
   * {@code plain.equalsExact(clothoid)} dispatches to
   * {@link LineString#equalsExact(Geometry, double)} which only sees the
   * 2-point coord sequence and may return true. Curve-aware code should
   * always have a curve-typed reference on the receiver side of the call,
   * or use the {@link #equals(Object)} alternative which is type-strict by
   * default.
   */
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
   * Reverses the traversal direction of the clothoid (§3.8 of the proposal).
   * <p>
   * Curvature is the rate of change of heading with respect to arc length,
   * {@code κ = dθ/ds}. Reversing flips both: the new heading at any
   * physical point is the old heading + π, and the arc length runs the
   * other way ({@code ds' = −ds}). So the new curvature at any physical
   * point is the negation of the old one, which gives the endpoint
   * transformation:
   * <ul>
   *   <li>new {@code κ₀ = −old κ₁}</li>
   *   <li>new {@code κ₁ = −old κ₀}</li>
   * </ul>
   * Start state moves to the old end point, tangent rotated 180°. Length
   * is unchanged. Twice-reversing is the identity within float noise.
   */
  @Override
  protected ClothoidSegment reverseInternal() {
    Coordinate newStart = new Coordinate(endPoint);
    double newTangent = normaliseAngle(endTangent + Math.PI);
    return new ClothoidSegment(newStart, newTangent,
        -endKappa, -startKappa, length, getFactory());
  }

  private static double normaliseAngle(double theta) {
    while (theta >   Math.PI) theta -= 2.0 * Math.PI;
    while (theta <= -Math.PI) theta += 2.0 * Math.PI;
    return theta;
  }

  /**
   * Densify via composite Simpson's rule on the heading integral.
   * Step count is derived from the requested tolerance and the
   * maximum curvature on the segment so that the perpendicular
   * distance from any point on the analytical curve to its
   * containing chord segment is bounded by {@code tolerance}.
   *
   * <p>Derivation: the chord-to-arc sagitta of a piece of curve of
   * arc-length {@code δs} at local curvature {@code κ} is
   * approximately {@code δs²·κ/8}. For sagitta ≤ ε this gives
   * {@code δs ≤ √(8ε/κ)}, so {@code N ≥ L·√(κ/(8ε))}. We use
   * {@code max(|κ₀|, |κ₁|)} as a conservative upper bound on
   * curvature along the segment.
   *
   * <p>Clamped to {@code [16, 8192]} — the floor avoids
   * under-densification on near-straight inputs, and the ceiling
   * caps memory for absurd tolerances. Tolerance below ~1e-9
   * hits the cap on tight-radius inputs.
   */
  @Override
  public Geometry toLinear(double tolerance) {
    if (tolerance <= 0) tolerance = 1e-3;
    double maxKappa = Math.max(Math.abs(startKappa), Math.abs(endKappa));
    int n;
    if (maxKappa < 1e-12) {
      // Degenerate to straight; two points suffice.
      n = 1;
    } else {
      double err = Math.max(tolerance, 1e-9);
      n = (int) Math.ceil(length * Math.sqrt(maxKappa / (8.0 * err)));
      n = Math.max(16, Math.min(8192, n));
    }
    Coordinate[] pts = densifyByN(startPoint, startTangent, startKappa, endKappa, length, n);
    // Snap final point to the canonical end so toLinear is consistent
    // with getEndCoordinate(); the integrator converges to a slightly
    // different value depending on step count.
    pts[pts.length - 1] = new Coordinate(endPoint);
    return getFactory().createLineString(pts);
  }

  /**
   * Analytical envelope per §3.9 of the proposal. The chord-only bbox
   * inherited from {@link LineString} under-represents the curve's actual
   * extent — a clothoid bulges outside the chord between its endpoints.
   *
   * <p>Approach: extreme x values occur where {@code dx/ds = cos(θ(s)) = 0},
   * i.e. where {@code θ(s) = π/2 + nπ}. Extreme y values occur where
   * {@code θ(s) = nπ}. With {@code θ(s) = θ₀ + κ₀·s + ½·(κ₁−κ₀)/L · s²}
   * each equation reduces to a quadratic in {@code s}, with at most two
   * roots, clipped to {@code [0, L]}. For each root we Simpson-integrate
   * up to that arc length and expand the envelope by the resulting
   * (x, y). Endpoints are always included.
   */
  @Override
  protected Envelope computeEnvelopeInternal() {
    if (isEmpty()) return new Envelope();
    Envelope env = new Envelope(startPoint);
    env.expandToInclude(endPoint.x, endPoint.y);
    expandByExtremes(env, Math.PI / 2.0);  // dx/ds = 0
    expandByExtremes(env, 0.0);            // dy/ds = 0
    return env;
  }

  /** Add to {@code env} every position along the curve where the heading
   *  hits {@code phaseOffset + nπ} for some integer {@code n}, restricted
   *  to {@code s ∈ [0, L]}. */
  private void expandByExtremes(Envelope env, double phaseOffset) {
    double thetaMin = Math.min(startTangent, endTangent);
    double thetaMax = Math.max(startTangent, endTangent);
    int nMin = (int) Math.floor((thetaMin - phaseOffset) / Math.PI);
    int nMax = (int) Math.ceil((thetaMax - phaseOffset) / Math.PI);
    double alpha = (endKappa - startKappa) / (2.0 * length);
    for (int n = nMin; n <= nMax; n++) {
      double thetaTarget = phaseOffset + n * Math.PI;
      if (thetaTarget < thetaMin - 1e-12 || thetaTarget > thetaMax + 1e-12) continue;
      // alpha·s² + κ₀·s + (θ₀ - thetaTarget) = 0
      double[] roots = solveQuadratic(alpha, startKappa, startTangent - thetaTarget);
      for (double s : roots) {
        if (Double.isNaN(s) || s < 0 || s > length) continue;
        double[] xy = integrateTo(s);
        env.expandToInclude(xy[0], xy[1]);
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

  /** Real roots of {@code a·x² + b·x + c = 0}; degenerates correctly when
   *  {@code a → 0}. Returns 0, 1, or 2 roots. */
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

  // -------- integration helpers -----------------------------------

  private static double headingAt(double startTheta, double k0, double k1,
                                  double L, double s) {
    return startTheta + k0 * s + (k1 - k0) * s * s / (2.0 * L);
  }

  /**
   * Returns {@code [endX, endY, endTheta]} at {@code s = L} via
   * Simpson's rule on the position integral with a generous fixed step
   * count (256). Used once at construction; toLinear uses an
   * adaptive-by-tolerance step count for actual densification.
   */
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
}

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
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;

/**
 * A polygon whose rings may be straight, circular, or compound curves.
 *
 * <p>Option A (F-CP / FCP-DOVE per SPEC_F_CP.md): legacy {@code getExteriorRing()}
 * and {@code getInteriorRingN(i)} return {@link LinearRing} views obtained from
 * the control-point polyline of the structural ring (phase-1 linear view).
 * {@code toLinear(0.0)} (and thus the legacy ring views) currently return the
 * raw control points with <b>no arc tessellation</b>; the {@code tolerance}
 * parameter is accepted for {@link Linearizable} compatibility but is a no-op
 * in phase 1. Curve-aware code uses {@link #getExteriorCurve()} /
 * {@link #getInteriorCurveN(int)} to obtain the structural {@link LineString}
 * (which may be a {@link CircularString} or {@link CompoundCurve}).
 *
 * <p><b>Equality / identity semantics (EPIC §7 risk, R-EQ TAG):</b>
 * {@code equalsExact} (and by extension structural equality for use in
 * collections via {@link #equals(Object)} / {@link #hashCode()}) is
 * inherited from {@link Polygon} without override. It compares only the
 * control-point {@link LinearRing} views (via {@code toLinear(0.0)}). The
 * structural curves (shell/holes) are <i>not</i> consulted. Consequently:
 * <ul>
 *   <li>Two {@code CurvePolygon}s whose control polylines are identical
 *       compare equal via {@code equalsExact}, even if the curves themselves
 *       differ in type or parameters.</li>
 *   <li>A {@code CurvePolygon} never {@code equalsExact}s a plain
 *       {@code Polygon} (even with identical control points), because
 *       {@link Polygon#isEquivalentClass(Geometry)} (inherited) requires
 *       exact class name match. (Contrast with {@code LineString} subclasses,
 *       where {@code isEquivalentClass} is lenient via {@code instanceof}.)
 * </ul>
 * This is the current (phase-1) behaviour. Arc-aware / structural equality
 * is explicitly deferred to the R-EQ TAG; see the EPIC and
 * {@code SPEC_F_CP.md}. Tests should not assume structural curves affect
 * equality.
 */
public class CurvePolygon extends Polygon implements Linearizable {
  private static final long serialVersionUID = 1L;

  private final LineString structuralShell;
  private final LineString[] structuralHoles;

  public CurvePolygon(LinearRing shell, LinearRing[] holes, GeometryFactory factory) {
    // Note: ternary in super() arg to keep 'super' as the first *statement* for Java 8 source compat.
    // (Flexible constructor bodies / statements-before-super are a newer language feature.)
    super(shell, (holes == null ? new LinearRing[0] : holes), factory);
    this.structuralShell = shell;
    this.structuralHoles = copyAsLineStrings(holes);
  }

  public CurvePolygon(GeometryFactory factory) {
    super(null, null, factory);
    this.structuralShell = null;
    this.structuralHoles = new LineString[0];
  }

  /**
   * Structural constructor (Option A): accepts shell and holes as any LineString
   * (CircularString / CompoundCurve / LineString / LinearRing). Derives
   * LinearRing views at default tolerance for the Polygon supertype so that
   * legacy callers continue to work.
   */
  public CurvePolygon(LineString structuralShell, LineString[] holes, GeometryFactory factory) {
    super(
        deriveLinearRing(structuralShell, factory),
        deriveLinearRings(holes, factory),
        factory);
    this.structuralShell = structuralShell;
    this.structuralHoles = (holes == null) ? new LineString[0] : holes.clone();
  }

  private static LineString[] copyAsLineStrings(LinearRing[] in) {
    if (in == null || in.length == 0) return new LineString[0];
    LineString[] out = new LineString[in.length];
    System.arraycopy(in, 0, out, 0, in.length);
    return out;
  }

  private static LinearRing deriveLinearRing(LineString s, GeometryFactory f) {
    if (s == null) return null;
    if (s instanceof LinearRing) return (LinearRing) s;
    LineString flat = (s instanceof Linearizable)
        ? (LineString) ((Linearizable) s).toLinear(0.0)
        : s;
    return f.createLinearRing(flat.getCoordinates());
  }

  private static LinearRing[] deriveLinearRings(LineString[] hs, GeometryFactory f) {
    if (hs == null || hs.length == 0) return new LinearRing[0];
    LinearRing[] out = new LinearRing[hs.length];
    for (int i = 0; i < hs.length; i++) {
      out[i] = deriveLinearRing(hs[i], f);
    }
    return out;
  }

  /**
   * Returns the structural shell (may be CircularString, CompoundCurve,
   * LineString or LinearRing). Null for empty.
   */
  public LineString getExteriorCurve() {
    return structuralShell;
  }

  /**
   * Returns the structural interior ring (may be curved). Index 0..getNumInteriorRing()-1.
   */
  public LineString getInteriorCurveN(int n) {
    return structuralHoles[n];
  }

  @Override
  public String getGeometryType() {
    return "CurvePolygon";
  }

  /**
   * The area enclosed by the (possibly curved) rings: the area of the polygon
   * through the ring control points plus the signed circular-segment correction
   * for each arc of a {@link CircularString} ring (M-AREA-CP, JTS #1195). For
   * rings with no arcs this equals the ordinary polygon area. Holes are
   * subtracted. A disk expressed as a closed {@code CircularString} shell thus
   * has area {@code pi*r^2}.
   */
  @Override
  public double getArea() {
    if (isEmpty() || structuralShell == null) return 0.0;
    double area = Math.abs(ringSignedArea(structuralShell));
    for (int i = 0; i < structuralHoles.length; i++) {
      area -= Math.abs(ringSignedArea(structuralHoles[i]));
    }
    return area;
  }

  /**
   * The area-weighted centroid, accounting for the circular segments of curved
   * ({@link CircularString}) rings (C-AREA, JTS #1195): for each ring the
   * endpoint-polygon area moments are corrected by the per-arc circular-segment
   * moments (segment area times {@link CircularArcs#segmentCentroid}); holes are
   * subtracted. For rings with no arcs this equals the ordinary polygon
   * centroid. A disk expressed as a closed {@code CircularString} shell thus has
   * its centroid at the circle centre.
   */
  @Override
  public Point getCentroid() {
    if (isEmpty() || structuralShell == null) return super.getCentroid();
    double[] s = ringMoment(structuralShell);
    double area = Math.abs(s[0]);
    double mx = Math.signum(s[0]) * s[1];
    double my = Math.signum(s[0]) * s[2];
    for (int i = 0; i < structuralHoles.length; i++) {
      double[] h = ringMoment(structuralHoles[i]);
      area -= Math.abs(h[0]);
      mx -= Math.signum(h[0]) * h[1];
      my -= Math.signum(h[0]) * h[2];
    }
    if (area == 0.0) return super.getCentroid();
    return getFactory().createPoint(new Coordinate(mx / area, my / area));
  }

  /**
   * Signed area moments {@code [A, Mx, My]} of a (possibly curved) ring, where
   * {@code Mx = integral(x) dA} and {@code My = integral(y) dA} over the enclosed
   * region: the endpoint-polygon shoelace moments plus, for each arc, the signed
   * segment area times its centroid. For a non-curved ring this is the ordinary
   * polygon moment.
   */
  private static double[] ringMoment(LineString ring) {
    CoordinateSequence seq = ring.getCoordinateSequence();
    int n = seq.size();
    double a = 0, mx = 0, my = 0;
    if (n < 3) return new double[]{ 0, 0, 0 };
    if (ring instanceof CircularString) {
      int i = 0;
      for (; i + 2 < n; i += 2) {
        double sx = seq.getX(i),     sy = seq.getY(i);
        double mmx = seq.getX(i + 1), mmy = seq.getY(i + 1);
        double ex = seq.getX(i + 2), ey = seq.getY(i + 2);
        double cross = sx * ey - ex * sy;
        a += 0.5 * cross;
        mx += (sx + ex) * cross / 6.0;
        my += (sy + ey) * cross / 6.0;
        double segA = CircularArcs.signedSegmentArea(sx, sy, mmx, mmy, ex, ey);
        double[] segC = CircularArcs.segmentCentroid(sx, sy, mmx, mmy, ex, ey);
        a += segA;
        mx += segA * segC[0];
        my += segA * segC[1];
      }
      for (; i + 1 < n; i++) {
        double sx = seq.getX(i), sy = seq.getY(i), ex = seq.getX(i + 1), ey = seq.getY(i + 1);
        double cross = sx * ey - ex * sy;
        a += 0.5 * cross; mx += (sx + ex) * cross / 6.0; my += (sy + ey) * cross / 6.0;
      }
      return new double[]{ a, mx, my };
    }
    for (int i = 0; i < n - 1; i++) {
      double sx = seq.getX(i), sy = seq.getY(i), ex = seq.getX(i + 1), ey = seq.getY(i + 1);
      double cross = sx * ey - ex * sy;
      a += 0.5 * cross; mx += (sx + ex) * cross / 6.0; my += (sy + ey) * cross / 6.0;
    }
    return new double[]{ a, mx, my };
  }

  /**
   * Signed area of a (possibly curved) ring: the shoelace area of the polygon
   * through its endpoints plus the signed segment area of each arc. For a
   * non-curved ring this reduces to the ordinary shoelace area.
   */
  private static double ringSignedArea(LineString ring) {
    org.locationtech.jts.geom.CoordinateSequence seq = ring.getCoordinateSequence();
    int n = seq.size();
    if (n < 3) return 0.0;
    if (ring instanceof CircularString) {
      double a = 0.0;
      int i = 0;
      for (; i + 2 < n; i += 2) {
        double sx = seq.getX(i),     sy = seq.getY(i);
        double mx = seq.getX(i + 1), my = seq.getY(i + 1);
        double ex = seq.getX(i + 2), ey = seq.getY(i + 2);
        a += 0.5 * (sx * ey - ex * sy);                       // chord (endpoint polygon) term
        a += CircularArcs.signedSegmentArea(sx, sy, mx, my, ex, ey);
      }
      for (; i + 1 < n; i++) {                                // dangling trailing edge
        a += 0.5 * (seq.getX(i) * seq.getY(i + 1) - seq.getX(i + 1) * seq.getY(i));
      }
      return a;
    }
    // non-curved ring: ordinary shoelace over all vertices
    double a = 0.0;
    for (int i = 0; i < n - 1; i++) {
      a += 0.5 * (seq.getX(i) * seq.getY(i + 1) - seq.getX(i + 1) * seq.getY(i));
    }
    return a;
  }

  @Override
  public CurvePolygon reverse() {
    return (CurvePolygon) super.reverse();
  }

  @Override
  protected CurvePolygon reverseInternal() {
    GeometryFactory f = getFactory();
    if (isEmpty() || structuralShell == null) {
      return new CurvePolygon(f);
    }
    LineString revShell = (LineString) structuralShell.reverse();
    LineString[] revHoles = new LineString[structuralHoles.length];
    for (int i = 0; i < structuralHoles.length; i++) {
      revHoles[i] = (LineString) structuralHoles[i].reverse();
    }
    return new CurvePolygon(revShell, revHoles, f);
  }

  @Override
  public void normalize() {
    // Normalize the legacy LinearRing views (shell/holes) per Polygon contract (Option A).
    // The structural curves (source of truth for arcs) are left unchanged; their densified
    // views are normalized. This avoids losing curved identity while keeping legacy API
    // behaviour (e.g. normalized() rings for getExteriorRing etc.).
    // Full arc-aware structural normalization (e.g. consistent orientation of CircularString
    // members) is deferred; see review notes for SPEC_F_CP.md alignment.
    super.normalize();
  }

  @Override
  protected CurvePolygon copyInternal() {
    GeometryFactory f = getFactory();
    if (isEmpty() || structuralShell == null) return new CurvePolygon(f);
    LineString shellCopy = (LineString) structuralShell.copy();
    LineString[] holeCopies = new LineString[structuralHoles.length];
    for (int i = 0; i < structuralHoles.length; i++) {
      holeCopies[i] = (LineString) structuralHoles[i].copy();
    }
    return new CurvePolygon(shellCopy, holeCopies, f);
  }

  @Override
  public Geometry toLinear(double tolerance) {
    GeometryFactory f = getFactory();
    if (isEmpty() || structuralShell == null) return f.createPolygon();
    LineString shellFlat = (structuralShell instanceof Linearizable)
        ? (LineString) ((Linearizable) structuralShell).toLinear(tolerance)
        : structuralShell;
    LinearRing shell = f.createLinearRing(shellFlat.getCoordinates());
    LinearRing[] holeRings = new LinearRing[structuralHoles.length];
    for (int i = 0; i < structuralHoles.length; i++) {
      LineString h = structuralHoles[i];
      LineString hflat = (h instanceof Linearizable)
          ? (LineString) ((Linearizable) h).toLinear(tolerance)
          : h;
      holeRings[i] = f.createLinearRing(hflat.getCoordinates());
    }
    return f.createPolygon(shell, holeRings);
  }
}

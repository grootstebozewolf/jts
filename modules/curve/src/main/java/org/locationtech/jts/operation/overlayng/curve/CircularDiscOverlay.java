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
package org.locationtech.jts.operation.overlayng.curve;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.CoordinateSequence;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.geom.curve.CircularArcDensifier;
import org.locationtech.jts.geom.curve.CircularString;
import org.locationtech.jts.geom.curve.CompoundCurve;
import org.locationtech.jts.geom.curve.CurvePolygon;
import org.locationtech.jts.geom.curve.MultiSurface;
import org.locationtech.jts.operation.overlayng.OverlayNG;

/**
 * Closed-form overlay of two circular discs. Package-private -- not a new
 * public API, and not a noder. OverlayNG never sees these edges.
 * <p>
 * Two proper crossings become a {@link CurvePolygon} whose shell is two
 * {@link CircularString}s (the intersection points plus a mid-arc control
 * on the line of centres). CAP is the lens, CUP the outer blob, SUB a
 * crescent, XOR both crescents. Nested discs (0 nodes, one strictly
 * inside the other) are the annulus: CAP the inner, CUP the outer,
 * SUB / XOR the outer with the inner as a hole. Same closed form as
 * {@link HalfDiscOverlay#containedShell}; not a noder. Anything else
 * -- not both discs, 1 intersection, a nest that is not strictly
 * inside ({@code H-ANNULUS-TANGENT}: internal tangent, 1 node,
 * d+r = R) -- returns {@code null} so the caller can take the chord
 * baseline without paying this path first.
 */
final class CircularDiscOverlay {

  /**
   * Two computed nodes closer than this fraction of the smaller radius are
   * a tangent pair in floating point, not a proper crossing.
   */
  private static final double PROPER_CROSS_FRAC = TwoNodeClip.PROPER_CROSS_FRAC;
  private static final double TWO_PI = TwoNodeClip.TWO_PI;
  private static final double SWEEP_EPS = 1.0e-9;

  private CircularDiscOverlay() { }

  /**
   * Exact overlay of two circular discs, or {@code null} if this class
   * cannot answer. The cheap shape check runs first; a miss does not
   * densify and does not node.
   */
  /**
   * {@code {cx, cy, r}} when {@code g} is a circular disc, else {@code null}.
   * Shared with {@link CircularDiscPolygonOverlay}; same predicate as
   * {@code CurveExact.circularDisc}.
   */
  static double[] centreRadius(Geometry g) {
    Disc d = circularDisc(g);
    if (d == null) return null;
    return new double[] { d.cx, d.cy, d.r };
  }

  /**
   * The disc of centre {@code (cx, cy)} and radius {@code r} as a
   * five-point {@link CurvePolygon}. Used by the complementary
   * half-disc CUP / XOR; not a noder.
   */
  static CurvePolygon discPolygon(double cx, double cy, double r,
      GeometryFactory f) {
    Coordinate[] pts = new Coordinate[] {
        new Coordinate(cx + r, cy),
        new Coordinate(cx, cy + r),
        new Coordinate(cx - r, cy),
        new Coordinate(cx, cy - r),
        new Coordinate(cx + r, cy)
    };
    CircularString ring = new CircularString(
        f.getCoordinateSequenceFactory().create(pts), f);
    return new CurvePolygon(ring, null, f);
  }

  static Geometry overlay(Geometry a, Geometry b, int opCode) {
    Disc da = circularDisc(a);
    if (da == null) return null;
    Disc db = circularDisc(b);
    if (db == null) return null;

    Coordinate[] nodes = intersectCircles(da, db);
    if (nodes.length != 2) {
      return nestedAnnulus(a, b, da, db, opCode);
    }
    double minR = Math.min(da.r, db.r);
    if (nodes[0].distance(nodes[1]) < PROPER_CROSS_FRAC * minR) return null;

    Coordinate inA = pole(da, db, true);
    Coordinate outA = pole(da, db, false);
    Coordinate inB = pole(db, da, true);
    Coordinate outB = pole(db, da, false);
    if (inA == null || outA == null || inB == null || outB == null) return null;
    if (!usableMid(inA, nodes, minR) || !usableMid(outA, nodes, minR)
        || !usableMid(inB, nodes, minR) || !usableMid(outB, nodes, minR)) {
      return null;
    }

    GeometryFactory f = TwoNodeClip.curveFactory(a);
    Coordinate p = nodes[0];
    Coordinate q = nodes[1];
    if (opCode == OverlayNG.INTERSECTION) {
      return twoArcPolygon(p, inA, q, inB, f);
    }
    if (opCode == OverlayNG.UNION) {
      return twoArcPolygon(p, outA, q, outB, f);
    }
    if (opCode == OverlayNG.DIFFERENCE) {
      return twoArcPolygon(p, outA, q, inB, f);
    }
    if (opCode == OverlayNG.SYMDIFFERENCE) {
      Polygon ab = twoArcPolygon(p, outA, q, inB, f);
      Polygon ba = twoArcPolygon(p, outB, q, inA, f);
      return new MultiSurface(new Polygon[] { ab, ba }, f);
    }
    return null;
  }

  /**
   * 0-node nested discs. CAP the inner, CUP the outer, SUB / XOR the
   * annulus. A nest that is not strictly inside is {@code null}
   * ({@code H-ANNULUS-TANGENT}: internal tangent, 1 node, d+r = R).
   */
  private static Geometry nestedAnnulus(Geometry a, Geometry b, Disc da,
      Disc db, int opCode) {
    CurvePolygon ca = asCurvePolygon(a);
    CurvePolygon cb = asCurvePolygon(b);
    if (ca == null || cb == null) return null;
    double scale = Math.max(Math.max(da.r, db.r), 1.0);
    double eps = Math.max(PROPER_CROSS_FRAC * scale, 1.0e-12);
    double d = Math.hypot(da.cx - db.cx, da.cy - db.cy);
    boolean sameCircle = d <= eps && Math.abs(da.r - db.r) <= eps;
    if (sameCircle) {
      return HalfDiscOverlay.identityShell(opCode, a,
          TwoNodeClip.curveFactory(a));
    }
    boolean aInB = d + da.r <= db.r - eps;
    boolean bInA = d + db.r <= da.r - eps;
    if (aInB && !bInA) {
      return HalfDiscOverlay.containedShell(ca, cb, true, opCode, a,
          TwoNodeClip.curveFactory(a));
    }
    if (bInA && !aInB) {
      return HalfDiscOverlay.containedShell(cb, ca, false, opCode, a,
          TwoNodeClip.curveFactory(a));
    }
    // Internal tangent / 1-node nest is not strictly inside
    // (H-ANNULUS-TANGENT). Keep null; do not laser it.
    return null;
  }

  private static CurvePolygon asCurvePolygon(Geometry g) {
    if (g instanceof MultiSurface) {
      if (g.getNumGeometries() != 1) return null;
      g = g.getGeometryN(0);
    }
    if (!(g instanceof CurvePolygon)) return null;
    CurvePolygon cp = (CurvePolygon) g;
    if (cp.isEmpty() || cp.getNumInteriorRing() > 0) return null;
    return cp;
  }

  /**
   * Point of {@code self} on the line of centres, toward {@code other}
   * ({@code inside}) or away from it. Those two points are the mid-arc
   * controls of the minor and major arcs between the crossing nodes.
   */
  private static Coordinate pole(Disc self, Disc other, boolean inside) {
    double dx = other.cx - self.cx;
    double dy = other.cy - self.cy;
    double d = Math.hypot(dx, dy);
    if (d == 0.0) return null;
    double s = inside ? 1.0 : -1.0;
    return new Coordinate(
        self.cx + s * self.r * dx / d,
        self.cy + s * self.r * dy / d);
  }

  private static boolean usableMid(Coordinate mid, Coordinate[] nodes,
      double minR) {
    double eps = PROPER_CROSS_FRAC * minR;
    return mid.distance(nodes[0]) > eps && mid.distance(nodes[1]) > eps;
  }

  private static Polygon twoArcPolygon(Coordinate p, Coordinate midA,
      Coordinate q, Coordinate midB, GeometryFactory f) {
    CircularString arcA = TwoNodeClip.arc(p, midA, q, f);
    CircularString arcB = TwoNodeClip.arc(q, midB, p, f);
    CompoundCurve shell = new CompoundCurve(new LineString[] { arcA, arcB }, f);
    return new CurvePolygon(shell, null, f);
  }

  private static Disc circularDisc(Geometry g) {
    if (g instanceof MultiSurface) {
      if (g.getNumGeometries() != 1) return null;
      return circularDisc(g.getGeometryN(0));
    }
    if (!(g instanceof CurvePolygon)) return null;
    CurvePolygon cp = (CurvePolygon) g;
    if (cp.isEmpty() || cp.getNumInteriorRing() > 0) return null;
    return fullCircle(cp.getExteriorCurve());
  }

  private static Disc fullCircle(LineString ring) {
    if (ring instanceof CircularString) {
      return fullCircle((CircularString) ring);
    }
    if (ring instanceof CompoundCurve) {
      CompoundCurve cc = (CompoundCurve) ring;
      Disc found = null;
      double sweep = 0.0;
      for (int i = 0; i < cc.getNumMembers(); i++) {
        LineString m = cc.getMemberN(i);
        if (!(m instanceof CircularString)) return null;
        CircularString cs = (CircularString) m;
        Disc c = sameCircle(cs, found);
        if (c == null) return null;
        found = c;
        sweep += totalSweep(cs);
      }
      if (found == null || !ring.isClosed()) return null;
      if (Math.abs(Math.abs(sweep) - TWO_PI) > SWEEP_EPS) return null;
      return found;
    }
    return null;
  }

  private static Disc fullCircle(CircularString cs) {
    if (cs.isEmpty() || !cs.isClosed() || cs.getNumPoints() < 5) return null;
    Disc c = sameCircle(cs, null);
    if (c == null) return null;
    if (Math.abs(Math.abs(totalSweep(cs)) - TWO_PI) > SWEEP_EPS) return null;
    return c;
  }

  private static Disc sameCircle(CircularString cs, Disc expected) {
    CoordinateSequence seq = cs.getCoordinateSequence();
    int n = seq.size();
    if (n < 3) return null;
    Disc found = expected;
    for (int i = 0; i + 2 < n; i += 2) {
      double[] c = CircularArcDensifier.circumcircle(
          seq.getCoordinate(i), seq.getCoordinate(i + 1), seq.getCoordinate(i + 2));
      if (c == null) return null;
      if (found == null) {
        found = new Disc(c[0], c[1], c[2]);
      } else if (Math.hypot(found.cx - c[0], found.cy - c[1]) > 1.0e-9
          || Math.abs(found.r - c[2]) > 1.0e-9) {
        return null;
      }
    }
    return found;
  }

  private static double totalSweep(CircularString cs) {
    CoordinateSequence seq = cs.getCoordinateSequence();
    int n = seq.size();
    double total = 0.0;
    for (int i = 0; i + 2 < n; i += 2) {
      Coordinate start = seq.getCoordinate(i);
      Coordinate mid = seq.getCoordinate(i + 1);
      Coordinate end = seq.getCoordinate(i + 2);
      double[] c = CircularArcDensifier.circumcircle(start, mid, end);
      if (c == null) continue;
      double a0 = Math.atan2(start.y - c[1], start.x - c[0]);
      double aMid = Math.atan2(mid.y - c[1], mid.x - c[0]);
      double a1 = Math.atan2(end.y - c[1], end.x - c[0]);
      boolean ccw = normPos(aMid - a0) < normPos(a1 - a0);
      double sweep = ccw ? normPos(a1 - a0) : -normPos(a0 - a1);
      if (sweep == 0.0) sweep = ccw ? TWO_PI : -TWO_PI;
      total += sweep;
    }
    return total;
  }

  private static double normPos(double angle) {
    return TwoNodeClip.normPos(angle);
  }

  /**
   * Radical-axis nodes of two supporting circles. Empty when the circles
   * are disjoint, nested, or coincident.
   */
  private static Coordinate[] intersectCircles(Disc ca, Disc cb) {
    if (ca == null || cb == null) return new Coordinate[0];
    return TwoNodeClip.intersectCircles(ca.cx, ca.cy, ca.r, cb.cx, cb.cy, cb.r);
  }

  private static final class Disc {
    final double cx, cy, r;
    Disc(double cx, double cy, double r) {
      this.cx = cx;
      this.cy = cy;
      this.r = r;
    }
  }
}

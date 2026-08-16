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

import java.util.ArrayList;
import java.util.List;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.geom.curve.CurvePolygon;
import org.locationtech.jts.geom.curve.MultiSurface;
import org.locationtech.jts.operation.overlayng.OverlayNG;

/**
 * Closed-form overlay of two half-discs: complementary (shared
 * diameter, opposite caps), perpendicular same-circle sectors, and
 * collinear same-side identity / nested / half-lens / point-touch.
 * Package-private -- not a noder.
 */
final class HalfDiscOverlay {

  private HalfDiscOverlay() { }

  static Geometry overlay(CurvePolygon a, CurvePolygon b, int opCode,
      Geometry first) {
    Geometry halves = complementaryHalfDiscs(a, b, opCode, first);
    if (halves != null) {
      return halves;
    }
    Geometry sectors = overlappingSameCircleHalfDiscs(a, b, opCode, first);
    if (sectors != null) {
      return sectors;
    }
    return collinearSameSideHalfDiscs(a, b, opCode, first);
  }

  /**
   * Two half-discs of the same circle that share a diameter and take
   * opposite caps. Not a two-shell noder: any other pair is
   * {@code null} so the caller can try sectors or a two-node walk.
   */
  private static Geometry complementaryHalfDiscs(CurvePolygon a,
      CurvePolygon b, int opCode, Geometry first) {
    HalfDisc ha = halfDisc(a);
    HalfDisc hb = halfDisc(b);
    if (ha == null || hb == null) return null;
    double scale = Math.max(ha.r, 1.0);
    double eps = Math.max(TwoNodeClip.PROPER_CROSS_FRAC * scale, 1.0e-12);
    if (ha.centre.distance(hb.centre) > eps || Math.abs(ha.r - hb.r) > eps) {
      return null;
    }
    if (!sameEnds(ha.d0, ha.d1, hb.d0, hb.d1, eps)) return null;
    if (ha.mid.distance(hb.mid) <= ha.r) return null;
    GeometryFactory f = TwoNodeClip.curveFactory(first);
    if (opCode == OverlayNG.INTERSECTION) {
      return f.createEmpty(2);
    }
    if (opCode == OverlayNG.UNION || opCode == OverlayNG.SYMDIFFERENCE) {
      return CircularDiscOverlay.discPolygon(ha.centre.x, ha.centre.y, ha.r, f);
    }
    if (opCode == OverlayNG.DIFFERENCE) {
      return first;
    }
    return null;
  }

  /**
   * Two half-discs of the same circle whose diameters are
   * perpendicular. Cheaper than a two-node walk: the diameters meet
   * at the centre, so a generic clip would see three nodes.
   */
  private static Geometry overlappingSameCircleHalfDiscs(CurvePolygon a,
      CurvePolygon b, int opCode, Geometry first) {
    HalfDisc ha = halfDisc(a);
    HalfDisc hb = halfDisc(b);
    if (ha == null || hb == null) return null;
    double scale = Math.max(ha.r, 1.0);
    double eps = Math.max(TwoNodeClip.PROPER_CROSS_FRAC * scale, 1.0e-12);
    if (ha.centre.distance(hb.centre) > eps || Math.abs(ha.r - hb.r) > eps) {
      return null;
    }
    if (!isDiameter(ha, eps) || !isDiameter(hb, eps)) return null;
    double adx = ha.d1.x - ha.d0.x;
    double ady = ha.d1.y - ha.d0.y;
    double bdx = hb.d1.x - hb.d0.x;
    double bdy = hb.d1.y - hb.d0.y;
    if (Math.abs(adx * bdx + ady * bdy) > eps * 4.0 * ha.r) return null;

    Coordinate capA = strictlyInsideEnd(ha, hb, eps);
    Coordinate fromA = strictlyOutsideEnd(ha, hb, eps);
    Coordinate capB = strictlyInsideEnd(hb, ha, eps);
    Coordinate fromB = strictlyOutsideEnd(hb, ha, eps);
    if (capA == null || fromA == null || capB == null || fromB == null) {
      return null;
    }
    Coordinate midCap = onCircle(ha.centre, ha.r, capA, capB);
    Coordinate midAB = onCircle(ha.centre, ha.r, fromA, capB);
    Coordinate midBA = onCircle(ha.centre, ha.r, fromB, capA);
    if (midCap == null || midAB == null || midBA == null) return null;

    GeometryFactory f = TwoNodeClip.curveFactory(first);
    if (opCode == OverlayNG.INTERSECTION) {
      return sector(capA, midCap, capB, ha.centre, f, scale);
    }
    if (opCode == OverlayNG.UNION) {
      return sector(fromA, midCap, fromB, ha.centre, f, scale);
    }
    if (opCode == OverlayNG.DIFFERENCE) {
      return sector(fromA, midAB, capB, ha.centre, f, scale);
    }
    if (opCode == OverlayNG.SYMDIFFERENCE) {
      Polygon ab = sector(fromA, midAB, capB, ha.centre, f, scale);
      Polygon ba = sector(fromB, midBA, capA, ha.centre, f, scale);
      if (ab == null || ba == null) return null;
      return new MultiSurface(new Polygon[] { ab, ba }, f);
    }
    return null;
  }

  /**
   * Two half-discs whose diameters lie on one line and whose caps
   * take the same side. Same circle is the half-disc; nested is the
   * smaller / larger / half-annulus; a point-touch is disjoint
   * interiors; a proper crossing is the half-lens on that line.
   */
  private static Geometry collinearSameSideHalfDiscs(CurvePolygon a,
      CurvePolygon b, int opCode, Geometry first) {
    HalfDisc ha = halfDisc(a);
    HalfDisc hb = halfDisc(b);
    if (ha == null || hb == null) return null;
    double scale = Math.max(Math.max(ha.r, hb.r), 1.0);
    double eps = Math.max(TwoNodeClip.PROPER_CROSS_FRAC * scale, 1.0e-12);
    if (!isDiameter(ha, eps) || !isDiameter(hb, eps)) return null;
    if (!collinearDiameters(ha, hb, eps) || !sameCapSide(ha, hb, eps)) {
      return null;
    }

    GeometryFactory f = TwoNodeClip.curveFactory(first);
    double d = ha.centre.distance(hb.centre);
    boolean sameCircle = d <= eps && Math.abs(ha.r - hb.r) <= eps;
    if (sameCircle) {
      if (!sameEnds(ha.d0, ha.d1, hb.d0, hb.d1, eps)) return null;
      return identityShell(opCode, first, f);
    }
    boolean aInB = d + ha.r <= hb.r + eps;
    boolean bInA = d + hb.r <= ha.r + eps;
    if (aInB && !bInA) {
      return containedShell(a, b, true, opCode, first, f);
    }
    if (bInA && !aInB) {
      return containedShell(b, a, false, opCode, first, f);
    }
    if (d >= ha.r + hb.r - eps) {
      return disjointShells(opCode, first, a, b, f);
    }
    return crossingHalfLens(ha, hb, opCode, f, scale, eps);
  }

  static Geometry identityShell(int opCode, Geometry first,
      GeometryFactory f) {
    if (opCode == OverlayNG.INTERSECTION || opCode == OverlayNG.UNION) {
      return first.copy();
    }
    if (opCode == OverlayNG.DIFFERENCE || opCode == OverlayNG.SYMDIFFERENCE) {
      return f.createEmpty(2);
    }
    return null;
  }

  static Geometry containedShell(CurvePolygon inner, CurvePolygon outer,
      boolean innerIsFirst, int opCode, Geometry first, GeometryFactory f) {
    if (opCode == OverlayNG.INTERSECTION) {
      return innerIsFirst ? first.copy() : inner.copy();
    }
    if (opCode == OverlayNG.UNION) {
      return innerIsFirst ? outer.copy() : first.copy();
    }
    if (opCode == OverlayNG.DIFFERENCE) {
      return innerIsFirst ? f.createEmpty(2) : withHole(outer, inner, f);
    }
    if (opCode == OverlayNG.SYMDIFFERENCE) {
      return withHole(outer, inner, f);
    }
    return null;
  }

  static Geometry disjointShells(int opCode, Geometry first,
      CurvePolygon a, CurvePolygon b, GeometryFactory f) {
    if (opCode == OverlayNG.INTERSECTION) {
      return f.createEmpty(2);
    }
    if (opCode == OverlayNG.DIFFERENCE) {
      return first.copy();
    }
    if (opCode == OverlayNG.UNION || opCode == OverlayNG.SYMDIFFERENCE) {
      return new MultiSurface(new Polygon[] {
          (Polygon) a.copy(), (Polygon) b.copy()
      }, f);
    }
    return null;
  }

  static Coordinate shellSample(CurvePolygon cp) {
    Envelope env = cp.getEnvelopeInternal();
    Coordinate c = new Coordinate(
        0.5 * (env.getMinX() + env.getMaxX()),
        0.5 * (env.getMinY() + env.getMaxY()));
    if (TwoNodeClip.locateInShell(c, cp) == TwoNodeClip.IN) {
      return c;
    }
    HalfDisc h = halfDisc(cp);
    if (h == null) return null;
    return new Coordinate(
        h.centre.x + 0.5 * (h.mid.x - h.centre.x),
        h.centre.y + 0.5 * (h.mid.y - h.centre.y));
  }

  private static Geometry withHole(CurvePolygon outer, CurvePolygon inner,
      GeometryFactory f) {
    return new CurvePolygon(outer.getExteriorCurve(),
        new LineString[] { inner.getExteriorCurve() }, f);
  }

  private static boolean isDiameter(HalfDisc h, double eps) {
    if (Math.abs(h.d0.distance(h.d1) - 2.0 * h.r) > eps) return false;
    double t = TwoNodeClip.parameter(h.d0, h.d1, h.centre);
    Coordinate q = new Coordinate(
        h.d0.x + t * (h.d1.x - h.d0.x),
        h.d0.y + t * (h.d1.y - h.d0.y));
    return q.distance(h.centre) <= eps
        && h.d0.distance(h.centre) > eps
        && h.d1.distance(h.centre) > eps;
  }

  private static double capDot(Coordinate p, HalfDisc h) {
    return (p.x - h.centre.x) * (h.mid.x - h.centre.x)
        + (p.y - h.centre.y) * (h.mid.y - h.centre.y);
  }

  private static Coordinate strictlyInsideEnd(HalfDisc self, HalfDisc other,
      double eps) {
    double thresh = eps * other.r * other.r;
    boolean in0 = capDot(self.d0, other) > thresh;
    boolean in1 = capDot(self.d1, other) > thresh;
    if (in0 == in1) return null;
    return in0 ? self.d0 : self.d1;
  }

  private static Coordinate strictlyOutsideEnd(HalfDisc self, HalfDisc other,
      double eps) {
    Coordinate inside = strictlyInsideEnd(self, other, eps);
    if (inside == null) return null;
    return inside.distance(self.d0) <= eps ? self.d1 : self.d0;
  }

  private static Coordinate onCircle(Coordinate c, double r, Coordinate p,
      Coordinate q) {
    double ux = (p.x - c.x) + (q.x - c.x);
    double uy = (p.y - c.y) + (q.y - c.y);
    double n = Math.hypot(ux, uy);
    if (n == 0.0) return null;
    return new Coordinate(c.x + r * ux / n, c.y + r * uy / n);
  }

  private static Polygon sector(Coordinate from, Coordinate mid, Coordinate to,
      Coordinate c, GeometryFactory f, double scale) {
    List<LineString> members = new ArrayList<LineString>();
    members.add(f.createLineString(new Coordinate[] {
        new Coordinate(c), new Coordinate(from)
    }));
    members.add(TwoNodeClip.arc(from, mid, to, f));
    members.add(f.createLineString(new Coordinate[] {
        new Coordinate(to), new Coordinate(c)
    }));
    return TwoNodeClip.closeRing(members, f,
        Math.max(TwoNodeClip.PROPER_CROSS_FRAC * scale, 1.0e-12));
  }

  private static boolean collinearDiameters(HalfDisc a, HalfDisc b,
      double eps) {
    double len = a.d0.distance(a.d1);
    if (len == 0.0) return false;
    double tol = eps * len;
    return Math.abs(cross2(a.d0, a.d1, b.d0)) <= tol
        && Math.abs(cross2(a.d0, a.d1, b.d1)) <= tol;
  }

  private static boolean sameCapSide(HalfDisc a, HalfDisc b, double eps) {
    double sa = cross2(a.d0, a.d1, a.mid);
    double sb = cross2(a.d0, a.d1, b.mid);
    double thresh = eps * a.d0.distance(a.d1);
    if (Math.abs(sa) <= thresh || Math.abs(sb) <= thresh) return false;
    return sa * sb > 0.0;
  }

  private static double cross2(Coordinate o, Coordinate a, Coordinate p) {
    return (a.x - o.x) * (p.y - o.y) - (a.y - o.y) * (p.x - o.x);
  }

  private static Geometry crossingHalfLens(HalfDisc ha, HalfDisc hb,
      int opCode, GeometryFactory f, double scale, double eps) {
    Coordinate node = capNode(ha, hb);
    Coordinate endA = endInDisc(ha, hb, eps);
    Coordinate endB = endInDisc(hb, ha, eps);
    if (node == null || endA == null || endB == null) return null;
    if (node.distance(endA) <= eps || node.distance(endB) <= eps) {
      return null;
    }
    Coordinate fromA = endA.distance(ha.d0) <= eps ? ha.d1 : ha.d0;
    Coordinate fromB = endB.distance(hb.d0) <= eps ? hb.d1 : hb.d0;
    Coordinate midCapA = pickCapMid(node, endA, ha, hb, true);
    Coordinate midCapB = pickCapMid(endB, node, hb, ha, true);
    Coordinate midCupA = pickCapMid(fromA, node, ha, hb, false);
    Coordinate midCupB = pickCapMid(node, fromB, hb, ha, false);
    if (midCapA == null || midCapB == null || midCupA == null
        || midCupB == null) {
      return null;
    }
    if (opCode == OverlayNG.INTERSECTION) {
      return closePieces(f, scale,
          TwoNodeClip.arc(node, midCapA, endA, f),
          line(endA, endB, f),
          TwoNodeClip.arc(endB, midCapB, node, f));
    }
    if (opCode == OverlayNG.UNION) {
      return closePieces(f, scale,
          TwoNodeClip.arc(fromA, midCupA, node, f),
          TwoNodeClip.arc(node, midCupB, fromB, f),
          line(fromB, fromA, f));
    }
    if (opCode == OverlayNG.DIFFERENCE) {
      return closePieces(f, scale,
          TwoNodeClip.arc(fromA, midCupA, node, f),
          TwoNodeClip.arc(node, midCapB, endB, f),
          line(endB, fromA, f));
    }
    if (opCode == OverlayNG.SYMDIFFERENCE) {
      Polygon ab = closePieces(f, scale,
          TwoNodeClip.arc(fromA, midCupA, node, f),
          TwoNodeClip.arc(node, midCapB, endB, f),
          line(endB, fromA, f));
      Polygon ba = closePieces(f, scale,
          TwoNodeClip.arc(fromB, midCupB, node, f),
          TwoNodeClip.arc(node, midCapA, endA, f),
          line(endA, fromB, f));
      if (ab == null || ba == null) return null;
      return new MultiSurface(new Polygon[] { ab, ba }, f);
    }
    return null;
  }

  private static Coordinate capNode(HalfDisc ha, HalfDisc hb) {
    Coordinate[] xs = TwoNodeClip.intersectCircles(
        ha.centre.x, ha.centre.y, ha.r, hb.centre.x, hb.centre.y, hb.r);
    Coordinate found = null;
    boolean two = false;
    for (int k = 0; k < xs.length; k++) {
      if (onCap(ha, xs[k]) && onCap(hb, xs[k])) {
        if (found != null) {
          two = true;
        }
        else {
          found = xs[k];
        }
      }
    }
    return two ? null : found;
  }

  private static boolean onCap(HalfDisc h, Coordinate p) {
    double[] c = new double[] { h.centre.x, h.centre.y, h.r };
    return TwoNodeClip.isOnSweep(p, c, h.d0, h.mid, h.d1);
  }

  private static Coordinate endInDisc(HalfDisc self, HalfDisc other,
      double eps) {
    boolean in0 = self.d0.distance(other.centre) <= other.r + eps;
    boolean in1 = self.d1.distance(other.centre) <= other.r + eps;
    if (in0 == in1) return null;
    return in0 ? self.d0 : self.d1;
  }

  /**
   * Mid-arc of the cap sweep from {@code from} to {@code to} that
   * lies inside ({@code wantInside}) or outside the other half-disc.
   */
  private static Coordinate pickCapMid(Coordinate from, Coordinate to,
      HalfDisc self, HalfDisc other, boolean wantInside) {
    double a0 = Math.atan2(from.y - self.centre.y, from.x - self.centre.x);
    double a1 = Math.atan2(to.y - self.centre.y, to.x - self.centre.x);
    Coordinate ccw = TwoNodeClip.midOnCircle(self.centre.x, self.centre.y,
        self.r, a0, TwoNodeClip.normPos(a1 - a0));
    Coordinate cw = TwoNodeClip.midOnCircle(self.centre.x, self.centre.y,
        self.r, a0, -TwoNodeClip.normPos(a0 - a1));
    boolean ccwOk = onCap(self, ccw)
        && (ccw.distance(other.centre) < other.r) == wantInside;
    boolean cwOk = onCap(self, cw)
        && (cw.distance(other.centre) < other.r) == wantInside;
    if (ccwOk == cwOk) return null;
    return ccwOk ? ccw : cw;
  }

  private static LineString line(Coordinate a, Coordinate b,
      GeometryFactory f) {
    return f.createLineString(new Coordinate[] {
        new Coordinate(a), new Coordinate(b)
    });
  }

  private static Polygon closePieces(GeometryFactory f, double scale,
      LineString p0, LineString p1, LineString p2) {
    List<LineString> members = new ArrayList<LineString>();
    members.add(p0);
    members.add(p1);
    members.add(p2);
    return TwoNodeClip.closeRing(members, f,
        Math.max(TwoNodeClip.PROPER_CROSS_FRAC * scale, 1.0e-12));
  }

  private static HalfDisc halfDisc(CurvePolygon cp) {
    List<TwoNodeClip.Edge> edges = TwoNodeClip.flatten(cp);
    if (edges == null || edges.size() != 2) return null;
    TwoNodeClip.Edge arc = null;
    TwoNodeClip.Edge line = null;
    for (int i = 0; i < edges.size(); i++) {
      TwoNodeClip.Edge e = edges.get(i);
      if (e.isArc) {
        if (arc != null) return null;
        arc = e;
      }
      else {
        if (line != null) return null;
        line = e;
      }
    }
    if (arc == null || line == null) return null;
    double eps = Math.max(TwoNodeClip.PROPER_CROSS_FRAC * arc.circle[2],
        1.0e-12);
    if (!sameEnds(line.a, line.b, arc.a, arc.b, eps)) return null;
    return new HalfDisc(new Coordinate(arc.circle[0], arc.circle[1]),
        arc.circle[2], arc.a, arc.b, arc.mid);
  }

  private static boolean sameEnds(Coordinate a0, Coordinate a1,
      Coordinate b0, Coordinate b1, double eps) {
    return a0.distance(b0) <= eps && a1.distance(b1) <= eps
        || a0.distance(b1) <= eps && a1.distance(b0) <= eps;
  }

  private static final class HalfDisc {
    final Coordinate centre;
    final double r;
    final Coordinate d0;
    final Coordinate d1;
    final Coordinate mid;
    HalfDisc(Coordinate centre, double r, Coordinate d0, Coordinate d1,
        Coordinate mid) {
      this.centre = centre;
      this.r = r;
      this.d0 = d0;
      this.d1 = d1;
      this.mid = mid;
    }
  }
}

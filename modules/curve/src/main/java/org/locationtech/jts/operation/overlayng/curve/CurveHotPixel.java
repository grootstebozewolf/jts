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
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.locationtech.jts.geom.Coordinate;

/**
 * One hot pixel around one node. Package-private -- not a public
 * API, not a noder, not core {@code HotPixel}. The square / half-open
 * / scale contract matches snap-rounding: width {@code 1/scale},
 * interior plus left and bottom, minus top and right. Intersection
 * is tested in the scaled (squared) metric.
 * <p>
 * A circular leave is not a segment. {@code intersects} of an arc
 * is circle–square / arc–AABB in that scaled space, then restricted
 * to the sweep. It is not the supporting chord, not
 * {@code HotPixel.intersectsScaled}, and not a densified polyline.
 * This rung does not snap leave-angles and does not walk faces.
 */
final class CurveHotPixel {

  private static final double TOLERANCE = 0.5;
  private static final double EPS = 1.0e-12;

  private final Coordinate originalPt;
  private final double scaleFactor;
  private final double hpx;
  private final double hpy;

  /**
   * Pixel centred on a (already rounded) node, of width
   * {@code 1/scaleFactor}. Scale must be strictly positive.
   */
  CurveHotPixel(Coordinate pt, double scaleFactor) {
    if (scaleFactor <= 0.0) {
      throw new IllegalArgumentException("Scale factor must be non-zero");
    }
    this.originalPt = pt;
    this.scaleFactor = scaleFactor;
    if (scaleFactor != 1.0) {
      this.hpx = Math.round(pt.getX() * scaleFactor);
      this.hpy = Math.round(pt.getY() * scaleFactor);
    }
    else {
      this.hpx = pt.getX();
      this.hpy = pt.getY();
    }
  }

  Coordinate getCoordinate() {
    return originalPt;
  }

  double getScaleFactor() {
    return scaleFactor;
  }

  /** Width of the tolerance square in the original coordinates. */
  double getWidth() {
    return 1.0 / scaleFactor;
  }

  /**
   * Point in the half-open pixel. Same sides as core HotPixel:
   * left and bottom closed, top and right open.
   */
  boolean intersects(Coordinate p) {
    if (p == null) return false;
    double x = p.x * scaleFactor;
    double y = p.y * scaleFactor;
    if (x >= hpx + TOLERANCE) return false;
    if (x < hpx - TOLERANCE) return false;
    if (y >= hpy + TOLERANCE) return false;
    if (y < hpy - TOLERANCE) return false;
    return true;
  }

  /**
   * Arc ∩ pixel in the squared metric, or chord ∩ pixel by a
   * closed-form clip. Not {@code intersectsScaled} on a chord
   * standing in for an arc.
   */
  boolean intersects(CurveSegmentString s) {
    if (s == null) return false;
    if (s.isDegenerate()) {
      return intersects(s.getStart());
    }
    if (s.isArc()) {
      return intersectsArc(s);
    }
    return intersectsChord(s.getStart(), s.getEnd());
  }

  /**
   * Circle–square in scaled space ({@code d²_min ≤ r² ≤ d²_max}
   * on the closed box), then a sweep-restricted witness in the
   * half-open pixel. Controls and the node itself are the first
   * witnesses; a clip that only touches the open top or right
   * is a miss.
   */
  private boolean intersectsArc(CurveSegmentString s) {
    if (intersects(s.getStart()) || intersects(s.getMid())
        || intersects(s.getEnd())) {
      return true;
    }
    double[] c = s.asEdge().circle;
    if (c == null || c[2] <= 0.0) {
      return intersectsChord(s.getStart(), s.getEnd());
    }
    if (!circleHitsClosed(c[0], c[1], c[2])) {
      return false;
    }
    List<Coordinate> hits = edgeHits(c[0], c[1], c[2]);
    addClosestOnBox(hits, c[0], c[1], c[2]);
    if (hits.size() > 1) {
      sortByAngle(hits, c[0], c[1]);
    }
    boolean hit = false;
    int n = hits.size();
    for (int i = 0; i < n && !hit; i++) {
      Coordinate p = hits.get(i);
      if (onSweep(p, s) && intersects(p)) {
        hit = true;
      }
      if (!hit && n >= 2) {
        Coordinate q = hits.get((i + 1) % n);
        double a0 = Math.atan2(p.y - c[1], p.x - c[0]);
        double a1 = Math.atan2(q.y - c[1], q.x - c[0]);
        Coordinate mid = TwoNodeClip.midOnCircle(c[0], c[1], c[2], a0,
            TwoNodeClip.normPos(a1 - a0));
        if (inClosed(mid) && onSweep(mid, s) && intersects(mid)) {
          hit = true;
        }
      }
    }
    return hit;
  }

  /**
   * Closed-box circle test in the scaled metric. No square root:
   * compare squared distances to r². A miss here is a miss of
   * the half-open pixel.
   */
  private boolean circleHitsClosed(double cx, double cy, double r) {
    double scx = cx * scaleFactor;
    double scy = cy * scaleFactor;
    double sr = r * scaleFactor;
    double r2 = sr * sr;
    double minx = hpx - TOLERANCE;
    double maxx = hpx + TOLERANCE;
    double miny = hpy - TOLERANCE;
    double maxy = hpy + TOLERANCE;
    double qx = scx < minx ? minx : (scx > maxx ? maxx : scx);
    double qy = scy < miny ? miny : (scy > maxy ? maxy : scy);
    double dx = scx - qx;
    double dy = scy - qy;
    double d2min = dx * dx + dy * dy;
    if (d2min > r2) return false;
    double farx = Math.max(scx - minx, maxx - scx);
    double fary = Math.max(scy - miny, maxy - scy);
    return farx * farx + fary * fary >= r2;
  }

  private List<Coordinate> edgeHits(double cx, double cy, double r) {
    double xmin = (hpx - TOLERANCE) / scaleFactor;
    double xmax = (hpx + TOLERANCE) / scaleFactor;
    double ymin = (hpy - TOLERANCE) / scaleFactor;
    double ymax = (hpy + TOLERANCE) / scaleFactor;
    Coordinate ll = new Coordinate(xmin, ymin);
    Coordinate lr = new Coordinate(xmax, ymin);
    Coordinate ul = new Coordinate(xmin, ymax);
    Coordinate ur = new Coordinate(xmax, ymax);
    List<Coordinate> hits = new ArrayList<Coordinate>();
    addHits(hits, TwoNodeClip.intersectSegmentCircle(cx, cy, r, ll, ul));
    addHits(hits, TwoNodeClip.intersectSegmentCircle(cx, cy, r, lr, ur));
    addHits(hits, TwoNodeClip.intersectSegmentCircle(cx, cy, r, ll, lr));
    addHits(hits, TwoNodeClip.intersectSegmentCircle(cx, cy, r, ul, ur));
    return hits;
  }

  /**
   * Closest point of the closed box to the circle centre, when
   * that point lies on the circle. Covers a numerical tangent
   * the edge quadratic might miss.
   */
  private void addClosestOnBox(List<Coordinate> hits, double cx,
      double cy, double r) {
    double xmin = (hpx - TOLERANCE) / scaleFactor;
    double xmax = (hpx + TOLERANCE) / scaleFactor;
    double ymin = (hpy - TOLERANCE) / scaleFactor;
    double ymax = (hpy + TOLERANCE) / scaleFactor;
    double qx = cx < xmin ? xmin : (cx > xmax ? xmax : cx);
    double qy = cy < ymin ? ymin : (cy > ymax ? ymax : cy);
    double dx = qx - cx;
    double dy = qy - cy;
    double d2 = dx * dx + dy * dy;
    if (Math.abs(d2 - r * r) <= EPS) {
      addUnique(hits, new Coordinate(qx, qy));
    }
  }

  private boolean intersectsChord(Coordinate a, Coordinate b) {
    if (intersects(a) || intersects(b)) return true;
    double ax = a.x * scaleFactor;
    double ay = a.y * scaleFactor;
    double dx = (b.x - a.x) * scaleFactor;
    double dy = (b.y - a.y) * scaleFactor;
    double[] t = clipClosed(ax, ay, dx, dy);
    if (t == null) return false;
    double tm = 0.5 * (t[0] + t[1]);
    return intersects(new Coordinate(a.x + tm * (b.x - a.x),
        a.y + tm * (b.y - a.y)));
  }

  /**
   * Liang-Barsky against the closed scaled box. Not the core
   * integer-domain orientation walk.
   */
  private double[] clipClosed(double ax, double ay, double dx,
      double dy) {
    double[] t = new double[] { 0.0, 1.0 };
    if (!clipEdge(-dx, ax - (hpx - TOLERANCE), t)) return null;
    if (!clipEdge(dx, (hpx + TOLERANCE) - ax, t)) return null;
    if (!clipEdge(-dy, ay - (hpy - TOLERANCE), t)) return null;
    if (!clipEdge(dy, (hpy + TOLERANCE) - ay, t)) return null;
    return t;
  }

  private static boolean clipEdge(double p, double q, double[] t) {
    if (p == 0.0) {
      return q >= 0.0;
    }
    double r = q / p;
    boolean ok = true;
    if (p < 0.0) {
      if (r > t[1]) {
        ok = false;
      }
      else if (r > t[0]) {
        t[0] = r;
      }
    }
    else if (r < t[0]) {
      ok = false;
    }
    else if (r < t[1]) {
      t[1] = r;
    }
    return ok;
  }

  private boolean inClosed(Coordinate p) {
    double x = p.x * scaleFactor;
    double y = p.y * scaleFactor;
    if (x > hpx + TOLERANCE) return false;
    if (x < hpx - TOLERANCE) return false;
    if (y > hpy + TOLERANCE) return false;
    if (y < hpy - TOLERANCE) return false;
    return true;
  }

  private static boolean onSweep(Coordinate p, CurveSegmentString s) {
    TwoNodeClip.Edge e = s.asEdge();
    return TwoNodeClip.isOnSweep(p, e.circle, e.a, e.mid, e.b);
  }

  private static void addHits(List<Coordinate> dest, Coordinate[] xs) {
    for (int i = 0; i < xs.length; i++) {
      addUnique(dest, xs[i]);
    }
  }

  private static void addUnique(List<Coordinate> dest, Coordinate p) {
    boolean seen = false;
    for (int i = 0; i < dest.size() && !seen; i++) {
      if (dest.get(i).distance(p) <= EPS) {
        seen = true;
      }
    }
    if (!seen) {
      dest.add(p);
    }
  }

  private static void sortByAngle(List<Coordinate> pts, final double cx,
      final double cy) {
    Collections.sort(pts, new Comparator<Coordinate>() {
      public int compare(Coordinate p, Coordinate q) {
        return Double.compare(
            Math.atan2(p.y - cy, p.x - cx),
            Math.atan2(q.y - cy, q.x - cx));
      }
    });
  }
}

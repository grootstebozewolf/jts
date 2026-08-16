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
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.geom.curve.CurvePolygon;
import org.locationtech.jts.geom.curve.MultiSurface;

/**
 * Nodes and shared edges. Intersects {@link CurveSegmentString}s
 * and returns the discrete node set the kits already compute, or
 * {@code null}. A shared run is an {@linkplain #edges edge}
 * (interval), not a pair of endpoints. Package-private -- not a
 * core {@code Noder}, not N-SS, not a public API, not a face walker.
 * <p>
 * MIXED (collinear overlap) is not a discrete node set; {@link #nodes}
 * stays {@code null} and {@link #edges} names the interval.
 * A tangent pinch (TOUCH-ext, H-ANNULUS-TANGENT) is a zero-length
 * edge, not a face. Overlay still goes through the existing kits.
 * Densify is never a noder.
 */
final class CurveSegmentNoder {

  private CurveSegmentNoder() { }

  /**
   * Discrete nodes of a circular pair, or {@code null}. Holes,
   * collinear overlap, pinch, and pairs the strings cannot name
   * stay {@code null}. The shared run lives on {@link #edges}.
   */
  static Coordinate[] nodes(Geometry a, Geometry b) {
    if (hasHole(a) || hasHole(b)) return null;
    List<CurveSegmentString> sa = CurveSegmentString.of(a);
    List<CurveSegmentString> sb = CurveSegmentString.of(b);
    if (sa == null || sb == null) return null;
    double scale = scaleOf(a, b);
    return nodes(sa, sb, scale);
  }

  /**
   * Discrete nodes of two string collections. {@code null} is MIXED
   * (or fewer than two distinct hits).
   */
  static Coordinate[] nodes(List<CurveSegmentString> a,
      List<CurveSegmentString> b, double scale) {
    if (a == null || b == null || a.isEmpty() || b.isEmpty()) {
      return null;
    }
    List<Coordinate> hits = new ArrayList<Coordinate>();
    boolean miss = false;
    for (int i = 0; i < a.size() && !miss; i++) {
      for (int j = 0; j < b.size() && !miss; j++) {
        Coordinate[] xs = CurveSegmentString.intersect(a.get(i), b.get(j),
            scale);
        if (xs == null) {
          miss = true;
        }
        else {
          addUnique(hits, xs, scale);
        }
      }
    }
    if (miss) return null;
    if (hits.size() < 2) return null;
    if (hits.size() == 2
        && hits.get(0).distance(hits.get(1))
            < TwoNodeClip.PROPER_CROSS_FRAC * Math.max(scale, 1.0)) {
      return null;
    }
    return hits.toArray(new Coordinate[0]);
  }

  /**
   * Shared runs of a circular pair. Empty is no interval and no
   * pinch. {@code null} is a hole (P2.3 / P2.4) or a pair the
   * strings cannot name. Does not assemble a face.
   */
  static List<CurveSegmentString> edges(Geometry a, Geometry b) {
    if (hasHole(a) || hasHole(b)) return null;
    List<CurveSegmentString> sa = CurveSegmentString.of(a);
    List<CurveSegmentString> sb = CurveSegmentString.of(b);
    if (sa == null || sb == null) return null;
    return edges(sa, sb, scaleOf(a, b));
  }

  /**
   * Shared runs of two string collections. Empty is no shared edge.
   */
  static List<CurveSegmentString> edges(List<CurveSegmentString> a,
      List<CurveSegmentString> b, double scale) {
    List<CurveSegmentString> out = new ArrayList<CurveSegmentString>();
    if (a == null || b == null || a.isEmpty() || b.isEmpty()) {
      return out;
    }
    for (int i = 0; i < a.size(); i++) {
      for (int j = 0; j < b.size(); j++) {
        List<CurveSegmentString> shared = CurveSegmentString.shared(
            a.get(i), b.get(j), scale);
        for (int k = 0; k < shared.size(); k++) {
          addUniqueEdge(out, shared.get(k), scale);
        }
      }
    }
    return out;
  }

  private static void addUnique(List<Coordinate> hits, Coordinate[] xs,
      double scale) {
    double eps = Math.max(TwoNodeClip.PROPER_CROSS_FRAC * scale, 1.0e-12);
    for (int k = 0; k < xs.length; k++) {
      boolean seen = false;
      for (int i = 0; i < hits.size() && !seen; i++) {
        if (hits.get(i).distance(xs[k]) <= eps) {
          seen = true;
        }
      }
      if (!seen) {
        hits.add(xs[k]);
      }
    }
  }

  private static void addUniqueEdge(List<CurveSegmentString> out,
      CurveSegmentString e, double scale) {
    double eps = Math.max(TwoNodeClip.PROPER_CROSS_FRAC * scale, 1.0e-12);
    boolean seen = false;
    for (int i = 0; i < out.size() && !seen; i++) {
      if (sameEdge(out.get(i), e, eps, scale)) {
        seen = true;
      }
    }
    if (!seen) {
      out.add(e);
    }
  }

  private static boolean sameEdge(CurveSegmentString p, CurveSegmentString q,
      double eps, double scale) {
    if (p.isArc() != q.isArc()) return false;
    boolean ends = (p.getStart().distance(q.getStart()) <= eps
            && p.getEnd().distance(q.getEnd()) <= eps)
        || (p.getStart().distance(q.getEnd()) <= eps
            && p.getEnd().distance(q.getStart()) <= eps);
    if (!ends) return false;
    if (!p.isArc()) return true;
    return CurveSegmentString.sameCircle(p, q, scale);
  }

  private static boolean hasHole(Geometry g) {
    Geometry geom = unwrap(g);
    if (geom instanceof CurvePolygon) {
      return ((CurvePolygon) geom).getNumInteriorRing() > 0;
    }
    if (geom instanceof Polygon) {
      return ((Polygon) geom).getNumInteriorRing() > 0;
    }
    return false;
  }

  private static Geometry unwrap(Geometry g) {
    if (g == null || g.isEmpty()) return null;
    if (g instanceof MultiSurface) {
      if (g.getNumGeometries() != 1) return null;
      return unwrap(g.getGeometryN(0));
    }
    return g;
  }

  private static double scaleOf(Geometry a, Geometry b) {
    double wa = Math.max(a.getEnvelopeInternal().getWidth(),
        a.getEnvelopeInternal().getHeight());
    double wb = Math.max(b.getEnvelopeInternal().getWidth(),
        b.getEnvelopeInternal().getHeight());
    return Math.max(Math.max(wa, wb), 1.0);
  }
}

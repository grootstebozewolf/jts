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

import org.locationtech.jts.algorithm.locate.SimplePointInAreaLocator;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Location;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.geom.curve.CurvePolygon;

/**
 * Closed-form overlay of one circular disc and one plain Polygon.
 * Package-private -- not a new public API, and not a noder.
 * <p>
 * Lives next to {@link OverlayNGCurve} so the ratchet can call it without
 * a public bridge (the same reason {@link CircularDiscOverlay} is here
 * rather than in {@code geom.curve}). Two-disc pairs stay on
 * {@link CircularDiscOverlay}; this class does not overload that path.
 * <p>
 * Two proper line–circle nodes become a {@link CurvePolygon} whose shell
 * is the surviving circular arc plus the polygon walk between the nodes
 * (CAP the clip, CUP the blob, SUB a bite or a cap, XOR both), assembled
 * by {@link TwoNodeClip}. Anything else -- not this shape pair, holes,
 * 0 / 1 / 3+ nodes -- returns {@code null} so the caller can take the
 * chord baseline without paying this path first.
 */
final class CircularDiscPolygonOverlay {

  private CircularDiscPolygonOverlay() { }

  /**
   * Exact overlay of a circular disc and a plain polygon, or {@code null}
   * if this class cannot answer. The cheap shape check runs first; a miss
   * does not intersect and does not node.
   */
  static Geometry overlay(Geometry a, Geometry b, int opCode) {
    double[] disc = CircularDiscOverlay.centreRadius(a);
    Geometry poly = b;
    boolean discFirst = true;
    if (disc == null) {
      disc = CircularDiscOverlay.centreRadius(b);
      poly = a;
      discFirst = false;
    }
    if (disc == null || !TwoNodeClip.isPlainPolygon(poly)) return null;

    Clip clip = clip(disc[0], disc[1], disc[2], (Polygon) poly);
    if (clip == null) return null;

    GeometryFactory f = TwoNodeClip.curveFactory(a);
    List<LineString> discIn = TwoNodeClip.listOf(
        TwoNodeClip.arc(clip.p, clip.midIn, clip.q, f));
    List<LineString> discOut = TwoNodeClip.listOf(
        TwoNodeClip.arc(clip.p, clip.midOut, clip.q, f));
    List<LineString> polyIn = TwoNodeClip.listOf(
        TwoNodeClip.asLine(clip.pathIn, f));
    List<LineString> polyOut = TwoNodeClip.listOf(
        TwoNodeClip.asLine(clip.pathOut, f));
    return TwoNodeClip.overlay(opCode, discFirst, discIn, discOut, polyIn,
        polyOut, clip.p, clip.q, f, clip.r);
  }

  private static Clip clip(double cx, double cy, double r, Polygon poly) {
    Coordinate[] ring = poly.getExteriorRing().getCoordinates();
    if (ring.length < 4) return null;
    int n = ring.length - 1;
    List<TwoNodeClip.Node> nodes = new ArrayList<TwoNodeClip.Node>();
    for (int i = 0; i < n; i++) {
      Coordinate[] hits = TwoNodeClip.intersectSegmentCircle(
          cx, cy, r, ring[i], ring[i + 1]);
      for (int k = 0; k < hits.length; k++) {
        TwoNodeClip.addUnique(nodes, new TwoNodeClip.Node(i,
            TwoNodeClip.parameter(ring[i], ring[i + 1], hits[k]), hits[k]), r);
      }
    }
    if (!TwoNodeClip.properPair(nodes, r)) return null;

    TwoNodeClip.Node p = nodes.get(0);
    TwoNodeClip.Node q = nodes.get(1);
    List<Coordinate> pq = TwoNodeClip.walkRing(ring, p, q, r);
    List<Coordinate> qp = TwoNodeClip.walkRing(ring, q, p, r);
    if (pq == null || qp == null) return null;
    int pqSide = TwoNodeClip.sideOfDiscPath(pq, cx, cy, r);
    int qpSide = TwoNodeClip.sideOfDiscPath(qp, cx, cy, r);
    if (pqSide == TwoNodeClip.MIXED || qpSide == TwoNodeClip.MIXED
        || pqSide == qpSide) {
      return null;
    }
    List<Coordinate> pathIn = pqSide == TwoNodeClip.IN ? pq : qp;
    List<Coordinate> pathOut = pqSide == TwoNodeClip.IN ? qp : pq;

    Coordinate midIn = TwoNodeClip.sweepMid(p.pt, q.pt, cx, cy, r, true,
        insidePoly(poly));
    Coordinate midOut = TwoNodeClip.sweepMid(p.pt, q.pt, cx, cy, r, false,
        insidePoly(poly));
    if (midIn == null || midOut == null) return null;
    if (midIn.distance(p.pt) < TwoNodeClip.PROPER_CROSS_FRAC * r
        || midIn.distance(q.pt) < TwoNodeClip.PROPER_CROSS_FRAC * r
        || midOut.distance(p.pt) < TwoNodeClip.PROPER_CROSS_FRAC * r
        || midOut.distance(q.pt) < TwoNodeClip.PROPER_CROSS_FRAC * r) {
      return null;
    }
    return new Clip(p.pt, q.pt, midIn, midOut, pathIn, pathOut, r);
  }

  private static TwoNodeClip.Side insidePoly(final Polygon poly) {
    return new TwoNodeClip.Side() {
      public boolean inside(Coordinate c) {
        return SimplePointInAreaLocator.locate(c, poly) == Location.INTERIOR;
      }
    };
  }

  private static final class Clip {
    final Coordinate p, q, midIn, midOut;
    final List<Coordinate> pathIn, pathOut;
    final double r;

    Clip(Coordinate p, Coordinate q, Coordinate midIn, Coordinate midOut,
        List<Coordinate> pathIn, List<Coordinate> pathOut, double r) {
      this.p = p;
      this.q = q;
      this.midIn = midIn;
      this.midOut = midOut;
      this.pathIn = pathIn;
      this.pathOut = pathOut;
      this.r = r;
    }
  }
}

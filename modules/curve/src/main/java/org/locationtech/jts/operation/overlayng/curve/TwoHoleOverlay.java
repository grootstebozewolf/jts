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
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.geom.curve.CurvePolygon;
import org.locationtech.jts.geom.curve.MultiSurface;
import org.locationtech.jts.operation.overlayng.OverlayNG;

/**
 * Two-hole arrangement on a shared CompoundCurve outer
 * ({@code H-SHELL-HOLE-X}). The noder names the two hole–hole
 * nodes; this rung walks each ring in and out of the other and
 * assembles the four hole faces. Overlay then punches the shared
 * shell (CAP / CUP) or returns the hole difference (SUB / XOR).
 * <p>
 * Same-outer non-crossing holes stay on {@link SameOuterHoleOverlay}.
 * A straddling hole is {@link BiteVsHole}. Four-or-more nodes,
 * collinear overlap, or a pair that would need snap-rounding stay
 * {@code null}. Not a noder, not N-SS, not a general arrangement.
 */
final class TwoHoleOverlay {

  static final int CROSS = 1;
  static final int MISS = 0;

  private TwoHoleOverlay() { }

  /**
   * Exact overlay, or {@code null} if the two holes do not form a
   * certified two-node crossing on the same outer.
   */
  static Geometry overlay(Geometry a, Geometry b, int opCode) {
    Walk walk = walk(a, b);
    if (walk == null) return null;
    GeometryFactory f = TwoNodeClip.curveFactory(a);
    if (opCode == OverlayNG.INTERSECTION) {
      return punch(walk.outer, walk.holeCup, f);
    }
    if (opCode == OverlayNG.UNION) {
      return punch(walk.outer, walk.holeCap, f);
    }
    if (opCode == OverlayNG.DIFFERENCE) {
      return walk.holeBA;
    }
    if (opCode == OverlayNG.SYMDIFFERENCE) {
      return join(walk.holeAB, walk.holeBA, f);
    }
    return null;
  }

  /**
   * {@link #CROSS} when the two hole rings meet at two proper nodes
   * and each has an in-walk and an out-walk. {@link #MISS} otherwise.
   */
  static int decide(Geometry a, Geometry b) {
    Walk walk = walk(a, b);
    return walk == null ? MISS : CROSS;
  }

  /**
   * The two hole–hole nodes, or {@code null}. Not a face.
   */
  static Coordinate[] clipNodes(Geometry a, Geometry b) {
    Walk walk = walk(a, b);
    if (walk == null) return null;
    return new Coordinate[] { new Coordinate(walk.p), new Coordinate(walk.q) };
  }

  private static Walk walk(Geometry a, Geometry b) {
    CurvePolygon ca = SameOuterHoleOverlay.mixedShell(a);
    CurvePolygon cb = SameOuterHoleOverlay.mixedShell(b);
    if (ca == null || cb == null) return null;
    if (ca.getNumInteriorRing() != 1 || cb.getNumInteriorRing() != 1) {
      return null;
    }
    if (!ca.getExteriorCurve().equalsExact(cb.getExteriorCurve())) {
      return null;
    }
    LineString holeA = SameOuterHoleOverlay.plainHole(ca);
    LineString holeB = SameOuterHoleOverlay.plainHole(cb);
    if (holeA == null || holeB == null) return null;
    if (BiteVsHole.decide(a, b) != BiteVsHole.MISS) return null;

    GeometryFactory f = TwoNodeClip.curveFactory(a);
    CurvePolygon outer = new CurvePolygon(ca.getExteriorCurve(), null, f);
    if (!SameOuterHoleOverlay.holeInsideShell(ca, outer)) return null;
    if (!SameOuterHoleOverlay.holeInsideShell(cb, outer)) return null;

    List<CurveSegmentString> sa = CurveSegmentString.of(holeA);
    List<CurveSegmentString> sb = CurveSegmentString.of(holeB);
    if (sa == null || sb == null) return null;
    double scale = scaleOf(ca, cb);
    Coordinate[] named = CurveSegmentNoder.nodes(sa, sb, scale);
    // Two proper nodes. MIXED / pinch / 4+ is a different cell.
    if (named == null || named.length != 2) return null;
    if (named[0].distance(named[1])
        < TwoNodeClip.PROPER_CROSS_FRAC * scale) {
      return null;
    }
    List<CurveSegmentString> shared = CurveSegmentNoder.edges(sa, sb, scale);
    if (shared == null || !shared.isEmpty()) return null;

    Coordinate[] ringA = holeA.getCoordinates();
    Coordinate[] ringB = holeB.getCoordinates();
    List<Coordinate> aPQ = TwoNodeClip.walkRing(ringA, named[0], named[1]);
    List<Coordinate> aQP = TwoNodeClip.walkRing(ringA, named[1], named[0]);
    List<Coordinate> bPQ = TwoNodeClip.walkRing(ringB, named[0], named[1]);
    List<Coordinate> bQP = TwoNodeClip.walkRing(ringB, named[1], named[0]);
    if (aPQ == null || aQP == null || bPQ == null || bQP == null) {
      return null;
    }

    Polygon polyA = asPolygon(holeA, f);
    Polygon polyB = asPolygon(holeB, f);
    if (polyA == null || polyB == null) return null;
    List<Coordinate> aIn = inWalk(aPQ, aQP, polyB);
    List<Coordinate> aOut = outWalk(aPQ, aQP, polyB);
    List<Coordinate> bIn = inWalk(bPQ, bQP, polyA);
    List<Coordinate> bOut = outWalk(bPQ, bQP, polyA);
    if (aIn == null || aOut == null || bIn == null || bOut == null) {
      return null;
    }

    Coordinate p = aIn.get(0);
    Coordinate q = aIn.get(aIn.size() - 1);
    List<LineString> aInLs = TwoNodeClip.listOf(TwoNodeClip.asLine(aIn, f));
    List<LineString> aOutLs = TwoNodeClip.listOf(TwoNodeClip.asLine(aOut, f));
    List<LineString> bInLs = TwoNodeClip.listOf(TwoNodeClip.asLine(bIn, f));
    List<LineString> bOutLs = TwoNodeClip.listOf(TwoNodeClip.asLine(bOut, f));
    Geometry holeCap = TwoNodeClip.ring(aInLs, bInLs, p, q, f, scale);
    Geometry holeCup = TwoNodeClip.ring(aOutLs, bOutLs, p, q, f, scale);
    Geometry holeAB = TwoNodeClip.ring(aOutLs, bInLs, p, q, f, scale);
    Geometry holeBA = TwoNodeClip.ring(bOutLs, aInLs, p, q, f, scale);
    if (holeCap == null || holeCup == null
        || holeAB == null || holeBA == null) {
      return null;
    }
    if (holeCap.getArea() <= 0.0 || holeCup.getArea() <= 0.0
        || holeAB.getArea() <= 0.0 || holeBA.getArea() <= 0.0) {
      return null;
    }

    Walk w = new Walk();
    w.outer = outer;
    w.p = p;
    w.q = q;
    w.holeCap = holeCap;
    w.holeCup = holeCup;
    w.holeAB = holeAB;
    w.holeBA = holeBA;
    return w;
  }

  private static List<Coordinate> inWalk(List<Coordinate> pq,
      List<Coordinate> qp, Polygon other) {
    int pqSide = sideOf(pq, other);
    int qpSide = sideOf(qp, other);
    if (pqSide == TwoNodeClip.IN && qpSide == TwoNodeClip.OUT) return pq;
    if (qpSide == TwoNodeClip.IN && pqSide == TwoNodeClip.OUT) return qp;
    return null;
  }

  private static List<Coordinate> outWalk(List<Coordinate> pq,
      List<Coordinate> qp, Polygon other) {
    int pqSide = sideOf(pq, other);
    int qpSide = sideOf(qp, other);
    if (pqSide == TwoNodeClip.OUT && qpSide == TwoNodeClip.IN) return pq;
    if (qpSide == TwoNodeClip.OUT && pqSide == TwoNodeClip.IN) return qp;
    return null;
  }

  private static int sideOf(List<Coordinate> path, Polygon poly) {
    if (path == null || path.size() < 2) return TwoNodeClip.MIXED;
    Coordinate sample;
    if (path.size() >= 3) {
      sample = path.get(path.size() / 2);
    }
    else {
      sample = new Coordinate(0.5 * (path.get(0).x + path.get(1).x),
          0.5 * (path.get(0).y + path.get(1).y));
    }
    return TwoNodeClip.sideOfPolygon(sample, poly);
  }

  private static Geometry punch(CurvePolygon outer, Geometry holeFace,
      GeometryFactory f) {
    if (outer == null || holeFace == null) return null;
    LineString ring = holeRing(holeFace);
    if (ring == null) return null;
    return new CurvePolygon(outer.getExteriorCurve(),
        new LineString[] { ring }, f);
  }

  private static LineString holeRing(Geometry face) {
    if (face == null || face.isEmpty() || face.getNumGeometries() != 1) {
      return null;
    }
    Geometry g = face.getGeometryN(0);
    if (g instanceof CurvePolygon) {
      LineString sh = ((CurvePolygon) g).getExteriorCurve();
      if (sh == null || sh.isEmpty()) return null;
      return sh;
    }
    if (g instanceof Polygon) {
      return ((Polygon) g).getExteriorRing();
    }
    return null;
  }

  private static Polygon asPolygon(LineString hole, GeometryFactory f) {
    try {
      return f.createPolygon(f.createLinearRing(hole.getCoordinates()));
    }
    catch (RuntimeException ex) {
      return null;
    }
  }

  private static Geometry join(Geometry a, Geometry extra, GeometryFactory f) {
    if (extra == null) return null;
    if (a == null || a.isEmpty()) return extra;
    if (extra.isEmpty()) return a;
    List<Polygon> faces = new ArrayList<Polygon>();
    if (!addFaces(faces, a) || !addFaces(faces, extra)) return null;
    if (faces.isEmpty()) return null;
    if (faces.size() == 1) return faces.get(0);
    return new MultiSurface(faces.toArray(new Polygon[0]), f);
  }

  private static boolean addFaces(List<Polygon> dest, Geometry g) {
    boolean ok = true;
    for (int i = 0; i < g.getNumGeometries() && ok; i++) {
      Geometry p = g.getGeometryN(i);
      if (p instanceof Polygon && !p.isEmpty()) {
        dest.add((Polygon) p);
      }
      else {
        ok = false;
      }
    }
    return ok;
  }

  private static double scaleOf(Geometry a, Geometry b) {
    double wa = Math.max(a.getEnvelopeInternal().getWidth(),
        a.getEnvelopeInternal().getHeight());
    double wb = Math.max(b.getEnvelopeInternal().getWidth(),
        b.getEnvelopeInternal().getHeight());
    return Math.max(Math.max(wa, wb), 1.0);
  }

  private static final class Walk {
    CurvePolygon outer;
    Coordinate p;
    Coordinate q;
    Geometry holeCap;
    Geometry holeCup;
    Geometry holeAB;
    Geometry holeBA;
  }
}

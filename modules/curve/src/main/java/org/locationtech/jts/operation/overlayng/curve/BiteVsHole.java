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
import org.locationtech.jts.geom.LinearRing;
import org.locationtech.jts.geom.Location;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.geom.curve.CircularString;
import org.locationtech.jts.geom.curve.CompoundCurve;
import org.locationtech.jts.geom.curve.CurvePolygon;
import org.locationtech.jts.geom.curve.MultiSurface;
import org.locationtech.jts.operation.overlayng.OverlayNG;

/**
 * First face walk: bite versus hole. One predicate -- if the new
 * edge of {@code hole ∩ other} is a subset of the other shell, it
 * is a bite, not an interior punch ({@code H-SHELL-HOLE-CROSS}).
 * A hole that does not cross, but whose ring overlaps the other
 * shell ({@code H-SHELL-HOLE-OUTER}: hole-edge ⊂ other.shell),
 * is the same bite. {@link CurveSegmentNoder#edges} already names
 * that shared run; there are no crossing nodes.
 * <p>
 * The noder already names the two hole–shell nodes of a straddle.
 * This rung walks those into the clip edge on the other shell, or
 * walks the shared edge when the hole sits entirely in the solid.
 * Overlay then splices the bite (SUB / XOR face) or punches the
 * leftover hole. Two holes that cross ({@code H-SHELL-HOLE-X})
 * are {@link TwoHoleOverlay}. A pair this walk cannot certify
 * keeps the named miss. Not a noder, not N-SS.
 */
final class BiteVsHole {

  static final int BITE = 1;
  static final int HOLE = -1;
  static final int MISS = 0;

  private BiteVsHole() { }

  /**
   * True when a polygonal result still carries a curve shell (not a
   * densified control polygon). Used to refuse bite certification when
   * the outer CAP has already fallen to chords.
   */
  private static boolean keepsCurveShell(Geometry g) {
    if (g == null || g.isEmpty()) {
      return false;
    }
    if (g instanceof CurvePolygon) {
      LineString shell = ((CurvePolygon) g).getExteriorCurve();
      return shell instanceof CircularString || shell instanceof CompoundCurve;
    }
    if (g instanceof MultiSurface) {
      for (int i = 0; i < g.getNumGeometries(); i++) {
        if (keepsCurveShell(g.getGeometryN(i))) {
          return true;
        }
      }
    }
    return false;
  }

  /**
   * Exact overlay, or {@code null} if the walk cannot certify bite
   * versus hole. Same-outer and strictly-inside / strictly-outside
   * different-outer cells stay on those kits.
   */
  static Geometry overlay(Geometry a, Geometry b, int opCode) {
    Walk walk = walk(a, b);
    if (walk == null || walk.kind != BITE) return null;
    GeometryFactory f = TwoNodeClip.curveFactory(a);
    CurvePolygon outerHoled = new CurvePolygon(
        walk.holed.getExteriorCurve(), null, f);
    Geometry firstOuter = walk.holedFirst ? outerHoled : walk.solid;
    Geometry secondOuter = walk.holedFirst ? walk.solid : outerHoled;
    if (opCode == OverlayNG.INTERSECTION) {
      Geometry cap = CompoundCurveShellOverlay.overlay(
          firstOuter, secondOuter, OverlayNG.INTERSECTION);
      // Refuse when the outer CAP densifies: cannot certify an arc-honest
      // bite (H-SHELL-HOLE-OUTER on a complementary diameter stays a miss).
      if (!keepsCurveShell(cap)) {
        return null;
      }
      return splice(cap, walk.holeIn, walk.p, walk.q, f, walk.scale);
    }
    if (opCode == OverlayNG.UNION) {
      Geometry cup = CompoundCurveShellOverlay.overlay(
          firstOuter, secondOuter, OverlayNG.UNION);
      if (walk.leftover == null || walk.leftover.isEmpty()) {
        return cup;
      }
      return punch(cup, walk.leftover, f);
    }
    if (opCode == OverlayNG.DIFFERENCE) {
      Geometry sub = CompoundCurveShellOverlay.overlay(
          firstOuter, secondOuter, OverlayNG.DIFFERENCE);
      if (walk.holedFirst) {
        return splice(sub, walk.holeOut, walk.p, walk.q, f, walk.scale);
      }
      return join(sub, walk.bite, f);
    }
    if (opCode == OverlayNG.SYMDIFFERENCE) {
      Geometry abOuters = CompoundCurveShellOverlay.overlay(
          firstOuter, secondOuter, OverlayNG.DIFFERENCE);
      Geometry baOuters = CompoundCurveShellOverlay.overlay(
          secondOuter, firstOuter, OverlayNG.DIFFERENCE);
      Geometry ab = walk.holedFirst
          ? splice(abOuters, walk.holeOut, walk.p, walk.q, f, walk.scale)
          : join(abOuters, walk.bite, f);
      Geometry ba = walk.holedFirst
          ? join(baOuters, walk.bite, f)
          : splice(baOuters, walk.holeOut, walk.p, walk.q, f, walk.scale);
      return join(ab, ba, f);
    }
    return null;
  }

  /**
   * {@link #BITE} when the new edge ⊂ other.shell, {@link #HOLE}
   * when the walk says punch, {@link #MISS} when the predicate
   * cannot be certified.
   */
  static int decide(Geometry a, Geometry b) {
    Walk walk = walk(a, b);
    return walk == null ? MISS : walk.kind;
  }

  /**
   * The certified clip edge, or {@code null}. A chord on the other
   * shell; not a face.
   */
  static CurveSegmentString clipEdge(Geometry a, Geometry b) {
    Walk walk = walk(a, b);
    if (walk == null || walk.kind != BITE) return null;
    if (walk.newEdge == null || walk.newEdge.size() != 1) return null;
    LineString e = walk.newEdge.get(0);
    if (e instanceof CircularString) return null;
    Coordinate[] c = e.getCoordinates();
    if (c.length < 2) return null;
    return CurveSegmentString.segment(c[0], c[c.length - 1]);
  }

  private static Walk walk(Geometry a, Geometry b) {
    CurvePolygon ca = SameOuterHoleOverlay.mixedShell(a);
    CurvePolygon cb = SameOuterHoleOverlay.mixedShell(b);
    if (ca == null || cb == null) return null;
    int ha = ca.getNumInteriorRing();
    int hb = cb.getNumInteriorRing();
    CurvePolygon holed = null;
    CurvePolygon solid = null;
    boolean holedFirst = false;
    if (ha == 1 && hb == 0) {
      holed = ca;
      solid = cb;
      holedFirst = true;
    }
    else if (ha == 0 && hb == 1) {
      holed = cb;
      solid = ca;
      holedFirst = false;
    }
    if (holed == null || solid == null) return null;
    if (holed.getExteriorCurve().equalsExact(solid.getExteriorCurve())) {
      return null;
    }
    LineString hole = SameOuterHoleOverlay.plainHole(holed);
    if (hole == null) return null;
    GeometryFactory f = TwoNodeClip.curveFactory(a);
    CurvePolygon outerHoled = new CurvePolygon(
        holed.getExteriorCurve(), null, f);
    if (!SameOuterHoleOverlay.holeInsideShell(holed, outerHoled)) {
      return null;
    }

    List<TwoNodeClip.Edge> shell = TwoNodeClip.flatten(solid);
    if (shell == null) return null;
    Coordinate[] ring = hole.getCoordinates();
    double scale = scaleOf(holed, solid);
    List<CurveSegmentString> holeStr = CurveSegmentString.of(hole);
    List<CurveSegmentString> shellStr = CurveSegmentString.of(solid);
    if (holeStr == null || shellStr == null) return null;
    Coordinate[] named = CurveSegmentNoder.nodes(holeStr, shellStr, scale);
    if (named != null && named.length != 2) return null;
    if (named == null) {
      return walkSharedEdge(holed, solid, hole, holedFirst, scale, f);
    }

    List<TwoNodeClip.Node> nodes = TwoNodeClip.nodesVsPolygon(shell, ring);
    if (nodes == null || nodes.size() != 2) return null;
    if (nodes.get(0).pt.distance(nodes.get(1).pt)
        < TwoNodeClip.PROPER_CROSS_FRAC * scale) {
      return null;
    }

    TwoNodeClip.Node n0 = nodes.get(0);
    TwoNodeClip.Node n1 = nodes.get(1);
    List<Coordinate> pq = TwoNodeClip.walkRing(ring, n0.pt, n1.pt);
    List<Coordinate> qp = TwoNodeClip.walkRing(ring, n1.pt, n0.pt);
    if (pq == null || qp == null) return null;
    int pqSide = sideOfShell(pq, solid);
    int qpSide = sideOfShell(qp, solid);
    List<Coordinate> holeIn = null;
    List<Coordinate> holeOut = null;
    if (pqSide == TwoNodeClip.IN && qpSide == TwoNodeClip.OUT) {
      holeIn = pq;
      holeOut = qp;
    }
    else if (qpSide == TwoNodeClip.IN && pqSide == TwoNodeClip.OUT) {
      holeIn = qp;
      holeOut = pq;
    }
    if (holeIn == null || holeOut == null) return null;

    List<LineString> s01 = TwoNodeClip.walkEdges(shell, n0, n1, f);
    List<LineString> s10 = TwoNodeClip.walkEdges(shell, n1, n0, f);
    if (s01 == null || s10 == null) return null;
    List<LineString> edge01 = clipEdgeOnShell(s01, solid, hole, scale);
    List<LineString> edge10 = clipEdgeOnShell(s10, solid, hole, scale);
    List<LineString> newEdge = null;
    if (edge01 != null && edge10 == null) {
      newEdge = edge01;
    }
    else if (edge10 != null && edge01 == null) {
      newEdge = edge10;
    }
    if (newEdge == null) return null;
    if (lengthOf(newEdge) <= TwoNodeClip.PROPER_CROSS_FRAC * scale) {
      return null;
    }

    Coordinate p = holeIn.get(0);
    Coordinate q = holeIn.get(holeIn.size() - 1);
    List<LineString> oriented = directed(newEdge, q, p, scale, f);
    if (oriented == null) return null;

    Geometry bite = closePlain(holeIn, oriented, f, scale);
    Geometry leftover = closePlain(holeOut, oriented, f, scale);
    if (bite == null || leftover == null) return null;
    Walk w = new Walk();
    w.kind = BITE;
    w.holed = holed;
    w.solid = solid;
    w.holedFirst = holedFirst;
    w.holeIn = holeIn;
    w.holeOut = holeOut;
    w.newEdge = oriented;
    w.p = p;
    w.q = q;
    w.bite = bite;
    w.leftover = leftover;
    w.scale = scale;
    return w;
  }

  /**
   * Hole-edge ⊂ other.shell, no crossing nodes. P2.2 already names
   * the shared run. The hole sits entirely in the solid, so the
   * shared edge is the clip and the rest of the ring is a bite.
   * CUP is the outer union: the solid fills the hole.
   */
  private static Walk walkSharedEdge(CurvePolygon holed, CurvePolygon solid,
      LineString hole, boolean holedFirst, double scale, GeometryFactory f) {
    List<CurveSegmentString> holeStr = CurveSegmentString.of(hole);
    List<CurveSegmentString> shellStr = CurveSegmentString.of(solid);
    if (holeStr == null || shellStr == null) return null;
    List<CurveSegmentString> shared = CurveSegmentNoder.edges(holeStr,
        shellStr, scale);
    CurveSegmentString run = singleChord(shared, scale);
    if (run == null) return null;
    if (TwoNodeClip.locateInShell(run.getStart(), solid) != TwoNodeClip.MIXED) {
      return null;
    }
    if (TwoNodeClip.locateInShell(run.getEnd(), solid) != TwoNodeClip.MIXED) {
      return null;
    }

    Coordinate[] ring = hole.getCoordinates();
    List<Coordinate> pq = TwoNodeClip.walkRing(ring, run.getStart(),
        run.getEnd());
    List<Coordinate> qp = TwoNodeClip.walkRing(ring, run.getEnd(),
        run.getStart());
    if (pq == null || qp == null) return null;
    int pqSide = sideOfShell(pq, solid);
    int qpSide = sideOfShell(qp, solid);
    List<Coordinate> holeIn = null;
    List<Coordinate> holeOut = null;
    if (pqSide == TwoNodeClip.IN && isOnShellWalk(qp, run, scale)) {
      holeIn = pq;
      holeOut = qp;
    }
    else if (qpSide == TwoNodeClip.IN && isOnShellWalk(pq, run, scale)) {
      holeIn = qp;
      holeOut = pq;
    }
    if (holeIn == null || holeOut == null) return null;
    if (!holeInteriorInSolid(ring, run, solid, scale)) return null;

    Coordinate p = holeIn.get(0);
    Coordinate q = holeIn.get(holeIn.size() - 1);
    List<LineString> newEdge = new ArrayList<LineString>();
    newEdge.add(f.createLineString(new Coordinate[] {
        new Coordinate(run.getStart()), new Coordinate(run.getEnd())
    }));
    List<LineString> oriented = directed(newEdge, q, p, scale, f);
    if (oriented == null) return null;
    if (lengthOf(oriented) <= TwoNodeClip.PROPER_CROSS_FRAC * scale) {
      return null;
    }

    Geometry bite = closePlain(holeIn, oriented, f, scale);
    if (bite == null) return null;
    Walk w = new Walk();
    w.kind = BITE;
    w.holed = holed;
    w.solid = solid;
    w.holedFirst = holedFirst;
    w.holeIn = holeIn;
    w.holeOut = holeOut;
    w.newEdge = oriented;
    w.p = p;
    w.q = q;
    w.bite = bite;
    w.leftover = f.createEmpty(2);
    w.scale = scale;
    return w;
  }

  private static CurveSegmentString singleChord(
      List<CurveSegmentString> shared, double scale) {
    if (shared == null) return null;
    CurveSegmentString found = null;
    boolean two = false;
    for (int i = 0; i < shared.size() && !two; i++) {
      CurveSegmentString e = shared.get(i);
      if (e.isArc() || e.isDegenerate()) {
        continue;
      }
      if (e.length() <= TwoNodeClip.PROPER_CROSS_FRAC * scale) {
        continue;
      }
      if (found != null) {
        two = true;
      }
      else {
        found = e;
      }
    }
    return two ? null : found;
  }

  private static boolean isOnShellWalk(List<Coordinate> path,
      CurveSegmentString run, double scale) {
    if (path == null || path.size() != 2) return false;
    double eps = Math.max(TwoNodeClip.PROPER_CROSS_FRAC * scale, 1.0e-12);
    return onRun(path.get(0), run, eps) && onRun(path.get(1), run, eps);
  }

  private static boolean holeInteriorInSolid(Coordinate[] ring,
      CurveSegmentString run, CurvePolygon solid, double scale) {
    if (ring == null || ring.length < 4) return false;
    double eps = Math.max(TwoNodeClip.PROPER_CROSS_FRAC * scale, 1.0e-12);
    int n = ring.length;
    if (ring[0].equals2D(ring[n - 1])) {
      n--;
    }
    if (n < 3) return false;
    boolean ok = true;
    for (int i = 0; i < n && ok; i++) {
      if (onRun(ring[i], run, eps)) {
        continue;
      }
      if (TwoNodeClip.locateInShell(ring[i], solid) != TwoNodeClip.IN) {
        ok = false;
      }
    }
    for (int i = 0; i < n && ok; i++) {
      Coordinate a = ring[i];
      Coordinate b = ring[(i + 1) % n];
      if (onRun(a, run, eps) && onRun(b, run, eps)) {
        continue;
      }
      Coordinate mid = new Coordinate(0.5 * (a.x + b.x), 0.5 * (a.y + b.y));
      if (TwoNodeClip.locateInShell(mid, solid) != TwoNodeClip.IN) {
        ok = false;
      }
    }
    return ok;
  }

  private static boolean onRun(Coordinate p, CurveSegmentString run,
      double eps) {
    if (p.distance(run.getStart()) <= eps || p.distance(run.getEnd()) <= eps) {
      return true;
    }
    double t = TwoNodeClip.parameter(run.getStart(), run.getEnd(), p);
    if (t < -1.0e-12 || t > 1.0 + 1.0e-12) return false;
    return onChord(p, run.getStart(), run.getEnd());
  }

  /**
   * The shell walk whose step into the other interior lands in the
   * hole. That walk is the new edge, and it is a subset of the other
   * shell by construction. No such walk is a named miss.
   */
  private static List<LineString> clipEdgeOnShell(List<LineString> walk,
      CurvePolygon other, LineString hole, double scale) {
    if (walk == null || walk.isEmpty()) return null;
    Coordinate step = stepInside(walk, other, scale);
    if (step == null) return null;
    if (!inPlainRing(step, hole)) return null;
    return walk;
  }

  private static Coordinate stepInside(List<LineString> walk,
      CurvePolygon other, double scale) {
    LineString m = walk.get(0);
    Coordinate a = m.getCoordinateN(0);
    Coordinate b = m.getCoordinateN(m.getNumPoints() - 1);
    Coordinate mid;
    if (m instanceof CircularString && m.getNumPoints() >= 3) {
      mid = m.getCoordinateN(1);
    }
    else {
      mid = new Coordinate(0.5 * (a.x + b.x), 0.5 * (a.y + b.y));
    }
    double dx = b.x - a.x;
    double dy = b.y - a.y;
    double len = Math.hypot(dx, dy);
    if (len <= 1.0e-12) return null;
    double eps = Math.max(1.0e-6, 1.0e-4 * scale);
    Coordinate left = new Coordinate(
        mid.x - dy / len * eps, mid.y + dx / len * eps);
    Coordinate right = new Coordinate(
        mid.x + dy / len * eps, mid.y - dx / len * eps);
    int sl = TwoNodeClip.locateInShell(left, other);
    int sr = TwoNodeClip.locateInShell(right, other);
    if (sl == TwoNodeClip.IN && sr != TwoNodeClip.IN) return left;
    if (sr == TwoNodeClip.IN && sl != TwoNodeClip.IN) return right;
    return null;
  }

  private static int sideOfShell(List<Coordinate> path, CurvePolygon shell) {
    if (path == null || path.size() < 2) return TwoNodeClip.MIXED;
    Coordinate sample;
    if (path.size() >= 3) {
      sample = path.get(path.size() / 2);
    }
    else {
      sample = new Coordinate(0.5 * (path.get(0).x + path.get(1).x),
          0.5 * (path.get(0).y + path.get(1).y));
    }
    return TwoNodeClip.locateInShell(sample, shell);
  }

  /**
   * Replace the shared clip edge on {@code face} with {@code via}
   * (the hole walk). The new edge ⊂ face.shell here -- a bite, not
   * a punch. A face that does not carry that edge stays {@code null}.
   */
  private static Geometry splice(Geometry face, List<Coordinate> via,
      Coordinate p, Coordinate q, GeometryFactory f, double scale) {
    if (face == null || via == null) return null;
    if (face.isEmpty()) return face;
    if (face.getNumGeometries() != 1) return null;
    Geometry g = face.getGeometryN(0);
    if (!(g instanceof CurvePolygon)) return null;
    CurvePolygon cp = (CurvePolygon) g;
    if (cp.getNumInteriorRing() > 0) return null;
    List<TwoNodeClip.Edge> edges = TwoNodeClip.flatten(cp);
    if (edges == null) return null;
    int hit = indexChord(edges, p, q);
    if (hit < 0) return null;
    TwoNodeClip.Edge e = edges.get(hit);
    double tP = TwoNodeClip.parameter(e.a, e.b, p);
    double tQ = TwoNodeClip.parameter(e.a, e.b, q);
    Coordinate first = tP <= tQ ? p : q;
    Coordinate second = tP <= tQ ? q : p;
    List<Coordinate> repl = directedPath(via, first, second, scale);
    if (repl == null) return null;
    List<LineString> members = new ArrayList<LineString>();
    boolean ok = true;
    for (int i = 0; i < edges.size() && ok; i++) {
      if (i == hit) {
        addChord(members, e.a, first, f, scale);
        addPath(members, repl, f);
        addChord(members, second, e.b, f, scale);
      }
      else {
        LineString piece = toLine(edges.get(i), f);
        if (piece == null) {
          ok = false;
        }
        else {
          members.add(piece);
        }
      }
    }
    if (!ok) return null;
    return TwoNodeClip.closeRing(members, f,
        Math.max(TwoNodeClip.PROPER_CROSS_FRAC * scale, 1.0e-12));
  }

  private static int indexChord(List<TwoNodeClip.Edge> edges,
      Coordinate p, Coordinate q) {
    int found = -1;
    boolean two = false;
    for (int i = 0; i < edges.size() && !two; i++) {
      TwoNodeClip.Edge e = edges.get(i);
      if (!e.isArc && onChord(p, e.a, e.b) && onChord(q, e.a, e.b)) {
        if (found >= 0) {
          two = true;
        }
        else {
          found = i;
        }
      }
    }
    return two ? -1 : found;
  }

  private static boolean onChord(Coordinate p, Coordinate a, Coordinate b) {
    double t = TwoNodeClip.parameter(a, b, p);
    Coordinate q = new Coordinate(
        a.x + t * (b.x - a.x), a.y + t * (b.y - a.y));
    return p.distance(q) <= 1.0e-12;
  }

  private static void addChord(List<LineString> dest, Coordinate from,
      Coordinate to, GeometryFactory f, double scale) {
    double eps = Math.max(TwoNodeClip.PROPER_CROSS_FRAC * scale, 1.0e-12);
    if (from.distance(to) <= eps) return;
    dest.add(f.createLineString(new Coordinate[] {
        new Coordinate(from), new Coordinate(to)
    }));
  }

  private static void addPath(List<LineString> dest, List<Coordinate> path,
      GeometryFactory f) {
    if (path == null || path.size() < 2) return;
    dest.add(f.createLineString(copy(path)));
  }

  private static List<Coordinate> directedPath(List<Coordinate> path,
      Coordinate from, Coordinate to, double scale) {
    if (path == null || path.size() < 2) return null;
    double eps = Math.max(TwoNodeClip.PROPER_CROSS_FRAC * scale, 1.0e-12);
    Coordinate a = path.get(0);
    Coordinate b = path.get(path.size() - 1);
    if (a.distance(from) <= eps && b.distance(to) <= eps) return path;
    if (a.distance(to) <= eps && b.distance(from) <= eps) {
      return reverse(path);
    }
    return null;
  }

  private static List<LineString> directed(List<LineString> parts,
      Coordinate from, Coordinate to, double scale, GeometryFactory f) {
    double eps = Math.max(TwoNodeClip.PROPER_CROSS_FRAC * scale, 1.0e-12);
    Coordinate start = startOf(parts);
    Coordinate end = endOf(parts);
    if (start.distance(from) <= eps && end.distance(to) <= eps) {
      return parts;
    }
    if (start.distance(to) <= eps && end.distance(from) <= eps) {
      return reverseMembers(parts);
    }
    return null;
  }

  private static Geometry closePlain(List<Coordinate> along,
      List<LineString> back, GeometryFactory f, double scale) {
    if (along == null || back == null || along.size() < 2) return null;
    List<LineString> oriented = directed(back, along.get(along.size() - 1),
        along.get(0), scale, f);
    if (oriented == null) return null;
    List<Coordinate> ring = new ArrayList<Coordinate>();
    addUniquePts(ring, along);
    addUniquePts(ring, coordsOf(oriented));
    if (ring.size() < 3) return null;
    if (!ring.get(0).equals2D(ring.get(ring.size() - 1))) {
      ring.add(new Coordinate(ring.get(0)));
    }
    try {
      Geometry poly = f.createPolygon(f.createLinearRing(copy(ring)));
      if (poly.getArea() <= 0.0) return null;
      return poly;
    }
    catch (RuntimeException ex) {
      return null;
    }
  }

  private static Geometry punch(Geometry solid, Geometry hole, GeometryFactory f) {
    if (solid == null || hole == null) return null;
    if (solid.isEmpty()) return solid;
    if (hole.isEmpty()) return solid;
    if (solid.getNumGeometries() != 1) return null;
    Geometry g = solid.getGeometryN(0);
    LineString ring = asHoleRing(hole, f);
    if (ring == null) return null;
    if (g instanceof CurvePolygon) {
      CurvePolygon cp = (CurvePolygon) g;
      if (cp.getNumInteriorRing() > 0) return null;
      return new CurvePolygon(cp.getExteriorCurve(),
          new LineString[] { ring }, f);
    }
    if (TwoNodeClip.isPlainPolygon(g)) {
      try {
        return f.createPolygon(((Polygon) g).getExteriorRing(),
            new LinearRing[] { f.createLinearRing(ring.getCoordinates()) });
      }
      catch (RuntimeException ex) {
        return null;
      }
    }
    return null;
  }

  private static LineString asHoleRing(Geometry hole, GeometryFactory f) {
    if (hole instanceof Polygon) {
      return ((Polygon) hole).getExteriorRing();
    }
    if (hole instanceof LineString && ((LineString) hole).isClosed()) {
      return (LineString) hole;
    }
    return null;
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

  private static boolean inPlainRing(Coordinate p, LineString hole) {
    try {
      Polygon poly = hole.getFactory().createPolygon(
          hole.getFactory().createLinearRing(hole.getCoordinates()));
      return SimplePointInAreaLocator.locate(p, poly) == Location.INTERIOR;
    }
    catch (RuntimeException ex) {
      return false;
    }
  }

  private static LineString toLine(TwoNodeClip.Edge e, GeometryFactory f) {
    if (e.isArc) {
      return TwoNodeClip.arc(e.a, e.mid, e.b, f);
    }
    return f.createLineString(new Coordinate[] {
        new Coordinate(e.a), new Coordinate(e.b)
    });
  }

  private static double lengthOf(List<LineString> parts) {
    double len = 0.0;
    for (int i = 0; i < parts.size(); i++) {
      len += parts.get(i).getLength();
    }
    return len;
  }

  private static Coordinate startOf(List<LineString> parts) {
    return parts.get(0).getCoordinateN(0);
  }

  private static Coordinate endOf(List<LineString> parts) {
    LineString last = parts.get(parts.size() - 1);
    return last.getCoordinateN(last.getNumPoints() - 1);
  }

  private static List<LineString> reverseMembers(List<LineString> parts) {
    List<LineString> out = new ArrayList<LineString>(parts.size());
    for (int i = parts.size() - 1; i >= 0; i--) {
      out.add((LineString) parts.get(i).reverse());
    }
    return out;
  }

  private static List<Coordinate> reverse(List<Coordinate> path) {
    List<Coordinate> out = new ArrayList<Coordinate>(path.size());
    for (int i = path.size() - 1; i >= 0; i--) {
      out.add(path.get(i));
    }
    return out;
  }

  private static List<Coordinate> coordsOf(List<LineString> parts) {
    List<Coordinate> out = new ArrayList<Coordinate>();
    for (int i = 0; i < parts.size(); i++) {
      Coordinate[] c = parts.get(i).getCoordinates();
      for (int k = 0; k < c.length; k++) {
        out.add(c[k]);
      }
    }
    return out;
  }

  private static void addUniquePts(List<Coordinate> dest,
      List<Coordinate> src) {
    if (src == null) return;
    for (int i = 0; i < src.size(); i++) {
      Coordinate p = src.get(i);
      if (dest.isEmpty() || !dest.get(dest.size() - 1).equals2D(p)) {
        dest.add(new Coordinate(p));
      }
    }
  }

  private static Coordinate[] copy(List<Coordinate> path) {
    Coordinate[] c = new Coordinate[path.size()];
    for (int i = 0; i < path.size(); i++) {
      c[i] = new Coordinate(path.get(i));
    }
    return c;
  }

  private static double scaleOf(Geometry a, Geometry b) {
    double wa = Math.max(a.getEnvelopeInternal().getWidth(),
        a.getEnvelopeInternal().getHeight());
    double wb = Math.max(b.getEnvelopeInternal().getWidth(),
        b.getEnvelopeInternal().getHeight());
    return Math.max(Math.max(wa, wb), 1.0);
  }

  private static final class Walk {
    int kind;
    CurvePolygon holed;
    CurvePolygon solid;
    boolean holedFirst;
    List<Coordinate> holeIn;
    List<Coordinate> holeOut;
    List<LineString> newEdge;
    Coordinate p;
    Coordinate q;
    Geometry bite;
    Geometry leftover;
    double scale;
  }
}

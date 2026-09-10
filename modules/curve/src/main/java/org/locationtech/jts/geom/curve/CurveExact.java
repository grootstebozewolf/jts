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

import java.util.ArrayList;
import java.util.List;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.CoordinateSequence;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.IntersectionMatrix;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Location;
import org.locationtech.jts.geom.MultiLineString;
import org.locationtech.jts.geom.MultiPoint;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;

/**
 * Closed-form answers for the shapes a cheap check can recognise: a circular
 * disc, a certified stadium (two equal-radius semicircular caps and two
 * parallel sides), a single circular arc, the convex hull of
 * circular-plus-straight members, a point against an arc, a disc against
 * a Point or MultiPoint (PIP and DE-9IM), a disc against a LineString
 * (DE-9IM from line–circle nodes), a disc against a plain Polygon
 * (DE-9IM from vertices, edge nodes, and mid-arc PIP), or two circular
 * discs (DE-9IM from {@code d²} vs {@code (r1±r2)²} in {@code R²}).
 * Package-private -- not a new public API.
 * {@link CurveOps} takes these only when they can answer; anything else
 * goes straight to the chord baseline. Trying and falling through would
 * pay both tools.
 */
final class CurveExact {

  private static final double TWO_PI = 2.0 * Math.PI;
  private static final double SWEEP_EPS = 1.0e-9;

  /**
   * JTS/SFS DE-9IM for an areal geometry vs a Point (or a MultiPoint
   * whose members all share one location class). Probed against a plain
   * {@code POLYGON} in jts-core; {@code 0FFFFF212} is the reverse
   * ({@code point.relate(area)}), not {@code area.relate(point)}.
   */
  static final String IM_POINT_INTERIOR = "0F2FF1FF2";
  static final String IM_POINT_BOUNDARY = "FF20F1FF2";
  static final String IM_POINT_EXTERIOR = "FF2FF10F2";

  /**
   * JTS/SFS DE-9IM for an areal geometry vs a LineString. Probed against
   * the inscribed diamond / octagon of {@code CIRCLE_5} in jts-core;
   * a square is the wrong probe for a tangent (the line lies on an edge).
   */
  static final String IM_LINE_CROSS = "1F20F1102";
  static final String IM_LINE_TANGENT = "FF20F1102";
  static final String IM_LINE_EXTERIOR = "FF2FF1102";
  static final String IM_LINE_END_INTERIOR = "1020F1102";

  /**
   * JTS/SFS DE-9IM for an areal geometry vs a Polygon. Probed against
   * the inscribed diamond / octagon of {@code CIRCLE_5} in jts-core,
   * and against two plain polygons (or vertex-touch squares) for the
   * two-disc location classes. Circles cannot share an edge, so an
   * edge-sharing square ({@code BB=1}) is the wrong tangent probe.
   */
  static final String IM_AREA_DISJOINT = "FF2FF1212";
  static final String IM_AREA_COVERS = "212FF1FF2";
  static final String IM_AREA_COVEREDBY = "2FF1FF212";
  static final String IM_AREA_OVERLAP = "212101212";
  static final String IM_AREA_EXT_TANGENT = "FF2F01212";
  static final String IM_AREA_INT_TANGENT = "212F01FF2";
  static final String IM_AREA_EQUAL = "2FFF1FFF2";

  private CurveExact() { }

  /**
   * Exact convex hull, or {@code null} if this geometry is not a circular
   * disc, a single arc, or a circular-plus-straight mix
   * {@link CurveConvexHull} can certify. A disc's hull is the disc; a
   * single arc's hull is the slice bounded by the arc and its chord; a
   * CompoundCurve of arcs and segments (H-CC, a stadium) is a
   * {@link CurvePolygon} whose shell is the exposed arcs plus the
   * supporting tangents. A clothoid or any uncertified mix is
   * {@code null} so {@link CurveOps} can take the chords alone -- never
   * densify-then-hull and flag the result exact.
   */
  static Geometry convexHull(Geometry g) {
    if (g == null || g.isEmpty()) return null;
    CircularArcDensifier.Circle disc = circularDisc(g);
    if (disc != null) {
      return g instanceof CurvePolygon ? g.copy() : makeDisc(disc, g.getFactory());
    }
    if (g instanceof CircularString) {
      CircularString cs = (CircularString) g;
      if (isSingleArc(cs)) {
        return arcChordHull(cs);
      }
    }
    return CurveConvexHull.hull(g);
  }

  /**
   * Exact buffer: circular disc → concentric disc; single open
   * CircularString arc → CurvePolygon corridor (outer/inner parallels
   * + round caps). {@code null} otherwise (chainsaw + warn via
   * {@link CurveOps}).
   */
  static Geometry buffer(Geometry g, double distance) {
    CircularArcDensifier.Circle disc = circularDisc(g);
    if (disc != null) {
      double r = disc.r + distance;
      if (r <= 0.0) return g.getFactory().createPolygon();
      return makeDisc(new CircularArcDensifier.Circle(disc.cx, disc.cy, r),
          g.getFactory());
    }
    if (g instanceof CircularString && isSingleArc((CircularString) g)) {
      return bufferOpenArc((CircularString) g, distance);
    }
    if (g instanceof CompoundCurve) {
      CompoundCurve cc = (CompoundCurve) g;
      if (cc.isClosed()) {
        CurvePolygon wrap = new CurvePolygon(cc, null, g.getFactory());
        Geometry stadiumFromCc = bufferStadium(wrap, distance);
        if (stadiumFromCc != null) {
          return stadiumFromCc;
        }
      }
      Geometry openCc = bufferOpenCompound(cc, distance);
      if (openCc != null) {
        return openCc;
      }
    }
    Geometry stadiumBuf = bufferStadium(g, distance);
    if (stadiumBuf != null) {
      return stadiumBuf;
    }
    return null;
  }

  /**
   * BUF-N open mixed corridor: one or more members, each a single
   * segment or single circular arc, {@code d > 0}. Builds left/right
   * parallels with round joins at junctions and round caps at the
   * ends. A collapsed inward arc radius returns {@code null}.
   */
  private static Geometry bufferOpenCompound(CompoundCurve cc, double d) {
    int n = cc.getNumMembers();
    if (!(d > 0.0) || n < 1) {
      return null;
    }
    LineString[] members = new LineString[n];
    for (int i = 0; i < n; i++) {
      members[i] = cc.getMemberN(i);
      if (!isBufferablePiece(members[i])) {
        return null;
      }
      if (i > 0) {
        Coordinate a = members[i - 1].getCoordinateN(
            members[i - 1].getNumPoints() - 1);
        Coordinate b = members[i].getCoordinateN(0);
        if (a.distance(b) > 1.0e-9) {
          return null;
        }
      }
    }
    Parallel[] left = new Parallel[n];
    Parallel[] right = new Parallel[n];
    for (int i = 0; i < n; i++) {
      left[i] = parallelPiece(members[i], d, true);
      right[i] = parallelPiece(members[i], d, false);
      if (left[i] == null || right[i] == null) {
        return null;
      }
    }
    GeometryFactory f = cc.getFactory();
    java.util.ArrayList<LineString> shell = new java.util.ArrayList<LineString>();
    for (int i = 0; i < n; i++) {
      if (i > 0) {
        Coordinate junction = members[i].getCoordinateN(0);
        LineString join = roundJoin(f, junction, left[i - 1].end, left[i].start,
            d, true);
        if (join != null) {
          shell.add(join);
        }
      }
      shell.add(left[i].geom);
    }
    shell.add(endCap(f, members[n - 1], left[n - 1].end, right[n - 1].end, d,
        true));
    for (int i = n - 1; i >= 0; i--) {
      if (i < n - 1) {
        Coordinate junction = members[i + 1].getCoordinateN(0);
        LineString join = roundJoin(f, junction, right[i + 1].start, right[i].end,
            d, false);
        if (join != null) {
          shell.add(join);
        }
      }
      shell.add(reversePiece(right[i].geom));
    }
    shell.add(endCap(f, members[0], right[0].start, left[0].start, d, false));
    return new CurvePolygon(
        new CompoundCurve(shell.toArray(new LineString[0]), f), null, f);
  }

  private static boolean isBufferablePiece(LineString m) {
    if (m instanceof CircularString) {
      return isSingleArc((CircularString) m);
    }
    return !(m instanceof CircularString) && m.getNumPoints() == 2
        && !m.getCoordinateN(0).equals2D(m.getCoordinateN(1));
  }

  /** One side of a buffered piece. */
  private static final class Parallel {
    final LineString geom;
    final Coordinate start;
    final Coordinate end;
    Parallel(LineString geom, Coordinate start, Coordinate end) {
      this.geom = geom;
      this.start = start;
      this.end = end;
    }
  }

  private static Parallel parallelPiece(LineString m, double d, boolean left) {
    if (m instanceof CircularString) {
      return parallelArc((CircularString) m, d, left);
    }
    return parallelSeg(m, d, left);
  }

  private static Parallel parallelSeg(LineString seg, double d, boolean left) {
    Coordinate a = seg.getCoordinateN(0);
    Coordinate b = seg.getCoordinateN(1);
    double dx = b.x - a.x;
    double dy = b.y - a.y;
    double len = Math.hypot(dx, dy);
    if (!(len > 0.0)) {
      return null;
    }
    double nx = -dy / len;
    double ny = dx / len;
    if (!left) {
      nx = -nx;
      ny = -ny;
    }
    Coordinate a2 = new Coordinate(a.x + d * nx, a.y + d * ny);
    Coordinate b2 = new Coordinate(b.x + d * nx, b.y + d * ny);
    LineString g = seg.getFactory().createLineString(new Coordinate[] { a2, b2 });
    return new Parallel(g, a2, b2);
  }

  private static Parallel parallelArc(CircularString arc, double d, boolean left) {
    Coordinate p0 = arc.getCoordinateN(0);
    Coordinate p1 = arc.getCoordinateN(1);
    Coordinate p2 = arc.getCoordinateN(2);
    CircularArcDensifier.Circle c = CircularArcDensifier.Circle.fromThreePoints(
        p0, p1, p2);
    if (c == null || !(c.r > 0.0)) {
      return null;
    }
    double a0 = Math.atan2(p0.y - c.cy, p0.x - c.cx);
    double a1 = Math.atan2(p1.y - c.cy, p1.x - c.cx);
    double a2 = Math.atan2(p2.y - c.cy, p2.x - c.cx);
    boolean ccw = midInCcw(a0, a1, a2);
    // Left of CCW is inward (smaller radius).
    boolean inward = left == ccw;
    double rn = inward ? c.r - d : c.r + d;
    if (!(rn > 0.0)) {
      return null;
    }
    Coordinate q0 = radial(c, a0, rn);
    Coordinate q1 = radial(c, a1, rn);
    Coordinate q2 = radial(c, a2, rn);
    CircularString g = circ(arc.getFactory(), q0, q1, q2);
    return new Parallel(g, q0, q2);
  }

  private static LineString reversePiece(LineString g) {
    if (g instanceof CircularString) {
      return (LineString) g.reverse();
    }
    Coordinate[] pts = g.getCoordinates();
    Coordinate[] rev = new Coordinate[pts.length];
    for (int i = 0; i < pts.length; i++) {
      rev[i] = new Coordinate(pts[pts.length - 1 - i]);
    }
    return g.getFactory().createLineString(rev);
  }

  /**
   * Round join of radius {@code d} at {@code vertex} from {@code from}
   * to {@code to}. Returns {@code null} when the points already meet
   * (G1 / collinear offset).
   */
  private static LineString roundJoin(GeometryFactory f, Coordinate vertex,
      Coordinate from, Coordinate to, double d, boolean preferCcw) {
    if (from.distance(to) <= 1.0e-9) {
      return null;
    }
    Coordinate mid = joinMid(vertex, from, to, d, preferCcw);
    return circ(f, from, mid, to);
  }

  private static Coordinate joinMid(Coordinate v, Coordinate from, Coordinate to,
      double d, boolean preferCcw) {
    double ax = from.x - v.x;
    double ay = from.y - v.y;
    double bx = to.x - v.x;
    double by = to.y - v.y;
    double al = Math.hypot(ax, ay);
    double bl = Math.hypot(bx, by);
    if (al < 1.0e-12 || bl < 1.0e-12) {
      return new Coordinate(v.x + d, v.y);
    }
    ax /= al;
    ay /= al;
    bx /= bl;
    by /= bl;
    double cross = ax * by - ay * bx;
    double mx = ax + bx;
    double my = ay + by;
    boolean ccwShort = cross >= 0.0;
    if (preferCcw != ccwShort) {
      mx = -mx;
      my = -my;
    }
    double ml = Math.hypot(mx, my);
    if (ml < 1.0e-12) {
      mx = -ay;
      my = ax;
      ml = 1.0;
    }
    return new Coordinate(v.x + d * mx / ml, v.y + d * my / ml);
  }

  private static CircularString endCap(GeometryFactory f, LineString piece,
      Coordinate from, Coordinate to, double d, boolean atEnd) {
    Coordinate tip = capTip(piece, d, atEnd);
    return circ(f, from, tip, to);
  }

  private static Coordinate capTip(LineString piece, double d, boolean atEnd) {
    if (piece instanceof CircularString) {
      CircularString arc = (CircularString) piece;
      Coordinate p0 = arc.getCoordinateN(0);
      Coordinate p1 = arc.getCoordinateN(1);
      Coordinate p2 = arc.getCoordinateN(2);
      CircularArcDensifier.Circle c = CircularArcDensifier.Circle.fromThreePoints(
          p0, p1, p2);
      double a0 = Math.atan2(p0.y - c.cy, p0.x - c.cx);
      double a1 = Math.atan2(p1.y - c.cy, p1.x - c.cx);
      double a2 = Math.atan2(p2.y - c.cy, p2.x - c.cx);
      boolean ccw = midInCcw(a0, a1, a2);
      return tangentTip(c, atEnd ? a2 : a0, ccw, d, atEnd);
    }
    Coordinate a = piece.getCoordinateN(0);
    Coordinate b = piece.getCoordinateN(1);
    double dx = b.x - a.x;
    double dy = b.y - a.y;
    double len = Math.hypot(dx, dy);
    double ux = dx / len;
    double uy = dy / len;
    Coordinate tipAt = atEnd ? b : a;
    if (!atEnd) {
      ux = -ux;
      uy = -uy;
    }
    return new Coordinate(tipAt.x + d * ux, tipAt.y + d * uy);
  }

  /**
   * BUF-N subset: certified stadium {@link CurvePolygon} dilates to a
   * larger stadium (caps at {@code r+d}, sides re-joined). Erosion and
   * non-stadium CompoundCurves stay on the chainsaw.
   */
  private static Geometry bufferStadium(Geometry g, double d) {
    if (!(d > 0.0)) {
      return null;
    }
    if (!(g instanceof CurvePolygon)) {
      return null;
    }
    CircularArcDensifier.Circle mic = stadiumMic(g);
    if (mic == null) {
      return null;
    }
    CurvePolygon cp = (CurvePolygon) g;
    CompoundCurve cc = (CompoundCurve) cp.getExteriorCurve();
    GeometryFactory f = g.getFactory();
    LineString[] scaled = new LineString[4];
    for (int i = 0; i < 4; i++) {
      LineString m = cc.getMemberN(i);
      if (m instanceof CircularString) {
        scaled[i] = scaleArcOut((CircularString) m, d);
        if (scaled[i] == null) {
          return null;
        }
      }
    }
    for (int i = 0; i < 4; i++) {
      if (scaled[i] != null) {
        continue;
      }
      LineString prev = scaled[(i + 3) % 4];
      LineString next = scaled[(i + 1) % 4];
      if (prev == null || next == null) {
        return null;
      }
      Coordinate a = prev.getCoordinateN(prev.getNumPoints() - 1);
      Coordinate b = next.getCoordinateN(0);
      scaled[i] = f.createLineString(new Coordinate[] {
          new Coordinate(a), new Coordinate(b)
      });
    }
    CompoundCurve shell = new CompoundCurve(scaled, f);
    return new CurvePolygon(shell, null, f);
  }

  private static CircularString scaleArcOut(CircularString cs, double d) {
    Coordinate p0 = cs.getCoordinateN(0);
    Coordinate p1 = cs.getCoordinateN(1);
    Coordinate p2 = cs.getCoordinateN(2);
    CircularArcDensifier.Circle c = CircularArcDensifier.Circle.fromThreePoints(
        p0, p1, p2);
    if (c == null || !(c.r > 0.0)) {
      return null;
    }
    double k = (c.r + d) / c.r;
    Coordinate[] pts = new Coordinate[] {
        scalePt(p0, c.cx, c.cy, k),
        scalePt(p1, c.cx, c.cy, k),
        scalePt(p2, c.cx, c.cy, k)
    };
    return new CircularString(
        cs.getFactory().getCoordinateSequenceFactory().create(pts),
        cs.getFactory());
  }

  private static Coordinate scalePt(Coordinate p, double cx, double cy,
      double k) {
    return new Coordinate(cx + (p.x - cx) * k, cy + (p.y - cy) * k);
  }

  /**
   * BUF-1 / BUF-NEG: open single-arc buffer corridor.
   * Positive {@code d} dilates; {@code |d|} larger than radius on the
   * inward side collapses that parallel (returns empty for
   * {@code d < 0 && |d| >= r} on an open arc — BUF-NEG).
   */
  private static Geometry bufferOpenArc(CircularString arc, double d) {
    if (d == 0.0) {
      return arc.copy();
    }
    double abs = Math.abs(d);
    Coordinate p0 = arc.getCoordinateN(0);
    Coordinate p1 = arc.getCoordinateN(1);
    Coordinate p2 = arc.getCoordinateN(2);
    CircularArcDensifier.Circle c = CircularArcDensifier.Circle.fromThreePoints(
        p0, p1, p2);
    if (c == null) {
      return null;
    }
    // Negative buffer of an open arc is not a simple corridor.
    if (d < 0.0) {
      if (abs >= c.r) {
        return arc.getFactory().createPolygon(); // BUF-NEG empty
      }
      return null; // erosion of open arc → chainsaw
    }
    if (c.r - abs <= 0.0) {
      // Inward parallel collapses: buffer is outer arc + full end discs
      // joined — fall through until a dedicated cell lands.
      return null;
    }
    GeometryFactory f = arc.getFactory();
    double a0 = Math.atan2(p0.y - c.cy, p0.x - c.cx);
    double a1 = Math.atan2(p1.y - c.cy, p1.x - c.cx);
    double a2 = Math.atan2(p2.y - c.cy, p2.x - c.cx);
    boolean ccw = midInCcw(a0, a1, a2);

    Coordinate out0 = radial(c, a0, c.r + abs);
    Coordinate out1 = radial(c, a1, c.r + abs);
    Coordinate out2 = radial(c, a2, c.r + abs);
    Coordinate in0 = radial(c, a0, c.r - abs);
    Coordinate in1 = radial(c, a1, c.r - abs);
    Coordinate in2 = radial(c, a2, c.r - abs);

    CircularString outer = circ(f, out0, out1, out2);
    CircularString innerRev = circ(f, in2, in1, in0);

    // End caps: semicircle of radius |d| centred at the arc end,
    // from outer end through the outward tangent tip to inner end.
    Coordinate endTanTip = tangentTip(c, a2, ccw, abs, true);
    Coordinate startTanTip = tangentTip(c, a0, ccw, abs, false);
    CircularString capEnd = circ(f, out2, endTanTip, in2);
    CircularString capStart = circ(f, in0, startTanTip, out0);

    CompoundCurve shell = new CompoundCurve(
        new LineString[] { outer, capEnd, innerRev, capStart }, f);
    return new CurvePolygon(shell, null, f);
  }

  private static Coordinate radial(CircularArcDensifier.Circle c, double ang,
      double r) {
    return new Coordinate(c.cx + r * Math.cos(ang), c.cy + r * Math.sin(ang));
  }

  /**
   * Tip of the round cap: centre ± |d| along the arc tangent (forward at
   * end, backward at start).
   */
  private static Coordinate tangentTip(CircularArcDensifier.Circle c,
      double ang, boolean ccw, double d, boolean atEnd) {
    // Unit tangent for increasing angle (CCW): (-sin, cos)
    double tx = -Math.sin(ang);
    double ty = Math.cos(ang);
    if (!ccw) {
      tx = -tx;
      ty = -ty;
    }
    if (!atEnd) {
      tx = -tx;
      ty = -ty;
    }
    double px = c.cx + c.r * Math.cos(ang);
    double py = c.cy + c.r * Math.sin(ang);
    return new Coordinate(px + d * tx, py + d * ty);
  }

  private static CircularString circ(GeometryFactory f, Coordinate a,
      Coordinate b, Coordinate c) {
    return new CircularString(f.getCoordinateSequenceFactory().create(
        new Coordinate[] { a, b, c }), f);
  }

  /**
   * Exact distance, or {@code null} if the pair is not a shape this class
   * can answer (disc-disc, disc-point, point-arc, arc-arc, arc-segment).
   */
  static Double distance(Geometry a, Geometry b) {
    if (a == null || b == null || a.isEmpty() || b.isEmpty()) return null;
    CircularArcDensifier.Circle da = circularDisc(a);
    CircularArcDensifier.Circle db = circularDisc(b);
    if (da != null && db != null) {
      return Double.valueOf(Math.max(0.0,
          Math.hypot(da.cx - db.cx, da.cy - db.cy) - da.r - db.r));
    }
    if (da != null && b instanceof Point) {
      return Double.valueOf(pointToFilledDisc(((Point) b).getCoordinate(), da));
    }
    if (db != null && a instanceof Point) {
      return Double.valueOf(pointToFilledDisc(((Point) a).getCoordinate(), db));
    }
    Point pt = null;
    Geometry curve = null;
    if (a instanceof Point && isLinealCurve(b)) {
      pt = (Point) a;
      curve = b;
    } else if (b instanceof Point && isLinealCurve(a)) {
      pt = (Point) b;
      curve = a;
    }
    if (pt != null) {
      return Double.valueOf(pointToLinealCurve(pt.getCoordinate(), curve));
    }
    if (isLinealCurve(a) && isLinealCurve(b)) {
      return Double.valueOf(linealToLineal(a, b));
    }
    if (isLinealCurve(a) && isPlainLineal(b)) {
      return Double.valueOf(linealToPlain(a, (LineString) b));
    }
    if (isLinealCurve(b) && isPlainLineal(a)) {
      return Double.valueOf(linealToPlain(b, (LineString) a));
    }
    Double members = distanceMembers(a, b);
    if (members != null) return members;
    return null;
  }

  /**
   * SFS {@code covers} of a Point or MultiPoint by a circular disc or
   * certified half-disc, or {@code null} if this pair is not that shape.
   * Same locate test as {@link #contains}; the boundary is covered.
   */
  static Boolean covers(Geometry curve, Geometry other) {
    Boolean disc = discPuntal(curve, other, true);
    if (disc != null) return disc;
    return halfPuntal(curve, other, true);
  }

  /**
   * SFS {@code contains} of a Point or MultiPoint by a circular disc or
   * certified half-disc, or {@code null}. Boundary is not contained
   * ({@code d² == r²} / half boundary → false). A MultiPoint is
   * contained only when every member is interior.
   */
  static Boolean contains(Geometry curve, Geometry other) {
    Boolean disc = discPuntal(curve, other, false);
    if (disc != null) return disc;
    return halfPuntal(curve, other, false);
  }

  private static Boolean halfPuntal(Geometry curve, Geometry other,
      boolean cover) {
    HalfDisc half = HalfDisc.of(curve);
    if (half == null || !isPuntal(other)) return null;
    int n = other.getNumGeometries();
    boolean anyInterior = false;
    for (int i = 0; i < n; i++) {
      Geometry g = other.getGeometryN(i);
      if (!(g instanceof Point) || g.isEmpty()) return null;
      int loc = half.locate(((Point) g).getCoordinate());
      if (loc == Location.EXTERIOR) return Boolean.FALSE;
      if (loc == Location.BOUNDARY) {
        if (!cover) return Boolean.FALSE;
      } else {
        anyInterior = true;
      }
    }
    if (!cover && !anyInterior) return Boolean.FALSE;
    return Boolean.TRUE;
  }

  /**
   * SFS DE-9IM of a circular disc, a certified half-disc, or a single
   * open circular arc vs a Point / uniform MultiPoint / LineString /
   * second disc / plain Polygon. {@code null} if this pair is not that
   * shape. Puntal reuses locate; lineal and polygonal use
   * {@link CircularArcDensifier#intersectSegmentCircle}. Half-disc and
   * open-arc cells are R.2; full-disc cells are R.0 / R-PR.
   */
  static IntersectionMatrix relate(Geometry curve, Geometry other) {
    if (curve == null || other == null || other.isEmpty()) return null;

    CircularArcDensifier.Circle disc = circularDisc(curve);
    if (disc != null) {
      if (isPuntal(other)) return relatePuntal(disc, other);
      LineString line = plainLine(other);
      if (line != null) return relateLine(disc, line);
      CircularArcDensifier.Circle otherDisc = circularDisc(other);
      if (otherDisc != null) return relateDisc(disc, otherDisc);
      Polygon poly = plainPolygon(other);
      if (poly != null) return relatePolygon(disc, poly);
      // Disc vs half-disc: answer from the half side when possible.
      HalfDisc otherHalf = HalfDisc.of(other);
      if (otherHalf != null) return relateDiscHalf(disc, otherHalf);
      return null;
    }

    HalfDisc half = HalfDisc.of(curve);
    if (half != null) {
      if (isPuntal(other)) return relateHalfPuntal(half, other);
      LineString line = plainLine(other);
      if (line != null) return relateHalfLine(half, line);
      CircularArcDensifier.Circle otherDisc = circularDisc(other);
      if (otherDisc != null) return relateHalfToDisc(half, otherDisc);
      return null;
    }

    if (curve instanceof CircularString) {
      CircularString cs = (CircularString) curve;
      if (isSingleOpenArc(cs) && isPuntal(other)) {
        return relateOpenArcPuntal(cs, other);
      }
    }
    return null;
  }

  private static boolean isSingleOpenArc(CircularString cs) {
    return isSingleArc(cs) && !cs.isClosed();
  }

  private static IntersectionMatrix relateHalfPuntal(HalfDisc half,
      Geometry other) {
    int loc = -1;
    int n = other.getNumGeometries();
    for (int i = 0; i < n; i++) {
      Geometry g = other.getGeometryN(i);
      if (!(g instanceof Point) || g.isEmpty()) return null;
      int li = half.locate(((Point) g).getCoordinate());
      if (loc < 0) loc = li;
      else if (li != loc) return null;
    }
    if (loc < 0) return null;
    if (loc == Location.INTERIOR) return new IntersectionMatrix(IM_POINT_INTERIOR);
    if (loc == Location.BOUNDARY) return new IntersectionMatrix(IM_POINT_BOUNDARY);
    return new IntersectionMatrix(IM_POINT_EXTERIOR);
  }

  /**
   * Half-disc vs straight-edged line. Half-disc is convex. A segment
   * that lies on the diameter (or arc) is a 1-d boundary∩line-interior
   * meeting — BI={@code 1}, unlike a circle where nodes are points.
   */
  private static IntersectionMatrix relateHalfLine(HalfDisc half,
      LineString line) {
    Coordinate[] c = line.getCoordinates();
    if (c.length < 2) return null;
    boolean closed = line.isClosed();
    Coordinate first = c[0];
    Coordinate last = c[c.length - 1];

    boolean ii = false;
    boolean ib = false;
    boolean bi0 = false;
    boolean bi1 = false;
    boolean bb = false;
    boolean ei = false;
    boolean eb = false;

    if (!closed) {
      int loc0 = half.locate(first);
      int locN = half.locate(last);
      if (loc0 == Location.INTERIOR || locN == Location.INTERIOR) ib = true;
      if (loc0 == Location.BOUNDARY || locN == Location.BOUNDARY) bb = true;
      if (loc0 == Location.EXTERIOR || locN == Location.EXTERIOR) eb = true;
    }

    boolean anySeg = false;
    CircularArcDensifier.Circle disc = half.circle;
    for (int i = 1; i < c.length; i++) {
      Coordinate a = c[i - 1];
      Coordinate bpt = c[i];
      if (a.equals2D(bpt)) continue;
      anySeg = true;
      int locA = half.locate(a);
      int locB = half.locate(bpt);
      Coordinate mid = new Coordinate(0.5 * (a.x + bpt.x), 0.5 * (a.y + bpt.y));
      int locM = half.locate(mid);
      if (overlapsDiameter(half, a, bpt)) {
        bi1 = true;
      }
      Coordinate[] hits = CircularArcDensifier.intersectSegmentCircle(disc, a, bpt);
      boolean hitInterior = false;
      for (int h = 0; h < hits.length; h++) {
        int lh = half.locate(hits[h]);
        if (lh == Location.BOUNDARY
            && hitIsLineInterior(hits[h], first, last, closed)) {
          bi0 = true;
        } else if (lh == Location.INTERIOR) {
          hitInterior = true;
        }
      }
      if (locA == Location.INTERIOR || locB == Location.INTERIOR
          || locM == Location.INTERIOR || hitInterior) {
        ii = true;
      }
      if (locA == Location.EXTERIOR || locB == Location.EXTERIOR
          || locM == Location.EXTERIOR) {
        ei = true;
      }
    }
    if (!anySeg) return null;
    char bi = bi1 ? '1' : (bi0 ? '0' : 'F');
    return new IntersectionMatrix(""
        + (ii ? '1' : 'F') + (ib ? '0' : 'F') + '2'
        + bi + (bb ? '0' : 'F') + '1'
        + (ei ? '1' : 'F') + (eb ? '0' : 'F') + '2');
  }

  /**
   * True when {@code ab} shares a positive-length colinear run with the
   * half-disc diameter.
   */
  private static boolean overlapsDiameter(HalfDisc half, Coordinate a,
      Coordinate b) {
    Coordinate d0 = half.a;
    Coordinate d1 = half.b;
    double dx = d1.x - d0.x;
    double dy = d1.y - d0.y;
    double len2 = dx * dx + dy * dy;
    if (len2 == 0.0) return false;
    // Both endpoints of ab must be on the diameter line (not necessarily segment).
    double crossA = dx * (a.y - d0.y) - dy * (a.x - d0.x);
    double crossB = dx * (b.y - d0.y) - dy * (b.x - d0.x);
    if (Math.abs(crossA) > 1.0e-9 || Math.abs(crossB) > 1.0e-9) return false;
    double tA = ((a.x - d0.x) * dx + (a.y - d0.y) * dy) / len2;
    double tB = ((b.x - d0.x) * dx + (b.y - d0.y) * dy) / len2;
    double lo = Math.min(tA, tB);
    double hi = Math.max(tA, tB);
    double overlap = Math.min(hi, 1.0) - Math.max(lo, 0.0);
    return overlap > 1.0e-12;
  }

  /**
   * Half-disc vs full disc. Same circle → coveredBy with diameter in
   * disc interior ({@code 2FF11F212}). Other pairs null → linearise.
   */
  private static IntersectionMatrix relateHalfToDisc(HalfDisc half,
      CircularArcDensifier.Circle disc) {
    if (Math.hypot(half.circle.cx - disc.cx, half.circle.cy - disc.cy) > 1.0e-9
        || Math.abs(half.circle.r - disc.r) > 1.0e-9) {
      return null;
    }
    // Same circle: half ⊂ disc; diameter ⊂ disc interior; arc ⊂ boundary.
    return new IntersectionMatrix("2FF11F212");
  }

  private static IntersectionMatrix relateDiscHalf(
      CircularArcDensifier.Circle disc, HalfDisc half) {
    IntersectionMatrix rev = relateHalfToDisc(half, disc);
    if (rev == null) return null;
    return rev.transpose();
  }

  /**
   * Open single-arc CircularString vs Point / uniform MultiPoint.
   * Lineal DE-9IM: on-arc interior {@code 0F1FF0FF2}, endpoint
   * {@code FF10F0FF2}, miss {@code FF1FF00F2}.
   */
  private static IntersectionMatrix relateOpenArcPuntal(CircularString cs,
      Geometry other) {
    int kind = -1; // 0=miss, 1=end, 2=interior
    int n = other.getNumGeometries();
    for (int i = 0; i < n; i++) {
      Geometry g = other.getGeometryN(i);
      if (!(g instanceof Point) || g.isEmpty()) return null;
      int k = locateOnOpenArc(cs, ((Point) g).getCoordinate());
      if (kind < 0) kind = k;
      else if (k != kind) return null;
    }
    if (kind < 0) return null;
    if (kind == 2) return new IntersectionMatrix("0F1FF0FF2");
    if (kind == 1) return new IntersectionMatrix("FF10F0FF2");
    return new IntersectionMatrix("FF1FF00F2");
  }

  /** 0 = miss, 1 = endpoint, 2 = open-arc interior. */
  private static int locateOnOpenArc(CircularString cs, Coordinate p) {
    Coordinate s = cs.getCoordinateN(0);
    Coordinate m = cs.getCoordinateN(1);
    Coordinate e = cs.getCoordinateN(2);
    if (p.equals2D(s) || p.equals2D(e)) return 1;
    CircularArcDensifier.Circle c =
        CircularArcDensifier.Circle.fromThreePoints(s, m, e);
    if (c == null) {
      // Colinear: chord segment.
      return onSegment(p, s, e) ? 2 : 0;
    }
    double dx = p.x - c.cx;
    double dy = p.y - c.cy;
    double dist = Math.hypot(dx, dy);
    if (Math.abs(dist - c.r) > 1.0e-9) return 0;
    double a0 = Math.atan2(s.y - c.cy, s.x - c.cx);
    double aMid = Math.atan2(m.y - c.cy, m.x - c.cx);
    double a1 = Math.atan2(e.y - c.cy, e.x - c.cx);
    boolean ccw = midInCcw(a0, aMid, a1);
    double sweep = ccw ? normPos(a1 - a0) : normPos(a0 - a1);
    if (sweep == 0.0) sweep = TWO_PI;
    double angle = Math.atan2(p.y - c.cy, p.x - c.cx);
    double travelled = ccw ? normPos(angle - a0) : normPos(a0 - angle);
    if (travelled <= 1.0e-12 || travelled >= sweep - 1.0e-12) return 1;
    if (travelled < sweep) return 2;
    return 0;
  }

  private static boolean onSegment(Coordinate p, Coordinate a, Coordinate b) {
    double len = a.distance(b);
    if (len == 0.0) return p.equals2D(a);
    double t = ((p.x - a.x) * (b.x - a.x) + (p.y - a.y) * (b.y - a.y))
        / (len * len);
    if (t < 0.0 || t > 1.0) return false;
    Coordinate proj = new Coordinate(
        a.x + t * (b.x - a.x), a.y + t * (b.y - a.y));
    return p.distance(proj) <= 1.0e-9;
  }

  private static IntersectionMatrix relatePuntal(CircularArcDensifier.Circle disc,
      Geometry other) {
    double r2 = disc.r * disc.r;
    int loc = -1;
    int n = other.getNumGeometries();
    for (int i = 0; i < n; i++) {
      Geometry g = other.getGeometryN(i);
      if (!(g instanceof Point) || g.isEmpty()) return null;
      int li = locatePoint(disc, ((Point) g).getCoordinate(), r2);
      if (loc < 0) loc = li;
      else if (li != loc) return null;
    }
    if (loc < 0) return null;
    if (loc == Location.INTERIOR) return new IntersectionMatrix(IM_POINT_INTERIOR);
    if (loc == Location.BOUNDARY) return new IntersectionMatrix(IM_POINT_BOUNDARY);
    return new IntersectionMatrix(IM_POINT_EXTERIOR);
  }

  /**
   * Location classes of a straight-edged line vs a circular disc.
   * A disc is convex, so each segment meets the closed disc in at most
   * one interval; the DE-9IM is the OR of those intervals against the
   * line's interior and (if open) its two endpoints.
   */
  private static IntersectionMatrix relateLine(CircularArcDensifier.Circle disc,
      LineString line) {
    Coordinate[] c = line.getCoordinates();
    if (c.length < 2) return null;
    boolean closed = line.isClosed();
    double r2 = disc.r * disc.r;
    Coordinate first = c[0];
    Coordinate last = c[c.length - 1];

    boolean ii = false;
    boolean ib = false;
    boolean bi = false;
    boolean bb = false;
    boolean ei = false;
    boolean eb = false;

    if (!closed) {
      int loc0 = locatePoint(disc, first, r2);
      int locN = locatePoint(disc, last, r2);
      if (loc0 == Location.INTERIOR || locN == Location.INTERIOR) ib = true;
      if (loc0 == Location.BOUNDARY || locN == Location.BOUNDARY) bb = true;
      if (loc0 == Location.EXTERIOR || locN == Location.EXTERIOR) eb = true;
    }

    boolean anySeg = false;
    for (int i = 1; i < c.length; i++) {
      Coordinate a = c[i - 1];
      Coordinate bpt = c[i];
      if (a.equals2D(bpt)) continue;
      anySeg = true;
      int locA = locatePoint(disc, a, r2);
      int locB = locatePoint(disc, bpt, r2);
      Coordinate[] hits = CircularArcDensifier.intersectSegmentCircle(disc, a, bpt);
      if (hits.length == 2) {
        // Secant / chord: the open interval between the nodes is interior.
        ii = true;
        if (locA == Location.EXTERIOR || locB == Location.EXTERIOR) ei = true;
        if (hitIsLineInterior(hits[0], first, last, closed)
            || hitIsLineInterior(hits[1], first, last, closed)) {
          bi = true;
        }
      } else if (hits.length == 1) {
        if (locA == Location.INTERIOR || locB == Location.INTERIOR) ii = true;
        if (locA == Location.EXTERIOR || locB == Location.EXTERIOR) ei = true;
        if (hitIsLineInterior(hits[0], first, last, closed)) bi = true;
      } else if (locA == Location.INTERIOR && locB == Location.INTERIOR) {
        ii = true;
      } else if (locA == Location.EXTERIOR && locB == Location.EXTERIOR) {
        ei = true;
      } else if ((locA == Location.INTERIOR && locB == Location.EXTERIOR)
          || (locA == Location.EXTERIOR && locB == Location.INTERIOR)) {
        // A convex disc must produce a node on [0,1].
        return null;
      } else if (locA == Location.INTERIOR || locB == Location.INTERIOR) {
        ii = true;
      } else if (locA == Location.EXTERIOR || locB == Location.EXTERIOR) {
        ei = true;
      }
    }
    if (!anySeg) return null;
    // IE=2, BE=1, EE=2: a disc is 2-dimensional and its circle is not
    // covered by a straight-edged line.
    return new IntersectionMatrix(""
        + (ii ? '1' : 'F') + (ib ? '0' : 'F') + '2'
        + (bi ? '0' : 'F') + (bb ? '0' : 'F') + '1'
        + (ei ? '1' : 'F') + (eb ? '0' : 'F') + '2');
  }

  private static boolean hitIsLineInterior(Coordinate hit, Coordinate first,
      Coordinate last, boolean closed) {
    if (closed) return true;
    return !samePoint(hit, first) && !samePoint(hit, last);
  }

  private static boolean samePoint(Coordinate a, Coordinate b) {
    return a.distance(b) <= 1.0e-12;
  }

  /**
   * A plain LineString, or the single member of a MultiLineString.
   * CircularString / CompoundCurve / multi-member collections miss.
   */
  private static LineString plainLine(Geometry g) {
    if (g instanceof MultiLineString) {
      if (g.getNumGeometries() != 1) return null;
      g = g.getGeometryN(0);
    }
    if (g instanceof CircularString || g instanceof CompoundCurve) return null;
    if (g instanceof LineString) return (LineString) g;
    return null;
  }

  /**
   * Location classes of two circular discs. Envelope miss is disjoint.
   * The class is decided in {@code R²}: {@code d²} against
   * {@code (r1+r2)²} and {@code (r1-r2)²}. No {@code hypot} and no
   * radical-axis node for the decision (V1, before noding). Public
   * numbers may take {@code sqrt}; this matrix does not need the kiss
   * coordinates. T-ext is {@link #IM_AREA_EXT_TANGENT} {@code FF2F01212}
   * — one kiss, no shared flesh — ISO/IEC 13249-3 {@code ST_Touches}
   * (DE-9IM {@code FT*******} / {@code F**T*****} / {@code F***T****}).
   * A non-disc {@code CurvePolygon} never reaches this method.
   */
  private static IntersectionMatrix relateDisc(CircularArcDensifier.Circle a,
      CircularArcDensifier.Circle b) {
    Envelope ea = new Envelope(a.cx - a.r, a.cx + a.r, a.cy - a.r, a.cy + a.r);
    Envelope eb = new Envelope(b.cx - b.r, b.cx + b.r, b.cy - b.r, b.cy + b.r);
    if (!ea.intersects(eb)) {
      return new IntersectionMatrix(IM_AREA_DISJOINT);
    }
    double dx = a.cx - b.cx;
    double dy = a.cy - b.cy;
    double d2 = dx * dx + dy * dy;
    double sum = a.r + b.r;
    double sum2 = sum * sum;
    double diff = a.r - b.r;
    double diff2 = diff * diff;
    if (d2 == 0.0 && a.r == b.r) {
      return new IntersectionMatrix(IM_AREA_EQUAL);
    }
    if (d2 > sum2) {
      return new IntersectionMatrix(IM_AREA_DISJOINT);
    }
    if (d2 == sum2) {
      return new IntersectionMatrix(IM_AREA_EXT_TANGENT);
    }
    if (d2 == diff2) {
      return a.r > b.r
          ? new IntersectionMatrix(IM_AREA_INT_TANGENT)
          : new IntersectionMatrix(IntersectionMatrix.transpose(IM_AREA_INT_TANGENT));
    }
    if (d2 < diff2) {
      return a.r > b.r
          ? new IntersectionMatrix(IM_AREA_COVERS)
          : new IntersectionMatrix(IM_AREA_COVEREDBY);
    }
    return new IntersectionMatrix(IM_AREA_OVERLAP);
  }

  /**
   * A plain Polygon: no holes, no curve rings. {@link CurvePolygon} and
   * MultiPolygon miss this cell.
   */
  private static Polygon plainPolygon(Geometry g) {
    if (!(g instanceof Polygon) || g instanceof CurvePolygon) return null;
    Polygon p = (Polygon) g;
    if (p.isEmpty() || p.getNumInteriorRing() > 0) return null;
    LineString ring = p.getExteriorRing();
    if (ring instanceof CircularString || ring instanceof CompoundCurve) {
      return null;
    }
    return p;
  }

  /**
   * Location classes of a straight-edged polygon vs a circular disc.
   * Vertices use {@link #locatePoint}; each edge reuses the line–circle
   * quadratic; mid-arc samples ask the polygon (jts-core PIP).
   */
  private static IntersectionMatrix relatePolygon(CircularArcDensifier.Circle disc,
      Polygon poly) {
    Envelope de = new Envelope(disc.cx - disc.r, disc.cx + disc.r,
        disc.cy - disc.r, disc.cy + disc.r);
    if (!poly.getEnvelopeInternal().intersects(de)) {
      return new IntersectionMatrix(IM_AREA_DISJOINT);
    }
    Coordinate[] c = poly.getExteriorRing().getCoordinates();
    if (c.length < 4) return null;
    double r2 = disc.r * disc.r;

    boolean vertInt = false;
    boolean vertBnd = false;
    boolean vertExt = false;
    boolean edgeInt = false;
    boolean edgeExt = false;
    List<Coordinate> nodes = new ArrayList<Coordinate>();

    int nVert = c.length - 1;
    for (int i = 0; i < nVert; i++) {
      int loc = locatePoint(disc, c[i], r2);
      if (loc == Location.INTERIOR) vertInt = true;
      else if (loc == Location.BOUNDARY) {
        vertBnd = true;
        addNode(nodes, c[i]);
      } else {
        vertExt = true;
      }
    }

    for (int i = 1; i < c.length; i++) {
      Coordinate a = c[i - 1];
      Coordinate bpt = c[i];
      if (a.equals2D(bpt)) continue;
      int locA = locatePoint(disc, a, r2);
      int locB = locatePoint(disc, bpt, r2);
      Coordinate[] hits = CircularArcDensifier.intersectSegmentCircle(disc, a, bpt);
      for (int h = 0; h < hits.length; h++) {
        addNode(nodes, hits[h]);
      }
      if (hits.length == 2) {
        edgeInt = true;
        if (locA == Location.EXTERIOR || locB == Location.EXTERIOR) edgeExt = true;
      } else if (hits.length == 1) {
        if (locA == Location.INTERIOR || locB == Location.INTERIOR) edgeInt = true;
        if (locA == Location.EXTERIOR || locB == Location.EXTERIOR) edgeExt = true;
      } else if (locA == Location.INTERIOR && locB == Location.INTERIOR) {
        edgeInt = true;
      } else if (locA == Location.EXTERIOR && locB == Location.EXTERIOR) {
        edgeExt = true;
      } else if ((locA == Location.INTERIOR && locB == Location.EXTERIOR)
          || (locA == Location.EXTERIOR && locB == Location.INTERIOR)) {
        return null;
      } else if (locA == Location.INTERIOR || locB == Location.INTERIOR) {
        edgeInt = true;
      } else if (locA == Location.EXTERIOR || locB == Location.EXTERIOR) {
        edgeExt = true;
      }
    }

    boolean[] arc = new boolean[2];
    if (!sampleArcs(disc, poly, nodes, arc)) return null;
    boolean bi = arc[0];
    boolean be = arc[1];
    if (!bi && !be) return null;

    int locC = locateInPoly(poly, new Coordinate(disc.cx, disc.cy));
    boolean ii = vertInt || edgeInt || locC == Location.INTERIOR;
    boolean ib = vertInt || edgeInt;
    boolean ie = be;
    boolean bb = vertBnd || !nodes.isEmpty();
    boolean ei = vertExt || edgeExt;
    boolean eb = vertExt || edgeExt;
    return new IntersectionMatrix(""
        + (ii ? '2' : 'F') + (ib ? '1' : 'F') + (ie ? '2' : 'F')
        + (bi ? '1' : 'F') + (bb ? '0' : 'F') + (be ? '1' : 'F')
        + (ei ? '2' : 'F') + (eb ? '1' : 'F') + '2');
  }

  private static void addNode(List<Coordinate> nodes, Coordinate p) {
    for (int i = 0; i < nodes.size(); i++) {
      if (samePoint(nodes.get(i), p)) return;
    }
    nodes.add(new Coordinate(p));
  }

  /**
   * Classifies each open arc between line–circle nodes (or the whole
   * circle if there are none) against the polygon. {@code out[0]} is
   * BI, {@code out[1]} is BE. {@code false} if a sample lands on the
   * polygon boundary (a straight edge cannot contain an arc).
   */
  private static boolean sampleArcs(CircularArcDensifier.Circle disc, Polygon poly,
      List<Coordinate> nodes, boolean[] out) {
    int n = nodes.size();
    if (n == 0) {
      return markArcSample(poly, new Coordinate(disc.cx + disc.r, disc.cy), out);
    }
    if (n == 1) {
      Coordinate p = nodes.get(0);
      return markArcSample(poly,
          new Coordinate(2.0 * disc.cx - p.x, 2.0 * disc.cy - p.y), out);
    }
    sortNodesByAngle(disc, nodes);
    for (int i = 0; i < n; i++) {
      Coordinate a = nodes.get(i);
      Coordinate b = nodes.get((i + 1) % n);
      double a0 = Math.atan2(a.y - disc.cy, a.x - disc.cx);
      double a1 = Math.atan2(b.y - disc.cy, b.x - disc.cx);
      double mid = a0 + 0.5 * normPos(a1 - a0);
      Coordinate s = new Coordinate(
          disc.cx + disc.r * Math.cos(mid),
          disc.cy + disc.r * Math.sin(mid));
      if (!markArcSample(poly, s, out)) return false;
    }
    return true;
  }

  private static boolean markArcSample(Polygon poly, Coordinate p, boolean[] out) {
    int loc = locateInPoly(poly, p);
    if (loc == Location.INTERIOR) {
      out[0] = true;
      return true;
    }
    if (loc == Location.EXTERIOR) {
      out[1] = true;
      return true;
    }
    return false;
  }

  private static void sortNodesByAngle(final CircularArcDensifier.Circle disc,
      List<Coordinate> nodes) {
    for (int i = 1; i < nodes.size(); i++) {
      Coordinate key = nodes.get(i);
      double keyA = Math.atan2(key.y - disc.cy, key.x - disc.cx);
      int j = i - 1;
      while (j >= 0) {
        Coordinate cur = nodes.get(j);
        double curA = Math.atan2(cur.y - disc.cy, cur.x - disc.cx);
        if (curA <= keyA) break;
        nodes.set(j + 1, cur);
        j--;
      }
      nodes.set(j + 1, key);
    }
  }

  private static int locateInPoly(Polygon poly, Coordinate p) {
    Point pt = poly.getFactory().createPoint(p);
    if (poly.contains(pt)) return Location.INTERIOR;
    if (poly.covers(pt)) return Location.BOUNDARY;
    return Location.EXTERIOR;
  }

  /**
   * {@code Boolean.TRUE}/{@code FALSE} when {@code curve} is a circular
   * disc and {@code other} is a non-empty Point or MultiPoint;
   * {@code null} otherwise so the caller can take the chords alone.
   */
  private static Boolean discPuntal(Geometry curve, Geometry other,
      boolean cover) {
    if (curve == null || other == null || other.isEmpty()) return null;
    if (!isPuntal(other)) return null;
    CircularArcDensifier.Circle disc = circularDisc(curve);
    if (disc == null) return null;

    double r2 = disc.r * disc.r;
    int n = other.getNumGeometries();
    boolean anyInterior = false;
    for (int i = 0; i < n; i++) {
      Geometry g = other.getGeometryN(i);
      if (!(g instanceof Point) || g.isEmpty()) return null;
      int loc = locatePoint(disc, ((Point) g).getCoordinate(), r2);
      if (loc == Location.EXTERIOR) return Boolean.FALSE;
      if (loc == Location.BOUNDARY) {
        if (!cover) return Boolean.FALSE;
      } else {
        anyInterior = true;
      }
    }
    if (!cover && !anyInterior) return Boolean.FALSE;
    return Boolean.TRUE;
  }

  private static boolean isPuntal(Geometry g) {
    return g instanceof Point || g instanceof MultiPoint;
  }

  /**
   * {@link Location#INTERIOR}, {@link Location#BOUNDARY} or
   * {@link Location#EXTERIOR} from {@code d²} vs {@code r²}. Exact
   * equality is the on-circle band; no extra tolerance.
   */
  static int locatePoint(CircularArcDensifier.Circle disc, Coordinate p,
      double r2) {
    double dx = p.x - disc.cx;
    double dy = p.y - disc.cy;
    double d2 = dx * dx + dy * dy;
    if (d2 < r2) return Location.INTERIOR;
    if (d2 > r2) return Location.EXTERIOR;
    return Location.BOUNDARY;
  }

  /**
   * Min distance over collection members, using the same cheap checks.
   * Any member this class cannot answer fails the whole collection --
   * the caller then takes the chord baseline rather than mixing tools.
   */
  private static Double distanceMembers(Geometry a, Geometry b) {
    if (isCurveCollection(a)) {
      if (a.getNumGeometries() == 0) return null;
      double min = Double.POSITIVE_INFINITY;
      for (int i = 0; i < a.getNumGeometries(); i++) {
        Double d = distance(a.getGeometryN(i), b);
        if (d == null) return null;
        min = Math.min(min, d.doubleValue());
      }
      return Double.valueOf(min);
    }
    if (isCurveCollection(b)) {
      return distanceMembers(b, a);
    }
    return null;
  }

  private static boolean isCurveCollection(Geometry g) {
    return g instanceof MultiSurface || g instanceof MultiCurve;
  }

  /**
   * MIC of a circular disc or a certified stadium, or {@code null}.
   * Disc is tried first so {@code CIRCLE_5} stays on the ML.0 path.
   * A stadium miss is a named miss -- the caller stays on chords.
   */
  static CircularArcDensifier.Circle mic(Geometry g) {
    CircularArcDensifier.Circle disc = circularDisc(g);
    if (disc != null) return disc;
    CircularArcDensifier.Circle stadium = stadiumMic(g);
    if (stadium != null) return stadium;
    return halfDiscMic(g);
  }

  /**
   * ML.2: MIC of a certified half-disc. Centre sits on the symmetry
   * ray through the arc mid-control; radius is {@code R/2}. Matches
   * densify-{@code MaximumInscribedCircle} on HALF_DISC (R=5 → (0, 2.5)).
   * Non-half-disc convex shells and nonconvex shapes miss.
   */
  static CircularArcDensifier.Circle halfDiscMic(Geometry g) {
    HalfDisc half = HalfDisc.of(g);
    if (half == null) return null;
    double r = half.circle.r;
    if (r <= 0.0) return null;
    double ux = half.mid.x - half.circle.cx;
    double uy = half.mid.y - half.circle.cy;
    double len = Math.hypot(ux, uy);
    if (len <= 1.0e-12) return null;
    ux /= len;
    uy /= len;
    double micR = 0.5 * r;
    return new CircularArcDensifier.Circle(
        half.circle.cx + micR * ux, half.circle.cy + micR * uy, micR);
  }

  /**
   * MIC of a certified stadium: radius is the cap radius, centre is
   * the midpoint of the two cap centres. {@code null} if {@code g} is
   * not a hole-free four-member stadium (including {@code HALF_DISC}).
   */
  static CircularArcDensifier.Circle stadiumMic(Geometry g) {
    return StadiumMic.compute(g);
  }

  static CircularArcDensifier.Circle circularDisc(Geometry g) {
    if (g instanceof MultiSurface) {
      if (g.getNumGeometries() != 1) return null;
      return circularDisc(g.getGeometryN(0));
    }
    if (!(g instanceof CurvePolygon)) return null;
    CurvePolygon cp = (CurvePolygon) g;
    if (cp.isEmpty() || cp.getNumInteriorRing() > 0) return null;
    return fullCircle(cp.getExteriorCurve());
  }

  static CircularArcDensifier.Circle fullCircle(LineString ring) {
    if (ring instanceof CircularString) {
      return fullCircle((CircularString) ring);
    }
    if (ring instanceof CompoundCurve) {
      CompoundCurve cc = (CompoundCurve) ring;
      CircularArcDensifier.Circle found = null;
      double sweep = 0.0;
      for (int i = 0; i < cc.getNumMembers(); i++) {
        LineString m = cc.getMemberN(i);
        if (!(m instanceof CircularString)) return null;
        CircularString cs = (CircularString) m;
        CircularArcDensifier.Circle c = sameCircle(cs, found);
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

  private static CircularArcDensifier.Circle fullCircle(CircularString cs) {
    if (cs.isEmpty() || !cs.isClosed() || cs.getNumPoints() < 3) return null;
    int n = cs.getNumPoints();
    if (n == 4) {
      if (CircularArcDensifier.threePointCircleCloseMid(cs.getCoordinateSequence()) == null) {
        return null;
      }
    } else if (n != 3 && (n < 5 || n % 2 == 0)) {
      return null;
    }
    CircularArcDensifier.Circle c = sameCircle(cs, null);
    if (c == null) return null;
    if (Math.abs(Math.abs(totalSweep(cs)) - TWO_PI) > SWEEP_EPS) return null;
    return c;
  }

  static CircularArcDensifier.Circle sameCircle(CircularString cs,
      CircularArcDensifier.Circle expected) {
    CoordinateSequence seq = cs.getCoordinateSequence();
    int n = seq.size();
    if (n < 3) return null;
    CircularArcDensifier.Circle found = expected;
    for (int i = 0; i + 2 < n; i += 2) {
      CircularArcDensifier.Circle c = CircularArcDensifier.Circle.fromThreePoints(
          seq.getCoordinate(i), seq.getCoordinate(i + 1), seq.getCoordinate(i + 2));
      if (c == null) return null;
      if (found == null) {
        found = c;
      } else if (Math.hypot(found.cx - c.cx, found.cy - c.cy) > 1.0e-9
          || Math.abs(found.r - c.r) > 1.0e-9) {
        return null;
      }
    }
    return found;
  }

  static double totalSweep(CircularString cs) {
    CoordinateSequence seq = cs.getCoordinateSequence();
    int n = seq.size();
    double total = 0.0;
    for (int i = 0; i + 2 < n; i += 2) {
      Coordinate start = seq.getCoordinate(i);
      Coordinate mid = seq.getCoordinate(i + 1);
      Coordinate end = seq.getCoordinate(i + 2);
      CircularArcDensifier.Circle c = CircularArcDensifier.Circle.fromThreePoints(
          start, mid, end);
      if (c == null) continue;
      double a0 = Math.atan2(start.y - c.cy, start.x - c.cx);
      double aMid = Math.atan2(mid.y - c.cy, mid.x - c.cx);
      double a1 = Math.atan2(end.y - c.cy, end.x - c.cx);
      boolean ccw = midInCcw(a0, aMid, a1);
      double sweep = ccw ? normPos(a1 - a0) : -normPos(a0 - a1);
      if (sweep == 0.0) sweep = ccw ? TWO_PI : -TWO_PI;
      total += sweep;
    }
    Coordinate closeMid = CircularArcDensifier.threePointCircleCloseMid(seq);
    if (closeMid != null) {
      Coordinate start = seq.getCoordinate(n - 2);
      Coordinate end = seq.getCoordinate(0);
      CircularArcDensifier.Circle c = CircularArcDensifier.Circle.fromThreePoints(
          start, closeMid, end);
      if (c != null) {
        double a0 = Math.atan2(start.y - c.cy, start.x - c.cx);
        double aMid = Math.atan2(closeMid.y - c.cy, closeMid.x - c.cx);
        double a1 = Math.atan2(end.y - c.cy, end.x - c.cx);
        boolean ccw = midInCcw(a0, aMid, a1);
        double sweep = ccw ? normPos(a1 - a0) : -normPos(a0 - a1);
        if (sweep == 0.0) sweep = ccw ? TWO_PI : -TWO_PI;
        total += sweep;
      }
    }
    return total;
  }

  private static boolean isSingleArc(CircularString cs) {
    return !cs.isEmpty() && cs.getNumPoints() == 3
        && CircularArcDensifier.Circle.fromThreePoints(
            cs.getCoordinateN(0), cs.getCoordinateN(1), cs.getCoordinateN(2)) != null;
  }

  private static Geometry arcChordHull(CircularString cs) {
    GeometryFactory f = cs.getFactory();
    Coordinate start = cs.getCoordinateN(0);
    Coordinate end = cs.getCoordinateN(2);
    if (start.equals2D(end)) {
      return cs.copy();
    }
    LineString chord = f.createLineString(new Coordinate[] {
        new Coordinate(end), new Coordinate(start)
    });
    CompoundCurve shell = new CompoundCurve(
        new LineString[] { (CircularString) cs.copy(), chord }, f);
    return new CurvePolygon(shell, null, f);
  }

  static Geometry makeDisc(CircularArcDensifier.Circle c, GeometryFactory f) {
    Coordinate[] pts = new Coordinate[] {
        new Coordinate(c.cx + c.r, c.cy),
        new Coordinate(c.cx, c.cy + c.r),
        new Coordinate(c.cx - c.r, c.cy),
        new Coordinate(c.cx, c.cy - c.r),
        new Coordinate(c.cx + c.r, c.cy)
    };
    CircularString ring = new CircularString(
        f.getCoordinateSequenceFactory().create(pts), f);
    return new CurvePolygon(ring, null, f);
  }

  private static double pointToFilledDisc(Coordinate p,
      CircularArcDensifier.Circle c) {
    double d = Math.hypot(p.x - c.cx, p.y - c.cy) - c.r;
    return d < 0.0 ? 0.0 : d;
  }

  private static boolean isLinealCurve(Geometry g) {
    return g instanceof CircularString || g instanceof CompoundCurve;
  }

  private static boolean isPlainLineal(Geometry g) {
    return g instanceof LineString && !isLinealCurve(g);
  }

  private static double pointToLinealCurve(Coordinate p, Geometry curve) {
    if (curve instanceof CircularString) {
      return pointToCircularString(p, (CircularString) curve);
    }
    CompoundCurve cc = (CompoundCurve) curve;
    double min = Double.POSITIVE_INFINITY;
    for (int i = 0; i < cc.getNumMembers(); i++) {
      LineString m = cc.getMemberN(i);
      if (m instanceof CircularString) {
        min = Math.min(min, pointToCircularString(p, (CircularString) m));
      } else {
        min = Math.min(min, pointToLineString(p, m));
      }
    }
    return min;
  }

  private static double pointToCircularString(Coordinate p, CircularString cs) {
    CoordinateSequence seq = cs.getCoordinateSequence();
    int n = seq.size();
    if (n < 3) return pointToLineString(p, cs);
    double min = Double.POSITIVE_INFINITY;
    for (int i = 0; i + 2 < n; i += 2) {
      min = Math.min(min, CircularArcDensifier.distancePointToArc(
          p, seq.getCoordinate(i), seq.getCoordinate(i + 1), seq.getCoordinate(i + 2)));
    }
    return min;
  }

  private static double pointToLineString(Coordinate p, LineString ls) {
    Coordinate[] c = ls.getCoordinates();
    double min = Double.POSITIVE_INFINITY;
    for (int i = 1; i < c.length; i++) {
      min = Math.min(min, CircularArcDensifier.distancePointToSegment(p, c[i - 1], c[i]));
    }
    return min;
  }

  private static double linealToLineal(Geometry a, Geometry b) {
    double min = Double.POSITIVE_INFINITY;
    int na = arcCount(a);
    int nb = arcCount(b);
    for (int i = 0; i < na; i++) {
      Coordinate[] aa = arcAt(a, i);
      for (int j = 0; j < nb; j++) {
        Coordinate[] bb = arcAt(b, j);
        min = Math.min(min, CircularArcDensifier.distanceArcToArc(
            aa[0], aa[1], aa[2], bb[0], bb[1], bb[2]));
      }
    }
    return min;
  }

  private static double linealToPlain(Geometry curve, LineString plain) {
    Coordinate[] p = plain.getCoordinates();
    double min = Double.POSITIVE_INFINITY;
    int n = arcCount(curve);
    for (int i = 0; i < n; i++) {
      Coordinate[] a = arcAt(curve, i);
      for (int j = 1; j < p.length; j++) {
        min = Math.min(min, CircularArcDensifier.distanceArcToSegment(
            a[0], a[1], a[2], p[j - 1], p[j]));
      }
    }
    return min;
  }

  private static int arcCount(Geometry g) {
    if (g instanceof CircularString) {
      int n = ((CircularString) g).getNumPoints();
      return n < 3 ? 0 : (n - 1) / 2;
    }
    CompoundCurve cc = (CompoundCurve) g;
    int total = 0;
    for (int i = 0; i < cc.getNumMembers(); i++) {
      LineString m = cc.getMemberN(i);
      if (m instanceof CircularString) {
        int n = m.getNumPoints();
        if (n >= 3) total += (n - 1) / 2;
      } else if (m.getNumPoints() >= 2) {
        total += m.getNumPoints() - 1;
      }
    }
    return total;
  }

  /**
   * Triple (start, mid, end) for the i-th piece. Straight members are
   * returned as a colinear triple so {@link CircularArcDensifier} degrades
   * them to the chord.
   */
  private static Coordinate[] arcAt(Geometry g, int index) {
    if (g instanceof CircularString) {
      CoordinateSequence seq = ((CircularString) g).getCoordinateSequence();
      int i = index * 2;
      return new Coordinate[] {
          seq.getCoordinate(i), seq.getCoordinate(i + 1), seq.getCoordinate(i + 2)
      };
    }
    CompoundCurve cc = (CompoundCurve) g;
    int seen = 0;
    for (int m = 0; m < cc.getNumMembers(); m++) {
      LineString mem = cc.getMemberN(m);
      if (mem instanceof CircularString) {
        int n = mem.getNumPoints();
        int arcs = n < 3 ? 0 : (n - 1) / 2;
        if (index < seen + arcs) {
          int i = (index - seen) * 2;
          return new Coordinate[] {
              mem.getCoordinateN(i), mem.getCoordinateN(i + 1), mem.getCoordinateN(i + 2)
          };
        }
        seen += arcs;
      } else {
        int segs = Math.max(0, mem.getNumPoints() - 1);
        if (index < seen + segs) {
          int i = index - seen;
          Coordinate a = mem.getCoordinateN(i);
          Coordinate b = mem.getCoordinateN(i + 1);
          return new Coordinate[] { a, mid(a, b), b };
        }
        seen += segs;
      }
    }
    throw new IndexOutOfBoundsException("arc " + index);
  }

  private static Coordinate mid(Coordinate a, Coordinate b) {
    return new Coordinate((a.x + b.x) / 2.0, (a.y + b.y) / 2.0);
  }

  private static boolean midInCcw(double a0, double aMid, double a1) {
    return normPos(aMid - a0) < normPos(a1 - a0);
  }

  private static double normPos(double angle) {
    double twoPi = TWO_PI;
    angle = angle % twoPi;
    if (angle < 0.0) angle += twoPi;
    return angle;
  }
}

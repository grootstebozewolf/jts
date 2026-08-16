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
   * Exact buffer of a circular disc, or {@code null} if {@code g} is not one.
   * Open arcs stay on {@code BufferOp}: an offset corridor is not a disc.
   */
  static Geometry buffer(Geometry g, double distance) {
    CircularArcDensifier.Circle disc = circularDisc(g);
    if (disc == null) return null;
    double r = disc.r + distance;
    if (r <= 0.0) return g.getFactory().createPolygon();
    return makeDisc(new CircularArcDensifier.Circle(disc.cx, disc.cy, r),
        g.getFactory());
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
   * SFS {@code contains} of a Point or MultiPoint in a circular disc, or
   * {@code null} if this pair is not that shape. Cheap check first.
   * <p>
   * For disc centre {@code C}, radius {@code r}, query {@code P},
   * {@code d² = |P−C|²}:
   * <ul>
   * <li>{@code d² < r²} -- interior -- {@code true}</li>
   * <li>{@code d² == r²} -- boundary -- {@code false} (geometries do not
   *     contain their boundary)</li>
   * <li>{@code d² > r²} -- exterior -- {@code false}</li>
   * </ul>
   * A MultiPoint is contained only when every member is interior.
   */
  static Boolean contains(Geometry curve, Geometry other) {
    return discPuntal(curve, other, false);
  }

  /**
   * SFS {@code covers} of a Point or MultiPoint by a circular disc, or
   * {@code null} if this pair is not that shape. Same {@code d²} test as
   * {@link #contains}; the boundary ({@code d² == r²}) is covered.
   */
  static Boolean covers(Geometry curve, Geometry other) {
    return discPuntal(curve, other, true);
  }

  /**
   * SFS DE-9IM of a circular disc vs a Point, a uniform MultiPoint, a
   * LineString (or a single-member MultiLineString), a second circular
   * disc, or a plain Polygon (no holes, no curve rings). {@code null}
   * if this pair is not that shape. Puntal reuses {@link #locatePoint};
   * lineal and polygonal use
   * {@link CircularArcDensifier#intersectSegmentCircle}
   * ({@code t ∈ [0,1]}, the R1.6 / {@code ARC_SEGMENT_XY} quadratic)
   * plus endpoint / vertex location. Two discs classify in {@code R²}
   * ({@code d²} vs {@code (r1±r2)²}); the kiss coordinates are not
   * needed for the matrix. A polygonal miss also samples
   * mid-arc points in the polygon (jts-core PIP). Mixed MultiPoint
   * location classes, a multi-member MultiLineString or MultiSurface,
   * a holed or non-disc curve polygon return {@code null} so the caller
   * linearises rather than guessing. A single-member MultiSurface of a
   * disc unwraps through {@link #circularDisc}.
   */
  static IntersectionMatrix relate(Geometry curve, Geometry other) {
    if (curve == null || other == null || other.isEmpty()) return null;
    CircularArcDensifier.Circle disc = circularDisc(curve);
    if (disc == null) return null;
    if (isPuntal(other)) return relatePuntal(disc, other);
    LineString line = plainLine(other);
    if (line != null) return relateLine(disc, line);
    CircularArcDensifier.Circle otherDisc = circularDisc(other);
    if (otherDisc != null) return relateDisc(disc, otherDisc);
    Polygon poly = plainPolygon(other);
    if (poly != null) return relatePolygon(disc, poly);
    return null;
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
    return stadiumMic(g);
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
    if (cs.isEmpty() || !cs.isClosed() || cs.getNumPoints() < 5) return null;
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

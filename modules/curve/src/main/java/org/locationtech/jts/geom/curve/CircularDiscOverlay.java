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

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.operation.overlayng.OverlayNG;

/**
 * Closed-form overlay of two circular discs. Package-private -- not a new
 * public API, and not a noder. OverlayNG never sees these edges.
 * <p>
 * Two proper crossings become a {@link CurvePolygon} whose shell is two
 * {@link CircularString}s (the intersection points plus a mid-arc control
 * on the line of centres). CAP is the lens, CUP the outer blob, SUB a
 * crescent, XOR both crescents. Anything else -- not both discs, 0 or 1
 * intersection, coincident centres -- returns {@code null} so the caller
 * can take the chord baseline without paying this path first.
 */
final class CircularDiscOverlay {

  /**
   * Two computed nodes closer than this fraction of the smaller radius are
   * a tangent pair in floating point, not a proper crossing.
   */
  private static final double PROPER_CROSS_FRAC = 1.0e-9;

  private CircularDiscOverlay() { }

  /**
   * Exact overlay of two circular discs, or {@code null} if this class
   * cannot answer. The cheap shape check runs first; a miss does not
   * densify and does not node.
   */
  static Geometry overlay(Geometry a, Geometry b, int opCode) {
    CircularArcDensifier.Circle da = CurveExact.circularDisc(a);
    if (da == null) return null;
    CircularArcDensifier.Circle db = CurveExact.circularDisc(b);
    if (db == null) return null;

    Coordinate[] nodes = CircularArcDensifier.intersectCircles(da, db);
    if (nodes.length != 2) return null;
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

    GeometryFactory f = curveFactory(a);
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
   * Point of {@code self} on the line of centres, toward {@code other}
   * ({@code inside}) or away from it. Those two points are the mid-arc
   * controls of the minor and major arcs between the crossing nodes.
   */
  private static Coordinate pole(CircularArcDensifier.Circle self,
      CircularArcDensifier.Circle other, boolean inside) {
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
    CircularString arcA = arc(p, midA, q, f);
    CircularString arcB = arc(q, midB, p, f);
    CompoundCurve shell = new CompoundCurve(new LineString[] { arcA, arcB }, f);
    return new CurvePolygon(shell, null, f);
  }

  private static CircularString arc(Coordinate start, Coordinate mid,
      Coordinate end, GeometryFactory f) {
    Coordinate[] pts = new Coordinate[] {
        new Coordinate(start), new Coordinate(mid), new Coordinate(end)
    };
    return new CircularString(f.getCoordinateSequenceFactory().create(pts), f);
  }

  private static GeometryFactory curveFactory(Geometry g) {
    GeometryFactory f = g.getFactory();
    if (f instanceof CurveGeometryFactory) return f;
    return new CurveGeometryFactory(f.getPrecisionModel(), f.getSRID(),
        f.getCoordinateSequenceFactory());
  }
}

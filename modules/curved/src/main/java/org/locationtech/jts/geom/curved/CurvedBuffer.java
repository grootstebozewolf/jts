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
package org.locationtech.jts.geom.curved;

import java.util.ArrayList;
import java.util.List;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.CoordinateSequence;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.impl.CoordinateArraySequence;

/**
 * Arc-aware buffer of a convex closed curved ring (BUF-1 / BUF-NEG, JTS #1195).
 * <p>
 * Buffers a {@link CurvePolygon} (its shell) or a closed {@link CircularString}
 * ring by signed distance {@code d} into a {@link CurvePolygon}, assembling the
 * offset boundary analytically (no densification): each boundary piece is offset
 * by {@code d} — an arc to its concentric arc {@code r+d}
 * ({@link CircularArcs#offsetArc}), a straight chord to its parallel segment along
 * the outward normal — and consecutive offset pieces are joined directly where the
 * junction is tangent-continuous (G1) or by a round-join arc of radius {@code |d|}
 * at a convex corner ({@code d>0}). This matches the oracle {@code BUFFER_REGION}
 * v1 regime.
 * <ul>
 *   <li>{@code d>0} dilates; {@code d<0} erodes a smooth (all-G1) ring.</li>
 *   <li>Collapse — an arc whose {@code r+d <= 0} — yields the EMPTY CurvePolygon
 *       (BUF-NEG).</li>
 *   <li>The DEGENERATE regime (a reflex corner, or a cornered ring eroded with
 *       {@code d<0} where the offset self-intersects) is out of v1 and throws
 *       {@link UnsupportedOperationException} — it needs the arc noder (the
 *       deferred P2 frontier).</li>
 * </ul>
 */
public final class CurvedBuffer {

  private CurvedBuffer() {}

  private static final double EPS = 1e-7;

  /** Buffers the convex curved ring of {@code geom} by signed distance {@code d}. */
  public static Geometry buffer(Geometry geom, double d) {
    CurvedGeometryFactory gf = factoryOf(geom);
    CoordinateSequence ring = ringSeq(geom);
    if (ring == null) return gf.createCurvePolygon();        // empty in -> empty out

    if (d == 0.0) {
      return gf.createCurvePolygon(gf.createCircularString(copy(ring)));
    }

    int n = ring.size();
    int nArcs = (n - 1) / 2;
    boolean ccw = signedArea(ring) > 0.0;

    // 1. offset each boundary piece
    double[][] off = new double[nArcs][];
    for (int i = 0; i < nArcs; i++) {
      double[] p = piece(ring, i);
      double[] o = isArc(p) ? CircularArcs.offsetArc(p[0],p[1],p[2],p[3],p[4],p[5], d)
                            : offsetSegment(p, d, ccw);
      if (o == null) return gf.createCurvePolygon();         // collapse -> EMPTY (BUF-NEG)
      off[i] = o;
    }

    // 2. assemble, inserting round joins at corners (gaps between offset pieces)
    List<double[]> arcs = new ArrayList<double[]>();
    for (int i = 0; i < nArcs; i++) {
      double[] prev = off[(i - 1 + nArcs) % nArcs];
      double cornerX = ring.getX(2 * i), cornerY = ring.getY(2 * i);
      double ax = prev[4], ay = prev[5];                     // previous offset end
      double bx = off[i][0], by = off[i][1];                 // this offset start
      if (Math.hypot(ax - bx, ay - by) > EPS) {              // a corner
        if (d < 0.0) throw new UnsupportedOperationException(
            "inward buffer of a cornered ring is degenerate (needs noding)");
        arcs.add(roundJoin(cornerX, cornerY, ax, ay, bx, by, Math.abs(d), ccw));
      }
      arcs.add(off[i]);
    }

    // 3. flatten the arc triples into one closed control sequence
    List<Coordinate> ctrl = new ArrayList<Coordinate>();
    ctrl.add(new Coordinate(arcs.get(0)[0], arcs.get(0)[1]));
    for (double[] a : arcs) {
      ctrl.add(new Coordinate(a[2], a[3]));
      ctrl.add(new Coordinate(a[4], a[5]));
    }
    Coordinate first = ctrl.get(0), last = ctrl.get(ctrl.size() - 1);
    if (first.distance(last) > EPS) return gf.createCurvePolygon();   // failed to close -> degenerate/empty
    last.setCoordinate(first);                               // exact closure

    CircularString shell = gf.createCircularString(
        new CoordinateArraySequence(ctrl.toArray(new Coordinate[0])));
    return gf.createCurvePolygon(shell);
  }

  // ---- offsets ----

  /** Parallel offset of a straight chord piece by {@code d} along its outward normal. */
  private static double[] offsetSegment(double[] p, double d, boolean ccw) {
    double sx = p[0], sy = p[1], ex = p[4], ey = p[5];
    double dx = ex - sx, dy = ey - sy, len = Math.hypot(dx, dy);
    if (len == 0.0) return null;
    // outward normal: right of the directed edge for a CCW ring, left for CW
    double nx = (ccw ? dy : -dy) / len, ny = (ccw ? -dx : dx) / len;
    double ox = d * nx, oy = d * ny;
    double sx2 = sx + ox, sy2 = sy + oy, ex2 = ex + ox, ey2 = ey + oy;
    return new double[]{ sx2, sy2, 0.5*(sx2+ex2), 0.5*(sy2+ey2), ex2, ey2 };
  }

  /**
   * Round-join arc of radius {@code rad} centred at the corner {@code (cx,cy)} from
   * the previous offset end {@code (ax,ay)} to this offset start {@code (bx,by)},
   * sweeping the convex (short) way; reflex corners throw.
   */
  private static double[] roundJoin(double cx, double cy, double ax, double ay,
                                    double bx, double by, double rad, boolean ccw) {
    double uax = (ax - cx) / rad, uay = (ay - cy) / rad;
    double ubx = (bx - cx) / rad, uby = (by - cy) / rad;
    double cross = uax * uby - uay * ubx;                    // turn direction A->B
    boolean convex = ccw ? (cross > 0) : (cross < 0);
    if (!convex) throw new UnsupportedOperationException(
        "reflex corner buffer is degenerate (needs noding)");
    double bisx = uax + ubx, bisy = uay + uby;
    double bl = Math.hypot(bisx, bisy);
    if (bl < EPS) {                                          // ~180deg: bisector via perpendicular
      bisx = ccw ? -uay : uay; bisy = ccw ? uax : -uax; bl = Math.hypot(bisx, bisy);
    }
    double mx = cx + rad * bisx / bl, my = cy + rad * bisy / bl;
    return new double[]{ ax, ay, mx, my, bx, by };
  }

  // ---- ring access / geometry helpers ----

  private static CurvedGeometryFactory factoryOf(Geometry g) {
    return (g.getFactory() instanceof CurvedGeometryFactory)
        ? (CurvedGeometryFactory) g.getFactory() : new CurvedGeometryFactory();
  }

  private static CoordinateSequence ringSeq(Geometry g) {
    if (g.isEmpty()) return null;
    if (g instanceof CurvePolygon) {
      LineString shell = ((CurvePolygon) g).getExteriorCurve();
      return shell == null ? null : shell.getCoordinateSequence();
    }
    if (g instanceof CircularString || g instanceof CompoundCurve) {
      return ((LineString) g).getCoordinateSequence();
    }
    throw new IllegalArgumentException("CurvedBuffer requires a CurvePolygon or closed CircularString ring");
  }

  private static double signedArea(CoordinateSequence ring) {
    int n = ring.size();
    double a = 0.0;
    for (int i = 0; i + 2 < n; i += 2) {
      a += ring.getX(i) * ring.getY(i + 2) - ring.getX(i + 2) * ring.getY(i);
    }
    return 0.5 * a;     // chord-polygon orientation suffices for convex rings
  }

  private static double[] piece(CoordinateSequence s, int i) {
    int b = 2 * i;
    return new double[]{ s.getX(b), s.getY(b), s.getX(b+1), s.getY(b+1), s.getX(b+2), s.getY(b+2) };
  }

  private static boolean isArc(double[] p) {
    return 2 * (p[0]*(p[3]-p[5]) + p[2]*(p[5]-p[1]) + p[4]*(p[1]-p[3])) != 0.0;
  }

  private static CoordinateSequence copy(CoordinateSequence s) {
    Coordinate[] c = new Coordinate[s.size()];
    for (int i = 0; i < c.length; i++) c[i] = new Coordinate(s.getX(i), s.getY(i));
    return new CoordinateArraySequence(c);
  }
}

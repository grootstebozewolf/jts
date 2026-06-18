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

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.CoordinateSequence;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.impl.CoordinateArraySequence;

/**
 * Arc-aware validity of a {@link CurvePolygon} (V-CP, JTS #1195), composing the
 * oracle-pinned ring primitives over the structural shell and holes:
 * <ol>
 *   <li>each ring is closed and arc-aware <b>simple</b>
 *       ({@link ArcStringSimplicity}, node semantics — pieces meet only at shared
 *       control vertices);</li>
 *   <li>each hole lies inside the shell — {@link ArcRingRelation#relate} reports
 *       {@code B_IN_A} (nested, no boundary crossing);</li>
 *   <li>the holes are mutually disjoint — {@code relate} reports
 *       {@code DISJOINT} for every hole pair.</li>
 * </ol>
 * Orientation is deliberately <b>not</b> a validity gate: like core
 * {@code Polygon.isValid()}, validity is orientation-agnostic (CW/CCW is a
 * normalization concern), and {@link ArcRingRelation} nesting is orientation
 * independent.
 * <p>
 * Each structural ring is normalized to the arc control-point triple encoding the
 * primitives consume: a {@link CircularString}/{@link CompoundCurve} sequence is
 * already in that form; a plain {@link LineString}/{@code LinearRing} polyline has
 * a collinear midpoint inserted per segment (so each segment reads as a chord).
 */
final class CurvePolygonValidity {

  private CurvePolygonValidity() {}

  static boolean isValid(CurvePolygon poly) {
    if (poly.isEmpty()) return true;
    LineString shell = poly.getExteriorCurve();
    if (shell == null) return true;

    CoordinateSequence shellSeq = arcTriples(shell);
    if (!ringValid(shellSeq)) return false;

    int nh = poly.getNumInteriorRing();
    CoordinateSequence[] holes = new CoordinateSequence[nh];
    for (int i = 0; i < nh; i++) {
      holes[i] = arcTriples(poly.getInteriorCurveN(i));
      if (!ringValid(holes[i])) return false;
      // each hole strictly nested in the shell (no boundary crossing)
      if (ArcRingRelation.relate(shellSeq, holes[i]) != ArcRingRelation.Relation.B_IN_A) return false;
    }
    // holes mutually disjoint
    for (int i = 0; i < nh; i++) {
      for (int j = i + 1; j < nh; j++) {
        if (ArcRingRelation.relate(holes[i], holes[j]) != ArcRingRelation.Relation.DISJOINT) return false;
      }
    }
    return true;
  }

  /** A ring is closed (first == last control point) and arc-aware simple. */
  private static boolean ringValid(CoordinateSequence seq) {
    int n = seq.size();
    if (n < 3) return false;
    if (!(seq.getX(0) == seq.getX(n - 1) && seq.getY(0) == seq.getY(n - 1))) return false;
    return ArcStringSimplicity.isSimple(seq);
  }

  /**
   * The ring's control-point sequence in arc-triple form. Curved rings already
   * carry alternating endpoint/mid control points; a plain polyline gets a
   * collinear midpoint inserted per segment so each segment is read as a chord.
   */
  private static CoordinateSequence arcTriples(LineString ring) {
    CoordinateSequence s = ring.getCoordinateSequence();
    if (ring instanceof CircularString || ring instanceof CompoundCurve) return s;
    int k = s.size();                       // plain polyline vertices v0..v(k-1)
    if (k < 2) return s;
    Coordinate[] out = new Coordinate[2 * (k - 1) + 1];
    out[0] = s.getCoordinate(0).copy();
    int idx = 1;
    for (int i = 0; i + 1 < k; i++) {
      out[idx++] = new Coordinate(0.5 * (s.getX(i) + s.getX(i + 1)), 0.5 * (s.getY(i) + s.getY(i + 1)));
      out[idx++] = s.getCoordinate(i + 1).copy();
    }
    return new CoordinateArraySequence(out);
  }
}

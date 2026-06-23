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
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.operation.buffer.OffsetCurve;

/**
 * Curve-aware offset curve (OFF, JTS #1195).
 * <p>
 * The core {@link OffsetCurve} densifies a {@link CircularString} to its
 * control-point chord polyline before offsetting, destroying arc identity. For
 * a single-arc {@code CircularString} the offset is analytical: the concentric
 * arc with the same centre and sweep at radius {@code r + d} (R±d parallel
 * arcs). This shadow entry point preserves that identity and falls back to the
 * core for all other geometry types.
 *
 * <p>Scope (v1): single-arc {@code CircularString} (3 control points). Multi-arc
 * {@code CircularString} and other curved types delegate to the core (which
 * densifies); multi-arc arc-preserving offset requires junction handling and is
 * tracked as BUF-N.
 */
public final class CurvedOffsetCurve {

  private CurvedOffsetCurve() {}

  /**
   * Computes the offset curve of {@code geom} at signed distance {@code distance}.
   * <p>
   * For a single-arc {@link CircularString} (3 control points) the result is the
   * analytically-offset {@code CircularString} at radius {@code r + distance}
   * (same centre and sweep). When {@code r + distance <= 0} the arc collapses and
   * an empty {@code LineString} is returned.
   * <p>
   * All other geometry types — including multi-arc {@code CircularString} — are
   * delegated to {@link OffsetCurve#getCurve(Geometry, double)}.
   *
   * @param geom     input geometry
   * @param distance signed offset distance (positive = left of direction)
   * @return offset curve
   */
  public static Geometry getCurve(Geometry geom, double distance) {
    if (geom instanceof CircularString) {
      CircularString cs = (CircularString) geom;
      CoordinateSequence seq = cs.getCoordinateSequence();
      if (seq.size() == 3) {
        return offsetSingleArc(cs, seq, distance);
      }
    }
    return OffsetCurve.getCurve(geom, distance);
  }

  private static Geometry offsetSingleArc(CircularString cs, CoordinateSequence seq, double distance) {
    double[] off = CircularArcs.offsetArc(
        seq.getX(0), seq.getY(0),
        seq.getX(1), seq.getY(1),
        seq.getX(2), seq.getY(2),
        distance);
    if (off == null) {
      return cs.getFactory().createLineString(new Coordinate[0]);
    }
    CoordinateSequence newSeq = cs.getFactory().getCoordinateSequenceFactory().create(
        new Coordinate[]{
            new Coordinate(off[0], off[1]),
            new Coordinate(off[2], off[3]),
            new Coordinate(off[4], off[5])});
    return new CircularString(newSeq, cs.getFactory());
  }
}

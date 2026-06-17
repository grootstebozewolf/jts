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
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;

/**
 * Linearises a flat arc-string control sequence (consecutive triples
 * {@code (p[2i], p[2i+1], p[2i+2])}) to a chord polyline whose sagitta is at most
 * the given tolerance (DSF, JTS #1195).
 * <p>
 * A {@code tolerance <= 0} (or a sequence that is not a clean arc string —
 * fewer than three points, or a malformed even length) returns the bare control
 * points, preserving the phase-1 {@code toLinear(0.0)} contract used by the
 * structural ring views. A positive tolerance samples each arc with
 * {@link CircularArcs#tessellate} so the output points lie on the arcs rather
 * than on the control-point chords.
 */
final class ArcTessellation {

  private ArcTessellation() {}

  static LineString toPolyline(CoordinateSequence seq, double tolerance, GeometryFactory f) {
    int n = seq.size();
    if (tolerance <= 0.0 || n < 3 || (n % 2) == 0) {
      return f.createLineString(seq.copy());
    }
    List<Coordinate> out = new ArrayList<Coordinate>();
    for (int i = 0; i + 2 < n; i += 2) {
      double[][] pts = CircularArcs.tessellate(
          seq.getX(i),     seq.getY(i),
          seq.getX(i + 1), seq.getY(i + 1),
          seq.getX(i + 2), seq.getY(i + 2), tolerance);
      int start = (i == 0) ? 0 : 1;            // drop the joint already added by the previous arc
      for (int k = start; k < pts.length; k++) {
        out.add(new Coordinate(pts[k][0], pts[k][1]));
      }
    }
    return f.createLineString(out.toArray(new Coordinate[0]));
  }
}

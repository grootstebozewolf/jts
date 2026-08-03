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
package org.locationtech.jtstest.function;

import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryCollection;
import org.locationtech.jts.geom.curved.Linearizable;
import org.locationtech.jtstest.geomfunction.Metadata;

/**
 * Functions on the OGC SFA / ISO 19125-2 curve geometry types.
 * <p>
 * TestBuilder renders curves as true arcs, so the canvas looks correct without
 * any linearisation. That is exactly why {@link #toLinear} is worth exposing:
 * every legacy consumer of a curve receives the polyline, not the arc, and this
 * is the only way to see that polyline at a tolerance you choose.
 */
public class CurveFunctions {

  /**
   * Linearises every curve in a geometry, replacing each arc with a polyline
   * whose deviation from the true arc is at most {@code tolerance}.
   * <p>
   * Geometries with no curve in them are returned unchanged, so this is safe to
   * apply to anything. Collections are walked recursively, since a curve may be
   * nested inside one.
   * <p>
   * A tolerance of 0 does not mean "exact" -- no polyline can be exact.
   * {@code CircularArcDensifier} reads it as a request for its default, one
   * hundredth of the arc radius.
   */
  @Metadata(description =
      "Replace arcs with polylines deviating by at most the given tolerance")
  public static Geometry toLinear(Geometry g,
      @Metadata(title = "Tolerance")
      double tolerance) {
    return linearize(g, tolerance);
  }

  /**
   * Shared with {@link BufferFunctions}, which must linearise before handing a
   * curve to the chord-based buffer curve builder.
   */
  public static Geometry linearize(Geometry g, double tolerance) {
    if (g == null || g.isEmpty()) return g;
    if (g instanceof Linearizable) return ((Linearizable) g).toLinear(tolerance);
    if (g instanceof GeometryCollection) {
      int n = g.getNumGeometries();
      Geometry[] members = new Geometry[n];
      for (int i = 0; i < n; i++) {
        members[i] = linearize(g.getGeometryN(i), tolerance);
      }
      return g.getFactory().createGeometryCollection(members);
    }
    return g;
  }
}

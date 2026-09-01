/*
 * Copyright (c) 2019 Martin Davis.
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

import static org.locationtech.jts.operation.overlayng.OverlayNG.DIFFERENCE;
import static org.locationtech.jts.operation.overlayng.OverlayNG.INTERSECTION;
import static org.locationtech.jts.operation.overlayng.OverlayNG.SYMDIFFERENCE;
import static org.locationtech.jts.operation.overlayng.OverlayNG.UNION;

import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.operation.overlayng.OverlayNG;

public class OverlayNGStrictFunctions {
  /**
   * Densifies curve operands before handing them to core.
   * <p>
   * These are static entry points taking a {@link Geometry}, so a curve type has
   * no virtual call to override, and left alone they node the chords through the
   * control points: two concentric circles of radius 5 and 3 intersected in 18
   * rather than 9*pi. Worse, a CurvePolygon reports an arc-aware area while its
   * coordinates enclose the chord area, and OverlayNG's own cross-check rejected
   * that contradiction with
   * {@code TopologyException("Result area inconsistent with overlay operation")}.
   * <p>
   * Non-curve input is returned as the same object, so nothing without an arc is
   * affected. The arc cannot survive an overlay at any tolerance -- see
   * {@code CurveOps} -- so the result is a densified plain geometry by necessity.
   */
  private static Geometry arc(Geometry g) {
    return CurveFunctions.linearizeForOps(g);
  }

  
  public static Geometry difference(Geometry a, Geometry b) {
    return overlay(a, b, DIFFERENCE );
  }

  public static Geometry differenceBA(Geometry a, Geometry b) {
      return overlay(b, a, DIFFERENCE );
  }

  public static Geometry intersection(Geometry a, Geometry b) {
    return overlay(a, b, INTERSECTION );
  }

  public static Geometry symDifference(Geometry a, Geometry b) {
    return overlay(a, b, SYMDIFFERENCE );
  }

  public static Geometry union(Geometry a, Geometry b) {
    return overlay(a, b, UNION );
  }

  private static Geometry overlay(Geometry a, Geometry b, int opCode) {
    OverlayNG overlay = new OverlayNG(arc(a), arc(b), opCode );
    overlay.setStrictMode(true);
    return overlay.getResult();

  }
}

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
package org.locationtech.jts.algorithm.exactarc;

import org.locationtech.jts.geom.Coordinate;

/**
 * @deprecated use {@link org.locationtech.jts.algorithm.exactcurve.AngleBetween}.
 * Temporary alias so Option-B {@code ArcGeometry} keeps compiling.
 * New code must import {@code exactcurve}.
 */
@Deprecated
public final class AngleBetween {

  public static final double TWO_PI =
      org.locationtech.jts.algorithm.exactcurve.AngleBetween.TWO_PI;

  private AngleBetween() { }

  public static double signedShort(double ux, double uy, double vx, double vy) {
    return org.locationtech.jts.algorithm.exactcurve.AngleBetween.signedShort(
        ux, uy, vx, vy);
  }

  public static double directedSweep(double cx, double cy,
      Coordinate start, Coordinate mid, Coordinate end) {
    return org.locationtech.jts.algorithm.exactcurve.AngleBetween.directedSweep(
        cx, cy, start, mid, end);
  }

  public static boolean isCcw(double a0, double aMid, double a1) {
    return org.locationtech.jts.algorithm.exactcurve.AngleBetween.isCcw(
        a0, aMid, a1);
  }

  public static double directedSweepFromAngles(double a0, double aMid,
      double a1) {
    return org.locationtech.jts.algorithm.exactcurve.AngleBetween
        .directedSweepFromAngles(a0, aMid, a1);
  }

  public static double normalizePositive(double angle) {
    return org.locationtech.jts.algorithm.exactcurve.AngleBetween
        .normalizePositive(angle);
  }
}

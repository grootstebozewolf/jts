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
package org.locationtech.jts.operation.overlayng.curve;

import org.locationtech.jts.geom.Coordinate;

/**
 * Snap a leave's pixel-exit onto a discrete grid heading.
 * Package-private -- not a noder, not a face walk, not core
 * {@code HotPixel}. {@link CurveHotPixel} stays a pixel test;
 * this is the heading key a later walk (HP.4) can compare.
 * <p>
 * The key is integer {@code (dx, dy)} in the scaled lattice:
 * the pixel-exit rounded with {@code Math.round} (half-up, same
 * family as HotPixel scale-round) minus the already-rounded
 * node. Two leaves have distinct headings iff the keys differ.
 * {@code (0, 0)} is not a heading -- the exit snapped back onto
 * the node.
 * <p>
 * Do not snap the shared tangent. Both H-SHELL-N-ODD leaves at
 * {@code (0, 5)} are +x; that stays coincident. Snap the
 * circle–square exit of the leave, not the supporting chord.
 * A string that does not intersect the pixel has no heading
 * (documented miss, not a fake key).
 * <p>
 * If two leaves share one key they share one snapped ray:
 * {@link #SHARED_SNAPPED_RAY}. Stamp and stop. Same stop rule
 * as P2.5.4. This constant is not a replacement for
 * {@link CurveSegmentFaces#TANGENT_LEAVE_ANGLE} on the unsnapped
 * {@code faces} path. Not a curvature-order tie-break (HP.1).
 */
final class CurveHotPixelSnap {

  /**
   * Named stamp: both leaves snap to the same lattice heading.
   * HP.3 stop. Not wired into {@link CurveSegmentFaces#faces}.
   */
  static final String SHARED_SNAPPED_RAY = "HP.3 shared snapped ray";

  private CurveHotPixelSnap() { }

  /**
   * Heading of {@code leave} through {@code pixel}, or
   * {@code null}. The leave must start in the pixel and
   * {@link CurveHotPixel#intersects(CurveSegmentString) intersect}
   * it. The key is the snapped arc-exit, not the tangent and
   * not the chord.
   */
  static Heading heading(CurveHotPixel pixel, CurveSegmentString leave) {
    if (pixel == null || leave == null) return null;
    Coordinate exit = pixel.exit(leave);
    if (exit == null) return null;
    return snap(pixel, exit);
  }

  /**
   * {@link #SHARED_SNAPPED_RAY} when both headings exist and
   * the keys are equal; {@code null} when they differ or either
   * is a miss. Does not invent a curvature-order split.
   */
  static String sharedRayOrNull(Heading a, Heading b) {
    if (a != null && a.equals(b)) {
      return SHARED_SNAPPED_RAY;
    }
    return null;
  }

  /**
   * Round the exit onto the scaled lattice and subtract the
   * node. An open-edge exit at {@code +0.5} snaps outward
   * ({@code Math.round(0.5) == 1}), not back onto the node.
   */
  private static Heading snap(CurveHotPixel pixel, Coordinate exit) {
    double scale = pixel.getScaleFactor();
    Coordinate node = pixel.getCoordinate();
    int gx = (int) Math.round(exit.x * scale);
    int gy = (int) Math.round(exit.y * scale);
    int nx = (int) Math.round(node.x * scale);
    int ny = (int) Math.round(node.y * scale);
    int dx = gx - nx;
    int dy = gy - ny;
    if (dx == 0 && dy == 0) return null;
    return new Heading(dx, dy);
  }

  /**
   * Integer {@code (dx, dy)} in the scaled lattice from the
   * node to the snapped pixel-exit. Comparable by equality.
   * The walk (HP.4) can use the pair as a map key; this rung
   * does not order or walk.
   */
  static final class Heading {
    final int dx;
    final int dy;

    Heading(int dx, int dy) {
      this.dx = dx;
      this.dy = dy;
    }

    public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof Heading)) return false;
      Heading h = (Heading) o;
      return dx == h.dx && dy == h.dy;
    }

    public int hashCode() {
      return 31 * dx + dy;
    }

    public String toString() {
      return "(" + dx + ", " + dy + ")";
    }
  }
}

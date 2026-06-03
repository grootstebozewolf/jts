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

import org.locationtech.jts.algorithm.Orientation;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.CoordinateSequence;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;

/**
 * A connected sequence of circular arcs, where each consecutive triple of
 * control points (start, mid, end) defines one arc and the end point of one
 * arc is the start point of the next.
 * <p>
 * This is a phase-1 stand-in: the control points are stored as a single
 * {@link CoordinateSequence} (inherited via {@link LineString}) and spatial
 * operations fall through to the parent's polyline behaviour. Native
 * arc-aware algorithms are out of scope for this module today.
 */
public class CircularString extends LineString implements Linearizable {
  private static final long serialVersionUID = 1L;

  public CircularString(CoordinateSequence points, GeometryFactory factory) {
    super(points, factory);
  }

  @Override
  public String getGeometryType() {
    return "CircularString";
  }

  @Override
  protected CircularString copyInternal() {
    return new CircularString(getCoordinateSequence().copy(), getFactory());
  }

  @Override
  public Geometry toLinear(double tolerance) {
    return getFactory().createLineString(getCoordinateSequence().copy());
  }

  @Override
  public double getLength() {
    // M-LEN-CS green: analytical sum, not chord sum of controls.
    // Walks the control seq taking every consecutive triple (stride 2) as one arc.
    CoordinateSequence cs = getCoordinateSequence();
    int n = cs.size();
    if (n < 3) return 0.0;
    double len = 0.0;
    for (int i = 0; i + 2 < n; i += 2) {
      len += exactCircularArcLength(
          cs.getX(i), cs.getY(i),
          cs.getX(i + 1), cs.getY(i + 1),
          cs.getX(i + 2), cs.getY(i + 2)
      );
    }
    return len;
  }

  /**
   * Exact arc length for one circular arc given its 3 control points.
   * (Inlined here for main-code use by getLength(); the test CurveRefRunner
   * keeps its own copy for adversarial/hunter isolation.)
   */
  private static double exactCircularArcLength(double sx, double sy,
                                               double mx, double my,
                                               double ex, double ey) {
    // Robust, scale-invariant degeneracy test: the three control points
    // define a genuine arc exactly when the mid point lies off the
    // start-end chord. Decide that with the exact-sign Orientation
    // predicate, NOT an absolute determinant threshold. The criterion is
    // the one proven sound in NetTopologySuite.Proofs ArcOrient.v
    // (arc_side_chord_mid_nonzero: mid off the chord <=> valid arc). An
    // absolute |det| < eps test misclassifies valid arcs at small
    // coordinate magnitudes, since det scales as O(coord^2) -- e.g. a
    // radius ~5e-8 arc has |det| ~4e-15 and was wrongly treated as a
    // straight chord (returning ~2e-8 instead of the true ~2.0133e-8).
    Coordinate s = new Coordinate(sx, sy);
    Coordinate m = new Coordinate(mx, my);
    Coordinate e = new Coordinate(ex, ey);
    if (Orientation.index(s, m, e) == Orientation.COLLINEAR) {
      return Math.hypot(ex - sx, ey - sy);
    }
    // Work in a frame translated to the start point. Arc length is
    // translation-invariant, and computing the circumcentre from the
    // (small) local offsets instead of the (large) absolute coordinates
    // avoids catastrophic cancellation for arcs whose extent is tiny
    // relative to their distance from the origin -- otherwise a genuine
    // arc can come out shorter than its chord (violating
    // ArcLength.chord_le_arc_length).
    double mxL = mx - sx, myL = my - sy;
    double exL = ex - sx, eyL = ey - sy;
    double d = 2 * (mxL * eyL - exL * myL);
    double mSq = mxL * mxL + myL * myL;
    double eSq = exL * exL + eyL * eyL;
    double cx = (mSq * eyL - eSq * myL) / d;
    double cy = (eSq * mxL - mSq * exL) / d;
    double r = Math.hypot(cx, cy);
    if (!Double.isFinite(r) || r == 0.0) {
      // Overflow/underflow at extreme magnitudes: fall back to the chord.
      return Math.hypot(ex - sx, ey - sy);
    }
    double a0 = Math.atan2(-cy, -cx);
    double a1 = Math.atan2(myL - cy, mxL - cx);
    double a2 = Math.atan2(eyL - cy, exL - cx);
    // Swept angle of start -> mid -> end. Normalise the mid and end offsets
    // from the start direction into [0, 2*PI). If the mid lies within the
    // CCW span from start to end, the arc is CCW (sweep = endCcw); otherwise
    // it is CW (sweep = 2*PI - endCcw). This is the orientation-robust
    // selection used by CircularArcDensifier and is correct for minor, major
    // and reflex arcs alike -- unlike a (atan2-difference, mid-sign) test,
    // whose angle wrapping is fragile once a2 - a0 falls below -PI.
    double midCcw = normTwoPi(a1 - a0);
    double endCcw = normTwoPi(a2 - a0);
    double theta = (midCcw <= endCcw) ? endCcw : (2 * Math.PI - endCcw);
    return r * theta;
  }

  /** Reduce an angle to {@code [0, 2*PI)}. */
  private static double normTwoPi(double a) {
    double twoPi = 2 * Math.PI;
    a = a % twoPi;
    if (a < 0) a += twoPi;
    return a;
  }
}

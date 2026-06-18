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

import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.util.AffineTransformation;

/**
 * Curve-aware affine transformation (AT-S / AT-NS, JTS #1195).
 * <p>
 * A <b>similarity</b> (rotation, uniform scale, translation, reflection) maps a
 * circle to a circle, so transforming a curved geometry's control points keeps it
 * a valid arc — the ordinary {@link AffineTransformation#transform(Geometry)}
 * already preserves the {@link CircularString} / {@link CurvePolygon} subclass
 * (AT-S). A <b>non-similarity</b> (shear or non-uniform scale) maps a circle to an
 * <i>ellipse</i>, which JTS does not model; transforming the three control points
 * would yield a {@code CircularString} through points that no longer lie on a
 * circle. AT-NS detects this and instead linearises the curve via
 * {@link Linearizable#toLinear(double)} (arc tessellation to the sagitta
 * tolerance) <i>before</i> transforming, so the result is an honest polyline.
 */
public final class CurvedAffineTransformation {

  private CurvedAffineTransformation() {}

  /**
   * Transforms {@code geom} by {@code trans}: arcs are preserved under a similarity,
   * and linearised to {@code tolerance} first under a non-similarity.
   */
  public static Geometry transform(AffineTransformation trans, Geometry geom, double tolerance) {
    if (isSimilarity(trans) || !(geom instanceof Linearizable)) {
      return trans.transform(geom);
    }
    return trans.transform(((Linearizable) geom).toLinear(tolerance));
  }

  /**
   * Whether the transform's linear part is conformal — a uniform scale times a
   * rotation or reflection — i.e. it maps circles to circles. True iff the two
   * column vectors of the 2x2 linear part have equal length and are orthogonal.
   */
  public static boolean isSimilarity(AffineTransformation trans) {
    double[] m = trans.getMatrixEntries();   // m00 m01 m02 m10 m11 m12
    double m00 = m[0], m01 = m[1], m10 = m[3], m11 = m[4];
    double col0 = m00 * m00 + m10 * m10;     // |first column|^2
    double col1 = m01 * m01 + m11 * m11;     // |second column|^2
    double dot  = m00 * m01 + m10 * m11;     // columns dot product
    double scale = Math.max(col0, col1);
    if (scale == 0.0) return false;          // degenerate (collapses to a point)
    double eps = 1e-9 * scale;
    return Math.abs(col0 - col1) <= eps && Math.abs(dot) <= eps;
  }
}

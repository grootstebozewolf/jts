/*
 * Copyright (c) 2026 grootstebozewolf
 * Portions adapted from a 2020 contribution by Jeroen Bloemscheer
 * to a JTS fork (the `CIRCULARSTRING` branch).
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * and Eclipse Distribution License v. 1.0 which accompanies this distribution.
 * The Eclipse Public License is available at http://www.eclipse.org/legal/epl-v20.html
 * and the Eclipse Distribution License is available at
 *
 * http://www.eclipse.org/org/documents/edl-v10.php.
 */
package org.locationtech.jts.awt.curved;

import java.awt.Shape;
import java.awt.geom.GeneralPath;
import java.awt.geom.Point2D;

import org.locationtech.jts.awt.PointShapeFactory;
import org.locationtech.jts.awt.PointTransformation;
import org.locationtech.jts.awt.ShapeWriter;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.curved.CircularString;
import org.locationtech.jts.geom.curved.MultiCurve;

/**
 * A {@link ShapeWriter} that renders the OGC SFA / ISO 19125-2 curve
 * geometry types as smooth visual curves.
 * <p>
 * Phase-1 strategy: each consecutive triple of {@link CircularString}
 * control points is approximated by a single cubic Bezier segment whose
 * mid-control point is the arc mid-point. This is a visual approximation
 * — accurate enough to look like a curve at typical zoom levels but not a
 * geometrically correct arc densification. Use
 * {@link org.locationtech.jts.geom.curved.Linearizable#toLinear(double)}
 * when geometric accuracy matters.
 * <p>
 * Three colinear control points fall back to a straight {@code lineTo}.
 */
public class CurvedShapeWriter extends ShapeWriter {

  public CurvedShapeWriter() {
    super();
  }

  public CurvedShapeWriter(PointTransformation pointTransformer) {
    super(pointTransformer);
  }

  public CurvedShapeWriter(PointTransformation pointTransformer, PointShapeFactory pointFactory) {
    super(pointTransformer, pointFactory);
  }

  @Override
  protected Shape toShapeOther(Geometry geometry) {
    if (geometry instanceof CircularString) return toShape((CircularString) geometry);
    if (geometry instanceof MultiCurve) return toShape((MultiCurve) geometry);
    return null;
  }

  private Shape toShape(MultiCurve mc) {
    GeneralPath path = new GeneralPath();
    for (int i = 0; i < mc.getNumGeometries(); i++) {
      Geometry member = mc.getGeometryN(i);
      if (member instanceof CircularString) {
        path.append(toShape((CircularString) member), false);
      } else if (member instanceof LineString) {
        path.append(toShape(member), false);
      }
    }
    return path;
  }

  private Shape toShape(CircularString cs) {
    GeneralPath shape = new GeneralPath();
    int n = cs.getNumPoints();
    if (n == 0) return shape;

    Point2D start = transformPoint(cs.getCoordinateN(0));
    shape.moveTo((float) start.getX(), (float) start.getY());
    double sx = start.getX(), sy = start.getY();

    // Triples (start, mid, end), (end, mid', end'), ...
    for (int i = 1; i + 1 <= n - 1; i += 2) {
      Coordinate midC = cs.getCoordinateN(i);
      Coordinate endC = cs.getCoordinateN(i + 1);
      Point2D mid = transformPoint(midC);
      Point2D end = transformPoint(endC);
      double mx = mid.getX(), my = mid.getY();
      double ex = end.getX(), ey = end.getY();

      if (isColinear(sx, sy, mx, my, ex, ey)) {
        shape.lineTo((float) ex, (float) ey);
      } else {
        // Cubic-Bezier visual approximation: control points pinned to
        // start, mid (twice), end. Good enough for screen rendering at
        // moderate zoom; replace with arc-correct densification when
        // Linearizable.toLinear() grows real arc support.
        shape.curveTo((float) sx, (float) sy,
                      (float) mx, (float) my,
                      (float) ex, (float) ey);
      }
      sx = ex;
      sy = ey;
    }
    return shape;
  }

  private static boolean isColinear(double x1, double y1,
                                    double x2, double y2,
                                    double x3, double y3) {
    // Cross-product test, tolerance-free (acceptable for screen rendering).
    return (y2 - y1) * (x3 - x1) == (y3 - y1) * (x2 - x1);
  }
}

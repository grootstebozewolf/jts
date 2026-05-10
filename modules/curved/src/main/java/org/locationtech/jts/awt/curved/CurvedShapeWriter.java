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
import org.locationtech.jts.geom.CoordinateSequence;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.curved.CircularString;
import org.locationtech.jts.geom.curved.MultiCurve;

/**
 * A {@link ShapeWriter} that renders the OGC SFA / ISO 19125-2 curve
 * geometry types as cubic Bezier approximations of true circular arcs
 * via {@link CircularArcRenderer}.
 */
public class CurvedShapeWriter extends ShapeWriter {

  /** Identity transformation used when no viewport is supplied (the
   *  base {@link ShapeWriter} instance has its own internal
   *  transformer; we mirror that behaviour by routing through
   *  {@link #transformPoint(Coordinate, Point2D)} via this adaptor). */
  private final PointTransformation transformer = new PointTransformation() {
    @Override
    public void transform(Coordinate model, Point2D view) {
      transformPoint(model, view);
    }
  };

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
    GeneralPath path = new GeneralPath();
    if (cs.isEmpty()) return path;

    CoordinateSequence seq = cs.getCoordinateSequence();
    int n = seq.size();
    if (n < 3) {
      moveToView(path, seq.getCoordinate(0));
      for (int i = 1; i < n; i++) lineToView(path, seq.getCoordinate(i));
      return path;
    }

    moveToView(path, seq.getCoordinate(0));
    for (int i = 0; i + 2 < n; i += 2) {
      CircularArcRenderer.appendArc(path,
          seq.getCoordinate(i),
          seq.getCoordinate(i + 1),
          seq.getCoordinate(i + 2),
          transformer);
    }
    return path;
  }

  private void moveToView(GeneralPath path, Coordinate model) {
    Point2D v = transformPoint(model);
    path.moveTo((float) v.getX(), (float) v.getY());
  }

  private void lineToView(GeneralPath path, Coordinate model) {
    Point2D v = transformPoint(model);
    path.lineTo((float) v.getX(), (float) v.getY());
  }
}

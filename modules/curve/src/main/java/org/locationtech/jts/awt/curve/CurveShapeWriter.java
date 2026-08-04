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
/* Portions of the cubic-Bezier rendering approach were originally
 * prototyped in 2020 on a JTS fork (the `CIRCULARSTRING` branch) by
 * Jeroen Bloemscheer; the implementation here is a substantial
 * rewrite. */
package org.locationtech.jts.awt.curve;

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
import org.locationtech.jts.geom.curve.CircularString;
import org.locationtech.jts.geom.curve.ClothoidSegment;
import org.locationtech.jts.geom.curve.CompoundCurve;
import org.locationtech.jts.geom.curve.MultiCurve;

/**
 * A {@link ShapeWriter} that renders the OGC SFA / ISO 19125-2 curve
 * geometry types as cubic Bezier approximations of true circular arcs
 * via {@link CircularArcRenderer}.
 */
public class CurveShapeWriter extends ShapeWriter {

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

  public CurveShapeWriter() {
    super();
  }

  public CurveShapeWriter(PointTransformation pointTransformer) {
    super(pointTransformer);
  }

  public CurveShapeWriter(PointTransformation pointTransformer, PointShapeFactory pointFactory) {
    super(pointTransformer, pointFactory);
  }

  @Override
  protected Shape toShapeOther(Geometry geometry) {
    if (geometry instanceof CircularString) return toShape((CircularString) geometry);
    if (geometry instanceof CompoundCurve) return toShape((CompoundCurve) geometry);
    if (geometry instanceof MultiCurve) return toShape((MultiCurve) geometry);
    return null;
  }

  private Shape toShape(MultiCurve mc) {
    GeneralPath path = new GeneralPath();
    for (int i = 0; i < mc.getNumGeometries(); i++) {
      Geometry member = mc.getGeometryN(i);
      if (member instanceof CircularString) {
        path.append(toShape((CircularString) member), false);
      } else if (member instanceof CompoundCurve) {
        path.append(toShape((CompoundCurve) member), false);
      } else if (member instanceof LineString) {
        path.append(toShape(member), false);
      }
    }
    return path;
  }

  private Shape toShape(CompoundCurve cc) {
    GeneralPath path = new GeneralPath();
    if (cc.isEmpty()) return path;
    boolean started = false;
    for (int i = 0; i < cc.getNumMembers(); i++) {
      LineString member = cc.getMemberN(i);
      if (member.isEmpty()) continue;
      CoordinateSequence seq = member.getCoordinateSequence();
      if (!started) {
        moveToView(path, seq.getCoordinate(0));
        started = true;
      }
      if (member instanceof ClothoidSegment) {
        appendClothoidSegments(path, (ClothoidSegment) member);
      } else if (member instanceof CircularString) {
        appendCircularStringSegments(path, seq);
      } else {
        for (int j = 1; j < seq.size(); j++) {
          lineToView(path, seq.getCoordinate(j));
        }
      }
    }
    return path;
  }

  private void appendClothoidSegments(GeneralPath path, ClothoidSegment cs) {
    Coordinate[] dense = cs.toLinear(0.5).getCoordinates();
    // dense[0] equals the segment start; pen is already there from the
    // CompoundCurve walker's moveToView / preceding-member tail.
    for (int i = 1; i < dense.length; i++) {
      lineToView(path, dense[i]);
    }
  }

  private void appendCircularStringSegments(GeneralPath path, CoordinateSequence seq) {
    int n = seq.size();
    if (n < 3) {
      for (int i = 1; i < n; i++) lineToView(path, seq.getCoordinate(i));
      return;
    }
    for (int i = 0; i + 2 < n; i += 2) {
      CircularArcRenderer.appendArc(path,
          seq.getCoordinate(i),
          seq.getCoordinate(i + 1),
          seq.getCoordinate(i + 2),
          transformer);
    }
  }

  private Shape toShape(CircularString cs) {
    GeneralPath path = new GeneralPath();
    if (cs.isEmpty()) return path;
    CoordinateSequence seq = cs.getCoordinateSequence();
    moveToView(path, seq.getCoordinate(0));
    appendCircularStringSegments(path, seq);
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

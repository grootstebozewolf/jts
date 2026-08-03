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
import org.locationtech.jts.geom.curved.CompoundCurve;
import org.locationtech.jts.geom.curved.CurvePolygon;
import org.locationtech.jts.geom.curved.MultiCurve;
import org.locationtech.jts.geom.curved.MultiSurface;

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
    if (geometry instanceof CompoundCurve) return toShape((CompoundCurve) geometry);
    if (geometry instanceof MultiCurve) return toShape((MultiCurve) geometry);
    if (geometry instanceof CurvePolygon && hasCurvedRing((CurvePolygon) geometry)) {
      return toShape((CurvePolygon) geometry);
    }
    if (geometry instanceof MultiSurface && hasCurvedMember((MultiSurface) geometry)) {
      return toShape((MultiSurface) geometry);
    }
    return null;
  }

  /**
   * True if any structural ring is a curve. An all-linear CurvePolygon is left
   * to the inherited {@link ShapeWriter} polygon path, so its rendering is
   * unchanged.
   */
  private static boolean hasCurvedRing(CurvePolygon cp) {
    if (cp.isEmpty()) return false;
    if (isCurve(cp.getExteriorCurve())) return true;
    for (int i = 0; i < cp.getNumInteriorRing(); i++) {
      if (isCurve(cp.getInteriorCurveN(i))) return true;
    }
    return false;
  }

  private static boolean hasCurvedMember(MultiSurface ms) {
    for (int i = 0; i < ms.getNumGeometries(); i++) {
      Geometry m = ms.getGeometryN(i);
      if (m instanceof CurvePolygon && hasCurvedRing((CurvePolygon) m)) return true;
    }
    return false;
  }

  private static boolean isCurve(LineString ring) {
    return ring instanceof CircularString || ring instanceof CompoundCurve;
  }

  private Shape toShape(MultiSurface ms) {
    GeneralPath path = new GeneralPath();
    for (int i = 0; i < ms.getNumGeometries(); i++) {
      Geometry m = ms.getGeometryN(i);
      if (m instanceof CurvePolygon && hasCurvedRing((CurvePolygon) m)) {
        path.append(toShape((CurvePolygon) m), false);
      } else {
        path.append(toShape(m), false);
      }
    }
    return path;
  }

  /** Each ring becomes its own closed subpath, so holes render as holes. */
  private Shape toShape(CurvePolygon cp) {
    GeneralPath path = new GeneralPath();
    if (cp.isEmpty()) return path;
    appendRing(path, cp.getExteriorCurve());
    for (int i = 0; i < cp.getNumInteriorRing(); i++) {
      appendRing(path, cp.getInteriorCurveN(i));
    }
    return path;
  }

  private void appendRing(GeneralPath path, LineString ring) {
    if (ring == null || ring.isEmpty()) return;
    if (ring instanceof CompoundCurve) {
      path.append(toShape((CompoundCurve) ring), false);
      path.closePath();
      return;
    }
    CoordinateSequence seq = ring.getCoordinateSequence();
    moveToView(path, seq.getCoordinate(0));
    if (ring instanceof CircularString) {
      appendCircularStringSegments(path, seq);
    } else {
      for (int j = 1; j < seq.size(); j++) {
        lineToView(path, seq.getCoordinate(j));
      }
    }
    path.closePath();
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
      if (member instanceof CircularString) {
        appendCircularStringSegments(path, seq);
      } else {
        for (int j = 1; j < seq.size(); j++) {
          lineToView(path, seq.getCoordinate(j));
        }
      }
    }
    return path;
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

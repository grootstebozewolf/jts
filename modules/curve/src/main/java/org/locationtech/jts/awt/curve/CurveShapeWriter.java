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
import org.locationtech.jts.geom.curve.CircularArcDensifier;
import org.locationtech.jts.geom.curve.CircularString;
import org.locationtech.jts.geom.curve.ClothoidSegment;
import org.locationtech.jts.geom.curve.CompoundCurve;
import org.locationtech.jts.geom.curve.CurvePolygon;
import org.locationtech.jts.geom.curve.MultiCurve;
import org.locationtech.jts.geom.curve.MultiSurface;

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
    if (geometry instanceof CurvePolygon && hasCurveRing((CurvePolygon) geometry)) {
      return toShape((CurvePolygon) geometry);
    }
    if (geometry instanceof MultiSurface && hasCurveMember((MultiSurface) geometry)) {
      return toShape((MultiSurface) geometry);
    }
    return null;
  }

  /**
   * True if any structural ring is a curve. An all-linear CurvePolygon is left
   * to the inherited {@link ShapeWriter} polygon path, so its rendering is
   * unchanged.
   */
  private static boolean hasCurveRing(CurvePolygon cp) {
    if (cp.isEmpty()) return false;
    if (isCurve(cp.getExteriorCurve())) return true;
    for (int i = 0; i < cp.getNumInteriorRing(); i++) {
      if (isCurve(cp.getInteriorCurveN(i))) return true;
    }
    return false;
  }

  private static boolean hasCurveMember(MultiSurface ms) {
    for (int i = 0; i < ms.getNumGeometries(); i++) {
      Geometry m = ms.getGeometryN(i);
      if (m instanceof CurvePolygon && hasCurveRing((CurvePolygon) m)) return true;
    }
    return false;
  }

  private static boolean isCurve(LineString ring) {
    return ring instanceof CircularString || ring instanceof CompoundCurve;
  }

  /**
   * Members are appended into one path, so it carries the same
   * {@link GeneralPath#WIND_EVEN_ODD} rule the members need -- appending copies
   * segments, not winding rules, so a member's own rule would be discarded.
   * <p>
   * This matches how core renders a MultiPolygon: {@code ShapeCollectionPathIterator}
   * reports {@code WIND_EVEN_ODD} across all components. It inherits core's
   * consequence too -- overlapping members cancel where they overlap -- which
   * core documents on {@code ShapeWriter.toShape(Geometry)}.
   */
  private Shape toShape(MultiSurface ms) {
    GeneralPath path = new GeneralPath(GeneralPath.WIND_EVEN_ODD);
    for (int i = 0; i < ms.getNumGeometries(); i++) {
      Geometry m = ms.getGeometryN(i);
      if (m instanceof CurvePolygon && hasCurveRing((CurvePolygon) m)) {
        path.append(toShape((CurvePolygon) m), false);
      } else {
        path.append(toShape(m), false);
      }
    }
    return path;
  }

  /**
   * Each ring becomes its own closed subpath, under
   * {@link GeneralPath#WIND_EVEN_ODD} so that holes render as holes.
   * <p>
   * The rule is not incidental. Under the {@code GeneralPath} default of
   * {@code WIND_NON_ZERO} a hole cancels the shell only when the two rings wind
   * in opposite directions; wound the same way, the winding number inside the
   * hole is 2 and it fills in. WKT does not constrain ring orientation and
   * nothing normalises it on the way in, so relying on the input is not an
   * option.
   * <p>
   * Core reached the same conclusion for the same reason: {@code PolygonShape}
   * builds with {@code WIND_EVEN_ODD}, and
   * {@code ShapeCollectionPathIterator.getWindingRule()} notes that
   * "WIND_NON_ZERO requires that the ring orientation be correct." Matching core
   * also keeps an arc-ringed CurvePolygon consistent with the all-linear one,
   * which falls through to core and has always had its hole.
   */
  private Shape toShape(CurvePolygon cp) {
    GeneralPath path = new GeneralPath(GeneralPath.WIND_EVEN_ODD);
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
      appendThreePointOpenComplementaryClose(path, threePointOpenArc(ring));
      path.closePath();
      return;
    }
    CoordinateSequence seq = ring.getCoordinateSequence();
    moveToView(path, seq.getCoordinate(0));
    if (ring instanceof CircularString) {
      appendCircularStringSegments(path, seq);
      appendThreePointOpenComplementaryClose(path, threePointOpenArc(ring));
    } else {
      for (int j = 1; j < seq.size(); j++) {
        lineToView(path, seq.getCoordinate(j));
      }
    }
    path.closePath();
  }

  /**
   * Paint-only close of an open 3-control CircularString ring.
   * <p>
   * ISO/IEC 13249-3: an open {@code CIRCULARSTRING} is odd and at least
   * three controls; a closed CircularString ring is five tokens
   * first=last. {@code (A,B,C,A)} is not a valid CircularString ring.
   * A 3-point hole therefore stays the open triple in WKT. Closing the
   * path with {@code closePath()} would stroke the chord triangle of
   * those three points. The complementary arc is the unique circumcircle
   * close, and is not written back into the geometry.
   */
  private void appendThreePointOpenComplementaryClose(GeneralPath path,
      CoordinateSequence seq) {
    if (seq == null) return;
    Coordinate closeMid = CircularArcDensifier.complementaryArcMid(
        seq.getCoordinate(2), seq.getCoordinate(1), seq.getCoordinate(0));
    if (closeMid == null) return;
    CircularArcRenderer.appendArc(path,
        seq.getCoordinate(2), closeMid, seq.getCoordinate(0), transformer);
  }

  /**
   * The 3-control open CircularString of a ring, or {@code null}.
   * A single-member CompoundCurve hole is the same 3-point arc.
   * {@code (A,B,A)} is not this: two distinct points do not determine
   * a 13249-3 circle, and a closed CS ring is five tokens.
   */
  private static CoordinateSequence threePointOpenArc(LineString ring) {
    if (ring instanceof CompoundCurve) {
      CompoundCurve cc = (CompoundCurve) ring;
      if (cc.getNumMembers() != 1) return null;
      return threePointOpenArc(cc.getMemberN(0));
    }
    if (!(ring instanceof CircularString)) return null;
    CoordinateSequence seq = ring.getCoordinateSequence();
    if (seq.size() != 3) return null;
    if (seq.getCoordinate(0).equals2D(seq.getCoordinate(2))) return null;
    return seq;
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
      } else if (member instanceof ClothoidSegment) {
        appendLinearized(path, ((ClothoidSegment) member).toLinear(0.5));
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
    Coordinate closeMid = CircularArcDensifier.threePointCircleCloseMid(seq);
    if (closeMid != null) {
      CircularArcRenderer.appendArc(path,
          seq.getCoordinate(n - 2),
          closeMid,
          seq.getCoordinate(0),
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

  private void appendLinearized(GeneralPath path, Geometry linear) {
    Coordinate[] pts = linear.getCoordinates();
    for (int j = 1; j < pts.length; j++) {
      lineToView(path, pts[j]);
    }
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

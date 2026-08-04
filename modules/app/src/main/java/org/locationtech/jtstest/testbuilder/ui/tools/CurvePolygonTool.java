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
package org.locationtech.jtstest.testbuilder.ui.tools;

import java.awt.Shape;
import java.awt.geom.GeneralPath;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.List;

import org.locationtech.jts.awt.PointTransformation;
import org.locationtech.jts.awt.curve.CircularArcRenderer;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jtstest.testbuilder.model.GeometryType;

/**
 * Stream-style mouse-draw tool for
 * {@link org.locationtech.jts.geom.curve.CurvePolygon} geometries. Control
 * points are captured like {@link CircularStringTool} (each triple defines an
 * arc), but on right-click the ring is closed and committed as a
 * {@code CurvePolygon} with a {@code CircularString} shell.
 *
 * <p>The in-progress preview renders completed arcs and a closing hint line
 * back to the first captured point.
 */
public class CurvePolygonTool extends AbstractStreamDrawTool {

  private static CurvePolygonTool singleton = null;

  public static CurvePolygonTool getInstance() {
    if (singleton == null)
      singleton = new CurvePolygonTool();
    return singleton;
  }

  private CurvePolygonTool() {
  }

  @Override
  protected int getGeometryType() {
    return GeometryType.CURVEPOLYGON;
  }

  /**
   * Commits the captured control points as a {@code CurvePolygon}. Requires
   * an odd count &ge; 3 (same rule as CircularString); if even, the trailing
   * point is dropped. The {@link
   * org.locationtech.jtstest.testbuilder.geom.GeometryCombiner#addCurvePolygon}
   * method closes the ring automatically.
   */
  @Override
  protected void bandFinished() throws Exception {
    if (panel().getModel() == null) return;
    panel().getGeomModel().setGeometryType(getGeometryType());

    List<Coordinate> coords = new ArrayList<Coordinate>();
    for (Object o : getCoordinates()) coords.add((Coordinate) o);
    if (coords.size() >= 3 && coords.size() % 2 == 0) {
      coords.remove(coords.size() - 1);
    }
    if (coords.size() < 3) return;

    geomModel().addComponent(coords);
    panel().updateGeom();
  }

  @Override
  protected Shape getShape() {
    List<Coordinate> captured = new ArrayList<Coordinate>();
    for (Object o : getCoordinates()) captured.add((Coordinate) o);
    if (captured.isEmpty()) return null;
    if (tentativeCoordinate != null) captured.add(tentativeCoordinate);
    int n = captured.size();

    int arcPts = (n >= 3) ? (n % 2 == 1 ? n : n - 1) : 0;

    GeneralPath path = new GeneralPath();
    PointTransformation pt = new PointTransformation() {
      @Override
      public void transform(Coordinate model, Point2D view) {
        Point2D out = toView(model);
        view.setLocation(out.getX(), out.getY());
      }
    };

    Point2D first = toView(captured.get(0));
    path.moveTo((float) first.getX(), (float) first.getY());

    if (arcPts >= 3) {
      for (int i = 0; i + 2 < arcPts; i += 2) {
        CircularArcRenderer.appendArc(path,
            captured.get(i),
            captured.get(i + 1),
            captured.get(i + 2),
            pt);
      }
    }

    int trailingStart = (arcPts >= 3) ? arcPts : 1;
    for (int i = trailingStart; i < n; i++) {
      Point2D v = toView(captured.get(i));
      path.lineTo((float) v.getX(), (float) v.getY());
    }

    // Closing hint: dashed line back to the first point
    if (n >= 3) {
      Point2D last = toView(captured.get(n - 1));
      path.moveTo((float) last.getX(), (float) last.getY());
      path.lineTo((float) first.getX(), (float) first.getY());
    }

    drawVertices(path, captured);
    return path;
  }

  private void drawVertices(GeneralPath path, List<Coordinate> coords) {
    for (Coordinate coord : coords) {
      Point2D p = toView(coord);
      path.moveTo((float) (p.getX() - 2), (float) (p.getY() - 2));
      path.lineTo((float) (p.getX() + 2), (float) (p.getY() - 2));
      path.lineTo((float) (p.getX() + 2), (float) (p.getY() + 2));
      path.lineTo((float) (p.getX() - 2), (float) (p.getY() + 2));
      path.lineTo((float) (p.getX() - 2), (float) (p.getY() - 2));
    }
  }
}

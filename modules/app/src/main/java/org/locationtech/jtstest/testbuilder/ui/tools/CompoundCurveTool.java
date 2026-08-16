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
 * Stream-style mouse-draw tool for OGC SFA
 * {@link org.locationtech.jts.geom.curve.CompoundCurve} geometries.
 * Each captured triple becomes one {@code CircularString} member.
 * Members are built through {@code createCircularString} then
 * {@code createCompoundCurve(LineString[])} — never the legacy
 * flat {@code CoordinateSequence} constructor.
 *
 * <p>This slice is CircularString members only (no LineString
 * members, no non-SFA keywords). Double-click commits the stream
 * with the same odd-&ge;3 / drop-trailing-even rule as
 * {@link CircularStringTool}.
 */
public class CompoundCurveTool extends AbstractStreamDrawTool {

  private static CompoundCurveTool singleton = null;

  public static CompoundCurveTool getInstance() {
    if (singleton == null)
      singleton = new CompoundCurveTool();
    return singleton;
  }

  private CompoundCurveTool() {
  }

  @Override
  protected int getGeometryType() {
    return GeometryType.COMPOUNDCURVE;
  }

  @Override
  protected void bandFinished() throws Exception {
    if (panel().getModel() == null) return;
    panel().getGeomModel().setGeometryType(getGeometryType());

    List<Coordinate> coords = new ArrayList<Coordinate>();
    for (Object o : getCoordinates()) {
      coords.add((Coordinate) o);
    }
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

    drawVertices(path, captured);
    drawJoinMarks(path, captured, arcPts);
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

  /** Diamond marks at piece joins (every completed arc end). */
  private void drawJoinMarks(GeneralPath path, List<Coordinate> coords, int arcPts) {
    if (arcPts < 3) return;
    for (int i = 2; i < arcPts; i += 2) {
      Point2D p = toView(coords.get(i));
      path.moveTo((float) p.getX(), (float) (p.getY() - 4));
      path.lineTo((float) (p.getX() + 4), (float) p.getY());
      path.lineTo((float) p.getX(), (float) (p.getY() + 4));
      path.lineTo((float) (p.getX() - 4), (float) p.getY());
      path.lineTo((float) p.getX(), (float) (p.getY() - 4));
    }
  }
}

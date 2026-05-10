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

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jtstest.testbuilder.model.GeometryType;

/**
 * Multi-triangle drawing tool for OGC SFA / ISO 19125-2
 * {@link org.locationtech.jts.geom.curved.Tin} (Triangulated Irregular
 * Network) geometries.
 *
 * <p><b>UX</b>: every triple of clicks adds one triangle patch to the
 * in-progress TIN; double-click finalises. Trailing 1 or 2 unfinished
 * vertices of an incomplete triangle are dropped on commit (consistent
 * with the {@code CircularString} odd-trailing-drop policy).
 *
 * <p><b>Preview</b>: complete triangles render as closed outlines, the
 * trailing in-progress triangle as a polyline hint that the user can
 * see closing toward the next click.
 */
public class TINTool extends AbstractStreamDrawTool {

  private static TINTool singleton = null;

  public static TINTool getInstance() {
    if (singleton == null)
      singleton = new TINTool();
    return singleton;
  }

  private TINTool() {
  }

  @Override
  protected int getGeometryType() {
    return GeometryType.TIN;
  }

  @Override
  protected Shape getShape() {
    List<Coordinate> captured = new ArrayList<Coordinate>();
    for (Object o : getCoordinates()) captured.add((Coordinate) o);
    if (captured.isEmpty()) return null;

    // tentativeCoordinate is the live cursor position when no button is
    // pressed. Showing it as the next vertex of the in-progress triangle
    // gives the user a real-time preview of the triangle being formed.
    List<Coordinate> all = new ArrayList<Coordinate>(captured);
    if (tentativeCoordinate != null) all.add(tentativeCoordinate);
    int n = all.size();

    GeneralPath path = new GeneralPath();
    int completedTriangles = captured.size() / 3;

    for (int t = 0; t < completedTriangles; t++) {
      int base = t * 3;
      Point2D p0 = toView(all.get(base));
      Point2D p1 = toView(all.get(base + 1));
      Point2D p2 = toView(all.get(base + 2));
      path.moveTo((float) p0.getX(), (float) p0.getY());
      path.lineTo((float) p1.getX(), (float) p1.getY());
      path.lineTo((float) p2.getX(), (float) p2.getY());
      path.lineTo((float) p0.getX(), (float) p0.getY());
    }

    int trailingStart = completedTriangles * 3;
    if (trailingStart < n) {
      Point2D p = toView(all.get(trailingStart));
      path.moveTo((float) p.getX(), (float) p.getY());
      for (int i = trailingStart + 1; i < n; i++) {
        Point2D q = toView(all.get(i));
        path.lineTo((float) q.getX(), (float) q.getY());
      }
    }

    drawVertices(path, captured);
    return path;
  }

  @Override
  protected void bandFinished() throws Exception {
    if (panel().getModel() == null) return;
    panel().getGeomModel().setGeometryType(getGeometryType());

    List<Coordinate> coords = new ArrayList<Coordinate>();
    for (Object o : getCoordinates()) coords.add((Coordinate) o);
    int complete = coords.size() / 3;
    if (complete < 1) return;
    if (coords.size() != complete * 3) {
      coords = new ArrayList<Coordinate>(coords.subList(0, complete * 3));
    }

    geomModel().addComponent(coords);
    panel().updateGeom();
  }

  /** Square markers at each captured vertex (matches LineBandTool's
   *  vertex decoration so users can see anchor points). */
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

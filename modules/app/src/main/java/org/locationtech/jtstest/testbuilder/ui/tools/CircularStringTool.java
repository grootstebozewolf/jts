/*
 * Copyright (c) 2026 grootstebozewolf
 * Adapted from a 2020 contribution by Jeroen Bloemscheer to a JTS fork
 * (the `CIRCULARSTRING` branch).
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
import org.locationtech.jts.awt.curved.CircularArcRenderer;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jtstest.testbuilder.model.GeometryType;

/**
 * Stream-style mouse-draw tool for {@link
 * org.locationtech.jts.geom.curved.CircularString} geometries. Each
 * captured triple of points becomes one circular arc.
 *
 * <p>Overrides {@link #getShape()} so that the in-progress preview is
 * rendered as actual arcs — using the same {@link CircularArcRenderer}
 * that {@link org.locationtech.jts.awt.curved.CurvedShapeWriter} uses
 * for finished geometry — instead of straight line segments between
 * control points.
 */
public class CircularStringTool extends AbstractStreamDrawTool {

  private static CircularStringTool singleton = null;

  public static CircularStringTool getInstance() {
    if (singleton == null)
      singleton = new CircularStringTool();
    return singleton;
  }

  private CircularStringTool() {
  }

  @Override
  protected int getGeometryType() {
    return GeometryType.CIRCULARSTRING;
  }

  /**
   * Per OGC SFA, a CIRCULARSTRING must contain an odd number of points
   * &ge; 3 (each consecutive (start, mid, end) triple defines one arc).
   * If the user releases on an even count, the trailing point is not
   * anchored to a complete arc, so we drop it before committing rather
   * than emit an invalid geometry. If fewer than 3 points were captured
   * we abort without committing — the geometry would be degenerate.
   *
   * <p>This complements the rubber-band preview, which already renders
   * a complete triple as an arc and any trailing odd point as a straight
   * "what comes next" hint, so the visual cue and the commit semantics
   * agree.
   */
  @Override
  protected void bandFinished() throws Exception {
    if (panel().getModel() == null) return;
    panel().getGeomModel().setGeometryType(getGeometryType());

    java.util.List<org.locationtech.jts.geom.Coordinate> coords =
        new java.util.ArrayList<org.locationtech.jts.geom.Coordinate>();
    for (Object o : getCoordinates()) {
      coords.add((org.locationtech.jts.geom.Coordinate) o);
    }
    if (coords.size() >= 3 && coords.size() % 2 == 0) {
      coords.remove(coords.size() - 1);
    }
    if (coords.size() < 3) return;

    geomModel().addComponent(coords);
    panel().updateGeom();
  }

  /**
   * Renders the in-progress band as cubic-Bezier arcs through every
   * complete (start, mid, end) triple of captured control points,
   * with a straight-line "what-comes-next" hint for any trailing
   * control point that does not yet form a complete triple.
   */
  @Override
  protected Shape getShape() {
    List<Coordinate> captured = new ArrayList<Coordinate>();
    for (Object o : getCoordinates()) captured.add((Coordinate) o);
    if (captured.isEmpty()) return null;
    if (tentativeCoordinate != null) captured.add(tentativeCoordinate);
    int n = captured.size();

    // Number of points consumed by complete arcs: largest odd value
    // <= n that is also >= 3 (each new triple starts at an even index
    // and shares its first point with the previous triple's end).
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

    // Trailing partial: lineTo every captured point past the last arc end.
    int trailingStart = (arcPts >= 3) ? arcPts : 1;
    for (int i = trailingStart; i < n; i++) {
      Point2D v = toView(captured.get(i));
      path.lineTo((float) v.getX(), (float) v.getY());
    }

    drawVertices(path, captured);
    return path;
  }

  /** Square markers at each captured control point. Mirrors
   *  {@link LineBandTool}'s vertex decoration so the user can see
   *  which points anchor the arcs. */
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

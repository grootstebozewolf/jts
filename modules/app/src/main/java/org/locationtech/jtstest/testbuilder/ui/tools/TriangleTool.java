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

import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jtstest.testbuilder.model.GeometryType;

/**
 * Click-to-place tool for OGC SFA / ISO 19125-2
 * {@link org.locationtech.jts.geom.curved.Triangle} geometries.
 *
 * <p>The user clicks three corners; the tool auto-finishes on the
 * third click (no need for a closing double-click). The result is
 * committed as a Triangle (a Polygon with a single 4-point closed
 * exterior ring and no holes), produced via
 * {@link org.locationtech.jtstest.testbuilder.geom.GeometryCombiner#addTriangle}.
 *
 * <p>Stream-drag (button-down drag) is intentionally not used here:
 * a triangle is defined by exactly three vertices, so capturing
 * many points would just be ignored. Three left-clicks is the model.
 */
public class TriangleTool extends AbstractStreamDrawTool {

  private static TriangleTool singleton = null;

  public static TriangleTool getInstance() {
    if (singleton == null)
      singleton = new TriangleTool();
    return singleton;
  }

  private TriangleTool() {
  }

  @Override
  protected int getGeometryType() {
    return GeometryType.TRIANGLE;
  }

  /**
   * After every click, check whether we already have three vertices.
   * If so, finish the gesture immediately so the user doesn't need
   * to double-click to commit the triangle.
   */
  @Override
  public void mousePressed(MouseEvent e) {
    super.mousePressed(e);
    try {
      if (e.getClickCount() == 1 && getCoordinates().size() >= 3) {
        finishGesture();
      }
    } catch (Exception ignored) {
    }
  }

  /**
   * Override commit to drop any over-capture (only the first three
   * vertices should anchor the triangle), and to abort if we somehow
   * have fewer than three vertices.
   */
  @Override
  protected void bandFinished() throws Exception {
    if (panel().getModel() == null) return;
    panel().getGeomModel().setGeometryType(getGeometryType());

    List<Coordinate> coords = new ArrayList<Coordinate>();
    for (Object o : getCoordinates()) coords.add((Coordinate) o);
    if (coords.size() < 3) return;
    if (coords.size() > 3) {
      coords = new ArrayList<Coordinate>(coords.subList(0, 3));
    }
    geomModel().addComponent(coords);
    panel().updateGeom();
  }
}

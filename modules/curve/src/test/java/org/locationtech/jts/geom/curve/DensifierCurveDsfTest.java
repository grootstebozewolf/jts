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
package org.locationtech.jts.geom.curve;

import org.locationtech.jts.densify.Densifier;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.io.curve.CurveWKTReader;

import junit.framework.TestCase;
import junit.textui.TestRunner;

/**
 * DSF: {@link Densifier} densifies CircularString via {@code toLinear}.
 */
public class DensifierCurveDsfTest extends TestCase {

  public static void main(String[] args) {
    TestRunner.run(DensifierCurveDsfTest.class);
  }

  public DensifierCurveDsfTest(String name) {
    super(name);
  }

  public void testCircularStringDensifyLiesOnArc() throws Exception {
    Geometry cs = new CurveWKTReader().read("CIRCULARSTRING (5 0, 0 5, -5 0)");
    Geometry dens = Densifier.densify(cs, 0.5);
    assertTrue(dens instanceof LineString);
    assertTrue(dens.getNumPoints() > 3);
    // Every sample near the circle centre (0,0) radius 5
    Coordinate[] pts = dens.getCoordinates();
    for (int i = 0; i < pts.length; i++) {
      double r = Math.hypot(pts[i].x, pts[i].y);
      assertEquals("sample on arc", 5.0, r, 1.0e-6);
    }
  }

  public void testPlainLineStringUnchangedPath() {
    Geometry ls = new CurveGeometryFactory().createLineString(new Coordinate[] {
        new Coordinate(0, 0), new Coordinate(10, 0)
    });
    Geometry dens = Densifier.densify(ls, 2.0);
    assertTrue(dens.getNumPoints() >= 6);
    for (int i = 0; i < dens.getNumPoints(); i++) {
      assertEquals(0.0, dens.getCoordinates()[i].y, 0.0);
    }
  }
}

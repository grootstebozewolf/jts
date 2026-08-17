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

import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.io.curve.CurveWKTReader;
import org.locationtech.jts.triangulate.DelaunayTriangulationBuilder;
import org.locationtech.jts.triangulate.VoronoiDiagramBuilder;

import junit.framework.TestCase;
import junit.textui.TestRunner;

/**
 * TRI-DT / TRI-VR: curve sites densify via toLinear before triangulation.
 */
public class TriangulationCurveSitesTest extends TestCase {

  public static void main(String[] args) {
    TestRunner.run(TriangulationCurveSitesTest.class);
  }

  public TriangulationCurveSitesTest(String name) {
    super(name);
  }

  public void testDelaunayUsesArcSitesNotJustControls() throws Exception {
    Geometry arc = new CurveWKTReader().read("CIRCULARSTRING (5 0, 0 5, -5 0)");
    DelaunayTriangulationBuilder dt = new DelaunayTriangulationBuilder();
    dt.setSites(arc);
    Geometry tris = dt.getTriangles(arc.getFactory());
    assertTrue(tris.getNumGeometries() > 1);
    // Control-only sites would yield a single thin triangle from 3 points.
    assertTrue("arc densify must create more than one triangle",
        tris.getNumGeometries() >= 3);
  }

  public void testVoronoiAcceptsCurvePolygon() throws Exception {
    Geometry disc = new CurveWKTReader().read(
        "CURVEPOLYGON (CIRCULARSTRING (-5 0, 0 5, 5 0, 0 -5, -5 0))");
    VoronoiDiagramBuilder vb = new VoronoiDiagramBuilder();
    vb.setSites(disc);
    Geometry diagram = vb.getDiagram(disc.getFactory());
    assertNotNull(diagram);
    assertTrue(diagram.getNumGeometries() > 0);
  }
}

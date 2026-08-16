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
package org.locationtech.jts.operation.overlayng;

import java.util.List;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.PrecisionModel;
import org.locationtech.jts.noding.CircularNodedSegmentString;
import org.locationtech.jts.noding.NodedSegmentString;

import junit.textui.TestRunner;
import test.jts.GeometryTestCase;

/**
 * OverlayNG-for-circles: {@link EdgeNodingBuilder} accepts a
 * {@link CircularNodedSegmentString}. Stock IntersectionAdder must
 * not treat the arc ends as a chord; the builder still nodes and
 * merges the linear partner.
 */
public class EdgeNodingBuilderCircularTest extends GeometryTestCase {

  public static void main(String[] args) {
    TestRunner.run(EdgeNodingBuilderCircularTest.class);
  }

  public EdgeNodingBuilderCircularTest(String name) {
    super(name);
  }

  public void testAcceptsCircularNodedSegmentString() {
    PrecisionModel pm = new PrecisionModel();
    EdgeNodingBuilder builder = new EdgeNodingBuilder(pm, null);
    EdgeSourceInfo info0 = new EdgeSourceInfo(0, 1, false);
    EdgeSourceInfo info1 = new EdgeSourceInfo(1, 1, false);
    CircularNodedSegmentString arc = new CircularNodedSegmentString(
        new Coordinate(-5, 0), new Coordinate(0, 5),
        new Coordinate(5, 0), info0);
    NodedSegmentString diameter = new NodedSegmentString(
        new Coordinate[] { new Coordinate(5, 0), new Coordinate(-5, 0) },
        info1);
    builder.addEdge(arc);
    builder.addEdge(diameter);
    List<Edge> edges = builder.buildPrepared();
    assertTrue("noded edges from prepared circular + chord",
        edges.size() >= 1);
    assertTrue(builder.hasEdgesFor(0));
    assertTrue(builder.hasEdgesFor(1));
  }

  public void testPreparedInputOnOverlayNG() {
    PrecisionModel pm = new PrecisionModel();
    OverlayNG ov = new OverlayNG(
        read("POLYGON ((0 0, 2 0, 2 2, 0 2, 0 0))"),
        read("POLYGON ((1 0, 3 0, 3 2, 1 2, 1 0))"),
        pm, OverlayNG.INTERSECTION);
    EdgeSourceInfo a = new EdgeSourceInfo(0, 1, false);
    EdgeSourceInfo b = new EdgeSourceInfo(1, 1, false);
    List<NodedSegmentString> prepared =
        new java.util.ArrayList<NodedSegmentString>();
    prepared.add(new NodedSegmentString(new Coordinate[] {
        new Coordinate(0, 0), new Coordinate(2, 0),
        new Coordinate(2, 2), new Coordinate(0, 2),
        new Coordinate(0, 0)
    }, a));
    prepared.add(new NodedSegmentString(new Coordinate[] {
        new Coordinate(1, 0), new Coordinate(3, 0),
        new Coordinate(3, 2), new Coordinate(1, 2),
        new Coordinate(1, 0)
    }, b));
    ov.setInputEdges(prepared);
    org.locationtech.jts.geom.Geometry result = ov.getResult();
    assertFalse(result.isEmpty());
    assertEquals(2.0, result.getArea(), 1.0e-9);
  }
}

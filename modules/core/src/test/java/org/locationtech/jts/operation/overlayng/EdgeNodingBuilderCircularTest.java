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

import junit.textui.TestRunner;
import test.jts.GeometryTestCase;

/**
 * OverlayNG-for-circles surface: {@link EdgeNodingBuilder}.
 * Stock extract is linearized. Prepared exact arcs are accepted.
 * IntersectionAdder collapses a segment only when
 * {@code mayCollapseToChord} is true.
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
    CircularNodedSegmentString arc = CircularNodedSegmentString.arc(
        new Coordinate(-5, 0), new Coordinate(0, 5),
        new Coordinate(5, 0), info0);
    CircularNodedSegmentString diameter = CircularNodedSegmentString.certified(
        new Coordinate(5, 0), new Coordinate(-5, 0), info1);
    builder.addEdge(arc);
    builder.addEdge(diameter);
    assertTrue("OverlayNG may consume exact edges", builder.hasExactSegment());
    List<Edge> edges = builder.buildPrepared();
    assertTrue("noded edges from prepared circular + certified",
        edges.size() >= 1);
    assertTrue(builder.hasEdgesFor(0));
    assertTrue(builder.hasEdgesFor(1));
  }

  public void testStockExtractIsLinearized() {
    EdgeNodingBuilder builder = new EdgeNodingBuilder(new PrecisionModel(),
        null);
    builder.build(
        read("POLYGON ((0 0, 1 0, 1 1, 0 1, 0 0))"),
        read("POLYGON ((2 0, 3 0, 3 1, 2 1, 2 0))"));
    assertFalse("stock extract stays linearized", builder.hasExactSegment());
  }
}

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
package org.locationtech.jts.geom.curved;

import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.io.curved.CurvedWKTReader;

import junit.textui.TestRunner;
import test.jts.GeometryTestCase;

/**
 * CRV-ENV: a curve's envelope must cover the arc, not just its control points.
 * <p>
 * {@link CircularString} inherits {@code LineString.computeEnvelopeInternal()},
 * which expands over the coordinate sequence. For a semicircle that happens to
 * be right -- the extremum is the middle control point -- so the bug hides. It
 * shows on any arc sweeping more than 180 degrees, where an axis extremum falls
 * strictly between control points.
 * <p>
 * The 270-degree arc below runs anticlockwise on the unit circle from 0 through
 * 135 to 270 degrees, so it passes the top (0,1) at 90 degrees and the left
 * (-1,0) at 180 degrees. Neither is a control point, so the inherited envelope
 * clips the arc on two sides.
 * <p>
 * A too-small envelope is worse than a wrong measurement: envelopes gate
 * spatial-index candidate selection and the short-circuit tests in
 * intersects/distance, so anything indexed on it can miss real hits.
 */
public class CurveEnvelopeTest extends GeometryTestCase {

  private static final double R = Math.sqrt(0.5);

  /** 270-degree unit arc: 0 -> 135 -> 270 degrees, anticlockwise. */
  private static final String ARC_270 =
      "CIRCULARSTRING (1 0, " + (-R) + " " + R + ", 0 -1)";

  private static final double TOL = 1.0e-9;

  public static void main(String[] args) {
    TestRunner.run(CurveEnvelopeTest.class);
  }

  public CurveEnvelopeTest(String name) { super(name); }

  private static Geometry readCurve(String wkt) throws Exception {
    return new CurvedWKTReader().read(wkt);
  }

  /** The 270-degree arc reaches the top of the circle, between control points. */
  public void testArcEnvelopeReachesTop() throws Exception {
    Envelope env = readCurve(ARC_270).getEnvelopeInternal();
    assertEquals("arc passes (0,1) at 90 degrees", 1.0, env.getMaxY(), 1.0e-9);
  }

  /** And the left of the circle, also between control points. */
  public void testArcEnvelopeReachesLeft() throws Exception {
    Envelope env = readCurve(ARC_270).getEnvelopeInternal();
    assertEquals("arc passes (-1,0) at 180 degrees", -1.0, env.getMinX(), 1.0e-9);
  }

  /** The full envelope of the 270-degree arc is the unit square. */
  public void testArcEnvelopeIsUnitSquare() throws Exception {
    Envelope env = readCurve(ARC_270).getEnvelopeInternal();
    assertEquals(-1.0, env.getMinX(), 1.0e-9);
    assertEquals(1.0, env.getMaxX(), 1.0e-9);
    assertEquals(-1.0, env.getMinY(), 1.0e-9);
    assertEquals(1.0, env.getMaxY(), 1.0e-9);
  }

  /** The envelope must contain every densified point on the arc. */
  public void testEnvelopeContainsDensifiedArc() throws Exception {
    Geometry arc = readCurve(ARC_270);
    Envelope env = arc.getEnvelopeInternal();
    Geometry dense = ((Linearizable) arc).toLinear(1.0e-4);
    assertTrue("envelope " + env + " must cover the densified arc "
        + dense.getEnvelopeInternal(),
        env.covers(dense.getEnvelopeInternal()));
  }

  /** A CurvePolygon with a full circular ring spans the whole circle. */
  public void testCurvePolygonEnvelopeCoversCircle() throws Exception {
    Envelope env = readCurve(
        "CURVEPOLYGON (CIRCULARSTRING (2 0, 0 2, -2 0, 0 -2, 2 0))")
        .getEnvelopeInternal();
    assertEquals(-2.0, env.getMinX(), 1.0e-9);
    assertEquals(2.0, env.getMaxX(), 1.0e-9);
    assertEquals(-2.0, env.getMinY(), 1.0e-9);
    assertEquals(2.0, env.getMaxY(), 1.0e-9);
  }

  /** Guard: for a semicircle the control points already bound the arc. */
  public void testSemicircleEnvelopeUnchanged() throws Exception {
    Envelope env = readCurve("CIRCULARSTRING (0 0, 2 2, 4 0)").getEnvelopeInternal();
    assertEquals(0.0, env.getMinX(), TOL);
    assertEquals(4.0, env.getMaxX(), TOL);
    assertEquals(0.0, env.getMinY(), TOL);
    assertEquals(2.0, env.getMaxY(), TOL);
  }

  /** Guard: colinear control points describe a straight segment. */
  public void testColinearEnvelopeUnchanged() throws Exception {
    Envelope env = readCurve("CIRCULARSTRING (0 0, 1 0, 2 0)").getEnvelopeInternal();
    assertEquals(0.0, env.getMinX(), TOL);
    assertEquals(2.0, env.getMaxX(), TOL);
    assertEquals(0.0, env.getMinY(), TOL);
    assertEquals(0.0, env.getMaxY(), TOL);
  }

  /** Guard: an empty arc has an empty envelope. */
  public void testEmptyEnvelope() throws Exception {
    assertTrue(readCurve("CIRCULARSTRING EMPTY").getEnvelopeInternal().isNull());
  }
}

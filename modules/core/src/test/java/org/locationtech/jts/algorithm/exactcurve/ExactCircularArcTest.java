/*
 * Copyright (c) 2026 Martin Davis and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * and Eclipse Distribution License v. 1.0 which accompanies this distribution.
 * The Eclipse Public License is available at http://www.eclipse.org/legal/epl-v20.html
 * and the Eclipse Distribution License is available at
 *
 * http://www.eclipse.org/org/documents/edl-v10.php.
 */
package org.locationtech.jts.algorithm.exactcurve;

import java.util.ArrayList;
import java.util.List;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Envelope;

import junit.framework.TestCase;
import junit.framework.TestSuite;
import junit.textui.TestRunner;

/**
 * Tests for {@link ExactCircularArc} collinear degrade and exact length.
 *
 * @author Martin Davis
 */
public class ExactCircularArcTest extends TestCase {

  public static void main(String[] args) {
    TestRunner.run(new TestSuite(ExactCircularArcTest.class));
  }

  public ExactCircularArcTest(String name) {
    super(name);
  }

  public void testCollinearIsChord() {
    ExactCircularArc arc = new ExactCircularArc(
        new Coordinate(0, 0), new Coordinate(1, 0), new Coordinate(2, 0));
    assertTrue(arc.isCollinear());
    assertNull(arc.getCenter());
    assertEquals(2.0, arc.getLength(), 1e-15);

    List<Coordinate> pts = new ArrayList<Coordinate>();
    arc.appendLinearized(pts, 0.001, true);
    assertEquals(3, pts.size());
    assertTrue(pts.get(0).equals2D(new Coordinate(0, 0)));
    assertTrue(pts.get(1).equals2D(new Coordinate(1, 0)));
    assertTrue(pts.get(2).equals2D(new Coordinate(2, 0)));
  }

  public void testSemicircleLengthAndEnvelope() {
    ExactCircularArc arc = new ExactCircularArc(
        new Coordinate(1, 0), new Coordinate(0, 1), new Coordinate(-1, 0));
    assertFalse(arc.isCollinear());
    assertEquals(1.0, arc.getRadius(), 1e-10);
    assertEquals(Math.PI, arc.getLength(), 1e-10);

    Envelope env = new Envelope();
    arc.expandEnvelope(env);
    assertEquals(-1.0, env.getMinX(), 1e-10);
    assertEquals(1.0, env.getMaxX(), 1e-10);
    assertEquals(0.0, env.getMinY(), 1e-10);
    assertEquals(1.0, env.getMaxY(), 1e-10);
  }

  public void testFullCircle() {
    ExactCircularArc arc = new ExactCircularArc(
        new Coordinate(0, 0), new Coordinate(1, 0), new Coordinate(0, 0));
    assertTrue(arc.isFullCircle());
    assertFalse(arc.isCollinear());
    assertEquals(0.5, arc.getRadius(), 1e-12);
    assertEquals(Math.PI, arc.getLength(), 1e-12);
  }

  public void testDegeneratePoint() {
    ExactCircularArc arc = new ExactCircularArc(
        new Coordinate(3, 4), new Coordinate(3, 4), new Coordinate(3, 4));
    assertTrue(arc.isCollinear());
    assertEquals(0.0, arc.getLength(), 0.0);
  }
}

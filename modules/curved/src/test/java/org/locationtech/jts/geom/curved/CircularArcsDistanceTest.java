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

import junit.framework.TestCase;
import junit.textui.TestRunner;

/**
 * D-PT (#1195): {@link CircularArcs#distancePointToArc} is the analytical
 * shortest distance from a point to a circular arc, clamped to the arc's sweep
 * (so a foot outside the span falls back to the nearer endpoint). Verified
 * against exact closed-form distances. Arc in most cases: upper semicircle R=5
 * about the origin, (5,0)-(0,5)-(-5,0).
 */
public class CircularArcsDistanceTest extends TestCase {

  public static void main(String args[]) {
    TestRunner.run(CircularArcsDistanceTest.class);
  }

  public CircularArcsDistanceTest(String name) { super(name); }

  private double dist(double px, double py) {
    return CircularArcs.distancePointToArc(5,0, 0,5, -5,0, px, py);
  }

  public void testOutsideOnRadial() {
    assertEquals(5.0, dist(0, 10), 1e-12);   // foot (0,5) on arc; |10-5|
  }

  public void testInsideOnRadial() {
    assertEquals(1.0, dist(0, 4), 1e-12);    // foot (0,5) on arc; |4-5|
  }

  public void testAtCentre() {
    assertEquals(5.0, dist(0, 0), 1e-12);    // every arc point is r=5 away
  }

  public void testOffSpanClampsToEndpoint() {
    // foot of (10,-1) is on the lower (excluded) semicircle -> nearest endpoint (5,0)
    assertEquals(Math.sqrt(26), dist(10, -1), 1e-12);
  }

  public void testGeneralInteriorPoint() {
    // P=(3,3): foot at 45 deg on the upper arc; distance = |sqrt(18) - 5|
    assertEquals(Math.abs(Math.sqrt(18) - 5), dist(3, 3), 1e-12);
  }

  public void testPointOnArcIsZero() {
    assertEquals(0.0, dist(0, 5), 1e-12);
  }

  public void testMatchesDensifiedArc() {
    // independent cross-check: min distance to a finely sampled arc polyline
    double px = -2.3, py = 6.1;
    double ref = Double.MAX_VALUE;
    int N = 20000;
    for (int k = 0; k <= N; k++) {
      double ang = Math.PI * k / N;          // 0..pi (upper semicircle)
      double x = 5 * Math.cos(ang), y = 5 * Math.sin(ang);
      ref = Math.min(ref, Math.hypot(px - x, py - y));
    }
    assertEquals(ref, dist(px, py), 1e-3);
  }
}

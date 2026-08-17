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
package org.locationtech.jts.algorithm.exactarc;

import org.locationtech.jts.geom.Coordinate;

import junit.framework.TestCase;
import junit.textui.TestRunner;

public class AngleBetweenTest extends TestCase {

  public static void main(String[] args) {
    TestRunner.run(AngleBetweenTest.class);
  }

  public AngleBetweenTest(String name) {
    super(name);
  }

  public void testSignedShortQuarter() {
    assertEquals(Math.PI / 2.0, AngleBetween.signedShort(1, 0, 0, 1), 1.0e-15);
    assertEquals(-Math.PI / 2.0, AngleBetween.signedShort(1, 0, 0, -1), 1.0e-15);
  }

  public void testDirectedSemicircle() {
    double s = AngleBetween.directedSweep(0, 0,
        new Coordinate(5, 0), new Coordinate(0, 5), new Coordinate(-5, 0));
    assertEquals(Math.PI, s, 1.0e-12);
  }

  public void testDirectedLongWay() {
    // Mid on the lower half → the long CCW (or CW) turn is 3π/2? 
    // start (5,0), mid (0,-5), end (-5,0) is the lower semicircle, sweep π.
    double s = AngleBetween.directedSweep(0, 0,
        new Coordinate(5, 0), new Coordinate(0, -5), new Coordinate(-5, 0));
    assertEquals(Math.PI, s, 1.0e-12);
  }

  public void testNormalizePositive() {
    assertEquals(0.0, AngleBetween.normalizePositive(0.0), 0.0);
    assertEquals(Math.PI, AngleBetween.normalizePositive(-Math.PI), 1.0e-15);
  }
}

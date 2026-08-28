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
package org.locationtech.jtstest.testbuilder.ui.style;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.LinearRing;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.io.ParseException;
import org.locationtech.jts.io.curve.CurveWKTReader;

import junit.framework.TestCase;
import junit.textui.TestRunner;

/**
 * #114 witness is four-item {@code (A, B, C, A)}. Not in the PostGIS
 * model. JTS-only annulus exception reverted. Do not invent a 5th
 * control. Not a paint remake.
 */
public class LineStringStyleCurveHoleTest extends TestCase {

  private static final String WITNESS =
      "CURVEPOLYGON (CIRCULARSTRING (60 380, 240 440, 404 326, 60 380), "
      + "CIRCULARSTRING (141 84, 270 28, 170 290, 141 84))";

  public static void main(String[] args) {
    TestRunner.run(LineStringStyleCurveHoleTest.class);
  }

  public LineStringStyleCurveHoleTest(String name) { super(name); }

  public void testWitnessFourItemRejected() {
    try {
      new CurveWKTReader().read(WITNESS);
      fail("Expected reject 4-item CIRCULARSTRING (A, B, C, A)");
    } catch (ParseException e) {
      assertTrue(e.getMessage().indexOf("odd") >= 0
          || e.getMessage().indexOf("Four-item") >= 0);
    } catch (Exception e) {
      fail("unexpected: " + e);
    }
  }

  /** Guard: a plain polygon still uses the LinearRing views. */
  public void testPlainPolygonUnchanged() {
    org.locationtech.jts.geom.GeometryFactory factory =
        new org.locationtech.jts.geom.GeometryFactory();
    LinearRing shell = factory.createLinearRing(new Coordinate[] {
        new Coordinate(-5, -5), new Coordinate(5, -5), new Coordinate(5, 5),
        new Coordinate(-5, 5), new Coordinate(-5, -5)
    });
    LinearRing hole = factory.createLinearRing(new Coordinate[] {
        new Coordinate(-1, -1), new Coordinate(1, -1), new Coordinate(1, 1),
        new Coordinate(-1, 1), new Coordinate(-1, -1)
    });
    Polygon p = factory.createPolygon(shell, new LinearRing[] { hole });
    assertSame(shell, LineStringStyle.exteriorRing(p));
    assertSame(hole, LineStringStyle.interiorRing(p, 0));
  }
}

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
package org.locationtech.jts.io;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.CoordinateSequence;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;

import junit.framework.Test;
import junit.framework.TestSuite;
import junit.textui.TestRunner;
import test.jts.GeometryTestCase;

/**
 * Proves the core {@link WKBReader} switch dispatches ISO/OGC type 8
 * to {@link GeometryFactory#createCircularString} without importing
 * jts-curve. The test factory returns a LineString only to observe
 * the dispatch; that is not a claim that core built a CircularString.
 */
public class WKBReaderCurveTypeDispatchTest extends GeometryTestCase {

  /**
   * Locked XDR / 2D / no-SRID hex for
   * {@code CIRCULARSTRING (0 0, 5 5, 10 0)}.
   */
  private static final String HEX_CIRCULARSTRING =
      "000000000800000003000000000000000000000000000000004014000000000000401400000000000040240000000000000000000000000000";

  public static void main(String args[]) {
    TestRunner.run(suite());
  }

  public static Test suite() { return new TestSuite(WKBReaderCurveTypeDispatchTest.class); }

  public WKBReaderCurveTypeDispatchTest(String name) { super(name); }

  public void testType8DispatchesToFactoryCreateCircularString() throws Exception {
    GeometryFactory factory = new GeometryFactory() {
      @Override
      public LineString createCircularString(CoordinateSequence points) {
        return createLineString(points);
      }
    };
    Geometry g = new WKBReader(factory).read(WKBReader.hexToBytes(HEX_CIRCULARSTRING));
    assertEquals(3, g.getNumPoints());
    Coordinate[] c = g.getCoordinates();
    assertEquals(0.0, c[0].x, 0.0);
    assertEquals(0.0, c[0].y, 0.0);
    assertEquals(5.0, c[1].x, 0.0);
    assertEquals(5.0, c[1].y, 0.0);
    assertEquals(10.0, c[2].x, 0.0);
    assertEquals(0.0, c[2].y, 0.0);
    assertTrue(g instanceof LineString);
    assertFalse("dispatch proof only — core must not claim a CircularString",
        g.getClass().getName().indexOf("CircularString") >= 0);
  }

  public void testDefaultFactoryType8MentionsCurveFactory() {
    try {
      new WKBReader().read(WKBReader.hexToBytes(HEX_CIRCULARSTRING));
      fail("Expected ParseException from default WKBReader for type 8");
    } catch (Throwable e) {
      assertTrue("Expected ParseException, got: " + e, e instanceof ParseException);
      String msg = e.getMessage();
      assertTrue(msg, msg.indexOf("Unknown WKB type 8") < 0);
      assertTrue(msg, msg.indexOf("8") >= 0);
      String lower = msg.toLowerCase();
      assertTrue(msg, lower.indexOf("factory") >= 0);
      assertTrue(msg, lower.indexOf("curve") >= 0);
    }
  }

  public void testType99StillUnknown() {
    try {
      new WKBReader().read(WKBReader.hexToBytes("0163000000"));
      fail("Expected ParseException for unknown type 99");
    } catch (Throwable e) {
      assertTrue("Expected ParseException, got: " + e, e instanceof ParseException);
      assertTrue(e.getMessage().indexOf("Unknown WKB type 99") >= 0);
    }
  }

  /**
   * GEO-TIN WKB 15–17 (Triangle / PolyhedralSurface / TIN) waits
   * Architect SIGN. Unknown type throws.
   */
  public void testType15_16_17StillUnknown() {
    assertUnknownType("010F000000", 15);
    assertUnknownType("0110000000", 16);
    assertUnknownType("0111000000", 17);
  }

  private static void assertUnknownType(String hex, int typeCode) {
    try {
      new WKBReader().read(WKBReader.hexToBytes(hex));
      fail("Expected ParseException for unknown type " + typeCode);
    } catch (Throwable e) {
      assertTrue("Expected ParseException, got: " + e, e instanceof ParseException);
      assertTrue(e.getMessage().indexOf("Unknown WKB type " + typeCode) >= 0);
    }
  }
}

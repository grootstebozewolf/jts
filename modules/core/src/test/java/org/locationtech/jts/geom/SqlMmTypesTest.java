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
package org.locationtech.jts.geom;

import org.locationtech.jts.io.WKTWriter;

import junit.framework.TestCase;
import junit.textui.TestRunner;

/**
 * I/O type identity: core writers must not flatten unexpected
 * LineString / Polygon subclasses to control chords.
 * SQL/MM ISO/IEC 13249-3. Not overlay honesty.
 */
public class SqlMmTypesTest extends TestCase {

  public static void main(String[] args) {
    TestRunner.run(SqlMmTypesTest.class);
  }

  public SqlMmTypesTest(String name) { super(name); }

  private static LineString fakeCurve() {
    GeometryFactory gf = new GeometryFactory();
    return new LineString(
        gf.getCoordinateSequenceFactory().create(new Coordinate[] {
            new Coordinate(0, 0), new Coordinate(1, 1)
        }), gf) {
      private static final long serialVersionUID = 1L;
      public String getGeometryType() { return "FakeCurve"; }
    };
  }

  private static void assertRefused(String site, Runnable r) {
    try {
      r.run();
      fail(site + " must not flatten an unexpected LineString subclass");
    }
    catch (IllegalArgumentException e) {
      assertTrue(e.getMessage().indexOf("ISO/IEC 13249-3") >= 0);
    }
  }

  public void testRefuseFlattenUnexpectedSubclass() {
    assertRefused("SqlMmTypes", new Runnable() {
      public void run() {
        SqlMmTypes.refuseFlatten(fakeCurve(), "SqlMmTypes");
      }
    });
  }

  public void testWktWriterRefusesUnexpectedSubclass() {
    assertRefused("WKTWriter", new Runnable() {
      public void run() {
        new WKTWriter().write(fakeCurve());
      }
    });
  }

  public void testCircularStringKeywordAllowed() {
    GeometryFactory gf = new GeometryFactory();
    LineString cs = new LineString(
        gf.getCoordinateSequenceFactory().create(new Coordinate[] {
            new Coordinate(0, 0), new Coordinate(1, 1), new Coordinate(2, 0)
        }), gf) {
      private static final long serialVersionUID = 1L;
      public String getGeometryType() { return "CircularString"; }
    };
    String wkt = new WKTWriter().write(cs);
    assertTrue(wkt.toUpperCase().startsWith("CIRCULARSTRING"));
  }

  public void testLinearRingStillAllowed() {
    GeometryFactory gf = new GeometryFactory();
    LinearRing ring = gf.createLinearRing(new Coordinate[] {
        new Coordinate(0, 0), new Coordinate(1, 0), new Coordinate(1, 1),
        new Coordinate(0, 0)
    });
    SqlMmTypes.refuseFlatten(ring, "test");
    String wkt = new WKTWriter().write(ring);
    assertTrue(wkt.toUpperCase().startsWith("LINEARRING")
        || wkt.toUpperCase().startsWith("LINESTRING"));
  }
}

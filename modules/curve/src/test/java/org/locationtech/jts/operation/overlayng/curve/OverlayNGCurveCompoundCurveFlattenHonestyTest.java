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
package org.locationtech.jts.operation.overlayng.curve;

import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.SqlMmTypes;
import org.locationtech.jts.geom.curve.CompoundCurve;
import org.locationtech.jts.geom.curve.CurveGeometryFactory;
import org.locationtech.jts.io.curve.CurveWKTReader;
import org.locationtech.jts.operation.overlayng.OverlayNG;
import org.locationtech.jts.operation.overlayng.OverlayNGRobust;

import junit.textui.TestRunner;
import test.jts.GeometryTestCase;

/**
 * CRV-CC ticket 30: COMPOUNDCURVE flatten-elimination.
 * SQL/MM ISO/IEC 13249-3 §4.2.13 / §7.10.1, WKB 9. Off #7.
 * <p>
 * Silent chord OverlayNG of a CompoundCurve is a refuse. Named
 * OverlayNGCurve leftover cells densify only with
 * {@code isApproximate()=true}. Kits that already answer exactly
 * stay exact. Do not remint 424-b / 508-* / 615-b.
 */
public class OverlayNGCurveCompoundCurveFlattenHonestyTest
    extends GeometryTestCase {

  /** Mixed lineal compound that is not a two-node R-LL / R-AA kit. */
  private static final String COMPOUND =
      "COMPOUNDCURVE (CIRCULARSTRING (0 0, 5 5, 10 0), (10 0, 20 0))";
  /**
   * MultiPolygon so R-LL / R-AA / R1.7 miss. Leftover R2 must stamp
   * APPROX rather than return control-chord overlay as exact.
   */
  private static final String BOX_MULTI =
      "MULTIPOLYGON (((4 -1, 16 -1, 16 6, 4 6, 4 -1)))";
  /** Half-disc shell already answered exactly by R1.7. */
  private static final String HALF_DISC =
      "CURVEPOLYGON (COMPOUNDCURVE (CIRCULARSTRING (-5 0, 0 5, 5 0), (5 0, -5 0)))";
  private static final String SQUARE_CAP =
      "POLYGON ((-6 2, 6 2, 6 10, -6 10, -6 2))";

  public static void main(String[] args) {
    TestRunner.run(OverlayNGCurveCompoundCurveFlattenHonestyTest.class);
  }

  public OverlayNGCurveCompoundCurveFlattenHonestyTest(String name) {
    super(name);
  }

  private static Geometry readCurve(String wkt) throws Exception {
    return new CurveWKTReader(new CurveGeometryFactory()).read(wkt);
  }

  private static void assertRefused(String site, Runnable r) {
    try {
      r.run();
      fail(site + " must not flatten COMPOUNDCURVE to Coordinate[] chords");
    }
    catch (IllegalArgumentException e) {
      String msg = e.getMessage();
      assertTrue(site + " message: " + msg,
          msg.indexOf("ISO/IEC 13249-3") >= 0
              && (msg.indexOf("CompoundCurve") >= 0
                  || msg.indexOf("COMPOUNDCURVE") >= 0));
    }
  }

  public void testOverlayNGRefusesLinealCompoundCurve() throws Exception {
    final Geometry cc = readCurve(COMPOUND);
    final Geometry line = readCurve("LINESTRING (0 1, 20 1)");
    assertTrue(cc instanceof CompoundCurve);
    assertRefused("OverlayNG.overlay", new Runnable() {
      public void run() {
        OverlayNG.overlay(cc, line, OverlayNG.INTERSECTION);
      }
    });
    assertRefused("OverlayNGRobust.overlay", new Runnable() {
      public void run() {
        OverlayNGRobust.overlay(cc, line, OverlayNG.UNION);
      }
    });
  }

  public void testOverlayNGRefusesCompoundCurveShelledCurvePolygon()
      throws Exception {
    final Geometry half = readCurve(HALF_DISC);
    final Geometry square = readCurve(SQUARE_CAP);
    assertTrue("core type-name + ring reflect sees the CompoundCurve shell",
        SqlMmTypes.containsCompoundCurve(half));
    assertRefused("OverlayNG.overlay CurvePolygon shell", new Runnable() {
      public void run() {
        OverlayNG.overlay(half, square, OverlayNG.INTERSECTION);
      }
    });
  }

  /**
   * Leftover unnamed cell: mixed lineal CompoundCurve vs a crossing
   * polyline is not R-LL (3+ windows) / R-AA / R1.7. R2 must stamp
   * APPROX rather than return the control-chord overlay as exact.
   */
  public void testLeftoverCellIsNamedApproximate() throws Exception {
    Geometry cc = readCurve(COMPOUND);
    Geometry box = readCurve(BOX_MULTI);
    OverlayNGCurve op = new OverlayNGCurve(cc, box);
    Geometry r = op.getResult(OverlayNG.INTERSECTION);
    assertTrue("leftover CompoundCurve overlay is named APPROX",
        op.isApproximate());
    assertFalse("named path must not keep a dishonest CompoundCurve laser",
        r instanceof CompoundCurve);
    assertFalse("named toLinear path still answers the box ∩ arc",
        r.isEmpty());
  }

  public void testKitCellStaysExactCompoundCurve() throws Exception {
    Geometry half = readCurve(HALF_DISC);
    Geometry square = readCurve(SQUARE_CAP);
    OverlayNGCurve op = new OverlayNGCurve(half, square);
    Geometry laser = op.getResult(OverlayNG.INTERSECTION);
    assertFalse("R1.7 half ∩ square stays exact", op.isApproximate());
    assertEquals("CurvePolygon", laser.getGeometryType());
    LineString shell = ((org.locationtech.jts.geom.curve.CurvePolygon) laser)
        .getExteriorCurve();
    assertTrue("exact kit keeps a CompoundCurve shell",
        shell instanceof CompoundCurve);
  }

  public void testInstanceIntersectionDoesNotUseSilentOverlayNG()
      throws Exception {
    Geometry cc = readCurve(COMPOUND);
    Geometry box = readCurve(BOX_MULTI);
    Geometry viaGeom = cc.intersection(box);
    assertNotNull(viaGeom);
    assertFalse("Geometry.intersection leftover is not a silent CompoundCurve",
        viaGeom instanceof CompoundCurve);
  }
}

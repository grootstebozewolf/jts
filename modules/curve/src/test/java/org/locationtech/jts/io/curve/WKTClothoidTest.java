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
package org.locationtech.jts.io.curve;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.curve.ClothoidSegment;
import org.locationtech.jts.geom.curve.CompoundCurve;
import org.locationtech.jts.geom.curve.CurveGeometryFactory;
import org.locationtech.jts.geom.curve.CurvePolygon;
import org.locationtech.jts.io.ParseException;
import org.locationtech.jts.io.WKTReader;

import junit.framework.TestCase;
import junit.textui.TestRunner;

/**
 * grammars-v4 WKT CLOTHOID as a non-leading COMPOUNDCURVE member.
 * Highway-entry example: {@code COMPOUNDCURVE ((0 0, 100 0), CLOTHOID (0, 0.005, 80))}.
 */
public class WKTClothoidTest extends TestCase {

  private static final String HIGHWAY =
      "COMPOUNDCURVE ((0 0, 100 0), CLOTHOID (0, 0.005, 80))";

  public WKTClothoidTest(String name) { super(name); }
  public static void main(String[] args) { TestRunner.run(WKTClothoidTest.class); }

  public void testHighwayEntryRoundTrip() throws Exception {
    Geometry g = new CurveWKTReader(new CurveGeometryFactory()).read(HIGHWAY);
    assertTrue(g instanceof CompoundCurve);
    CompoundCurve cc = (CompoundCurve) g;
    assertEquals(2, cc.getNumMembers());
    assertEquals("LineString", cc.getMemberN(0).getGeometryType());
    assertFalse(cc.getMemberN(0) instanceof ClothoidSegment);
    assertTrue(cc.getMemberN(1) instanceof ClothoidSegment);
    ClothoidSegment cl = (ClothoidSegment) cc.getMemberN(1);
    assertEquals(0.0, cl.getStartKappa(), 0.0);
    assertEquals(0.005, cl.getEndKappa(), 1e-12);
    assertEquals(80.0, cl.getLength(), 0.0);
    assertEquals(2, cl.getNumPoints());

    String emitted = new CurveWKTWriter().write(cc);
    assertTrue("expected grammars-v4 member form, got " + emitted,
        emitted.replace(" ", "").contains("CLOTHOID(0,0.005,80)")
        || emitted.contains("CLOTHOID (0, 0.005, 80)"));
    assertTrue(emitted.startsWith("COMPOUNDCURVE ("));
    assertFalse(emitted.startsWith("CLOTHOID"));
  }

  public void testRejectsLeadingClothoid() {
    try {
      new CurveWKTReader(new CurveGeometryFactory()).read(
          "COMPOUNDCURVE (CLOTHOID (0, 0.005, 80))");
      fail("leading CLOTHOID must fail");
    }
    catch (ParseException ok) {
      // expected
    }
    catch (Exception e) {
      fail("expected ParseException, got " + e);
    }
  }

  public void testRejectsTopLevelClothoid() {
    try {
      new CurveWKTReader(new CurveGeometryFactory()).read("CLOTHOID (0, 0.005, 80)");
      fail("top-level CLOTHOID must fail");
    }
    catch (ParseException ok) {
      // expected
    }
    catch (Exception e) {
      fail("expected ParseException, got " + e);
    }
  }

  public void testCoreWktReaderDoesNotKnowClothoid() {
    try {
      new WKTReader().read(HIGHWAY);
      fail("core WKTReader must not silently accept CLOTHOID");
    }
    catch (Exception ok) {
      // expected — unknown keyword or parse failure
    }
  }

  public void testRejectsLoneClothoidCurvePolygonRing() {
    try {
      new CurveWKTReader(new CurveGeometryFactory()).read(
          "CURVEPOLYGON (CLOTHOID (0, 0.005, 80))");
      fail("clothoid must not be a lone CurvePolygon ring");
    }
    catch (ParseException ok) {
      // expected
    }
    catch (Exception e) {
      fail("expected ParseException, got " + e);
    }
  }

  public void testCurvePolygonCompoundCurveShellWithClothoid() {
    CurveGeometryFactory f = new CurveGeometryFactory();
    LineString line = f.createLineString(new Coordinate[] {
        new Coordinate(0, 0), new Coordinate(100, 0)
    });
    ClothoidSegment cl = new ClothoidSegment(
        new Coordinate(100, 0), 0.0, 0.0, 0.005, 80.0, f);
    LineString close = f.createLineString(new Coordinate[] {
        cl.getEndCoordinate(), new Coordinate(0, 0)
    });
    CompoundCurve shell = f.createCompoundCurve(new LineString[] { line, cl, close });
    CurvePolygon cp = f.createCurvePolygon(shell, null);
    assertTrue(cp.getExteriorCurve() instanceof CompoundCurve);
    CompoundCurve out = (CompoundCurve) cp.getExteriorCurve();
    assertTrue(out.getMemberN(1) instanceof ClothoidSegment);
    String emitted = new CurveWKTWriter().write(cp);
    assertTrue(emitted.startsWith("CURVEPOLYGON (COMPOUNDCURVE"));
    assertTrue(emitted.contains("CLOTHOID"));
  }
}

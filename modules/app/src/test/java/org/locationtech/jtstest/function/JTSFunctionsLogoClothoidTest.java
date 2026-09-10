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
package org.locationtech.jtstest.function;

import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.curve.CompoundCurve;
import org.locationtech.jts.geom.curve.CurvePolygon;
import org.locationtech.jts.geom.curve.Linearizable;
import org.locationtech.jts.geom.curve.MultiCurve;
import org.locationtech.jtstest.geomfunction.GeometryFunction;
import org.locationtech.jtstest.geomfunction.GeometryFunctionRegistry;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;
import junit.textui.TestRunner;

/**
 * Pins TestBuilder {@code logoClothoid} / {@code clothoidHalo} as a
 * clothoid-fillet frame around {@link JTSFunctions#logoLines}: CompoundCurve
 * / CurvePolygon of ClothoidSegment members, not densified LINESTRING.
 */
public class JTSFunctionsLogoClothoidTest extends TestCase {

  public static void main(String[] args) { TestRunner.run(suite()); }
  public static Test suite() { return new TestSuite(JTSFunctionsLogoClothoidTest.class); }
  public JTSFunctionsLogoClothoidTest(String name) { super(name); }

  public void testLogoClothoidIsClothoidFrame() {
    Geometry halo = JTSFunctions.logoClothoid(null);
    assertClothoidFrame("logoClothoid", halo);
  }

  public void testClothoidHaloIsClothoidFrame() {
    Geometry halo = JTSFunctions.clothoidHalo(null);
    assertClothoidFrame("clothoidHalo", halo);
  }

  public void testClothoidHaloDistanceOverloadIsClothoidFrame() {
    Geometry halo = JTSFunctions.clothoidHalo(null, 18.0);
    assertClothoidFrame("clothoidHalo(distance)", halo);
  }

  public void testLogoClothoidAndClothoidHaloMatchAtDefault() {
    Geometry a = JTSFunctions.logoClothoid(null);
    Geometry b = JTSFunctions.clothoidHalo(null);
    assertTrue("logoClothoid and clothoidHalo are the same default mark",
        a.equalsExact(b));
    assertEquals(a.getUserData(), b.getUserData());
  }

  public void testHaloIsNotDensifiedLineString() {
    Geometry halo = JTSFunctions.logoClothoid(null);
    assertFalse(halo.getClass().equals(LineString.class));
    assertTrue("halo is curve-typed, got " + halo.getClass().getName(),
        halo instanceof CompoundCurve || halo instanceof CurvePolygon
            || halo instanceof Linearizable);
    String wkt = new org.locationtech.jts.io.curve.CurveWKTWriter().write(halo);
    assertTrue("WKT must name CLOTHOID, got " + wkt, wkt.indexOf("CLOTHOID") >= 0);
    assertFalse("WKT must not be a chord LINESTRING: " + wkt,
        wkt.startsWith("LINESTRING"));
  }

  public void testHaloFramesTheWordmarkWithoutFlatteningLogoLines() {
    Geometry logo = JTSFunctions.logoLines(null);
    assertTrue("logoLines stays a MultiCurve of real curves",
        logo instanceof MultiCurve);
    Envelope logoEnv = logo.getEnvelopeInternal();

    Geometry halo = JTSFunctions.logoClothoid(null);
    Envelope haloEnv = halo.getEnvelopeInternal();
    assertTrue("halo envelope must cover the wordmark envelope",
        haloEnv.contains(logoEnv));
    assertTrue("halo must sit outside the letters, not on the control box",
        haloEnv.getWidth() > logoEnv.getWidth()
            && haloEnv.getHeight() > logoEnv.getHeight());

    Geometry logoAgain = JTSFunctions.logoLines(null);
    assertTrue(logoAgain instanceof MultiCurve);
    assertEquals("logoLines is not flattened by the halo helper",
        logo.getNumPoints(), logoAgain.getNumPoints());
  }

  public void testHaloIsNotLogoBuffer() {
    Geometry halo = JTSFunctions.logoClothoid(null);
    Geometry circular = JTSFunctions.logoBuffer(null, 12.0);
    assertFalse("clothoid halo must not reuse logoBuffer (MKT-1)",
        halo.equalsExact(circular));
    assertFalse(halo.equalsNorm(circular));
  }

  public void testRegistryExposesBothNames() {
    GeometryFunctionRegistry registry =
        GeometryFunctionRegistry.createTestBuilderRegistry();
    GeometryFunction logo = registry.find("logoClothoid");
    GeometryFunction halo = registry.find("clothoidHalo");
    assertNotNull("TestBuilder must expose logoClothoid", logo);
    assertNotNull("TestBuilder must expose clothoidHalo", halo);
    assertEquals("logo as curves plus a clothoid halo.", logo.getDescription());
    assertEquals("logo as curves plus a clothoid halo.", halo.getDescription());
  }

  private static void assertClothoidFrame(String label, Geometry halo) {
    assertNotNull(label + " must return a geometry", halo);
    assertFalse(label + " must not be empty", halo.isEmpty());
    assertFalse(label + " must not be densified LINESTRING, got "
        + halo.getClass().getName(),
        halo.getClass().equals(LineString.class));
    assertTrue(label + " must be CompoundCurve or CurvePolygon, got "
        + halo.getClass().getName(),
        halo instanceof CompoundCurve || halo instanceof CurvePolygon);
    String wkt = new org.locationtech.jts.io.curve.CurveWKTWriter().write(halo);
    assertTrue(label + " WKT must name CLOTHOID, got " + wkt,
        wkt.indexOf("CLOTHOID") >= 0);
  }
}

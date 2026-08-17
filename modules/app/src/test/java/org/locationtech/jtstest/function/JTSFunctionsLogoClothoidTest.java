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
import org.locationtech.jts.geom.MultiPolygon;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.geom.curve.CircularString;
import org.locationtech.jts.geom.curve.ClothoidSegment;
import org.locationtech.jts.geom.curve.CompoundCurve;
import org.locationtech.jts.geom.curve.Linearizable;
import org.locationtech.jts.geom.curve.MultiCurve;
import org.locationtech.jts.io.WKTWriter;
import org.locationtech.jtstest.geomfunction.GeometryFunction;
import org.locationtech.jtstest.geomfunction.GeometryFunctionRegistry;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;
import junit.textui.TestRunner;

/**
 * Pins TestBuilder {@code logoClothoid} / {@code clothoidHalo} as a named
 * linear fallback around {@link JTSFunctions#logoLines}: LINESTRING or
 * POLYGON (or MultiPolygon) of chords, stamped CHORD-PATH or NAMED-APPROX.
 * Not a CIRCULARSTRING Qed, not a laser, not {@link JTSFunctions#logoBuffer}.
 */
public class JTSFunctionsLogoClothoidTest extends TestCase {

  public static void main(String[] args) { TestRunner.run(suite()); }
  public static Test suite() { return new TestSuite(JTSFunctionsLogoClothoidTest.class); }
  public JTSFunctionsLogoClothoidTest(String name) { super(name); }

  public void testLogoClothoidIsNamedLinearFallback() {
    Geometry halo = JTSFunctions.logoClothoid(null);
    assertNamedLinearFallback("logoClothoid", halo);
  }

  public void testClothoidHaloIsNamedLinearFallback() {
    Geometry halo = JTSFunctions.clothoidHalo(null);
    assertNamedLinearFallback("clothoidHalo", halo);
  }

  public void testClothoidHaloDistanceOverloadIsNamedLinearFallback() {
    Geometry halo = JTSFunctions.clothoidHalo(null, 18.0);
    assertNamedLinearFallback("clothoidHalo(distance)", halo);
  }

  public void testLogoClothoidAndClothoidHaloMatchAtDefault() {
    Geometry a = JTSFunctions.logoClothoid(null);
    Geometry b = JTSFunctions.clothoidHalo(null);
    assertTrue("logoClothoid and clothoidHalo are the same default mark",
        a.equalsExact(b));
    assertEquals(a.getUserData(), b.getUserData());
  }

  public void testHaloIsNotCircularStringQed() {
    Geometry halo = JTSFunctions.logoClothoid(null);
    assertFalse(halo instanceof CircularString);
    assertFalse(halo instanceof CompoundCurve);
    assertFalse(halo instanceof ClothoidSegment);
    assertFalse("result must not stay Linearizable / curve-typed",
        halo instanceof Linearizable);
    String type = halo.getGeometryType();
    assertFalse("getGeometryType stays linear, got " + type,
        type.equalsIgnoreCase("CircularString")
            || type.equalsIgnoreCase("CompoundCurve")
            || type.equalsIgnoreCase("ClothoidSegment")
            || type.equalsIgnoreCase("MultiCurve"));
    String wkt = new WKTWriter().write(halo);
    assertFalse("WKT must not claim CIRCULARSTRING Qed: " + wkt,
        wkt.startsWith("CIRCULARSTRING") || wkt.startsWith("COMPOUNDCURVE"));
  }

  public void testPathIsNamed() {
    Geometry halo = JTSFunctions.logoClothoid(null);
    Object stamp = halo.getUserData();
    assertNotNull("halo must carry a named-fallback stamp", stamp);
    assertTrue("stamp must be NAMED-APPROX or CHORD-PATH, got " + stamp,
        JTSFunctions.CLOTHOID_HALO_STAMP_NAMED_APPROX.equals(stamp)
            || JTSFunctions.CLOTHOID_HALO_STAMP_CHORD_PATH.equals(stamp));
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

  private static void assertNamedLinearFallback(String label, Geometry halo) {
    assertNotNull(label + " must return a geometry", halo);
    assertFalse(label + " must not be empty", halo.isEmpty());
    assertTrue(label + " must be LineString / Polygon / MultiPolygon, got "
        + halo.getClass().getName(),
        halo instanceof LineString
            || halo instanceof Polygon
            || halo instanceof MultiPolygon);
    assertFalse(label + " must not be a CircularString",
        halo instanceof CircularString);
    Object stamp = halo.getUserData();
    assertTrue(label + " stamp must be NAMED-APPROX or CHORD-PATH, got " + stamp,
        JTSFunctions.CLOTHOID_HALO_STAMP_NAMED_APPROX.equals(stamp)
            || JTSFunctions.CLOTHOID_HALO_STAMP_CHORD_PATH.equals(stamp));
    if (halo instanceof LineString) {
      assertTrue(label + " path should be closed", ((LineString) halo).isClosed());
    }
    if (halo instanceof Polygon) {
      assertTrue(label + " polygonal halo should be valid", halo.isValid());
      assertTrue(label + " polygonal halo should have area", halo.getArea() > 0.0);
    }
  }
}

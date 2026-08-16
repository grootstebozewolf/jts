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
package org.locationtech.jts.geom.curve;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.util.AffineTransformation;
import org.locationtech.jts.io.curve.CurveWKTReader;
import org.locationtech.jts.io.curve.CurveWKTWriter;

import junit.textui.TestRunner;
import test.jts.GeometryTestCase;

/**
 * Affine <em>translate</em> must stay type-honest on ISO/IEC 13249-3
 * {@code CIRCULARSTRING} / {@code COMPOUNDCURVE} / {@code CURVEPOLYGON}
 * / {@code MULTICURVE}.
 * <p>
 * Canvas miss is TestBuilder MoveTool drag, not Function-tree
 * AffineTranslation. MoveTool.execute is
 * {@code translationInstance} then {@code copy(); apply(trans)}. The
 * inherited LineString apply walked only the concatenated CompoundCurve
 * sequence, which omits each later member's start. Mid and end of a
 * three-point arc moved; the start did not. The type stayed
 * {@code CIRCULARSTRING}; the arc lied.
 * <p>
 * Function-tree AffineTranslation is the same apply
 * ({@code AffineTransformation.transform} = {@code copy(); apply}).
 * That is verified, not a UX SIGN of the Function-tree. Testers still
 * shoot AffineTranslation (10, 8) on pin JAR {@code 61eb3377}. Do not
 * retip that pin. Do not rebuild the guides JAR.
 * <p>
 * This locks translate only. It does not sign that shear or
 * non-uniform scale still describes a circular arc. No Bézier I/O
 * type. Type set stays 1–7 + 8–12.
 */
public class CompoundCurveTranslateTest extends GeometryTestCase {

  private static final double DX = 10.0;
  private static final double DY = 8.0;
  private static final double EPS = 1.0e-12;

  /** Line then quarter-circle: the reported junction-start miss. */
  private static final String LINE_THEN_ARC =
      "COMPOUNDCURVE ((0 70, 30 70, 30 25), CIRCULARSTRING (30 25, 22.677669529663685 7.322330470336315, 5 0))";

  private static final String S_BOWLS =
      "COMPOUNDCURVE ((150 70, 132.5 70), CIRCULARSTRING (132.5 70, 115 52.5, 132.5 35), "
      + "CIRCULARSTRING (132.5 35, 150 17.5, 132.5 0), (132.5 0, 115 0))";

  private static final String MULTI =
      "MULTICURVE (" + LINE_THEN_ARC + ", (30 70, 127.5 70), " + S_BOWLS + ")";

  private static final String CP_COMPOUND_SHELL =
      "CURVEPOLYGON (COMPOUNDCURVE (CIRCULARSTRING (0 0, 1 1, 2 0), (2 0, 0 0)))";

  private static final String CP_COMPOUND_HOLE =
      "CURVEPOLYGON (CIRCULARSTRING (0 0, 8 0, 8 8, 0 8, 0 0), "
      + "COMPOUNDCURVE (CIRCULARSTRING (2 2, 3 3, 4 2), (4 2, 2 2)))";

  public static void main(String[] args) {
    TestRunner.run(CompoundCurveTranslateTest.class);
  }

  public CompoundCurveTranslateTest(String name) { super(name); }

  private static Geometry readCurve(String wkt) throws Exception {
    return new CurveWKTReader(new CurveGeometryFactory()).read(wkt);
  }

  private static Geometry translate(Geometry g) {
    return AffineTransformation.translationInstance(DX, DY).transform(g);
  }

  public void testCompoundCurveKeepsTypeAndThreePointArc() throws Exception {
    Geometry moved = translate(readCurve(LINE_THEN_ARC));
    assertEquals("CompoundCurve", moved.getGeometryType());
    CompoundCurve cc = (CompoundCurve) moved;
    assertEquals(2, cc.getNumMembers());
    assertEquals("LineString", cc.getMemberN(0).getGeometryType());
    assertEquals("CircularString", cc.getMemberN(1).getGeometryType());
    assertEquals("arc stays a three-point control triple",
        3, cc.getMemberN(1).getNumPoints());
  }

  public void testEveryControlPointIncludingMemberStartMoves() throws Exception {
    Geometry original = readCurve(LINE_THEN_ARC);
    Geometry moved = translate(original);
    assertTranslatedMembers((CompoundCurve) original, (CompoundCurve) moved);
  }

  public void testSecondCircularStringStartAlsoMoves() throws Exception {
    Geometry original = readCurve(S_BOWLS);
    Geometry moved = translate(original);
    assertEquals("CompoundCurve", moved.getGeometryType());
    assertTranslatedMembers((CompoundCurve) original, (CompoundCurve) moved);
    CompoundCurve cc = (CompoundCurve) moved;
    assertEquals(3, cc.getMemberN(1).getNumPoints());
    assertEquals(3, cc.getMemberN(2).getNumPoints());
  }

  public void testMultiCurveTranslateKeepsMemberTypes() throws Exception {
    Geometry original = readCurve(MULTI);
    Geometry moved = translate(original);
    assertEquals("MultiCurve", moved.getGeometryType());
    assertEquals(3, moved.getNumGeometries());
    assertEquals("CompoundCurve", moved.getGeometryN(0).getGeometryType());
    assertEquals("LineString", moved.getGeometryN(1).getGeometryType());
    assertEquals("CompoundCurve", moved.getGeometryN(2).getGeometryType());
    assertTranslatedMembers(
        (CompoundCurve) original.getGeometryN(0),
        (CompoundCurve) moved.getGeometryN(0));
    assertTranslatedMembers(
        (CompoundCurve) original.getGeometryN(2),
        (CompoundCurve) moved.getGeometryN(2));
  }

  public void testCurvePolygonCompoundShell() throws Exception {
    Geometry original = readCurve(CP_COMPOUND_SHELL);
    Geometry moved = translate(original);
    assertEquals("CurvePolygon", moved.getGeometryType());
    LineString shell = ((CurvePolygon) moved).getExteriorCurve();
    assertEquals("CompoundCurve", shell.getGeometryType());
    assertTranslatedMembers(
        (CompoundCurve) ((CurvePolygon) original).getExteriorCurve(),
        (CompoundCurve) shell);
  }

  public void testCurvePolygonCompoundHole() throws Exception {
    Geometry original = readCurve(CP_COMPOUND_HOLE);
    Geometry moved = translate(original);
    CurvePolygon cp = (CurvePolygon) moved;
    assertEquals("CurvePolygon", cp.getGeometryType());
    assertEquals("CircularString", cp.getExteriorCurve().getGeometryType());
    assertEquals("CompoundCurve", cp.getInteriorCurveN(0).getGeometryType());
    assertTranslated(
        ((CurvePolygon) original).getExteriorCurve().getCoordinates(),
        cp.getExteriorCurve().getCoordinates());
    assertTranslatedMembers(
        (CompoundCurve) ((CurvePolygon) original).getInteriorCurveN(0),
        (CompoundCurve) cp.getInteriorCurveN(0));
  }

  public void testNoBezierVerticesInWkt() throws Exception {
    Geometry moved = translate(readCurve(MULTI));
    String wkt = new CurveWKTWriter().write(moved);
    assertTrue(wkt.contains("MULTICURVE"));
    assertTrue(wkt.contains("COMPOUNDCURVE"));
    assertTrue(wkt.contains("CIRCULARSTRING"));
    assertFalse("do not invent a Bézier I/O type",
        wkt.toUpperCase().contains("BEZIER"));
    assertFalse(wkt.toUpperCase().contains("CUBIC"));
  }

  public void testOriginalIsUnchanged() throws Exception {
    Geometry original = readCurve(LINE_THEN_ARC);
    String before = new CurveWKTWriter().write(original);
    translate(original);
    assertEquals(before, new CurveWKTWriter().write(original));
  }

  public void testBareCircularStringStillTranslates() throws Exception {
    Geometry original = readCurve("CIRCULARSTRING (30 25, 22 7, 5 0)");
    Geometry moved = translate(original);
    assertEquals("CircularString", moved.getGeometryType());
    assertEquals(3, moved.getNumPoints());
    assertTranslated(original.getCoordinates(), moved.getCoordinates());
  }

  private static void assertTranslatedMembers(CompoundCurve original, CompoundCurve moved) {
    assertEquals(original.getNumMembers(), moved.getNumMembers());
    for (int i = 0; i < original.getNumMembers(); i++) {
      LineString a = original.getMemberN(i);
      LineString b = moved.getMemberN(i);
      assertEquals(a.getGeometryType(), b.getGeometryType());
      assertEquals(a.getNumPoints(), b.getNumPoints());
      if (a instanceof CircularString) {
        assertEquals("CIRCULARSTRING stays a 3-point arc", 3, b.getNumPoints());
      }
      assertTranslated(a.getCoordinates(), b.getCoordinates());
    }
  }

  private static void assertTranslated(Coordinate[] from, Coordinate[] to) {
    assertEquals(from.length, to.length);
    for (int i = 0; i < from.length; i++) {
      assertEquals("x[" + i + "]", from[i].x + DX, to[i].x, EPS);
      assertEquals("y[" + i + "]", from[i].y + DY, to[i].y, EPS);
    }
  }
}

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

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryCollection;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.geom.curve.CircularString;
import org.locationtech.jts.geom.curve.CompoundCurve;
import org.locationtech.jts.geom.curve.MultiCurve;
import org.locationtech.jts.geom.util.AffineTransformation;
import org.locationtech.jts.io.curve.CurveWKTWriter;
import org.locationtech.jtstest.testbuilder.geom.GeometryComponentTransformer;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;
import junit.textui.TestRunner;

/**
 * Canvas miss is MoveTool drag, not Function-tree AffineTranslation.
 * <p>
 * {@code logoLines} then MoveTool.execute ({@code translationInstance}
 * + {@link GeometryComponentTransformer#transform(Geometry, AffineTransformation)},
 * which is {@code copy(); apply(trans)}) must keep the ISO/IEC 13249-3
 * MultiCurve and every three-point {@code CIRCULARSTRING}, including
 * each circular member's start.
 * <p>
 * Verified same apply: Function-tree
 * {@link AffineTransformationFunctions#translate} is
 * {@code translationInstance(dx, dy).transform(g)}, which is also
 * {@code copy(); apply(trans)}. That is stated, not a UX SIGN of the
 * Function-tree. Testers still shoot AffineTranslation (10, 8) on pin
 * JAR {@code 61eb3377}. Do not retip that pin. Do not rebuild the
 * guides JAR. Translate only. No Bézier I/O type.
 * {@code logoBuffer} stays named CHORD-PATH / toLinear + BufferOp.
 */
public class JTSFunctionsLogoLinesTranslateTest extends TestCase {

  private static final double DX = 10.0;
  private static final double DY = 8.0;
  private static final double EPS = 1.0e-12;

  public static void main(String[] args) { TestRunner.run(suite()); }
  public static Test suite() { return new TestSuite(JTSFunctionsLogoLinesTranslateTest.class); }
  public JTSFunctionsLogoLinesTranslateTest(String name) { super(name); }

  public void testMoveToolDragKeepsTypesAndArcStarts() {
    Geometry logo = JTSFunctions.logoLines(null);
    Geometry moved = moveToolTranslate(logo, DX, DY);

    assertTrue(moved instanceof MultiCurve);
    assertEquals(logo.getNumGeometries(), moved.getNumGeometries());
    assertTranslatedTree(logo, moved);

    String wkt = new CurveWKTWriter().write(moved);
    assertTrue(wkt.contains("MULTICURVE"));
    assertTrue(wkt.contains("COMPOUNDCURVE"));
    assertTrue(wkt.contains("CIRCULARSTRING"));
    assertFalse(wkt.toUpperCase().contains("BEZIER"));
  }

  /**
   * Same apply, not a Function-tree SIGN. MoveTool.execute and
   * AffineTransformation.translate are both {@code copy(); apply(trans)}.
   */
  public void testFunctionTreeTranslateIsTheSameApplyAsMoveTool() {
    Geometry logo = JTSFunctions.logoLines(null);
    Geometry viaMoveTool = moveToolTranslate(logo, DX, DY);
    Geometry viaFunctionTree = AffineTransformationFunctions.translate(logo, DX, DY);
    assertTrue("Function-tree AffineTranslation is the same copy(); apply(trans) as MoveTool",
        viaMoveTool.equalsExact(viaFunctionTree));
  }

  public void testLogoBufferPathUnchangedAfterTranslate() {
    Geometry moved = moveToolTranslate(JTSFunctions.logoLines(null), DX, DY);
    Geometry halo = JTSFunctions.logoBuffer(moved, 4.0);
    assertTrue("logoBuffer stays polygonal CHORD-PATH",
        halo instanceof Polygon || halo.getGeometryType().contains("Polygon"));
    assertFalse("named fallback, never isApproximate()=false",
        halo.getGeometryType().equals("CircularString"));
  }

  /** MoveTool.execute whole-geom path. */
  private static Geometry moveToolTranslate(Geometry geom, double dx, double dy) {
    return GeometryComponentTransformer.transform(
        geom, AffineTransformation.translationInstance(dx, dy));
  }

  private static void assertTranslatedTree(Geometry original, Geometry moved) {
    assertEquals(original.getGeometryType(), moved.getGeometryType());
    if (original instanceof CircularString) {
      assertEquals(3, moved.getNumPoints());
      assertTranslated(original.getCoordinates(), moved.getCoordinates());
      return;
    }
    if (original instanceof CompoundCurve) {
      CompoundCurve a = (CompoundCurve) original;
      CompoundCurve b = (CompoundCurve) moved;
      assertEquals(a.getNumMembers(), b.getNumMembers());
      for (int i = 0; i < a.getNumMembers(); i++) {
        assertTranslatedTree(a.getMemberN(i), b.getMemberN(i));
      }
      return;
    }
    if (original instanceof GeometryCollection) {
      assertEquals(original.getNumGeometries(), moved.getNumGeometries());
      for (int i = 0; i < original.getNumGeometries(); i++) {
        assertTranslatedTree(original.getGeometryN(i), moved.getGeometryN(i));
      }
      return;
    }
    if (original instanceof LineString) {
      assertTranslated(original.getCoordinates(), moved.getCoordinates());
    }
  }

  private static void assertTranslated(Coordinate[] from, Coordinate[] to) {
    assertEquals(from.length, to.length);
    for (int i = 0; i < from.length; i++) {
      assertEquals(from[i].x + DX, to[i].x, EPS);
      assertEquals(from[i].y + DY, to[i].y, EPS);
    }
  }
}

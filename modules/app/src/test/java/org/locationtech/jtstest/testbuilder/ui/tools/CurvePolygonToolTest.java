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
package org.locationtech.jtstest.testbuilder.ui.tools;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.geom.curve.CircularString;
import org.locationtech.jts.geom.curve.CompoundCurve;
import org.locationtech.jts.geom.curve.CurveGeometryFactory;
import org.locationtech.jts.geom.curve.CurvePolygon;
import org.locationtech.jts.io.curve.CurveWKTWriter;
import org.locationtech.jtstest.testbuilder.geom.GeometryCombiner;
import org.locationtech.jtstest.testbuilder.model.GeometryEditModel;
import org.locationtech.jtstest.testbuilder.model.GeometryType;
import org.locationtech.jtstest.testbuilder.model.TestCaseEdit;

import junit.framework.TestCase;
import junit.textui.TestRunner;

/**
 * Pins CurvePolygonTool finish/cancel for issue #56: a double-click
 * anywhere (or click-start) must commit ISO/IEC 13249-3
 * {@code CURVEPOLYGON (CIRCULARSTRING …)}, never a linearized
 * {@code POLYGON} or a chord ring, and never a silent empty A.
 * Escape status is exactly {@link CurvePolygonTool#CANCELLED_STATUS}
 * on the bottom status bar (not Log-only). No mixed-shell editor.
 */
public class CurvePolygonToolTest extends TestCase {

  private static final Coordinate A = new Coordinate(-5, 0);
  private static final Coordinate B = new Coordinate(0, 5);
  private static final Coordinate C = new Coordinate(5, 0);
  private static final Coordinate D = new Coordinate(0, -5);

  private static final double RADIUS = 5.0;
  private static final double ARC_EPS = 1e-8;

  public CurvePolygonToolTest(String name) {
    super(name);
  }

  public static void main(String[] args) {
    TestRunner.run(CurvePolygonToolTest.class);
  }

  public void testCancelledStatusIsExact() {
    assertEquals("CurvePolygon cancelled.", CurvePolygonTool.CANCELLED_STATUS);
  }

  public void testDoubleClickAnywhereAutoClosesToCircleNotChord() {
    List<Coordinate> drawn = Arrays.asList(A, B, C);
    List<Coordinate> shell = CurvePolygonTool.closeCircularShell(drawn);
    assertNotNull("double-click finish must auto-close, not drop", shell);
    assertTrue(shell.get(0).equals2D(shell.get(shell.size() - 1)));
    assertEquals(5, shell.size());
    assertTrue("closing control must be the complementary arc mid, not the chord",
        shell.get(3).equals2D(D));
    assertFalse("must not insert the chord midpoint",
        shell.get(3).equals2D(new Coordinate(0, 0)));

    Geometry g = commit(shell);
    assertCurvePolygonCircularString(g);
    assertEquals(2.0 * Math.PI * RADIUS, g.getLength(), ARC_EPS);
  }

  public void testClickStartOnClosedCircleCommitsUnchanged() {
    List<Coordinate> closed = Arrays.asList(A, B, C, D, A);
    List<Coordinate> shell = CurvePolygonTool.closeCircularShell(closed);
    assertNotNull(shell);
    assertEquals(5, shell.size());
    assertTrue(shell.get(0).equals2D(A));
    assertTrue(shell.get(4).equals2D(A));

    Geometry g = commit(shell);
    assertCurvePolygonCircularString(g);
  }

  public void testFourUnclosedPointsCloseToOddCircularString() {
    List<Coordinate> drawn = Arrays.asList(A, B, C, D);
    List<Coordinate> shell = CurvePolygonTool.closeCircularShell(drawn);
    assertNotNull(shell);
    assertEquals(5, shell.size());
    assertTrue(shell.get(0).equals2D(shell.get(shell.size() - 1)));
    assertEquals(1, shell.size() % 2);

    Geometry g = commit(shell);
    assertCurvePolygonCircularString(g);
  }

  public void testTwoPointsPlusCloseIsSingleArcCircle() {
    List<Coordinate> drawn = Arrays.asList(A, B);
    List<Coordinate> shell = CurvePolygonTool.closeCircularShell(drawn);
    assertNotNull(shell);
    assertEquals(3, shell.size());
    assertTrue(shell.get(0).equals2D(A));
    assertTrue(shell.get(1).equals2D(B));
    assertTrue(shell.get(2).equals2D(A));

    Geometry g = commit(shell);
    assertCurvePolygonCircularString(g);
  }

  public void testOnePointDoesNotCommit() {
    assertNull(CurvePolygonTool.closeCircularShell(Arrays.asList(A)));
    assertNull(CurvePolygonTool.closeCircularShell(new ArrayList<Coordinate>()));
    assertNull(CurvePolygonTool.closeCircularShell(null));
  }

  public void testEvenAfterNaiveCloseIsRepairedNotDropped() {
    List<Coordinate> naive = new ArrayList<Coordinate>(Arrays.asList(A, B, C));
    naive.add(new Coordinate(A));
    assertEquals("old abort condition: 3 pts + start = even leftover",
        4, naive.size());
    assertEquals(0, naive.size() % 2);

    List<Coordinate> shell = CurvePolygonTool.closeCircularShell(
        Arrays.asList(A, B, C));
    assertNotNull("even leftover after close must be repaired, not dropped",
        shell);
    assertEquals(1, shell.size() % 2);
    assertFalse(shell.size() == 4);
  }

  public void testAddComponentKeepsCurvePolygonNotPolygon() {
    GeometryEditModel model = new GeometryEditModel();
    model.setTestCase(new TestCaseEdit(new org.locationtech.jts.geom.PrecisionModel()));
    model.setGeometryType(GeometryType.CURVEPOLYGON);

    List<Coordinate> shell = CurvePolygonTool.closeCircularShell(
        Arrays.asList(A, B, C));
    model.addComponent(shell);

    Geometry g = model.getGeometry();
    assertCurvePolygonCircularString(g);
    assertFalse(g.getClass().equals(Polygon.class));
  }

  public void testExistingCompoundShellIsNotLinearized() {
    Coordinate[] upper = new Coordinate[] { A, B, C };
    Coordinate[] lower = new Coordinate[] { C, D, A };
    Geometry g = combiner().addCurvePolygon(null, new Coordinate[][] { upper, lower });
    assertTrue(g instanceof CurvePolygon);
    assertTrue(((CurvePolygon) g).getExteriorCurve() instanceof CompoundCurve);
    String wkt = new CurveWKTWriter().write(g);
    assertTrue("already-compound shell must stay COMPOUNDCURVE: " + wkt,
        wkt.startsWith("CURVEPOLYGON (COMPOUNDCURVE (CIRCULARSTRING"));
    assertFalse("must not linearize a compound shell to POLYGON: " + wkt,
        wkt.startsWith("POLYGON"));
  }

  private static GeometryCombiner combiner() {
    return new GeometryCombiner(new CurveGeometryFactory());
  }

  private static Geometry commit(List<Coordinate> shell) {
    Coordinate[] pts = shell.toArray(new Coordinate[0]);
    return combiner().addCurvePolygon(null, pts);
  }

  private static void assertCurvePolygonCircularString(Geometry g) {
    assertNotNull("must commit a geometry, not silent empty A", g);
    assertTrue(g instanceof CurvePolygon);
    CurvePolygon cp = (CurvePolygon) g;
    assertTrue(cp.getExteriorCurve() instanceof CircularString);
    String wkt = new CurveWKTWriter().write(g);
    assertTrue("WKT must stay CURVEPOLYGON (CIRCULARSTRING, got " + wkt,
        wkt.startsWith("CURVEPOLYGON (CIRCULARSTRING"));
    assertFalse("must never flatten to POLYGON: " + wkt,
        wkt.startsWith("POLYGON"));
  }
}

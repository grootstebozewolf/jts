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
import java.util.List;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.PrecisionModel;
import org.locationtech.jts.geom.curve.CircularString;
import org.locationtech.jts.geom.util.AffineTransformation;
import org.locationtech.jts.io.curve.CurveWKTWriter;
import org.locationtech.jtstest.testbuilder.geom.GeometryComponentTransformer;
import org.locationtech.jtstest.testbuilder.model.GeometryEditModel;
import org.locationtech.jtstest.testbuilder.model.GeometryType;
import org.locationtech.jtstest.testbuilder.model.TestCaseEdit;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;
import junit.textui.TestRunner;

/**
 * MMF basics seed: three-point CircularString draw commit + MoveTool
 * translate — the curve analogue of upstream polygon/line draw then move.
 * <p>
 * No GUI. Commit path is {@link GeometryEditModel#addComponent} (same as
 * {@link CircularStringTool#bandFinished}). Move path is
 * {@code GeometryComponentTransformer.transform(g, translationInstance)}
 * — the same apply {@link MoveTool} uses for a whole-geom drag.
 * <p>
 * Appium recipe: {@code doc/APPIUM_IDS.md} (Seed: basics).
 */
public class CircularStringMoveToolBasicsTest extends TestCase {

  private static final double DX = 10.0;
  private static final double DY = 8.0;
  private static final double EPS = 1.0e-12;

  /** One arc: start / mid / end. Same witness as GeometryEditModelCircularStringTest. */
  private static final Coordinate P0 = new Coordinate(-5, 0);
  private static final Coordinate P1 = new Coordinate(0, 5);
  private static final Coordinate P2 = new Coordinate(5, 0);

  public static void main(String[] args) {
    TestRunner.run(suite());
  }

  public static Test suite() {
    return new TestSuite(CircularStringMoveToolBasicsTest.class);
  }

  public CircularStringMoveToolBasicsTest(String name) {
    super(name);
  }

  public void testThreePointsCommitCircularString() {
    Geometry g = commitThreePointCircularString();
    assertTrue(g instanceof CircularString);
    assertFalse(g.getClass().equals(LineString.class));
    assertEquals(3, g.getNumPoints());

    String wkt = new CurveWKTWriter().write(g);
    assertTrue("draw commit must emit CIRCULARSTRING, got " + wkt,
        wkt.startsWith("CIRCULARSTRING"));
    assertFalse(wkt.startsWith("LINESTRING"));
    assertFalse(g.isEmpty());
  }

  public void testMoveToolTranslateKeepsCircularString() {
    Geometry drawn = commitThreePointCircularString();
    Geometry moved = moveToolTranslate(drawn, DX, DY);

    assertTrue("MoveTool must keep CircularString, got " + moved.getClass().getName(),
        moved instanceof CircularString);
    assertFalse(moved.getClass().equals(LineString.class));
    assertFalse(moved.isEmpty());
    assertEquals(3, moved.getNumPoints());

    String wkt = new CurveWKTWriter().write(moved);
    assertTrue("after MoveTool WKT must stay CIRCULARSTRING, got " + wkt,
        wkt.startsWith("CIRCULARSTRING"));
    assertFalse(wkt.startsWith("LINESTRING"));

    Coordinate[] from = drawn.getCoordinates();
    Coordinate[] to = moved.getCoordinates();
    assertEquals(from.length, to.length);
    for (int i = 0; i < from.length; i++) {
      assertEquals(from[i].x + DX, to[i].x, EPS);
      assertEquals(from[i].y + DY, to[i].y, EPS);
    }
  }

  /**
   * Mid control stays off the chord after translate — move did not
   * silently flatten the arc to a straight LINESTRING of the endpoints.
   */
  public void testMoveToolTranslateKeepsArcInterior() {
    Geometry drawn = commitThreePointCircularString();
    Geometry moved = moveToolTranslate(drawn, DX, DY);

    Coordinate[] c = moved.getCoordinates();
    assertEquals(3, c.length);
    // Chord mid between endpoints after translate
    double chordMidX = (c[0].x + c[2].x) / 2.0;
    double chordMidY = (c[0].y + c[2].y) / 2.0;
    double bulge = Math.hypot(c[1].x - chordMidX, c[1].y - chordMidY);
    assertTrue("arc mid must stay off the endpoint chord after MoveTool (bulge="
        + bulge + ")", bulge > 1.0);

    // Same bulge magnitude as before translate
    Coordinate[] before = drawn.getCoordinates();
    double bulgeBefore = Math.hypot(
        before[1].x - (before[0].x + before[2].x) / 2.0,
        before[1].y - (before[0].y + before[2].y) / 2.0);
    assertEquals(bulgeBefore, bulge, EPS);
  }

  private static Geometry commitThreePointCircularString() {
    GeometryEditModel model = new GeometryEditModel();
    model.setTestCase(new TestCaseEdit(new PrecisionModel()));
    model.setGeometryType(GeometryType.CIRCULARSTRING);

    List coords = new ArrayList();
    coords.add(new Coordinate(P0));
    coords.add(new Coordinate(P1));
    coords.add(new Coordinate(P2));
    model.addComponent(coords);
    return model.getGeometry();
  }

  /** MoveTool.execute whole-geom path. */
  private static Geometry moveToolTranslate(Geometry geom, double dx, double dy) {
    return GeometryComponentTransformer.transform(
        geom, AffineTransformation.translationInstance(dx, dy));
  }
}

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
package org.locationtech.jtstest.testbuilder.appium;

import java.io.File;

import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.curve.CurveGeometryFactory;
import org.locationtech.jts.geom.util.AffineTransformation;
import org.locationtech.jts.io.WKTReader;
import org.locationtech.jts.io.curve.CurveWKTReader;
import org.locationtech.jts.io.curve.CurveWKTWriter;

import junit.framework.TestCase;
import junit.textui.TestRunner;

/**
 * Semantic playback of AffineTransformation.translate Appium sequences:
 * same apply the Function-tree Exec path uses ({@code translationInstance
 * .transform}). Locks upstream polygon golden + pr7 disc/circle/half-moon.
 */
public class TbAppiumTranslatePlaybackTest extends TestCase {

  private static final double DX = 10.0;
  private static final double DY = 8.0;

  public static void main(String[] args) {
    TestRunner.run(TbAppiumTranslatePlaybackTest.class);
  }

  public TbAppiumTranslatePlaybackTest(String name) {
    super(name);
  }

  public void testUpstreamPolygonGoldenTranslate() throws Exception {
    Geometry g = readPlain(fixture("upstream-polygon.wkt"));
    Geometry moved = translate(g);
    assertFalse(moved.isEmpty());
    assertEquals("Polygon", moved.getGeometryType());
    assertEquals(g.getArea(), moved.getArea(), 1.0e-12);
    assertEquals(g.getCentroid().getX() + DX, moved.getCentroid().getX(), 1.0e-9);
    assertEquals(g.getCentroid().getY() + DY, moved.getCentroid().getY(), 1.0e-9);
  }

  public void testPr7DiscPlayback() throws Exception {
    assertCurveTranslatePreserved(fixture("pr7-disc.wkt"));
  }

  public void testPr7CirclePlayback() throws Exception {
    assertCurveTranslatePreserved(fixture("pr7-circle.wkt"));
  }

  public void testPr7HalfMoonPlayback() throws Exception {
    Geometry g = readCurve(fixture("pr7-half-moon.wkt"));
    Geometry moved = translate(g);
    assertFalse(moved.isEmpty());
    String wkt = new CurveWKTWriter().write(moved);
    assertTrue(wkt.toUpperCase().contains("CURVEPOLYGON")
        || wkt.toUpperCase().contains("COMPOUNDCURVE"));
    assertTrue(wkt.toUpperCase().contains("CIRCULARSTRING"));
    assertFalse(wkt.toUpperCase().contains("EMPTY"));
    assertEquals(g.getGeometryType(), moved.getGeometryType());
  }

  private static void assertCurveTranslatePreserved(File path)
      throws Exception {
    Geometry g = readCurve(path);
    Geometry moved = translate(g);
    assertFalse(moved.isEmpty());
    assertEquals(g.getGeometryType(), moved.getGeometryType());
    String wkt = new CurveWKTWriter().write(moved);
    String u = wkt.toUpperCase();
    assertTrue(u.contains("CIRCULARSTRING") || u.contains("CURVEPOLYGON"));
    assertFalse(u.contains("EMPTY"));
    assertEquals(g.getArea(), moved.getArea(), 1.0e-9);
  }

  private static Geometry translate(Geometry g) {
    // Same apply as Function-tree AffineTransformation.translate / MoveTool.
    return AffineTransformation.translationInstance(DX, DY).transform(g);
  }

  private static File fixture(String name) {
    return TbAppiumPaths.fixture(name);
  }

  private static Geometry readPlain(File path) throws Exception {
    return new WKTReader().read(TbAppiumPaths.readFile(path).trim());
  }

  private static Geometry readCurve(File path) throws Exception {
    return new CurveWKTReader(new CurveGeometryFactory())
        .read(TbAppiumPaths.readFile(path).trim());
  }
}

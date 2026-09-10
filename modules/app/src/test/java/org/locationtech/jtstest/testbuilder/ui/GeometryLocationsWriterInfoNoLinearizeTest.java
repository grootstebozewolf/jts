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
package org.locationtech.jtstest.testbuilder.ui;

import java.util.ArrayList;
import java.util.List;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.curve.CurveGeometryFactory;
import org.locationtech.jts.geom.curve.CurveLinearizationStrategy;
import org.locationtech.jts.io.ParseException;
import org.locationtech.jts.io.curve.CurveWKTReader;

import junit.framework.TestCase;
import junit.textui.TestRunner;

/**
 * UX issue #84: Info / tooltip on a {@code GEOMETRYCOLLECTION} of a
 * {@code CIRCULARSTRING} must not dump
 * {@code CurveOps.linearise} warnings into the Log tab.
 * Same path as {@code GeometryEditPanel.getInfo} /
 * {@code getToolTipText} → {@link GeometryLocationsWriter} →
 * {@link GeometryElementLocater}.
 */
public class GeometryLocationsWriterInfoNoLinearizeTest extends TestCase {

  private static final String INPUT =
      "GEOMETRYCOLLECTION (CIRCULARSTRING (172 410, 180 398, 190 380, 196 370, "
          + "210 349, 225 329, 237 314, 258 284, 279 264, 299 245, 311 237, "
          + "330 225, 361 215, 371 221, 387 232, 395 238, 406 248, 413 256, "
          + "510 330))";

  private static final Coordinate CLICK = new Coordinate(172, 410);

  public GeometryLocationsWriterInfoNoLinearizeTest(String name) {
    super(name);
  }

  public static void main(String[] args) {
    TestRunner.run(GeometryLocationsWriterInfoNoLinearizeTest.class);
  }

  protected void tearDown() {
    CurveLinearizationStrategy.setWarnSink(null);
  }

  public void testInfoDoesNotWarnLinearise() throws ParseException {
    Geometry g = read(INPUT);
    final List warns = new ArrayList();
    CurveLinearizationStrategy.setWarnSink(
        new CurveLinearizationStrategy.WarnSink() {
          public void warn(String message) {
            warns.add(message);
          }
        });

    GeometryLocationsWriter writer = new GeometryLocationsWriter();
    writer.setHtml(false);
    String info = writer.writeElementLocation(g, CLICK, 5.0);

    assertNotNull(info);
    assertTrue("inspect must name CIRCULARSTRING, got " + info,
        info.indexOf("CIRCULARSTRING") >= 0);
    assertTrue("inspect must keep control count, got " + info,
        info.indexOf("(19)") >= 0);
    assertTrue("inspect must show length, got " + info,
        info.indexOf("Len:") >= 0);
    assertTrue("Info/tooltip must not densify, warns=" + warns, warns.isEmpty());
  }

  public void testRepeatedHoverDoesNotAccumulateWarns() throws ParseException {
    Geometry g = read(INPUT);
    final List warns = new ArrayList();
    CurveLinearizationStrategy.setWarnSink(
        new CurveLinearizationStrategy.WarnSink() {
          public void warn(String message) {
            warns.add(message);
          }
        });

    GeometryLocationsWriter writer = new GeometryLocationsWriter();
    writer.setHtml(true);
    for (int i = 0; i < 50; i++) {
      writer.writeElementLocation(g, CLICK, 5.0);
    }
    assertEquals("tooltip hover must not flood WarnSink, n=" + warns.size(),
        0, warns.size());
  }

  private static Geometry read(String wkt) throws ParseException {
    return new CurveWKTReader(new CurveGeometryFactory()).read(wkt);
  }
}

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
package org.locationtech.jtstest.testbuilder.model;

import java.util.ArrayList;
import java.util.List;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.PrecisionModel;
import org.locationtech.jts.geom.curve.CircularString;
import org.locationtech.jts.io.curve.CurveWKTWriter;

import junit.framework.TestCase;
import junit.textui.TestRunner;

/**
 * Pins {@link GeometryEditModel#addComponent} for
 * {@link GeometryType#CIRCULARSTRING}: the drawn triple must be a real
 * {@link CircularString}, not a 3-point {@code LINESTRING}.
 * <p>
 * No GUI. {@code JTSTestBuilder.getGeometryFactory()} returns a
 * {@code CurveGeometryFactory} when the app is not started.
 */
public class GeometryEditModelCircularStringTest extends TestCase {

  public GeometryEditModelCircularStringTest(String name) {
    super(name);
  }

  public static void main(String[] args) {
    TestRunner.run(GeometryEditModelCircularStringTest.class);
  }

  public void testAddComponentCircularStringIsNotLineString() {
    GeometryEditModel model = new GeometryEditModel();
    model.setTestCase(new TestCaseEdit(new PrecisionModel()));
    model.setGeometryType(GeometryType.CIRCULARSTRING);

    List coords = new ArrayList();
    coords.add(new Coordinate(-5, 0));
    coords.add(new Coordinate(0, 5));
    coords.add(new Coordinate(5, 0));
    model.addComponent(coords);

    Geometry g = model.getGeometry();
    assertTrue(g instanceof CircularString);
    assertFalse(g.getClass().equals(LineString.class));

    String wkt = new CurveWKTWriter().write(g);
    assertTrue("addComponent CIRCULARSTRING must emit CIRCULARSTRING, got " + wkt,
        wkt.startsWith("CIRCULARSTRING"));
    assertFalse(wkt.startsWith("LINESTRING"));
  }
}

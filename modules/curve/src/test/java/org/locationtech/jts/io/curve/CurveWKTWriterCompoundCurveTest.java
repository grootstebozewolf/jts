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
import org.locationtech.jts.geom.curve.CircularString;
import org.locationtech.jts.geom.curve.CompoundCurve;
import org.locationtech.jts.geom.curve.CurveGeometryFactory;

import junit.textui.TestRunner;
import test.jts.GeometryTestCase;

/**
 * Locks in the structural emitter for {@link CompoundCurve}: members
 * must round-trip with their segment kinds preserved
 * (CIRCULARSTRING vs untagged LineString chunks).
 */
public class CurveWKTWriterCompoundCurveTest extends GeometryTestCase {

  public static void main(String[] args) {
    TestRunner.run(CurveWKTWriterCompoundCurveTest.class);
  }

  public CurveWKTWriterCompoundCurveTest(String name) { super(name); }

  public void testEmittedWKTContainsTaggedCircularString() throws Exception {
    String src = "COMPOUNDCURVE ((5 3, 5 13), CIRCULARSTRING (5 13, 7 15, 9 13))";
    Geometry g = new CurveWKTReader().read(src);
    String emitted = new CurveWKTWriter().write(g);
    assertTrue("emitted WKT must include CIRCULARSTRING tag for the arc member, was: " + emitted,
        emitted.toUpperCase().contains("CIRCULARSTRING"));
  }

  public void testRoundTripPreservesMemberStructure() throws Exception {
    String src = "COMPOUNDCURVE ((5 3, 5 13), CIRCULARSTRING (5 13, 7 15, 9 13), (9 13, 9 3))";
    CompoundCurve g = (CompoundCurve) new CurveWKTReader().read(src);
    assertEquals(3, g.getNumMembers());
    assertFalse(g.getMemberN(0) instanceof CircularString);
    assertTrue(g.getMemberN(1) instanceof CircularString);
    assertFalse(g.getMemberN(2) instanceof CircularString);

    String emitted = new CurveWKTWriter().write(g);
    CompoundCurve g2 = (CompoundCurve) new CurveWKTReader().read(emitted);
    assertEquals(3, g2.getNumMembers());
    assertFalse(g2.getMemberN(0) instanceof CircularString);
    assertTrue(g2.getMemberN(1) instanceof CircularString);
    assertFalse(g2.getMemberN(2) instanceof CircularString);
  }

  public void testEmittedWKTRoundTripsViaReader() throws Exception {
    CurveGeometryFactory f = new CurveGeometryFactory();
    LineString line = f.createLineString(new Coordinate[] {
        new Coordinate(0, 0), new Coordinate(10, 0)
    });
    CircularString arc = f.createCircularString(f.getCoordinateSequenceFactory().create(new Coordinate[] {
        new Coordinate(10, 0), new Coordinate(15, 5), new Coordinate(20, 0)
    }));
    CompoundCurve cc = new CompoundCurve(new LineString[] { line, arc }, f);

    String emitted = new CurveWKTWriter().write(cc);
    CompoundCurve back = (CompoundCurve) new CurveWKTReader(f).read(emitted);
    assertEquals(2, back.getNumMembers());
    assertFalse(back.getMemberN(0) instanceof CircularString);
    assertTrue(back.getMemberN(1) instanceof CircularString);
    // chord points preserved
    assertEquals(0.0,  back.getCoordinates()[0].x, 0.0);
    assertEquals(20.0, back.getCoordinates()[3].x, 0.0);
  }

  public void testEmptyCompoundCurveRoundTrip() throws Exception {
    CurveGeometryFactory f = new CurveGeometryFactory();
    CompoundCurve cc = new CompoundCurve(new LineString[0], f);
    String emitted = new CurveWKTWriter().write(cc);
    assertTrue(emitted.toUpperCase().contains("COMPOUNDCURVE"));
    assertTrue(emitted.toUpperCase().contains("EMPTY"));
    Geometry back = new CurveWKTReader(f).read(emitted);
    assertTrue(back instanceof CompoundCurve);
    assertTrue(back.isEmpty());
  }
}

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
package org.locationtech.jtstest.testbuilder.geom;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.curve.CircularString;
import org.locationtech.jts.geom.curve.MultiCurve;
import org.locationtech.jts.geom.curve.ClothoidSegment;
import org.locationtech.jts.geom.curve.CompoundCurve;
import org.locationtech.jts.geom.curve.CurveGeometryFactory;
import org.locationtech.jts.geom.curve.CurvePolygon;
import org.locationtech.jts.io.curve.CurveWKTWriter;

import junit.framework.TestCase;
import junit.textui.TestRunner;

/**
 * Pins TestBuilder construction of CircularString / CompoundCurve /
 * CurvePolygon.
 * <p>
 * Honest factory on this branch: {@code createCircularString} then
 * {@code createCompoundCurve(LineString[])}. The legacy
 * {@code createCompoundCurve(CoordinateSequence)} wraps a plain
 * LineString member — WKT would still say COMPOUNDCURVE but the
 * member would be a control-point polyline. A drawn CircularString
 * triple must stay a {@code CircularString} ({@code CIRCULARSTRING}
 * in CurveWKT), not a 3-point {@code LINESTRING}. These tests refuse
 * that lie.
 * <p>
 * No Swing. CIRCLE_5 control points from jts-curve overlay fixtures.
 */
public class GeometryCombinerCurveTest extends TestCase {

  /** CIRCLE_5 upper semicircle: (-5 0, 0 5, 5 0). */
  private static final Coordinate[] UPPER = new Coordinate[] {
      new Coordinate(-5, 0), new Coordinate(0, 5), new Coordinate(5, 0)
  };

  /** CIRCLE_5 lower semicircle: (5 0, 0 -5, -5 0). */
  private static final Coordinate[] LOWER = new Coordinate[] {
      new Coordinate(5, 0), new Coordinate(0, -5), new Coordinate(-5, 0)
  };

  private static final Coordinate[] CIRCLE = new Coordinate[] {
      new Coordinate(-5, 0), new Coordinate(0, 5), new Coordinate(5, 0),
      new Coordinate(0, -5), new Coordinate(-5, 0)
  };

  private static final double RADIUS = 5.0;
  private static final double ARC_EPS = 1e-8;

  public GeometryCombinerCurveTest(String name) {
    super(name);
  }

  public static void main(String[] args) {
    TestRunner.run(GeometryCombinerCurveTest.class);
  }

  private GeometryCombiner combiner() {
    return new GeometryCombiner(new CurveGeometryFactory());
  }

  private static String wkt(Geometry g) {
    return new CurveWKTWriter().write(g);
  }

  public void testAddCircularStringReturnsCircularStringNotLineString() {
    Geometry g = combiner().addCircularString(null, UPPER);
    assertTrue(g instanceof CircularString);
    assertFalse(g.getClass().equals(LineString.class));
    assertFalse(g instanceof CompoundCurve);

    String emitted = wkt(g);
    assertTrue("drawn triple must be CIRCULARSTRING, not LINESTRING: " + emitted,
        emitted.startsWith("CIRCULARSTRING"));
    assertFalse(emitted.startsWith("LINESTRING"));

    double arcLen = RADIUS * Math.PI;
    double chordLen = UPPER[0].distance(UPPER[1]) + UPPER[1].distance(UPPER[2]);
    assertEquals(arcLen, g.getLength(), ARC_EPS);
    assertTrue("length must be the arc, not the control-point chord",
        g.getLength() > chordLen);
  }

  public void testAddCircularStringWithCoreFactoryStillReturnsCircularString() {
    GeometryCombiner core = new GeometryCombiner(new GeometryFactory());
    Geometry g = core.addCircularString(null, UPPER);
    assertTrue(g instanceof CircularString);
    assertTrue(wkt(g).startsWith("CIRCULARSTRING"));
    assertFalse(wkt(g).startsWith("LINESTRING"));
  }

  public void testAddCircularStringEvenLeftoverAborts() {
    Coordinate[] even = new Coordinate[] {
        new Coordinate(-5, 0), new Coordinate(0, 5),
        new Coordinate(5, 0), new Coordinate(0, -5)
    };
    assertNull(combiner().addCircularString(null, even));
    assertNull(combiner().addCircularString(null, new Coordinate[] {
        new Coordinate(-5, 0), new Coordinate(0, 5)
    }));
  }

  public void testAddCircularStringOntoExistingStaysCircularStringMembers() {
    Geometry first = combiner().addCircularString(null, UPPER);
    Geometry both = combiner().addCircularString(first, LOWER);
    assertTrue(both instanceof MultiCurve);
    assertEquals(2, both.getNumGeometries());
    assertTrue(both.getGeometryN(0) instanceof CircularString);
    assertTrue(both.getGeometryN(1) instanceof CircularString);
    assertFalse(both.getGeometryN(0).getClass().equals(LineString.class));
    String emitted = wkt(both);
    assertTrue("second drawn arc must keep CIRCULARSTRING members: " + emitted,
        emitted.contains("CIRCULARSTRING"));
    assertFalse(emitted.startsWith("LINESTRING"));
    assertFalse(emitted.startsWith("MULTILINESTRING"));
  }

  public void testOneMemberCompoundCurveIsCircularStringNotPolyline() {
    Geometry g = combiner().addCompoundCurve(null, UPPER);
    assertTrue(g instanceof CompoundCurve);
    CompoundCurve cc = (CompoundCurve) g;
    assertEquals(1, cc.getNumMembers());
    assertTrue(cc.getMemberN(0) instanceof CircularString);
    assertFalse(cc.getMemberN(0).getClass().equals(LineString.class));

    String emitted = wkt(cc);
    assertTrue("WKT must tag the member as CIRCULARSTRING, not a bare polyline: "
        + emitted, emitted.startsWith("COMPOUNDCURVE (CIRCULARSTRING"));

    double arcLen = RADIUS * Math.PI;
    double chordLen = UPPER[0].distance(UPPER[1]) + UPPER[1].distance(UPPER[2]);
    assertEquals(arcLen, cc.getMemberN(0).getLength(), ARC_EPS);
    assertTrue("member length must be the arc, not the control-point chord",
        cc.getMemberN(0).getLength() > chordLen);
    assertEquals(arcLen, cc.getLength(), ARC_EPS);
  }

  public void testTwoJoiningSemicirclesBecomeCompoundCurve() {
    Geometry g = combiner().addCompoundCurve(null, new Coordinate[][] { UPPER, LOWER });
    assertTrue(g instanceof CompoundCurve);
    CompoundCurve cc = (CompoundCurve) g;
    assertEquals(2, cc.getNumMembers());
    assertTrue(cc.getMemberN(0) instanceof CircularString);
    assertTrue(cc.getMemberN(1) instanceof CircularString);

    String emitted = wkt(cc);
    assertTrue("stadium WKT must start COMPOUNDCURVE (CIRCULARSTRING: " + emitted,
        emitted.startsWith("COMPOUNDCURVE (CIRCULARSTRING"));

    double semi = RADIUS * Math.PI;
    assertEquals(semi, cc.getMemberN(0).getLength(), ARC_EPS);
    assertEquals(semi, cc.getMemberN(1).getLength(), ARC_EPS);
    assertEquals(2.0 * semi, cc.getLength(), ARC_EPS);
  }

  public void testOddCountCircularStringPieceAccepted() {
    Geometry g = combiner().addCompoundCurve(null, CIRCLE);
    assertTrue(g instanceof CompoundCurve);
    CompoundCurve cc = (CompoundCurve) g;
    assertEquals(1, cc.getNumMembers());
    assertTrue(cc.getMemberN(0) instanceof CircularString);
    assertEquals(5, cc.getMemberN(0).getNumPoints());
    assertTrue(wkt(cc).startsWith("COMPOUNDCURVE (CIRCULARSTRING"));
  }

  public void testEvenLeftoverAbortsCompoundCurve() {
    Coordinate[] even = new Coordinate[] {
        new Coordinate(-5, 0), new Coordinate(0, 5),
        new Coordinate(5, 0), new Coordinate(0, -5)
    };
    assertNull(combiner().addCompoundCurve(null, even));
    assertNull(combiner().addCompoundCurve(null, new Coordinate[][] { even }));
    assertNull(combiner().addCompoundCurve(null,
        new Coordinate[][] { UPPER, new Coordinate[] {
            new Coordinate(5, 0), new Coordinate(0, -5)
        } }));
  }

  public void testClosedCircularStringBecomesCurvePolygon() {
    Geometry g = combiner().addCurvePolygon(null, CIRCLE);
    assertTrue(g instanceof CurvePolygon);
    CurvePolygon cp = (CurvePolygon) g;
    assertTrue(cp.getExteriorCurve() instanceof CircularString);
    String emitted = wkt(cp);
    assertTrue("WKT must be CURVEPOLYGON (CIRCULARSTRING: " + emitted,
        emitted.startsWith("CURVEPOLYGON (CIRCULARSTRING"));
  }

  public void testClosedCompoundCurveShellBecomesCurvePolygon() {
    Geometry g = combiner().addCurvePolygon(null, new Coordinate[][] { UPPER, LOWER });
    assertTrue(g instanceof CurvePolygon);
    CurvePolygon cp = (CurvePolygon) g;
    assertTrue(cp.getExteriorCurve() instanceof CompoundCurve);
    CompoundCurve shell = (CompoundCurve) cp.getExteriorCurve();
    assertEquals(2, shell.getNumMembers());
    assertTrue(shell.getMemberN(0) instanceof CircularString);
    String emitted = wkt(cp);
    assertTrue("WKT must nest COMPOUNDCURVE (CIRCULARSTRING: " + emitted,
        emitted.startsWith("CURVEPOLYGON (COMPOUNDCURVE (CIRCULARSTRING"));
  }

  public void testUnclosedCurvePolygonAborts() {
    assertNull(combiner().addCurvePolygon(null, UPPER));
    assertNull(combiner().addCurvePolygon(null, new Coordinate[][] { UPPER }));
  }

  public void testFourPointClosedCircleIsCurvePolygon() {
    Coordinate[] closedFour = new Coordinate[] {
        new Coordinate(-5, 0), new Coordinate(0, 5),
        new Coordinate(5, 0), new Coordinate(-5, 0)
    };
    Geometry g = combiner().addCurvePolygon(null, closedFour);
    assertTrue("construct (A,B,C,A) expands to a 5-token CIRCULARSTRING, got "
        + (g == null ? "null" : wkt(g)), g instanceof CurvePolygon);
    CurvePolygon cp = (CurvePolygon) g;
    assertEquals(5, cp.getExteriorCurve().getNumPoints());
    assertTrue(wkt(g).startsWith("CURVEPOLYGON (CIRCULARSTRING"));
    assertEquals(2.0 * Math.PI * RADIUS, g.getLength(), ARC_EPS);
    assertEquals(Math.PI * RADIUS * RADIUS, g.getArea(), ARC_EPS);
  }

  public void testEvenLeftoverAbortsCurvePolygon() {
    Coordinate[] evenOpen = new Coordinate[] {
        new Coordinate(-5, 0), new Coordinate(0, 5),
        new Coordinate(5, 0), new Coordinate(0, -5)
    };
    assertNull(combiner().addCurvePolygon(null, evenOpen));
  }

  public void testToolbarStreamSplitsIntoJoiningCircularStringMembers() {
    Coordinate[][] pieces = GeometryCombiner.circularStringPieces(CIRCLE);
    assertNotNull(pieces);
    assertEquals(2, pieces.length);
    Geometry g = combiner().addCompoundCurve(null, pieces);
    CompoundCurve cc = (CompoundCurve) g;
    assertEquals(2, cc.getNumMembers());
    assertTrue(cc.getMemberN(0) instanceof CircularString);
    assertTrue(wkt(cc).startsWith("COMPOUNDCURVE (CIRCULARSTRING"));
  }

  public void testHighwayEntryClothoidIsNonLeadingMember() {
    Geometry line = combiner().addCompoundCurveLine(null, new Coordinate[] {
        new Coordinate(0, 0), new Coordinate(100, 0)
    });
    Geometry g = combiner().addClothoid(line, 0.005, 80.0);
    assertTrue(g instanceof CompoundCurve);
    CompoundCurve cc = (CompoundCurve) g;
    assertEquals(2, cc.getNumMembers());
    assertEquals("LineString", cc.getMemberN(0).getGeometryType());
    assertTrue(cc.getMemberN(1) instanceof ClothoidSegment);
    ClothoidSegment cl = (ClothoidSegment) cc.getMemberN(1);
    assertEquals(0.0, cl.getStartKappa(), 0.0);
    assertEquals(0.005, cl.getEndKappa(), 1e-12);
    assertEquals(80.0, cl.getLength(), 0.0);
    String emitted = wkt(cc);
    assertTrue("grammars-v4 highway-entry WKT, got " + emitted,
        emitted.contains("CLOTHOID") && emitted.startsWith("COMPOUNDCURVE ("));
    assertFalse(cc.getMemberN(1).getClass().equals(LineString.class));
  }

  public void testAddClothoidRejectsLeadingAndConstantKappa() {
    assertNull(combiner().addClothoid(null, 0.005, 80.0));
    Geometry line = combiner().addCompoundCurveLine(null, new Coordinate[] {
        new Coordinate(0, 0), new Coordinate(100, 0)
    });
    assertSame(line, combiner().addClothoid(line, 0.0, 80.0));
  }

  public void testDisconnectedPiecesAbort() {
    Coordinate[] other = new Coordinate[] {
        new Coordinate(20, 0), new Coordinate(25, 5), new Coordinate(30, 0)
    };
    assertNull(combiner().addCompoundCurve(null, new Coordinate[][] { UPPER, other }));
  }
}

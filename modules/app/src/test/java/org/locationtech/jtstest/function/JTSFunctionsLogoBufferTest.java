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
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.MultiPolygon;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.geom.curve.Linearizable;
import org.locationtech.jts.geom.curve.MultiCurve;
import org.locationtech.jts.operation.buffer.BufferOp;
import org.locationtech.jts.operation.buffer.BufferParameters;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;
import junit.textui.TestRunner;

/**
 * Locks the hero halo: {@code logoLines} then {@code logoBuffer} at
 * distance 4, {@link BufferParameters#JOIN_MITRE} with a real mitre
 * limit (not 0), {@link BufferParameters#CAP_SQUARE} (box caps).
 * <p>
 * Honesty: named {@link BufferOp} / CHORD-PATH / NAMED-APPROX.
 * {@code toLinear(0.0)} is CircularArcDensifier 1% of radius, then
 * one BufferOp on the whole ISO/IEC 13249-3 MultiCurve. Not a laser.
 * Not clothoid. This test does not assert {@code isApproximate()=false}.
 * <p>
 * Quiet defaults, written not flattened:
 * {@code Buffer.bufferWithParams} still BufferOps the raw MultiCurve
 * (control-point chords); empty Mitre Limit → 0.0 bevels.
 * {@code Buffer.buffer} after logo in A is CAP_ROUND + JOIN_ROUND.
 * Connectedness is already BufferOp union of overlapping sausages
 * (T–S gap 5, d=4, 4+4&gt;5). This is not a weld and not a
 * {@code logoLines} overlay-union.
 */
public class JTSFunctionsLogoBufferTest extends TestCase {

  private static final double DISTANCE = 4.0;
  /** Linearizable 0.0 → CircularArcDensifier 1% of radius. */
  private static final double SAGITTA = 0.0;

  private static final double HEIGHT = 70.0;
  private static final double S_RADIUS = HEIGHT / 4.0;
  private static final double WIDTH = 150.0;

  public static void main(String[] args) { TestRunner.run(suite()); }
  public static Test suite() { return new TestSuite(JTSFunctionsLogoBufferTest.class); }
  public JTSFunctionsLogoBufferTest(String name) { super(name); }

  /**
   * logoLines stays an ISO/IEC 13249-3 MultiCurve of four members.
   * Do not weld J/T/S. Do not overlay-union the letters.
   */
  public void testLogoLinesStaysFourMemberMultiCurve() {
    Geometry logo = JTSFunctions.logoLines(null);
    assertTrue("hero input is MultiCurve, not a unioned overlay",
        logo instanceof MultiCurve);
    assertEquals("J + T-stem + T-crossbar + S", 4, logo.getNumGeometries());
  }

  /**
   * BufferOp on the whole collection already unions. T-top ends at
   * x=127.5, S top at x=132.5, gap=5; at d=4 the sausages overlap
   * (4+4&gt;5). One polygonal result is that overlap, not a weld.
   * {@code bufferEach} is the named disconnect path (off by default).
   */
  public void testOverlappingSausagesAreAlreadyOnePolygon() {
    Geometry halo = JTSFunctions.logoBuffer(null, DISTANCE);
    assertTrue("halo is polygonal, got " + halo.getGeometryType(),
        halo instanceof Polygon || halo instanceof MultiPolygon);
    assertEquals("4+4>5 sausages already union in one BufferOp",
        1, halo.getNumGeometries());

    Geometry each = BufferFunctions.bufferEach(JTSFunctions.logoLines(null), DISTANCE);
    assertEquals("bufferEach is the named per-stroke disconnect (off by default)",
        4, each.getNumGeometries());
    assertFalse("hero is not bufferEach", halo.equalsExact(each));
  }

  /**
   * Params that land are JOIN_MITRE + DEFAULT_MITRE_LIMIT + CAP_SQUARE
   * at distance 4, on toLinear(0.0) + BufferOp. Old logoBuffer set
   * box caps only (JOIN_ROUND default). Mitre limit 0 bevels.
   */
  public void testMitreBoxAndRealMitreLimitLandOnNamedBufferOp() {
    Geometry halo = JTSFunctions.logoBuffer(null, DISTANCE);
    Geometry named = namedBufferOp(BufferParameters.CAP_SQUARE,
        BufferParameters.JOIN_MITRE, BufferParameters.DEFAULT_MITRE_LIMIT);
    assertTrue("logoBuffer is toLinear(0.0) + BufferOp(mitre, box, limit 5)",
        halo.equalsExact(named));

    Geometry squareRound = namedBufferOp(BufferParameters.CAP_SQUARE,
        BufferParameters.JOIN_ROUND, BufferParameters.DEFAULT_MITRE_LIMIT);
    assertFalse("JOIN_MITRE must actually be set; JOIN_ROUND was the old default",
        halo.equalsExact(squareRound));

    Geometry mitreZero = namedBufferOp(BufferParameters.CAP_SQUARE,
        BufferParameters.JOIN_MITRE, 0.0);
    assertFalse("mitreLimit 0 bevels; hero must use a real limit, not 0",
        halo.equalsExact(mitreZero));

    Geometry roundRound = namedBufferOp(BufferParameters.CAP_ROUND,
        BufferParameters.JOIN_ROUND, BufferParameters.DEFAULT_MITRE_LIMIT);
    assertFalse("CAP_SQUARE (box) must land", halo.equalsExact(roundRound));
  }

  /**
   * J base ends at (0, 0) heading west. CAP_SQUARE extends a box past
   * the endpoint; CAP_ROUND is a semicircle of r=4 and misses the
   * square corner.
   */
  public void testBoxCapCornerIsCoveredRoundCapIsNot() {
    Geometry halo = JTSFunctions.logoBuffer(null, DISTANCE);
    Point boxCorner = point(-DISTANCE, DISTANCE);
    assertTrue("CAP_SQUARE corner (-4, 4) must land in the halo",
        halo.covers(boxCorner) || halo.intersects(boxCorner));

    Geometry mitreRound = namedBufferOp(BufferParameters.CAP_ROUND,
        BufferParameters.JOIN_MITRE, BufferParameters.DEFAULT_MITRE_LIMIT);
    assertFalse("round cap must not cover the box corner — otherwise the witness is dead",
        mitreRound.covers(boxCorner) || mitreRound.intersects(boxCorner));
  }

  /**
   * Quiet chainsaw, written not flattened: BufferOp on the raw
   * MultiCurve reads CircularString control triples as polyline
   * chords. The S lower-bowl quarter-arc bulge, offset outward by 4,
   * is on the named halo and off that default.
   */
  public void testNotAThreePointChordChainsaw() {
    Geometry halo = JTSFunctions.logoBuffer(null, DISTANCE);
    Geometry chainsaw = rawControlPointBuffer();

    assertFalse("densified halo must not equal the control-point BufferOp",
        halo.equalsExact(chainsaw));
    assertTrue("toLinear(0.0) must add vertices the 3-point chords cannot",
        halo.getNumPoints() > chainsaw.getNumPoints());

    // S lower bowl (ISO/IEC 13249-3 CircularString): centre (132.5, 17.5),
    // r=17.5, right semicircle. Mid-arc between north and east control
    // points, then +4 outward. The T-bar strip (y≈66–74) does not reach
    // here, so this is not a dead witness under another letter.
    double cx = WIDTH - S_RADIUS;
    double cy = S_RADIUS;
    double ang = 0.25 * Math.PI;
    Point bulge = point(
        cx + (S_RADIUS + DISTANCE) * Math.cos(ang),
        cy + (S_RADIUS + DISTANCE) * Math.sin(ang));
    assertTrue("S-arc offset must be in the named halo, got miss at " + bulge,
        halo.covers(bulge) || halo.intersects(bulge));
    assertFalse("3-point-chord BufferOp must miss the S-arc bulge — otherwise not a chainsaw witness",
        chainsaw.covers(bulge) || chainsaw.intersects(bulge));
  }

  // -- named BufferOp path (same densify + params the hero must use) --------

  private static Geometry namedBufferOp(int capStyle, int joinStyle, double mitreLimit) {
    Geometry lines = JTSFunctions.logoLines(null);
    lines = ((Linearizable) lines).toLinear(SAGITTA);
    BufferParameters bufParams = new BufferParameters();
    bufParams.setEndCapStyle(capStyle);
    bufParams.setJoinStyle(joinStyle);
    bufParams.setMitreLimit(mitreLimit);
    return BufferOp.bufferOp(lines, DISTANCE, bufParams);
  }

  /**
   * The named quiet default: BufferOp on raw curve control points
   * (what {@code bufferWithParams} does). Not the product path.
   */
  private static Geometry rawControlPointBuffer() {
    BufferParameters bufParams = new BufferParameters();
    bufParams.setEndCapStyle(BufferParameters.CAP_SQUARE);
    bufParams.setJoinStyle(BufferParameters.JOIN_MITRE);
    bufParams.setMitreLimit(BufferParameters.DEFAULT_MITRE_LIMIT);
    return BufferOp.bufferOp(JTSFunctions.logoLines(null), DISTANCE, bufParams);
  }

  private static Point point(double x, double y) {
    return new GeometryFactory().createPoint(new Coordinate(x, y));
  }
}

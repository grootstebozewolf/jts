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
 * distance 4, {@link BufferParameters#JOIN_MITRE},
 * {@link BufferParameters#CAP_SQUARE} (box caps).
 * <p>
 * Honesty: named {@link BufferOp} / CHORD-PATH / NAMED-APPROX. Arcs
 * (ISO/IEC 13249-3 {@code CircularString} / {@code CompoundCurve} /
 * {@code MultiCurve}) are densified, then one BufferOp runs on the
 * whole linearized collection. Not a laser. Not clothoid. This test
 * does not assert {@code isApproximate()=false}.
 * <p>
 * {@code BufferFunctions.bufferWithParams} on the raw MultiCurve stays
 * the named quiet-chainsaw default and is not the product path here.
 */
public class JTSFunctionsLogoBufferTest extends TestCase {

  private static final double DISTANCE = 4.0;
  private static final double SAGITTA = Math.max(0.001, Math.abs(DISTANCE) / 100.0);

  private static final double HEIGHT = 70.0;
  private static final double J_WIDTH = 30.0;
  private static final double S_RADIUS = HEIGHT / 4.0;
  private static final double WIDTH = 150.0;

  public static void main(String[] args) { TestRunner.run(suite()); }
  public static Test suite() { return new TestSuite(JTSFunctionsLogoBufferTest.class); }
  public JTSFunctionsLogoBufferTest(String name) { super(name); }

  /**
   * logoLines stays an ISO/IEC 13249-3 MultiCurve of four members.
   * Overlay-union of the letters is not this path.
   */
  public void testLogoLinesStaysFourMemberMultiCurve() {
    Geometry logo = JTSFunctions.logoLines(null);
    assertTrue("hero input is MultiCurve, not a unioned overlay",
        logo instanceof MultiCurve);
    assertEquals("J + T-stem + T-crossbar + S", 4, logo.getNumGeometries());
  }

  /**
   * One BufferOp on the whole linearized logo. Letter offsets at d=4
   * overlap (T–S gap is 5), so the halo is one polygon, not four
   * isolated per-letter buffers from bufferEach.
   */
  public void testOneBufferOpConnectsOverlappingLetterOffsets() {
    Geometry halo = JTSFunctions.logoBuffer(null, DISTANCE);
    assertTrue("halo is polygonal, got " + halo.getGeometryType(),
        halo instanceof Polygon || halo instanceof MultiPolygon);
    assertEquals("overlapping d=4 offsets union in one BufferOp",
        1, halo.getNumGeometries());

    Geometry each = BufferFunctions.bufferEach(JTSFunctions.logoLines(null), DISTANCE);
    assertEquals("bufferEach keeps a member per logo stroke",
        4, each.getNumGeometries());
    assertFalse("hero is not bufferEach", halo.equalsExact(each));
  }

  /**
   * Params that land are JOIN_MITRE + CAP_SQUARE at distance 4, on the
   * named toLinear + BufferOp path. Old logoBuffer set box caps only
   * (JOIN_ROUND default).
   */
  public void testMitreAndBoxCapsLandOnNamedBufferOp() {
    Geometry halo = JTSFunctions.logoBuffer(null, DISTANCE);
    Geometry named = namedBufferOp(BufferParameters.CAP_SQUARE, BufferParameters.JOIN_MITRE);
    assertTrue("logoBuffer is toLinear(sagitta) + BufferOp(mitre, box)",
        halo.equalsExact(named));

    Geometry squareRound = namedBufferOp(BufferParameters.CAP_SQUARE, BufferParameters.JOIN_ROUND);
    assertFalse("JOIN_MITRE must actually be set; JOIN_ROUND was the old default",
        halo.equalsExact(squareRound));

    Geometry roundRound = namedBufferOp(BufferParameters.CAP_ROUND, BufferParameters.JOIN_ROUND);
    assertFalse("CAP_SQUARE (box) must land", halo.equalsExact(roundRound));
  }

  /**
   * J stem corner (30, 70) is a 90° vertex. Mitre reaches the (4,4)
   * offset; a round join is a r=4 fillet and misses that tip.
   */
  public void testMitreTipIsCoveredRoundJoinIsNot() {
    Geometry halo = JTSFunctions.logoBuffer(null, DISTANCE);
    Point mitreTip = point(J_WIDTH + DISTANCE, HEIGHT + DISTANCE);
    assertTrue("JOIN_MITRE tip (34, 74) must land in the halo",
        halo.covers(mitreTip) || halo.intersects(mitreTip));

    Geometry squareRound = namedBufferOp(BufferParameters.CAP_SQUARE, BufferParameters.JOIN_ROUND);
    assertFalse("round join must not cover the mitre tip — otherwise the witness is dead",
        squareRound.covers(mitreTip) || squareRound.intersects(mitreTip));
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

    Geometry mitreRound = namedBufferOp(BufferParameters.CAP_ROUND, BufferParameters.JOIN_MITRE);
    assertFalse("round cap must not cover the box corner — otherwise the witness is dead",
        mitreRound.covers(boxCorner) || mitreRound.intersects(boxCorner));
  }

  /**
   * Quiet chainsaw: BufferOp on the raw MultiCurve reads CircularString
   * control triples as polyline chords. The S upper-bowl quarter-arc
   * bulge, offset outward by 4, is on the named halo and off the
   * chainsaw.
   */
  public void testNotAThreePointChordChainsaw() {
    Geometry halo = JTSFunctions.logoBuffer(null, DISTANCE);
    Geometry chainsaw = rawControlPointBuffer();

    assertFalse("densified halo must not equal the control-point BufferOp",
        halo.equalsExact(chainsaw));
    assertTrue("named densify must add vertices the 3-point chords cannot",
        halo.getNumPoints() > chainsaw.getNumPoints());

    // S upper bowl: centre (132.5, 52.5), r=17.5, left semicircle.
    // Mid-arc between north and west control points, then +4 outward.
    double cx = WIDTH - S_RADIUS;
    double cy = HEIGHT - S_RADIUS;
    double ang = 0.75 * Math.PI;
    Point bulge = point(
        cx + (S_RADIUS + DISTANCE) * Math.cos(ang),
        cy + (S_RADIUS + DISTANCE) * Math.sin(ang));
    assertTrue("S-arc offset must be in the named halo, got miss at " + bulge,
        halo.covers(bulge) || halo.intersects(bulge));
    assertFalse("3-point-chord BufferOp must miss the S-arc bulge — otherwise not a chainsaw witness",
        chainsaw.covers(bulge) || chainsaw.intersects(bulge));
  }

  // -- named BufferOp path (same densify + params the hero must use) --------

  private static Geometry namedBufferOp(int capStyle, int joinStyle) {
    Geometry lines = JTSFunctions.logoLines(null);
    lines = ((Linearizable) lines).toLinear(SAGITTA);
    BufferParameters bufParams = new BufferParameters();
    bufParams.setEndCapStyle(capStyle);
    bufParams.setJoinStyle(joinStyle);
    return BufferOp.bufferOp(lines, DISTANCE, bufParams);
  }

  /** The named quiet default: BufferOp on raw curve control points. */
  private static Geometry rawControlPointBuffer() {
    BufferParameters bufParams = new BufferParameters();
    bufParams.setEndCapStyle(BufferParameters.CAP_SQUARE);
    bufParams.setJoinStyle(BufferParameters.JOIN_MITRE);
    return BufferOp.bufferOp(JTSFunctions.logoLines(null), DISTANCE, bufParams);
  }

  private static Point point(double x, double y) {
    return new GeometryFactory().createPoint(new Coordinate(x, y));
  }
}

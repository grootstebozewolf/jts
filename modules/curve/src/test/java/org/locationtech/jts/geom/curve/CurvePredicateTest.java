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
package org.locationtech.jts.geom.curve;

import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.io.curve.CurveWKTReader;

import junit.textui.TestRunner;
import test.jts.GeometryTestCase;

/**
 * CRV-REL: the spatial predicates must see the arc, in both error directions.
 * <p>
 * The inherited predicates evaluate {@code getCoordinates()}, so a curve is
 * judged by its control polygon. For the radius-5 circle whose control points
 * are the four axis extremes, that polygon is the inscribed diamond
 * {@code |x| + |y| <= 5}, and the errors run both ways:
 * <ul>
 * <li><b>False negative.</b> {@code POINT (3 3)} is 4.243 from the centre --
 *     inside the circle -- but {@code |3|+|3| = 6 > 5}, outside the diamond. So
 *     {@code contains} answered false for a point the polygon contains. A
 *     containment filter using it silently drops interior features.</li>
 * <li><b>False positive.</b> The chord from {@code (0 5)} to {@code (5 0)}
 *     passes through {@code (3.5, 1.5)}; the true arc at {@code x = 3.5} is at
 *     {@code y = 3.571}. A segment spanning {@code (3.5 1)..(3.5 2)} intersects
 *     the chord and misses the arc entirely, so {@code intersects} answered true
 *     for a geometry the curve never touches.</li>
 * </ul>
 * Booleans have no tolerance to hide in: an area can be approximately right, a
 * predicate is right or wrong. This is the same polarity concern the overlay
 * ratchet's margin gate addresses -- a wrong {@code covers} or {@code disjoint}
 * feeds decisions downstream that nothing re-checks.
 * <p>
 * <b>The remedy and its reach.</b> One override per type carries almost the whole
 * family: {@code contains}, {@code covers}, {@code touches}, {@code crosses},
 * {@code overlaps} and {@code equalsTopo} all route through
 * {@code this.relate(other)}, so overriding {@code relate} makes them arc-aware
 * at once. Three do not: {@code intersects} takes a {@code RectangleIntersects}
 * fast path over raw coordinates when the <em>other</em> operand is a rectangle,
 * and {@code within} / {@code coveredBy} delegate to
 * {@code other.contains(this)} / {@code other.covers(this)} -- dispatching on
 * the wrong object. Those three are overridden individually, and
 * {@code disjoint} follows from {@code intersects}.
 * <p>
 * Reverse {@code plain.op(curve)} is flipped in {@code Geometry} onto the
 * curve receiver, so the same overrides run in both orders. Difference
 * is not flipped; it is routed through OverlayNGCurve as
 * {@code (plain, curve)}. MultiCurve / MultiSurface override the same
 * family. A point lying <em>exactly on</em> the arc is inside the
 * densification band, where no inscribed approximation can answer --
 * boundary-touching input remains undecidable until an arc-aware noder
 * exists.
 */
public class CurvePredicateTest extends GeometryTestCase {

  private static final String CIRCLE_5 =
      "CURVEPOLYGON (CIRCULARSTRING (-5 0, 0 5, 5 0, 0 -5, -5 0))";
  private static final String CIRCLE_3 =
      "CURVEPOLYGON (CIRCULARSTRING (-3 0, 0 3, 3 0, 0 -3, -3 0))";

  /** Inside the circle (4.243 < 5), outside the control diamond (6 > 5). */
  private static final String BULGE_POINT = "POINT (3 3)";

  /**
   * Wholly inside the circle, wholly outside the diamond. The far corner
   * {@code (3.4, 3.4)} is at distance 4.808 &lt; 5; the near corner
   * {@code (3.2, 3.2)} has {@code |x|+|y| = 6.4 > 5}.
   * <p>
   * The first version of this square ran to 3.6, whose far corner sits at
   * distance 5.091 -- outside the circle -- and the arc-aware {@code covers}
   * correctly answered false against the test's wrong premise. The predicate
   * under test refuted its own test data; only the square changed.
   */
  private static final String BULGE_SQUARE =
      "POLYGON ((3.2 3.2, 3.4 3.2, 3.4 3.4, 3.2 3.4, 3.2 3.2))";

  /** Crosses the chord (0 5)-(5 0) at (3.5, 1.5); the arc there is at y=3.571. */
  private static final String CHORD_ONLY_SEGMENT = "LINESTRING (3.5 1, 3.5 2)";

  public static void main(String[] args) { TestRunner.run(CurvePredicateTest.class); }

  public CurvePredicateTest(String name) { super(name); }

  private static Geometry readCurve(String wkt) throws Exception {
    return new CurveWKTReader(new CurveGeometryFactory()).read(wkt);
  }

  // -- false negatives: the bulge is part of the polygon -------------------

  public void testContainsBulgePoint() throws Exception {
    assertTrue("(3 3) is 4.243 from the centre of a radius-5 circle",
        readCurve(CIRCLE_5).contains(readCurve(BULGE_POINT)));
  }

  public void testIntersectsBulgePoint() throws Exception {
    assertTrue("contains implies intersects",
        readCurve(CIRCLE_5).intersects(readCurve(BULGE_POINT)));
  }

  public void testCoversBulgeSquare() throws Exception {
    assertTrue("a square wholly inside the circle is covered by it",
        readCurve(CIRCLE_5).covers(readCurve(BULGE_SQUARE)));
  }

  public void testDisjointBulgePointIsFalse() throws Exception {
    assertFalse("disjoint must agree with intersects",
        readCurve(CIRCLE_5).disjoint(readCurve(BULGE_POINT)));
  }

  /** within dispatches on the curve but delegates to other.contains(this). */
  public void testInnerCircleIsWithinOuter() throws Exception {
    assertTrue("the r=3 circle lies within the r=5 circle",
        readCurve(CIRCLE_3).within(readCurve(CIRCLE_5)));
  }

  /** coveredBy likewise delegates to other.covers(this). */
  public void testInnerCircleIsCoveredByOuter() throws Exception {
    assertTrue("the r=3 circle is covered by the r=5 circle",
        readCurve(CIRCLE_3).coveredBy(readCurve(CIRCLE_5)));
  }

  /**
   * The false-positive side of within, and the security-relevant one: claiming
   * containment that does not hold. The r=3 circle's control points (all at
   * distance 3) sit inside the r=4 diamond, but its bulge reaches
   * {@code (2.12, 2.12)} where {@code |x|+|y| = 4.24 > 4} -- outside. The chord
   * reading says within; the truth is not-within. A wrong "within" here is a
   * geofence that reports a feature safely inside a boundary it actually
   * breaches.
   */
  public void testBulgingCircleIsNotWithinTheSmallerDiamond() throws Exception {
    Geometry diamond4 = readCurve("POLYGON ((-4 0, 0 4, 4 0, 0 -4, -4 0))");
    assertFalse("the circle's bulge breaches the diamond at 45 degrees",
        readCurve(CIRCLE_3).within(diamond4));
    assertFalse("and coveredBy must agree",
        readCurve(CIRCLE_3).coveredBy(diamond4));
  }

  /** The rectangle fast path reads raw coordinates, bypassing relate. */
  public void testIntersectsRectangleOnTheBulge() throws Exception {
    assertTrue("a rectangle on the bulge intersects the circle even via the "
        + "RectangleIntersects fast path",
        readCurve(CIRCLE_5).intersects(readCurve(BULGE_SQUARE)));
  }

  /** relate itself, since everything else routes through it. */
  public void testRelateSeesTheArc() throws Exception {
    assertTrue("relate should report containment of the bulge point",
        readCurve(CIRCLE_5).relate(readCurve(BULGE_POINT)).isContains());
  }

  // -- false positives: the chords are not part of the curve ---------------

  /**
   * The other polarity, and the sharper one: the chord answer is not an
   * under-claim but a phantom. The segment intersects the control polygon's
   * chord and never comes within 1.5 units of the true circle boundary region it
   * claims to touch... it lies inside the circle, wholly, so for the AREAL curve
   * polygon it intersects. The line version below is the true discriminator.
   */
  public void testCircularStringDoesNotIntersectChordOnlySegment() throws Exception {
    Geometry arc = readCurve("CIRCULARSTRING (-5 0, 0 5, 5 0)");
    assertFalse("the segment spans y 1..2 at x 3.5; the arc passes at y 3.571 -- "
        + "only the chord through the control points is hit",
        arc.intersects(readCurve(CHORD_ONLY_SEGMENT)));
  }

  public void testCircularStringDisjointChordOnlySegment() throws Exception {
    Geometry arc = readCurve("CIRCULARSTRING (-5 0, 0 5, 5 0)");
    assertTrue("disjoint is the complement",
        arc.disjoint(readCurve(CHORD_ONLY_SEGMENT)));
  }

  /** A line that truly enters through the bulge crosses the areal polygon. */
  public void testLineThroughBulgeCrosses() throws Exception {
    assertTrue("a line from inside the bulge to far outside crosses the circle",
        readCurve(CIRCLE_5).crosses(readCurve("LINESTRING (3.4 3.4, 10 10)")));
  }

  // -- CompoundCurve takes the same overrides -------------------------------

  public void testCompoundCurvePolygonContainsBulgePoint() throws Exception {
    Geometry halfDisc = readCurve(
        "CURVEPOLYGON (COMPOUNDCURVE (CIRCULARSTRING (-5 0, 0 5, 5 0), (5 0, -5 0)))");
    assertTrue("(3 3) is inside the upper half-disc",
        halfDisc.contains(readCurve(BULGE_POINT)));
  }

  // -- guards ----------------------------------------------------------------

  /** Guard: plain geometries answer exactly as core always has. */
  public void testPlainPredicatesUnchanged() throws Exception {
    Geometry p = readCurve("POLYGON ((0 0, 10 0, 10 10, 0 10, 0 0))");
    Geometry q = readCurve("POLYGON ((2 2, 4 2, 4 4, 2 4, 2 2))");
    assertTrue(p.contains(q));
    assertTrue(q.within(p));
    assertTrue(p.intersects(q));
    assertFalse(p.disjoint(q));
    assertFalse(p.contains(readCurve("POINT (11 11)")));
  }

  /** Guard: verdicts that were already right stay right. */
  public void testAgreedVerdictsUnchanged() throws Exception {
    Geometry a = readCurve(CIRCLE_5);
    assertTrue("centre point: inside circle and diamond alike",
        a.contains(readCurve("POINT (0 0)")));
    assertFalse("far point: outside both",
        a.contains(readCurve("POINT (100 100)")));
    assertTrue("far circle: disjoint under either reading",
        a.disjoint(readCurve(
            "CURVEPOLYGON (CIRCULARSTRING (100 0, 105 5, 110 0, 105 -5, 100 0))")));
  }

  /**
   * Reverse of {@link #testBulgingCircleIsNotWithinTheSmallerDiamond}: the
   * r=3 circle's control points sit inside the r=4 diamond, but the bulge
   * at 45 degrees breaches it. {@code diamond.contains(circle)} used to
   * follow the control polygon and answer true.
   */
  public void testReverseContainsSeesTheBulge() throws Exception {
    Geometry diamond4 = readCurve("POLYGON ((-4 0, 0 4, 4 0, 0 -4, -4 0))");
    assertFalse("plain.contains(curve) must see the bulge that breaches the diamond",
        diamond4.contains(readCurve(CIRCLE_3)));
    assertFalse("and covers must agree",
        diamond4.covers(readCurve(CIRCLE_3)));
  }

  /**
   * Reverse of {@link #testCircularStringDoesNotIntersectChordOnlySegment}:
   * the segment hits the control chord and misses the arc. Putting the
   * LineString on the left used to answer true.
   */
  public void testReverseIntersectsDoesNotSeeChordOnly() throws Exception {
    Geometry seg = readCurve(CHORD_ONLY_SEGMENT);
    Geometry arc = readCurve("CIRCULARSTRING (-5 0, 0 5, 5 0)");
    assertFalse("plain.intersects(curve) must not report the phantom chord hit",
        seg.intersects(arc));
  }

  /**
   * The r=3 circle is inside the r=5 diamond under both readings. Locked so
   * the reverse flip does not invert a genuinely-true contains.
   */
  public void testReverseContainsWhenArcsAndChordsAgree() throws Exception {
    Geometry plainDiamond = readCurve("POLYGON ((-5 0, 0 5, 5 0, 0 -5, -5 0))");
    assertTrue("r=3 circle is inside the r=5 diamond",
        plainDiamond.contains(readCurve(CIRCLE_3)));
  }

  /**
   * MultiSurface of the r=3 circle must see the same bulge the single
   * disc does: the r=4 diamond does not contain it.
   */
  public void testMultiSurfaceReverseContainsSeesTheBulge() throws Exception {
    Geometry diamond4 = readCurve("POLYGON ((-4 0, 0 4, 4 0, 0 -4, -4 0))");
    Geometry multi = readCurve("MULTISURFACE (" + CIRCLE_3 + ")");
    assertFalse("plain.contains(multi) must see the bulge",
        diamond4.contains(multi));
    assertFalse(multi.within(diamond4));
  }
}

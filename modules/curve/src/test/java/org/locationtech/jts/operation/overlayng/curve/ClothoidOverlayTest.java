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
package org.locationtech.jts.operation.overlayng.curve;

import org.locationtech.jts.algorithm.distance.DiscreteHausdorffDistance;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.curve.CircularString;
import org.locationtech.jts.geom.curve.ClothoidSegment;
import org.locationtech.jts.geom.curve.CompoundCurve;
import org.locationtech.jts.geom.curve.CurveGeometryFactory;
import org.locationtech.jts.geom.curve.CurveOps;
import org.locationtech.jts.geom.curve.CurvePolygon;
import org.locationtech.jts.io.curve.CurveWKTReader;
import org.locationtech.jts.operation.overlayng.OverlayNG;
import org.locationtech.jts.operation.overlayng.OverlayNGRobust;

import junit.textui.TestRunner;
import test.jts.GeometryTestCase;

/**
 * Bar 2 clothoid kit: 0-node identity / disjoint / nest using the
 * analytical clothoid envelope. A pair that needs a clothoid–circle
 * node is {@code CLOTHOID-FRESNEL} -- named miss, never a chord
 * flatten. OverlayNGCurve is never <em>Curved</em>.
 */
public class ClothoidOverlayTest extends GeometryTestCase {

  private static final String CIRCLE_5 =
      "CURVEPOLYGON (CIRCULARSTRING (-5 0, 0 5, 5 0, 0 -5, -5 0))";
  private static final String CIRCLE_CROSSING =
      "CURVEPOLYGON (CIRCULARSTRING (2 0, 7 5, 12 0, 7 -5, 2 0))";
  private static final double DISC = 25.0 * Math.PI;
  private static final double AREA_TOL = 1.0e-3;
  private static final double EXACT = 1.0e-9;

  public static void main(String[] args) {
    TestRunner.run(ClothoidOverlayTest.class);
  }

  public ClothoidOverlayTest(String name) { super(name); }

  private static Geometry readCurve(String wkt) throws Exception {
    return new CurveWKTReader(new CurveGeometryFactory()).read(wkt);
  }

  /**
   * Hole-free leftover: lead along +X, clothoid, close to the origin.
   * The parent sequence of the clothoid is start+end only; the kit
   * must not treat that chord as the edge.
   */
  static CurvePolygon leftover(double x0, double y0, double lead,
      double k0, double k1, double length) {
    CurveGeometryFactory f = new CurveGeometryFactory();
    Coordinate a = new Coordinate(x0, y0);
    Coordinate b = new Coordinate(x0 + lead, y0);
    LineString line = f.createLineString(new Coordinate[] { a, b });
    ClothoidSegment cl = new ClothoidSegment(b, 0.0, k0, k1, length, f);
    LineString close = f.createLineString(new Coordinate[] {
        cl.getEndCoordinate(), a
    });
    return f.createCurvePolygon(
        f.createCompoundCurve(new LineString[] { line, cl, close }), null);
  }

  /** Small leftover whose analytical AABB sits inside CIRCLE_5. */
  static CurvePolygon nestLeftover() {
    return leftover(0.0, 0.0, 1.0, 0.0, 0.1, 2.0);
  }

  /** Same leftover translated far to the right. */
  static CurvePolygon farLeftover() {
    return leftover(200.0, 0.0, 1.0, 0.0, 0.1, 2.0);
  }

  /** Leftover that starts at the origin and exits CIRCLE_5. */
  static CurvePolygon crossingLeftover() {
    return leftover(0.0, 0.0, 1.0, 0.0, 0.05, 20.0);
  }

  public void testIdentityKeepsTheClothoid() throws Exception {
    Geometry a = nestLeftover();
    OverlayNGCurve op = new OverlayNGCurve(a, a);
    Geometry cap = op.getResult(OverlayNG.INTERSECTION);
    assertFalse("CLOTHOID-ID CAP is exact", op.isApproximate());
    assertKeepsClothoid(cap);
    assertEquals("identity CAP is the leftover", a.getArea(), cap.getArea(),
        EXACT);

    OverlayNGCurve sub = new OverlayNGCurve(a, a);
    Geometry empty = sub.getResult(OverlayNG.DIFFERENCE);
    assertFalse("CLOTHOID-ID SUB is exact", sub.isApproximate());
    assertTrue(empty.isEmpty());

    Geometry kit = CompoundCurveShellOverlay.overlay(a, a,
        OverlayNG.INTERSECTION);
    assertNotNull("kit answers identity", kit);
    assertKeepsClothoid(kit);
  }

  public void testDisjointEnvelopesKeepBothShells() throws Exception {
    Geometry a = nestLeftover();
    Geometry b = farLeftover();
    assertFalse("test premise: analytical envelopes do not meet",
        a.getEnvelopeInternal().intersects(b.getEnvelopeInternal()));

    OverlayNGCurve cap = new OverlayNGCurve(a, b);
    Geometry empty = cap.getResult(OverlayNG.INTERSECTION);
    assertFalse("CLOTHOID-DISJOINT CAP is exact", cap.isApproximate());
    assertTrue(empty.isEmpty());

    OverlayNGCurve cup = new OverlayNGCurve(a, b);
    Geometry both = cup.getResult(OverlayNG.UNION);
    assertFalse("CLOTHOID-DISJOINT CUP is exact", cup.isApproximate());
    assertEquals("two members", 2, both.getNumGeometries());
    assertKeepsClothoid(both.getGeometryN(0));
    assertKeepsClothoid(both.getGeometryN(1));

    Geometry kit = CompoundCurveShellOverlay.overlay(a, b,
        OverlayNG.UNION);
    assertNotNull("kit answers envelope-disjoint", kit);
    assertEquals(2, kit.getNumGeometries());
  }

  public void testNestInsideDiscKeepsTheClothoidHole() throws Exception {
    Geometry inner = nestLeftover();
    Geometry disc = readCurve(CIRCLE_5);
    assertTrue("test premise: leftover AABB is inside the disc",
        ClothoidOverlay.strictlyInsideDisc((CurvePolygon) inner,
            CircularDiscOverlay.centreRadius(disc)));

    OverlayNGCurve cap = new OverlayNGCurve(inner, disc);
    Geometry clip = cap.getResult(OverlayNG.INTERSECTION);
    assertFalse("CLOTHOID-NEST CAP is exact", cap.isApproximate());
    assertKeepsClothoid(clip);
    assertParity(inner, disc, OverlayNG.INTERSECTION, clip);

    OverlayNGCurve cup = new OverlayNGCurve(inner, disc);
    Geometry outer = cup.getResult(OverlayNG.UNION);
    assertFalse("CLOTHOID-NEST CUP is exact", cup.isApproximate());
    assertEquals("outer disc", DISC, outer.getArea(), EXACT);

    OverlayNGCurve bite = new OverlayNGCurve(disc, inner);
    Geometry holed = bite.getResult(OverlayNG.DIFFERENCE);
    assertFalse("CLOTHOID-NEST SUB is exact (not the chordsaw)",
        bite.isApproximate());
    assertEquals("one clothoid hole", 1,
        ((CurvePolygon) holed).getNumInteriorRing());
    assertKeepsClothoidHole((CurvePolygon) holed);
    assertEquals("disc minus leftover", DISC - inner.getArea(),
        holed.getArea(), AREA_TOL);
    assertParity(disc, inner, OverlayNG.DIFFERENCE, holed);

    OverlayNGCurve rev = new OverlayNGCurve(inner, disc);
    Geometry empty = rev.getResult(OverlayNG.DIFFERENCE);
    assertFalse("inner \\ disc is exact", rev.isApproximate());
    assertTrue(empty.isEmpty());

    Geometry kit = CompoundCurveShellOverlay.overlay(disc, inner,
        OverlayNG.DIFFERENCE);
    assertNotNull("kit answers the nest punch", kit);
    assertEquals(1, ((CurvePolygon) kit).getNumInteriorRing());
    assertKeepsClothoidHole((CurvePolygon) kit);
  }

  public void testCrossingDiscIsNamedFresnelMiss() throws Exception {
    Geometry cloth = crossingLeftover();
    Geometry disc = readCurve(CIRCLE_5);
    assertTrue("test premise: envelopes meet",
        cloth.getEnvelopeInternal().intersects(disc.getEnvelopeInternal()));
    assertFalse("test premise: leftover is not nested in the disc",
        ClothoidOverlay.strictlyInsideDisc((CurvePolygon) cloth,
            CircularDiscOverlay.centreRadius(disc)));

    assertNull("CLOTHOID-FRESNEL: clothoid–circle nodes are not a kit",
        CompoundCurveShellOverlay.overlay(cloth, disc,
            OverlayNG.INTERSECTION));
    assertNull(ClothoidOverlay.overlay(cloth, disc, OverlayNG.INTERSECTION));

    OverlayNGCurve op = new OverlayNGCurve(cloth, disc);
    Geometry r = op.getResult(OverlayNG.INTERSECTION);
    assertTrue("crossing falls to the chordsaw", op.isApproximate());
    assertFalse(r.isEmpty());
  }

  public void testFlattenDoesNotTreatClothoidAsAChord() throws Exception {
    Geometry cloth = nestLeftover();
    assertNull("flatten refuses a clothoid member",
        TwoNodeClip.flatten((CurvePolygon) cloth));

    Geometry mixed = mixedArcAndClothoid();
    Geometry disc = readCurve(CIRCLE_CROSSING);
    assertTrue(ClothoidOverlay.hasClothoid(mixed));
    assertNull("mixed clothoid+arc vs disc is not a dishonest lens",
        CompoundCurveShellOverlay.overlay(mixed, disc,
            OverlayNG.INTERSECTION));
    OverlayNGCurve op = new OverlayNGCurve(mixed, disc);
    op.getResult(OverlayNG.INTERSECTION);
    assertTrue("mixed clothoid+arc falls to R2, not a chord two-node walk",
        op.isApproximate());
  }

  /**
   * Semicircle plus a clothoid off the diameter. Flatten would turn
   * the clothoid into the diameter chord and answer the half-lens.
   */
  private static Geometry mixedArcAndClothoid() {
    CurveGeometryFactory f = new CurveGeometryFactory();
    CircularString arc = f.createCircularString(
        f.getCoordinateSequenceFactory().create(new Coordinate[] {
            new Coordinate(-5, 0), new Coordinate(0, 5), new Coordinate(5, 0)
        }));
    ClothoidSegment cl = new ClothoidSegment(
        new Coordinate(5, 0), Math.PI, 0.0, 0.02, 3.0, f);
    LineString close = f.createLineString(new Coordinate[] {
        cl.getEndCoordinate(), new Coordinate(-5, 0)
    });
    return f.createCurvePolygon(
        f.createCompoundCurve(new LineString[] { arc, cl, close }), null);
  }

  private static void assertKeepsClothoid(Geometry g) {
    if (g.getNumGeometries() > 1) {
      assertKeepsClothoid(g.getGeometryN(0));
      return;
    }
    assertEquals("CurvePolygon", g.getGeometryType());
    LineString shell = ((CurvePolygon) g).getExteriorCurve();
    assertTrue("shell is a CompoundCurve, got " + shell.getGeometryType(),
        shell instanceof CompoundCurve);
    assertTrue("CompoundCurve shell keeps a ClothoidSegment",
        hasClothoidMember((CompoundCurve) shell));
  }

  private static void assertKeepsClothoidHole(CurvePolygon cp) {
    LineString hole = cp.getInteriorCurveN(0);
    assertTrue(hole instanceof CompoundCurve);
    assertTrue("hole keeps a ClothoidSegment",
        hasClothoidMember((CompoundCurve) hole));
  }

  private static boolean hasClothoidMember(CompoundCurve cc) {
    for (int i = 0; i < cc.getNumMembers(); i++) {
      if (cc.getMemberN(i) instanceof ClothoidSegment) return true;
    }
    return false;
  }

  private static void assertParity(Geometry a, Geometry b, int opCode,
      Geometry laser) {
    Geometry chord = OverlayNGRobust.overlay(
        CurveOps.linearise(a), CurveOps.linearise(b), opCode);
    assertEquals("area vs chord overlay", chord.getArea(), laser.getArea(),
        AREA_TOL);
    double hd = DiscreteHausdorffDistance.distance(
        CurveOps.linearise(laser), chord);
    assertTrue("Hausdorff vs chord overlay " + hd + " > " + AREA_TOL,
        hd <= AREA_TOL);
  }
}

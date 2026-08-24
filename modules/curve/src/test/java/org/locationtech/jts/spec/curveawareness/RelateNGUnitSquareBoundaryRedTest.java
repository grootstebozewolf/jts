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
package org.locationtech.jts.spec.curveawareness;

import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.curve.CurveGeometryFactory;
import org.locationtech.jts.io.curve.CurveWKTReader;
import org.locationtech.jts.operation.relateng.RelateNG;

import junit.framework.Test;
import junit.framework.TestSuite;
import junit.textui.TestRunner;
import test.jts.GeometryTestCase;

/**
 * Red lock for Proofs #67 matrix + boundary on the unit-square witness.
 * <p>
 * 67-a/b (linear self-relate {@code 2FFF1FFF2}, classical ∂ = ring) already
 * hold on {@link RelateNG}. The product gap is that RelateNG still reads
 * {@code CurvePolygon.getExteriorRing()} control chords, so a bulge point
 * of the inscribed disc is Exterior while {@code getBoundary()} is the
 * circular ring and {@code Geometry.relate} reports Interior (claimId 67-d).
 * Opt-in: {@code -Dtest=RelateNGUnitSquareBoundaryRedTest}.
 */
public class RelateNGUnitSquareBoundaryRedTest extends GeometryTestCase {

  /** Proofs 67-a witness. */
  private static final String UNIT_SQUARE =
      "POLYGON ((0 0, 1 0, 1 1, 0 1, 0 0))";

  /** Disc inscribed in the unit square (touches mid-sides). */
  private static final String INSCRIBED_DISC =
      "CURVEPOLYGON (CIRCULARSTRING (0 0.5, 0.5 1, 1 0.5, 0.5 0, 0 0.5))";

  /**
   * Inside the disc ({@code d=0.3√2&lt;½}), outside the control diamond
   * ({@code |x-½|+|y-½|=0.6&gt;½}).
   */
  private static final String BULGE = "POINT (0.8 0.8)";

  private static final String IM_EQUAL = "2FFF1FFF2";
  private static final String IM_PT_IN = "0F2FF1FF2";
  private static final String IM_PT_ON = "FF20F1FF2";
  private static final String IM_PT_OUT = "FF2FF10F2";

  public static void main(String[] args) { TestRunner.run(suite()); }
  public static Test suite() { return new TestSuite(RelateNGUnitSquareBoundaryRedTest.class); }
  public RelateNGUnitSquareBoundaryRedTest(String name) { super(name); }

  private static Geometry readCurve(String wkt) throws Exception {
    return new CurveWKTReader(new CurveGeometryFactory()).read(wkt);
  }

  /** 67-a: unit-square self-relate is OGC equal. */
  public void testLinearUnitSquareSelfRelateIsEqual() throws Exception {
    Geometry sq = readCurve(UNIT_SQUARE);
    assertEquals(IM_EQUAL, RelateNG.relate(sq, sq).toString());
  }

  /** 67-b: RelateNG strata match getBoundary() on the linear ring. */
  public void testLinearUnitSquareBoundaryGraphAgreesWithGetBoundary()
      throws Exception {
    Geometry sq = readCurve(UNIT_SQUARE);
    Geometry mid = readCurve("POINT (0.5 0)");
    Geometry center = readCurve("POINT (0.5 0.5)");
    Geometry out = readCurve("POINT (2 2)");
    Geometry bnd = sq.getBoundary();
    assertEquals(0.0, bnd.distance(mid), 0.0);
    assertTrue(bnd.distance(center) > 0.0);
    assertEquals(IM_PT_ON, RelateNG.relate(sq, mid).toString());
    assertEquals(IM_PT_IN, RelateNG.relate(sq, center).toString());
    assertEquals(IM_PT_OUT, RelateNG.relate(sq, out).toString());
  }

  /**
   * 67-d: RelateNG must use getBoundary() (classical ∂, the circular ring)
   * rather than getExteriorRing() control chords. Geometry.relate already
   * returns interior {@code 0F2FF1FF2} for the bulge; RelateNG reports
   * exterior {@code FF2FF10F2} because it locates against the diamond.
   */
  public void testRelateNGUsesGetBoundaryNotControlChords() throws Exception {
    Geometry disc = readCurve(INSCRIBED_DISC);
    Geometry bulge = readCurve(BULGE);
    Geometry bnd = disc.getBoundary();
    String exact = disc.relate(bulge).toString();
    String ng = RelateNG.relate(disc, bulge).toString();
    assertEquals("Geometry.relate / CurveExact", IM_PT_IN, exact);
    assertTrue("getBoundary is the circular ring (π), not the diamond (2√2)",
        bnd.getLength() > 3.0);
    assertEquals("RelateNG vs classical ∂ / getBoundary", exact, ng);
  }
}

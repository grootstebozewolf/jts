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

import java.lang.reflect.Field;

import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.IntersectionMatrix;
import org.locationtech.jts.io.curve.CurveWKTReader;
import org.locationtech.jts.operation.overlayng.curve.OverlayNGCurve;
import org.locationtech.jts.operation.relate.RelateOp;

import junit.textui.TestRunner;
import test.jts.GeometryTestCase;

/**
 * R.1 TOUCH laser cell. Two externally tangent circular discs:
 * one kiss, no shared flesh. T-ext DE-9IM {@code FF2F01212}.
 * <p>
 * ISO/IEC 13249-3 {@code ST_Touches}: the only points in common lie
 * on the boundaries (DE-9IM {@code FT*******} / {@code F**T*****} /
 * {@code F***T****}). No DOI. Stamp: EXACT. V1 before noding — the
 * class is {@code d² == (r1+r2)²} in {@code R²}, not a hypot
 * quotient and not a noder. Public {@link Geometry#touches} is the
 * RelateOp predicate ({@link IntersectionMatrix#isTouches}) on that
 * matrix. No {@code OverlayNGCurve.TOUCH}. No fifth overlay op.
 * <p>
 * The axis-aligned pair in {@link CurveExactRelateDiscTest} kisses at
 * a shared control vertex, so the control diamonds also touch. This
 * cell pins a 3-4-5 pair whose kiss {@code (4, 3)} is not a control:
 * RelateOp on the control polygons is the lie (disjoint).
 */
public class CurveExactRelateTouchTest extends GeometryTestCase {

  private static final String CIRCLE_5 =
      "CURVEPOLYGON (CIRCULARSTRING (-5 0, 0 5, 5 0, 0 -5, -5 0))";
  /** Centre (8, 6), r=5. Externally tangent to CIRCLE_5; kiss (4, 3). */
  private static final String CIRCLE_3_4_5 =
      "CURVEPOLYGON (CIRCULARSTRING (13 6, 8 11, 3 6, 8 1, 13 6))";
  private static final String MULTI_5 = "MULTISURFACE (" + CIRCLE_5 + ")";
  private static final String MULTI_345 = "MULTISURFACE (" + CIRCLE_3_4_5 + ")";
  private static final String CONTROL_5 =
      "POLYGON ((-5 0, 0 5, 5 0, 0 -5, -5 0))";
  private static final String CONTROL_345 =
      "POLYGON ((13 6, 8 11, 3 6, 8 1, 13 6))";

  /** T-ext: II=F, BB=0. One kiss, no shared flesh. */
  private static final String IM_EXT = CurveExact.IM_AREA_EXT_TANGENT;

  public static void main(String[] args) {
    TestRunner.run(CurveExactRelateTouchTest.class);
  }

  public CurveExactRelateTouchTest(String name) { super(name); }

  private static Geometry readCurve(String wkt) throws Exception {
    return new CurveWKTReader(new CurveGeometryFactory()).read(wkt);
  }

  public void testMatrixIsFF2F01212() {
    assertEquals("FF2F01212", IM_EXT);
    assertTrue("SFS / RelateOp Touch on T-ext",
        new IntersectionMatrix(IM_EXT).isTouches(2, 2));
  }

  public void testR2KissIsExactEquality() throws Exception {
    Geometry a = readCurve(CIRCLE_5);
    Geometry b = readCurve(CIRCLE_3_4_5);
    CircularArcDensifier.Circle da = CurveExact.circularDisc(a);
    CircularArcDensifier.Circle db = CurveExact.circularDisc(b);
    assertNotNull(da);
    assertNotNull(db);
    double dx = da.cx - db.cx;
    double dy = da.cy - db.cy;
    double d2 = dx * dx + dy * dy;
    double sum2 = (da.r + db.r) * (da.r + db.r);
    assertEquals("3-4-5: d² = 8²+6² = 100", 100.0, d2, 0.0);
    assertEquals("(r1+r2)² = 10²", 100.0, sum2, 0.0);
    assertEquals("T-ext is d² == (r1+r2)², no hypot",
        Double.doubleToRawLongBits(d2), Double.doubleToRawLongBits(sum2));
  }

  public void testPublicRelateAndTouches() throws Exception {
    Geometry a = readCurve(CIRCLE_5);
    Geometry b = readCurve(CIRCLE_3_4_5);
    assertEquals(IM_EXT, CurveExact.relate(a, b).toString());
    assertEquals(IM_EXT, CurveExact.relate(b, a).toString());
    assertEquals(IM_EXT, a.relate(b).toString());
    assertEquals(IM_EXT, b.relate(a).toString());
    assertTrue("Geometry.touches via RelateOp isTouches", a.touches(b));
    assertTrue(b.touches(a));
    assertTrue(a.relate(b).isTouches(a.getDimension(), b.getDimension()));
    assertTrue(a.relate(b, IM_EXT));
    assertTrue(a.intersects(b));
    assertFalse("no shared flesh", a.overlaps(b));
    assertFalse(a.contains(b));
    assertFalse(a.covers(b));
    assertFalse(a.crosses(b));
    assertFalse(a.equalsTopo(b));
  }

  public void testControlPolygonRelateOpIsTheLie() throws Exception {
    Geometry a = readCurve(CIRCLE_5);
    Geometry b = readCurve(CIRCLE_3_4_5);
    Geometry diamondA = read(CONTROL_5);
    Geometry diamondB = read(CONTROL_345);
    String lie = RelateOp.relate(diamondA, diamondB).toString();
    assertFalse("control diamonds miss the (4, 3) kiss",
        IM_EXT.equals(lie));
    assertFalse("RelateOp on controls is not Touch",
        diamondA.touches(diamondB));
    assertEquals("laser is not the control-polygon matrix",
        IM_EXT, a.relate(b).toString());
    assertTrue(a.touches(b));
  }

  public void testSingleMemberMultiSurface() throws Exception {
    Geometry ma = readCurve(MULTI_5);
    Geometry mb = readCurve(MULTI_345);
    Geometry b = readCurve(CIRCLE_3_4_5);
    assertEquals(IM_EXT, CurveExact.relate(ma, b).toString());
    assertEquals(IM_EXT, ma.relate(b).toString());
    assertEquals(IM_EXT, CurveExact.relate(ma, mb).toString());
    assertEquals(IM_EXT, ma.relate(mb).toString());
    assertTrue(ma.touches(b));
    assertTrue(ma.touches(mb));
  }

  public void testNoOverlayNGCurveTouch() {
    Field[] fields = OverlayNGCurve.class.getDeclaredFields();
    for (int i = 0; i < fields.length; i++) {
      assertFalse("DE-9IM Touch only — no OverlayNGCurve.TOUCH",
          "TOUCH".equals(fields[i].getName()));
    }
  }
}

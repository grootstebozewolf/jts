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

import java.lang.reflect.Field;

import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.geom.curve.CurveGeometryFactory;
import org.locationtech.jts.geom.curve.CurveOps;
import org.locationtech.jts.geom.curve.CurvePolygon;
import org.locationtech.jts.geom.curve.Linearizable;
import org.locationtech.jts.io.curve.CurveWKTReader;

import junit.textui.TestRunner;
import test.jts.GeometryTestCase;

/**
 * Canonical TOUCH / near-touch witness for OverlayNGCurve CAP·CUP·SUB·XOR:
 * a U-shaped {@link CurvePolygon} whose arms almost touch
 * <em>past the densify / decide threshold</em>. Topology must win over
 * densify — linearized and curve-native must <b>not</b> invent a donut
 * (false interior ring) by sealing the mouth.
 * <p>
 * Probe on tip {@code b73b14e9}: linearized matches native (holes=0 both).
 * Two lobes with the same tip gap stay disjoint ({@code FF2FF1212}), CUP is
 * a two-member Multi*, never a single polygon with a hole.
 * <p>
 * DE-9IM Touch only — no {@code OverlayNGCurve.TOUCH} fifth op. Sibling of
 * two-disc T-ext {@code CurveExactRelateTouchTest} ({@code FF2F01212}).
 */
public class OverlayNGCurveUShapeTouchBasicsTest extends GeometryTestCase {

  /**
   * Tip–tip gap across the U mouth. Extent ~10 → decideTol ~0.01, so
   * {@code TIP_GAP < decideTol} (past decide threshold). Ops densify
   * ({@code CurveOps} 1e-6×extent) is finer; gap is not past opsTol.
   */
  private static final double TIP_GAP = 0.001;

  /**
   * U shell: arc lobes + parallel tip walls with gap {@link #TIP_GAP}.
   * Tips at x=4.9995 and x=5.0005.
   */
  private static final String U_SHAPE =
      "CURVEPOLYGON (COMPOUNDCURVE ("
      + "(0 0, 0 8),"
      + "CIRCULARSTRING (0 8, 2 10, 4.9995 8),"
      + "(4.9995 8, 4.9995 1),"
      + "(4.9995 1, 5.0005 1),"
      + "(5.0005 1, 5.0005 8),"
      + "CIRCULARSTRING (5.0005 8, 8 10, 10 8),"
      + "(10 8, 10 0),"
      + "(10 0, 0 0)"
      + "))";

  /** Left lobe of the same mouth (for two-body CAP·CUP·SUB·XOR). */
  private static final String U_LEFT =
      "CURVEPOLYGON (COMPOUNDCURVE ("
      + "(0 0, 0 8),"
      + "CIRCULARSTRING (0 8, 2 10, 4.9995 8),"
      + "(4.9995 8, 4.9995 0),"
      + "(4.9995 0, 0 0)"
      + "))";

  /** Right lobe — tip gap {@link #TIP_GAP} from {@link #U_LEFT}. */
  private static final String U_RIGHT =
      "CURVEPOLYGON (COMPOUNDCURVE ("
      + "(5.0005 0, 5.0005 8),"
      + "CIRCULARSTRING (5.0005 8, 8 10, 10 8),"
      + "(10 8, 10 0),"
      + "(10 0, 5.0005 0)"
      + "))";

  public static void main(String[] args) {
    TestRunner.run(OverlayNGCurveUShapeTouchBasicsTest.class);
  }

  public OverlayNGCurveUShapeTouchBasicsTest(String name) {
    super(name);
  }

  private static Geometry readCurve(String wkt) throws Exception {
    return new CurveWKTReader(new CurveGeometryFactory()).read(wkt);
  }

  private static int holeCount(Geometry g) {
    int n = 0;
    if (g instanceof Polygon) {
      n += ((Polygon) g).getNumInteriorRing();
    }
    for (int i = 0; i < g.getNumGeometries(); i++) {
      Geometry c = g.getGeometryN(i);
      if (c != g && c instanceof Polygon) {
        n += ((Polygon) c).getNumInteriorRing();
      }
    }
    return n;
  }

  public void testUShapeGapIsPastDecideThreshold() throws Exception {
    Geometry u = readCurve(U_SHAPE);
    double decide = OverlayNGCurve.decideTolerance(u);
    assertTrue("U must carry arc decide tolerance", decide > 0.0);
    assertTrue("canonical: tip gap past decide threshold ("
        + TIP_GAP + " < " + decide + ")",
        TIP_GAP < decide);
    Geometry left = readCurve(U_LEFT);
    Geometry right = readCurve(U_RIGHT);
    double gap = left.distance(right);
    assertEquals("lobe tip gap", TIP_GAP, gap, 1.0e-12);
    double margin = OverlayNGCurve.decideTolerance(left)
        + OverlayNGCurve.decideTolerance(right);
    assertTrue("two-lobe gap past decide margin", gap < margin);
  }

  public void testUShapeHasNoDonut() throws Exception {
    Geometry u = readCurve(U_SHAPE);
    assertTrue(u instanceof CurvePolygon);
    assertTrue(u.isValid());
    assertEquals("native U must not be a donut", 0, holeCount(u));
  }

  /**
   * Linearized path must match native on this witness: no densify-sealed
   * donut at decide or ops tolerance (probe parity on #7).
   */
  public void testLinearizedSameNoDonutAsNative() throws Exception {
    Geometry u = readCurve(U_SHAPE);
    assertTrue(u instanceof Linearizable);
    double decide = OverlayNGCurve.decideTolerance(u);
    Geometry linDecide = ((Linearizable) u).toLinear(decide);
    Geometry linOps = CurveOps.linearise(u);

    assertEquals("toLinear(decide) must not invent a donut",
        0, holeCount(linDecide));
    assertEquals("CurveOps.linearise must not invent a donut",
        0, holeCount(linOps));
    assertEquals("linearized hole count matches native",
        holeCount(u), holeCount(linDecide));
    assertEquals(holeCount(u), holeCount(linOps));
    assertTrue("decide densify stays valid", linDecide.isValid());
    assertTrue("ops densify stays valid", linOps.isValid());
  }

  public void testCapCupSubXorSelfNoDonut() throws Exception {
    Geometry u = readCurve(U_SHAPE);
    assertOpNoDonut("CAP", u, u, OverlayNGCurve.INTERSECTION);
    assertOpNoDonut("CUP", u, u, OverlayNGCurve.UNION);
    assertOpNoDonut("SUB", u, u, OverlayNGCurve.DIFFERENCE);
    assertOpNoDonut("XOR", u, u, OverlayNGCurve.SYMDIFFERENCE);
  }

  /**
   * Two arms almost touching past decide: CUP/XOR stay two members
   * (not one polygon with a hole). CAP empty. Same on linearized operands.
   */
  public void testTwoLobesAlmostTouchNoDonutCurveAndLinearized()
      throws Exception {
    Geometry left = readCurve(U_LEFT);
    Geometry right = readCurve(U_RIGHT);

    OverlayNGCurve cup = new OverlayNGCurve(left, right);
    Geometry cupR = cup.getResult(OverlayNGCurve.UNION);
    assertEquals("curve CUP: no donut", 0, holeCount(cupR));
    assertEquals("curve CUP: stay two bodies, not sealed",
        2, cupR.getNumGeometries());

    OverlayNGCurve cap = new OverlayNGCurve(left, right);
    Geometry capR = cap.getResult(OverlayNGCurve.INTERSECTION);
    assertTrue("curve CAP empty (no shared flesh)", capR.isEmpty());
    assertEquals(0, holeCount(capR));

    OverlayNGCurve xor = new OverlayNGCurve(left, right);
    Geometry xorR = xor.getResult(OverlayNGCurve.SYMDIFFERENCE);
    assertEquals("curve XOR: no donut", 0, holeCount(xorR));
    assertEquals(2, xorR.getNumGeometries());

    assertEquals("near-touch past decide is disjoint, not Touch lie",
        "FF2FF1212", left.relate(right).toString());
    assertFalse(left.touches(right));

    Geometry lLin = CurveOps.linearise(left);
    Geometry rLin = CurveOps.linearise(right);
    OverlayNGCurve cupLin = new OverlayNGCurve(lLin, rLin);
    Geometry cupLinR = cupLin.getResult(OverlayNGCurve.UNION);
    assertEquals("linearized CUP: no donut", 0, holeCount(cupLinR));
    assertEquals("linearized CUP: same two-body topology as curve",
        2, cupLinR.getNumGeometries());
    assertEquals("linearized relate matches curve",
        left.relate(right).toString(), lLin.relate(rLin).toString());
  }

  public void testNoOverlayNGCurveTouchFifthOp() {
    Field[] fields = OverlayNGCurve.class.getDeclaredFields();
    for (int i = 0; i < fields.length; i++) {
      assertFalse("DE-9IM Touch only — no OverlayNGCurve.TOUCH",
          "TOUCH".equals(fields[i].getName()));
    }
  }

  private static void assertOpNoDonut(String label, Geometry a, Geometry b,
      int op) {
    OverlayNGCurve ov = new OverlayNGCurve(a, b);
    Geometry r = ov.getResult(op);
    assertEquals(label + " must not invent a donut (approx="
        + ov.isApproximate() + " type=" + r.getGeometryType() + ")",
        0, holeCount(r));
  }
}

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

import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.curve.CircularString;
import org.locationtech.jts.geom.curve.ClothoidSegment;
import org.locationtech.jts.geom.curve.CompoundCurve;

import junit.framework.TestCase;
import junit.textui.TestRunner;

/**
 * Pins the TestBuilder CurveExample generators brought over from the
 * clothoid playground: ProRail 823_12V_4.3, isolated transitions, and
 * the G1-only arc chain used as the Insert-spiral target.
 */
public class CurveExampleFunctionsTest extends TestCase {

  public CurveExampleFunctionsTest(String name) { super(name); }
  public static void main(String[] args) { TestRunner.run(CurveExampleFunctionsTest.class); }

  public void testClothoidRailBendMembers() {
    Geometry g = CurveExampleFunctions.clothoidRailBend(null);
    assertTrue(g instanceof CompoundCurve);
    CompoundCurve cc = (CompoundCurve) g;
    assertEquals(5, cc.getNumMembers());
    assertFalse(cc.getMemberN(0) instanceof ClothoidSegment);
    assertTrue(cc.getMemberN(1) instanceof ClothoidSegment);
    assertTrue(cc.getMemberN(2) instanceof CircularString);
    assertTrue(cc.getMemberN(3) instanceof ClothoidSegment);
    assertFalse(cc.getMemberN(4) instanceof ClothoidSegment);
    ClothoidSegment entry = (ClothoidSegment) cc.getMemberN(1);
    assertEquals(0.0, entry.getStartKappa(), 0.0);
    assertEquals(0.005, entry.getEndKappa(), 1e-12);
    assertEquals(48.0, entry.getLength(), 0.0);
  }

  public void testClothoidSingleTransition() {
    Geometry g = CurveExampleFunctions.clothoidSingleTransition(null);
    CompoundCurve cc = (CompoundCurve) g;
    assertEquals(2, cc.getNumMembers());
    assertTrue(cc.getMemberN(1) instanceof ClothoidSegment);
  }

  public void testArcChainNoSpiralsIsG1Only() {
    Geometry g = CurveExampleFunctions.arcChainNoSpirals(null);
    CompoundCurve cc = (CompoundCurve) g;
    assertEquals(3, cc.getNumMembers());
    assertFalse(cc.getMemberN(0) instanceof ClothoidSegment);
    assertTrue(cc.getMemberN(1) instanceof CircularString);
    assertFalse(cc.getMemberN(2) instanceof ClothoidSegment);
  }
}

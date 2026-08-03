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
package org.locationtech.jts.io.curved;

import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.curved.CurvePolygon;

import junit.textui.TestRunner;
import test.jts.GeometryTestCase;

/**
 * FCP-WKT: {@link CurvedWKTWriter} must emit the ring tag inside a
 * {@code CURVEPOLYGON} body.
 * <p>
 * The writer overrides {@code appendOtherGeometryTaggedText} for
 * {@code CompoundCurve} only. A {@link CurvePolygon} falls through to the
 * inherited {@code WKTWriter}, which formats it as a plain polygon and drops
 * the ring tags -- so an arc shell comes back out as
 * {@code CURVEPOLYGON ((0 0, 4 0, ...))} and the arc is unrecoverable from
 * the text.
 * <p>
 * With FCP-S and FCP-H landed, the structural rings are available via
 * {@code getExteriorCurve()} / {@code getInteriorCurveN(n)}, so the writer
 * can walk them the same way it already walks CompoundCurve members.
 */
public class CurvedWKTWriterCurvePolygonTest extends GeometryTestCase {

  private static final String ARC_SHELL =
      "CURVEPOLYGON (CIRCULARSTRING (0 0, 4 0, 4 4, 0 4, 0 0))";

  public static void main(String[] args) {
    TestRunner.run(CurvedWKTWriterCurvePolygonTest.class);
  }

  public CurvedWKTWriterCurvePolygonTest(String name) { super(name); }

  private static String write(String wkt) throws Exception {
    return new CurvedWKTWriter().write(new CurvedWKTReader().read(wkt));
  }

  public void testArcShellEmitsCircularStringTag() throws Exception {
    String emitted = write(ARC_SHELL);
    assertTrue("emitted WKT should tag the arc shell, was: " + emitted,
        emitted.toUpperCase().contains("CIRCULARSTRING"));
  }

  public void testArcHoleEmitsCircularStringTag() throws Exception {
    String emitted = write("CURVEPOLYGON ("
        + "CIRCULARSTRING (0 0, 8 0, 8 8, 0 8, 0 0), "
        + "CIRCULARSTRING (2 2, 4 2, 4 4, 2 4, 2 2))");
    int first = emitted.toUpperCase().indexOf("CIRCULARSTRING");
    assertTrue("shell tag missing: " + emitted, first >= 0);
    assertTrue("hole tag missing: " + emitted,
        emitted.toUpperCase().indexOf("CIRCULARSTRING", first + 1) > first);
  }

  public void testCompoundCurveShellEmitsTag() throws Exception {
    String emitted = write(
        "CURVEPOLYGON (COMPOUNDCURVE (CIRCULARSTRING (0 0, 1 1, 2 0), (2 0, 0 0)))");
    assertTrue("emitted WKT should tag the compound shell, was: " + emitted,
        emitted.toUpperCase().contains("COMPOUNDCURVE"));
  }

  /** The arc must survive a full text round trip, not just appear once. */
  public void testRoundTripPreservesArcShell() throws Exception {
    String emitted = write(ARC_SHELL);
    CurvePolygon back = (CurvePolygon) new CurvedWKTReader().read(emitted);
    assertEquals("arc shell should survive the round trip",
        "CircularString", back.getExteriorCurve().getGeometryType());
  }

  /** A plain polygon gains no spurious arc tag. */
  public void testLinearShellEmitsNoArcTag() throws Exception {
    String emitted = write("CURVEPOLYGON ((0 0, 1 0, 1 1, 0 1, 0 0))");
    assertFalse("plain ring must not be tagged as an arc, was: " + emitted,
        emitted.toUpperCase().contains("CIRCULARSTRING"));
    assertTrue("should still be a CURVEPOLYGON, was: " + emitted,
        emitted.toUpperCase().contains("CURVEPOLYGON"));
  }

  /** Empty CurvePolygon keeps its existing representation. */
  public void testEmptyUnchanged() throws Exception {
    Geometry g = new CurvedWKTReader().read("CURVEPOLYGON EMPTY");
    String emitted = new CurvedWKTWriter().write(g);
    assertTrue("empty should stay EMPTY, was: " + emitted,
        emitted.toUpperCase().contains("EMPTY"));
  }
}

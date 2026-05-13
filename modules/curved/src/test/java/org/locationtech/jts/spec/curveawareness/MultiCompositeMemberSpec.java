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
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.geom.curved.CircularString;
import org.locationtech.jts.geom.curved.CompoundCurve;
import org.locationtech.jts.geom.curved.CurvePolygon;
import org.locationtech.jts.geom.curved.MultiCurve;
import org.locationtech.jts.geom.curved.MultiSurface;
import org.locationtech.jts.io.curved.CurvedWKTReader;
import org.locationtech.jts.io.curved.CurvedWKTWriter;

import junit.framework.Test;
import junit.framework.TestSuite;
import junit.textui.TestRunner;
import test.jts.GeometryTestCase;

/**
 * Focused red-test suite for sub-issues <strong>F-MC</strong> (structural
 * MultiCurve) and <strong>F-MS</strong> (structural MultiSurface) of the
 * SFA Curve Awareness epic (locationtech/jts#1195).
 *
 * <p>Unlike F-CP, these two have <b>no DOVE-style risk</b>:
 * {@code MultiLineString.getGeometryN(int)} already returns
 * {@code Geometry}, and {@code MultiPolygon.getGeometryN(int)} returns
 * {@code Geometry} too — so a member typed as a {@code CircularString}
 * or {@code CurvePolygon} satisfies the existing contract without
 * widening any return type.
 *
 * <p>The questions for F-MC / F-MS are simpler:
 * <ol>
 *   <li><b>FMC-READ</b>: does the WKT reader preserve heterogeneous
 *       members (CircularString / CompoundCurve / plain LineString)
 *       inside MULTICURVE?</li>
 *   <li><b>FMC-COPY</b>: does {@code MultiCurve.copy()} preserve each
 *       member's subtype, not just the parent {@code LineString}?</li>
 *   <li><b>FMC-WKT</b>: does the writer emit member type tags so
 *       round-trip is lossless?</li>
 *   <li><b>FMS-READ</b>: same question for MULTISURFACE
 *       (Polygon / CurvePolygon members).</li>
 *   <li><b>FMS-COPY</b>: same copy preservation question for
 *       MultiSurface.</li>
 *   <li><b>FMS-WKT</b>: writer round-trip for MULTISURFACE.</li>
 * </ol>
 *
 * <p>Each {@code fail("FMC-…: …")} / {@code "FMS-…: …"} message names
 * the sub-issue tag so the message stream is a live progress meter.
 * Tests already known to pass via the {@code readCurveMember} /
 * {@code readSurfaceMember} dispatch in PR #1194 will go green
 * immediately; the rest mark the remaining gap.
 *
 * <p>Run on demand: {@code mvn -pl modules/curved test
 * -Dtest=MultiCompositeMemberSpec}.
 */
public class MultiCompositeMemberSpec extends GeometryTestCase {

  public static void main(String[] args) { TestRunner.run(suite()); }
  public static Test suite() { return new TestSuite(MultiCompositeMemberSpec.class); }
  public MultiCompositeMemberSpec(String name) { super(name); }

  // -- F-MC --------------------------------------------------------------

  private static final String WKT_MC_HETEROGENEOUS =
      "MULTICURVE("
      + "(5 5, 3 5, 3 3, 0 3), "
      + "CIRCULARSTRING(0 0, 1 1, 2 0), "
      + "COMPOUNDCURVE(CIRCULARSTRING(0 0, 1 1, 2 0), (2 0, 5 0))"
      + ")";

  /** FMC-READ: MultiCurve member subtypes survive the reader. */
  public void test_FMC_READ_membersRetainSubtypes() throws Exception {
    Geometry g = new CurvedWKTReader().read(WKT_MC_HETEROGENEOUS);
    assertTrue("Reader returns MultiCurve", g instanceof MultiCurve);
    MultiCurve mc = (MultiCurve) g;
    assertEquals("FMC-READ: three members", 3, mc.getNumGeometries());

    Geometry m0 = mc.getGeometryN(0);
    Geometry m1 = mc.getGeometryN(1);
    Geometry m2 = mc.getGeometryN(2);
    assertEquals("FMC-READ: member 0 is plain LineString, got " + m0.getGeometryType(),
        "LineString", m0.getGeometryType());
    assertTrue("FMC-READ: member 1 is CircularString, got " + m1.getClass().getSimpleName(),
        m1 instanceof CircularString);
    assertTrue("FMC-READ: member 2 is CompoundCurve, got " + m2.getClass().getSimpleName(),
        m2 instanceof CompoundCurve);
  }

  /** FMC-COPY: MultiCurve.copy() preserves each member's subtype. */
  public void test_FMC_COPY_copyPreservesMemberSubtypes() throws Exception {
    MultiCurve mc = (MultiCurve) new CurvedWKTReader().read(WKT_MC_HETEROGENEOUS);
    MultiCurve copy = (MultiCurve) mc.copy();
    for (int i = 0; i < mc.getNumGeometries(); i++) {
      assertEquals("FMC-COPY: member " + i + " class survives copy",
          mc.getGeometryN(i).getClass(),
          copy.getGeometryN(i).getClass());
    }
    assertNotSame("FMC-COPY: deep copy of first member",
        mc.getGeometryN(0), copy.getGeometryN(0));
  }

  /** FMC-WKT: writer emits member type tags so re-reading recovers the same shape. */
  public void test_FMC_WKT_writerEmitsMemberTagsForRoundTrip() throws Exception {
    MultiCurve mc = (MultiCurve) new CurvedWKTReader().read(WKT_MC_HETEROGENEOUS);
    String emitted = new CurvedWKTWriter().write(mc);
    assertTrue("FMC-WKT: emitted WKT mentions CIRCULARSTRING tag inside body, got: "
        + emitted, emitted.toUpperCase().contains("CIRCULARSTRING"));
    assertTrue("FMC-WKT: emitted WKT mentions COMPOUNDCURVE tag inside body, got: "
        + emitted, emitted.toUpperCase().contains("COMPOUNDCURVE"));

    MultiCurve roundTripped = (MultiCurve) new CurvedWKTReader().read(emitted);
    assertEquals("FMC-WKT: same member count", mc.getNumGeometries(),
        roundTripped.getNumGeometries());
    for (int i = 0; i < mc.getNumGeometries(); i++) {
      assertEquals("FMC-WKT: member " + i + " class survives round-trip",
          mc.getGeometryN(i).getClass(),
          roundTripped.getGeometryN(i).getClass());
    }
  }

  // -- F-MS --------------------------------------------------------------

  private static final String WKT_MS_HETEROGENEOUS =
      "MULTISURFACE("
      + "((0 0, 10 0, 10 10, 0 10, 0 0)), "
      + "CURVEPOLYGON(CIRCULARSTRING(0 0, 4 0, 4 4, 0 4, 0 0))"
      + ")";

  /** FMS-READ: MultiSurface preserves Polygon vs CurvePolygon members. */
  public void test_FMS_READ_membersRetainSubtypes() throws Exception {
    Geometry g = new CurvedWKTReader().read(WKT_MS_HETEROGENEOUS);
    assertTrue("Reader returns MultiSurface", g instanceof MultiSurface);
    MultiSurface ms = (MultiSurface) g;
    assertEquals("FMS-READ: two members", 2, ms.getNumGeometries());

    Geometry m0 = ms.getGeometryN(0);
    Geometry m1 = ms.getGeometryN(1);
    assertEquals("FMS-READ: member 0 is plain Polygon, got " + m0.getGeometryType(),
        "Polygon", m0.getGeometryType());
    assertTrue("FMS-READ: member 1 is CurvePolygon, got " + m1.getClass().getSimpleName(),
        m1 instanceof CurvePolygon);
  }

  /** FMS-COPY: MultiSurface.copy() preserves Polygon vs CurvePolygon. */
  public void test_FMS_COPY_copyPreservesMemberSubtypes() throws Exception {
    MultiSurface ms = (MultiSurface) new CurvedWKTReader().read(WKT_MS_HETEROGENEOUS);
    MultiSurface copy = (MultiSurface) ms.copy();
    for (int i = 0; i < ms.getNumGeometries(); i++) {
      assertEquals("FMS-COPY: member " + i + " class survives copy",
          ms.getGeometryN(i).getClass(),
          copy.getGeometryN(i).getClass());
    }
  }

  /** FMS-WKT: round-trip preserves the CURVEPOLYGON member tag. */
  public void test_FMS_WKT_writerEmitsCurvePolygonTagForRoundTrip() throws Exception {
    MultiSurface ms = (MultiSurface) new CurvedWKTReader().read(WKT_MS_HETEROGENEOUS);
    String emitted = new CurvedWKTWriter().write(ms);
    assertTrue("FMS-WKT: emitted WKT mentions CURVEPOLYGON tag inside body, got: "
        + emitted, emitted.toUpperCase().contains("CURVEPOLYGON"));

    MultiSurface roundTripped = (MultiSurface) new CurvedWKTReader().read(emitted);
    assertEquals("FMS-WKT: same member count", ms.getNumGeometries(),
        roundTripped.getNumGeometries());
    for (int i = 0; i < ms.getNumGeometries(); i++) {
      assertEquals("FMS-WKT: member " + i + " class survives round-trip",
          ms.getGeometryN(i).getClass(),
          roundTripped.getGeometryN(i).getClass());
    }
  }
}

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
package org.locationtech.jtstest.testbuilder.geom;

import java.util.ArrayList;
import java.util.List;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.curve.CircularString;
import org.locationtech.jts.geom.curve.ClothoidSegment;
import org.locationtech.jts.geom.curve.CompoundCurve;
import org.locationtech.jts.geom.curve.CurveGeometryFactory;
import org.locationtech.jts.geom.curve.CurveLinearizationStrategy;
import org.locationtech.jts.io.curve.CurveWKTReader;
import org.locationtech.jts.io.curve.CurveWKTWriter;
import org.locationtech.jtstest.function.CurveExampleFunctions;

import junit.framework.TestCase;
import junit.textui.TestRunner;

/**
 * UX issue #95: Extract Elements/Segments of {@code clothoidRailBend}
 * must keep {@code CLOTHOID} as a non-leading CompoundCurve member,
 * not emit the two-point start–end chord as {@code LINESTRING}.
 * Same paths as {@code ExtractComponentTool}:
 * {@link GeometryElementLocater} (drag) and {@link SegmentExtracter}
 * (Ctrl-drag).
 */
public class ExtractClothoidHonestyTest extends TestCase {

  public ExtractClothoidHonestyTest(String name) {
    super(name);
  }

  public static void main(String[] args) {
    TestRunner.run(ExtractClothoidHonestyTest.class);
  }

  public void testExtractElementsWholeBendKeepsClothoid() {
    List reverseWarns = new ArrayList();
    Geometry g = CurveExampleFunctions.clothoidRailBend(null);
    Geometry extracted = extractWithWarns(g,
        box(115000, 411000, 117000, 413000), reverseWarns, false);
    assertCompoundWithClothoid(extracted, "elements whole");
    assertEquals(5, ((CompoundCurve) extracted).getNumMembers());
    assertTrue("full extract must keep original orientation, not reverse",
        extracted.getCoordinates()[0].equals2D(g.getCoordinates()[0]));
    assertFalse("full extract must not log a reverse, got " + reverseWarns,
        containsReverseLog(reverseWarns));
  }

  public void testExtractElementsExitRunIsCompoundCurveNotChord() {
    Geometry g = CurveExampleFunctions.clothoidRailBend(null);
    CompoundCurve cc = (CompoundCurve) g;
    Envelope env = new Envelope();
    env.expandToInclude(cc.getMemberN(2).getEnvelopeInternal());
    env.expandToInclude(cc.getMemberN(3).getEnvelopeInternal());
    env.expandToInclude(cc.getMemberN(4).getEnvelopeInternal());
    Geometry extracted = GeometryElementLocater.extractElements(g,
        cc.getFactory().toGeometry(env));
    assertCompoundWithClothoid(extracted, "elements exit run");
    CompoundCurve out = (CompoundCurve) extracted;
    assertFalse(out.getMemberN(0) instanceof ClothoidSegment);
    assertTrue(hasCircularString(out));
  }

  /**
   * A box on the exit clothoid only would make CLOTHOID leading.
   * Reverse that run onto the following straight instead of pulling in
   * the previous circular arc.
   */
  public void testExtractElementsClothoidOnlyReversesOntoSuccessor() {
    List reverseWarns = new ArrayList();
    Geometry g = CurveExampleFunctions.clothoidRailBend(null);
    CompoundCurve cc = (CompoundCurve) g;
    Geometry aoi = boxAroundClothoidMid((ClothoidSegment) cc.getMemberN(3));
    Geometry extracted = extractWithWarns(g, aoi, reverseWarns, false);
    assertCompoundWithClothoid(extracted, "elements clothoid-only reverse");
    CompoundCurve out = (CompoundCurve) extracted;
    assertFalse(out.getMemberN(0) instanceof ClothoidSegment);
    assertEquals("LineString", out.getMemberN(0).getGeometryType());
    assertFalse("must not pull the previous CIRCULARSTRING; reverse onto the exit line",
        hasCircularString(out));
    assertTrue("reverse must be logged, got " + reverseWarns,
        containsReverseLog(reverseWarns));
  }

  public void testExtractSegmentsClothoidOnlyReversesOntoSuccessor() {
    List reverseWarns = new ArrayList();
    Geometry g = CurveExampleFunctions.clothoidRailBend(null);
    CompoundCurve cc = (CompoundCurve) g;
    Geometry aoi = boxAroundClothoidMid((ClothoidSegment) cc.getMemberN(3));
    Geometry extracted = extractWithWarns(g, aoi, reverseWarns, true);
    assertCompoundWithClothoid(extracted, "segments clothoid-only reverse");
    CompoundCurve out = (CompoundCurve) extracted;
    assertFalse(out.getMemberN(0) instanceof ClothoidSegment);
    assertFalse(hasCircularString(out));
    assertTrue("reverse must be logged, got " + reverseWarns,
        containsReverseLog(reverseWarns));
  }

  public void testExtractSegmentsWholeBendKeepsClothoid() {
    Geometry g = CurveExampleFunctions.clothoidRailBend(null);
    Geometry extracted = SegmentExtracter.extract(g,
        box(115000, 411000, 117000, 413000));
    assertCompoundWithClothoid(extracted, "segments whole");
    assertEquals(5, ((CompoundCurve) extracted).getNumMembers());
    assertTrue("full extract must keep original orientation, not reverse",
        extracted.getCoordinates()[0].equals2D(g.getCoordinates()[0]));
  }

  public void testExtractSegmentsExitRunKeepsClothoid() {
    Geometry g = CurveExampleFunctions.clothoidRailBend(null);
    CompoundCurve cc = (CompoundCurve) g;
    Envelope env = new Envelope();
    env.expandToInclude(cc.getMemberN(2).getEnvelopeInternal());
    env.expandToInclude(cc.getMemberN(3).getEnvelopeInternal());
    env.expandToInclude(cc.getMemberN(4).getEnvelopeInternal());
    Geometry extracted = SegmentExtracter.extract(g,
        cc.getFactory().toGeometry(env));
    assertCompoundWithClothoid(extracted, "segments exit run");
  }

  private static void assertCompoundWithClothoid(Geometry g, String path) {
    assertNotNull(path + " extracted nothing", g);
    String wkt = write(g);
    assertFalse(path + " flattened to LINESTRING, got " + wkt,
        g.getClass().equals(LineString.class));
    assertFalse(path + " boxed clothoid as MULTICURVE chord, got " + wkt,
        wkt.startsWith("MULTICURVE"));
    assertFalse(wkt.startsWith("LINESTRING"));
    assertTrue(path + " must stay COMPOUNDCURVE, got "
        + g.getClass().getName() + " " + wkt,
        g instanceof CompoundCurve);
    assertTrue(path + " WKT must contain CLOTHOID, got " + wkt,
        wkt.indexOf("CLOTHOID") >= 0);
    assertTrue(path + " must keep a ClothoidSegment, got " + wkt,
        hasClothoid((CompoundCurve) g));
    Geometry roundTrip = read(wkt);
    assertTrue(path + " round-trip lost CLOTHOID, got " + write(roundTrip),
        roundTrip instanceof CompoundCurve
            && hasClothoid((CompoundCurve) roundTrip));
  }

  private static boolean hasClothoid(CompoundCurve cc) {
    for (int i = 0; i < cc.getNumMembers(); i++) {
      if (cc.getMemberN(i) instanceof ClothoidSegment) {
        return true;
      }
    }
    return false;
  }

  private static boolean hasCircularString(CompoundCurve cc) {
    for (int i = 0; i < cc.getNumMembers(); i++) {
      if (cc.getMemberN(i) instanceof CircularString) {
        return true;
      }
    }
    return false;
  }

  private static Geometry read(String wkt) {
    try {
      return new CurveWKTReader(new CurveGeometryFactory()).read(wkt);
    }
    catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private static String write(Geometry g) {
    return new CurveWKTWriter().write(g);
  }

  private static Geometry box(double minx, double miny, double maxx, double maxy) {
    return new CurveGeometryFactory().toGeometry(new Envelope(minx, maxx, miny, maxy));
  }

  /** Tight box on a mid-spiral sample so the AOI does not also hit the arc. */
  private static Geometry boxAroundClothoidMid(ClothoidSegment cl) {
    Geometry lin = cl.toLinear(0.5);
    Coordinate[] pts = lin.getCoordinates();
    Coordinate mid = pts[pts.length / 2];
    Envelope env = new Envelope(mid);
    env.expandBy(1.0);
    return cl.getFactory().toGeometry(env);
  }

  private static Geometry extractWithWarns(Geometry g, Geometry aoi, final List warns,
      boolean segments) {
    CurveLinearizationStrategy.WarnSink prev =
        null;
    CurveLinearizationStrategy.setWarnSink(
        new CurveLinearizationStrategy.WarnSink() {
          public void warn(String message) {
            warns.add(message);
          }
        });
    try {
      if (segments) {
        return SegmentExtracter.extract(g, aoi);
      }
      return GeometryElementLocater.extractElements(g, aoi);
    }
    finally {
      CurveLinearizationStrategy.setWarnSink(prev);
    }
  }

  private static boolean containsReverseLog(List warns) {
    for (int i = 0; i < warns.size(); i++) {
      String m = String.valueOf(warns.get(i));
      if (m.indexOf("reversed COMPOUNDCURVE") >= 0) {
        return true;
      }
    }
    return false;
  }
}

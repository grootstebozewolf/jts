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

import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.curve.CircularString;
import org.locationtech.jts.geom.curve.ClothoidSegment;
import org.locationtech.jts.geom.curve.CompoundCurve;
import org.locationtech.jts.geom.curve.CurveGeometryFactory;
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
    Geometry g = CurveExampleFunctions.clothoidRailBend(null);
    Geometry extracted = GeometryElementLocater.extractElements(g,
        box(115000, 411000, 117000, 413000));
    assertCompoundWithClothoid(extracted, "elements whole");
    assertEquals(5, ((CompoundCurve) extracted).getNumMembers());
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

  public void testExtractElementsClothoidOnlyPrependsPredecessor() {
    Geometry g = CurveExampleFunctions.clothoidRailBend(null);
    CompoundCurve cc = (CompoundCurve) g;
    Geometry extracted = GeometryElementLocater.extractElements(g,
        cc.getFactory().toGeometry(cc.getMemberN(3).getEnvelopeInternal()));
    assertCompoundWithClothoid(extracted, "elements clothoid-only");
    assertFalse(((CompoundCurve) extracted).getMemberN(0) instanceof ClothoidSegment);
  }

  public void testExtractSegmentsWholeBendKeepsClothoid() {
    Geometry g = CurveExampleFunctions.clothoidRailBend(null);
    Geometry extracted = SegmentExtracter.extract(g,
        box(115000, 411000, 117000, 413000));
    assertCompoundWithClothoid(extracted, "segments whole");
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
}

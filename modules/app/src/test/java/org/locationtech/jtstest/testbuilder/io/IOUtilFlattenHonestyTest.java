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
package org.locationtech.jtstest.testbuilder.io;

import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.curve.CircularString;
import org.locationtech.jts.geom.curve.CompoundCurve;
import org.locationtech.jts.geom.curve.CurveGeometryFactory;
import org.locationtech.jts.io.curve.CurveWKTReader;

import junit.framework.TestCase;
import junit.framework.TestSuite;
import junit.textui.TestRunner;

/**
 * TestBuilder clipboard / WKT paths must keep COMPOUNDCURVE members
 * (SQL/MM ISO/IEC 13249-3). GML2 cannot carry arcs and must refuse.
 */
public class IOUtilFlattenHonestyTest extends TestCase {

  private static final String COMPOUNDCURVE =
      "COMPOUNDCURVE (CIRCULARSTRING (0 0, 5 5, 10 0), (10 0, 20 0))";

  public static void main(String[] args) { TestRunner.run(suite()); }
  public static junit.framework.Test suite() { return new TestSuite(IOUtilFlattenHonestyTest.class); }
  public IOUtilFlattenHonestyTest(String name) { super(name); }

  private static Geometry readCurve(String wkt) throws Exception {
    return new CurveWKTReader(new CurveGeometryFactory()).read(wkt);
  }

  public void testToWktKeepsCompoundCurveMembers() throws Exception {
    Geometry cc = readCurve(COMPOUNDCURVE);
    String wkt = IOUtil.toWKT(cc, true);
    assertTrue("formatted WKT must keep CIRCULARSTRING member, was: " + wkt,
        wkt.toUpperCase().contains("CIRCULARSTRING"));
    CompoundCurve back = (CompoundCurve) new CurveWKTReader(
        new CurveGeometryFactory()).read(wkt);
    assertEquals(2, back.getNumMembers());
    assertTrue(back.getMemberN(0) instanceof CircularString);
  }

  public void testToWktUnformattedUsesToText() throws Exception {
    Geometry cc = readCurve(COMPOUNDCURVE);
    String wkt = IOUtil.toWKT(cc, false);
    assertTrue("unformatted toString/toText must keep CIRCULARSTRING, was: " + wkt,
        wkt.toUpperCase().contains("CIRCULARSTRING"));
  }

  public void testToGmlRefusesCompoundCurve() throws Exception {
    Geometry cc = readCurve(COMPOUNDCURVE);
    try {
      IOUtil.toGML(cc);
      fail("IOUtil.toGML must not emit a control-polygon LineString");
    }
    catch (IllegalArgumentException e) {
      assertTrue(e.getMessage().indexOf("ISO/IEC 13249-3") >= 0);
    }
  }
}

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
package org.locationtech.jts.io;

import java.io.IOException;
import java.util.EnumSet;

import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.Point;

import junit.framework.Test;
import junit.framework.TestSuite;
import junit.textui.TestRunner;
import test.jts.GeometryTestCase;

/**
 * Verifies the {@link WKBReader#readOtherGeometry} and
 * {@link WKBWriter#writeOtherGeometry} extension hooks without taking
 * a dependency on jts-curve.
 */
public class WKBReaderExtensionHookTest extends GeometryTestCase {

  public static void main(String args[]) {
    TestRunner.run(suite());
  }

  public static Test suite() { return new TestSuite(WKBReaderExtensionHookTest.class); }

  public WKBReaderExtensionHookTest(String name) { super(name); }

  private static class DummyWriter extends WKBWriter {
    boolean hookCalled = false;

    @Override
    protected boolean writeOtherGeometry(Geometry geom,
        EnumSet<Ordinate> outputOrdinates, OutStream os) throws IOException {
      if (geom instanceof Point && !geom.isEmpty()) {
        hookCalled = true;
        writeByteOrder(os);
        writeGeometryType(WKBConstants.wkbPoint, outputOrdinates, geom, os);
        writeCoordinateSequence(((Point) geom).getCoordinateSequence(),
            outputOrdinates, false, os);
        return true;
      }
      return false;
    }
  }

  private static class DummyReader extends WKBReader {
    boolean hookCalled = false;

    @Override
    protected Geometry readOtherGeometry(int geometryType,
        EnumSet<Ordinate> ordinateFlags, int SRID)
        throws IOException, ParseException {
      hookCalled = true;
      return super.readOtherGeometry(geometryType, ordinateFlags, SRID);
    }
  }

  public void testWriterHookFiresBeforeInstanceofLadder() throws Exception {
    DummyWriter writer = new DummyWriter();
    Geometry pt = read("POINT (1 2)");
    byte[] wkb = writer.write(pt);
    assertTrue("writeOtherGeometry should have been called", writer.hookCalled);
    Geometry back = new WKBReader().read(wkb);
    assertTrue(pt.equalsExact(back));
  }

  public void testWriterDefaultHookReturnsFalse() throws Exception {
    Geometry pt = read("POINT (1 2)");
    byte[] core = new WKBWriter().write(pt);
    byte[] dummy = new DummyWriter().write(pt);
    assertEquals(WKBWriter.toHex(core), WKBWriter.toHex(dummy));
  }

  public void testReaderHookIsInvokedForUnknownType() {
    DummyReader reader = new DummyReader();
    try {
      // type 99, little-endian empty-ish header
      reader.read(WKBReader.hexToBytes("0163000000"));
      fail("Expected ParseException for unknown type");
    } catch (Throwable e) {
      assertTrue("Expected ParseException, got: " + e, e instanceof ParseException);
      assertTrue(reader.hookCalled);
    }
  }

  public void testCoreReaderStillThrowsForUnknownType() {
    try {
      new WKBReader().read(WKBReader.hexToBytes("0108000000"));
      fail("Expected ParseException from default WKBReader for type 8");
    } catch (Throwable e) {
      assertTrue("Expected ParseException, got: " + e, e instanceof ParseException);
    }
  }
}

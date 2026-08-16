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
package org.locationtech.jts.io.gml2;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;

import junit.framework.TestCase;
import junit.textui.TestRunner;

/**
 * GML2 cannot carry SQL/MM ISO/IEC 13249-3 arcs.
 */
public class GMLWriterFlattenTest extends TestCase {

  public static void main(String[] args) {
    TestRunner.run(GMLWriterFlattenTest.class);
  }

  public GMLWriterFlattenTest(String name) { super(name); }

  public void testUnexpectedLineStringSubclassRefused() {
    GeometryFactory gf = new GeometryFactory();
    LineString fake = new LineString(
        gf.getCoordinateSequenceFactory().create(new Coordinate[] {
            new Coordinate(0, 0), new Coordinate(1, 1)
        }), gf) {
      private static final long serialVersionUID = 1L;
      public String getGeometryType() { return "FakeCurve"; }
    };
    try {
      new GMLWriter().write(fake);
      fail("GMLWriter must not flatten an unexpected LineString subclass");
    }
    catch (IllegalArgumentException e) {
      assertTrue(e.getMessage().indexOf("ISO/IEC 13249-3") >= 0);
    }
  }
}

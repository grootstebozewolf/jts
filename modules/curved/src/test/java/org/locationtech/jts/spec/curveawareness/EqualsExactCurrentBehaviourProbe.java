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
import org.locationtech.jts.io.curved.CurvedWKTReader;

import junit.framework.Test;
import junit.framework.TestSuite;
import junit.textui.TestRunner;
import test.jts.GeometryTestCase;

/**
 * Probe printing today's truth table for {@code equalsExact} across
 * curve-vs-line and curve-vs-curve pairs. Used as the R-EQ spike's
 * empirical baseline; the printed table is the evidence.
 *
 * <p>Run on demand:
 * {@code mvn -pl modules/curved test -Dtest=EqualsExactCurrentBehaviourProbe}.
 */
public class EqualsExactCurrentBehaviourProbe extends GeometryTestCase {

  public static void main(String[] args) { TestRunner.run(suite()); }
  public static Test suite() { return new TestSuite(EqualsExactCurrentBehaviourProbe.class); }
  public EqualsExactCurrentBehaviourProbe(String name) { super(name); }

  public void testProbe() throws Exception {
    Geometry ls   = readCurved("LINESTRING (0 0, 5 5, 10 0)");
    Geometry cs   = readCurved("CIRCULARSTRING (0 0, 5 5, 10 0)");
    Geometry cc   = readCurved("COMPOUNDCURVE ((0 0, 5 5), (5 5, 10 0))");
    Geometry lr   = readCurved("LINEARRING (0 0, 5 5, 10 0, 0 0)");
    Geometry lrLs = readCurved("LINESTRING (0 0, 5 5, 10 0, 0 0)"); // same coords as the ring
    Geometry poly = readCurved("POLYGON ((0 0, 10 0, 0 10, 0 0))");

    StringBuilder out = new StringBuilder(
        "\n=== R-EQ probe: equalsExact across coordinate-sharing geometries ===\n");
    out.append(String.format("%-40s  %s%n", "comparison", "today"));
    out.append(String.format("%-40s  %s%n", "----------------------------------------", "-----"));
    out.append(row("cs.equalsExact(ls)",  cs, ls));
    out.append(row("ls.equalsExact(cs)",  ls, cs));
    out.append(row("cc.equalsExact(ls)",  cc, ls));
    out.append(row("ls.equalsExact(cc)",  ls, cc));
    out.append(row("cc.equalsExact(cs)",  cc, cs));
    out.append(row("cs.equalsExact(cc)",  cs, cc));
    out.append(row("cs.equalsExact(cs)",  cs, cs));
    out.append(row("lr.equalsExact(ls')", lr, lrLs));
    out.append(row("ls'.equalsExact(lr)", lrLs, lr));
    out.append(row("poly.equalsExact(ls)", poly, ls));
    System.out.println(out);
    assertNotNull("probe ran", out.toString());
  }

  private static Geometry readCurved(String wkt) throws Exception {
    return new CurvedWKTReader().read(wkt);
  }

  private static String row(String label, Geometry a, Geometry b) {
    return String.format("%-40s  %s%n", label, String.valueOf(a.equalsExact(b)));
  }
}

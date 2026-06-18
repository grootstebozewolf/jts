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
package org.locationtech.jts.geom.curved;

import java.util.ArrayList;
import java.util.List;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.impl.CoordinateArraySequence;

import junit.framework.TestCase;
import junit.textui.TestRunner;

import org.locationtech.jts.geom.curved.ArcRingRelation.Relation;

/**
 * V-CP building block (#1195): {@link ArcRingRelation#relate} classifies two
 * closed curved rings as DISJOINT / CROSS / A_IN_B / B_IN_A — the holes-disjoint
 * check for CurvePolygon validity. Pinned against the exact HOLES_DISJOINT oracle
 * (NetTopologySuite.Proofs Rocq/Coq), plus geometric anchors.
 */
public class ArcRingRelationTest extends TestCase {

  public static void main(String args[]) {
    TestRunner.run(ArcRingRelationTest.class);
  }

  public ArcRingRelationTest(String name) { super(name); }

  private static CoordinateArraySequence circle(double cx, double cy, double r) {
    double q = r / Math.sqrt(2);
    double[][] p = {{cx+r,cy},{cx+q,cy+q},{cx,cy+r},{cx-q,cy+q},{cx-r,cy},{cx-q,cy-q},{cx,cy-r},{cx+q,cy-q},{cx+r,cy}};
    Coordinate[] c = new Coordinate[p.length];
    for (int i = 0; i < p.length; i++) c[i] = new Coordinate(p[i][0], p[i][1]);
    return new CoordinateArraySequence(c);
  }

  public void testDisjoint() {
    assertEquals(Relation.DISJOINT, ArcRingRelation.relate(circle(0.1,0.1,5), circle(20.1,0.1,5)));
  }

  public void testCross() {
    assertEquals(Relation.CROSS, ArcRingRelation.relate(circle(0.1,0.1,5), circle(6.1,0.1,5)));
  }

  public void testBInsideA() {
    assertEquals(Relation.B_IN_A, ArcRingRelation.relate(circle(0.1,0.1,5), circle(1.1,1.1,1)));
  }

  public void testAInsideB() {
    assertEquals(Relation.A_IN_B, ArcRingRelation.relate(circle(1.1,1.1,1), circle(0.1,0.1,5)));
  }

  /** Pins the relation against the exact HOLES_DISJOINT oracle vectors. */
  public void testMatchesOracleVectors() throws Exception {
    java.io.InputStream in = getClass().getResourceAsStream(
        "/org/locationtech/jts/geom/curved/rocqref/curve_holes_disjoint_vectors.txt");
    assertNotNull("holes-disjoint vectors resource", in);
    java.io.BufferedReader rd = new java.io.BufferedReader(
        new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8));
    String line; int checked = 0;
    while ((line = rd.readLine()) != null) {
      String s = line.trim();
      if (s.isEmpty() || s.startsWith("#")) continue;
      String[] t = s.split("\\s+");
      int idx = 0;
      int na = Integer.parseInt(t[idx++]);
      CoordinateArraySequence a = readRing(t, idx, na); idx += 2 * na;
      int nb = Integer.parseInt(t[idx++]);
      CoordinateArraySequence b = readRing(t, idx, nb); idx += 2 * nb;
      Relation expected = Relation.valueOf(t[idx]);
      assertEquals("relation for " + s, expected, ArcRingRelation.relate(a, b));
      checked++;
    }
    rd.close();
    assertTrue("should have checked oracle vectors", checked >= 10);
  }

  private static CoordinateArraySequence readRing(String[] t, int off, int n) {
    List<Coordinate> pts = new ArrayList<Coordinate>();
    for (int i = 0; i < n; i++)
      pts.add(new Coordinate(Double.parseDouble(t[off + 2*i]), Double.parseDouble(t[off + 2*i + 1])));
    return new CoordinateArraySequence(pts.toArray(new Coordinate[0]));
  }
}

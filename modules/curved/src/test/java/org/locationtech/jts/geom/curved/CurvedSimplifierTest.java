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

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.impl.CoordinateArraySequence;
import org.locationtech.jts.simplify.DouglasPeuckerSimplifier;

import junit.framework.TestCase;
import junit.textui.TestRunner;

/**
 * S-DP (#1195) — curve-aware Douglas–Peucker: a curved geometry is returned
 * unchanged (arc identity preserved, not collapsed to its chord), while a plain
 * geometry matches the core {@link DouglasPeuckerSimplifier} exactly.
 */
public class CurvedSimplifierTest extends TestCase {

  public static void main(String[] args) { TestRunner.run(CurvedSimplifierTest.class); }
  public CurvedSimplifierTest(String name) { super(name); }

  private static final CurvedGeometryFactory GF = new CurvedGeometryFactory();
  private static final GeometryFactory PF = new GeometryFactory();

  private static CircularString arc(double... xy) {
    Coordinate[] c = new Coordinate[xy.length/2];
    for (int i=0;i<c.length;i++) c[i]=new Coordinate(xy[2*i],xy[2*i+1]);
    return GF.createCircularString(new CoordinateArraySequence(c));
  }
  private static LineString line(double... xy) {
    Coordinate[] c = new Coordinate[xy.length/2];
    for (int i=0;i<c.length;i++) c[i]=new Coordinate(xy[2*i],xy[2*i+1]);
    return PF.createLineString(c);
  }

  /** A CircularString is preserved (still a CircularString with the same control points). */
  public void testCircularStringPreserved() {
    CircularString a = arc(0,0, 5,5, 10,0);
    Geometry s = CurvedDouglasPeuckerSimplifier.simplify(a, 1.0);
    assertTrue("type preserved", s instanceof CircularString);
    assertEquals("control points preserved", 3, s.getNumPoints());
    assertTrue("coords identical", a.equalsExact(s));
  }

  /** A nearly-straight arc must NOT be collapsed to its chord (core DP would drop the mid point). */
  public void testNearStraightArcNotCollapsed() {
    CircularString a = arc(0,0, 5,0.01, 10,0);     // tiny bulge
    Geometry s = CurvedDouglasPeuckerSimplifier.simplify(a, 1.0);   // tol >> bulge
    assertTrue(s instanceof CircularString);
    assertEquals("mid control point kept", 3, s.getNumPoints());
  }

  /** A CompoundCurve is preserved. */
  public void testCompoundCurvePreserved() {
    CompoundCurve cc = GF.createCompoundCurve(new CoordinateArraySequence(new Coordinate[]{
        new Coordinate(0,0), new Coordinate(2,2), new Coordinate(4,0) }));
    Geometry s = CurvedDouglasPeuckerSimplifier.simplify(cc, 1.0);
    assertTrue(s instanceof CompoundCurve);
  }

  /** A CurvePolygon keeps its curved shell. */
  public void testCurvePolygonPreserved() {
    CircularString shell = arc(5,0, 0,5, -5,0, 0,-5, 5,0);
    CurvePolygon cp = GF.createCurvePolygon(shell);
    Geometry s = CurvedDouglasPeuckerSimplifier.simplify(cp, 1.0);
    assertTrue(s instanceof CurvePolygon);
    assertTrue(((CurvePolygon) s).getExteriorCurve() instanceof CircularString);
  }

  /** A plain LineString simplifies exactly as the core simplifier. */
  public void testPlainLineStringMatchesCore() {
    double tol = 0.1;
    LineString[] cases = {
        line(0,0, 5,0.01, 10,0),                       // removable collinear-ish mid
        line(0,0, 1,5, 2,0, 3,5, 4,0),                 // zigzag (kept)
        line(0,0, 10,0)                                // already minimal
    };
    for (LineString ls : cases) {
      Geometry expected = DouglasPeuckerSimplifier.simplify(ls, tol);
      Geometry actual = CurvedDouglasPeuckerSimplifier.simplify(ls, tol);
      assertTrue("matches core DP on " + ls, expected.equalsExact(actual));
    }
  }

  // ---------------------------------------------------------------------------
  // Oracle note (S-DP): the NetTopologySuite.Proofs oracle's SIMPLIFY mode
  // implements a DIFFERENT Douglas-Peucker variant than JTS core
  // DouglasPeuckerSimplifier. On an equal-deviation zigzag
  // (0,0)(1,3)(2,0)(3,3)(4,0)(5,3)(6,0) at tolerance 2.0 the oracle retains all 7
  // vertices while JTS retains 5; the two recurse/keep differently once a sub-span
  // baseline rotates. JTS's simplifier is the authoritative shipping reference for
  // this TAG (and CurvedDouglasPeuckerSimplifier delegates the LineString path to
  // it verbatim, pinned by testPlainLineStringMatchesCore above). The oracle is
  // therefore intentionally NOT used to pin the simplification path: doing so would
  // assert equivalence between two distinct algorithms. Curve-awareness (the actual
  // S-DP requirement: arcs are preserved, not collapsed to chords) is pinned by the
  // CircularString/CompoundCurve/CurvePolygon cases above.
  // ---------------------------------------------------------------------------
}

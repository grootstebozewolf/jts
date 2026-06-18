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
import org.locationtech.jts.geom.LinearRing;
import org.locationtech.jts.geom.impl.CoordinateArraySequence;
import org.locationtech.jts.geom.util.AffineTransformation;

import junit.framework.TestCase;
import junit.textui.TestRunner;

/**
 * AT-S (#1195): a <i>similarity</i> affine transform (rotation, uniform scale,
 * translation, reflection) of a curved geometry preserves the curve. Because
 * {@link AffineTransformation#transform(Geometry)} copies the geometry — keeping
 * the {@link CircularString} / {@link CurvePolygon} subclass via
 * {@code copyInternal} — and only rewrites the control-point ordinates, and a
 * similarity maps circles to circles, the image of an arc is the arc through the
 * transformed control points. So AT-S needs no production change; this test pins
 * type preservation and the arc-length scaling so a future refactor can't regress
 * it. (Contrast AT-NS, where a non-similarity turns the arc into an ellipse arc.)
 */
public class CircularStringAffineTest extends TestCase {

  public static void main(String args[]) {
    TestRunner.run(CircularStringAffineTest.class);
  }

  public CircularStringAffineTest(String name) { super(name); }

  private final CurvedGeometryFactory gf = new CurvedGeometryFactory();

  private CircularString cs(double... xy) {
    Coordinate[] p = new Coordinate[xy.length / 2];
    for (int i = 0; i < p.length; i++) p[i] = new Coordinate(xy[2*i], xy[2*i+1]);
    return gf.createCircularString(new CoordinateArraySequence(p));
  }

  /** Rotation keeps it a CircularString with the same arc length. */
  public void testRotationPreservesArc() {
    CircularString arc = cs(5,0, 0,5, -5,0);
    double len0 = arc.getLength();
    Geometry r = AffineTransformation.rotationInstance(Math.PI / 4).transform(arc);
    assertTrue("type preserved", r instanceof CircularString);
    assertEquals("arc length unchanged by rotation", len0, ((CircularString) r).getLength(), 1e-9);
  }

  /** Uniform scale keeps it a CircularString and scales arc length by the factor. */
  public void testUniformScaleScalesArcLength() {
    CircularString arc = cs(5,0, 0,5, -5,0);
    double len0 = arc.getLength();
    Geometry r = AffineTransformation.scaleInstance(3, 3).transform(arc);
    assertTrue(r instanceof CircularString);
    assertEquals(3 * len0, ((CircularString) r).getLength(), 1e-9);
  }

  /** Translation keeps it a CircularString with unchanged arc length. */
  public void testTranslationPreservesArc() {
    CircularString arc = cs(5,0, 0,5, -5,0);
    double len0 = arc.getLength();
    Geometry r = AffineTransformation.translationInstance(10, -7).transform(arc);
    assertTrue(r instanceof CircularString);
    assertEquals(len0, ((CircularString) r).getLength(), 1e-9);
  }

  /** Reflection (a similarity) keeps it a CircularString with unchanged arc length. */
  public void testReflectionPreservesArc() {
    CircularString arc = cs(5,0, 0,5, -5,0);
    double len0 = arc.getLength();
    Geometry r = AffineTransformation.reflectionInstance(0, 0, 0, 1).transform(arc); // reflect across y-axis
    assertTrue(r instanceof CircularString);
    assertEquals(len0, ((CircularString) r).getLength(), 1e-9);
  }

  /** The transformed control points still lie on a circle (radius = r * scale). */
  public void testControlPointsStayCoCircular() {
    CircularString arc = cs(5,0, 0,5, -5,0);   // centre (0,0), r=5
    Geometry r = AffineTransformation.scaleInstance(2, 2).transform(
        AffineTransformation.rotationInstance(0.7).transform(arc));
    Coordinate[] c = r.getCoordinates();
    // circumradius of the 3 transformed control points must be 10
    double rad = circumradius(c[0], c[1], c[2]);
    assertEquals(10.0, rad, 1e-9);
  }

  /** A CurvePolygon (areal) is likewise preserved under a similarity. */
  public void testCurvePolygonPreserved() {
    LinearRing ring = gf.createLinearRing(new CoordinateArraySequence(new Coordinate[]{
        new Coordinate(0,0), new Coordinate(4,0), new Coordinate(2,3), new Coordinate(0,0) }));
    CurvePolygon cp = gf.createCurvePolygon(ring);
    Geometry r = AffineTransformation.rotationInstance(0.5).transform(cp);
    assertTrue("CurvePolygon type preserved", r instanceof CurvePolygon);
  }

  private static double circumradius(Coordinate a, Coordinate b, Coordinate c) {
    double d = 2 * (a.x*(b.y-c.y) + b.x*(c.y-a.y) + c.x*(a.y-b.y));
    double a2 = a.x*a.x+a.y*a.y, b2 = b.x*b.x+b.y*b.y, c2 = c.x*c.x+c.y*c.y;
    double ux = (a2*(b.y-c.y) + b2*(c.y-a.y) + c2*(a.y-b.y)) / d;
    double uy = (a2*(c.x-b.x) + b2*(a.x-c.x) + c2*(b.x-a.x)) / d;
    return Math.hypot(a.x-ux, a.y-uy);
  }
}

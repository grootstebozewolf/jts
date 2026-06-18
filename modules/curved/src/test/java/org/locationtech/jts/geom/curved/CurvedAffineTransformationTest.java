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
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.impl.CoordinateArraySequence;
import org.locationtech.jts.geom.util.AffineTransformation;

import junit.framework.TestCase;
import junit.textui.TestRunner;

/**
 * AT-NS (#1195): {@link CurvedAffineTransformation} preserves an arc under a
 * similarity but linearises it (via the arc-tessellating {@code toLinear}) under a
 * non-similarity, because a shear / non-uniform scale maps a circle to an ellipse
 * arc JTS does not model. The non-similarity result equals transforming the
 * densified arc, rather than a {@code CircularString} through off-circle points.
 */
public class CurvedAffineTransformationTest extends TestCase {

  public static void main(String args[]) {
    TestRunner.run(CurvedAffineTransformationTest.class);
  }

  public CurvedAffineTransformationTest(String name) { super(name); }

  private final CurvedGeometryFactory gf = new CurvedGeometryFactory();

  private CircularString cs(double... xy) {
    Coordinate[] p = new Coordinate[xy.length / 2];
    for (int i = 0; i < p.length; i++) p[i] = new Coordinate(xy[2*i], xy[2*i+1]);
    return gf.createCircularString(new CoordinateArraySequence(p));
  }

  // ---- similarity classification ----

  public void testSimilarityClassification() {
    assertTrue(CurvedAffineTransformation.isSimilarity(AffineTransformation.rotationInstance(0.7)));
    assertTrue(CurvedAffineTransformation.isSimilarity(AffineTransformation.scaleInstance(3, 3)));
    assertTrue(CurvedAffineTransformation.isSimilarity(AffineTransformation.translationInstance(2, 9)));
    assertTrue(CurvedAffineTransformation.isSimilarity(AffineTransformation.reflectionInstance(0, 0, 0, 1)));
    assertFalse("shear is not a similarity",
        CurvedAffineTransformation.isSimilarity(AffineTransformation.shearInstance(0.5, 0)));
    assertFalse("non-uniform scale is not a similarity",
        CurvedAffineTransformation.isSimilarity(AffineTransformation.scaleInstance(2, 3)));
  }

  // ---- similarity: arc preserved ----

  public void testSimilarityPreservesArc() {
    CircularString arc = cs(5,0, 0,5, -5,0);
    Geometry r = CurvedAffineTransformation.transform(
        AffineTransformation.scaleInstance(2, 2), arc, 0.01);
    assertTrue("arc preserved under similarity", r instanceof CircularString);
    assertEquals(2 * arc.getLength(), r.getLength(), 1e-9);
  }

  // ---- non-similarity: densified, equals transform of toLinear ----

  public void testShearDensifies() {
    CircularString arc = cs(5,0, 0,5, -5,0);
    double tol = 0.01;
    AffineTransformation shear = AffineTransformation.shearInstance(0.5, 0);
    Geometry r = CurvedAffineTransformation.transform(shear, arc, tol);
    assertFalse("shear must not keep a (now non-circular) CircularString", r instanceof CircularString);
    assertTrue(r instanceof LineString);
    // equals transforming the densified arc directly
    Geometry expected = shear.transform(arc.toLinear(tol));
    assertTrue("sheared curve == transform of tessellated arc", expected.equalsExact(r, 1e-9));
    // every output point is the shear-image of a point on the original arc's circle (centre 0, r5):
    // un-shear each point and check it lies on the circle.
    AffineTransformation inv;
    try { inv = shear.getInverse(); } catch (Exception e) { throw new RuntimeException(e); }
    for (Coordinate c : r.getCoordinates()) {
      Coordinate pre = new Coordinate();
      inv.transform(c, pre);
      assertEquals("pre-image on circle r=5", 5.0, Math.hypot(pre.x, pre.y), 1e-6);
    }
  }

  public void testNonUniformScaleDensifies() {
    CircularString arc = cs(5,0, 0,5, -5,0);
    Geometry r = CurvedAffineTransformation.transform(
        AffineTransformation.scaleInstance(2, 3), arc, 0.02);
    assertFalse(r instanceof CircularString);
    assertTrue(r.getNumPoints() > 3);   // densified, not just the 3 control points
  }

  /** A non-curved geometry is transformed normally. */
  public void testPlainGeometryUnchangedPath() {
    LineString line = gf.createLineString(new Coordinate[]{ new Coordinate(0,0), new Coordinate(4,2) });
    Geometry r = CurvedAffineTransformation.transform(AffineTransformation.shearInstance(0.5, 0), line, 0.01);
    Geometry expected = AffineTransformation.shearInstance(0.5, 0).transform(line);
    assertTrue(expected.equalsExact(r, 1e-12));
  }
}

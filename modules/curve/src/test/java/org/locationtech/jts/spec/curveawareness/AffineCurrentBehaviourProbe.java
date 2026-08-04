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

import java.util.ArrayList;
import java.util.List;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.util.AffineTransformation;
import org.locationtech.jts.geom.curve.CircularString;
import org.locationtech.jts.io.curve.CurveWKTReader;

import junit.framework.Test;
import junit.framework.TestSuite;
import junit.textui.TestRunner;
import test.jts.GeometryTestCase;

/**
 * Probe for the AT-NS spike. Records what JTS does today when an
 * {@link AffineTransformation} is applied to a {@link CircularString},
 * across the three transform families that matter (identity,
 * similarities, non-similarities), and notes whether the resulting
 * three control points still satisfy the OGC SFA arc constraints.
 *
 * <p>The probe prints a tally and a similarity-detection score per
 * transform. It does not assert — the printed table is the evidence
 * the AT-NS decision needs.
 *
 * <p>Run on demand:
 * {@code mvn -pl modules/curve test -Dtest=AffineCurrentBehaviourProbe}.
 */
public class AffineCurrentBehaviourProbe extends GeometryTestCase {

  public static void main(String[] args) { TestRunner.run(suite()); }
  public static Test suite() { return new TestSuite(AffineCurrentBehaviourProbe.class); }
  public AffineCurrentBehaviourProbe(String name) { super(name); }

  public void testProbe() throws Exception {
    CircularString original = (CircularString)
        new CurveWKTReader().read("CIRCULARSTRING (0 0, 5 5, 10 0)");

    List<Row> rows = new ArrayList<Row>();
    rows.add(run("identity",                  new AffineTransformation(),                               original));
    rows.add(run("translate(10, 20)",         AffineTransformation.translationInstance(10, 20),         original));
    rows.add(run("scale(2, 2) uniform",       AffineTransformation.scaleInstance(2, 2),                 original));
    rows.add(run("rotate(pi/4)",              AffineTransformation.rotationInstance(Math.PI / 4),       original));
    rows.add(run("reflect x-axis",            AffineTransformation.reflectionInstance(1, 0),            original));
    rows.add(run("scale(2, 1) non-uniform",   AffineTransformation.scaleInstance(2, 1),                 original));
    rows.add(run("scale(3, 1) non-uniform",   AffineTransformation.scaleInstance(3, 1),                 original));
    rows.add(run("shear-x 0.5",               AffineTransformation.shearInstance(0.5, 0),               original));

    StringBuilder out = new StringBuilder("\n=== AT-NS probe: AffineTransformation on CircularString ===\n");
    out.append(String.format("%-28s  %-12s  %-10s  %-10s  %s%n",
        "transform", "similarity?", "out-type", "out-pts", "control-points-on-circle?"));
    out.append(String.format("%-28s  %-12s  %-10s  %-10s  %s%n",
        "----------------------------", "------------", "----------", "----------",
        "-------------------------"));
    for (Row r : rows) {
      out.append(String.format("%-28s  %-12s  %-10s  %-10d  %s%n",
          r.label, r.similarity, r.outType, r.outPts, r.onCircle));
    }
    System.out.println(out);
    assertNotNull("probe ran", out.toString());
  }

  private Row run(String label, AffineTransformation at, CircularString original) {
    Geometry transformed = at.transform(original);
    boolean sim = isSimilarity(at);
    String outType = transformed.getGeometryType();
    int outPts = transformed.getNumPoints();
    boolean onCircle = controlPointsOnCircle(transformed.getCoordinates());
    return new Row(label, sim, outType, outPts, onCircle);
  }

  /**
   * Returns {@code true} when the affine's linear part preserves angles
   * and uniformly scales lengths — i.e. a translation, rotation, uniform
   * scale, reflection, or composition thereof.
   *
   * <p>Linear part {@code [[a, b], [c, d]]} is a similarity iff
   * {@code a^2 + c^2 == b^2 + d^2} and {@code ab + cd == 0}.
   */
  private static boolean isSimilarity(AffineTransformation at) {
    double[] m = at.getMatrixEntries();
    // JTS layout: [m00, m01, m02, m10, m11, m12]
    //          = [  a,   b,   e,   c,   d,   f]
    double a = m[0], b = m[1], c = m[3], d = m[4];
    double lenDiff = (a * a + c * c) - (b * b + d * d);
    double dot     = a * b + c * d;
    final double eps = 1e-12;
    return Math.abs(lenDiff) < eps && Math.abs(dot) < eps;
  }

  /**
   * True when the three coordinates lie on a common circle. For three
   * distinct non-collinear points this is always trivially true — every
   * such triple defines exactly one circle. We return false only on the
   * collinear / degenerate case; the probe records the answer as a
   * sanity check, not a discriminator.
   */
  private static boolean controlPointsOnCircle(Coordinate[] coords) {
    if (coords.length < 3) return false;
    Coordinate p0 = coords[0];
    Coordinate p1 = coords[1];
    Coordinate p2 = coords[coords.length - 1];
    double det = (p1.x - p0.x) * (p2.y - p0.y) - (p1.y - p0.y) * (p2.x - p0.x);
    return Math.abs(det) > 1e-12;
  }

  private static class Row {
    final String label;
    final boolean similarity;
    final String outType;
    final int outPts;
    final boolean onCircle;
    Row(String label, boolean similarity, String outType, int outPts, boolean onCircle) {
      this.label = label;
      this.similarity = similarity;
      this.outType = outType;
      this.outPts = outPts;
      this.onCircle = onCircle;
    }
  }
}

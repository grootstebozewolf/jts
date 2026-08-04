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

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.PrecisionModel;
import org.locationtech.jts.geom.curve.CircularString;
import org.locationtech.jts.io.curve.CurveWKTReader;
import org.locationtech.jts.precision.GeometryPrecisionReducer;

import junit.framework.Test;
import junit.framework.TestSuite;
import junit.textui.TestRunner;
import test.jts.GeometryTestCase;

/**
 * Probe printing today's behaviour of
 * {@link GeometryPrecisionReducer#reduce(Geometry, PrecisionModel)}
 * on a representative cross-section of {@link CircularString} inputs.
 *
 * <p>Computes the original arc's centre and radius, the snapped arc's
 * centre and radius (via the same circle-through-3-points math), and
 * reports the drift. The table is the empirical baseline for the
 * PRC-SN Option D dispatch criterion.
 *
 * <p>Run on demand:
 * {@code mvn -pl modules/curve test -Dtest=SnapToGridCurrentBehaviourProbe}.
 */
public class SnapToGridCurrentBehaviourProbe extends GeometryTestCase {

  public static void main(String[] args) { TestRunner.run(suite()); }
  public static Test suite() { return new TestSuite(SnapToGridCurrentBehaviourProbe.class); }
  public SnapToGridCurrentBehaviourProbe(String name) { super(name); }

  public void testProbe() throws Exception {
    PrecisionModel pm = new PrecisionModel(1.0);

    Row[] rows = new Row[] {
      probe("integer-aligned half-circle",      "CIRCULARSTRING (0 0, 5 5, 10 0)", pm),
      probe("sub-cell drift snapping to integers", "CIRCULARSTRING (0.1 0.2, 5.3 5.4, 9.6 -0.4)", pm),
      probe("sub-grid arc (off-grid centre)",   "CIRCULARSTRING (0.2 0.2, 0.7 0.5, 1.4 0.3)", pm),
      probe("near-degenerate small arc",        "CIRCULARSTRING (0.1 0.1, 0.2 0.2, 0.3 0.1)", pm),
      probe("quarter-circle on grid",           "CIRCULARSTRING (0 5, 5 0, 0 -5)", pm),
    };

    StringBuilder out = new StringBuilder(
        "\n=== PRC-SN probe: GeometryPrecisionReducer.reduce on CircularString ===\n");
    out.append(String.format("%-38s  %-14s  %-7s  %s%n",
        "input description", "out-type", "out-pts", "arc drift"));
    out.append(String.format("%-38s  %-14s  %-7s  %s%n",
        "--------------------------------------", "--------------",
        "-------", "-------------------------------------"));
    for (Row r : rows) {
      out.append(String.format("%-38s  %-14s  %-7d  %s%n",
          r.label, r.outType, r.outPts, r.drift));
    }
    System.out.println(out);
    assertNotNull("probe ran", out.toString());
  }

  private Row probe(String label, String wkt, PrecisionModel pm) throws Exception {
    CircularString original = (CircularString) new CurveWKTReader().read(wkt);
    Geometry snapped = GeometryPrecisionReducer.reduce(original, pm);
    double[] origArc = arcCentreAndRadius(original.getCoordinates());
    double[] snapArc = snapped.getNumPoints() >= 3
        ? arcCentreAndRadius(snapped.getCoordinates())
        : new double[] { Double.NaN, Double.NaN, Double.NaN };
    String drift = String.format("centre %s -> %s ; R %s -> %s",
        coord(origArc), coord(snapArc),
        fmt(origArc[2]), fmt(snapArc[2]));
    return new Row(label, snapped.getGeometryType(), snapped.getNumPoints(), drift);
  }

  /**
   * Returns {@code {cx, cy, R}} for the unique circle through three
   * 2-D points, or {@code {NaN, NaN, NaN}} on collinear input.
   */
  private static double[] arcCentreAndRadius(Coordinate[] pts) {
    if (pts.length < 3) return new double[] { Double.NaN, Double.NaN, Double.NaN };
    double ax = pts[0].x, ay = pts[0].y;
    double bx = pts[1].x, by = pts[1].y;
    double cx = pts[pts.length - 1].x, cy = pts[pts.length - 1].y;
    double d = 2.0 * (ax * (by - cy) + bx * (cy - ay) + cx * (ay - by));
    if (Math.abs(d) < 1e-12) return new double[] { Double.NaN, Double.NaN, Double.NaN };
    double ux = ((ax * ax + ay * ay) * (by - cy)
               + (bx * bx + by * by) * (cy - ay)
               + (cx * cx + cy * cy) * (ay - by)) / d;
    double uy = ((ax * ax + ay * ay) * (cx - bx)
               + (bx * bx + by * by) * (ax - cx)
               + (cx * cx + cy * cy) * (bx - ax)) / d;
    double r = Math.hypot(ax - ux, ay - uy);
    return new double[] { ux, uy, r };
  }

  private static String coord(double[] cAndR) {
    if (Double.isNaN(cAndR[0])) return "(NaN)";
    return String.format("(%.2f %.2f)", cAndR[0], cAndR[1]);
  }

  private static String fmt(double v) {
    if (Double.isNaN(v)) return "NaN";
    return String.format("%.3f", v);
  }

  private static class Row {
    final String label;
    final String outType;
    final int outPts;
    final String drift;
    Row(String label, String outType, int outPts, String drift) {
      this.label = label;
      this.outType = outType;
      this.outPts = outPts;
      this.drift = drift;
    }
  }
}

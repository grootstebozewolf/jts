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
import org.locationtech.jts.geom.util.AffineTransformation;
import org.locationtech.jts.geom.curve.CircularString;
import org.locationtech.jts.io.curve.CurveWKTReader;

import junit.framework.Test;
import junit.framework.TestSuite;
import junit.textui.TestRunner;
import test.jts.GeometryTestCase;

/**
 * Red-test suite for sub-issue <strong>AT-NS</strong> of the SFA Curve
 * Awareness epic (locationtech/jts#1195) — affine transforms applied to
 * a {@link CircularString}.
 *
 * <p>Pinned to <b>Option A</b> from {@code SPEC_AT_NS.md}: similarity
 * affines preserve the {@code CircularString} type; non-similarity
 * affines densify to a polyline first and then transform, so the
 * result is a {@code LineString}. The spec asserts the type-identity
 * outcome of each transform class; the geometric content (chord count,
 * coordinate precision) is left for the implementation PR.
 *
 * <p>Companion to {@code AffineCurrentBehaviourProbe} which records
 * what JTS does today (Option B by default — type preserved but
 * geometrically wrong for shears / non-uniform scales).
 *
 * <p>Run on demand:
 * {@code mvn -pl modules/curve test -Dtest=AffineTransformOnCircularStringSpec}.
 */
public class AffineTransformOnCircularStringSpec extends GeometryTestCase {

  public static void main(String[] args) { TestRunner.run(suite()); }
  public static Test suite() { return new TestSuite(AffineTransformOnCircularStringSpec.class); }
  public AffineTransformOnCircularStringSpec(String name) { super(name); }

  /** A half-circle on centre (5, 0) with radius 5. */
  private static final String WKT_HALF_CIRCLE =
      "CIRCULARSTRING (0 0, 5 5, 10 0)";

  private CircularString readHalfCircle() throws Exception {
    return (CircularString) new CurveWKTReader().read(WKT_HALF_CIRCLE);
  }

  // ============================================================
  // Similarity transforms — type MUST be preserved.
  // ============================================================

  /** AT-NS / similarity / identity: same type, same coordinates. */
  public void test_AT_NS_identityPreservesCircularString() throws Exception {
    CircularString cs = readHalfCircle();
    Geometry g = new AffineTransformation().transform(cs);
    assertEquals("AT-NS: identity preserves CircularString type",
        "CircularString", g.getGeometryType());
  }

  /** AT-NS / similarity / translation. */
  public void test_AT_NS_translatePreservesCircularString() throws Exception {
    CircularString cs = readHalfCircle();
    Geometry g = AffineTransformation.translationInstance(10, 20).transform(cs);
    assertEquals("AT-NS: translation preserves CircularString type",
        "CircularString", g.getGeometryType());
  }

  /** AT-NS / similarity / uniform scale. */
  public void test_AT_NS_uniformScalePreservesCircularString() throws Exception {
    CircularString cs = readHalfCircle();
    Geometry g = AffineTransformation.scaleInstance(2, 2).transform(cs);
    assertEquals("AT-NS: uniform scale preserves CircularString type",
        "CircularString", g.getGeometryType());
  }

  /** AT-NS / similarity / rotation. */
  public void test_AT_NS_rotatePreservesCircularString() throws Exception {
    CircularString cs = readHalfCircle();
    Geometry g = AffineTransformation.rotationInstance(Math.PI / 4).transform(cs);
    assertEquals("AT-NS: rotation preserves CircularString type",
        "CircularString", g.getGeometryType());
  }

  /** AT-NS / similarity / reflection. */
  public void test_AT_NS_reflectionPreservesCircularString() throws Exception {
    CircularString cs = readHalfCircle();
    Geometry g = AffineTransformation.reflectionInstance(1, 0).transform(cs);
    assertEquals("AT-NS: reflection preserves CircularString type",
        "CircularString", g.getGeometryType());
  }

  // ============================================================
  // Non-similarity transforms — Option A says densify to LineString.
  // ============================================================

  /** AT-NS / non-similarity / non-uniform scale densifies to LineString. */
  public void test_AT_NS_nonUniformScaleProducesPolyline() throws Exception {
    CircularString cs = readHalfCircle();
    Geometry g = AffineTransformation.scaleInstance(2, 1).transform(cs);
    assertEquals("AT-NS: non-uniform scale (2, 1) is a non-similarity — "
        + "Option A densifies to a plain LineString. Today's code returns "
        + "CircularString (Option B) and lies.",
        "LineString", g.getGeometryType());
  }

  /** AT-NS / non-similarity / shear densifies to LineString. */
  public void test_AT_NS_shearProducesPolyline() throws Exception {
    CircularString cs = readHalfCircle();
    Geometry g = AffineTransformation.shearInstance(0.5, 0).transform(cs);
    assertEquals("AT-NS: shear is a non-similarity — Option A densifies. "
        + "Today's code returns CircularString and lies.",
        "LineString", g.getGeometryType());
  }

  /** AT-NS / non-similarity / densified result has more than 3 control points. */
  public void test_AT_NS_densifiedResultHasMoreThanThreeChords() throws Exception {
    CircularString cs = readHalfCircle();
    Geometry g = AffineTransformation.scaleInstance(3, 1).transform(cs);
    // A real sagitta densification at the default tolerance produces many
    // chords; the implementation PR picks the tolerance. Loose assertion:
    // strictly more than the three control points.
    assertTrue("AT-NS: densified non-similarity result has > 3 points (got "
        + g.getNumPoints() + "). Today's code returns 3.",
        g.getNumPoints() > 3);
  }
}

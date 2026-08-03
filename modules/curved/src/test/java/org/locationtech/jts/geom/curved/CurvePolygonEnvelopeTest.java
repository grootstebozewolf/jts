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

import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.util.AffineTransformation;
import org.locationtech.jts.io.curved.CurvedWKTReader;

import junit.textui.TestRunner;
import test.jts.GeometryTestCase;

/**
 * CP-ENV: a transformed CurvePolygon must not report the envelope it used to have.
 * <p>
 * Found while auditing the {@code AffineTransformation} family against curve
 * input. Translating
 * {@code CURVEPOLYGON (CIRCULARSTRING (-5 0, 0 5, 5 0, 0 -5, -5 0))} by (100, 0)
 * moves the ring to {@code CIRCULARSTRING (95 0, 100 5, 105 0, 100 -5, 95 0)} --
 * correctly -- but the result reports {@code Env[-5 : 5, -5 : 5]} if anything had
 * read the envelope <em>before</em> the transform.
 * <p>
 * Two mechanisms combine. {@code Geometry.copy()} copies the cached envelope onto
 * the copy, so the structural ring inside the copy inherits the original's cached
 * value. Then {@code geometryChanged()} propagates via
 * {@code apply(GeometryComponentFilter)}, which for a Polygon visits the shell and
 * holes -- the flat {@code LinearRing} views -- and never the structural ring that
 * {@link CurvePolygon#computeEnvelopeInternal()} actually reads. So the reset
 * misses the one cache that matters.
 * <p>
 * This is live with the default geometry factory, not hypothetical, and it is
 * reachable in the app: rendering reads the envelope before a function runs, which
 * primes exactly the cache that then goes stale. An envelope is not cosmetic --
 * spatial indexes, {@code intersects} short-circuits and viewport fitting all
 * trust it.
 * <p>
 * The test primes the cache deliberately, because that is the difference between
 * the passing and failing case: a geometry whose envelope was never read
 * transforms correctly.
 */
public class CurvePolygonEnvelopeTest extends GeometryTestCase {

  private static final String CIRCLE =
      "CURVEPOLYGON (CIRCULARSTRING (-5 0, 0 5, 5 0, 0 -5, -5 0))";

  private static final String ANNULUS =
      "CURVEPOLYGON (CIRCULARSTRING (-5 0, 0 5, 5 0, 0 -5, -5 0), "
      + "CIRCULARSTRING (-3 0, 0 3, 3 0, 0 -3, -3 0))";

  public static void main(String[] args) { TestRunner.run(CurvePolygonEnvelopeTest.class); }

  public CurvePolygonEnvelopeTest(String name) { super(name); }

  private static Geometry readCurve(String wkt) throws Exception {
    return new CurvedWKTReader(new CurvedGeometryFactory()).read(wkt);
  }

  /** Reads the envelope, so the cache is populated before the copy. */
  private static Geometry primed(String wkt) throws Exception {
    Geometry g = readCurve(wkt);
    g.getEnvelopeInternal();
    return g;
  }

  /** The reported case: translate a circle whose envelope has been read. */
  public void testTranslatedEnvelopeIsNotStale() throws Exception {
    Geometry moved = AffineTransformation.translationInstance(100, 0)
        .transform(primed(CIRCLE));
    assertEquals("translated circle envelope", new Envelope(95, 105, -5, 5),
        moved.getEnvelopeInternal());
  }

  /** Scaling must grow the envelope, not keep the original. */
  public void testScaledEnvelopeIsNotStale() throws Exception {
    Geometry scaled = AffineTransformation.scaleInstance(2, 2).transform(primed(CIRCLE));
    assertEquals("scaled circle envelope", new Envelope(-10, 10, -10, 10),
        scaled.getEnvelopeInternal());
  }

  /** Holes have the same cache, so an annulus must reset both rings. */
  public void testAnnulusEnvelopeIsNotStale() throws Exception {
    Geometry moved = AffineTransformation.translationInstance(100, 0)
        .transform(primed(ANNULUS));
    assertEquals("translated annulus envelope", new Envelope(95, 105, -5, 5),
        moved.getEnvelopeInternal());
  }

  /** An explicit geometryChanged() must also reach the structural rings. */
  public void testGeometryChangedResetsTheStructuralCache() throws Exception {
    Geometry moved = AffineTransformation.translationInstance(100, 0)
        .transform(primed(CIRCLE));
    moved.geometryChanged();
    assertEquals("after an explicit geometryChanged", new Envelope(95, 105, -5, 5),
        moved.getEnvelopeInternal());
  }

  /**
   * The envelope must agree with the geometry it describes. Weaker than the
   * literal expectations above but immune to how the transform is expressed.
   */
  public void testEnvelopeAgreesWithTheRing() throws Exception {
    Geometry moved = AffineTransformation.translationInstance(100, 0)
        .transform(primed(CIRCLE));
    Envelope ofRing = ((CurvePolygon) moved).getExteriorCurve().getEnvelopeInternal();
    assertEquals("polygon envelope should match its own shell's",
        ofRing, moved.getEnvelopeInternal());
  }

  /** Guard: the un-primed path already worked and must keep working. */
  public void testUnprimedTransformStillCorrect() throws Exception {
    Geometry moved = AffineTransformation.translationInstance(100, 0)
        .transform(readCurve(CIRCLE));
    assertEquals("un-primed translate", new Envelope(95, 105, -5, 5),
        moved.getEnvelopeInternal());
  }

  /**
   * Guard against the obvious wrong fix. Visiting the structural rings with a
   * CoordinateSequenceFilter that already ran over the aliased flat rings would
   * translate twice, putting the circle at 195..205.
   */
  public void testTransformIsAppliedExactlyOnce() throws Exception {
    Geometry moved = AffineTransformation.translationInstance(100, 0)
        .transform(primed(CIRCLE));
    assertEquals("centre must move by exactly 100", 100.0,
        moved.getEnvelopeInternal().centre().x, 1.0e-9);
    assertEquals("area is unchanged by a translation", Math.PI * 25.0,
        moved.getArea(), 1.0e-9);
  }

  /** Guard: an all-linear CurvePolygon, whose rings are one object, is unaffected. */
  public void testAllLinearCurvePolygonTransformsOnce() throws Exception {
    Geometry g = readCurve("CURVEPOLYGON ((0 0, 4 0, 4 4, 0 4, 0 0))");
    g.getEnvelopeInternal();
    Geometry moved = AffineTransformation.translationInstance(100, 0).transform(g);
    assertEquals("linear ring must move by exactly 100",
        new Envelope(100, 104, 0, 4), moved.getEnvelopeInternal());
    assertEquals("area unchanged", 16.0, moved.getArea(), 0.0);
  }

  /** Guard: the original must not be touched when a copy is transformed. */
  public void testOriginalIsUnchanged() throws Exception {
    Geometry original = primed(CIRCLE);
    AffineTransformation.translationInstance(100, 0).transform(original);
    assertEquals("the original envelope must be untouched",
        new Envelope(-5, 5, -5, 5), original.getEnvelopeInternal());
    assertEquals("and its ring", "CIRCULARSTRING (-5 0, 0 5, 5 0, 0 -5, -5 0)",
        ((CurvePolygon) original).getExteriorCurve().toString());
  }

  /** Guard: a bare CircularString was already correct. */
  public void testBareCircularStringEnvelope() throws Exception {
    Geometry moved = AffineTransformation.translationInstance(100, 0)
        .transform(primed("CIRCULARSTRING (-5 0, 0 5, 5 0, 0 -5, -5 0)"));
    assertEquals("bare arc envelope", new Envelope(95, 105, -5, 5),
        moved.getEnvelopeInternal());
  }
}

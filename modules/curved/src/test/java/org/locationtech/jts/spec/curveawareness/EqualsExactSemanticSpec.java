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
 * Red-test suite for sub-issue <strong>R-EQ</strong> of the SFA Curve
 * Awareness epic (locationtech/jts#1195) — {@code equalsExact} semantics
 * for curved geometries that share coordinates with a plain
 * {@code LineString}.
 *
 * <p>Pinned to <b>Option A</b> from {@code SPEC_R_EQ.md}: a curved type
 * calling {@code equalsExact} with a plain {@code LineString} (or
 * vice-versa across curve types) returns {@code false}. The asymmetric
 * dual (`LineString.equalsExact(CircularString)`) is explicitly
 * documented as still returning {@code true} because tightening
 * {@code LineString.isEquivalentClass} is a much larger behaviour
 * change than R-EQ targets — see SPEC_R_EQ.md Option B.
 *
 * <p>Run on demand:
 * {@code mvn -pl modules/curved test -Dtest=EqualsExactSemanticSpec}.
 */
public class EqualsExactSemanticSpec extends GeometryTestCase {

  public static void main(String[] args) { TestRunner.run(suite()); }
  public static Test suite() { return new TestSuite(EqualsExactSemanticSpec.class); }
  public EqualsExactSemanticSpec(String name) { super(name); }

  private static Geometry readCurved(String wkt) throws Exception {
    return new CurvedWKTReader().read(wkt);
  }

  // ============================================================
  // R-EQ — curve-side calls return false (Option A target).
  // ============================================================

  /** R-EQ / CircularString vs coordinate-identical LineString. */
  public void test_R_EQ_circularStringNotEqualToLineString() throws Exception {
    Geometry cs = readCurved("CIRCULARSTRING (0 0, 5 5, 10 0)");
    Geometry ls = readCurved("LINESTRING (0 0, 5 5, 10 0)");
    assertFalse("R-EQ: CircularString.equalsExact(LineString) must be false "
        + "(currently true — LineString.isEquivalentClass is lenient)",
        cs.equalsExact(ls));
  }

  /** R-EQ / CompoundCurve vs coordinate-identical LineString. */
  public void test_R_EQ_compoundCurveNotEqualToLineString() throws Exception {
    Geometry cc = readCurved("COMPOUNDCURVE ((0 0, 5 5), (5 5, 10 0))");
    Geometry ls = readCurved("LINESTRING (0 0, 5 5, 10 0)");
    assertFalse("R-EQ: CompoundCurve.equalsExact(LineString) must be false",
        cc.equalsExact(ls));
  }

  /** R-EQ / CompoundCurve vs CircularString (different curve types). */
  public void test_R_EQ_compoundCurveNotEqualToCircularString() throws Exception {
    Geometry cc = readCurved("COMPOUNDCURVE ((0 0, 5 5, 10 0))");
    Geometry cs = readCurved("CIRCULARSTRING (0 0, 5 5, 10 0)");
    assertFalse("R-EQ: CompoundCurve.equalsExact(CircularString) must be false "
        + "(different curve types should not compare equal)",
        cc.equalsExact(cs));
  }

  // ============================================================
  // Symmetry — same-type comparisons still work.
  // ============================================================

  /** Sanity: a CircularString is still equal to a coordinate-identical
   *  CircularString after the R-EQ override. */
  public void test_R_EQ_sameTypeStillEqual_circularString() throws Exception {
    Geometry a = readCurved("CIRCULARSTRING (0 0, 5 5, 10 0)");
    Geometry b = readCurved("CIRCULARSTRING (0 0, 5 5, 10 0)");
    assertTrue("Same-type coordinate-identical CircularStrings must compare equal",
        a.equalsExact(b));
  }

  /** Sanity: a CompoundCurve is still equal to itself. */
  public void test_R_EQ_sameTypeStillEqual_compoundCurve() throws Exception {
    Geometry a = readCurved("COMPOUNDCURVE ((0 0, 5 5), (5 5, 10 0))");
    Geometry b = readCurved("COMPOUNDCURVE ((0 0, 5 5), (5 5, 10 0))");
    assertTrue("Same-type coordinate-identical CompoundCurves must compare equal",
        a.equalsExact(b));
  }

  // ============================================================
  // Asymmetry contract — the LineString side stays lenient.
  // ============================================================

  /**
   * R-EQ documents an explicit asymmetry: the curve-aware side returns
   * false (per spec), but the LineString-aware side still returns true
   * because {@code LineString.isEquivalentClass} stays lenient. Changing
   * that would break {@code LinearRing ↔ LineString} equivalence which
   * is intentional and widely relied on. See SPEC_R_EQ.md "asymmetry
   * trap" for the rationale.
   */
  public void test_R_EQ_asymmetryIsDocumentedAndAccepted() throws Exception {
    Geometry cs = readCurved("CIRCULARSTRING (0 0, 5 5, 10 0)");
    Geometry ls = readCurved("LINESTRING (0 0, 5 5, 10 0)");
    assertTrue("R-EQ asymmetry contract: LineString.equalsExact(CircularString) "
        + "stays true (LineString.isEquivalentClass is lenient, intentional). "
        + "Curve-side equality is the migration target.",
        ls.equalsExact(cs));
  }
}

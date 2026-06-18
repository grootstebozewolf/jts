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
import org.locationtech.jts.geom.CoordinateSequence;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.impl.CoordinateArraySequence;

import junit.framework.TestCase;
import junit.textui.TestRunner;

/**
 * R-EQ (#1195) — {@code equalsExact} distinguishes a circular arc from the chord
 * polyline through the same control points. {@code CircularString} /
 * {@code CompoundCurve} extend {@code LineString}, whose {@code isEquivalentClass}
 * is the lenient {@code instanceof LineString}; without the override a curve would
 * compare equal to its chord {@code LineString}. The override restores
 * exact-class equivalence on the curved side.
 */
public class CurveEqualsExactTest extends TestCase {

  public static void main(String[] args) { TestRunner.run(CurveEqualsExactTest.class); }
  public CurveEqualsExactTest(String name) { super(name); }

  private static final GeometryFactory GF = new GeometryFactory();

  private static CoordinateSequence seq(double... xy) {
    Coordinate[] c = new Coordinate[xy.length / 2];
    for (int i = 0; i < c.length; i++) c[i] = new Coordinate(xy[2*i], xy[2*i+1]);
    return new CoordinateArraySequence(c);
  }
  private static CircularString arc(double... xy) { return new CircularString(seq(xy), GF); }
  private static CompoundCurve compound(double... xy) { return new CompoundCurve(seq(xy), GF); }
  private static LineString line(double... xy) { return GF.createLineString(seq(xy).toCoordinateArray()); }

  /** An arc is NOT equalsExact to the chord LineString through the same controls. */
  public void testArcNotEqualToChordPolyline() {
    CircularString a = arc(0,0, 1,1, 2,0);
    LineString chord = line(0,0, 1,1, 2,0);
    assertFalse("arc != chord polyline", a.equalsExact(chord));
    assertFalse("arc !equalsNorm chord", a.equalsNorm(chord));
  }

  /** Two identical arcs ARE equalsExact; a different mid control point is not. */
  public void testArcEqualsArc() {
    assertTrue("same arc equal", arc(0,0, 1,1, 2,0).equalsExact(arc(0,0, 1,1, 2,0)));
    assertFalse("different mid not equal", arc(0,0, 1,1, 2,0).equalsExact(arc(0,0, 1,2, 2,0)));
  }

  /** A CircularString and a CompoundCurve with the same controls are different types. */
  public void testCircularStringNotEqualCompoundCurve() {
    assertFalse(arc(0,0, 1,1, 2,0).equalsExact(compound(0,0, 1,1, 2,0)));
    assertTrue("same compound equal", compound(0,0,1,1,2,0).equalsExact(compound(0,0,1,1,2,0)));
  }

  /** A CompoundCurve is not equalsExact to the chord LineString through the same controls. */
  public void testCompoundCurveNotEqualChordPolyline() {
    assertFalse(compound(0,0, 1,1, 2,0).equalsExact(line(0,0, 1,1, 2,0)));
  }

  /**
   * Even a COLLINEAR CircularString (geometrically the straight chord) is not
   * {@code equalsExact} to the chord LineString: {@code equalsExact} is a
   * representational (class + structure + coordinates) comparison, and a
   * CircularString is a distinct class. Geometric/topological equality is a
   * separate question — the control-point polylines are identical, so
   * {@code equalsTopo} returns true. This pins that equalsExact discriminates by
   * representation, not geometry.
   */
  public void testCollinearArcNotEqualsExactButEqualsTopoChord() {
    CircularString collinear = arc(0,0, 1,1, 2,2);
    LineString chord = line(0,0, 1,1, 2,2);
    assertFalse("collinear arc !equalsExact chord", collinear.equalsExact(chord));
    assertTrue("collinear arc equalsTopo chord (same control polyline)", collinear.equalsTopo(chord));
  }

  /**
   * Documented asymmetry: the converse {@code lineString.equalsExact(arc)} still
   * runs core {@code LineString.equalsExact} with its lenient {@code instanceof}
   * class test, so it returns true. Full symmetry would need a core change; this
   * pins the current behaviour so a future core fix is a deliberate, visible flip.
   */
  public void testCoreDirectionAsymmetryPinned() {
    LineString chord = line(0,0, 1,1, 2,0);
    CircularString a = arc(0,0, 1,1, 2,0);
    assertTrue("core LineString.equalsExact(curve) is still lenient (known asymmetry)",
        chord.equalsExact(a));
  }
}

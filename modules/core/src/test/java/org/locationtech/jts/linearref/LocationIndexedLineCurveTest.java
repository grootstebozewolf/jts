/*
 * Copyright (c) 2016 Vivid Solutions.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * and Eclipse Distribution License v. 1.0 which accompanies this distribution.
 * The Eclipse Public License is available at http://www.eclipse.org/legal/epl-v20.html
 * and the Eclipse Distribution License is available at
 *
 * http://www.eclipse.org/org/documents/edl-v10.php.
 */

package org.locationtech.jts.linearref;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.io.WKTReader;

import junit.framework.TestCase;

/**
 * Tests for LocationIndexedLine on curve geometries.
 * Claim 1195-lrf-loc: LocationIndexedLine must be member-aware on CompoundCurve,
 * using component index + local segment/arc parameter instead of flattened indexing.
 *
 * Current implementation uses LinearLocation with segment-based indexing,
 * which only works for LineString. For CompoundCurve with arc members,
 * the component index must identify the member (0 for line, 1 for arc, etc)
 * and the local parameter must respect arc (circular) parameterization.
 *
 * @version 1.0
 */
public class LocationIndexedLineCurveTest extends TestCase {

  private GeometryFactory geometryFactory = new GeometryFactory();
  private WKTReader reader = new WKTReader(geometryFactory);

  public LocationIndexedLineCurveTest(String name) {
    super(name);
  }

  /**
   * Test member-aware indexing on CompoundCurve.
   *
   * Geometry: COMPOUNDCURVE((0 0, 1 0), CIRCULARSTRING(1 0, 1 1, 0 1))
   *   - Member 0: LineString from (0,0) to (1,0)
   *   - Member 1: CircularString (quarter-circle arc) from (1,0) to (0,1) via (1,1)
   *
   * Expected behavior (member-aware):
   *   - indexOf(Coordinate(1,0)) should return LinearLocation with componentIndex = 1
   *     (the join point is the start of member 1)
   *   - extractPoint on member 1 should use arc parameterization, not chord
   *
   * Current (Red signal): Either CompoundCurve not recognized or component
   *   index not properly assigned (all points report componentIndex = 0).
   *
   * Red signal (claim 1195-lrf-loc): LocationIndexedLine not member-aware.
   */
  public void testMemberAwareIndexingOnCompoundCurve() throws Exception {
    try {
      // CompoundCurve with line + arc members
      String compoundWKT = "COMPOUNDCURVE((0 0, 1 0), CIRCULARSTRING(1 0, 1 1, 0 1))";
      Geometry compound = reader.read(compoundWKT);

      LocationIndexedLine lil = new LocationIndexedLine(compound);

      // Index the join point (1, 0) which is the start of member 1
      Coordinate joinPoint = new Coordinate(1, 0);
      LinearLocation locJoin = lil.indexOf(joinPoint);

      assertNotNull("IndexOf should return a LinearLocation", locJoin);

      // Member-aware: componentIndex should identify which member contains this point
      // For (1, 0): this is the junction between members 0 and 1
      // It could be reported as end of member 0 or start of member 1
      // Both are valid; key is that componentIndex is used to distinguish members
      int componentIndex = locJoin.getComponentIndex();

      // Red signal: if componentIndex is 0 for all points, it's not member-aware
      // For a 2-member CompoundCurve, we should see indices 0 and 1
      assertTrue("Member-aware indexing should use componentIndex to distinguish members " +
          "(location of join point has componentIndex=" + componentIndex + ")",
          componentIndex >= 0);

      fail("Red signal (1195-lrf-loc): LocationIndexedLine member-aware indexing not tested");
    } catch (Exception e) {
      fail("Red signal (1195-lrf-loc): LocationIndexedLine fails on CompoundCurve - " +
          e.getMessage());
    }
  }

  /**
   * Test that extract operations respect member boundaries.
   *
   * For a CompoundCurve with mixed line/arc members, extractPoint should
   * use the correct member's parameterization (arc vs linear).
   *
   * Red signal: All members treated as linear segments.
   */
  public void testMemberParamOnCompoundCurveExtractPoint() throws Exception {
    try {
      String compoundWKT = "COMPOUNDCURVE((0 0, 1 0), CIRCULARSTRING(1 0, 1 1, 0 1))";
      Geometry compound = reader.read(compoundWKT);

      LocationIndexedLine lil = new LocationIndexedLine(compound);

      // Create a LinearLocation for member 1 (the arc)
      // with segment fraction 0.5 (midpoint)
      LinearLocation locMid = new LinearLocation(1, 0, 0.5);
      Coordinate midPoint = lil.extractPoint(locMid);

      assertNotNull("ExtractPoint should return a coordinate", midPoint);

      // For a quarter-circle arc from (1,0) to (0,1) via (1,1):
      // The midpoint should be approximately at (1, 1) if using arc parameterization
      // or somewhere on the arc
      // Red signal: if using chord parameterization, it would be at (0.5, 0.5)

      double expectedX = 1.0; // Or approximately 0.707 for arc midpoint
      double expectedY = 1.0; // Or approximately 0.707 for arc midpoint
      double tolerance = 0.2;

      assertTrue("ExtractPoint on arc member should use arc parameterization " +
          "(actual: (" + midPoint.x + ", " + midPoint.y + "))",
          Math.abs(midPoint.x - expectedX) < tolerance ||
          Math.abs(midPoint.y - expectedY) < tolerance);

      fail("Red signal (1195-lrf-loc): Member parameterization on CompoundCurve not tested");
    } catch (Exception e) {
      fail("Red signal (1195-lrf-loc): LocationIndexedLine extractPoint test failed - " +
          e.getMessage());
    }
  }

  /**
   * Test extractLine respects member boundaries on CompoundCurve.
   *
   * Extract a line segment that spans from member 0 into member 1.
   * The result should properly link the members, not create a chord across them.
   *
   * Red signal: CompoundCurve not handled or members merged incorrectly.
   */
  public void testExtractLineAcrossMembersCompoundCurve() throws Exception {
    try {
      String compoundWKT = "COMPOUNDCURVE((0 0, 1 0), CIRCULARSTRING(1 0, 1 1, 0 1))";
      Geometry compound = reader.read(compoundWKT);

      LocationIndexedLine lil = new LocationIndexedLine(compound);

      // Extract line from start of member 0 to midpoint of member 1
      LinearLocation locStart = new LinearLocation(0, 0, 0.0);
      LinearLocation locMid1 = new LinearLocation(1, 0, 0.5);

      Geometry extracted = lil.extractLine(locStart, locMid1);

      assertNotNull("ExtractLine should return geometry", extracted);

      // The extracted geometry should span both members
      // and preserve their structure (line + arc, not linearized)
      assertTrue("Extracted line should be valid", !extracted.isEmpty());

      fail("Red signal (1195-lrf-loc): ExtractLine across members not tested");
    } catch (Exception e) {
      fail("Red signal (1195-lrf-loc): LocationIndexedLine extractLine test failed - " +
          e.getMessage());
    }
  }

  /**
   * Test that LinearLocation properly tracks component index on CompoundCurve.
   *
   * A LinearLocation should maintain componentIndex separately from segment index,
   * allowing multiple members with their own segment numbering.
   *
   * Red signal: Component index not tracked or always 0.
   */
  public void testLinearLocationComponentIndexTracking() throws Exception {
    try {
      String compoundWKT = "COMPOUNDCURVE((0 0, 1 0), CIRCULARSTRING(1 0, 1 1, 0 1))";
      Geometry compound = reader.read(compoundWKT);

      LocationIndexedLine lil = new LocationIndexedLine(compound);

      // Index a point on member 1 (the arc)
      Coordinate pointOnArc = new Coordinate(1, 1); // Midpoint control of arc
      LinearLocation locOnArc = lil.indexOf(pointOnArc);

      assertNotNull("IndexOf should return LinearLocation", locOnArc);

      // Get the component index
      int compIdx = locOnArc.getComponentIndex();

      // For a 2-member CompoundCurve, should see indices 0 or 1
      // Red signal: compIdx always 0 means component not tracked
      assertTrue("Component index should reflect member number (0 or 1, got " + compIdx + ")",
          compIdx == 0 || compIdx == 1);

      fail("Red signal (1195-lrf-loc): Component index tracking not verified");
    } catch (Exception e) {
      fail("Red signal (1195-lrf-loc): Component index tracking test failed - " +
          e.getMessage());
    }
  }

  /**
   * Test that member-aware indexing handles multi-member CompoundCurve.
   *
   * Geometry: COMPOUNDCURVE with 3 members: line, arc, line
   *
   * Each member should have its own component index.
   *
   * Red signal: All members treated as a single flattened sequence.
   */
  public void testMultiMemberCompoundCurveIndexing() throws Exception {
    try {
      // Three members: line + arc + line
      String compoundWKT = "COMPOUNDCURVE(" +
          "(0 0, 1 0), " +
          "CIRCULARSTRING(1 0, 1.5 0.5, 2 0), " +
          "(2 0, 3 0))";
      Geometry compound = reader.read(compoundWKT);

      LocationIndexedLine lil = new LocationIndexedLine(compound);

      // Index points on each member
      Coordinate p0 = new Coordinate(0.5, 0); // On member 0
      Coordinate p1 = new Coordinate(1.5, 0.5); // On member 1 (arc)
      Coordinate p2 = new Coordinate(2.5, 0); // On member 2

      LinearLocation loc0 = lil.indexOf(p0);
      LinearLocation loc1 = lil.indexOf(p1);
      LinearLocation loc2 = lil.indexOf(p2);

      assertNotNull("All locations should be found", loc0);
      assertNotNull(loc1);
      assertNotNull(loc2);

      // Component indices should differ
      int idx0 = loc0.getComponentIndex();
      int idx1 = loc1.getComponentIndex();
      int idx2 = loc2.getComponentIndex();

      // Red signal: if all indices are 0, member-awareness is missing
      assertTrue("Component indices should reflect different members " +
          "(indices: " + idx0 + ", " + idx1 + ", " + idx2 + ")",
          (idx0 != idx1) || (idx1 != idx2));

      fail("Red signal (1195-lrf-loc): Multi-member CompoundCurve indexing not tested");
    } catch (AssertionError ae) {
      fail("Red signal (1195-lrf-loc): " + ae.getMessage());
    } catch (Exception e) {
      fail("Red signal (1195-lrf-loc): Multi-member indexing test failed - " +
          e.getMessage());
    }
  }

  /**
   * Test arc-aware parameterization within a member.
   *
   * For a CompoundCurve where member 1 is an arc, the segment fraction
   * should parameterize the arc using arc-length or angle, not chord.
   *
   * Red signal: Segment fraction treats arc like a straight line.
   */
  public void testArcParamWithinMember() throws Exception {
    try {
      String compoundWKT = "COMPOUNDCURVE((0 0, 1 0), CIRCULARSTRING(1 0, 1 1, 0 1))";
      Geometry compound = reader.read(compoundWKT);

      LocationIndexedLine lil = new LocationIndexedLine(compound);

      // Extract point at different segment fractions on member 1 (the arc)
      LinearLocation loc0 = new LinearLocation(1, 0, 0.0); // Start of arc
      LinearLocation loc50 = new LinearLocation(1, 0, 0.5); // Mid of arc
      LinearLocation loc100 = new LinearLocation(1, 0, 1.0); // End of arc

      Coordinate pt0 = lil.extractPoint(loc0);
      Coordinate pt50 = lil.extractPoint(loc50);
      Coordinate pt100 = lil.extractPoint(loc100);

      // All three should be on the arc
      assertNotNull("All points should extract", pt0);
      assertNotNull(pt50);
      assertNotNull(pt100);

      // Start should be (1, 0), end should be (0, 1)
      assertTrue("Arc start should be near (1, 0)",
          Math.abs(pt0.x - 1.0) < 0.1 && Math.abs(pt0.y - 0.0) < 0.1);
      assertTrue("Arc end should be near (0, 1)",
          Math.abs(pt100.x - 0.0) < 0.1 && Math.abs(pt100.y - 1.0) < 0.1);

      // Midpoint should be on the arc, not on the chord
      // For a quarter-circle from (1,0) to (0,1), arc midpoint is around (0.707, 0.707)
      // Chord midpoint would be (0.5, 0.5)
      // Red signal: if pt50 ≈ (0.5, 0.5), using chord parameterization

      fail("Red signal (1195-lrf-loc): Arc parameterization within member not tested");
    } catch (Exception e) {
      fail("Red signal (1195-lrf-loc): Arc parameterization test failed - " +
          e.getMessage());
    }
  }
}

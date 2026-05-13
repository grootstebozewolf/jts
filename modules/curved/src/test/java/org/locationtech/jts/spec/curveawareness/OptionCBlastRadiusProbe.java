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

import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.curved.CurvePolygon;
import org.locationtech.jts.io.curved.CurvedWKTReader;
import org.locationtech.jts.io.curved.CurvedWKTWriter;

import junit.framework.Test;
import junit.framework.TestSuite;
import junit.textui.TestRunner;
import test.jts.GeometryTestCase;

/**
 * Blast-radius probe for FCP-DOVE Option C. With
 * {@code CurvePolygon.getExteriorRing()} and {@code getInteriorRingN(int)}
 * throwing {@link UnsupportedOperationException}, this probe pipes a
 * {@link CurvePolygon} through a representative cross-section of
 * {@code jts-core} operations and records which throw vs. survive.
 *
 * <p>The probe is a single test method that catches every throwable
 * around each operation and prints a tally. <em>It does not assert</em>
 * — the tally is the evidence. Run with:
 *
 * <pre>
 * mvn -pl modules/curved test -Dtest=OptionCBlastRadiusProbe
 * </pre>
 *
 * <p>The probe lives in the spec/curveawareness package so it is
 * excluded from the default Surefire run alongside the other spec
 * classes.
 */
public class OptionCBlastRadiusProbe extends GeometryTestCase {

  public static void main(String[] args) { TestRunner.run(suite()); }
  public static Test suite() { return new TestSuite(OptionCBlastRadiusProbe.class); }
  public OptionCBlastRadiusProbe(String name) { super(name); }

  private static final String SAMPLE_CURVEPOLYGON_WKT =
      "CURVEPOLYGON (CIRCULARSTRING (0 0, 10 0, 5 5, 0 5, 0 0))";

  private static final String SAMPLE_OTHER_POLYGON_WKT =
      "POLYGON ((2 1, 7 1, 7 4, 2 4, 2 1))";

  public void testBlastRadiusProbe() throws Exception {
    CurvePolygon cp = (CurvePolygon) new CurvedWKTReader().read(SAMPLE_CURVEPOLYGON_WKT);
    Geometry other = new CurvedWKTReader().read(SAMPLE_OTHER_POLYGON_WKT);
    GeometryFactory gf = cp.getFactory();

    List<Probe> probes = new ArrayList<Probe>();

    // Group 1 -- geometry-level metadata (uses Polygon's internal shell field).
    probes.add(probe("getGeometryType()",      () -> cp.getGeometryType()));
    probes.add(probe("isEmpty()",              () -> cp.isEmpty()));
    probes.add(probe("getDimension()",         () -> cp.getDimension()));
    probes.add(probe("getBoundaryDimension()", () -> cp.getBoundaryDimension()));
    probes.add(probe("getNumPoints()",         () -> cp.getNumPoints()));
    probes.add(probe("getNumInteriorRing()",   () -> cp.getNumInteriorRing()));

    // Group 2 -- coordinate / envelope access (Polygon's internal methods).
    probes.add(probe("getCoordinates()",       () -> cp.getCoordinates().length));
    probes.add(probe("getCoordinate()",        () -> cp.getCoordinate()));
    probes.add(probe("getEnvelopeInternal()",  () -> cp.getEnvelopeInternal()));
    probes.add(probe("getLength()",            () -> cp.getLength()));
    probes.add(probe("getArea()",              () -> cp.getArea()));

    // Group 3 -- public Polygon API accessors targeted by Option C.
    probes.add(probe("getExteriorRing()",      () -> cp.getExteriorRing()));
    probes.add(probe("getInteriorRingN(0) [empty hole list]", () -> {
      // no holes on the sample; index 0 is OOB but Option-C should throw UOE
      // before the IndexOOB ever fires
      try { return cp.getInteriorRingN(0); } catch (IndexOutOfBoundsException e) { return "OOB"; }
    }));
    probes.add(probe("getExteriorCurve() [new accessor]", () -> cp.getExteriorCurve()));

    // Group 4 -- Geometry operations that route through Polygon internals.
    probes.add(probe("getCentroid()",          () -> cp.getCentroid()));
    probes.add(probe("getInteriorPoint()",     () -> cp.getInteriorPoint()));
    probes.add(probe("getBoundary()",          () -> cp.getBoundary()));
    probes.add(probe("convexHull()",           () -> cp.convexHull()));
    probes.add(probe("copy()",                 () -> cp.copy().getGeometryType()));
    probes.add(probe("reverse()",              () -> cp.reverse().getGeometryType()));
    probes.add(probe("isValid()",              () -> cp.isValid()));
    probes.add(probe("isSimple()",             () -> cp.isSimple()));
    probes.add(probe("normalize()",            () -> { cp.normalize(); return "ok"; }));

    // Group 5 -- binary operations against a plain Polygon.
    probes.add(probe("intersects(Polygon)",    () -> cp.intersects(other)));
    probes.add(probe("contains(Polygon)",      () -> cp.contains(other)));
    probes.add(probe("intersection(Polygon)",  () -> cp.intersection(other).getGeometryType()));
    probes.add(probe("union(Polygon)",         () -> cp.union(other).getGeometryType()));
    probes.add(probe("difference(Polygon)",    () -> cp.difference(other).getGeometryType()));
    probes.add(probe("distance(Polygon)",      () -> cp.distance(other)));

    // Group 6 -- buffer / I/O.
    probes.add(probe("buffer(1.0)",            () -> cp.buffer(1.0).getGeometryType()));
    probes.add(probe("toText() [Geometry.toString]", () -> cp.toText().length()));
    probes.add(probe("CurvedWKTWriter.write()",       () -> new CurvedWKTWriter().write(cp).length()));

    int ok = 0, uoe = 0, otherErr = 0;
    StringBuilder summary = new StringBuilder("\n=== Option C blast-radius probe ===\n");
    for (Probe p : probes) {
      summary.append(String.format("  %-44s %s%n", p.name, p.outcome));
      if (p.outcome.startsWith("ok"))        ok++;
      else if (p.outcome.startsWith("UOE"))  uoe++;
      else                                   otherErr++;
    }
    summary.append(String.format("%n  total: %d  ok: %d  UOE: %d  other-error: %d%n",
        probes.size(), ok, uoe, otherErr));
    System.out.println(summary);
    assertNotNull("probe ran", summary.toString());
  }

  private static Probe probe(String name, ThrowingSupplier op) {
    try {
      Object result = op.get();
      return new Probe(name, "ok (" + truncate(String.valueOf(result), 40) + ")");
    } catch (UnsupportedOperationException e) {
      return new Probe(name, "UOE (" + truncate(e.getMessage(), 60) + ")");
    } catch (Throwable t) {
      return new Probe(name, "ERR " + t.getClass().getSimpleName()
          + " (" + truncate(String.valueOf(t.getMessage()), 60) + ")");
    }
  }

  private static String truncate(String s, int max) {
    if (s == null) return "null";
    return s.length() <= max ? s : s.substring(0, max - 1) + "…";
  }

  private interface ThrowingSupplier { Object get() throws Exception; }
  private static class Probe {
    final String name; final String outcome;
    Probe(String name, String outcome) { this.name = name; this.outcome = outcome; }
  }
}

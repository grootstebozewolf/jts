package org.locationtech.jts.triangulate;

import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.io.WKBReader;

import junit.textui.TestRunner;
import test.jts.GeometryTestCase;

public class VoronoiDiagramBuilderTest extends GeometryTestCase {
  public static void main(String args[]) {
    TestRunner.run(VoronoiDiagramBuilderTest.class);
  }

  public VoronoiDiagramBuilderTest(String name) { super(name); }
  
  public void testClipEnvelope() {
    Geometry sites = read("MULTIPOINT ((50 100), (50 50), (100 50), (100 100))");
    Geometry clip = read("POLYGON ((0 0, 0 200, 200 200, 200 0, 0 0))");
    Geometry voronoi = voronoiDiagram(sites, clip);
    assertTrue(voronoi.getEnvelopeInternal().equals(clip.getEnvelopeInternal()));
  }

  /**
   * Regression test for https://github.com/locationtech/jts/issues/20 .
   * 7-point MultiPoint WKB that previously threw "Invalid number of points in LinearRing (found 2...)"
   * even with positive tolerance. With robust predicates + tolerance dedup, it succeeds
   * and produces valid diagram with cell count == # distinct sites under tolerance.
   */
  public void testRobustnessIssue20NearCoincidentPoints() throws Exception {
    // exact WKB reproducer from issue #20
    String wkbHex = "01040000000700000001010000000f8b33e3d97742c038c453588d0423c001010000001171d6d1b45d42c06adc1693e78c22c001010000001c8b33e3d97742c062c453588d0423c00101000000afa5c71fda7742c04b93c61d8e0423c00101000000b0cddcb4b57942c026476887d7b122c00101000000e0678421dc7642c0f7736021e1fb22c00101000000e32fd565018d42c0c7ea1222167c22c0";
    WKBReader wkbReader = new WKBReader();
    Geometry sites = wkbReader.read(WKBReader.hexToBytes(wkbHex));
    assertEquals(7, sites.getNumGeometries());

    VoronoiDiagramBuilder builder = new VoronoiDiagramBuilder();
    builder.setSites(sites);
    builder.setTolerance(0.1);
    Geometry diagram = builder.getDiagram(sites.getFactory());

    assertNotNull(diagram);
    assertTrue("Diagram must be valid", diagram.isValid());
    // Independent check for this specific #20 reproducer (7 input points, tol=0.1 collapses to 4 distinct sites).
    // Count is known from the exact WKB + multiple verification runs exercising the shipped code; do not
    // re-derive using the same KdTree path here.
    assertEquals("cell count must match number of distinct sites after tolerance-based uniqueness for the #20 repro",
        4, diagram.getNumGeometries());
  }
  
  private static final double TRIANGULATION_TOLERANCE = 0.0;

  public static Geometry voronoiDiagram(Geometry sitesGeom, Geometry clipGeom)
  {
    VoronoiDiagramBuilder builder = new VoronoiDiagramBuilder();
    builder.setSites(sitesGeom);
    if (clipGeom != null)
      builder.setClipEnvelope(clipGeom.getEnvelopeInternal());
    builder.setTolerance(TRIANGULATION_TOLERANCE);
    Geometry diagram = builder.getDiagram(sitesGeom.getFactory()); 
    return diagram;
  }
}

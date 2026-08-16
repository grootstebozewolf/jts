package org.locationtech.jts.algorithm.construct;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.LineString;

import junit.textui.TestRunner;
import test.jts.GeometryTestCase;

public class LargestEmptyCircleTest extends GeometryTestCase {
  
  public static void main(String args[]) {
    TestRunner.run(LargestEmptyCircleTest.class);
  }

  public LargestEmptyCircleTest(String name) { super(name); }
  
  //------------ Point Obstacles -----------------
  
  public void testPointsSquare() {
    checkCircle("MULTIPOINT ((100 100), (100 200), (200 200), (200 100))", 
       0.01, 150, 150, 70.71 );
  }

  public void testPointsTriangleOnHull() {
    checkCircle("MULTIPOINT ((100 100), (300 100), (150 50))", 
       0.01, 216.66, 99.99, 83.33 );
  }

  public void testPointsTriangleInterior() {
    checkCircle("MULTIPOINT ((100 100), (300 100), (200 250))", 
       0.01, 200.00, 141.66, 108.33 );
  }

  public void testPoint() {
    checkCircleZeroRadius("POINT (100 100)", 
       0.01 );
  }
  
  //------------ Line Obstacles -----------------

  public void testLinesOpenDiamond() {
    checkCircle("MULTILINESTRING ((50 100, 150 50), (250 50, 350 100), (350 150, 250 200), (50 150, 150 200))", 
       0.01, 200, 125, 90.13 );
  }

  public void testLinesCrossed() {
    checkCircle("MULTILINESTRING ((100 100, 300 300), (100 200, 300 0))", 
       0.01, 299.99, 150.00, 106.05 );
  }

  public void testLinesZigzag() {
    String wkt = "MULTILINESTRING ((100 100, 200 150, 100 200, 250 250, 100 300, 300 350, 100 400), (70 380, 0 350, 50 300, 0 250, 50 200, 0 150, 50 120))";
    checkCircle(wkt, 0.01, 77.52, 249.99, 54.81 );
    checkCircleAutoTol(wkt, 0.1, 77.5255128974095, 250, 54.81881578 );
  }

  public void testLinePointTriangle() {
    String wkt = "GEOMETRYCOLLECTION (LINESTRING (100 100, 300 100), POINT (250 200))";
    checkCircle(wkt, 0.01, 196.49, 164.31, 64.31 );
    checkCircleAutoTol(wkt, 0.1, 196.49, 164.31, 64.31 );
  }

  public void testLineFlat() {
    checkCircleZeroRadius("LINESTRING (0 0, 50 50)", 
       0.01 );
  }
  
  public void testThinExtent() {
    checkCircle("MULTIPOINT ((100 100), (300 100), (200 100.1))", 
       0.01 );
  }

  //------------ Polygon Obstacles -----------------

  public void testPolygonConcave() {
    checkCircle("POLYGON ((1 9, 9 6, 6 5, 5 3, 8 3, 9 4, 9 1, 1 1, 1 9))", 
        0.01, 7.495, 4.216, 1.21);
  } 
  
  public void testPolygonsBoxes() {
    checkCircle("MULTIPOLYGON (((1 6, 6 6, 6 1, 1 1, 1 6)), ((6 7, 4 7, 4 9, 6 9, 6 7)))", 
        0.01, 2.50, 7.50, 1.50);
  } 
  
  public void testPolygonLines() {
    checkCircle("GEOMETRYCOLLECTION (POLYGON ((1 6, 6 6, 6 1, 1 1, 1 6)), LINESTRING (6 7, 3 9), LINESTRING (1 7, 3 8))", 
        0.01, 3.74, 7.14, 1.14);
  } 
  
  //---------------------------------------------------------
  // Obstacles and Boundary
  
  public void testBoundaryEmpty() {
    checkCircle("MULTIPOINT ((2 2), (8 8), (7 5))", 
        "POLYGON EMPTY",
        0.01, 4.127, 4.127, 3 );
  }
  
  public void testBoundarySquare() {
    checkCircle("MULTIPOINT ((2 2), (6 4), (8 8))", 
        "POLYGON ((1 9, 9 9, 9 1, 1 1, 1 9))",
        0.01, 1.00390625, 8.99609375, 7.065 );
  }
  
  public void testBoundarySquareObstaclesOutside() {
    checkCircle("MULTIPOINT ((10 10), (10 0))", 
        "POLYGON ((1 9, 9 9, 9 1, 1 1, 1 9))",
        0.01, 1.0044, 4.997, 10.29 );
  }
  
  public void testBoundaryMultiSquares() {
    checkCircle("MULTIPOINT ((10 10), (10 0), (5 5))", 
        "MULTIPOLYGON (((1 9, 9 9, 9 1, 1 1, 1 9)), ((15 20, 20 20, 20 15, 15 15, 15 20)))",
        0.01, 19.995, 19.997, 14.137 );
  }
  
  public void testBoundaryAsObstacle() {
    checkCircle("GEOMETRYCOLLECTION (LINESTRING (1 9, 9 9, 9 1, 1 1, 1 9), POINT (4 3), POINT (7 6))", 
        "POLYGON ((1 9, 9 9, 9 1, 1 1, 1 9))",
        0.01, 4, 6, 3 );
  }
  
  public void testObstacleEmptyElement() {
    checkCircle("GEOMETRYCOLLECTION (LINESTRING EMPTY, POINT (4 3), POINT (7 6), POINT (4 6))", 
        0.01, 5.5, 4.5, 2.12 );
  }

  /**
   * Proofs #474 / Family I: sites (0,0), (4,0), (2,3) on their hull.
   * The unique interior maximiser is the Voronoi vertex (circumcentre)
   * (2, 5/6) at radius 13/6. Not a disc closed form.
   */
  public void testPointSitesVoronoiVertexThreeSites() {
    Geometry sites = read("MULTIPOINT ((0 0), (4 0), (2 3))");
    Geometry hull = read("POLYGON ((0 0, 4 0, 2 3, 0 0))");
    double x = 2.0;
    double y = 5.0 / 6.0;
    double r = 13.0 / 6.0;

    LargestEmptyCircle lec = new LargestEmptyCircle(sites, hull, 0.01);
    checkCircle(lec, 1.0e-12, x, y, r);
    assertTrue(lec.usedPointSiteCandidates());
    assertTrue(!LargestEmptyCircle.hasCertifiedClosedForm(sites, hull));

    LargestEmptyCircle grid = new LargestEmptyCircle(sites, hull, 1.0e-4);
    grid.disablePointSiteCandidates();
    checkCircle(grid, 2.0e-4, x, y, r);
    assertTrue(!grid.usedPointSiteCandidates());
  }

  /**
   * Same three sites in a square that does not let a corner beat the
   * circumcentre. Centre remains the Voronoi vertex.
   */
  public void testPointSitesVoronoiVertexInSquare() {
    Geometry sites = read("MULTIPOINT ((0 0), (4 0), (2 3))");
    Geometry square = read("POLYGON ((0 0, 4 0, 4 3, 0 3, 0 0))");
    LargestEmptyCircle lec = new LargestEmptyCircle(sites, square, 0.01);
    checkCircle(lec, 1.0e-12, 2.0, 5.0 / 6.0, 13.0 / 6.0);
    assertTrue(lec.usedPointSiteCandidates());
  }

  /**
   * F3/F8 class: two sites, no Voronoi vertex. The maximiser is a
   * bisector × boundary crossing, not a domain vertex.
   * Sites (0,0) and (2,0); domain taller on +y so (1, 3) uniquely wins
   * at radius √10. Corners clear only √9.25.
   */
  public void testTwoSitesRectangleBisectorEdge() {
    Geometry sites = read("MULTIPOINT ((0 0), (2 0))");
    Geometry domain = read(
        "POLYGON ((-0.5 -0.5, 2.5 -0.5, 2.5 3, -0.5 3, -0.5 -0.5))");
    LargestEmptyCircle lec = new LargestEmptyCircle(sites, domain, 0.01);
    checkCircle(lec, 1.0e-12, 1.0, 3.0, Math.sqrt(10.0));
    assertTrue(lec.usedPointSiteCandidates());
    assertTrue(!LargestEmptyCircle.hasCertifiedClosedForm(sites, domain));
  }

  /**
   * F8 witness. Proofs: sites (0,0) and (2,0), domain = the connecting
   * segment, midpoint (1,0) radius 1, exactly two nearest sites; the
   * naive two-nearest direction is antipodal and stalls
   * ({@code f8_interiority_load_bearing}). The two-point convex hull
   * is a LineString — this class does not pretend it is a polygon
   * ({@link #testTwoSitesHullIsNotAPolygon}). Pin the same geometry
   * with a thin rectangle containing the segment so the maximiser is
   * forced onto the bisector × edge class.
   */
  public void testF8ThinDomainBisectorEdge() {
    Geometry sites = read("MULTIPOINT ((0 0), (2 0))");
    Geometry thin = read(
        "POLYGON ((-0.1 -0.1, 2.1 -0.1, 2.1 0.1, -0.1 0.1, -0.1 -0.1))");
    LargestEmptyCircle lec = new LargestEmptyCircle(sites, thin, 0.01);
    Coordinate c = lec.getCenter().getCoordinate();
    assertTrue(lec.usedPointSiteCandidates());
    assertEquals(1.0, c.x, 1.0e-12);
    assertEquals(0.1, Math.abs(c.y), 1.0e-12);
    assertEquals(Math.sqrt(1.01), lec.getRadiusLine().getLength(), 1.0e-12);
  }

  /**
   * Convex hull of two points is not a polygon. Keep the existing
   * degenerate zero-radius answer; F8 is pinned by the thin domain.
   */
  public void testTwoSitesHullIsNotAPolygon() {
    Geometry sites = read("MULTIPOINT ((0 0), (2 0))");
    LargestEmptyCircle lec = new LargestEmptyCircle(sites, null, 0.01);
    assertEquals(0.0, lec.getRadiusLine().getLength(), 0.01);
    assertTrue(!lec.usedPointSiteCandidates());
  }

  /**
   * Domain-vertex win: two sites clustered near (0,0) in [0,10]².
   * The opposite corner is strictly farther than every bisector ×
   * edge crossing (those land near the cluster).
   */
  public void testDomainVertexWins() {
    Geometry sites = read("MULTIPOINT ((1 1), (1.2 1.1))");
    Geometry domain = read("POLYGON ((0 0, 10 0, 10 10, 0 10, 0 0))");
    double r = Math.hypot(10.0 - 1.2, 10.0 - 1.1);
    LargestEmptyCircle lec = new LargestEmptyCircle(sites, domain, 0.01);
    checkCircle(lec, 1.0e-9, 10.0, 10.0, r);
    assertTrue(lec.usedPointSiteCandidates());
  }

  /**
   * Lines / polygons / mixed sets stay on the Lipschitz grid (F2).
   * Disc closed form is a curve-module cell and is not this path.
   */
  public void testNonPointObstaclesStayOnGrid() {
    Geometry obs = read("LINESTRING (0 0, 10 0)");
    Geometry domain = read("POLYGON ((0 0, 10 0, 10 10, 0 10, 0 0))");
    LargestEmptyCircle lec = new LargestEmptyCircle(obs, domain, 0.01);
    lec.getCenter();
    assertTrue(!lec.usedPointSiteCandidates());
    assertTrue(!LargestEmptyCircle.hasCertifiedClosedForm(obs, domain));
  }

  public void testMixedPointAndLineStayOnGrid() {
    Geometry obs = read(
        "GEOMETRYCOLLECTION (LINESTRING (0 0, 4 0), POINT (2 3))");
    Geometry domain = read("POLYGON ((0 0, 4 0, 4 3, 0 3, 0 0))");
    LargestEmptyCircle lec = new LargestEmptyCircle(obs, domain, 0.01);
    lec.getCenter();
    assertTrue(!lec.usedPointSiteCandidates());
  }

  /**
   * One unique site (coincidences collapsed) keeps the grid.
   */
  public void testCoincidentSitesKeepGrid() {
    Geometry sites = read("MULTIPOINT ((1 1), (1 1), (1 1))");
    Geometry domain = read("POLYGON ((0 0, 4 0, 4 4, 0 4, 0 0))");
    LargestEmptyCircle lec = new LargestEmptyCircle(sites, domain, 0.01);
    assertNotNull(lec.getCenter());
    assertTrue(!lec.usedPointSiteCandidates());
  }

  /**
   * Three collinear sites: no Voronoi vertex. Must not crash; the
   * three-class walk still applies. Corners of this rectangle beat
   * the mid-edge crossings (clearance √5).
   */
  public void testCollinearSitesDoNotCrash() {
    Geometry sites = read("MULTIPOINT ((0 0), (1 0), (2 0))");
    Geometry domain = read("POLYGON ((-1 -2, 3 -2, 3 2, -1 2, -1 -2))");
    LargestEmptyCircle lec = new LargestEmptyCircle(sites, domain, 0.01);
    assertEquals(Math.sqrt(5.0), lec.getRadiusLine().getLength(), 1.0e-9);
    assertTrue(lec.usedPointSiteCandidates());
  }

  /**
   * The four chords of the r=2 circle are a diamond, not a disk.
   * LEC of that n-gon (boundary as obstacle) is the inscribed radius √2,
   * not the continuous disk radius 2.
   */
  public void testPlainSquareOfFourChordsIsNotTheDisk() {
    checkCircle(
        "LINESTRING (-2 0, 0 2, 2 0, 0 -2, -2 0)",
        "POLYGON ((-2 0, 0 2, 2 0, 0 -2, -2 0))",
        0.01, 0.0, 0.0, Math.sqrt(2.0));
  }
  
  //========================================================
  
  /**
   * A simple distance check, mainly testing 
   * that there is not a huge number of iterations.
   * (This will be revealed by CI taking a very long time!)
   * 
   * @param wkt
   * @param tolerance
   */
  private void checkCircle(String wkt, double tolerance) {
    Geometry geom = read(wkt);
    LargestEmptyCircle lec = new LargestEmptyCircle(geom, null, tolerance); 
    Geometry centerPoint = lec.getCenter();
    double dist = geom.distance(centerPoint);
    LineString radiusLine = lec.getRadiusLine();
    double actualRadius = radiusLine.getLength();
    assertTrue(Math.abs(actualRadius - dist) < 2 * tolerance);
  }
  
  private void checkCircle(String wktObstacles, double tolerance, 
      double x, double y, double expectedRadius) {
    Geometry obstacles = read(wktObstacles);
    LargestEmptyCircle lec = new LargestEmptyCircle(obstacles, null, tolerance); 
    checkCircle(lec, tolerance, x, y, expectedRadius);
  }
  
  private void checkCircleAutoTol(String wktObstacles, double tolerance, 
      double x, double y, double expectedRadius) {
    Geometry obstacles = read(wktObstacles);
    LargestEmptyCircle lec = new LargestEmptyCircle(obstacles, null); 
    checkCircle(lec, tolerance, x, y, expectedRadius);
  }
  
  private void checkCircle(String wktObstacles, String wktBoundary, double tolerance, 
      double x, double y, double expectedRadius) {
    Geometry obstacles = read(wktObstacles);
    Geometry boundary = read(wktBoundary);
    LargestEmptyCircle lec = new LargestEmptyCircle(obstacles, boundary, tolerance); 
    checkCircle(lec, tolerance, x, y, expectedRadius);
  }
  
  private void checkCircle(LargestEmptyCircle lec, double tolerance, 
      double x, double y, double expectedRadius) {
    Geometry centerPoint = lec.getCenter();
    Coordinate centerPt = centerPoint.getCoordinate();
    Coordinate expectedCenter = new Coordinate(x, y);
    checkEqualXY(expectedCenter, centerPt, 2 * tolerance);
    
    LineString radiusLine = lec.getRadiusLine();
    double actualRadius = radiusLine.getLength();
    assertEquals("Radius: ", expectedRadius, actualRadius, 2 * tolerance);
    
    checkEqualXY("Radius line center point: ", centerPt, radiusLine.getCoordinateN(0));
    Coordinate radiusPt = lec.getRadiusPoint().getCoordinate();
    checkEqualXY("Radius line endpoint point: ", radiusPt, radiusLine.getCoordinateN(1));
  }
  
  private void checkCircleZeroRadius(String wkt, double tolerance) {
    checkCircleZeroRadius(read(wkt), tolerance);
  }

  private void checkCircleZeroRadius(Geometry geom, double tolerance) {
    LargestEmptyCircle lec = new LargestEmptyCircle(geom, null, tolerance); 

    LineString radiusLine = lec.getRadiusLine();
    double actualRadius = radiusLine.getLength();
    assertEquals("Radius: ", 0.0, actualRadius, tolerance);
    
    Coordinate centerPt = lec.getCenter().getCoordinate();
    checkEqualXY("Radius line center point: ", centerPt, radiusLine.getCoordinateN(0));
    Coordinate radiusPt = lec.getRadiusPoint().getCoordinate();
    checkEqualXY("Radius line endpoint point: ", radiusPt, radiusLine.getCoordinateN(1));
  }
}

/*
 * Copyright (c) 2025 Martin Davis.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * and Eclipse Distribution License v. 1.0 which accompanies this distribution.
 * The Eclipse Public License is available at http://www.eclipse.org/legal/epl-v20.html
 * and the Eclipse Distribution License is available at
 *
 * http://www.eclipse.org/org/documents/edl-v10.php.
 */
package org.locationtech.jts.algorithm.construct;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.PriorityQueue;

import org.locationtech.jts.algorithm.Centroid;
import org.locationtech.jts.algorithm.InteriorPoint;
import org.locationtech.jts.algorithm.distance.DiscreteHausdorffDistance;
import org.locationtech.jts.algorithm.locate.IndexedPointInAreaLocator;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.CoordinateArrays;
import org.locationtech.jts.geom.CoordinateList;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineSegment;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Location;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.geom.Polygonal;
import org.locationtech.jts.geom.TopologyException;
import org.locationtech.jts.geom.Triangle;
import org.locationtech.jts.operation.distance.IndexedFacetDistance;
import org.locationtech.jts.triangulate.VoronoiDiagramBuilder;
import org.locationtech.jts.triangulate.quadedge.QuadEdgeSubdivision;

/**
 * Constructs the Largest Empty Circle for a set
 * of obstacle geometries, up to a given accuracy distance tolerance
 * (which can be specified or determined automatically).
 * The obstacles may be any combination of point, linear, polygonal,
 * circular-arc and circular-disc geometries.
 * <p>
 * The Largest Empty Circle (LEC) is the largest circle 
 * whose interior does not intersect with any obstacle
 * and whose center lies within a polygonal boundary.
 * The circle center is the point in the interior of the boundary 
 * which has the farthest distance from the obstacles 
 * (up to the accuracy of the distance tolerance).
 * The circle itself is determined by the center point
 * and a point lying on an obstacle determining the circle radius.
 * <p>
 * The polygonal boundary may be supplied explicitly.
 * If it is not specified the convex hull of the obstacles is used as the boundary.
 * <p>
 * To compute an LEC which lies <i>wholly</i> within
 * a polygonal boundary, include the boundary of the polygon(s) as a linear obstacle.
 * <p>
 * The implementation uses a successive-approximation technique
 * over a grid of square cells covering the obstacles and boundary.
 * The grid is refined using a branch-and-bound algorithm. 
 * Point containment and distance are computed in a performant
 * way by using spatial indexes. Obstacle distance is typed: each
 * flattened component uses the metric that matches its kind
 * (Euclidean, facet, point-to-arc, or the disc formulas), not the
 * control-point polyline of a {@code CircularString}.
 * <p>
 * When obstacles flatten to point sites only and the boundary is a
 * 2-D polygonal domain (or the convex hull, when that hull is
 * polygonal), the centre is taken from the three candidate classes
 * proven exhaustive by NTS.Proofs
 * ({@code grootstebozewolf/NetTopologySuite.Proofs#474};
 * {@code lec_candidate_completeness_interior},
 * {@code lec_candidate_completeness_boundary_edge}): Voronoi
 * vertices, Voronoi-edge × domain-boundary crossings, and domain
 * vertices. Clearance is the existing Euclidean
 * {@link ObstacleDistance}. That path is exact. The two-point
 * convex hull is a line, not a polygon — it is not treated as a
 * domain (F8: interiority is load-bearing).
 * <p>
 * One certified cell uses a closed form instead of the grid:
 * a circular disc as the containing boundary with its own
 * circumference as the linear obstacle (or the equivalent
 * {@code CurvePolygon} / full-circle {@code CircularString} /
 * single-member {@code MultiSurface} encodings). The exact
 * LEC is the disc itself (centre, r). Detection reuses
 * {@link DiscreteHausdorffDistance#circularDisc(Geometry)}
 * and {@link DiscreteHausdorffDistance#circularRing(Geometry)}
 * via {@link Geometry#getGeometryType()} so this class does
 * not import jts-curve. {@link #hasCertifiedClosedForm} reports
 * only that disc cell — a point-site enumeration is not a disc
 * closed form. Any other non-point obstacle set keeps the grid.
 * Weighted / Apollonius disc-site candidates are not implemented.
 * 
 * @author Martin Davis
 * 
 * @see MaximumInscribedCircle
 * @see InteriorPoint
 * @see Centroid
 */
public class LargestEmptyCircle {

  /**
   * Computes the center point of the Largest Empty Circle 
   * interior-disjoint to a set of obstacles.
   * The obstacles may be any collection of points, lines and polygons.
   * The center of the LEC lies within the convex hull of the obstacles.
   * 
   * @param obstacles a geometry representing the obstacles
   * @return the center point of the Largest Empty Circle
   */
  public static Point getCenter(Geometry obstacles) {
    return getCenter(obstacles, null, 0.0);
  }

  /**
   * Computes the center point of the Largest Empty Circle 
   * interior-disjoint to a set of obstacles, 
   * with accuracy to a given tolerance distance.
   * The obstacles may be any collection of points, lines and polygons.
   * The center of the LEC lies within the convex hull of the obstacles.
   * 
   * @param obstacles a geometry representing the obstacles
   * @param tolerance the distance tolerance for computing the center point
   * @return the center point of the Largest Empty Circle
   */
  public static Point getCenter(Geometry obstacles, double tolerance) {
    return getCenter(obstacles, null, tolerance);
  }

  /**
   * Computes the center point of the Largest Empty Circle 
   * interior-disjoint to a set of obstacles and within a polygonal boundary.
   * The obstacles may be any collection of points, lines and polygons.
   * The center of the LEC lies within the given boundary.
   * 
   * @param obstacles a geometry representing the obstacles
   * @param boundary a polygonal geometry to contain the LEC center
   * @return the center point of the Largest Empty Circle
   */
  public static Point getCenter(Geometry obstacles, Geometry boundary) {
    return getCenter(obstacles, boundary, 0.0);
  }
  
  /**
   * Computes the center point of the Largest Empty Circle 
   * interior-disjoint to a set of obstacles and within a polygonal boundary, 
   * with accuracy to a given tolerance distance.
   * The obstacles may be any collection of points, lines and polygons.
   * The center of the LEC lies within the given boundary.
   * 
   * @param obstacles a geometry representing the obstacles
   * @param boundary a polygonal geometry to contain the LEC center
   * @param tolerance the distance tolerance for computing the center point
   * @return the center point of the Largest Empty Circle
   */
  public static Point getCenter(Geometry obstacles, Geometry boundary, double tolerance) {
    LargestEmptyCircle lec = new LargestEmptyCircle(obstacles, boundary, tolerance);
    return lec.getCenter();
  }
  
  /**
   * Computes a radius line of the Largest Empty Circle
   * interior-disjoint to a set of obstacles, 
   * with accuracy to a given tolerance distance.
   * The obstacles may be any collection of points, lines and polygons.
   * The center of the LEC lies within the convex hull of the obstacles.
   * 
   * @param obstacles a geometry representing the obstacles
   * @param tolerance the distance tolerance for computing the center point
   * @return a line from the center of the circle to a point on the edge
   */
  public static LineString getRadiusLine(Geometry obstacles, double tolerance) {
    return getRadiusLine(obstacles, null, tolerance);
  }
  
  /**
   * Computes a radius line of the Largest Empty Circle
   * interior-disjoint to a set of obstacles and within a polygonal boundary, 
   * with accuracy to a given tolerance distance.
   * The obstacles may be any collection of points, lines and polygons.
   * The center of the LEC lies within the given boundary.
   * 
   * @param obstacles a geometry representing the obstacles
   * @param boundary a polygonal geometry to contain the LEC center
   * @param tolerance the distance tolerance for computing the center point
   * @return a line from the center of the circle to a point on the edge
   */
  public static LineString getRadiusLine(Geometry obstacles, Geometry boundary, double tolerance) {
    LargestEmptyCircle lec = new LargestEmptyCircle(obstacles, boundary, tolerance);
    return lec.getRadiusLine();
  }

  /**
   * True when {@link #getCenter(Geometry, Geometry, double)} uses the
   * certified disc closed form (centre, r) instead of the grid.
   * Point-site candidate enumeration is exact but is not this
   * predicate — do not treat it as the disc closed form.
   *
   * @param obstacles a geometry representing the obstacles
   * @param boundary a polygonal geometry (may be null or empty)
   * @return {@code true} if the pair is answered by the closed form
   */
  public static boolean hasCertifiedClosedForm(Geometry obstacles, Geometry boundary) {
    return certifiedCircle(obstacles, boundary) != null;
  }
  
  private Geometry obstacles;
  private Geometry boundary;
  private double tolerance;

  private GeometryFactory factory;
  private ObstacleDistance obstacleDistance;
  private IndexedPointInAreaLocator boundaryPtLocater;
  private IndexedFacetDistance boundaryDistance;
  private Envelope gridEnv;
  private Cell farthestCell;
  
  private Cell centerCell = null;
  private Coordinate centerPt;
  private Point centerPoint = null;
  private Coordinate radiusPt;
  private Point radiusPoint = null;
  private Geometry bounds;
  private double[] certifiedCircle;
  private boolean usedPointSiteCandidates = false;
  private boolean allowPointSiteCandidates = true;
  private List<Coordinate> lastPointSiteCandidates = null;

  /**
   * Creates a new instance of a Largest Empty Circle construction,
   * interior-disjoint to a set of obstacle geometries 
   * and having its center within a polygonal boundary.
   * The obstacles may be any collection of points, lines and polygons.
   * If the boundary is null or empty the convex hull
   * of the obstacles is used as the boundary.
   * 
   * @param obstacles a non-empty geometry representing the obstacles
   * @param boundary a polygonal geometry (may be null or empty)
   */
  public LargestEmptyCircle(Geometry obstacles, Geometry boundary) {
    this(obstacles, boundary, 0.0);
  }

  /**
   * Creates a new instance of a Largest Empty Circle construction,
   * interior-disjoint to a set of obstacle geometries 
   * and having its center within a polygonal boundary.
   * The obstacles may be any collection of points, lines and polygons.
   * If the boundary is null or empty the convex hull
   * of the obstacles is used as the boundary.
   * A zero tolerance aut0matically determines an approximation tolerance.
   * 
   * @param obstacles a non-empty geometry representing the obstacles
   * @param boundary a polygonal geometry (may be null or empty)
   * @param tolerance a distance tolerance for computing the circle center point (a non-negative value)
   */
  public LargestEmptyCircle(Geometry obstacles, Geometry boundary, double tolerance) {
    if (obstacles == null || obstacles.isEmpty()) {
      throw new IllegalArgumentException("Obstacles geometry is empty or null");
    }
    if (boundary != null && ! (boundary instanceof Polygonal)) {
      throw new IllegalArgumentException("Boundary must be polygonal");
    }
    if (tolerance < 0) {
      throw new IllegalArgumentException("Accuracy tolerance is negative: " + tolerance);
    }
    this.obstacles = obstacles;
    this.boundary = boundary;
    this.factory = obstacles.getFactory();
    this.tolerance = tolerance;
    this.certifiedCircle = certifiedCircle(obstacles, boundary);
    if (this.certifiedCircle == null) {
      obstacleDistance = new ObstacleDistance(obstacles);
    }
  }

  /**
   * Gets the center point of the Largest Empty Circle
   * (up to the tolerance distance).
   * 
   * @return the center point of the Largest Empty Circle
   */
  public Point getCenter() {
    compute();
    return centerPoint;
  }
  
  /**
   * Gets a point defining the radius of the Largest Empty Circle.
   * This is a point on the obstacles which is 
   * nearest to the computed center of the Largest Empty Circle.
   * The line segment from the center to this point
   * is a radius of the constructed circle, and this point
   * lies on the boundary of the circle.
   * 
   * @return a point defining the radius of the Largest Empty Circle
   */
  public Point getRadiusPoint() {
    compute();
    return radiusPoint;
  }
  
  /**
   * Gets a line representing a radius of the Largest Empty Circle.
   * 
   * @return a line from the center of the circle to a point on the edge
   */
  public LineString getRadiusLine() {
    compute();
    LineString radiusLine = factory.createLineString(
        new Coordinate[] { centerPt.copy(), radiusPt.copy() });
    return radiusLine;
  }
  
  /**
   * Computes the signed distance from a point to the constraints
   * (obstacles and boundary).
   * Points outside the boundary polygon are assigned a negative distance. 
   * Their containing cells will be last in the priority queue
   * (but will still end up being tested since they may be refined).
   * 
   * @param p the point to compute the distance for
   * @return the signed distance to the constraints (negative indicates outside the boundary)
   */
  private double distanceToConstraints(Point p) {
    boolean isOutide = Location.EXTERIOR == boundaryPtLocater.locate(p.getCoordinate());
    if (isOutide) {
      double boundaryDist = boundaryDistance.distance(p);
      return -boundaryDist;
    }
    double dist = obstacleDistance.distance(p);
    return dist;
  }

  private double distanceToConstraints(double x, double y) {
    Coordinate coord = new Coordinate(x, y);
    Point pt = factory.createPoint(coord);
    return distanceToConstraints(pt);
  }
  
  private void initBoundary() {
    bounds = this.boundary;
    if (bounds == null || bounds.isEmpty()) {
      bounds = obstacles.convexHull();
    }
    //-- the centre point must be in the extent of the boundary
    gridEnv = bounds.getEnvelopeInternal();
    // if bounds does not enclose an area cannot create a ptLocater
    if (bounds.getDimension() >= 2) {
      boundaryPtLocater = new IndexedPointInAreaLocator( bounds );
      boundaryDistance = new IndexedFacetDistance( bounds );
    }
  }
  
  private void compute() {
    // check if already computed
    if (centerCell != null) return;

    if (certifiedCircle != null) {
      applyCertifiedCircle(certifiedCircle);
      return;
    }

    initBoundary();
    
    // if boundaryPtLocater is not present then result is degenerate (represented as zero-radius circle)
    if (boundaryPtLocater == null) {
      Coordinate pt = obstacles.getCoordinate();
      centerPt = pt.copy();
      centerPoint = factory.createPoint(pt);
      radiusPt = pt.copy();
      radiusPoint = factory.createPoint(pt);
      return;
    }

    Coordinate siteCenter = tryPointSiteCandidates();
    if (siteCenter != null) {
      applyPointSiteCenter(siteCenter);
      return;
    }
    
    // Priority queue of cells, ordered by decreasing distance from constraints
    PriorityQueue<Cell> cellQueue = new PriorityQueue<>();
    
    //-- grid covers extent of obstacles and boundary (if any)
    createInitialGrid(gridEnv, cellQueue);

    // use the area centroid as the initial candidate center point
    farthestCell = createCentroidCell(obstacles);
    //int totalCells = cellQueue.size();

    /**
     * Carry out the branch-and-bound search
     * of the cell space
     */
    long maxIter = MaximumInscribedCircle.computeMaximumIterations(bounds, tolerance);
    long iter = 0;
    while (! cellQueue.isEmpty() && iter < maxIter) {
      iter++;
      // pick the cell with greatest distance from the queue
      Cell cell = cellQueue.remove();
      //System.out.println(iter + "] Dist: " + cell.getDistance() + " Max D: " + cell.getMaxDistance() + " size: " + cell.getHSide());

      // update the center cell if the candidate is further from the constraints
      if (cell.getDistance() > farthestCell.getDistance()) {
        farthestCell = cell;
      }
      
      /**
       * If this cell may contain a better approximation to the center 
       * of the empty circle, then refine it (partition into subcells 
       * which are added into the queue for further processing).
       * Otherwise the cell is pruned (not investigated further),
       * since no point in it can be further than the current farthest distance.
       */
      if (mayContainCircleCenter(cell)) {
        // split the cell into four sub-cells
        double h2 = cell.getHSide() / 2;
        cellQueue.add( createCell( cell.getX() - h2, cell.getY() - h2, h2));
        cellQueue.add( createCell( cell.getX() + h2, cell.getY() - h2, h2));
        cellQueue.add( createCell( cell.getX() - h2, cell.getY() + h2, h2));
        cellQueue.add( createCell( cell.getX() + h2, cell.getY() + h2, h2));
        //totalCells += 4;
      }
    }
    // the farthest cell is the best approximation to the LEC center
    centerCell = farthestCell;
    // compute center point
    centerPt = new Coordinate(centerCell.getX(), centerCell.getY());
    centerPoint = factory.createPoint(centerPt);
    // compute radius point
    Coordinate[] nearestPts = obstacleDistance.nearestPoints(centerPoint);
    radiusPt = nearestPts[0].copy();
    radiusPoint = factory.createPoint(radiusPt);
  }

  /**
   * Circle obstacle over its disk: exact LEC is (centre, r).
   * A radius point is taken on the +x axis so the radius line has length r.
   */
  private void applyCertifiedCircle(double[] c) {
    centerPt = new Coordinate(c[0], c[1]);
    centerPoint = factory.createPoint(centerPt);
    radiusPt = new Coordinate(c[0] + c[2], c[1]);
    radiusPoint = factory.createPoint(radiusPt);
    centerCell = new Cell(c[0], c[1], 0.0, c[2]);
  }

  /**
   * Exact point-site centre from the three NTS.Proofs
   * ({@code grootstebozewolf/NetTopologySuite.Proofs#474}) classes.
   * Radius point is the nearest site via {@link ObstacleDistance}.
   */
  private void applyPointSiteCenter(Coordinate c) {
    usedPointSiteCandidates = true;
    centerPt = c.copy();
    centerPoint = factory.createPoint(centerPt);
    Coordinate[] nearestPts = obstacleDistance.nearestPoints(centerPoint);
    radiusPt = nearestPts[0].copy();
    radiusPoint = factory.createPoint(radiusPt);
    centerCell = new Cell(centerPt.x, centerPt.y, 0.0,
        centerPt.distance(radiusPt));
  }

  /**
   * True after {@link #getCenter()} when the point-site candidate
   * walk supplied the answer. Package-private for tests.
   *
   * @return {@code true} if the three-class enumeration was used
   */
  boolean usedPointSiteCandidates() {
    return usedPointSiteCandidates;
  }

  /**
   * Force the branch-and-bound grid even for point sites.
   * Package-private so tests can compare the exact walk to the grid
   * at a tight tolerance. Must be called before {@link #getCenter()}.
   */
  void disablePointSiteCandidates() {
    allowPointSiteCandidates = false;
  }

  /**
   * Candidates collected by the last successful three-class walk.
   * Package-private so tests can pin Family I
   * ({@code (2, 5/6)}, {@code (2,0)}, {@code (1, 3/2)},
   * {@code (3, 3/2)}, and the three sites).
   *
   * @return the list, or {@code null} if the walk did not run
   */
  List<Coordinate> lastPointSiteCandidates() {
    return lastPointSiteCandidates;
  }

  private static final double ON_EDGE_TOL = 1.0e-8;

  /**
   * NTS.Proofs ({@code grootstebozewolf/NetTopologySuite.Proofs#474})
   * three-class walk for point sites in a 2-D polygonal
   * domain. Returns {@code null} to fall through to the grid.
   * Does not filter “not ≥ 3 nearest” — F8 empty-interior maximisers
   * are two-nearest bisector × edge crossings
   * ({@code lec_candidate_completeness_boundary_edge}).
   */
  private Coordinate tryPointSiteCandidates() {
    if (!allowPointSiteCandidates) {
      return null;
    }
    if (obstacleDistance == null || boundaryPtLocater == null
        || bounds == null || bounds.getDimension() < 2) {
      return null;
    }
    if (!arePointSites(obstacles) || uniqueSiteCount(obstacles) < 2) {
      return null;
    }

    List<Coordinate> candidates = new ArrayList<Coordinate>();
    addDomainVertices(bounds, candidates);
    addVoronoiCandidates(obstacles, bounds, candidates);
    lastPointSiteCandidates = candidates;
    return pickBestPointSite(candidates);
  }

  private Coordinate pickBestPointSite(List<Coordinate> candidates) {
    Coordinate best = null;
    double bestClearance = -1.0;
    for (int i = 0; i < candidates.size(); i++) {
      Coordinate c = candidates.get(i);
      if (isInDomainCandidate(c)) {
        double d = obstacleDistance.distance(factory.createPoint(c));
        if (d > bestClearance) {
          bestClearance = d;
          best = c;
        }
      }
    }
    return best;
  }

  private boolean isInDomainCandidate(Coordinate c) {
    if (c == null || Double.isNaN(c.x) || Double.isNaN(c.y)
        || Double.isInfinite(c.x) || Double.isInfinite(c.y)) {
      return false;
    }
    return boundaryPtLocater.locate(c) != Location.EXTERIOR;
  }

  /**
   * True when every flattened obstacle is a point. Empty members are
   * skipped. Lines, polygons, arcs, and discs fail — those stay on
   * the grid. Does not consult or modify {@link ObstacleDistance}.
   */
  private static boolean arePointSites(Geometry g) {
    if (g == null || g.isEmpty()) {
      return true;
    }
    if (DiscreteHausdorffDistance.circularDisc(g) != null) {
      return false;
    }
    String type = g.getGeometryType();
    if ("CircularString".equals(type) || "CompoundCurve".equals(type)
        || "CurvePolygon".equals(type)) {
      return false;
    }
    if (g instanceof Point) {
      return true;
    }
    if (g instanceof LineString || g instanceof Polygon) {
      return false;
    }
    if (g.getNumGeometries() > 1 || isCollectionType(type)) {
      boolean any = false;
      for (int i = 0; i < g.getNumGeometries(); i++) {
        Geometry mem = g.getGeometryN(i);
        if (mem != null && !mem.isEmpty()) {
          if (!arePointSites(mem)) {
            return false;
          }
          any = true;
        }
      }
      return any;
    }
    return false;
  }

  private static boolean isCollectionType(String type) {
    return "GeometryCollection".equals(type)
        || "MultiPoint".equals(type)
        || "MultiLineString".equals(type)
        || "MultiPolygon".equals(type)
        || "MultiCurve".equals(type)
        || "MultiSurface".equals(type);
  }

  private static int uniqueSiteCount(Geometry sites) {
    Coordinate[] pts = sites.getCoordinates();
    if (pts.length == 0) {
      return 0;
    }
    Coordinate[] copy = CoordinateArrays.copyDeep(pts);
    Arrays.sort(copy);
    return new CoordinateList(copy, false).size();
  }

  /**
   * Class 3: domain-ring coordinates, including holes.
   */
  private static void addDomainVertices(Geometry domain,
      List<Coordinate> candidates) {
    if (domain instanceof Polygon) {
      addPolygonVertices((Polygon) domain, candidates);
    }
    else {
      for (int i = 0; i < domain.getNumGeometries(); i++) {
        addDomainVertices(domain.getGeometryN(i), candidates);
      }
    }
  }

  private static void addPolygonVertices(Polygon poly,
      List<Coordinate> candidates) {
    addRingVertices(poly.getExteriorRing(), candidates);
    for (int i = 0; i < poly.getNumInteriorRing(); i++) {
      addRingVertices(poly.getInteriorRingN(i), candidates);
    }
  }

  private static void addRingVertices(LineString ring,
      List<Coordinate> candidates) {
    int n = ring.getNumPoints();
    int last = n > 1 ? n - 1 : n;
    for (int i = 0; i < last; i++) {
      candidates.add(ring.getCoordinateN(i).copy());
    }
  }

  /**
   * Classes 1 and 2 from {@link VoronoiDiagramBuilder} /
   * {@link QuadEdgeSubdivision}: Delaunay circumcentres (Voronoi
   * vertices) and neighbour-bisector × domain-edge crossings.
   * Two-site / collinear sets have no triangular vertex; the edge
   * theorem still supplies the crossings. Do not drop two-nearest.
   */
  private void addVoronoiCandidates(Geometry sites, Geometry domain,
      List<Coordinate> candidates) {
    try {
      VoronoiDiagramBuilder builder = new VoronoiDiagramBuilder();
      builder.setSites(sites);
      Envelope clip = domain.getEnvelopeInternal().copy();
      clip.expandToInclude(sites.getEnvelopeInternal());
      double pad = Math.max(clip.getWidth(), clip.getHeight());
      if (pad <= 0.0) {
        pad = 1.0;
      }
      clip.expandBy(pad);
      builder.setClipEnvelope(clip);

      QuadEdgeSubdivision subdiv = builder.getSubdivision();
      List tris = subdiv.getTriangleCoordinates(false);
      for (int i = 0; i < tris.size(); i++) {
        Coordinate[] p = (Coordinate[]) tris.get(i);
        if (p != null && p.length >= 3) {
          candidates.add(Triangle.circumcentre(p[0], p[1], p[2]));
        }
      }

      List<LineSegment> domainEdges = collectDomainEdges(domain);
      Geometry delEdges = subdiv.getEdges(factory);
      for (int i = 0; i < delEdges.getNumGeometries(); i++) {
        Coordinate[] p = delEdges.getGeometryN(i).getCoordinates();
        if (p.length >= 2) {
          addBisectorCrossings(p[0], p[1], domainEdges, candidates);
        }
      }

      Geometry diagram = builder.getDiagram(factory);
      for (int i = 0; i < diagram.getNumGeometries(); i++) {
        addSegmentCrossings(polygonEdges(diagram.getGeometryN(i)),
            domainEdges, candidates);
      }
    }
    catch (RuntimeException ex) {
      if (!(ex instanceof TopologyException)
          && !(ex instanceof IllegalArgumentException)) {
        throw ex;
      }
    }
  }

  private static void addBisectorCrossings(Coordinate a, Coordinate b,
      List<LineSegment> domainEdges, List<Coordinate> candidates) {
    double dx = b.x - a.x;
    double dy = b.y - a.y;
    if (dx == 0.0 && dy == 0.0) {
      return;
    }
    double mx = (a.x + b.x) * 0.5;
    double my = (a.y + b.y) * 0.5;
    LineSegment bisector = new LineSegment(
        new Coordinate(mx - dy, my + dx),
        new Coordinate(mx + dy, my - dx));
    for (int i = 0; i < domainEdges.size(); i++) {
      LineSegment e = domainEdges.get(i);
      Coordinate hit = bisector.lineIntersection(e);
      if (hit != null && e.distance(hit) <= ON_EDGE_TOL) {
        candidates.add(hit);
      }
    }
  }

  private static void addSegmentCrossings(List<LineSegment> edgesA,
      List<LineSegment> edgesB, List<Coordinate> candidates) {
    for (int i = 0; i < edgesA.size(); i++) {
      LineSegment a = edgesA.get(i);
      for (int j = 0; j < edgesB.size(); j++) {
        Coordinate hit = a.intersection(edgesB.get(j));
        if (hit != null) {
          candidates.add(hit);
        }
      }
    }
  }

  private static List<LineSegment> collectDomainEdges(Geometry domain) {
    List<LineSegment> edges = new ArrayList<LineSegment>();
    collectDomainEdges(domain, edges);
    return edges;
  }

  private static void collectDomainEdges(Geometry domain,
      List<LineSegment> edges) {
    if (domain instanceof Polygon) {
      Polygon poly = (Polygon) domain;
      addRingEdges(poly.getExteriorRing(), edges);
      for (int i = 0; i < poly.getNumInteriorRing(); i++) {
        addRingEdges(poly.getInteriorRingN(i), edges);
      }
    }
    else {
      for (int i = 0; i < domain.getNumGeometries(); i++) {
        collectDomainEdges(domain.getGeometryN(i), edges);
      }
    }
  }

  private static List<LineSegment> polygonEdges(Geometry g) {
    List<LineSegment> edges = new ArrayList<LineSegment>();
    if (g instanceof Polygon) {
      addRingEdges(((Polygon) g).getExteriorRing(), edges);
    }
    else {
      Coordinate[] pts = g.getCoordinates();
      for (int i = 0; i + 1 < pts.length; i++) {
        edges.add(new LineSegment(pts[i], pts[i + 1]));
      }
    }
    return edges;
  }

  private static void addRingEdges(LineString ring, List<LineSegment> edges) {
    int n = ring.getNumPoints();
    for (int i = 0; i + 1 < n; i++) {
      edges.add(new LineSegment(ring.getCoordinateN(i),
          ring.getCoordinateN(i + 1)));
    }
  }

  /**
   * Certified encodings: a circular disc (or its full-circle ring) as
   * the obstacle, with either no boundary (the disk is implied) or the
   * matching disc as the containing boundary.
   */
  private static double[] certifiedCircle(Geometry obstacles, Geometry boundary) {
    double[] obs = circleOf(obstacles);
    if (obs == null) return null;
    if (boundary == null || boundary.isEmpty()) {
      return obs;
    }
    double[] bnd = DiscreteHausdorffDistance.circularDisc(boundary);
    if (bnd == null) return null;
    if (Math.hypot(obs[0] - bnd[0], obs[1] - bnd[1]) > 1.0e-9) return null;
    if (Math.abs(obs[2] - bnd[2]) > 1.0e-9) return null;
    return obs;
  }

  private static double[] circleOf(Geometry g) {
    double[] d = DiscreteHausdorffDistance.circularDisc(g);
    if (d != null) return d;
    return DiscreteHausdorffDistance.circularRing(g);
  }

  //-- empirically determined to balance accuracy and speed
  private static final double AUTO_TOLERANCE_FRACTION = 0.001;
  
  /**
   * Tests whether a cell may contain the circle center,
   * and thus should be refined (split into subcells 
   * to be investigated further.)
   * 
   * @param cell the cell to test
   * @return true if the cell might contain the circle center
   */
  private boolean mayContainCircleCenter(Cell cell) {
    /**
     * Every point in the cell lies outside the boundary,
     * so they cannot be the center point
     */
    if (cell.isFullyOutside())
      return false;
    
    /**
     * The tolerance can be automatically determined 
     * as a fraction of the current farthest distance.
     * For a very small actual MIC distance this may cause many iterations, 
     * but the iter limit prevents an infinite loop
     */
   double requiredTol = tolerance > 0 
       ? tolerance
       : farthestCell.getDistance() * AUTO_TOLERANCE_FRACTION;
   
    /**
     * The cell is outside, but overlaps the boundary
     * so it may contain a point which should be checked.
     * This is only the case if the potential overlap distance 
     * is larger than the tolerance.
     */
    if (cell.isOutside()) {
      boolean isOverlapSignificant = cell.getMaxDistance() > requiredTol;
      return isOverlapSignificant;
    }
    
    /**
     * Cell is inside the boundary. It may contain the center
     * if the maximum possible distance is greater than the current distance
     * (up to tolerance).
     */
    double potentialIncrease = cell.getMaxDistance() - farthestCell.getDistance();
    return potentialIncrease > requiredTol;
  }

  /**
   * Initializes the queue with a cell covering 
   * the extent of the area.
   * 
   * @param env the area extent to cover
   * @param cellQueue the queue to initialize
   */
  private void createInitialGrid(Envelope env, PriorityQueue<Cell> cellQueue) {
    double cellSize = Math.max(env.getWidth(), env.getHeight());
    double hSide = cellSize / 2.0;

    // Check for flat collapsed input and if so short-circuit
    // Result will just be centroid
    if (cellSize == 0) return;
    
    Coordinate centre = env.centre();
    cellQueue.add(createCell(centre.x, centre.y, hSide));   
  }

  private Cell createCell(double x, double y, double h) {
    return new Cell(x, y, h, distanceToConstraints(x, y));
  }

  // create a cell centered on area centroid
  private Cell createCentroidCell(Geometry geom) {
    Point p = geom.getCentroid();
    return new Cell(p.getX(), p.getY(), 0, distanceToConstraints(p));
  }

  /**
   * A square grid cell centered on a given point 
   * with a given side half-length, 
   * and having a given distance from the center point to the constraints.
   * The maximum possible distance from any point in the cell to the
   * constraints can be computed.
   * This is used as the ordering and upper-bound function in
   * the branch-and-bound algorithm. 
   */
  private static class Cell implements Comparable<Cell> {

    private static final double SQRT2 = 1.4142135623730951;

    private double x;
    private double y;
    private double hSide;
    private double distance;
    private double maxDist;

    Cell(double x, double y, double hSide, double distanceToConstraints) {
      this.x = x; // cell center x
      this.y = y; // cell center y
      this.hSide = hSide; // half the cell size

      // the distance from cell center to constraints
      distance = distanceToConstraints;

      /**
       * The maximum possible distance to the constraints for points in this cell
       * is the center distance plus the radius (half the diagonal length).
       */
      this.maxDist = distance + hSide * SQRT2;
    }

    public boolean isFullyOutside() {
      return getMaxDistance() < 0;
    }

    public boolean isOutside() {
      return distance < 0;
    }

    public double getMaxDistance() {
      return maxDist;
    }

    public double getDistance() {
      return distance;
    }

    public double getHSide() {
      return hSide;
    }

    public double getX() {
      return x;
    }

    public double getY() {
      return y;
    }
    
    /**
     * For maximum efficieny sort the PriorityQueue with largest maxDistance at front.
     * Since Java PQ sorts least-first, need to invert the comparison
     */
    public int compareTo(Cell o) {
      return -Double.compare(maxDist, o.maxDist);
    }
  }

}

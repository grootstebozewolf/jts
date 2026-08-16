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
package org.locationtech.jts.algorithm.construct;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

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
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.geom.TopologyException;
import org.locationtech.jts.geom.Triangle;
import org.locationtech.jts.triangulate.DelaunayTriangulationBuilder;
import org.locationtech.jts.triangulate.VoronoiDiagramBuilder;

/**
 * Point-site LEC candidate enumeration for a 2-D polygonal domain.
 * <p>
 * NetTopologySuite.Proofs #474 ({@code LECCandidateComplete.v}) proves
 * that a maximiser of nearest-site clearance over a bounded domain is
 * one of three classes:
 * <ul>
 * <li>an interior Voronoi vertex (≥ 3 nearest sites) —
 *     {@code lec_candidate_completeness_interior}</li>
 * <li>a Voronoi-edge × domain-boundary crossing (≥ 2 nearest sites) —
 *     {@code lec_candidate_completeness_boundary_edge}</li>
 * <li>a domain vertex (the only remaining boundary points)</li>
 * </ul>
 * Both headlines are negation-form corollaries of
 * {@code improvement_kernel} on dist². This class walks exactly those
 * three classes and returns the max-clearance candidate.
 * <p>
 * F8 ({@code f8_interiority_load_bearing}): dropping the interiority
 * premise is false. Sites (0,0) and (2,0) on their connecting segment
 * have midpoint (1,0) radius 1 with exactly two nearest sites; the
 * naive two-nearest direction degenerates to zero (antipodal). This
 * enumerator therefore keeps all three classes. The two-point convex
 * hull is a LineString, not a polygon — callers must not pretend it
 * is a domain; pin F8 with an explicit thin polygonal domain.
 * <p>
 * Package-private: not a user-facing algorithm. Non-point obstacles
 * (lines, polygons, arcs, discs, mixed) stay on the LEC grid.
 * Weighted / Apollonius disc-site candidates are not this rung.
 *
 * @author JTS
 */
class LargestEmptyCirclePointSites {

  private static final double ON_EDGE_TOL = 1.0e-8;

  private LargestEmptyCirclePointSites() {
  }

  /**
   * Max-clearance candidate among the three proven classes, or
   * {@code null} when this path does not apply (not points-only, fewer
   * than two unique sites, or no 2-D polygonal domain).
   *
   * @param obstacles point-site obstacles
   * @param domain a polygonal domain of dimension ≥ 2
   * @param obstacleDistance typed distance (Euclidean to points)
   * @param locator point-in-domain test
   * @param factory geometry factory
   * @return the best centre, or {@code null}
   */
  static Coordinate findCenter(Geometry obstacles, Geometry domain,
      ObstacleDistance obstacleDistance, IndexedPointInAreaLocator locator,
      GeometryFactory factory) {
    if (obstacleDistance == null || !obstacleDistance.isPointSitesOnly()) {
      return null;
    }
    if (locator == null || domain == null || domain.getDimension() < 2) {
      return null;
    }
    if (uniqueSiteCount(obstacles) < 2) {
      return null;
    }

    List<Coordinate> candidates = new ArrayList<Coordinate>();
    addDomainVertices(domain, candidates);
    addDelaunayCircumcentres(obstacles, factory, candidates);
    addNeighbourBisectorCrossings(obstacles, domain, factory, candidates);
    addVoronoiDiagramCandidates(obstacles, domain, factory, candidates);

    return pickBest(candidates, obstacleDistance, locator, factory);
  }

  private static Coordinate pickBest(List<Coordinate> candidates,
      ObstacleDistance obstacleDistance, IndexedPointInAreaLocator locator,
      GeometryFactory factory) {
    Coordinate best = null;
    double bestClearance = -1.0;
    for (int i = 0; i < candidates.size(); i++) {
      Coordinate c = candidates.get(i);
      if (c != null
          && !Double.isNaN(c.x) && !Double.isNaN(c.y)
          && !Double.isInfinite(c.x) && !Double.isInfinite(c.y)
          && locator.locate(c) != Location.EXTERIOR) {
        double d = obstacleDistance.distance(factory.createPoint(c));
        if (d > bestClearance) {
          bestClearance = d;
          best = c;
        }
      }
    }
    return best;
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
   * Class 1: Delaunay circumcentres (true Voronoi vertices).
   * Two-site and collinear sets produce no triangles; class 2 still
   * supplies the bisector × boundary crossings.
   */
  private static void addDelaunayCircumcentres(Geometry obstacles,
      GeometryFactory factory, List<Coordinate> candidates) {
    DelaunayTriangulationBuilder dt = new DelaunayTriangulationBuilder();
    dt.setSites(obstacles);
    Geometry tris = dt.getTriangles(factory);
    for (int i = 0; i < tris.getNumGeometries(); i++) {
      Coordinate[] p = tris.getGeometryN(i).getCoordinates();
      if (p.length >= 3) {
        candidates.add(Triangle.circumcentre(p[0], p[1], p[2]));
      }
    }
  }

  /**
   * Class 2: infinite perp-bisector of each Delaunay neighbour pair
   * crossed with every domain edge. Extra (non-Voronoi-edge) hits are
   * evaluated and lose on clearance; two-site / collinear sets have
   * no Voronoi vertex and live entirely in this class plus vertices.
   */
  private static void addNeighbourBisectorCrossings(Geometry obstacles,
      Geometry domain, GeometryFactory factory,
      List<Coordinate> candidates) {
    List<LineSegment> domainEdges = collectDomainEdges(domain);
    DelaunayTriangulationBuilder dt = new DelaunayTriangulationBuilder();
    dt.setSites(obstacles);
    Geometry edges = dt.getEdges(factory);
    for (int i = 0; i < edges.getNumGeometries(); i++) {
      Coordinate[] p = edges.getGeometryN(i).getCoordinates();
      if (p.length >= 2) {
        addBisectorCrossings(p[0], p[1], domainEdges, candidates);
      }
    }
  }

  /**
   * VoronoiDiagramBuilder cells: vertices in the clip plus cell-edge
   * × domain-edge hits. Clip artefacts outside the domain are dropped
   * by the point-in-domain filter. Degenerate diagrams fall through
   * to the Delaunay classes already collected.
   */
  private static void addVoronoiDiagramCandidates(Geometry obstacles,
      Geometry domain, GeometryFactory factory,
      List<Coordinate> candidates) {
    try {
      VoronoiDiagramBuilder builder = new VoronoiDiagramBuilder();
      builder.setSites(obstacles);
      Envelope clip = domain.getEnvelopeInternal().copy();
      clip.expandToInclude(obstacles.getEnvelopeInternal());
      double pad = Math.max(clip.getWidth(), clip.getHeight());
      if (pad <= 0.0) {
        pad = 1.0;
      }
      clip.expandBy(pad);
      builder.setClipEnvelope(clip);
      Geometry diagram = builder.getDiagram(factory);
      List<LineSegment> domainEdges = collectDomainEdges(domain);
      for (int i = 0; i < diagram.getNumGeometries(); i++) {
        Geometry cell = diagram.getGeometryN(i);
        Coordinate[] pts = cell.getCoordinates();
        for (int j = 0; j < pts.length; j++) {
          candidates.add(pts[j]);
        }
        addSegmentCrossings(polygonEdges(cell), domainEdges, candidates);
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

  private static int uniqueSiteCount(Geometry obstacles) {
    Coordinate[] pts = obstacles.getCoordinates();
    if (pts.length == 0) {
      return 0;
    }
    Coordinate[] copy = CoordinateArrays.copyDeep(pts);
    Arrays.sort(copy);
    CoordinateList unique = new CoordinateList(copy, false);
    return unique.size();
  }
}

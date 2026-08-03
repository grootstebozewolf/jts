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
package org.locationtech.jtstest.testbuilder.ui.tools;

import java.awt.Shape;
import java.awt.geom.GeneralPath;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.locationtech.jts.algorithm.Orientation;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jtstest.testbuilder.model.GeometryType;

/**
 * Incremental TIN drawing tool ("ingress route" UX).
 *
 * <h3>Phases</h3>
 * <ul>
 *   <li><b>Seed</b>: the first three left-clicks place the initial
 *       triangular patch.</li>
 *   <li><b>Incremental</b>: every subsequent left-click outside the
 *       current triangulation creates one or more new triangles by
 *       fanning from the click point to every <em>visible</em> boundary
 *       edge of the current triangulation.</li>
 *   <li><b>Finish</b>: double-click commits the accumulated patches as
 *       a {@link org.locationtech.jts.geom.curve.Tin}.</li>
 * </ul>
 *
 * <h3>Visibility test</h3>
 * A boundary edge is "visible" from a point P if P lies on the opposite
 * side of the edge's supporting line from the third vertex of the
 * boundary edge's owner triangle. This is O(1) per edge, correct for
 * convex TINs, and at worst over-inclusive for concave TINs (some
 * edges blocked by occluding patches will still appear visible — the
 * user can simply avoid clicking those positions in v1).
 *
 * <h3>Z handling</h3>
 * All click-derived coordinates carry {@code Z = Double.NaN} (JTS
 * default for unset Z). 2.5D elevation can be assigned post-hoc.
 *
 * <h3>Forbidden hover</h3>
 * If the cursor is inside any committed triangle, no preview lines are
 * drawn — visually communicating "click does nothing here" without a
 * custom cursor image.
 */
public class TinTool extends AbstractStreamDrawTool {

  private static TinTool singleton = null;

  public static TinTool getInstance() {
    if (singleton == null)
      singleton = new TinTool();
    return singleton;
  }

  private TinTool() {
  }

  @Override
  protected int getGeometryType() {
    return GeometryType.TIN;
  }

  @Override
  protected Shape getShape() {
    TriangulationState state = replay();
    GeneralPath path = new GeneralPath();

    // Render every committed triangle as a closed outline.
    for (Coordinate[] tri : state.triangles()) {
      Point2D p0 = toView(tri[0]);
      Point2D p1 = toView(tri[1]);
      Point2D p2 = toView(tri[2]);
      path.moveTo((float) p0.getX(), (float) p0.getY());
      path.lineTo((float) p1.getX(), (float) p1.getY());
      path.lineTo((float) p2.getX(), (float) p2.getY());
      path.closePath();
    }

    // Render the in-progress preview.
    if (state.triangles().isEmpty()) {
      // Seed phase: 1 or 2 captured + tentative -> polyline hint.
      List<Coordinate> seed = state.seedingPoints();
      if (!seed.isEmpty()) {
        Point2D first = toView(seed.get(0));
        path.moveTo((float) first.getX(), (float) first.getY());
        for (int i = 1; i < seed.size(); i++) {
          Point2D q = toView(seed.get(i));
          path.lineTo((float) q.getX(), (float) q.getY());
        }
        if (tentativeCoordinate != null) {
          Point2D q = toView(tentativeCoordinate);
          path.lineTo((float) q.getX(), (float) q.getY());
        }
      }
    } else if (tentativeCoordinate != null
               && !state.containsInAnyPatch(tentativeCoordinate)) {
      // Incremental phase + cursor outside: fan preview to visible edges.
      Point2D pp = toView(tentativeCoordinate);
      for (Edge e : state.visibleEdgesFrom(tentativeCoordinate)) {
        Point2D pa = toView(e.a);
        Point2D pb = toView(e.b);
        path.moveTo((float) pp.getX(), (float) pp.getY());
        path.lineTo((float) pa.getX(), (float) pa.getY());
        path.moveTo((float) pp.getX(), (float) pp.getY());
        path.lineTo((float) pb.getX(), (float) pb.getY());
      }
    }
    // (Cursor inside a triangle: no preview lines drawn -> "forbidden".)

    drawVertexMarkers(path, state);
    return path;
  }

  @Override
  protected void bandFinished() throws Exception {
    if (panel().getModel() == null) return;
    panel().getGeomModel().setGeometryType(getGeometryType());

    TriangulationState state = replay();
    if (state.triangles().isEmpty()) return;

    List<Coordinate> flat = new ArrayList<Coordinate>(state.triangles().size() * 3);
    for (Coordinate[] tri : state.triangles()) {
      flat.add(tri[0]);
      flat.add(tri[1]);
      flat.add(tri[2]);
    }
    geomModel().addComponent(flat);
    panel().updateGeom();
  }

  /**
   * Re-derive the current triangulation by replaying every captured
   * click through {@link TriangulationState}. Replay is O(n &times; E)
   * where E is the boundary-edge count at each step — well under a
   * millisecond for the triangle counts a human can place in one
   * drawing session.
   */
  private TriangulationState replay() {
    TriangulationState state = new TriangulationState();
    for (Object o : getCoordinates()) {
      state.add((Coordinate) o);
    }
    return state;
  }

  /** Distinct vertex markers across all triangles + seed phase. */
  private void drawVertexMarkers(GeneralPath path, TriangulationState state) {
    Set<String> seen = new HashSet<String>();
    for (Coordinate[] tri : state.triangles()) {
      for (Coordinate c : tri) drawMarkerOnce(path, c, seen);
    }
    for (Coordinate c : state.seedingPoints()) drawMarkerOnce(path, c, seen);
  }

  private void drawMarkerOnce(GeneralPath path, Coordinate c, Set<String> seen) {
    String key = c.x + "," + c.y;
    if (!seen.add(key)) return;
    Point2D p = toView(c);
    path.moveTo((float) (p.getX() - 2), (float) (p.getY() - 2));
    path.lineTo((float) (p.getX() + 2), (float) (p.getY() - 2));
    path.lineTo((float) (p.getX() + 2), (float) (p.getY() + 2));
    path.lineTo((float) (p.getX() - 2), (float) (p.getY() + 2));
    path.lineTo((float) (p.getX() - 2), (float) (p.getY() - 2));
  }

  // ===========================================================================
  // TriangulationState — derived from the captured-clicks list on every replay.
  // ===========================================================================

  static final class TriangulationState {
    private final List<Coordinate> seeding = new ArrayList<Coordinate>();
    private final List<Coordinate[]> tris = new ArrayList<Coordinate[]>();

    void add(Coordinate p) {
      if (tris.isEmpty() && seeding.size() < 3) {
        seeding.add(p);
        if (seeding.size() == 3) {
          tris.add(new Coordinate[] { seeding.get(0), seeding.get(1), seeding.get(2) });
          seeding.clear();
        }
        return;
      }
      // Incremental: silently ignore points inside an existing triangle
      // (matches the "forbidden hover -> no preview" UX).
      if (containsInAnyPatch(p)) return;
      List<Edge> visible = visibleEdgesFrom(p);
      for (Edge e : visible) {
        // Skip degenerate (colinear) triangles.
        if (Orientation.index(e.a, e.b, p) == Orientation.COLLINEAR) continue;
        tris.add(new Coordinate[] { e.a, e.b, p });
      }
    }

    List<Coordinate[]> triangles() { return tris; }
    List<Coordinate> seedingPoints() { return seeding; }

    boolean containsInAnyPatch(Coordinate p) {
      for (Coordinate[] tri : tris) {
        if (pointInTriangle(p, tri[0], tri[1], tri[2])) return true;
      }
      return false;
    }

    /** Boundary edges of the current triangulation, paired with the
     *  third vertex of the (single) triangle that owns them. */
    List<Edge> boundaryEdges() {
      List<Edge> all = new ArrayList<Edge>(tris.size() * 3);
      for (Coordinate[] tri : tris) {
        all.add(new Edge(tri[0], tri[1], tri[2]));
        all.add(new Edge(tri[1], tri[2], tri[0]));
        all.add(new Edge(tri[2], tri[0], tri[1]));
      }
      List<Edge> boundary = new ArrayList<Edge>();
      for (int i = 0; i < all.size(); i++) {
        Edge ei = all.get(i);
        boolean sharedWithSibling = false;
        for (int j = 0; j < all.size(); j++) {
          if (i == j) continue;
          if (ei.sameUnordered(all.get(j))) {
            sharedWithSibling = true;
            break;
          }
        }
        if (!sharedWithSibling) boundary.add(ei);
      }
      return boundary;
    }

    List<Edge> visibleEdgesFrom(Coordinate p) {
      List<Edge> out = new ArrayList<Edge>();
      for (Edge e : boundaryEdges()) {
        int sideThird = Orientation.index(e.a, e.b, e.ownerThird);
        int sideP = Orientation.index(e.a, e.b, p);
        // Visible if P is on the opposite side of the edge from the
        // owning triangle's interior. Colinear -> skip (degenerate).
        if (sideP != Orientation.COLLINEAR && sideP != sideThird) {
          out.add(e);
        }
      }
      return out;
    }

    private static boolean pointInTriangle(Coordinate p, Coordinate a, Coordinate b, Coordinate c) {
      int o1 = Orientation.index(a, b, p);
      int o2 = Orientation.index(b, c, p);
      int o3 = Orientation.index(c, a, p);
      // Inside (or on edge): same sign across all three (zeros allowed).
      return (o1 >= 0 && o2 >= 0 && o3 >= 0) || (o1 <= 0 && o2 <= 0 && o3 <= 0);
    }
  }

  /** Directed edge with reference to the third vertex of its owning
   *  triangle (used by the visibility test). */
  static final class Edge {
    final Coordinate a, b, ownerThird;

    Edge(Coordinate a, Coordinate b, Coordinate ownerThird) {
      this.a = a;
      this.b = b;
      this.ownerThird = ownerThird;
    }

    boolean sameUnordered(Edge other) {
      return (a.equals2D(other.a) && b.equals2D(other.b))
          || (a.equals2D(other.b) && b.equals2D(other.a));
    }
  }
}

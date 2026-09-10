/*
 * Copyright (c) 2026 Jeroen Tech Solutions Ltd / JTS contributors.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * and Eclipse Distribution License v. 1.0 which accompanies this distribution.
 * The Eclipse Public License is available at http://www.eclipse.org/legal/epl-v20.html
 * and the Eclipse Distribution License is available at
 *
 * http://www.eclipse.org/org/documents/edl-v10.php.
 */
package org.locationtech.jts.geom.curve;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryCollection;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;

/**
 * COV (#1195): coverage union for hole-free {@link CurvePolygon}s that
 * dissolve shared boundary members while keeping exterior
 * {@link CircularString} / line members intact.
 * <p>
 * Chainsaw {@code CoverageUnion} densifies first and cannot emit
 * CIRCULARSTRING edges. This laser walks structural shell members,
 * counts each edge (forward or reverse) across the coverage, drops
 * count-2 shared edges, and stitches the remaining count-1 edges into
 * one or more {@link CurvePolygon} shells.
 * <p>
 * Scope: hole-free CurvePolygon / MultiSurface coverages whose shells
 * are {@link CircularString} or {@link CompoundCurve}. Invalid coverages
 * (overlapping interiors, mismatched shared edges) fall through to
 * {@code null} so the caller can use the stock densify path.
 */
public final class CurveCoverageUnion {

  private CurveCoverageUnion() {
  }

  /**
   * Unions a curve coverage, or returns {@code null} when the input is
   * not a supported hole-free CurvePolygon coverage.
   *
   * @param coverage a GeometryCollection / MultiSurface of CurvePolygons
   * @return CurvePolygon or MultiSurface, or {@code null} to fall through
   */
  public static Geometry union(Geometry coverage) {
    if (coverage == null || coverage.isEmpty()) {
      return coverage;
    }
    List<CurvePolygon> polys = extractCurvePolygons(coverage);
    if (polys == null || polys.isEmpty()) {
      return null;
    }
    if (polys.size() == 1) {
      return polys.get(0).copy();
    }

    List<EdgeUse> uses = new ArrayList<EdgeUse>();
    for (int i = 0; i < polys.size(); i++) {
      CurvePolygon cp = polys.get(i);
      if (cp.getNumInteriorRing() > 0) {
        return null; // holes out of scope
      }
      LineString shell = cp.getExteriorCurve();
      if (shell == null) {
        return null;
      }
      List<LineString> members = shellMembers(shell);
      if (members == null || members.isEmpty()) {
        return null;
      }
      for (int m = 0; m < members.size(); m++) {
        uses.add(new EdgeUse(members.get(m), i));
      }
    }

    Map<String, List<EdgeUse>> byKey = new HashMap<String, List<EdgeUse>>();
    for (int i = 0; i < uses.size(); i++) {
      EdgeUse u = uses.get(i);
      String key = u.canonicalKey();
      List<EdgeUse> bucket = byKey.get(key);
      if (bucket == null) {
        bucket = new ArrayList<EdgeUse>();
        byKey.put(key, bucket);
      }
      bucket.add(u);
    }

    List<LineString> exterior = new ArrayList<LineString>();
    for (Iterator<List<EdgeUse>> it = byKey.values().iterator(); it.hasNext();) {
      List<EdgeUse> bucket = it.next();
      if (bucket.size() == 1) {
        exterior.add(bucket.get(0).edge);
      }
      else if (bucket.size() == 2) {
        // shared interior edge — dissolve
        continue;
      }
      else {
        // odd multiplicity / invalid coverage
        return null;
      }
    }

    if (exterior.isEmpty()) {
      return null;
    }

    GeometryFactory gf = coverage.getFactory();
    if (!(gf instanceof CurveGeometryFactory)) {
      gf = new CurveGeometryFactory(gf.getPrecisionModel(), gf.getSRID());
    }
    CurveGeometryFactory cf = (CurveGeometryFactory) gf;

    List<LineString> rings = stitchRings(exterior);
    if (rings == null || rings.isEmpty()) {
      return null;
    }
    if (rings.size() == 1) {
      return cf.createCurvePolygon(rings.get(0), null);
    }
    CurvePolygon[] cps = new CurvePolygon[rings.size()];
    for (int i = 0; i < rings.size(); i++) {
      cps[i] = cf.createCurvePolygon(rings.get(i), null);
    }
    return cf.createMultiSurface(cps);
  }

  private static List<CurvePolygon> extractCurvePolygons(Geometry g) {
    List<CurvePolygon> out = new ArrayList<CurvePolygon>();
    if (g instanceof CurvePolygon) {
      out.add((CurvePolygon) g);
      return out;
    }
    if (g instanceof MultiSurface || g instanceof GeometryCollection) {
      for (int i = 0; i < g.getNumGeometries(); i++) {
        Geometry n = g.getGeometryN(i);
        if (!(n instanceof CurvePolygon)) {
          return null;
        }
        out.add((CurvePolygon) n);
      }
      return out;
    }
    return null;
  }

  private static List<LineString> shellMembers(LineString shell) {
    List<LineString> out = new ArrayList<LineString>();
    if (shell instanceof CompoundCurve) {
      CompoundCurve cc = (CompoundCurve) shell;
      for (int i = 0; i < cc.getNumMembers(); i++) {
        out.add(cc.getMemberN(i));
      }
      return out;
    }
    if (shell instanceof CircularString || shell instanceof LineString) {
      out.add(shell);
      return out;
    }
    return null;
  }

  /**
   * Greedy stitch of directed edges into closed rings. Prefers the
   * member orientation as stored; reverses a copy when only the reverse
   * endpoint matches.
   */
  private static List<LineString> stitchRings(List<LineString> edges) {
    List<LineString> remaining = new ArrayList<LineString>(edges);
    List<LineString> rings = new ArrayList<LineString>();
    while (!remaining.isEmpty()) {
      List<LineString> ring = new ArrayList<LineString>();
      LineString first = remaining.remove(0);
      ring.add(first);
      Coordinate start = first.getCoordinateN(0);
      Coordinate cur = first.getCoordinateN(first.getNumPoints() - 1);
      boolean progressed = true;
      while (progressed && !cur.equals2D(start)) {
        progressed = false;
        for (int i = 0; i < remaining.size(); i++) {
          LineString cand = remaining.get(i);
          Coordinate c0 = cand.getCoordinateN(0);
          Coordinate c1 = cand.getCoordinateN(cand.getNumPoints() - 1);
          if (cur.equals2D(c0)) {
            ring.add(cand);
            remaining.remove(i);
            cur = c1;
            progressed = true;
            break;
          }
          if (cur.equals2D(c1)) {
            ring.add(reverseCopy(cand));
            remaining.remove(i);
            cur = c0;
            progressed = true;
            break;
          }
        }
      }
      if (!cur.equals2D(start) || ring.isEmpty()) {
        return null;
      }
      rings.add(asShell(ring, first.getFactory()));
    }
    return rings;
  }

  private static LineString asShell(List<LineString> members, GeometryFactory f) {
    if (members.size() == 1) {
      return members.get(0);
    }
    CurveGeometryFactory cf = f instanceof CurveGeometryFactory
        ? (CurveGeometryFactory) f
        : new CurveGeometryFactory(f.getPrecisionModel(), f.getSRID());
    return cf.createCompoundCurve(members.toArray(new LineString[0]));
  }

  private static LineString reverseCopy(LineString ls) {
    Coordinate[] pts = ls.getCoordinates();
    Coordinate[] rev = new Coordinate[pts.length];
    for (int i = 0; i < pts.length; i++) {
      rev[i] = pts[pts.length - 1 - i].copy();
    }
    if (ls instanceof CircularString) {
      return new CircularString(
          ls.getFactory().getCoordinateSequenceFactory().create(rev),
          ls.getFactory());
    }
    return ls.getFactory().createLineString(rev);
  }

  private static final class EdgeUse {
    final LineString edge;
    final int polyIndex;

    EdgeUse(LineString edge, int polyIndex) {
      this.edge = edge;
      this.polyIndex = polyIndex;
    }

    String canonicalKey() {
      Coordinate[] pts = edge.getCoordinates();
      if (pts.length == 0) {
        return "empty";
      }
      boolean forward = true;
      if (pts.length >= 2) {
        Coordinate a = pts[0];
        Coordinate b = pts[pts.length - 1];
        if (b.compareTo(a) < 0) {
          forward = false;
        }
        else if (b.equals2D(a) && pts.length >= 3) {
          // closed ring fragment — keep native order
          forward = true;
        }
      }
      StringBuilder sb = new StringBuilder();
      sb.append(edge instanceof CircularString ? "A:" : "L:");
      if (forward) {
        for (int i = 0; i < pts.length; i++) {
          appendCoord(sb, pts[i]);
        }
      }
      else {
        for (int i = pts.length - 1; i >= 0; i--) {
          appendCoord(sb, pts[i]);
        }
      }
      return sb.toString();
    }

    private static void appendCoord(StringBuilder sb, Coordinate c) {
      sb.append(c.x).append(',').append(c.y).append(';');
    }
  }
}

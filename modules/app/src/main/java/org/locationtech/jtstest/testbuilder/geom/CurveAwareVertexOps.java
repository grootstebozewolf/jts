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
/*
 * AI Disclosure (Eclipse Foundation GenAI Guidelines):
 * AI-generated portions are dedicated to CC0-1.0; human-reviewed.
 *
 * SPDX-License-Identifier: (EPL-2.0 OR EDL-1.0) AND CC0-1.0
 * Assisted-by: Claude (Opus-4.7)
 */
package org.locationtech.jtstest.testbuilder.geom;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.CoordinateSequence;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.curve.CircularString;
import org.locationtech.jts.geom.curve.ClothoidSegment;
import org.locationtech.jts.geom.curve.CompoundCurve;
import org.locationtech.jts.geom.curve.CurveGeometryFactory;

/**
 * Curve-aware vertex Move / Insert / Delete used by the JTSTestBuilder
 * vertex tools so that interactive edits on a {@link CompoundCurve}
 * (or its curved members) preserve member structure and subtypes.
 *
 * <p>The generic {@link org.locationtech.jts.geom.util.GeometryEditor}
 * descends only into Point/LineString/Polygon/GeometryCollection, and
 * its {@code CoordinateOperation} flattens any LineString subclass
 * back into a plain LineString via {@code factory.createLineString(...)}.
 * That decomposes CompoundCurve members and strips CircularString /
 * ClothoidSegment subtypes — destructive in the testbuilder. These
 * helpers detect the curved cases and route them through the right
 * factory methods. Operations that would degrade the geometry (e.g.
 * deleting a control point of an arc) refuse silently by returning
 * the input unchanged.
 *
 * <p>Each entry point returns {@code null} when the input is not a
 * curved type so callers fall back to the generic {@link
 * org.locationtech.jts.geom.util.GeometryEditor} path.
 */
public final class CurveAwareVertexOps {
  private CurveAwareVertexOps() {}

  /** Move semantics:
   *  <ul>
   *  <li>{@code CompoundCurve}: every member whose coords contain
   *      {@code from} is updated subtype-preservingly. A junction
   *      vertex is naturally updated in both neighbouring members
   *      because each holds its own copy of that coord.
   *  <li>{@code CircularString}: rebuilt via {@link CurveGeometryFactory}.
   *  <li>{@code ClothoidSegment}: only the start (anchor) coord is
   *      movable — the end is analytically derived from κ₀, κ₁, L,
   *      θ_start; refuse otherwise.
   *  </ul>
   *  Returns {@code null} for non-curved input so the caller can
   *  fall back to the generic editor. */
  public static Geometry move(Geometry geom, Coordinate from, Coordinate to) {
    if (geom instanceof CompoundCurve) {
      return moveOnCompoundCurve((CompoundCurve) geom, from, to);
    }
    if (geom instanceof CircularString) {
      return moveOnCircularString((CircularString) geom, from, to);
    }
    if (geom instanceof ClothoidSegment) {
      return moveOnClothoid((ClothoidSegment) geom, from, to);
    }
    return null;
  }

  /** Insert is meaningful only on plain LineString members of a
   *  CompoundCurve. Inserting into an arc/clothoid breaks its analytic
   *  definition; we refuse and return the input unchanged. Returns
   *  {@code null} for non-curved input. */
  public static Geometry insert(Geometry parent, LineString component,
                                int flatSegIndex, Coordinate newVertex) {
    if (parent instanceof CompoundCurve && component == parent) {
      return insertOnCompoundCurve((CompoundCurve) parent, flatSegIndex, newVertex);
    }
    if (parent instanceof CircularString || parent instanceof ClothoidSegment) {
      return parent;
    }
    return null;
  }

  /** Delete refuses on:
   *  <ul>
   *  <li>junction vertices of a CompoundCurve (would tear the chain),
   *  <li>any vertex of a CircularString or ClothoidSegment member
   *      (the 2n+1 / start+end pattern is minimal — deleting any
   *      one of them degenerates the geometry).
   *  </ul>
   *  Internal vertices of plain LineString members are deleted while
   *  keeping the rest of the CompoundCurve intact. Returns {@code null}
   *  for non-curved input. */
  public static Geometry delete(Geometry parent, LineString component, int flatVertexIndex) {
    if (parent instanceof CompoundCurve && component == parent) {
      return deleteOnCompoundCurve((CompoundCurve) parent, flatVertexIndex);
    }
    if (parent instanceof CircularString) {
      return parent;
    }
    if (parent instanceof ClothoidSegment) {
      return parent;
    }
    return null;
  }

  // -- move ----------------------------------------------------------

  private static CompoundCurve moveOnCompoundCurve(CompoundCurve cc, Coordinate from, Coordinate to) {
    LineString[] members = cc.getMembers();
    boolean changed = false;
    for (int i = 0; i < members.length; i++) {
      LineString updated = moveOnMember(members[i], from, to);
      if (updated != members[i]) {
        members[i] = updated;
        changed = true;
      }
    }
    if (!changed) return cc;
    return new CompoundCurve(members, cc.getFactory());
  }

  /** Subtype-preserving member update. Returns the same instance if no
   *  matching coord is present or the member type refuses the move. */
  private static LineString moveOnMember(LineString m, Coordinate from, Coordinate to) {
    if (m instanceof ClothoidSegment) {
      return moveOnClothoid((ClothoidSegment) m, from, to);
    }
    if (m instanceof CircularString) {
      return moveOnCircularString((CircularString) m, from, to);
    }
    Coordinate[] cc = m.getCoordinates();
    Coordinate[] nc = replaceMatching(cc, from, to);
    if (nc == cc) return m;
    return m.getFactory().createLineString(nc);
  }

  private static CircularString moveOnCircularString(CircularString cs, Coordinate from, Coordinate to) {
    Coordinate[] cc = cs.getCoordinates();
    Coordinate[] nc = replaceMatching(cc, from, to);
    if (nc == cc) return cs;
    GeometryFactory gf = cs.getFactory();
    CoordinateSequence seq = gf.getCoordinateSequenceFactory().create(nc);
    if (gf instanceof CurveGeometryFactory) {
      return ((CurveGeometryFactory) gf).createCircularString(seq);
    }
    // Unreachable in the testbuilder (the WKT reader always uses a
    // CurveGeometryFactory), but keeps the helper safe in isolation.
    return new CircularString(seq, gf);
  }

  private static ClothoidSegment moveOnClothoid(ClothoidSegment cs, Coordinate from, Coordinate to) {
    // Re-anchor only when the user grabs the start point. The end is
    // analytic; moving it would have to change κ or L and is ambiguous,
    // so refuse and return the input unchanged.
    if (!from.equals2D(cs.getStartCoordinate())) return cs;
    return new ClothoidSegment(new Coordinate(to), cs.getStartTangent(),
        cs.getStartKappa(), cs.getEndKappa(), cs.getLength(), cs.getFactory());
  }

  private static Coordinate[] replaceMatching(Coordinate[] coords, Coordinate from, Coordinate to) {
    boolean any = false;
    for (int i = 0; i < coords.length; i++) {
      if (coords[i].equals2D(from)) { any = true; break; }
    }
    if (!any) return coords;
    Coordinate[] out = new Coordinate[coords.length];
    for (int i = 0; i < coords.length; i++) {
      out[i] = coords[i].equals2D(from) ? new Coordinate(to) : new Coordinate(coords[i]);
    }
    return out;
  }

  // -- insert / delete ----------------------------------------------

  /** Maps a flat *segment* index of a CompoundCurve to {@code (memberIdx,
   *  localSegIdx)}. Segments never span junctions because junctions are
   *  vertices, not segments — each segment lives wholly inside one
   *  member. Returns {@code null} if out of range. */
  static int[] flatSegmentToMember(CompoundCurve cc, int flatSeg) {
    int cum = 0;
    LineString[] members = cc.getMembers();
    for (int i = 0; i < members.length; i++) {
      int segs = members[i].getNumPoints() - 1;
      if (flatSeg >= cum && flatSeg <= cum + segs - 1) {
        return new int[] { i, flatSeg - cum };
      }
      cum += segs;
    }
    return null;
  }

  /** Maps a flat coord index of a CompoundCurve to {@code (memberIdx,
   *  localVertexIdx)}. At a junction the answer is ambiguous; we always
   *  return the EARLIER member (end of member i), since that is the
   *  last vertex appearing there in the flat traversal. Returns
   *  {@code null} if out of range. */
  static int[] flatVertexToMember(CompoundCurve cc, int flatVertex) {
    int cum = 0;
    LineString[] members = cc.getMembers();
    for (int i = 0; i < members.length; i++) {
      int size = members[i].getNumPoints();
      if (flatVertex >= cum && flatVertex <= cum + size - 1) {
        return new int[] { i, flatVertex - cum };
      }
      cum += size - 1;
    }
    return null;
  }

  /** True if a flat vertex index sits exactly on a member junction
   *  (i.e., end of one member = start of the next). */
  static boolean isJunctionVertex(CompoundCurve cc, int flatVertex) {
    int cum = 0;
    LineString[] members = cc.getMembers();
    for (int i = 0; i < members.length - 1; i++) {
      cum += members[i].getNumPoints() - 1;
      if (flatVertex == cum) return true;
    }
    return false;
  }

  private static Geometry insertOnCompoundCurve(CompoundCurve cc, int flatSegIndex, Coordinate newVertex) {
    int[] loc = flatSegmentToMember(cc, flatSegIndex);
    if (loc == null) return cc;
    int mi = loc[0];
    int localSegIdx = loc[1];
    LineString m = cc.getMemberN(mi);
    if (m instanceof CircularString || m instanceof ClothoidSegment) return cc;
    Coordinate[] mc = m.getCoordinates();
    Coordinate[] nc = new Coordinate[mc.length + 1];
    for (int i = 0; i <= localSegIdx; i++) nc[i] = mc[i];
    nc[localSegIdx + 1] = new Coordinate(newVertex);
    for (int i = localSegIdx + 1; i < mc.length; i++) nc[i + 1] = mc[i];
    LineString updated = m.getFactory().createLineString(nc);
    return cc.withMemberReplaced(mi, updated);
  }

  private static Geometry deleteOnCompoundCurve(CompoundCurve cc, int flatVertexIndex) {
    if (isJunctionVertex(cc, flatVertexIndex)) return cc;
    int[] loc = flatVertexToMember(cc, flatVertexIndex);
    if (loc == null) return cc;
    int mi = loc[0];
    int localIdx = loc[1];
    LineString m = cc.getMemberN(mi);
    if (m instanceof CircularString || m instanceof ClothoidSegment) return cc;
    Coordinate[] mc = m.getCoordinates();
    if (mc.length <= 2) return cc; // deleting would degenerate the member
    Coordinate[] nc = new Coordinate[mc.length - 1];
    int k = 0;
    for (int i = 0; i < mc.length; i++) {
      if (i == localIdx) continue;
      nc[k++] = mc[i];
    }
    LineString updated = m.getFactory().createLineString(nc);
    return cc.withMemberReplaced(mi, updated);
  }
}

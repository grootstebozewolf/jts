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
package org.locationtech.jts.operation.overlayng.curve;

import java.util.ArrayList;
import java.util.List;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.LinearRing;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.geom.curve.CompoundCurve;
import org.locationtech.jts.geom.curve.CurvePolygon;
import org.locationtech.jts.geom.curve.MultiSurface;
import org.locationtech.jts.operation.overlayng.OverlayNG;

/**
 * One CompoundCurve shell has a single plain hole; the other outer
 * is different and already has a certified clip. Compose: clip the
 * outers, then if the hole is strictly inside that CAP punch it,
 * and if it is strictly outside ignore it for CAP (keep it on the
 * holed side). A hole that meets or crosses the CAP shares the
 * clip edge: subtracting hole ∩ other is a bite, not an interior
 * punch ({@code H-SHELL-HOLE-CROSS}). Two holes that cross
 * ({@code H-SHELL-HOLE-X}) are a noder. Both stay {@code null}.
 */
final class DifferentOuterHoleOverlay {

  private DifferentOuterHoleOverlay() { }

  static Geometry overlay(Geometry a, Geometry b, int opCode) {
    CurvePolygon ca = SameOuterHoleOverlay.mixedShell(a);
    CurvePolygon cb = SameOuterHoleOverlay.mixedShell(b);
    if (ca == null || cb == null) return null;
    int ha = ca.getNumInteriorRing();
    int hb = cb.getNumInteriorRing();
    CurvePolygon holed = null;
    CurvePolygon solid = null;
    boolean holedFirst = false;
    if (ha == 1 && hb == 0) {
      holed = ca;
      solid = cb;
      holedFirst = true;
    }
    else if (ha == 0 && hb == 1) {
      holed = cb;
      solid = ca;
      holedFirst = false;
    }
    if (holed == null || solid == null) return null;
    if (holed.getExteriorCurve().equalsExact(solid.getExteriorCurve())) {
      return null;
    }
    LineString hole = SameOuterHoleOverlay.plainHole(holed);
    if (hole == null) return null;
    GeometryFactory f = TwoNodeClip.curveFactory(a);
    CurvePolygon outerHoled = new CurvePolygon(holed.getExteriorCurve(), null, f);
    if (!SameOuterHoleOverlay.holeInsideShell(holed, outerHoled)) {
      return null;
    }

    Geometry firstOuter = holedFirst ? outerHoled : solid;
    Geometry secondOuter = holedFirst ? solid : outerHoled;
    Geometry capOuters = CompoundCurveShellOverlay.overlay(
        firstOuter, secondOuter, OverlayNG.INTERSECTION);
    if (capOuters == null) return null;

    int holeVsCap = classifyHole(hole, capOuters);
    if (holeVsCap == TwoNodeClip.MIXED) return null;

    Geometry needed = outerResult(firstOuter, secondOuter, opCode);
    if (needed == null) return null;
    if (holeVsCap == TwoNodeClip.IN) {
      return composeInside(needed, hole, holedFirst, opCode, f);
    }
    return composeOutside(needed, hole, holedFirst, opCode, f);
  }

  private static Geometry outerResult(Geometry firstOuter, Geometry secondOuter,
      int opCode) {
    return CompoundCurveShellOverlay.overlay(firstOuter, secondOuter, opCode);
  }

  /**
   * Hole ⊂ outer CAP: CAP punches the hole; CUP is the outer union
   * (the other operand fills the hole); SUB / XOR keep the hole on
   * the solid-minus-holed side.
   */
  private static Geometry composeInside(Geometry outer, LineString hole,
      boolean holedFirst, int opCode, GeometryFactory f) {
    if (opCode == OverlayNG.INTERSECTION) {
      return punch(outer, hole, f);
    }
    if (opCode == OverlayNG.UNION) {
      return outer;
    }
    if (opCode == OverlayNG.DIFFERENCE) {
      return holedFirst ? outer : join(outer, holePolygon(hole, f), f);
    }
    if (opCode == OverlayNG.SYMDIFFERENCE) {
      return join(outer, holePolygon(hole, f), f);
    }
    return null;
  }

  /**
   * Hole ∩ outer CAP = empty: CAP / the solid-minus-holed side ignore
   * the hole; CUP and the holed-minus-solid side punch it.
   */
  private static Geometry composeOutside(Geometry outer, LineString hole,
      boolean holedFirst, int opCode, GeometryFactory f) {
    if (opCode == OverlayNG.INTERSECTION) {
      return outer;
    }
    if (opCode == OverlayNG.UNION) {
      return punch(outer, hole, f);
    }
    if (opCode == OverlayNG.DIFFERENCE) {
      return holedFirst ? punch(outer, hole, f) : outer;
    }
    if (opCode == OverlayNG.SYMDIFFERENCE) {
      return punch(outer, hole, f);
    }
    return null;
  }

  private static int classifyHole(LineString hole, Geometry cap) {
    Coordinate[] c = hole.getCoordinates();
    int n = c.length;
    if (c[0].equals2D(c[n - 1])) {
      n--;
    }
    if (n < 3) return TwoNodeClip.MIXED;
    boolean sawIn = false;
    boolean sawOut = false;
    boolean mixed = false;
    for (int i = 0; i < n && !mixed; i++) {
      int loc = locateInClip(c[i], cap);
      if (loc == TwoNodeClip.MIXED) {
        mixed = true;
      }
      else if (loc == TwoNodeClip.IN) {
        sawIn = true;
      }
      else {
        sawOut = true;
      }
    }
    for (int i = 0; i < n && !mixed; i++) {
      Coordinate mid = new Coordinate(
          0.5 * (c[i].x + c[(i + 1) % n].x),
          0.5 * (c[i].y + c[(i + 1) % n].y));
      int loc = locateInClip(mid, cap);
      if (loc == TwoNodeClip.MIXED) {
        mixed = true;
      }
      else if (loc == TwoNodeClip.IN) {
        sawIn = true;
      }
      else {
        sawOut = true;
      }
    }
    // Both sides of the CAP: the hole crosses the other shell.
    // hole ∩ CAP shares the clip edge (H-SHELL-HOLE-CROSS).
    if (mixed || (sawIn && sawOut)) return TwoNodeClip.MIXED;
    if (sawIn) return TwoNodeClip.IN;
    if (sawOut) return TwoNodeClip.OUT;
    return TwoNodeClip.MIXED;
  }

  private static int locateInClip(Coordinate p, Geometry clip) {
    if (clip == null || clip.isEmpty()) return TwoNodeClip.OUT;
    if (clip.getNumGeometries() != 1) return TwoNodeClip.MIXED;
    Geometry g = clip.getGeometryN(0);
    if (g instanceof CurvePolygon) {
      CurvePolygon cp = (CurvePolygon) g;
      if (cp.getNumInteriorRing() > 0) return TwoNodeClip.MIXED;
      LineString sh = cp.getExteriorCurve();
      if (sh instanceof CompoundCurve) {
        return TwoNodeClip.locateInShell(p, cp);
      }
      double[] disc = CircularDiscOverlay.centreRadius(cp);
      if (disc != null) {
        return TwoNodeClip.sideOfDisc(p, disc[0], disc[1], disc[2]);
      }
    }
    if (TwoNodeClip.isPlainPolygon(g)) {
      return TwoNodeClip.sideOfPolygon(p, (Polygon) g);
    }
    return TwoNodeClip.MIXED;
  }

  private static Geometry punch(Geometry solid, LineString hole,
      GeometryFactory f) {
    if (solid == null) return null;
    if (solid.isEmpty()) return solid;
    if (solid.getNumGeometries() == 1) {
      return punchOne(solid.getGeometryN(0), hole, f);
    }
    int hit = -1;
    boolean miss = false;
    for (int i = 0; i < solid.getNumGeometries() && !miss; i++) {
      int side = classifyHole(hole, solid.getGeometryN(i));
      if (side == TwoNodeClip.IN) {
        if (hit >= 0) {
          miss = true;
        }
        else {
          hit = i;
        }
      }
      else if (side != TwoNodeClip.OUT) {
        miss = true;
      }
    }
    if (miss || hit < 0) return null;
    List<Polygon> faces = new ArrayList<Polygon>();
    boolean ok = true;
    for (int i = 0; i < solid.getNumGeometries() && ok; i++) {
      Geometry face = i == hit
          ? punchOne(solid.getGeometryN(i), hole, f)
          : solid.getGeometryN(i);
      if (face instanceof Polygon && !face.isEmpty()) {
        faces.add((Polygon) face);
      }
      else {
        ok = false;
      }
    }
    if (!ok || faces.isEmpty()) return null;
    if (faces.size() == 1) return faces.get(0);
    return new MultiSurface(faces.toArray(new Polygon[0]), f);
  }

  private static Geometry punchOne(Geometry g, LineString hole,
      GeometryFactory f) {
    if (g instanceof CurvePolygon) {
      CurvePolygon cp = (CurvePolygon) g;
      if (cp.getNumInteriorRing() > 0) return null;
      return new CurvePolygon(cp.getExteriorCurve(),
          new LineString[] { hole }, f);
    }
    if (TwoNodeClip.isPlainPolygon(g)) {
      try {
        return f.createPolygon(((Polygon) g).getExteriorRing(),
            new LinearRing[] { f.createLinearRing(hole.getCoordinates()) });
      }
      catch (RuntimeException ex) {
        return null;
      }
    }
    return null;
  }

  private static Geometry holePolygon(LineString hole, GeometryFactory f) {
    try {
      return f.createPolygon(f.createLinearRing(hole.getCoordinates()));
    }
    catch (RuntimeException ex) {
      return null;
    }
  }

  private static Geometry join(Geometry a, Geometry extra, GeometryFactory f) {
    if (extra == null) return null;
    if (a == null || a.isEmpty()) return extra;
    if (extra.isEmpty()) return a;
    List<Polygon> faces = new ArrayList<Polygon>();
    if (!addFaces(faces, a) || !addFaces(faces, extra)) return null;
    if (faces.isEmpty()) return null;
    if (faces.size() == 1) return faces.get(0);
    return new MultiSurface(faces.toArray(new Polygon[0]), f);
  }

  private static boolean addFaces(List<Polygon> dest, Geometry g) {
    boolean ok = true;
    for (int i = 0; i < g.getNumGeometries() && ok; i++) {
      Geometry p = g.getGeometryN(i);
      if (p instanceof Polygon && !p.isEmpty()) {
        dest.add((Polygon) p);
      }
      else {
        ok = false;
      }
    }
    return ok;
  }
}

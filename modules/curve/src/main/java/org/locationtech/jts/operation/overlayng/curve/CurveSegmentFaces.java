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

import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.geom.curve.CurveGeometryFactory;
import org.locationtech.jts.geom.curve.CurvePolygon;
import org.locationtech.jts.geom.curve.MultiSurface;
import org.locationtech.jts.operation.overlayng.OverlayNG;

/**
 * Geometry assemble of an N-shell arrangement. Package-private --
 * not a public API, not an N-ary overlay, not a noder, not a DCEL.
 * The arrangement structure is {@link CurveSegmentDcel} (half-edges,
 * twins, next/prev, face pointers, {@link CurveSegmentString}
 * members). This class walks those bounded faces into
 * {@link Geometry}. OverlayNG stays binary; CAP / CUP / SUB / XOR
 * of a pair among the N stay on the existing kits.
 * <p>
 * N=2 recovers the pair-kit faces when the DCEL sews, or falls
 * back to the kits when it does not (0-node containment,
 * same-circle special case, MIXED-hides-crossing). Pinch / holed
 * Geometry-level stays {@code null}. A coincident leave-angle is
 * snap-rounding (P2.5.4): {@code faces} returns {@code null} and
 * {@link #missReason()} names {@link #TANGENT_LEAVE_ANGLE}.
 * Densify is never a noder. Not P2.5.5.
 */
final class CurveSegmentFaces {

  /** Named stamp: coincident leave-angle. Snap-rounding, not a walk. */
  static final String TANGENT_LEAVE_ANGLE =
      CurveSegmentDcel.TANGENT_LEAVE_ANGLE;

  private static String missReason;

  private CurveSegmentFaces() { }

  /**
   * Why the last {@link #faces(Geometry[])} / string-group call
   * returned {@code null}, or {@code null} when faces were
   * produced. Package-private -- not a public API.
   */
  static String missReason() {
    return missReason;
  }

  /**
   * Bounded faces of N hole-free circular / compound shells, or
   * {@code null}. N=2 is the pair-kit rings when the DCEL cannot
   * sew. N≥3 is the DCEL, or {@code null} when the walk would
   * need snap-rounding ({@link #missReason()} names the stamp).
   */
  static Geometry faces(Geometry[] geoms) {
    missReason = null;
    if (geoms == null || geoms.length < 2) return null;
    List<List<CurveSegmentString>> groups =
        new ArrayList<List<CurveSegmentString>>(geoms.length);
    Geometry factorySrc = null;
    boolean miss = false;
    for (int i = 0; i < geoms.length && !miss; i++) {
      if (geoms[i] == null || geoms[i].isEmpty() || hasHole(geoms[i])) {
        miss = true;
      }
      else {
        List<CurveSegmentString> s = CurveSegmentString.of(geoms[i]);
        if (s == null) {
          miss = true;
        }
        else {
          groups.add(s);
          if (factorySrc == null) {
            factorySrc = geoms[i];
          }
        }
      }
    }
    if (miss) return null;
    GeometryFactory f = factorySrc == null
        ? new CurveGeometryFactory()
        : TwoNodeClip.curveFactory(factorySrc);
    return faces(groups, scaleOf(geoms), f, geoms);
  }

  /**
   * Bounded faces of N string collections. Same assemble as
   * {@link #faces(Geometry[])}; no pair-kit fallback (no Geometry
   * overlay to recover).
   */
  static Geometry faces(List<List<CurveSegmentString>> groups, double scale) {
    return faces(groups, scale, new CurveGeometryFactory(), null);
  }

  private static Geometry faces(List<List<CurveSegmentString>> groups,
      double scale, GeometryFactory f, Geometry[] geoms) {
    missReason = null;
    if (groups == null || groups.size() < 2) return null;
    CurveSegmentDcel dcel = CurveSegmentDcel.of(groups, scale);
    if (dcel != null) {
      Geometry walked = assemble(dcel, scale, f);
      if (walked != null) {
        missReason = null;
        return walked;
      }
    }
    missReason = CurveSegmentDcel.missReason();
    if (geoms != null && geoms.length == 2) {
      Geometry kit = pairKitFaces(geoms[0], geoms[1]);
      if (kit != null) {
        missReason = null;
      }
      return kit;
    }
    return null;
  }

  /**
   * Pair-kit CAP + XOR components. Empty CAP is not a miss. A kit
   * that cannot certify (pinch, lineal) is {@code null}. MIXED
   * collinear overlap is {@link MixedOverlapOverlay} (shared
   * edge, not a discrete node pair). Does not call
   * {@link OverlayNGCurve} -- that would densify.
   */
  static Geometry pairKitFaces(Geometry a, Geometry b) {
    Geometry cap = exactOverlay(a, b, OverlayNG.INTERSECTION);
    Geometry xor = exactOverlay(a, b, OverlayNG.SYMDIFFERENCE);
    if (cap == null || xor == null) return null;
    List<Polygon> faces = new ArrayList<Polygon>();
    if (!addPoly(faces, cap) || !addPoly(faces, xor)) return null;
    return toGeometry(faces, TwoNodeClip.curveFactory(a));
  }

  private static Geometry assemble(CurveSegmentDcel dcel, double scale,
      GeometryFactory f) {
    double eps = Math.max(TwoNodeClip.PROPER_CROSS_FRAC * scale, 1.0e-12);
    List<Polygon> faces = new ArrayList<Polygon>();
    List<CurveSegmentDcel.Face> cells = dcel.boundedFaces();
    boolean miss = false;
    for (int i = 0; i < cells.size() && !miss; i++) {
      CurveSegmentDcel.Face cell = cells.get(i);
      List<LineString> members = cell.toLines(f);
      if (members == null) {
        miss = true;
      }
      else if (Math.abs(cell.signedArea()) > eps * eps) {
        Polygon face = TwoNodeClip.closeRing(members, f, eps);
        if (face == null) {
          miss = true;
        }
        else {
          faces.add(face);
        }
      }
    }
    if (miss || faces.isEmpty()) return null;
    dropUnion(faces, eps);
    if (faces.isEmpty()) return null;
    return toGeometry(faces, f);
  }

  /**
   * The walk also closes the union (the complementary outer ring).
   * That ring's area is the sum of the bounded faces; drop it.
   */
  private static void dropUnion(List<Polygon> faces, double eps) {
    if (faces.size() < 2) return;
    int maxAt = 0;
    double maxA = faces.get(0).getArea();
    double sum = maxA;
    for (int i = 1; i < faces.size(); i++) {
      double a = faces.get(i).getArea();
      sum += a;
      if (a > maxA) {
        maxA = a;
        maxAt = i;
      }
    }
    if (Math.abs(maxA - (sum - maxA)) <= Math.max(eps, 1.0e-8)) {
      faces.remove(maxAt);
    }
  }

  private static Geometry exactOverlay(Geometry a, Geometry b, int opCode) {
    Geometry g = CircularDiscOverlay.overlay(a, b, opCode);
    if (g != null) return g;
    g = CircularDiscPolygonOverlay.overlay(a, b, opCode);
    if (g != null) return g;
    return CompoundCurveShellOverlay.overlay(a, b, opCode);
  }

  private static boolean addPoly(List<Polygon> dest, Geometry g) {
    if (g == null) return false;
    if (g.isEmpty()) return true;
    if (g instanceof Polygon) {
      dest.add((Polygon) g);
      return true;
    }
    boolean ok = true;
    for (int i = 0; i < g.getNumGeometries() && ok; i++) {
      if (!addPoly(dest, g.getGeometryN(i))) {
        ok = false;
      }
    }
    return ok;
  }

  private static Geometry toGeometry(List<Polygon> faces, GeometryFactory f) {
    if (faces == null || faces.isEmpty()) return null;
    if (faces.size() == 1) return faces.get(0);
    return new MultiSurface(faces.toArray(new Polygon[0]), f);
  }

  private static boolean hasHole(Geometry g) {
    Geometry geom = unwrap(g);
    if (geom instanceof CurvePolygon) {
      return ((CurvePolygon) geom).getNumInteriorRing() > 0;
    }
    if (geom instanceof Polygon) {
      return ((Polygon) geom).getNumInteriorRing() > 0;
    }
    return false;
  }

  private static Geometry unwrap(Geometry g) {
    if (g == null || g.isEmpty()) return null;
    if (g instanceof MultiSurface) {
      if (g.getNumGeometries() != 1) return null;
      return unwrap(g.getGeometryN(0));
    }
    return g;
  }

  private static double scaleOf(Geometry[] geoms) {
    double s = 1.0;
    if (geoms == null) return s;
    for (int i = 0; i < geoms.length; i++) {
      if (geoms[i] == null || geoms[i].isEmpty()) {
        continue;
      }
      double w = Math.max(geoms[i].getEnvelopeInternal().getWidth(),
          geoms[i].getEnvelopeInternal().getHeight());
      if (w > s) {
        s = w;
      }
    }
    return s;
  }
}

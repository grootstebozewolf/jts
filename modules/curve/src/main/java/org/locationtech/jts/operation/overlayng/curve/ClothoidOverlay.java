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

import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.geom.curve.ClothoidSegment;
import org.locationtech.jts.geom.curve.CompoundCurve;
import org.locationtech.jts.geom.curve.CurvePolygon;
import org.locationtech.jts.geom.curve.MultiSurface;
import org.locationtech.jts.operation.overlayng.OverlayNG;

/**
 * 0-node overlay of a hole-free {@link CurvePolygon} whose shell
 * carries a {@link ClothoidSegment}. Package-private -- not a noder,
 * not a Fresnel solver, not a new public API.
 * <p>
 * A clothoid's parent {@link LineString} is start+end only. Flatten
 * would treat that chord as the edge and invent line–circle nodes
 * the spiral does not have. This kit intercepts before flatten.
 * <p>
 * Closed-form cells (no clothoid–circle / clothoid–line node):
 * <ul>
 *   <li>{@code CLOTHOID-ID} -- structural identity</li>
 *   <li>{@code CLOTHOID-DISJOINT} -- analytical envelopes do not meet</li>
 *   <li>{@code CLOTHOID-NEST} -- the clothoid shell's analytical
 *       envelope sits strictly inside a circular disc</li>
 * </ul>
 * Anything that would need a Fresnel noder is {@code CLOTHOID-FRESNEL}:
 * {@code null}, so the caller takes the chord baseline and does not
 * pay a dishonest two-node walk.
 */
final class ClothoidOverlay {

  private ClothoidOverlay() { }

  /**
   * True when {@code g} carries a {@link ClothoidSegment} on a ring
   * or as a lineal member. Used as the R1.7 gate: a clothoid pair
   * is this kit or a miss, never a chord flatten.
   */
  static boolean hasClothoid(Geometry g) {
    if (g == null || g.isEmpty()) return false;
    if (g instanceof ClothoidSegment) return true;
    if (g instanceof CompoundCurve) {
      CompoundCurve cc = (CompoundCurve) g;
      for (int i = 0; i < cc.getNumMembers(); i++) {
        if (hasClothoid(cc.getMemberN(i))) return true;
      }
      return false;
    }
    if (g instanceof CurvePolygon) {
      CurvePolygon cp = (CurvePolygon) g;
      if (hasClothoid(cp.getExteriorCurve())) return true;
      for (int i = 0; i < cp.getNumInteriorRing(); i++) {
        if (hasClothoid(cp.getInteriorCurveN(i))) return true;
      }
      return false;
    }
    int n = g.getNumGeometries();
    if (n >= 1 && g.getGeometryN(0) != g) {
      for (int i = 0; i < n; i++) {
        if (hasClothoid(g.getGeometryN(i))) return true;
      }
    }
    return false;
  }

  /**
   * Exact overlay, or {@code null} if this class cannot answer
   * ({@code CLOTHOID-FRESNEL}). A miss does not densify and does
   * not node.
   */
  static Geometry overlay(Geometry a, Geometry b, int opCode) {
    if (!hasClothoid(a) && !hasClothoid(b)) return null;
    if (sameClothoidGeometry(a, b)) {
      return HalfDiscOverlay.identityShell(opCode, a,
          TwoNodeClip.curveFactory(a));
    }

    CurvePolygon clothA = clothoidShell(a);
    CurvePolygon clothB = clothoidShell(b);
    if (clothA != null && clothB != null) {
      if (!clothA.getEnvelopeInternal().intersects(clothB.getEnvelopeInternal())) {
        return HalfDiscOverlay.disjointShells(opCode, a, clothA, clothB,
            TwoNodeClip.curveFactory(a));
      }
      return null;
    }

    CurvePolygon cloth = clothA != null ? clothA : clothB;
    Geometry other = clothA != null ? b : a;
    boolean clothFirst = clothA != null;
    if (cloth == null) return null;

    if (!cloth.getEnvelopeInternal().intersects(other.getEnvelopeInternal())) {
      return disjointOther(opCode, a, cloth, other);
    }

    double[] disc = CircularDiscOverlay.centreRadius(other);
    if (disc != null && strictlyInsideDisc(cloth, disc)) {
      CurvePolygon outer = other instanceof CurvePolygon
          ? (CurvePolygon) other : null;
      if (outer == null) return null;
      return HalfDiscOverlay.containedShell(cloth, outer, clothFirst,
          opCode, a, TwoNodeClip.curveFactory(a));
    }
    return null;
  }

  /**
   * Hole-free CurvePolygon whose shell is a closed CompoundCurve
   * with at least one {@link ClothoidSegment}.
   */
  static CurvePolygon clothoidShell(Geometry g) {
    if (g instanceof MultiSurface) {
      if (g.getNumGeometries() != 1) return null;
      g = g.getGeometryN(0);
    }
    if (!(g instanceof CurvePolygon)) return null;
    CurvePolygon cp = (CurvePolygon) g;
    if (cp.isEmpty() || cp.getNumInteriorRing() > 0) return null;
    LineString ring = cp.getExteriorCurve();
    if (!(ring instanceof CompoundCurve) || !ring.isClosed()) return null;
    return hasClothoid(ring) ? cp : null;
  }

  /**
   * Sufficient certificate: the analytical AABB of the clothoid
   * shell sits strictly inside the disc. Corners outside the circle
   * are a miss, not a flatten.
   */
  static boolean strictlyInsideDisc(CurvePolygon cloth, double[] disc) {
    if (cloth == null || disc == null) return false;
    double cx = disc[0];
    double cy = disc[1];
    double r = disc[2];
    double eps = Math.max(TwoNodeClip.PROPER_CROSS_FRAC * r, 1.0e-12);
    double maxR = r - eps;
    Envelope env = cloth.getEnvelopeInternal();
    return cornerInside(env.getMinX(), env.getMinY(), cx, cy, maxR)
        && cornerInside(env.getMinX(), env.getMaxY(), cx, cy, maxR)
        && cornerInside(env.getMaxX(), env.getMinY(), cx, cy, maxR)
        && cornerInside(env.getMaxX(), env.getMaxY(), cx, cy, maxR);
  }

  private static boolean cornerInside(double x, double y, double cx,
      double cy, double maxR) {
    double dx = x - cx;
    double dy = y - cy;
    return dx * dx + dy * dy < maxR * maxR;
  }

  private static boolean sameClothoidGeometry(Geometry a, Geometry b) {
    if (!OverlayNGCurve.isSameGeometry(a, b)) return false;
    return sameMembers(ringOf(a), ringOf(b));
  }

  private static LineString ringOf(Geometry g) {
    if (g instanceof MultiSurface && g.getNumGeometries() == 1) {
      g = g.getGeometryN(0);
    }
    if (g instanceof CurvePolygon) {
      return ((CurvePolygon) g).getExteriorCurve();
    }
    if (g instanceof LineString) {
      return (LineString) g;
    }
    return null;
  }

  private static boolean sameMembers(LineString r1, LineString r2) {
    if (r1 == r2) return true;
    if (r1 == null || r2 == null) return false;
    if (r1.getClass() != r2.getClass()) return false;
    if (!(r1 instanceof CompoundCurve)) {
      return r1.equalsExact(r2);
    }
    CompoundCurve c1 = (CompoundCurve) r1;
    CompoundCurve c2 = (CompoundCurve) r2;
    if (c1.getNumMembers() != c2.getNumMembers()) return false;
    for (int i = 0; i < c1.getNumMembers(); i++) {
      LineString m1 = c1.getMemberN(i);
      LineString m2 = c2.getMemberN(i);
      if (m1.getClass() != m2.getClass()) return false;
      if (!m1.equalsExact(m2)) return false;
    }
    return true;
  }

  private static Geometry disjointOther(int opCode, Geometry first,
      CurvePolygon cloth, Geometry other) {
    GeometryFactory f = TwoNodeClip.curveFactory(first);
    if (opCode == OverlayNG.INTERSECTION) {
      return f.createEmpty(2);
    }
    if (opCode == OverlayNG.DIFFERENCE) {
      return first.copy();
    }
    if (opCode == OverlayNG.UNION || opCode == OverlayNG.SYMDIFFERENCE) {
      if (other instanceof Polygon) {
        return new MultiSurface(new Polygon[] {
            (Polygon) cloth.copy(), (Polygon) other.copy()
        }, f);
      }
    }
    return null;
  }
}

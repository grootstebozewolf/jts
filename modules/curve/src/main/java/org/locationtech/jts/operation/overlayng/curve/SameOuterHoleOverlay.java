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

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.curve.CircularString;
import org.locationtech.jts.geom.curve.CompoundCurve;
import org.locationtech.jts.geom.curve.CurvePolygon;
import org.locationtech.jts.geom.curve.MultiSurface;
import org.locationtech.jts.operation.overlayng.OverlayNG;

/**
 * Same CompoundCurve outer; one operand has a single hole strictly
 * inside the other. CAP is the holed polygon, CUP the unholed,
 * SUB the empty or the hole ring. Not hole-nest noding.
 */
final class SameOuterHoleOverlay {

  private SameOuterHoleOverlay() { }

  static Geometry overlay(Geometry a, Geometry b, int opCode) {
    CurvePolygon ca = curvePolygonAllowHole(a);
    CurvePolygon cb = curvePolygonAllowHole(b);
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
    if (!holed.getExteriorCurve().equalsExact(solid.getExteriorCurve())) {
      return null;
    }
    if (!holeStrictlyInside(holed, solid)) return null;
    GeometryFactory f = TwoNodeClip.curveFactory(a);
    if (opCode == OverlayNG.INTERSECTION) {
      return holedFirst ? a.copy() : holed.copy();
    }
    if (opCode == OverlayNG.UNION) {
      return holedFirst ? solid.copy() : a.copy();
    }
    if (opCode == OverlayNG.DIFFERENCE) {
      return holedFirst ? f.createEmpty(2) : holeAsPolygon(holed, f);
    }
    if (opCode == OverlayNG.SYMDIFFERENCE) {
      return holeAsPolygon(holed, f);
    }
    return null;
  }

  private static CurvePolygon curvePolygonAllowHole(Geometry g) {
    if (g instanceof MultiSurface) {
      if (g.getNumGeometries() != 1) return null;
      g = g.getGeometryN(0);
    }
    if (!(g instanceof CurvePolygon)) return null;
    CurvePolygon cp = (CurvePolygon) g;
    if (cp.isEmpty() || cp.getNumInteriorRing() > 1) return null;
    LineString ring = cp.getExteriorCurve();
    if (!(ring instanceof CompoundCurve) || !ring.isClosed()) return null;
    CompoundCurve cc = (CompoundCurve) ring;
    boolean hasArc = false;
    boolean hasLine = false;
    for (int i = 0; i < cc.getNumMembers(); i++) {
      LineString m = cc.getMemberN(i);
      if (m instanceof CircularString) {
        hasArc = true;
      }
      else if (m instanceof LineString) {
        hasLine = true;
      }
      else {
        return null;
      }
    }
    if (!hasArc || !hasLine) return null;
    return cp;
  }

  private static boolean holeStrictlyInside(CurvePolygon holed,
      CurvePolygon solid) {
    LineString hole = holed.getInteriorCurveN(0);
    if (hole == null || hole.isEmpty() || hole.getNumPoints() < 4) {
      return false;
    }
    Coordinate[] c = hole.getCoordinates();
    int n = c.length;
    if (c[0].equals2D(c[n - 1])) {
      n--;
    }
    boolean inside = true;
    for (int i = 0; i < n && inside; i++) {
      if (TwoNodeClip.locateInShell(c[i], solid) != TwoNodeClip.IN) {
        inside = false;
      }
    }
    return inside;
  }

  private static Geometry holeAsPolygon(CurvePolygon holed, GeometryFactory f) {
    LineString hole = holed.getInteriorCurveN(0);
    if (hole instanceof CircularString || hole instanceof CompoundCurve) {
      return new CurvePolygon(hole, null, f);
    }
    try {
      return f.createPolygon(f.createLinearRing(hole.getCoordinates()));
    }
    catch (RuntimeException ex) {
      return null;
    }
  }
}

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
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.curve.CircularString;
import org.locationtech.jts.geom.curve.CompoundCurve;
import org.locationtech.jts.geom.curve.CurvePolygon;
import org.locationtech.jts.geom.curve.MultiSurface;
import org.locationtech.jts.noding.NodedSegmentString;

/**
 * OverlayNG-for-circles Option B slices: convert a leftover circular
 * pair onto core {@link org.locationtech.jts.noding.CircularNodedSegmentString},
 * node through OverlayNG's {@link org.locationtech.jts.noding.Noder}
 * entry, and assemble a named exact area.
 * <p>
 * Cells:
 * <ul>
 * <li>H-SHELL-N-MIXED — collinear diameter overlap → containment assemble</li>
 * <li>two-shell proper crossing — noder names crossings → {@link TwoShellClip}</li>
 * </ul>
 * The P2.1–P2.5.4 kits stay refused on their own path; the P2.5.4
 * tangent stamp stays {@code null}. Package-private -- not a public API.
 */
final class OverlayNGCircle {

  private OverlayNGCircle() { }

  /**
   * Exact overlay via OverlayNG noding, or {@code null} if this
   * slice cannot answer.
   */
  static Geometry overlay(Geometry a, Geometry b, int opCode) {
    CurvePolygon ca = mixedCompoundShell(a);
    CurvePolygon cb = mixedCompoundShell(b);
    if (ca == null || cb == null) {
      return null;
    }
    List<CurveSegmentString> sa = CurveSegmentString.of(ca);
    List<CurveSegmentString> sb = CurveSegmentString.of(cb);
    if (sa == null || sb == null) {
      return null;
    }
    double scale = scaleOf(ca, cb);
    List<NodedSegmentString> edges = new ArrayList<NodedSegmentString>();
    addPieces(edges, sa, 0);
    addPieces(edges, sb, 1);

    OverlayNGCircleNoder noder = new OverlayNGCircleNoder(scale);
    noder.computeNodes(edges);
    noder.getNodedSubstrings();
    if (noder.hasMixedOverlap() && !noder.hasProperCrossing()) {
      return TwoShellClip.containmentOverlay(ca, cb, opCode, a);
    }
    // Deliberate Option-B expand: proper crossings → two-shell assemble.
    if (noder.hasProperCrossing() && !noder.hasMixedOverlap()) {
      return TwoShellClip.overlay(ca, cb, opCode, a);
    }
    return null;
  }

  /**
   * Hole-free {@link CurvePolygon} whose shell is a mixed
   * {@link CompoundCurve} (LineString + CircularString). The same
   * shape gate as R1.7, so a line-only shell and a full disc skip
   * this path without paying the noder.
   */
  private static CurvePolygon mixedCompoundShell(Geometry g) {
    if (g instanceof MultiSurface) {
      if (g.getNumGeometries() != 1) {
        return null;
      }
      g = g.getGeometryN(0);
    }
    if (!(g instanceof CurvePolygon)) {
      return null;
    }
    CurvePolygon cp = (CurvePolygon) g;
    if (cp.isEmpty() || cp.getNumInteriorRing() > 0) {
      return null;
    }
    LineString ring = cp.getExteriorCurve();
    if (!(ring instanceof CompoundCurve) || !ring.isClosed()) {
      return null;
    }
    CompoundCurve cc = (CompoundCurve) ring;
    boolean hasArc = false;
    boolean hasLine = false;
    for (int i = 0; i < cc.getNumMembers(); i++) {
      LineString m = cc.getMemberN(i);
      if (m instanceof CircularString) {
        hasArc = true;
      }
      else {
        hasLine = true;
      }
    }
    if (!hasArc || !hasLine) {
      return null;
    }
    return cp;
  }

  private static void addPieces(List<NodedSegmentString> edges,
      List<CurveSegmentString> pieces, int geomIndex) {
    Integer data = Integer.valueOf(geomIndex);
    for (int i = 0; i < pieces.size(); i++) {
      edges.add(OverlayNGCircleNoder.toCore(pieces.get(i), data));
    }
  }

  private static double scaleOf(Geometry a, Geometry b) {
    double wa = Math.max(a.getEnvelopeInternal().getWidth(),
        a.getEnvelopeInternal().getHeight());
    double wb = Math.max(b.getEnvelopeInternal().getWidth(),
        b.getEnvelopeInternal().getHeight());
    return Math.max(Math.max(wa, wb), 1.0);
  }
}

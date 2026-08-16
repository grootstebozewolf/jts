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
import java.util.Collection;
import java.util.List;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.noding.CircularNodedSegmentString;
import org.locationtech.jts.noding.NodedSegmentString;
import org.locationtech.jts.noding.Noder;
import org.locationtech.jts.noding.SegmentString;

/**
 * OverlayNG noder for {@link CircularNodedSegmentString}. Lives in
 * jts-curve so arc–arc / arc–line closed forms stay
 * {@link CurveSegmentString} atoms -- core does not depend on this
 * module, and the formulas are not copied.
 * <p>
 * Implements {@link Noder} so {@code EdgeNodingBuilder} can run it.
 * MIXED (collinear overlap) inserts the overlap ends; a proper
 * crossing is a hit that is not a segment endpoint. Package-private
 * -- not a public circular noder API.
 */
final class OverlayNGCircleNoder implements Noder {

  private final double scale;
  private Collection<NodedSegmentString> segStrings;
  private boolean mixedOverlap;
  private boolean properCrossing;

  OverlayNGCircleNoder(double scale) {
    this.scale = scale;
  }

  boolean hasMixedOverlap() {
    return mixedOverlap;
  }

  boolean hasProperCrossing() {
    return properCrossing;
  }

  @Override
  public void computeNodes(Collection segStrings) {
    this.segStrings = cast(segStrings);
    mixedOverlap = false;
    properCrossing = false;
    List<NodedSegmentString> list =
        new ArrayList<NodedSegmentString>(this.segStrings);
    for (int i = 0; i < list.size(); i++) {
      for (int j = i; j < list.size(); j++) {
        nodePair(list.get(i), list.get(j), i == j);
      }
    }
  }

  @Override
  public Collection getNodedSubstrings() {
    return NodedSegmentString.getNodedSubstrings(segStrings);
  }

  private void nodePair(NodedSegmentString e0, NodedSegmentString e1,
      boolean same) {
    int n0 = e0.size() - 1;
    int n1 = e1.size() - 1;
    for (int i = 0; i < n0; i++) {
      for (int j = 0; j < n1; j++) {
        if (same && skipSame(e0, i, j)) {
          continue;
        }
        nodeSegments(e0, i, e1, j);
      }
    }
  }

  private static boolean skipSame(NodedSegmentString e, int i, int j) {
    if (i == j) {
      return true;
    }
    if (Math.abs(i - j) == 1) {
      return true;
    }
    if (e.isClosed()) {
      int max = e.size() - 2;
      if ((i == 0 && j == max) || (j == 0 && i == max)) {
        return true;
      }
    }
    return false;
  }

  private void nodeSegments(NodedSegmentString e0, int i,
      NodedSegmentString e1, int j) {
    CurveSegmentString p = piece(e0, i);
    CurveSegmentString q = piece(e1, j);
    Coordinate[] xs = CurveSegmentString.intersect(p, q, scale);
    if (xs == null) {
      CurveSegmentString run = CurveSegmentString.overlap(p, q, scale);
      if (run == null || run.isDegenerate()) {
        return;
      }
      mixedOverlap = true;
      addNode(e0, i, run.getStart());
      addNode(e0, i, run.getEnd());
      addNode(e1, j, run.getStart());
      addNode(e1, j, run.getEnd());
      return;
    }
    for (int k = 0; k < xs.length; k++) {
      addNode(e0, i, xs[k]);
      addNode(e1, j, xs[k]);
      if (!isEnd(e0, i, xs[k]) && !isEnd(e1, j, xs[k])) {
        properCrossing = true;
      }
    }
  }

  private static void addNode(NodedSegmentString ss, int segIndex,
      Coordinate p) {
    ss.addIntersection(p, segIndex);
  }

  private static boolean isEnd(NodedSegmentString ss, int segIndex,
      Coordinate p) {
    return p.equals2D(ss.getCoordinate(segIndex))
        || p.equals2D(ss.getCoordinate(segIndex + 1));
  }

  static CurveSegmentString piece(SegmentString ss, int segIndex) {
    Coordinate a = ss.getCoordinate(segIndex);
    Coordinate b = ss.getCoordinate(segIndex + 1);
    if (ss instanceof CircularNodedSegmentString) {
      CircularNodedSegmentString circ = (CircularNodedSegmentString) ss;
      if (circ.isCircularArc(segIndex)) {
        return CurveSegmentString.arc(a, circ.getArcMidpoint(segIndex), b);
      }
    }
    return CurveSegmentString.segment(a, b);
  }

  static NodedSegmentString toCore(CurveSegmentString s, Object data) {
    if (s.isArc()) {
      return new CircularNodedSegmentString(s.getStart(), s.getMid(),
          s.getEnd(), data);
    }
    return new NodedSegmentString(
        new Coordinate[] { s.getStart(), s.getEnd() }, data);
  }

  @SuppressWarnings("unchecked")
  private static Collection<NodedSegmentString> cast(Collection raw) {
    return raw;
  }
}

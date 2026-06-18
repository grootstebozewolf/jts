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
package org.locationtech.jts.geom.curved;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Arc-aware noder (N-SS, JTS #1195): nodes a collection of {@link ArcSegmentString}s
 * at their mutual crossings and returns the split sub-strings, so the noded
 * strings touch only at endpoints. This is the curved analogue of the linear
 * {@code SimpleNoder} — brute-force pairwise — using the oracle-pinned
 * {@link CircularArcs} intersection primitives (arc/arc, arc/segment) via
 * {@link ArcSegmentString#intersectPieces}.
 * <p>
 * It is a self-contained {@code jts-curved} utility: it does not implement the
 * core {@code Noder} interface (which is {@code Coordinate[]}-only) and does not
 * touch the overlay / buffer pipelines — that core integration (an arc-aware
 * {@code SegmentIntersector}, curved spatial indexing, arc snap-rounding) is a
 * separate, much larger effort.
 */
public final class ArcNoder {

  private List<ArcSegmentString> nodedSubstrings;
  private final List<double[]> nodePoints = new ArrayList<double[]>();

  /** Computes the nodes among the given arc strings and the resulting split sub-strings. */
  public void computeNodes(Collection<ArcSegmentString> strings) {
    List<ArcSegmentString> in = new ArrayList<ArcSegmentString>(strings);
    for (int i = 0; i < in.size(); i++) {
      for (int j = i + 1; j < in.size(); j++) {
        nodePair(in.get(i), in.get(j));
      }
    }
    nodedSubstrings = new ArrayList<ArcSegmentString>();
    for (ArcSegmentString s : in) nodedSubstrings.addAll(s.getNodedSubstrings());
  }

  /** The split sub-strings after {@link #computeNodes}. */
  public List<ArcSegmentString> getNodedSubstrings() { return nodedSubstrings; }

  /** The intersection (node) points found, for inspection / testing. */
  public List<double[]> getNodePoints() { return nodePoints; }

  private void nodePair(ArcSegmentString a, ArcSegmentString b) {
    for (int ai = 0; ai < a.numArcs(); ai++) {
      double[] pa = a.arc(ai);
      for (int bi = 0; bi < b.numArcs(); bi++) {
        double[] pb = b.arc(bi);
        for (double[] pt : ArcSegmentString.intersectPieces(pa, pb)) {
          a.addNode(ai, pt[0], pt[1]);
          b.addNode(bi, pt[0], pt[1]);
          nodePoints.add(pt);
        }
      }
    }
  }
}

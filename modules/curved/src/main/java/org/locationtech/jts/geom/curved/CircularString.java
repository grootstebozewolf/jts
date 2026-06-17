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

import org.locationtech.jts.geom.CoordinateSequence;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;

/**
 * A connected sequence of circular arcs, where each consecutive triple of
 * control points (start, mid, end) defines one arc and the end point of one
 * arc is the start point of the next.
 * <p>
 * This is a phase-1 stand-in: the control points are stored as a single
 * {@link CoordinateSequence} (inherited via {@link LineString}) and spatial
 * operations fall through to the parent's polyline behaviour. Native
 * arc-aware algorithms are out of scope for this module today.
 */
public class CircularString extends LineString implements Linearizable {
  private static final long serialVersionUID = 1L;

  public CircularString(CoordinateSequence points, GeometryFactory factory) {
    super(points, factory);
  }

  @Override
  public String getGeometryType() {
    return "CircularString";
  }

  /**
   * Arc-aware simplicity (V-CS, JTS #1195): the curve is simple when its circular
   * arcs do not cross, touch tangentially, or overlap, except at shared adjacency
   * endpoints (and the single closing endpoint when the curve is closed) — rather
   * than testing the chord polyline inherited from {@link LineString}, which can
   * disagree with the true arcs. Degenerate inputs (fewer than three points, or a
   * malformed even-length sequence) fall back to the polyline test.
   */
  @Override
  public boolean isSimple() {
    CoordinateSequence seq = getCoordinateSequence();
    int n = seq.size();
    if (n < 3 || (n % 2) == 0) return super.isSimple();
    return ArcStringSimplicity.isSimple(seq);
  }

  /**
   * The analytical circular arc length: the sum of {@code r * theta} over each
   * consecutive control-point triple {@code (p[2i], p[2i+1], p[2i+2])}, rather
   * than the chord-polyline length inherited from {@link LineString} (M-LEN-CS,
   * JTS #1195). Degenerate (collinear) arcs contribute their chord length.
   */
  @Override
  public double getLength() {
    CoordinateSequence seq = getCoordinateSequence();
    int n = seq.size();
    if (n < 3) return super.getLength();
    double total = 0.0;
    int i = 0;
    for (; i + 2 < n; i += 2) {
      total += CircularArcs.arcLength(
          seq.getX(i),     seq.getY(i),
          seq.getX(i + 1), seq.getY(i + 1),
          seq.getX(i + 2), seq.getY(i + 2));
    }
    // Defensive: a malformed (even-length) sequence leaves a dangling segment;
    // treat its trailing chord as a straight edge.
    for (; i + 1 < n; i++) {
      total += Math.hypot(seq.getX(i + 1) - seq.getX(i), seq.getY(i + 1) - seq.getY(i));
    }
    return total;
  }

  @Override
  protected CircularString copyInternal() {
    return new CircularString(getCoordinateSequence().copy(), getFactory());
  }

  @Override
  public Geometry toLinear(double tolerance) {
    return getFactory().createLineString(getCoordinateSequence().copy());
  }
}

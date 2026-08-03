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
import java.util.Collections;
import java.util.List;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.CoordinateSequence;
import org.locationtech.jts.geom.CoordinateSequences;
import org.locationtech.jts.geom.Envelope;
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

  @Override
  protected CircularString copyInternal() {
    return new CircularString(getCoordinateSequence().copy(), getFactory());
  }

  /**
   * The envelope of the arc itself, not just its control points.
   * <p>
   * The inherited {@code LineString.computeEnvelopeInternal()} expands over the
   * coordinate sequence, which clips any arc sweeping past an axis extreme that
   * is not a control point. That produces an envelope smaller than the geometry
   * it bounds, which makes spatial indexes and the envelope short-circuits in
   * {@code intersects}/{@code distance} miss real hits.
   */
  @Override
  protected Envelope computeEnvelopeInternal() {
    CoordinateSequence seq = getCoordinateSequence();
    int n = seq.size();
    if (n == 0) return new Envelope();
    if (n < 3) return super.computeEnvelopeInternal();
    Envelope env = new Envelope();
    for (int i = 0; i + 2 < n; i += 2) {
      CircularArcDensifier.expandEnvelope(
          seq.getCoordinate(i), seq.getCoordinate(i + 1), seq.getCoordinate(i + 2), env);
    }
    return env;
  }

  /**
   * The true arc length, summed over the arcs formed by each overlapping
   * control-point triple.
   * <p>
   * The inherited {@code LineString.getLength()} walks the control points as
   * straight segments, which returns the inscribed chord length -- for a
   * semicircle {@code 2r*sqrt(2)} instead of {@code pi*r}, about 10% short.
   * Colinear triples contribute their straight-line distance.
   */
  @Override
  public double getLength() {
    CoordinateSequence seq = getCoordinateSequence();
    int n = seq.size();
    if (n < 3) return super.getLength();
    double total = 0.0;
    for (int i = 0; i + 2 < n; i += 2) {
      total += CircularArcDensifier.arcLength(
          seq.getCoordinate(i), seq.getCoordinate(i + 1), seq.getCoordinate(i + 2));
    }
    return total;
  }

  /**
   * Reverses the control-point sequence, staying a CircularString.
   * <p>
   * Without this the inherited {@code LineString.reverseInternal()} rebuilds a
   * plain {@link org.locationtech.jts.geom.LineString} and the arc identity is
   * lost. Reversing the control points of an arc traverses the same arc in the
   * opposite direction: the start and end swap and the interior point stays
   * interior, so the circle through them is unchanged.
   */
  @Override
  protected CircularString reverseInternal() {
    CoordinateSequence seq = getCoordinateSequence().copy();
    CoordinateSequences.reverse(seq);
    return new CircularString(seq, getFactory());
  }

  @Override
  public Geometry toLinear(double tolerance) {
    return toLinear(tolerance, Collections.<Coordinate>emptyList());
  }

  @Override
  public Geometry toLinear(double tolerance, List<Coordinate> mustInclude) {
    if (isEmpty()) {
      return getFactory().createLineString();
    }
    CoordinateSequence seq = getCoordinateSequence();
    int n = seq.size();
    if (n < 3) {
      // Degenerate: no arc structure available, return whatever points we have.
      return getFactory().createLineString(seq.copy());
    }
    List<Coordinate> include = mustInclude == null
        ? Collections.<Coordinate>emptyList() : mustInclude;

    List<Coordinate> out = new ArrayList<Coordinate>();
    for (int i = 0; i + 2 < n; i += 2) {
      Coordinate start = seq.getCoordinate(i);
      Coordinate mid   = seq.getCoordinate(i + 1);
      Coordinate end   = seq.getCoordinate(i + 2);
      List<Coordinate> chord = CircularArcDensifier.densifyArc(start, mid, end, tolerance, include);
      // The first arc contributes its start; subsequent arcs share an
      // endpoint with the previous arc — drop the duplicate.
      int from = out.isEmpty() ? 0 : 1;
      for (int k = from; k < chord.size(); k++) {
        out.add(chord.get(k));
      }
    }
    return getFactory().createLineString(out.toArray(new Coordinate[0]));
  }
}

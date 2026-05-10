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
   * §3.7 — type identity is required. The inherited {@code LineString.isEquivalentClass}
   * accepts any LineString subclass, which would let a plain LineString
   * with the same 3-point coord seq compare equal to a CircularString.
   * The 3 points denote different geometries (a polyline vs a circular
   * arc through them), so the type check must be strict.
   */
  @Override
  protected boolean isEquivalentClass(Geometry other) {
    return other instanceof CircularString;
  }

  /**
   * Reversing a CircularString preserves the type — a 3-point arc traversed
   * in the opposite direction is still a 3-point arc on the same circle.
   * Without this override the inherited {@link LineString#reverseInternal()}
   * would return a plain LineString and lose the arc identity (which would
   * also break {@link CompoundCurve#reverseInternal()}).
   */
  @Override
  protected CircularString reverseInternal() {
    CoordinateSequence seq = getCoordinateSequence();
    int n = seq.size();
    Coordinate[] reversed = new Coordinate[n];
    for (int i = 0; i < n; i++) {
      reversed[i] = new Coordinate(seq.getCoordinate(n - 1 - i));
    }
    GeometryFactory f = getFactory();
    return new CircularString(f.getCoordinateSequenceFactory().create(reversed), f);
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

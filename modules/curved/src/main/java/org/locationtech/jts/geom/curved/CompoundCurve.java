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
import java.util.List;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.CoordinateSequence;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;

/**
 * A connected sequence of {@link LineString} and {@link CircularString}
 * segments — the OGC SFA / ISO 19125-2 {@code COMPOUNDCURVE} type.
 *
 * <p>The members are stored as a {@link LineString} array (with
 * {@code CircularString} instances appearing as members where the
 * corresponding source segment was an arc). The parent
 * {@link LineString}'s coordinate sequence is the concatenation of all
 * member control points (with shared endpoints deduplicated), so callers
 * who use only the {@code LineString} API see a sensible polyline view;
 * callers who want to inspect or render the compound structure use
 * {@link #getCurves()}, {@link #getNumCurves()}, {@link #getCurveN(int)}.
 *
 * <p>The legacy single-arg constructor that takes a
 * {@code CoordinateSequence} (with no member structure) is preserved
 * for two cases: (1) the lenient flat-form fallback used by
 * {@code CurvedWKTReader} when round-tripping output from this writer
 * pre-Phase-3, and (2) third-party callers that built up CompoundCurves
 * before member preservation existed. In both cases the resulting
 * {@code CompoundCurve} reports {@link #getNumCurves()} as 1, with the
 * single member being a plain {@code LineString} carrying all the
 * coordinates.
 */
public class CompoundCurve extends LineString implements Linearizable {
  private static final long serialVersionUID = 2L;

  private final LineString[] members;

  /**
   * Member-aware constructor. Each entry is either a {@code LineString}
   * (straight segment) or a {@code CircularString} (arc segment).
   * Adjacent members must share an endpoint per OGC SFA, but the
   * constructor does not enforce this — validation is deferred to a
   * later phase.
   */
  public CompoundCurve(LineString[] members, GeometryFactory factory) {
    super(concatenateMembers(members, factory), factory);
    this.members = members.clone();
  }

  /**
   * Legacy flat-form constructor. Wraps {@code points} as the single
   * member of this CompoundCurve. Use the array constructor when member
   * structure (lines vs arcs) needs to be preserved.
   */
  public CompoundCurve(CoordinateSequence points, GeometryFactory factory) {
    super(points, factory);
    this.members = new LineString[] { factory.createLineString(points.copy()) };
  }

  @Override
  public String getGeometryType() {
    return "CompoundCurve";
  }

  /** Number of segment members in this CompoundCurve. Always &ge; 1
   *  for non-empty instances. */
  public int getNumCurves() {
    return members.length;
  }

  /** The {@code n}-th member ({@link LineString} or
   *  {@link CircularString}). */
  public LineString getCurveN(int n) {
    return members[n];
  }

  /** A defensive copy of the member array. */
  public LineString[] getCurves() {
    return members.clone();
  }

  @Override
  protected CompoundCurve copyInternal() {
    LineString[] copy = new LineString[members.length];
    for (int i = 0; i < members.length; i++) {
      copy[i] = (LineString) members[i].copy();
    }
    return new CompoundCurve(copy, getFactory());
  }

  /**
   * Linearises every member and concatenates the result into a single
   * {@link LineString}. Members that implement {@link Linearizable}
   * (e.g. {@link CircularString}) are densified per their own contract;
   * plain {@code LineString} members are copied as-is.
   */
  @Override
  public Geometry toLinear(double tolerance) {
    if (members.length == 0) {
      return getFactory().createLineString();
    }
    List<Coordinate> all = new ArrayList<Coordinate>();
    for (int i = 0; i < members.length; i++) {
      LineString member = members[i];
      LineString linear;
      if (member instanceof Linearizable) {
        Geometry g = ((Linearizable) member).toLinear(tolerance);
        linear = (g instanceof LineString) ? (LineString) g
                                            : getFactory().createLineString(g.getCoordinates());
      } else {
        linear = member;
      }
      Coordinate[] coords = linear.getCoordinates();
      // Drop the leading vertex of every member after the first to
      // avoid duplicating the shared endpoint between adjacent
      // segments.
      int from = (i == 0) ? 0 : 1;
      for (int k = from; k < coords.length; k++) {
        all.add(coords[k]);
      }
    }
    return getFactory().createLineString(all.toArray(new Coordinate[0]));
  }

  /**
   * Concatenate every member's control points into one sequence,
   * deduplicating the shared endpoint between adjacent members. Used
   * to feed the parent {@link LineString} so its
   * {@code getCoordinateSequence} / {@code getCoordinates} continues to
   * return a sensible polyline view.
   */
  private static CoordinateSequence concatenateMembers(LineString[] members, GeometryFactory factory) {
    if (members == null || members.length == 0) {
      return factory.getCoordinateSequenceFactory().create(0, 2);
    }
    List<Coordinate> all = new ArrayList<Coordinate>();
    for (int i = 0; i < members.length; i++) {
      Coordinate[] coords = members[i].getCoordinates();
      int from = (i == 0) ? 0 : 1;
      for (int k = from; k < coords.length; k++) {
        all.add(coords[k]);
      }
    }
    return factory.getCoordinateSequenceFactory().create(all.toArray(new Coordinate[0]));
  }
}

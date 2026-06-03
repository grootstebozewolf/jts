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
import org.locationtech.jts.geom.Point;

/**
 * A connected sequence of {@link LineString} and {@link CircularString}
 * segments (and nested CompoundCurve). Supports member structure for
 * analytical operations (length, boundary, copy, toLinear, etc).
 */
public class CompoundCurve extends LineString implements Linearizable {
  private static final long serialVersionUID = 1L;

  private final LineString[] members;

  /**
   * Legacy/flat ctor (for backward compat with phase-1 seq-based creation):
   * wraps the seq as a single linear member.
   */
  public CompoundCurve(CoordinateSequence points, GeometryFactory factory) {
    this( new LineString[] { factory.createLineString(points) }, factory );
  }

  /** Structural ctor. */
  public CompoundCurve(LineString[] members, GeometryFactory factory) {
    super( buildSeq(members, factory), factory );
    this.members = (members == null ? new LineString[0] : members.clone());
  }

  private static CoordinateSequence buildSeq(LineString[] mems, GeometryFactory f) {
    if (mems == null || mems.length == 0) {
      return f.getCoordinateSequenceFactory().create(0, 2);
    }
    List<Coordinate> coords = new ArrayList<>();
    for (int i = 0; i < mems.length; i++) {
      Coordinate[] cs = mems[i].getCoordinates();
      int start = coords.isEmpty() ? 0 : 1; // skip junction
      for (int j = start; j < cs.length; j++) {
        coords.add(cs[j]);
      }
    }
    return f.getCoordinateSequenceFactory().create( coords.toArray(new Coordinate[0]) );
  }

  public int getNumCurves() {
    return members.length;
  }

  public LineString getCurveN(int n) {
    return members[n];
  }

  /** Returns the member curves (for writer etc). */
  public LineString[] getCurves() {
    return members.clone();
  }

  @Override
  public String getGeometryType() {
    return "CompoundCurve";
  }

  /**
   * M-DIM guard: empty curved lineals must report dimension 1.
   * <p>
   * Explicit override (in addition to inherited from LineString) so that
   * refactors to empty construction or future changes cannot regress the
   * empty-curve dimension semantics required by the spec. See
   * CurveAwarenessSpecTest#test_M_DIM_* (and its ship commit).
   */
  @Override
  public int getDimension() {
    return 1;
  }

  @Override
  public double getLength() {
    double len = 0.0;
    for (LineString m : members) {
      len += m.getLength();
    }
    return len;
  }

  @Override
  public Point getCentroid() {
    if (isEmpty() || members.length == 0) {
      return getFactory().createPoint();
    }
    double total = getLength();
    if (total == 0) {
      return getFactory().createPoint(getCoordinate());
    }
    double sx = 0, sy = 0;
    for (LineString m : members) {
      double ml = m.getLength();
      Point mc = m.getCentroid();
      if (mc != null && !mc.isEmpty()) {
        sx += ml * mc.getX();
        sy += ml * mc.getY();
      }
    }
    return getFactory().createPoint(new Coordinate(sx / total, sy / total));
  }

  /**
   * Returns the boundary of this CompoundCurve.
   *
   * <p>B-CC (low risk/cost RGR pivot): semantics are identical to LineString.
   * For an open curve: a 2-point MultiPoint of the distinct start and end
   * control points. For a closed curve (start coord == end coord): an empty
   * MultiPoint (per BoundaryNodeRule MOD2, closed endpoints have valence 2
   * and are not on the boundary).
   *
   * <p>Implementation delegates to the LineString / BoundaryOp path (which
   * already handles this via instanceof and isClosed()). The override exists
   * as an explicit guard / documentation point so that when CompoundCurve
   * is later reimplemented with member storage (segment-aware copy/toLinear
   * per epic arch notes), the boundary contract is re-validated here rather
   * than silently inheriting a flat-seq computation.
   *
   * <p>Risk: none (pure delegation of proven logic). Cost: trivial.
   * See CurveAwarenessSpecTest#test_B_CC_* for seam ID and risk/cost rationale.
   */
  @Override
  public Geometry getBoundary() {
    return super.getBoundary();
  }

  @Override
  protected CompoundCurve copyInternal() {
    LineString[] copies = new LineString[members.length];
    for (int i = 0; i < members.length; i++) {
      copies[i] = (LineString) members[i].copy();
    }
    return new CompoundCurve(copies, getFactory());
  }

  @Override
  public Geometry toLinear(double tolerance) {
    if (members.length == 0) {
      return getFactory().createLineString();
    }
    List<Coordinate> all = new ArrayList<>();
    for (int i = 0; i < members.length; i++) {
      LineString m = members[i];
      LineString lin = (m instanceof Linearizable)
          ? (LineString) ((Linearizable) m).toLinear(tolerance)
          : m;
      Coordinate[] cs = lin.getCoordinates();
      int start = all.isEmpty() ? 0 : 1;
      for (int j = start; j < cs.length; j++) {
        all.add(cs[j]);
      }
    }
    return getFactory().createLineString( all.toArray(new Coordinate[0]) );
  }
}

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
 * A connected sequence of {@link LineString} and {@link CircularString}
 * segments. Phase-1 stand-in: member structure is collapsed to a flat
 * concatenation of control points. A future phase will preserve segments.
 */
public class CompoundCurve extends LineString implements Linearizable {
  private static final long serialVersionUID = 1L;

  public CompoundCurve(CoordinateSequence points, GeometryFactory factory) {
    super(points, factory);
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
    return new CompoundCurve(getCoordinateSequence().copy(), getFactory());
  }

  @Override
  public Geometry toLinear(double tolerance) {
    return getFactory().createLineString(getCoordinateSequence().copy());
  }
}

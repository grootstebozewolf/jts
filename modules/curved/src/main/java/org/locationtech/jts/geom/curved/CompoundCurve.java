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
   * A {@code CompoundCurve} is class-equivalent only to another
   * {@code CompoundCurve} — never to a plain {@link LineString} with the same
   * control points (R-EQ, JTS #1195), overriding the lenient
   * {@code instanceof LineString} test inherited from {@link LineString} so
   * {@code equalsExact} / {@code equalsNorm} distinguish a curve from its chord
   * polyline. (The converse {@code lineString.equalsExact(compoundCurve)} still
   * runs the core lenient check; full symmetry would require a core change.)
   */
  @Override
  protected boolean isEquivalentClass(Geometry other) {
    return other != null && getClass() == other.getClass();
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

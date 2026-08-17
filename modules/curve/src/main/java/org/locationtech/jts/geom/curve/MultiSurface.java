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
package org.locationtech.jts.geom.curve;

import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.IntersectionMatrix;
import org.locationtech.jts.geom.MultiPolygon;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.io.curve.CurveWKTWriter;

/** A collection of {@link Polygon} and {@link CurvePolygon} members. */
public class MultiSurface extends MultiPolygon implements Linearizable {
  private static final long serialVersionUID = 1L;

  public MultiSurface(Polygon[] members, GeometryFactory factory) {
    super(members, factory);
  }

  @Override
  public String getGeometryType() {
    return "MultiSurface";
  }

  @Override
  protected MultiSurface copyInternal() {
    int n = getNumGeometries();
    Polygon[] members = new Polygon[n];
    for (int i = 0; i < n; i++) {
      members[i] = (Polygon) getGeometryN(i).copy();
    }
    return new MultiSurface(members, getFactory());
  }

  @Override
  public Geometry toLinear(double tolerance) {
    GeometryFactory f = getFactory();
    int n = getNumGeometries();
    Polygon[] linearMembers = new Polygon[n];
    for (int i = 0; i < n; i++) {
      Geometry m = getGeometryN(i);
      if (m instanceof Linearizable) {
        linearMembers[i] = (Polygon) ((Linearizable) m).toLinear(tolerance);
      } else {
        linearMembers[i] = (Polygon) m.copy();
      }
    }
    return f.createMultiPolygon(linearMembers);
  }

  // -- Arc-aware spatial operations ----------------------------------------
  // Same family the single curve types route through CurveOps. A member
  // with no cheaper path is the chord baseline.

  /**
   * Core {@code WKTWriter} refuses to flatten curved members to untagged polygons.
   */
  @Override
  public String toText() {
    return new CurveWKTWriter().write(this);
  }

  @Override
  public Geometry convexHull() {
    return CurveOps.convexHull(this);
  }

  @Override
  public double distance(Geometry g) {
    return CurveOps.distance(this, g);
  }

  @Override
  public boolean isWithinDistance(Geometry g, double distance) {
    return CurveOps.isWithinDistance(this, g, distance);
  }

  @Override
  public Geometry buffer(double distance) {
    return CurveOps.buffer(this, distance);
  }

  @Override
  public Geometry buffer(double distance, int quadrantSegments) {
    return CurveOps.buffer(this, distance, quadrantSegments);
  }

  @Override
  public Geometry buffer(double distance, int quadrantSegments, int endCapStyle) {
    return CurveOps.buffer(this, distance, quadrantSegments, endCapStyle);
  }

  @Override
  public Geometry intersection(Geometry other) {
    return CurveOps.intersection(this, other);
  }

  @Override
  public Geometry union(Geometry other) {
    return CurveOps.union(this, other);
  }

  @Override
  public Geometry difference(Geometry other) {
    return CurveOps.difference(this, other);
  }

  @Override
  public Geometry symDifference(Geometry other) {
    return CurveOps.symDifference(this, other);
  }

  @Override
  public IntersectionMatrix relate(Geometry g) {
    return CurveOps.relate(this, g);
  }

  @Override
  public boolean relate(Geometry g, String intersectionPattern) {
    return CurveOps.relate(this, g, intersectionPattern);
  }

  @Override
  public boolean intersects(Geometry g) {
    return CurveOps.intersects(this, g);
  }

  @Override
  public boolean touches(Geometry g) {
    return CurveOps.touches(this, g);
  }

  @Override
  public boolean crosses(Geometry g) {
    return CurveOps.crosses(this, g);
  }

  @Override
  public boolean within(Geometry g) {
    return CurveOps.within(this, g);
  }

  @Override
  public boolean contains(Geometry g) {
    return CurveOps.contains(this, g);
  }

  @Override
  public boolean overlaps(Geometry g) {
    return CurveOps.overlaps(this, g);
  }

  @Override
  public boolean covers(Geometry g) {
    return CurveOps.covers(this, g);
  }

  @Override
  public boolean coveredBy(Geometry g) {
    return CurveOps.coveredBy(this, g);
  }

  @Override
  public boolean equalsTopo(Geometry g) {
    return CurveOps.equalsTopo(this, g);
  }
}

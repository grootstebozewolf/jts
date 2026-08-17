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

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.CoordinateSequence;
import org.locationtech.jts.geom.CoordinateSequenceFactory;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.LinearRing;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.geom.PrecisionModel;

/**
 * A {@link GeometryFactory} subclass with creation methods for the
 * extended OGC SFA / ISO 19125-2 geometry types implemented in
 * {@code jts-curve}: {@link CircularString}, {@link CompoundCurve},
 * {@link CurvePolygon}, {@link MultiCurve}, {@link MultiSurface},
 * {@link Triangle}, {@link PolyhedralSurface}, and {@link Tin}.
 * <p>
 * Behaves identically to {@link GeometryFactory} for all standard
 * (non-curved) types. Use this factory when constructing curved
 * geometries programmatically; pair it with {@link
 * org.locationtech.jts.io.curve.CurveWKTReader} when reading WKT.
 */
public class CurveGeometryFactory extends GeometryFactory {

  public CurveGeometryFactory() {
    super();
  }

  public CurveGeometryFactory(PrecisionModel pm) {
    super(pm);
  }

  public CurveGeometryFactory(PrecisionModel pm, int srid) {
    super(pm, srid);
  }

  public CurveGeometryFactory(PrecisionModel pm, int srid, CoordinateSequenceFactory csf) {
    super(pm, srid, csf);
  }

  public CurveGeometryFactory(CoordinateSequenceFactory csf) {
    super(csf);
  }

  @Override
  public CircularString createCircularString(CoordinateSequence points) {
    return new CircularString(points, this);
  }

  public CompoundCurve createCompoundCurve(CoordinateSequence points) {
    return new CompoundCurve(points, this);
  }

  @Override
  public CompoundCurve createCompoundCurve(LineString[] members) {
    return new CompoundCurve(members, this);
  }

  @Override
  public CurvePolygon createCurvePolygon() {
    return new CurvePolygon(this);
  }

  public CurvePolygon createCurvePolygon(LinearRing shell) {
    return new CurvePolygon(shell, null, this);
  }

  public CurvePolygon createCurvePolygon(LinearRing shell, LinearRing[] holes) {
    return new CurvePolygon(shell, holes, this);
  }

  @Override
  public CurvePolygon createCurvePolygon(LineString shell, LineString[] holes) {
    return new CurvePolygon(shell, holes, this);
  }

  @Override
  public MultiCurve createMultiCurve(LineString[] members) {
    return new MultiCurve(members, this);
  }

  @Override
  public ClothoidSegment createClothoid(Coordinate start, double startTangent,
      double startKappa, double endKappa, double length) {
    return new ClothoidSegment(start, startTangent, startKappa, endKappa,
        length, this);
  }

  /**
   * Returns a {@link MultiCurve} when any member carries an arc, otherwise the
   * plain {@code MultiLineString} core would build.
   * <p>
   * Without this, every collection-building path erased curve identity: the
   * TestBuilder combiner adds a drawn arc to an existing one via
   * {@code buildGeometry}, which landed here and boxed both into a plain
   * MultiLineString -- whose WKT then writes the arc's control points as a
   * straight line. {@code MultiCurve extends MultiLineString}, so the upgrade is
   * invisible to callers that only wanted the supertype.
   */
  @Override
  public org.locationtech.jts.geom.MultiLineString createMultiLineString(LineString[] members) {
    if (hasCurveMember(members)) {
      return new MultiCurve(members, this);
    }
    return super.createMultiLineString(members);
  }

  /** The polygonal counterpart: any {@link CurvePolygon} member makes a MultiSurface. */
  @Override
  public org.locationtech.jts.geom.MultiPolygon createMultiPolygon(Polygon[] members) {
    if (members != null) {
      for (Polygon m : members) {
        if (m instanceof CurvePolygon) {
          return new MultiSurface(members, this);
        }
      }
    }
    return super.createMultiPolygon(members);
  }

  /**
   * Core's {@code buildGeometry} decides homogeneity by exact class, so a
   * {@code CircularString} next to a plain {@code LineString} -- mixed lineal,
   * exactly what {@code MULTICURVE} exists to hold -- degraded to a
   * {@code GEOMETRYCOLLECTION}. Dimension-homogeneous input with a curve member
   * routes to the multi creators above; everything else defers to core,
   * including the surface-next-to-bare-curve case, for which a
   * GeometryCollection is the honest answer.
   */
  @Override
  public Geometry buildGeometry(java.util.Collection geomList) {
    if (geomList != null && geomList.size() > 1) {
      boolean allLineal = true, allPolygonal = true, anyCurve = false;
      for (Object o : geomList) {
        allLineal &= o instanceof LineString;
        allPolygonal &= o instanceof Polygon;
        anyCurve |= o instanceof Linearizable;
      }
      if (anyCurve && allLineal) {
        return createMultiLineString(
            (LineString[]) geomList.toArray(new LineString[0]));
      }
      if (anyCurve && allPolygonal) {
        return createMultiPolygon((Polygon[]) geomList.toArray(new Polygon[0]));
      }
    }
    return super.buildGeometry(geomList);
  }

  private static boolean hasCurveMember(LineString[] members) {
    if (members == null) return false;
    for (LineString m : members) {
      if (m instanceof CircularString || m instanceof CompoundCurve
          || m instanceof ClothoidSegment) return true;
    }
    return false;
  }

  @Override
  public MultiSurface createMultiSurface(Polygon[] members) {
    return new MultiSurface(members, this);
  }

  public Triangle createTriangle() {
    return new Triangle(this);
  }

  public Triangle createTriangle(LinearRing shell) {
    return new Triangle(shell, this);
  }

  public PolyhedralSurface createPolyhedralSurface(Polygon[] patches) {
    return new PolyhedralSurface(patches, this);
  }

  public Tin createTin(Polygon[] patches) {
    return new Tin(patches, this);
  }
}

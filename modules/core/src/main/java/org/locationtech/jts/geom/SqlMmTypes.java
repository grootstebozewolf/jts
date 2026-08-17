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
package org.locationtech.jts.geom;

/**
 * I/O type identity (Architect SIGN 16 Aug 2026, ClaimId MMF-IO).
 * SQL/MM ISO/IEC 13249-3 types 8–12 plus ISO Z/M/ZM
 * ({@code CircularStringZ=1008} … {@code MultiSurfaceZM=3012}).
 * Core writers must not emit a LineString / Polygon / Multi*
 * control polygon for CircularString / CompoundCurve /
 * CurvePolygon / MultiCurve / MultiSurface.
 * I/O identity is not overlay honesty. Flavour ISO/EXTENDED
 * is #51 on #7 — this helper does not reimplement it.
 * {@link LinearRing} → {@link LineString} is the only allowed
 * collapse. HOLD GEO-TIN 15–17. HOLD elliptic / Bézier. No DOI.
 */
public final class SqlMmTypes {

  private SqlMmTypes() {}

  /**
   * Refuse a geometry (and collection members) that would flatten
   * a SQL/MM curve type to a core lineal / polygonal type.
   *
   * @param geom the geometry to check (null is ignored)
   * @param site the core entry point that would have flattened
   */
  public static void refuseFlatten(Geometry geom, String site) {
    refuseFlatten(geom, site, false);
  }

  /**
   * @param allowCircularStringKeyword {@code true} only for core
   *        {@code WKTWriter}, which already emits the CIRCULARSTRING
   *        keyword from {@link Geometry#getGeometryType()}. CompoundCurve
   *        members still refuse — that path collapses members to one seq.
   */
  public static void refuseFlatten(Geometry geom, String site,
      boolean allowCircularStringKeyword) {
    if (geom == null) {
      return;
    }
    refuseOne(geom, site, allowCircularStringKeyword);
    if (geom instanceof GeometryCollection) {
      for (int i = 0; i < geom.getNumGeometries(); i++) {
        Geometry g = geom.getGeometryN(i);
        if (g != geom) {
          refuseFlatten(g, site, allowCircularStringKeyword);
        }
      }
    }
  }

  /**
   * Refuse this geometry only (not collection members).
   * Used by {@code WKTWriter} so a GeometryCollection can still
   * dispatch members to {@code CurveWKTWriter}.
   */
  public static void refuseOne(Geometry geom, String site,
      boolean allowCircularStringKeyword) {
    if (geom instanceof LineString) {
      if (geom.getClass() == LineString.class || geom instanceof LinearRing) {
        return;
      }
      if (allowCircularStringKeyword
          && "CircularString".equals(geom.getGeometryType())) {
        return;
      }
      throw flattenRefused(geom, site);
    }
    if (geom instanceof Polygon) {
      if (geom.getClass() == Polygon.class) {
        return;
      }
      throw flattenRefused(geom, site);
    }
    if (geom instanceof MultiLineString) {
      if (geom.getClass() == MultiLineString.class) {
        return;
      }
      throw flattenRefused(geom, site);
    }
    if (geom instanceof MultiPolygon) {
      if (geom.getClass() == MultiPolygon.class) {
        return;
      }
      throw flattenRefused(geom, site);
    }
  }

  public static IllegalArgumentException flattenRefused(Geometry geom, String site) {
    return new IllegalArgumentException(
        site + " cannot flatten " + geom.getGeometryType()
        + " to a core type. SQL/MM ISO/IEC 13249-3 types 8–12 "
        + "(CircularString, CompoundCurve, CurvePolygon, MultiCurve, "
        + "MultiSurface) must use a curve-aware path. "
        + "LinearRing→LineString is the only allowed collapse.");
  }
}

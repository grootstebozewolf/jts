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
 * collapse. HOLD 13/14. HOLD GEO-TIN 15–17. HOLD type 18–20.
 * HOLD JTS I/O 21. HOLD elliptic / Bézier (named fallback, not
 * type 19). leftover 1000001–1000005 HOLD. No DOI.
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
      // HOLD GEO-TIN 15–17: Triangle / PolyhedralSurface / TIN are not
      // SQL/MM 8–12 flatten targets; core WKTWriter may emit their keywords.
      String t = geom.getGeometryType();
      if ("Triangle".equals(t) || "PolyhedralSurface".equals(t)
          || "Tin".equals(t) || "TIN".equals(t)) {
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
      // HOLD GEO-TIN 15–17: PolyhedralSurface / TIN are not 8–12.
      String t = geom.getGeometryType();
      if ("PolyhedralSurface".equals(t) || "Tin".equals(t) || "TIN".equals(t)) {
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

  /**
   * Overlay honesty (CRV-CC ticket 30): a SQL/MM
   * {@code COMPOUNDCURVE} (ISO/IEC 13249-3 §4.2.13 / §7.10.1, WKB 9)
   * must not be eaten as {@code Coordinate[]} / {@code LineString}
   * chords. Named fallback is OverlayNGCurve
   * {@code isApproximate()} or {@code toLinear} / {@code Linearize}.
   * I/O refuse is a sibling ({@link #refuseFlatten}); this is the
   * overlay / TestBuilder consume leftover.
   *
   * @param geom the geometry to check (null is ignored)
   * @param site the overlay entry that would have flattened
   */
  public static void refuseCompoundCurveChord(Geometry geom, String site) {
    if (geom == null || geom.isEmpty()) {
      return;
    }
    if (containsCompoundCurve(geom)) {
      throw compoundCurveChordRefused(geom, site);
    }
  }

  /**
   * True when {@code geom} is a CompoundCurve or a collection /
   * CurvePolygon that still carries a CompoundCurve member or ring.
   * Type-name walk so jts-core does not depend on jts-curve.
   */
  public static boolean containsCompoundCurve(Geometry geom) {
    if (geom == null) {
      return false;
    }
    if ("CompoundCurve".equals(geom.getGeometryType())) {
      return true;
    }
    if ("CurvePolygon".equals(geom.getGeometryType())
        && curvePolygonHasCompoundCurveRing(geom)) {
      return true;
    }
    if (geom instanceof GeometryCollection) {
      for (int i = 0; i < geom.getNumGeometries(); i++) {
        Geometry g = geom.getGeometryN(i);
        if (g != geom && containsCompoundCurve(g)) {
          return true;
        }
      }
    }
    return false;
  }

  /**
   * CurvePolygon lives in jts-curve. Structural rings are not the
   * {@link Polygon#getExteriorRing()} LinearRing OverlayNG would
   * node. Reflect the curve accessors so a CompoundCurve shell or
   * hole is still a refuse, not a control-polygon overlay.
   */
  private static boolean curvePolygonHasCompoundCurveRing(Geometry geom) {
    Object shell = invokeIfPresent(geom, "getExteriorCurve");
    if (isCompoundCurveType(shell)) {
      return true;
    }
    Object nHoles = invokeIfPresent(geom, "getNumInteriorRing");
    int n = nHoles instanceof Integer ? ((Integer) nHoles).intValue() : 0;
    for (int i = 0; i < n; i++) {
      Object hole = invokeIfPresent(geom, "getInteriorCurveN",
          new Class[] { int.class }, new Object[] { Integer.valueOf(i) });
      if (isCompoundCurveType(hole)) {
        return true;
      }
    }
    return false;
  }

  private static boolean isCompoundCurveType(Object g) {
    return g instanceof Geometry
        && "CompoundCurve".equals(((Geometry) g).getGeometryType());
  }

  private static Object invokeIfPresent(Geometry geom, String name) {
    return invokeIfPresent(geom, name, new Class[0], new Object[0]);
  }

  private static Object invokeIfPresent(Geometry geom, String name,
      Class[] params, Object[] args) {
    try {
      java.lang.reflect.Method pub = geom.getClass().getMethod(name, params);
      pub.setAccessible(true);
      return pub.invoke(geom, args);
    }
    catch (NoSuchMethodException e) {
      // CurvePolygon accessors are public; walk declared methods only if
      // a package-private stand-in is ever used from tests.
    }
    catch (ReflectiveOperationException e) {
      return null;
    }
    Class c = geom.getClass();
    while (c != null) {
      try {
        java.lang.reflect.Method m = c.getDeclaredMethod(name, params);
        m.setAccessible(true);
        return m.invoke(geom, args);
      }
      catch (NoSuchMethodException e) {
        c = c.getSuperclass();
      }
      catch (ReflectiveOperationException e) {
        return null;
      }
    }
    return null;
  }

  public static IllegalArgumentException compoundCurveChordRefused(
      Geometry geom, String site) {
    String type = geom == null ? "CompoundCurve" : geom.getGeometryType();
    return new IllegalArgumentException(
        site + " cannot flatten " + type
        + " to Coordinate[] chords. SQL/MM ISO/IEC 13249-3 COMPOUNDCURVE "
        + "(WKB 9) stays a COMPOUNDCURVE. Use OverlayNGCurve "
        + "(isApproximate) or named toLinear / Linearize.");
  }
}

/*
 * Copyright (c) 2016 Vivid Solutions.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * and Eclipse Distribution License v. 1.0 which accompanies this distribution.
 * The Eclipse Public License is available at http://www.eclipse.org/legal/epl-v20.html
 * and the Eclipse Distribution License is available at
 *
 * http://www.eclipse.org/org/documents/edl-v10.php.
 */
package org.locationtech.jts.io;

/**
 * Constant values used by the WKB format
 */
public interface WKBConstants {
  int wkbXDR = 0;
  int wkbNDR = 1;

  int wkbPoint = 1;
  int wkbLineString = 2;
  int wkbPolygon = 3;
  int wkbMultiPoint = 4;
  int wkbMultiLineString = 5;
  int wkbMultiPolygon = 6;
  int wkbGeometryCollection = 7;

  /**
   * ISO/IEC 13249-3 (SQL/MM Spatial) type codes for curve geometries.
   * Same integers as GEOS {@code WKBConstants} for types 8–12.
   * No {@code wkbCurve} / {@code wkbSurface}. WKB 15–17
   * (Triangle / PolyhedralSurface / TIN) — GEO-TIN waits Architect SIGN.
   * <p>
   * Fork MMF (#1195) greenfield zoo (SIGNED):
   * {@link #wkbClothoid}=18, {@link #wkbBezier}=19,
   * {@link #wkbEllipse}=20, {@link #wkbNurbs}=21.
   * Unknown types outside the signed set throw.
   */
  int wkbCircularString = 8;
  int wkbCompoundCurve = 9;
  int wkbCurvePolygon = 10;
  int wkbMultiCurve = 11;
  int wkbMultiSurface = 12;

  /** CRV-CLOTHOID — Euler / Cornu spiral (fork SIGN 18). */
  int wkbClothoid = 18;
  /** PRF-BEZIER — cubic Bézier curve geometry (fork SIGN 19). */
  int wkbBezier = 19;
  /** PRF-ELLIPSE — elliptic arc / ellipse primitive (fork SIGN 20). */
  int wkbEllipse = 20;
  /** CRV-NURBS — NURBS curve (fork SIGN 21). */
  int wkbNurbs = 21;

  /**
   * Writer flavour: PostGIS / SFSQL Extended WKB (EWKB high bits for
   * Z / M / SRID). Default, matching GEOS {@code WKBWriter}.
   */
  int wkbExtended = 1;

  /**
   * Writer flavour: ISO/IEC 13249-3 WKB. Dimension is
   * {@code type+1000} (Z), {@code +2000} (M), {@code +3000} (ZM).
   * ISO has no SRID embedding.
   */
  int wkbIso = 2;
}

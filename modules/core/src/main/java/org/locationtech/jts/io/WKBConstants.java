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
   * Same integers as GEOS {@code WKBConstants}: types 8–12 only.
   * No {@code wkbCurve} / {@code wkbSurface}. No WKB 15–17
   * (Triangle / PolyhedralSurface / TIN) — GEO-TIN waits Architect SIGN.
   * Unknown types throw.
   */
  int wkbCircularString = 8;
  int wkbCompoundCurve = 9;
  int wkbCurvePolygon = 10;
  int wkbMultiCurve = 11;
  int wkbMultiSurface = 12;

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

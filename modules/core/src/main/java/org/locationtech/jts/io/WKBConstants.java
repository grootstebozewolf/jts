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
   * Signed I/O set is ISO/IEC 13249-3 (SQL/MM Spatial) types 8–12 only:
   * CircularString=8, CompoundCurve=9, CurvePolygon=10,
   * MultiCurve=11, MultiSurface=12. Same integers as GEOS
   * {@code WKBConstants}. Cite 13249-3 for 8–12 only. No DOI.
   * DIS is not the 2016 IS — do not take DIS 18–24 as JTS I/O.
   * <p>
   * HOLD 13/14: no {@code wkbCurve} / {@code wkbSurface}.
   * HOLD GEO-TIN 15–17 (PolyhedralSurface=15, TIN=16, Triangle=17).
   * Do not add them.
   * leftover 1000001–1000005 HOLD.
   * <p>
   * Preview fork map 18 Clothoid / 19 Bézier / 20 Ellipse / 21 NURBS
   * is not SIGNED I/O and is not the curve SoT. HOLD type 18–20.
   * HOLD JTS I/O 21. Not Circle-as-18. Not Clothoid-as-22.
   * Bézier is a named fallback, not type 19. Unknown types throw.
   */
  int wkbCircularString = 8;
  int wkbCompoundCurve = 9;
  int wkbCurvePolygon = 10;
  int wkbMultiCurve = 11;
  int wkbMultiSurface = 12;

  /** Preview Clothoid code 18. HOLD type 18. Not SIGNED I/O. Not Circle. */
  int wkbClothoid = 18;
  /** Preview Bézier code 19. Named fallback, not type 19. HOLD type 19. */
  int wkbBezier = 19;
  /** Preview Ellipse code 20. HOLD type 20. Not SIGNED I/O. */
  int wkbEllipse = 20;
  /** Preview NURBS code 21. HOLD JTS I/O 21. Not SIGNED I/O. */
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

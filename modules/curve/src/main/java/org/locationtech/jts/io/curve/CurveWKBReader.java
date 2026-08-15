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
package org.locationtech.jts.io.curve;

import java.io.IOException;
import java.util.EnumSet;

import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.geom.curve.CircularString;
import org.locationtech.jts.geom.curve.CompoundCurve;
import org.locationtech.jts.geom.curve.CurvePolygon;
import org.locationtech.jts.geom.curve.MultiCurve;
import org.locationtech.jts.geom.curve.MultiSurface;
import org.locationtech.jts.io.Ordinate;
import org.locationtech.jts.io.ParseException;
import org.locationtech.jts.io.WKBConstants;
import org.locationtech.jts.io.WKBReader;

/**
 * A {@link WKBReader} subclass that constructs first-class curve types
 * from ISO/OGC SQL/MM type codes 8–12. Nested members are themselves
 * WKB geometries, so a {@code CurvePolygon} ring that is a
 * {@code CircularString} stays a {@code CircularString}.
 */
public class CurveWKBReader extends WKBReader {

  private static final String FIELD_NUMELEMS = "numElems";
  private static final String FIELD_NUMRINGS = "numRings";

  public CurveWKBReader() {
    super();
  }

  public CurveWKBReader(GeometryFactory geometryFactory) {
    super(geometryFactory);
  }

  @Override
  protected Geometry readOtherGeometry(int geometryType,
      EnumSet<Ordinate> ordinateFlags, int SRID)
      throws IOException, ParseException {
    switch (geometryType) {
      case WKBConstants.wkbCircularString:
        return readCircularString(ordinateFlags);
      case WKBConstants.wkbCompoundCurve:
        return readCompoundCurve(SRID);
      case WKBConstants.wkbCurvePolygon:
        return readCurvePolygon(SRID);
      case WKBConstants.wkbMultiCurve:
        return readMultiCurve(SRID);
      case WKBConstants.wkbMultiSurface:
        return readMultiSurface(SRID);
      default:
        return super.readOtherGeometry(geometryType, ordinateFlags, SRID);
    }
  }

  private CircularString readCircularString(EnumSet<Ordinate> ordinateFlags)
      throws IOException, ParseException {
    LineString ls = readLineString(ordinateFlags);
    return new CircularString(ls.getCoordinateSequence(), factory);
  }

  private CompoundCurve readCompoundCurve(int SRID)
      throws IOException, ParseException {
    int n = readNumField(FIELD_NUMELEMS);
    LineString[] members = new LineString[n];
    for (int i = 0; i < n; i++) {
      members[i] = asLine(readGeometry(SRID), "CompoundCurve");
    }
    return new CompoundCurve(members, factory);
  }

  private CurvePolygon readCurvePolygon(int SRID)
      throws IOException, ParseException {
    int nRing = readNumField(FIELD_NUMRINGS);
    if (nRing <= 0) return new CurvePolygon(factory);
    LineString shell = asLine(readGeometry(SRID), "CurvePolygon");
    LineString[] holes = new LineString[nRing - 1];
    for (int i = 0; i < holes.length; i++) {
      holes[i] = asLine(readGeometry(SRID), "CurvePolygon");
    }
    return new CurvePolygon(shell, holes, factory);
  }

  private MultiCurve readMultiCurve(int SRID)
      throws IOException, ParseException {
    int n = readNumField(FIELD_NUMELEMS);
    LineString[] members = new LineString[n];
    for (int i = 0; i < n; i++) {
      members[i] = asLine(readGeometry(SRID), "MultiCurve");
    }
    return new MultiCurve(members, factory);
  }

  private MultiSurface readMultiSurface(int SRID)
      throws IOException, ParseException {
    int n = readNumField(FIELD_NUMELEMS);
    Polygon[] members = new Polygon[n];
    for (int i = 0; i < n; i++) {
      Geometry g = readGeometry(SRID);
      if (!(g instanceof Polygon)) {
        throw new ParseException("Invalid geometry type encountered in MultiSurface");
      }
      members[i] = (Polygon) g;
    }
    return new MultiSurface(members, factory);
  }

  private static LineString asLine(Geometry g, String parent)
      throws ParseException {
    if (!(g instanceof LineString)) {
      throw new ParseException("Invalid geometry type encountered in " + parent);
    }
    return (LineString) g;
  }
}

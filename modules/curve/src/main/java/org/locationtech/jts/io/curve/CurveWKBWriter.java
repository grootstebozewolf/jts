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

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.curve.CircularString;
import org.locationtech.jts.geom.curve.ClothoidSegment;
import org.locationtech.jts.geom.curve.CompoundCurve;
import org.locationtech.jts.geom.curve.CurvePolygon;
import org.locationtech.jts.geom.curve.MultiCurve;
import org.locationtech.jts.geom.curve.MultiSurface;
import org.locationtech.jts.geom.impl.CoordinateArraySequence;
import org.locationtech.jts.io.Ordinate;
import org.locationtech.jts.io.OutStream;
import org.locationtech.jts.io.WKBConstants;
import org.locationtech.jts.io.WKBWriter;

/**
 * A {@link WKBWriter} subclass that emits ISO/IEC 13249-3 SQL/MM type
 * codes 8–12 for the curve types. Control points are written as the
 * curve's own coordinates; this path does not call {@code toLinear} /
 * {@code linearise}.
 * <p>
 * Flavour matches GEOS {@code WKBWriter} ({@code setFlavor}):
 * <ul>
 * <li>Default {@link WKBConstants#wkbExtended} — types 8–12 plus EWKB
 * bits {@code 0x80000000} Z / {@code 0x40000000} M /
 * {@code 0x20000000} SRID.</li>
 * <li>{@link WKBConstants#wkbIso} — types 8 / 1008 / 2008 / 3008
 * (and the 9–12 family). ISO has no SRID.</li>
 * </ul>
 * CurvePolygon rings are full child WKB (type header), not bare
 * coordinate sequences. {@link org.locationtech.jts.geom.LinearRing}
 * → LineString is the only type collapse.
 */
public class CurveWKBWriter extends WKBWriter {

  public CurveWKBWriter() {
    super();
  }

  public CurveWKBWriter(int outputDimension) {
    super(outputDimension);
  }

  public CurveWKBWriter(int outputDimension, boolean includeSRID) {
    super(outputDimension, includeSRID);
  }

  public CurveWKBWriter(int outputDimension, int byteOrder) {
    super(outputDimension, byteOrder);
  }

  public CurveWKBWriter(int outputDimension, int byteOrder, boolean includeSRID) {
    super(outputDimension, byteOrder, includeSRID);
  }

  @Override
  protected boolean writeOtherGeometry(Geometry geom,
      EnumSet<Ordinate> outputOrdinates, OutStream os) throws IOException {
    if (geom instanceof CircularString) {
      writeCircularString((CircularString) geom, outputOrdinates, os);
      return true;
    }
    if (geom instanceof CompoundCurve) {
      writeTypedCollection(WKBConstants.wkbCompoundCurve, geom,
          ((CompoundCurve) geom).getMembers(), outputOrdinates, os);
      return true;
    }
    if (geom instanceof CurvePolygon) {
      writeTypedCollection(WKBConstants.wkbCurvePolygon, geom,
          curvePolygonRings((CurvePolygon) geom), outputOrdinates, os);
      return true;
    }
    if (geom instanceof MultiCurve) {
      writeTypedCollection(WKBConstants.wkbMultiCurve, geom,
          children(geom), outputOrdinates, os);
      return true;
    }
    if (geom instanceof MultiSurface) {
      writeTypedCollection(WKBConstants.wkbMultiSurface, geom,
          children(geom), outputOrdinates, os);
      return true;
    }
    if (geom instanceof ClothoidSegment) {
      writeClothoid((ClothoidSegment) geom, outputOrdinates, os);
      return true;
    }
    return false;
  }

  /**
   * CRV-CLOTHOID WKB 18: start point ordinates (no size prefix) then
   * {@code startTangent, startKappa, endKappa, length}.
   */
  private void writeClothoid(ClothoidSegment cl,
      EnumSet<Ordinate> outputOrdinates, OutStream os) throws IOException {
    writeByteOrder(os);
    writeGeometryType(WKBConstants.wkbClothoid, outputOrdinates, cl, os);
    Coordinate start = cl.getStartCoordinate();
    CoordinateArraySequence seq = new CoordinateArraySequence(
        new Coordinate[] { start });
    writeCoordinateSequence(seq, outputOrdinates, false, os);
    writeDouble(cl.getStartTangent(), os);
    writeDouble(cl.getStartKappa(), os);
    writeDouble(cl.getEndKappa(), os);
    writeDouble(cl.getLength(), os);
  }

  private void writeCircularString(CircularString cs,
      EnumSet<Ordinate> outputOrdinates, OutStream os) throws IOException {
    writeByteOrder(os);
    writeGeometryType(WKBConstants.wkbCircularString, outputOrdinates, cs, os);
    writeCoordinateSequence(cs.getCoordinateSequence(), outputOrdinates, true, os);
  }

  private static Geometry[] curvePolygonRings(CurvePolygon cp) {
    if (cp.isEmpty()) return new Geometry[0];
    int nHole = cp.getNumInteriorRing();
    Geometry[] rings = new Geometry[1 + nHole];
    rings[0] = cp.getExteriorCurve();
    for (int i = 0; i < nHole; i++) {
      rings[i + 1] = cp.getInteriorCurveN(i);
    }
    return rings;
  }

  private static Geometry[] children(Geometry g) {
    int n = g.getNumGeometries();
    Geometry[] out = new Geometry[n];
    for (int i = 0; i < n; i++) {
      out[i] = g.getGeometryN(i);
    }
    return out;
  }
}

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
package org.locationtech.jts.io.curved;

import java.io.IOException;
import java.io.Writer;
import java.util.EnumSet;
import java.util.Locale;

import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.curved.CircularString;
import org.locationtech.jts.geom.curved.CompoundCurve;
import org.locationtech.jts.geom.curved.CurvePolygon;
import org.locationtech.jts.io.Ordinate;
import org.locationtech.jts.io.OrdinateFormat;
import org.locationtech.jts.io.WKTConstants;
import org.locationtech.jts.io.WKTWriter;

/**
 * A {@link WKTWriter} subclass for the OGC SFA / ISO 19125-2 extended
 * geometry types.
 *
 * <p>For most curve subclasses the inherited {@link WKTWriter} already
 * does the right thing — it dispatches by parent type and uses
 * {@code Geometry.getGeometryType().toUpperCase(Locale.ROOT)} as the
 * keyword, so e.g. {@code CircularString (1 2, 3 4, 5 6)} comes out
 * correctly via the {@code LineString} formatter.
 *
 * <p>{@link CompoundCurve} is the exception: emitting its concatenated
 * coordinate sequence as a flat {@code COMPOUNDCURVE (x1 y1, …)} loses
 * the segment structure. This writer overrides
 * {@link #appendOtherGeometryTaggedText} to walk
 * {@code CompoundCurve.getMembers()} and emit each member tagged
 * (CIRCULARSTRING) or untagged (raw {@code (…)} for LineString),
 * round-tripping cleanly through {@link CurvedWKTReader}.
 */
public class CurvedWKTWriter extends WKTWriter {

  public CurvedWKTWriter() {
    super();
  }

  public CurvedWKTWriter(int outputDimension) {
    super(outputDimension);
  }

  @Override
  protected boolean appendOtherGeometryTaggedText(
      Geometry geometry, EnumSet<Ordinate> outputOrdinates, boolean useFormatting,
      int level, Writer writer, OrdinateFormat formatter) throws IOException {
    if (geometry instanceof CompoundCurve) {
      appendCompoundCurveTaggedText(
          (CompoundCurve) geometry, outputOrdinates, useFormatting, level, writer, formatter);
      return true;
    }
    if (geometry instanceof CurvePolygon && hasCurvedRing((CurvePolygon) geometry)) {
      appendCurvePolygonTaggedText(
          (CurvePolygon) geometry, outputOrdinates, useFormatting, level, writer, formatter);
      return true;
    }
    return false;
  }

  /**
   * True if any structural ring is a curve. An all-linear CurvePolygon is
   * left to the inherited {@link WKTWriter} polygon formatter, so its output
   * is unchanged.
   */
  private static boolean hasCurvedRing(CurvePolygon cp) {
    if (cp.isEmpty()) return false;
    if (isCurve(cp.getExteriorCurve())) return true;
    for (int i = 0; i < cp.getNumInteriorRing(); i++) {
      if (isCurve(cp.getInteriorCurveN(i))) return true;
    }
    return false;
  }

  private static boolean isCurve(LineString ring) {
    return ring instanceof CircularString || ring instanceof CompoundCurve;
  }

  /**
   * Emits {@code CURVEPOLYGON (<ring>, <ring>, …)} where each ring carries its
   * own tag -- {@code CIRCULARSTRING (…)}, a nested {@code COMPOUNDCURVE (…)},
   * or a bare {@code (…)} for a linear ring -- so the rings round-trip through
   * {@link CurvedWKTReader#readCurvePolygonText}.
   */
  private void appendCurvePolygonTaggedText(
      CurvePolygon cp, EnumSet<Ordinate> outputOrdinates, boolean useFormatting,
      int level, Writer writer, OrdinateFormat formatter) throws IOException {
    writer.write(cp.getGeometryType().toUpperCase(Locale.ROOT));
    writer.write(" ");
    appendOrdinateText(outputOrdinates, writer);
    writer.write("(");
    appendRingText(cp.getExteriorCurve(), outputOrdinates, useFormatting, level, writer, formatter);
    for (int i = 0; i < cp.getNumInteriorRing(); i++) {
      writer.write(", ");
      appendRingText(
          cp.getInteriorCurveN(i), outputOrdinates, useFormatting, level, writer, formatter);
    }
    writer.write(")");
  }

  private void appendRingText(
      LineString ring, EnumSet<Ordinate> outputOrdinates, boolean useFormatting,
      int level, Writer writer, OrdinateFormat formatter) throws IOException {
    if (ring instanceof CompoundCurve) {
      appendCompoundCurveTaggedText(
          (CompoundCurve) ring, outputOrdinates, useFormatting, level, writer, formatter);
      return;
    }
    if (ring instanceof CircularString) {
      writer.write(WKTConstants.CIRCULARSTRING);
      writer.write(" ");
    }
    appendSequenceText(ring.getCoordinateSequence(), outputOrdinates, useFormatting,
        level, false, writer, formatter);
  }

  private void appendCompoundCurveTaggedText(
      CompoundCurve cc, EnumSet<Ordinate> outputOrdinates, boolean useFormatting,
      int level, Writer writer, OrdinateFormat formatter) throws IOException {
    writer.write(cc.getGeometryType().toUpperCase(Locale.ROOT));
    writer.write(" ");
    appendOrdinateText(outputOrdinates, writer);
    if (cc.isEmpty()) {
      writer.write(WKTConstants.EMPTY);
      return;
    }
    writer.write("(");
    int n = cc.getNumMembers();
    for (int i = 0; i < n; i++) {
      if (i > 0) writer.write(", ");
      LineString m = cc.getMemberN(i);
      if (m instanceof CircularString) {
        writer.write(WKTConstants.CIRCULARSTRING);
        writer.write(" ");
      }
      appendSequenceText(m.getCoordinateSequence(), outputOrdinates, useFormatting,
          level, false, writer, formatter);
    }
    writer.write(")");
  }
}

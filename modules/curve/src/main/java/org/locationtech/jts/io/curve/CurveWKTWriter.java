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
import java.io.Writer;
import java.util.EnumSet;
import java.util.Locale;

import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.geom.curve.CircularString;
import org.locationtech.jts.geom.curve.CompoundCurve;
import org.locationtech.jts.geom.curve.CurvePolygon;
import org.locationtech.jts.geom.curve.MultiSurface;
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
 * <p>The exceptions are the composite types, whose structure is lost if their
 * concatenated coordinates are emitted flat. This writer overrides
 * {@link #appendOtherGeometryTaggedText} to walk each one and tag its parts,
 * so they round-trip cleanly through {@link CurveWKTReader}:
 *
 * <ul>
 * <li>{@link CompoundCurve} — walks {@code getMembers()}, emitting each member
 *     tagged ({@code CIRCULARSTRING}) or untagged (bare {@code (…)}).
 * <li>{@link CurvePolygon} — walks the structural rings, emitting each tagged
 *     ({@code CIRCULARSTRING}, nested {@code COMPOUNDCURVE}) or bare.
 * <li>{@link MultiSurface} — emits curved members with their
 *     {@code CURVEPOLYGON} tag so their rings survive.
 * </ul>
 *
 * <p>Each override is gated on actually containing a curve, so an all-linear
 * geometry still goes through the inherited formatter and its output is
 * unchanged.
 */
public class CurveWKTWriter extends WKTWriter {

  public CurveWKTWriter() {
    super();
  }

  public CurveWKTWriter(int outputDimension) {
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
    if (geometry instanceof CurvePolygon && hasCurveRing((CurvePolygon) geometry)) {
      appendCurvePolygonTaggedText(
          (CurvePolygon) geometry, outputOrdinates, useFormatting, level, writer, formatter);
      return true;
    }
    if (geometry instanceof MultiSurface && hasCurveMember((MultiSurface) geometry)) {
      appendMultiSurfaceTaggedText(
          (MultiSurface) geometry, outputOrdinates, useFormatting, level, writer, formatter);
      return true;
    }
    return false;
  }

  private static boolean hasCurveMember(MultiSurface ms) {
    for (int i = 0; i < ms.getNumGeometries(); i++) {
      Geometry m = ms.getGeometryN(i);
      if (m instanceof CurvePolygon && hasCurveRing((CurvePolygon) m)) return true;
    }
    return false;
  }

  /**
   * Emits {@code MULTISURFACE (CURVEPOLYGON (…), (…))} -- curved members carry
   * their {@code CURVEPOLYGON} tag so their rings survive, plain members stay
   * untagged polygon bodies. {@code CurveWKTReader.readSurfaceMember} already
   * accepts both forms.
   */
  private void appendMultiSurfaceTaggedText(
      MultiSurface ms, EnumSet<Ordinate> outputOrdinates, boolean useFormatting,
      int level, Writer writer, OrdinateFormat formatter) throws IOException {
    writer.write(ms.getGeometryType().toUpperCase(Locale.ROOT));
    writer.write(" ");
    appendOrdinateText(outputOrdinates, writer);
    writer.write("(");
    for (int i = 0; i < ms.getNumGeometries(); i++) {
      if (i > 0) writer.write(", ");
      Polygon m = (Polygon) ms.getGeometryN(i);
      if (m instanceof CurvePolygon && hasCurveRing((CurvePolygon) m)) {
        appendCurvePolygonTaggedText(
            (CurvePolygon) m, outputOrdinates, useFormatting, level, writer, formatter);
      } else {
        appendPolygonText(m, outputOrdinates, useFormatting, level, false, writer, formatter);
      }
    }
    writer.write(")");
  }

  /**
   * True if any structural ring is a curve. An all-linear CurvePolygon is
   * left to the inherited {@link WKTWriter} polygon formatter, so its output
   * is unchanged.
   */
  private static boolean hasCurveRing(CurvePolygon cp) {
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
   * {@link CurveWKTReader#readCurvePolygonText}.
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

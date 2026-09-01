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
import org.locationtech.jts.geom.curve.ClothoidSegment;
import org.locationtech.jts.geom.curve.CompoundCurve;
import org.locationtech.jts.geom.curve.CurvePolygon;
import org.locationtech.jts.geom.curve.MultiCurve;
import org.locationtech.jts.geom.curve.MultiSurface;
import org.locationtech.jts.io.Ordinate;
import org.locationtech.jts.io.OrdinateFormat;
import org.locationtech.jts.io.WKTConstants;
import org.locationtech.jts.io.WKTWriter;

/**
 * A {@link WKTWriter} subclass for the ISO/IEC 13249-3 SQL/MM curve
 * types. Keywords are {@code CIRCULARSTRING} / {@code COMPOUNDCURVE} /
 * {@code CURVEPOLYGON} / {@code MULTICURVE} / {@code MULTISURFACE}
 * plus a spaced {@code Z} / {@code M} / {@code ZM} suffix matching
 * GEOS ({@code CIRCULARSTRING Z}, not glued {@code CIRCULARSTRINGZ}).
 * CompoundCurve / MultiCurve / CurvePolygon rings: a LineString is
 * bare {@code (x y, …)}; a CircularString stays tagged.
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
    // Always own CurvePolygon (incl. EMPTY / all-linear). Falling through
    // to core WKTWriter refuses CurvePolygon under SqlMmTypes honesty.
    if (geometry instanceof CurvePolygon) {
      appendCurvePolygonTaggedText(
          (CurvePolygon) geometry, outputOrdinates, useFormatting, level, writer, formatter);
      return true;
    }
    if (geometry instanceof MultiCurve && hasCurveMember((MultiCurve) geometry)) {
      appendMultiCurveTaggedText(
          (MultiCurve) geometry, outputOrdinates, useFormatting, level, writer, formatter);
      return true;
    }
    if (geometry instanceof MultiSurface && hasCurveMember((MultiSurface) geometry)) {
      appendMultiSurfaceTaggedText(
          (MultiSurface) geometry, outputOrdinates, useFormatting, level, writer, formatter);
      return true;
    }
    return false;
  }

  /**
   * GEOS {@code WKTWriter::appendOrdinateText}: {@code Z} / {@code M} /
   * {@code ZM} plus a trailing space so the suffix is
   * {@code CIRCULARSTRING Z (…)}, not glued to the body.
   */
  @Override
  protected void appendOrdinateText(EnumSet<Ordinate> outputOrdinates, Writer writer)
      throws IOException {
    super.appendOrdinateText(outputOrdinates, writer);
    if (outputOrdinates.contains(Ordinate.Z) || outputOrdinates.contains(Ordinate.M)) {
      writer.write(" ");
    }
  }

  private static boolean hasCurveMember(MultiCurve mc) {
    for (int i = 0; i < mc.getNumGeometries(); i++) {
      Geometry m = mc.getGeometryN(i);
      if (m instanceof CircularString || m instanceof CompoundCurve
          || m instanceof ClothoidSegment) return true;
    }
    return false;
  }

  /**
   * Emits {@code MULTICURVE (CIRCULARSTRING (...), (...))} -- arc members carry
   * their tag, compound members nest as {@code COMPOUNDCURVE (...)}, plain
   * members stay bare -- so the members round-trip through
   * {@link CurveWKTReader}. Missing before FCT-COLL because nothing built an
   * arc-bearing MultiCurve through the inherited creators: the factory boxed
   * arcs into plain MultiLineStrings first, and the erasure upstream masked the
   * writer gap downstream. The inherited flat formatter wrote the arc's control
   * points as a straight untagged line, which read back as a plain LineString.
   * An all-plain MultiCurve still uses the inherited formatter unchanged.
   */
  private void appendMultiCurveTaggedText(
      MultiCurve mc, EnumSet<Ordinate> outputOrdinates, boolean useFormatting,
      int level, Writer writer, OrdinateFormat formatter) throws IOException {
    writer.write(mc.getGeometryType().toUpperCase(Locale.ROOT));
    writer.write(" ");
    appendOrdinateText(outputOrdinates, writer);
    if (mc.isEmpty()) {
      writer.write(WKTConstants.EMPTY);
      return;
    }
    writer.write("(");
    for (int i = 0; i < mc.getNumGeometries(); i++) {
      if (i > 0) writer.write(", ");
      LineString m = (LineString) mc.getGeometryN(i);
      if (m instanceof CompoundCurve) {
        appendCompoundCurveTaggedText(
            (CompoundCurve) m, outputOrdinates, useFormatting, level, writer, formatter);
        continue;
      }
      if (m instanceof CircularString) {
        appendCircularStringTag(outputOrdinates, writer);
      }
      appendSequenceText(m.getCoordinateSequence(), outputOrdinates, useFormatting,
          level, false, writer, formatter);
    }
    writer.write(")");
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
    if (cp.isEmpty()) {
      writer.write(WKTConstants.EMPTY);
      return;
    }
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
      appendCircularStringTag(outputOrdinates, writer);
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
      if (m instanceof ClothoidSegment) {
        appendClothoidMemberText((ClothoidSegment) m, formatter, writer);
      }
      else {
        if (m instanceof CircularString) {
          appendCircularStringTag(outputOrdinates, writer);
        }
        appendSequenceText(m.getCoordinateSequence(), outputOrdinates, useFormatting,
            level, false, writer, formatter);
      }
    }
    writer.write(")");
  }

  private void appendCircularStringTag(EnumSet<Ordinate> outputOrdinates, Writer writer)
      throws IOException {
    writer.write(WKTConstants.CIRCULARSTRING);
    writer.write(" ");
    appendOrdinateText(outputOrdinates, writer);
  }

  /** grammars-v4 member form: {@code CLOTHOID (k0, k1, L)}. Never top-level. */
  private void appendClothoidMemberText(ClothoidSegment cs,
      OrdinateFormat formatter, Writer writer) throws IOException {
    writer.write("CLOTHOID (");
    writer.write(formatter.format(cs.getStartKappa()));
    writer.write(", ");
    writer.write(formatter.format(cs.getEndKappa()));
    writer.write(", ");
    writer.write(formatter.format(cs.getLength()));
    writer.write(")");
  }
}

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
import org.locationtech.jts.geom.curve.MultiCurve;
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
 * <p>This subclass uses the {@link #appendOtherGeometryTaggedText}
 * extension hook to handle the composite types whose flat-coordinate
 * emission would otherwise lose member structure:
 *
 * <ul>
 *   <li><b>COMPOUNDCURVE</b> — walks {@code getMembers()} and emits each
 *       member tagged (CIRCULARSTRING / CLOTHOID) or untagged (raw
 *       {@code (…)} for plain LineString), round-tripping cleanly
 *       through {@link CurveWKTReader}.</li>
 *   <li><b>MULTICURVE</b> — emits {@code CIRCULARSTRING (…)} or
 *       {@code COMPOUNDCURVE (…)} for non-plain members, body-only
 *       parentheses for plain {@code LineString} members.</li>
 *   <li><b>MULTISURFACE</b> — emits {@code CURVEPOLYGON (…)} for
 *       {@code CurvePolygon} members, body-only parentheses for plain
 *       {@code Polygon} members.</li>
 * </ul>
 *
 * <p>Closes F-MC-WKT and F-MS-WKT (locationtech/jts#1195). The
 * reader-side preservation of member subtypes already works on the
 * current Phase-1 base via {@code CurveWKTReader.readCurveMember /
 * readSurfaceMember}; only the writer side needed to learn.
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
    if (geometry instanceof MultiCurve) {
      writeMultiCurveText((MultiCurve) geometry, outputOrdinates, writer);
      return true;
    }
    if (geometry instanceof MultiSurface) {
      writeMultiSurfaceText((MultiSurface) geometry, outputOrdinates, writer);
      return true;
    }
    return false;
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
        appendClothoidSegmentText((ClothoidSegment) m, formatter, writer);
        continue;
      }
      if (m instanceof CircularString) {
        writer.write(WKTConstants.CIRCULARSTRING);
        writer.write(" ");
      }
      appendSequenceText(m.getCoordinateSequence(), outputOrdinates, useFormatting,
          level, false, writer, formatter);
    }
    writer.write(")");
  }

  /** JTS extension: {@code CLOTHOID(k0, k1, L)}. See grammars-v4 #4847. */
  private void appendClothoidSegmentText(ClothoidSegment cs,
      OrdinateFormat formatter, Writer writer) throws IOException {
    writer.write("CLOTHOID (");
    writer.write(formatter.format(cs.getStartKappa()));
    writer.write(", ");
    writer.write(formatter.format(cs.getEndKappa()));
    writer.write(", ");
    writer.write(formatter.format(cs.getLength()));
    writer.write(")");
  }

  private void writeMultiCurveText(MultiCurve mc, EnumSet<Ordinate> outputOrdinates, Writer writer)
      throws IOException {
    writer.write(mc.getGeometryType().toUpperCase(Locale.ROOT));
    appendOrdinateText(outputOrdinates, writer);
    if (mc.isEmpty()) {
      writer.write(" EMPTY");
      return;
    }
    writer.write(" (");
    for (int i = 0; i < mc.getNumGeometries(); i++) {
      if (i > 0) writer.write(", ");
      Geometry m = mc.getGeometryN(i);
      // Plain LineString members emit body-only (OGC SFA conventional).
      // Curve-typed members emit the tagged form so the reader recovers
      // their subtype on round-trip.
      if (m.getClass() == LineString.class) {
        writer.write(emitBodyOnly(m));
      } else {
        writer.write(new CurveWKTWriter().write(m));
      }
    }
    writer.write(")");
  }

  private void writeMultiSurfaceText(MultiSurface ms, EnumSet<Ordinate> outputOrdinates, Writer writer)
      throws IOException {
    writer.write(ms.getGeometryType().toUpperCase(Locale.ROOT));
    appendOrdinateText(outputOrdinates, writer);
    if (ms.isEmpty()) {
      writer.write(" EMPTY");
      return;
    }
    writer.write(" (");
    for (int i = 0; i < ms.getNumGeometries(); i++) {
      if (i > 0) writer.write(", ");
      Geometry m = ms.getGeometryN(i);
      if (m.getClass() == Polygon.class) {
        writer.write(emitBodyOnly(m));
      } else {
        writer.write(new CurveWKTWriter().write(m));
      }
    }
    writer.write(")");
  }

  /**
   * Body-only emission for plain LineString / Polygon members.
   * Strips the leading keyword (e.g. "LINESTRING ", "POLYGON ") so that
   * the result reads as a bare parenthesised body within a multi-composite.
   */
  private static String emitBodyOnly(Geometry plainMember) {
    String full = new CurveWKTWriter().write(plainMember);
    int firstParen = full.indexOf('(');
    return firstParen < 0 ? full : full.substring(firstParen);
  }
}

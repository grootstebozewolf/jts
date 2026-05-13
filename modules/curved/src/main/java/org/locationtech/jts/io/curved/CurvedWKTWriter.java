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
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.geom.curved.MultiCurve;
import org.locationtech.jts.geom.curved.MultiSurface;
import org.locationtech.jts.io.Ordinate;
import org.locationtech.jts.io.OrdinateFormat;
import org.locationtech.jts.io.WKTWriter;

/**
 * A {@link WKTWriter} subclass for the OGC SFA / ISO 19125-2 extended
 * geometry types.
 *
 * <p>Phase-1 keyword emission is inherited from the core writer
 * (which now reads the keyword from
 * {@code Geometry.getGeometryType().toUpperCase(Locale.ROOT)}, courtesy
 * of PR #1194). What this subclass adds, via the
 * {@link #appendOtherGeometryTaggedText} extension hook, is
 * <em>member-tag preservation</em> for the multi-composite types:
 *
 * <ul>
 *   <li><b>MULTICURVE</b> — emits {@code CIRCULARSTRING (…)} or
 *       {@code COMPOUNDCURVE (…)} for non-plain members, body-only
 *       parentheses for plain {@code LineString} members.</li>
 *   <li><b>MULTISURFACE</b> — emits {@code CURVEPOLYGON (…)} for
 *       {@code CurvePolygon} members, body-only parentheses for plain
 *       {@code Polygon} members.</li>
 * </ul>
 *
 * <p>This closes F-MC-WKT and F-MS-WKT (locationtech/jts#1195). The
 * reader-side preservation of member subtypes already works on the
 * current Phase-1 base via {@code CurvedWKTReader.readCurveMember /
 * readSurfaceMember}; only the writer side needed to learn.
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
        writer.write(new CurvedWKTWriter().write(m));
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
        writer.write(new CurvedWKTWriter().write(m));
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
    String full = new CurvedWKTWriter().write(plainMember);
    int firstParen = full.indexOf('(');
    return firstParen < 0 ? full : full.substring(firstParen);
  }
}

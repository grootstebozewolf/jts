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
 * <p>
 * For top-level curved types the core writer already uses {@code getGeometryType()}
 * so they round-trip their keyword. This subclass overrides the extension hook
 * to emit structural CurvePolygon rings (preserving CIRCULARSTRING / COMPOUNDCURVE
 * tags for curved rings inside the CURVEPOLYGON body) and to ensure COMPOUNDCURVE
 * (top-level or as ring) is always emitted in OGC-conformant member-structured form
 * COMPOUNDCURVE ( ( ... ) ) rather than the non-standard flat coordinate list form.
 */
public class CurvedWKTWriter extends WKTWriter {

  public CurvedWKTWriter() {
    super();
  }

  public CurvedWKTWriter(int outputDimension) {
    super(outputDimension);
  }

  @Override
  protected boolean appendOtherGeometryTaggedText(Geometry geometry,
      EnumSet<Ordinate> outputOrdinates, boolean useFormatting,
      int level, Writer writer, OrdinateFormat formatter) throws IOException {
    if (geometry instanceof CurvePolygon) {
      appendCurvePolygonTaggedText((CurvePolygon) geometry, outputOrdinates,
          useFormatting, level, writer, formatter);
      return true;
    } else if (geometry instanceof CompoundCurve) {
      appendCompoundCurveTaggedText((CompoundCurve) geometry, outputOrdinates,
          useFormatting, level, writer, formatter);
      return true;
    }
    return false;
  }

  private void appendCurvePolygonTaggedText(CurvePolygon polygon,
      EnumSet<Ordinate> outputOrdinates, boolean useFormatting,
      int level, Writer writer, OrdinateFormat formatter) throws IOException {
    writer.write(polygon.getGeometryType().toUpperCase(Locale.ROOT));
    writer.write(" ");
    appendOrdinateText(outputOrdinates, writer);
    appendCurvePolygonText(polygon, outputOrdinates, useFormatting, level, false, writer, formatter);
  }

  private void appendCurvePolygonText(CurvePolygon polygon,
      EnumSet<Ordinate> outputOrdinates, boolean useFormatting,
      int level, boolean indentFirst, Writer writer, OrdinateFormat formatter)
      throws IOException {
    if (polygon.isEmpty()) {
      writer.write(WKTConstants.EMPTY);
      return;
    }
    if (indentFirst) indent(useFormatting, level, writer);
    writer.write("(");
    LineString shell = polygon.getExteriorCurve();
    if (shell != null) {
      appendCurveRingText(shell, outputOrdinates, useFormatting, level, false, writer, formatter);
    }
    for (int i = 0; i < polygon.getNumInteriorRing(); i++) {
      writer.write(", ");
      appendCurveRingText(polygon.getInteriorCurveN(i), outputOrdinates, useFormatting,
          level + 1, true, writer, formatter);
    }
    writer.write(")");
  }

  private void appendCompoundCurveTaggedText(CompoundCurve cc,
      EnumSet<Ordinate> outputOrdinates, boolean useFormatting,
      int level, Writer writer, OrdinateFormat formatter) throws IOException {
    writer.write(cc.getGeometryType().toUpperCase(Locale.ROOT));
    writer.write(" ");
    appendOrdinateText(outputOrdinates, writer);
    writer.write(" (");
    LineString[] mems = cc.getCurves();
    for (int i = 0; i < mems.length; i++) {
      if (i > 0) {
        writer.write(", ");
      }
      LineString m = mems[i];
      if (m instanceof CircularString) {
        writer.write("CIRCULARSTRING ");
        appendOrdinateText(outputOrdinates, writer);
        // appendSequence provides the opening ( for coords
        appendSequenceText(m.getCoordinateSequence(), outputOrdinates, useFormatting,
            level, false, writer, formatter);
      } else if (m instanceof CompoundCurve) {
        // recurse for nested
        appendCompoundCurveTaggedText((CompoundCurve) m, outputOrdinates, useFormatting, level, writer, formatter);
      } else {
        // plain linear member: appendSequence provides ( pts )
        appendSequenceText(m.getCoordinateSequence(), outputOrdinates, useFormatting,
            level, false, writer, formatter);
      }
    }
    writer.write(")");
  }

  /**
   * Emit a ring position inside CURVEPOLYGON: for a curved ring (CircularString or
   * CompoundCurve) emit its tagged form e.g. "CIRCULARSTRING (pts)" or "COMPOUNDCURVE ( (pts), ... )";
   * for a linear one emit plain "(pts)".
   * <p>
   * COMPOUNDCURVE now emits full member structure for OGC SQL/MM conformance.
   */
  private void appendCurveRingText(LineString ring,
      EnumSet<Ordinate> outputOrdinates, boolean useFormatting,
      int level, boolean indentFirst, Writer writer, OrdinateFormat formatter)
      throws IOException {
    boolean isCurvedRing = ring.getGeometryType().equalsIgnoreCase("CircularString")
        || ring.getGeometryType().equalsIgnoreCase("CompoundCurve");
    if (isCurvedRing) {
      if (indentFirst) indent(useFormatting, level, writer);
      writer.write(ring.getGeometryType().toUpperCase(Locale.ROOT));
      if (ring instanceof CompoundCurve) {
        CompoundCurve cc = (CompoundCurve) ring;
        writer.write(" (");
        LineString[] mems = cc.getCurves();
        for (int k = 0; k < mems.length; k++) {
          if (k > 0) {
            writer.write(", ");
          }
          LineString m = mems[k];
          if (m instanceof CircularString) {
            writer.write("CIRCULARSTRING ");
            // NO appendOrdinateText here: dim qualifier only on outer CURVEPOLYGON, not repeated on inner ring members (per test and spec)
            // appendSequence provides the (
            appendSequenceText(m.getCoordinateSequence(), outputOrdinates, useFormatting,
                level, false, writer, formatter);
          } else {
            // line sub: append provides (
            appendSequenceText(m.getCoordinateSequence(), outputOrdinates, useFormatting,
                level, false, writer, formatter);
          }
        }
        writer.write(")");
      } else {
        // CIRCULARSTRING ring: "CIRCULARSTRING (pts...)"
        // Dim qualifier is only on the containing CURVEPOLYGON (standard WKT).
        writer.write(" ");
        appendSequenceText(ring.getCoordinateSequence(), outputOrdinates, useFormatting,
            level, false, writer, formatter);
      }
    } else {
      // Linear ring body: delegate to appendSequenceText which emits the parenthesized (coords)
      appendSequenceText(ring.getCoordinateSequence(), outputOrdinates, useFormatting,
          level, indentFirst, writer, formatter);
    }
  }
}

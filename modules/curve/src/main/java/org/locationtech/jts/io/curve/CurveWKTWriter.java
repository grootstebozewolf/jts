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
import org.locationtech.jts.geom.curve.CircularString;
import org.locationtech.jts.geom.curve.CompoundCurve;
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
 * round-tripping cleanly through {@link CurveWKTReader}.
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

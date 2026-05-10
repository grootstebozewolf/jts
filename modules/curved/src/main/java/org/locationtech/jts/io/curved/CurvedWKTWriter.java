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
import org.locationtech.jts.io.Ordinate;
import org.locationtech.jts.io.OrdinateFormat;
import org.locationtech.jts.io.WKTConstants;
import org.locationtech.jts.io.WKTWriter;

/**
 * A {@link WKTWriter} subclass for the OGC SFA / ISO 19125-2 extended
 * geometry types.
 *
 * <p>For most types the core writer already emits the right keyword
 * (because keyword emission was parameterised by
 * {@code Geometry.getGeometryType().toUpperCase()} in the extension-points
 * commit). This subclass intercepts the cases where the structural body
 * differs from the parent type — currently {@link CompoundCurve}, whose
 * SFA emission is a list of <em>tagged</em> members rather than a flat
 * coordinate sequence.
 */
public class CurvedWKTWriter extends WKTWriter {

  public CurvedWKTWriter() {
    super();
  }

  public CurvedWKTWriter(int outputDimension) {
    super(outputDimension);
  }

  @Override
  protected boolean appendOtherGeometryTaggedText(Geometry geometry, EnumSet<Ordinate> outputOrdinates,
      boolean useFormatting, int level, Writer writer, OrdinateFormat formatter) throws IOException {
    if (geometry instanceof CompoundCurve) {
      appendCompoundCurveTaggedText((CompoundCurve) geometry, outputOrdinates, useFormatting,
          level, writer, formatter);
      return true;
    }
    return false;
  }

  /**
   * Emits {@code COMPOUNDCURVE [Z|M|ZM] (member, member, ...)} where
   * each member is either an untagged {@code (...)} for a plain
   * {@link LineString} or a tagged {@code CIRCULARSTRING(...)} for a
   * {@link CircularString}. Inner ordinate text is omitted on each
   * member; the dimension flag is carried by the outer keyword only,
   * per OGC SFA convention.
   */
  private void appendCompoundCurveTaggedText(CompoundCurve cc, EnumSet<Ordinate> outputOrdinates,
      boolean useFormatting, int level, Writer writer, OrdinateFormat formatter) throws IOException {
    writer.write(cc.getGeometryType().toUpperCase(Locale.ROOT));
    writer.write(" ");
    appendOrdinateText(outputOrdinates, writer);
    if (cc.isEmpty()) {
      writer.write(WKTConstants.EMPTY);
      return;
    }
    writer.write("(");
    for (int i = 0; i < cc.getNumCurves(); i++) {
      if (i > 0) writer.write(", ");
      LineString member = cc.getCurveN(i);
      if (member instanceof CircularString) {
        writer.write("CIRCULARSTRING ");
      }
      // Body only: no per-member ordinate modifier, no nested keyword
      // for plain LineStrings.
      appendSequenceText(member.getCoordinateSequence(), outputOrdinates,
          useFormatting, level, false, writer, formatter);
    }
    writer.write(")");
  }
}

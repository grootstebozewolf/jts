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
package org.locationtech.jtstest.testbuilder.ui.tools;

import java.awt.Shape;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.curve.CurveGeometryFactory;
import org.locationtech.jts.io.ParseException;
import org.locationtech.jts.io.curve.CurveWKTReader;

import junit.framework.TestCase;
import junit.textui.TestRunner;

/**
 * UX issue #101: EditVertex drag preview of a GeometryCollection of
 * CircularString must rubber-band arcs via CurveShapeWriter, not chords
 * to adjacent controls. Same decision as
 * {@code EditVertexTool.circularStringPreviewShape}.
 */
public class EditVertexToolCurvePreviewTest extends TestCase {

  private static final Coordinate FROM = new Coordinate(1, 1);
  private static final Coordinate TO = new Coordinate(1, 2);

  public EditVertexToolCurvePreviewTest(String name) {
    super(name);
  }

  public static void main(String[] args) {
    TestRunner.run(EditVertexToolCurvePreviewTest.class);
  }

  public void testGeometryCollectionOfCircularStringUsesArcPreview()
      throws ParseException {
    Geometry g = read("GEOMETRYCOLLECTION (CIRCULARSTRING (0 0, 1 1, 2 0))");
    assertTrue("GC of CIRCULARSTRING must use CurveShapeWriter preview",
        EditVertexTool.usesCurveDragPreview(g));
    Shape preview = EditVertexTool.curveDragPreviewShape(g, FROM, TO);
    assertNotNull("GC of CIRCULARSTRING drag preview must be arcs, not chords",
        preview);
  }

  public void testBareCircularStringStillUsesArcPreview() throws ParseException {
    Geometry g = read("CIRCULARSTRING (0 0, 1 1, 2 0)");
    assertTrue(EditVertexTool.usesCurveDragPreview(g));
    assertNotNull(EditVertexTool.curveDragPreviewShape(g, FROM, TO));
  }

  public void testMultiCurveOfCircularStringUsesArcPreview() throws ParseException {
    Geometry g = read("MULTICURVE (CIRCULARSTRING (0 0, 1 1, 2 0))");
    assertTrue("MultiCurve of CIRCULARSTRING must use CurveShapeWriter preview",
        EditVertexTool.usesCurveDragPreview(g));
    assertNotNull(EditVertexTool.curveDragPreviewShape(g, FROM, TO));
  }

  public void testLineStringKeepsChordPreview() throws ParseException {
    Geometry g = read("LINESTRING (0 0, 1 1, 2 0)");
    assertFalse("LINESTRING must keep adjacent-control chord rubber-band",
        EditVertexTool.usesCurveDragPreview(g));
    assertNull(EditVertexTool.curveDragPreviewShape(g, FROM, TO));
  }

  private static Geometry read(String wkt) throws ParseException {
    return new CurveWKTReader(new CurveGeometryFactory()).read(wkt);
  }
}

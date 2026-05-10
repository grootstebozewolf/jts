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
package org.locationtech.jtstest.util;

import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.curved.CurvedGeometryFactory;
import org.locationtech.jtstest.util.io.MultiFormatReader;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;
import junit.textui.TestRunner;

/**
 * Regression tests for the WKT-paste pipeline used by the TestBuilder
 * Load button: {@code WKTPanel.getGeometryTextClean} -&gt;
 * {@link GeometryTextCleaner#cleanWKT} -&gt;
 * {@link MultiFormatReader#read} -&gt;
 * {@code IOUtil.readWKTString}.
 *
 * <p>Comments must be stripped <em>before</em> the character filter runs:
 * the filter removes {@code /} and {@code *} (neither is valid WKT),
 * which would orphan a {@code /* ... *}{@code /} block and leak the
 * comment body — including any human ellipsis — into the parser.
 */
public class GeometryTextCleanerTest extends TestCase {

  public static void main(String[] args) { TestRunner.run(suite()); }
  public static Test suite() { return new TestSuite(GeometryTextCleanerTest.class); }
  public GeometryTextCleanerTest(String name) { super(name); }

  public void testCleanWKT_stripsBlockCommentsBeforeFilter() {
    String src =
        "/* legend */\n" +
        "GEOMETRYCOLLECTION (\n" +
        "  POINT (1 2),\n" +
        "  /* extra members ... would go here */\n" +
        "  MULTIPOINT ((10 20), (30 40))\n" +
        ")";
    String cleaned = GeometryTextCleaner.cleanWKT(src);
    assertFalse("comment body 'legend' must not survive cleaning",
        cleaned.toLowerCase().contains("legend"));
    assertFalse("ellipsis inside comment must not survive cleaning",
        cleaned.contains("..."));
    assertTrue("WKT body must survive cleaning",
        cleaned.contains("GEOMETRYCOLLECTION"));
    assertTrue(cleaned.contains("MULTIPOINT"));
  }

  public void testCleanWKT_stripsLineComments() {
    String src =
        "GEOMETRYCOLLECTION (\n" +
        "  -- the J\n" +
        "  CIRCULARSTRING (38 248, 15 195, 48 125),\n" +
        "  LINESTRING (48 248, 48 55) -- vertical bar\n" +
        ")";
    String cleaned = GeometryTextCleaner.cleanWKT(src);
    assertFalse("line-comment body must not survive cleaning",
        cleaned.toLowerCase().contains("vertical bar"));
    assertFalse("line-comment body must not survive cleaning",
        cleaned.toLowerCase().contains("the j"));
  }

  /**
   * Mirrors the user-reported NPE/parse-failure trace: pasting WKT that
   * contains a {@code /* ... *}{@code /} block with a literal {@code ...}
   * inside.  Pre-fix, the filter removed the comment delimiters first
   * and the {@code ...} leaked into the MULTIPOINT, producing
   * {@code Invalid number: ... (line 14)}.
   */
  public void testPanelPath_blockCommentWithEllipsis_parses() throws Exception {
    String pasted =
        "/* JTS legend\n" +
        "   line 2\n" +
        "   line 3 ...\n" +
        "*/\n" +
        "GEOMETRYCOLLECTION (\n" +
        "  POINT (1 2),\n" +
        "  /* skip a few ... */\n" +
        "  LINESTRING (3 4, 5 6),\n" +
        "  /* tail ... ellipsis ... */\n" +
        "  MULTIPOINT ((10 20), (30 40))\n" +
        ")";
    String cleaned = GeometryTextCleaner.cleanWKT(pasted);
    Geometry g = new MultiFormatReader(new CurvedGeometryFactory()).read(cleaned);
    assertNotNull(g);
    assertEquals("GeometryCollection", g.getGeometryType());
    assertEquals(3, g.getNumGeometries());
  }

  public void testCleanWKT_preservesNegatives() {
    String src = "LINESTRING (-1 -2, -3 -4)";
    String cleaned = GeometryTextCleaner.cleanWKT(src);
    assertEquals(src, cleaned);
  }
}

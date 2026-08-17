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
package org.locationtech.jtstest.testbuilder.appium;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.curve.CurveGeometryFactory;
import org.locationtech.jts.io.curve.CurveWKTReader;
import org.locationtech.jtstest.geomfunction.GeometryFunction;
import org.locationtech.jtstest.geomfunction.GeometryFunctionRegistry;
import org.locationtech.jtstest.testbuilder.ui.AutomationIds;

import junit.framework.TestCase;
import junit.textui.TestRunner;

/**
 * PR #7 playback of Appium sequences: every non-skipped {@code *.pr7.json}
 * is invoked on its disc/circle/half-moon fixture via the Function registry
 * (same apply path as Function-tree Exec). JSON steps remain the Appium
 * click contract ({@link AutomationIds}).
 */
public class TbAppiumPr7PlaybackTest extends TestCase {

  public static void main(String[] args) {
    TestRunner.run(TbAppiumPr7PlaybackTest.class);
  }

  public TbAppiumPr7PlaybackTest(String name) {
    super(name);
  }

  public void testAllNonSkippedPr7SequencesPlayback() throws Exception {
    File root = TbAppiumPaths.seqRoot();
    List files = listPr7(root);
    assertTrue("expected pr7 sequences", files.size() >= 100);

    GeometryFunctionRegistry reg = GeometryFunctionRegistry.createTestBuilderRegistry();
    CurveWKTReader reader = new CurveWKTReader(new CurveGeometryFactory());

    int played = 0;
    int skippedJson = 0;
    StringBuffer fails = new StringBuffer();

    for (int i = 0; i < files.size(); i++) {
      File f = (File) files.get(i);
      String json = TbAppiumPaths.readFile(f);
      if (json.contains("\"skipped\": true")) {
        skippedJson++;
        continue;
      }
      assertTrue(f.getName() + " must include fn.tree",
          json.contains(AutomationIds.FN_TREE));
      assertTrue(f.getName() + " must include fn.exec",
          json.contains(AutomationIds.FN_EXEC));

      String cat = field(json, "category");
      String fn = field(json, "function");
      String fixRel = field(json, "fixtureFile");
      assertNotNull(f.getName(), cat);
      assertNotNull(f.getName(), fn);
      assertNotNull(f.getName(), fixRel);

      File fix = new File(root, fixRel.replaceFirst("^_fixtures/", "_fixtures/"));
      if (!fix.isFile()) {
        // fixtureFile is "_fixtures/pr7-disc.wkt"
        fix = new File(new File(root, "_fixtures"),
            fixRel.substring(fixRel.lastIndexOf('/') + 1));
      }
      assertTrue(f.getName() + " missing fixture " + fix, fix.isFile());

      Geometry g = reader.read(TbAppiumPaths.readFile(fix).trim());
      GeometryFunction gf = reg.find(cat, fn);
      if (gf == null) {
        fails.append("NOT_FOUND ").append(f.getName()).append(' ')
            .append(cat).append('.').append(fn).append('\n');
        continue;
      }
      try {
        Object r = invoke(gf, g);
        if (!(r instanceof Geometry)) {
          fails.append("NON_GEOM ").append(f.getName()).append('\n');
          continue;
        }
        Geometry out = (Geometry) r;
        // Empty allowed for some ops; still counts as playback
        played++;
        assertNotNull(out);
      } catch (Throwable ex) {
        fails.append("EXC ").append(f.getName()).append(' ')
            .append(ex.getClass().getSimpleName()).append(": ")
            .append(ex.getMessage()).append('\n');
      }
    }

    assertTrue("played=" + played + " skippedJson=" + skippedJson
        + " fails=\n" + fails, fails.length() == 0);
    assertTrue("expected substantial pr7 playback, played=" + played, played >= 100);
  }

  private static List listPr7(File root) {
    List out = new ArrayList();
    listPr7Rec(root, out);
    return out;
  }

  private static void listPr7Rec(File dir, List out) {
    File[] kids = dir.listFiles();
    if (kids == null) return;
    for (int i = 0; i < kids.length; i++) {
      if (kids[i].isDirectory()) {
        if ("_catalog".equals(kids[i].getName()) || "_fixtures".equals(kids[i].getName())) {
          continue;
        }
        listPr7Rec(kids[i], out);
      } else if (kids[i].getName().endsWith(".pr7.json")) {
        out.add(kids[i]);
      }
    }
  }

  private static Object invoke(GeometryFunction f, Geometry g) throws Exception {
    Class[] pt = f.getParameterTypes();
    List args = new ArrayList();
    for (int i = 0; i < pt.length; i++) {
      if (pt[i] == Geometry.class) {
        args.add(g);
      } else if (pt[i] == double.class || pt[i] == Double.class) {
        args.add(Double.valueOf(1.0));
      } else if (pt[i] == int.class || pt[i] == Integer.class) {
        args.add(Integer.valueOf(2));
      } else if (pt[i] == String.class) {
        args.add("A");
      } else if (pt[i] == boolean.class || pt[i] == Boolean.class) {
        args.add(Boolean.FALSE);
      } else {
        args.add(null);
      }
    }
    return f.invoke(g, args.toArray());
  }

  private static String field(String json, String name) {
    String key = "\"" + name + "\"";
    int i = json.indexOf(key);
    if (i < 0) return null;
    int colon = json.indexOf(':', i);
    int q1 = json.indexOf('"', colon + 1);
    if (q1 < 0) return null;
    int q2 = json.indexOf('"', q1 + 1);
    return json.substring(q1 + 1, q2);
  }
}

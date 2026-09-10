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

import org.locationtech.jtstest.testbuilder.ui.AutomationIds;

import junit.framework.TestCase;
import junit.textui.TestRunner;

/**
 * Locks Appium AutomationId click contracts under {@code doc/appium-sequences/}.
 * JSON is the suite source of truth (Appium-compatible {@code jts.tb.*} ids).
 */
public class TbAppiumSequenceContractTest extends TestCase {

  public static void main(String[] args) {
    TestRunner.run(TbAppiumSequenceContractTest.class);
  }

  public TbAppiumSequenceContractTest(String name) {
    super(name);
  }

  public void testFixturesExist() {
    assertTrue(TbAppiumPaths.fixture("upstream-polygon.wkt").isFile());
    assertTrue(TbAppiumPaths.fixture("upstream-linestring.wkt").isFile());
    assertTrue(TbAppiumPaths.fixture("pr7-disc.wkt").isFile());
    assertTrue(TbAppiumPaths.fixture("pr7-circle.wkt").isFile());
    assertTrue(TbAppiumPaths.fixture("pr7-half-moon.wkt").isFile());
  }

  public void testAffineTranslateSequencesHaveRequiredIds() throws Exception {
    File dir = new File(TbAppiumPaths.seqRoot(), "AffineTransformation");
    assertTrue(dir.isDirectory());
    File[] files = dir.listFiles();
    assertNotNull(files);
    int n = 0;
    for (int i = 0; i < files.length; i++) {
      if (!files[i].getName().endsWith(".json")) continue;
      n++;
      String json = TbAppiumPaths.readFile(files[i]);
      assertTrue(files[i].getName() + " must cite claimId",
          json.contains("TB-AP-AFFINE-TRANSLATE"));
      assertTrue(files[i].getName() + " must click fn.tree",
          json.contains(AutomationIds.FN_TREE));
      assertTrue(files[i].getName() + " must click fn.exec",
          json.contains(AutomationIds.FN_EXEC));
      assertTrue(files[i].getName() + " must set param.0",
          json.contains(AutomationIds.FN_PARAM_0));
      assertTrue(files[i].getName() + " must set param.1",
          json.contains(AutomationIds.FN_PARAM_1));
      assertTrue(files[i].getName() + " must touch wkt.a",
          json.contains(AutomationIds.WKT_A));
      assertTrue(files[i].getName() + " must load",
          json.contains(AutomationIds.WKT_LOAD));
      assertTrue(files[i].getName() + " must select translate",
          json.contains("\"translate\""));
    }
    assertTrue("expected Affine translate sequences", n >= 4);
  }

  public void testUpstreamGoldenMarked() throws Exception {
    String json = TbAppiumPaths.readFile(TbAppiumPaths.sequence(
        "AffineTransformation", "translate.polygon.upstream.json"));
    assertTrue(json.contains("\"golden\": true"));
    assertTrue(json.contains("\"branch\": \"upstream\""));
    assertFalse(json.contains("\"skipped\": true"));
  }

  public void testPr7PlaybackSetCoversDiscCircleHalfMoon() throws Exception {
    List names = new ArrayList();
    File dir = new File(TbAppiumPaths.seqRoot(), "AffineTransformation");
    File[] files = dir.listFiles();
    for (int i = 0; i < files.length; i++) {
      if (files[i].getName().endsWith(".pr7.json")) {
        names.add(files[i].getName());
      }
    }
    assertTrue(names.toString(), names.contains("translate.disc.pr7.json"));
    assertTrue(names.toString(), names.contains("translate.circle.pr7.json"));
    assertTrue(names.toString(), names.contains("translate.half-moon.pr7.json"));
  }
}

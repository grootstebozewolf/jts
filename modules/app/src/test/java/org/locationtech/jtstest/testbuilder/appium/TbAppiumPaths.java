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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * Resolves {@code doc/appium-sequences} whether Surefire cwd is the repo
 * root or {@code modules/app}.
 */
final class TbAppiumPaths {

  private TbAppiumPaths() {
  }

  static File seqRoot() {
    File here = new File("doc/appium-sequences");
    if (here.isDirectory()) return here;
    File up = new File("../..", "doc/appium-sequences");
    if (up.isDirectory()) return up;
    File abs = new File("/workspace/doc/appium-sequences");
    if (abs.isDirectory()) return abs;
    return here;
  }

  static File fixture(String name) {
    return new File(new File(seqRoot(), "_fixtures"), name);
  }

  static File sequence(String category, String file) {
    return new File(new File(seqRoot(), category), file);
  }

  static String readFile(File f) throws Exception {
    StringBuilder sb = new StringBuilder();
    BufferedReader br = new BufferedReader(
        new InputStreamReader(new FileInputStream(f), StandardCharsets.UTF_8));
    try {
      String line;
      while ((line = br.readLine()) != null) {
        sb.append(line).append('\n');
      }
    } finally {
      br.close();
    }
    return sb.toString();
  }
}

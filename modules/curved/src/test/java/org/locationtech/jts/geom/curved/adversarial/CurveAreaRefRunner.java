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
package org.locationtech.jts.geom.curved.adversarial;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.curved.CurvedGeometryFactory;
import org.locationtech.jts.io.curved.CurvedWKTReader;

/**
 * Ref runner for curve area (M-AREA-CP). Loads ring vectors + expected area.
 * Authoritative ref is Java BigDecimal (or proofs ARC_AREA for arc segs).
 * Used to assert getArea() deltas are 0.0 post-harden.
 * <p>
 * Hardened using oracle-bin-linux artifact run 26887314315/art 7385761173
 * (ARC_AREA / ARC_AREA_INVARIANTS_EXACT + AngleBetween for theta/sin contribs).
 */
public final class CurveAreaRefRunner {

  private CurveAreaRefRunner() {}

  public static final class AreaCase {
    public final String wkt; // or point list
    public final double expectedArea;

    public AreaCase(String wkt, double expected) {
      this.wkt = wkt;
      this.expectedArea = expected;
    }
  }

  public static List<AreaCase> loadAreaCases(InputStream in) throws IOException {
    List<AreaCase> cases = new ArrayList<>();
    BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
    String line; int lineNo = 0;
    while ((line = r.readLine()) != null) {
      lineNo++;
      String s = line.trim();
      if (s.isEmpty() || s.startsWith("#")) continue;
      // last token expected, preceding tokens are coords or a WKT-like
      // For simplicity support "x y x y ... expected" or full WKT in one token? here coord form
      String[] tok = s.split("\\s+");
      if (tok.length < 3) continue;
      double exp = Double.parseDouble(tok[tok.length - 1]);
      // build a simple WKT POLYGON from the coords for read
      StringBuilder w = new StringBuilder("POLYGON ((");
      for (int i = 0; i < tok.length - 1; i += 2) {
        if (i > 0) w.append(", ");
        w.append(tok[i]).append(" ").append(tok[i + 1]);
      }
      w.append("))");
      cases.add(new AreaCase(w.toString(), exp));
    }
    return cases;
  }

  public static List<AreaCase> loadAreaCases(String resourcePath) throws IOException {
    try (InputStream is = CurveAreaRefRunner.class.getResourceAsStream(resourcePath)) {
      if (is == null) throw new IOException("resource not found: " + resourcePath);
      return loadAreaCases(is);
    }
  }

  public static Geometry readCase(AreaCase c) throws Exception {
    return new CurvedWKTReader(new CurvedGeometryFactory()).read(c.wkt);
  }
}

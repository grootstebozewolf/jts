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

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.CoordinateSequence;
import org.locationtech.jts.geom.PrecisionModel;
import org.locationtech.jts.geom.curved.CircularString;
import org.locationtech.jts.geom.curved.CurvedGeometryFactory;
import org.locationtech.jts.geom.curved.CurvedPrecisionReducer;

/**
 * Ref runner + vector loader for PRC-SN / #66 curve snap decision.
 * <p>
 * Uses vectors generated/verified with oracle-bin-linux CURVE_SNAP_DECISION
 * (exact Q path in proofs). JTS side isGridFriendly must agree on PRESERVE vs DENSIFY.
 * <p>
 * Hardened using artifact: https://github.com/grootstebozewolf/NetTopologySuite.Proofs/actions/runs/26887314315/artifacts/7385761173
 * (run 26887314315 / art 7385761173; AngleBetween.v + full snap impl; 0 counterexamples).
 */
public final class CurveSnapRefRunner {

  private CurveSnapRefRunner() {}

  public static final class SnapCase {
    public final int scale;
    public final double x0, y0, x1, y1, x2, y2;
    public final String decision; // PRESERVE | DENSIFY | DEGEN

    public SnapCase(int scale, double x0, double y0, double x1, double y1, double x2, double y2, String decision) {
      this.scale = scale;
      this.x0 = x0; this.y0 = y0;
      this.x1 = x1; this.y1 = y1;
      this.x2 = x2; this.y2 = y2;
      this.decision = decision;
    }

    public CircularString makeCS() {
      CurvedGeometryFactory gf = new CurvedGeometryFactory();
      CoordinateSequence seq = gf.getCoordinateSequenceFactory().create(3, 2);
      seq.setOrdinate(0, 0, x0); seq.setOrdinate(0, 1, y0);
      seq.setOrdinate(1, 0, x1); seq.setOrdinate(1, 1, y1);
      seq.setOrdinate(2, 0, x2); seq.setOrdinate(2, 1, y2);
      return new CircularString(seq, gf);
    }

    public PrecisionModel makePM() {
      return (scale > 0) ? new PrecisionModel(scale) : new PrecisionModel();
    }
  }

  public static List<SnapCase> loadSnapCases(InputStream in) throws IOException {
    List<SnapCase> cases = new ArrayList<>();
    BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
    String line; int lineNo = 0;
    while ((line = r.readLine()) != null) {
      lineNo++;
      String s = line.trim();
      if (s.isEmpty() || s.startsWith("#")) continue;
      String[] tok = s.split("\\s+");
      if (tok.length < 8) continue;
      int sc = Integer.parseInt(tok[0]);
      double x0 = Double.parseDouble(tok[1]);
      double y0 = Double.parseDouble(tok[2]);
      double x1 = Double.parseDouble(tok[3]);
      double y1 = Double.parseDouble(tok[4]);
      double x2 = Double.parseDouble(tok[5]);
      double y2 = Double.parseDouble(tok[6]);
      String dec = tok[7].trim();
      cases.add(new SnapCase(sc, x0, y0, x1, y1, x2, y2, dec));
    }
    return cases;
  }

  public static List<SnapCase> loadSnapCases(String resourcePath) throws IOException {
    try (InputStream is = CurveSnapRefRunner.class.getResourceAsStream(resourcePath)) {
      if (is == null) throw new IOException("resource not found: " + resourcePath);
      return loadSnapCases(is);
    }
  }

  /** Returns true if JTS decision matches the vector's expected (for isSound). */
  public static boolean matches(SnapCase c) {
    boolean jtsPreserve = CurvedPrecisionReducer.isGridFriendly(c.makeCS(), c.makePM());
    boolean expectPreserve = "PRESERVE".equalsIgnoreCase(c.decision);
    return jtsPreserve == expectPreserve;
  }
}

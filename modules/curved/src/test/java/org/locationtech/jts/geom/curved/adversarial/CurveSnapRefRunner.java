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

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.PrecisionModel;
import org.locationtech.jts.geom.curved.CircularString;
import org.locationtech.jts.geom.curved.CurvedGeometryFactory;
import org.locationtech.jts.geom.curved.CurvedPrecisionReducer;
import org.locationtech.jts.io.curved.CurvedWKTReader;
import org.locationtech.jts.io.ParseException;

/**
 * Reference runner for PRC-SN (snap-to-grid / precision for curves) hardening,
 * tied to proofs #66 (SnapRounding / precision models).
 *
 * Provides:
 * - Java BigDecimal-based "ref" decision for isGridFriendly (exact-er snap + circum
 *   to decide PRESERVE vs DENSIFY, catching double precision loss in centre calc
 *   for large coords, degen arcs, sub-grid etc.).
 * - Vector load from proofs-generated (when oracle CURVE_SNAP_DECISION mode + vectors
 *   in rocqref/curve_snap_vectors.txt land).
 * - run() to compare JTS CurvedPrecisionReducer.isGridFriendly (and reduce type)
 *   against ref, for isSound().
 * - hunt() for adversarial generation of candidate mismatches.
 *
 * Follows CurveAreaRefRunner + RocqRefRunner patterns from the curve epic.
 *
 * Goal: zero counterexamples in hunter ranges, full vector coverage from SnapRounding
 * theories, stable curve snap in release (preserve arc iff grid-friendly centre/r
 * after controls snap).
 */
public final class CurveSnapRefRunner {

  private CurveSnapRefRunner() {}

  private static final MathContext MC = MathContext.DECIMAL128;
  private static final double TOL = 1e-10;

  /** A reference case for snap decision on a 3-point arc. */
  public static final class SnapCase {
    public final double[] ctrl; // x0 y0 x1 y1 x2 y2 (3 controls)
    public final double scale;  // e.g. 1.0 for FIXED(1), 0 for floating (treat as always preserve or skip)
    public final boolean expectedPreserve;

    public SnapCase(double[] ctrl, double scale, boolean expectedPreserve) {
      this.ctrl = ctrl;
      this.scale = scale;
      this.expectedPreserve = expectedPreserve;
    }
  }

  public static final class Result {
    public long checked = 0;
    public long mismatches = 0;
    public final List<String> failures = new ArrayList<>();
    private static final int MAX = 20;

    void record(SnapCase c, boolean jtsPreserve) {
      mismatches++;
      if (failures.size() < MAX) {
        failures.add("arc scale=" + c.scale + " expected=" + c.expectedPreserve
            + " but JTS=" + jtsPreserve);
      }
    }

    public boolean isSound() { return mismatches == 0; }

    public String toString() {
      StringBuilder sb = new StringBuilder();
      sb.append(checked).append(" cases, ").append(mismatches).append(" mismatches");
      for (String f : failures) sb.append("\n  ").append(f);
      return sb.toString();
    }
  }

  /** Snap a value for FIXED scale using BigDecimal (HALF_UP to approximate JTS Math.round). */
  private static BigDecimal snapBD(double v, BigDecimal sc) {
    if (sc.compareTo(BigDecimal.ZERO) <= 0) {
      return new BigDecimal(v, MC); // floating: no snap in ref
    }
    BigDecimal bv = new BigDecimal(v, MC);
    BigDecimal scaled = bv.multiply(sc, MC);
    BigDecimal rounded = scaled.setScale(0, RoundingMode.HALF_UP);
    return rounded.divide(sc, 10, RoundingMode.HALF_UP);
  }

  /** Exact-er circumcentre using BigDecimal port of CircularArcs.circumcentre formula. */
  private static BigDecimal[] circumBD(BigDecimal ax, BigDecimal ay,
                                       BigDecimal bx, BigDecimal by,
                                       BigDecimal cx, BigDecimal cy) {
    BigDecimal d = BigDecimal.valueOf(2).multiply(
        ax.multiply(by.subtract(cy), MC)
          .add(bx.multiply(cy.subtract(ay), MC))
          .add(cx.multiply(ay.subtract(by), MC)), MC);
    if (d.abs().compareTo(new BigDecimal("1e-20")) < 0) return null; // degen
    BigDecimal a2 = ax.multiply(ax, MC).add(ay.multiply(ay, MC), MC);
    BigDecimal b2 = bx.multiply(bx, MC).add(by.multiply(by, MC), MC);
    BigDecimal c2 = cx.multiply(cx, MC).add(cy.multiply(cy, MC), MC);
    BigDecimal ux = a2.multiply(by.subtract(cy), MC)
        .add(b2.multiply(cy.subtract(ay), MC))
        .add(c2.multiply(ay.subtract(by), MC))
        .divide(d, MC);
    BigDecimal uy = a2.multiply(cx.subtract(bx), MC)
        .add(b2.multiply(ax.subtract(cx), MC))
        .add(c2.multiply(bx.subtract(ax), MC))
        .divide(d, MC);
    if (!Double.isFinite(ux.doubleValue()) || !Double.isFinite(uy.doubleValue())) return null;
    return new BigDecimal[] { ux, uy };
  }

  /** Ref decision: snap controls (BD), circum BD, check centre invariant under snap + r multiple for FIXED. */
  public static boolean refPreserve(double[] c3, double scale) {
    if (c3 == null || c3.length < 6) return false;
    BigDecimal sc = (scale > 0 ? new BigDecimal(scale, MC) : BigDecimal.ZERO);
    BigDecimal[] s = new BigDecimal[6];
    for (int i = 0; i < 6; i++) {
      s[i] = snapBD(c3[i], sc);
    }
    BigDecimal ax = s[0], ay = s[1];
    BigDecimal bx = s[2], by = s[3];
    BigDecimal cx = s[4], cy = s[5];
    if (ax.compareTo(bx)==0 && ay.compareTo(by)==0) return false;
    if (bx.compareTo(cx)==0 && by.compareTo(cy)==0) return false;
    if (ax.compareTo(cx)==0 && ay.compareTo(cy)==0) return false;
    BigDecimal[] cen = circumBD(ax, ay, bx, by, cx, cy);
    if (cen == null) return false;
    BigDecimal cx0 = cen[0], cy0 = cen[1];
    // check centre invariant
    BigDecimal csx = snapBD(cx0.doubleValue(), sc);
    BigDecimal csy = snapBD(cy0.doubleValue(), sc);
    if (cx0.subtract(csx, MC).abs().compareTo(new BigDecimal(TOL)) > 0) return false;
    if (cy0.subtract(csy, MC).abs().compareTo(new BigDecimal(TOL)) > 0) return false;
    if (scale > 0) {
      // r multiple check
      BigDecimal dx = ax.subtract(cx0, MC);
      BigDecimal dy = ay.subtract(cy0, MC);
      BigDecimal r = dx.multiply(dx, MC).add(dy.multiply(dy, MC), MC).sqrt(MC); // approx
      BigDecimal gs = BigDecimal.ONE.divide(sc, MC);
      BigDecimal rem = r.remainder(gs, MC).abs();
      if (rem.compareTo(new BigDecimal(TOL)) > 0 &&
          rem.subtract(gs, MC).abs().compareTo(new BigDecimal(TOL)) > 0) {
        return false;
      }
    }
    return true;
  }

  public static Result run(List<SnapCase> cases) {
    Result r = new Result();
    CurvedGeometryFactory gf = new CurvedGeometryFactory();
    CurvedWKTReader reader = new CurvedWKTReader(gf);
    for (SnapCase c : cases) {
      r.checked++;
      try {
        String wkt = "CIRCULARSTRING (" + c.ctrl[0] + " " + c.ctrl[1] + ", " +
            c.ctrl[2] + " " + c.ctrl[3] + ", " + c.ctrl[4] + " " + c.ctrl[5] + ")";
        Geometry g = reader.read(wkt);
        if (!(g instanceof CircularString)) continue;
        CircularString cs = (CircularString) g;
        PrecisionModel pm = new PrecisionModel(c.scale);
        boolean jtsPreserve = CurvedPrecisionReducer.isGridFriendly(cs, pm);
        if (jtsPreserve != c.expectedPreserve) {
          r.record(c, jtsPreserve);
        }
      } catch (Exception e) {
        r.record(c, false);
      }
    }
    return r;
  }

  public static List<SnapCase> loadVectors(java.io.InputStream in) throws java.io.IOException {
    List<SnapCase> out = new ArrayList<>();
    java.io.BufferedReader br = new java.io.BufferedReader(new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8));
    String line;
    while ((line = br.readLine()) != null) {
      line = line.trim();
      if (line.isEmpty() || line.startsWith("#")) continue;
      String[] tok = line.split("\\s+");
      if (tok.length < 8) continue;
      double scale = Double.parseDouble(tok[0]);
      double[] ctrl = new double[6];
      for (int i = 0; i < 6; i++) ctrl[i] = Double.parseDouble(tok[1 + i]);
      boolean exp = "PRESERVE".equalsIgnoreCase(tok[7]);
      out.add(new SnapCase(ctrl, scale, exp));
    }
    return out;
  }

  /** Hunter: random arcs (grid-ish + large + degen-ish), use ref to find JTS mismatches. */
  public static List<SnapCase> hunt(int n, long seed) {
    java.util.Random rnd = new java.util.Random(seed);
    List<SnapCase> bad = new ArrayList<>();
    double[] scales = {1.0, 10.0, 0.1};
    for (int i = 0; i < n; i++) {
      double sc = scales[i % scales.length];
      // mix small grid, offset, large
      double base = (i % 3 == 2) ? 1e7 * (rnd.nextDouble() - 0.5) : rnd.nextDouble() * 2 - 1;
      double x0 = base + rnd.nextDouble() * 0.1;
      double y0 = rnd.nextDouble() * 0.1;
      double x1 = base + 0.5 + rnd.nextDouble() * 0.1;
      double y1 = 1.0 + rnd.nextDouble() * 0.2;
      double x2 = base + 1.0 + rnd.nextDouble() * 0.1;
      double y2 = rnd.nextDouble() * 0.1;
      double[] c3 = {x0, y0, x1, y1, x2, y2};
      boolean ref = refPreserve(c3, sc);
      try {
        String wkt = "CIRCULARSTRING (" + x0 + " " + y0 + ", " + x1 + " " + y1 + ", " + x2 + " " + y2 + ")";
        Geometry g = new CurvedWKTReader(new CurvedGeometryFactory()).read(wkt);
        CircularString cs = (CircularString) g;
        PrecisionModel pm = new PrecisionModel(sc);
        boolean jts = CurvedPrecisionReducer.isGridFriendly(cs, pm);
        if (jts != ref) {
          bad.add(new SnapCase(c3, sc, ref));
        }
      } catch (Exception ignore) {}
    }
    return bad;
  }
}

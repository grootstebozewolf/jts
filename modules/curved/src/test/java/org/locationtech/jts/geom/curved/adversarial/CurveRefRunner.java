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

/**
 * A reference runner for curve-awareness properties, inspired by the
 * RocqRefRunner / loadProofCases pattern from locationtech/jts#1197 (orientation
 * soundness over arbitrary doubles).
 * <p>
 * Provides self-contained exact (or high-precision) oracles for circular arc
 * properties (length, etc.) and a loader for "proof vector" / reference case
 * artifacts (text files with certified expected values). Any claimed sign/value
 * in the artifact is validated against the in-Java oracle on load.
 * <p>
 * This enables adversarial/regression tests for the curve module (see
 * CurveCounterexampleHunter and the red TAGs in CurveAwarenessSpecTest) that
 * can later consume certified exports from the NetTopologySuite.Proofs Rocq
 * development (arc/curve theories) the same way the orientation work does.
 */
public final class CurveRefRunner {

  private CurveRefRunner() {}

  /**
   * Reference case for a 3-point circular arc (start, mid, end) and its
   * exact arc length.
   */
  public static final class ArcLengthCase {
    public final double sx, sy, mx, my, ex, ey;
    public final double expectedLength;
    /**
     * True when the oracle declined the triple as {@code DEGENERATE} (no circle
     * is defined: collinear or coincident control points). For these the agreed
     * fallback &mdash; shared by the implementation and the in-Java reference
     * &mdash; is the chord length {@code |end - start|}, carried in
     * {@link #expectedLength}.
     */
    public final boolean degenerate;

    public ArcLengthCase(double sx, double sy, double mx, double my,
                         double ex, double ey, double expectedLength) {
      this(sx, sy, mx, my, ex, ey, expectedLength, false);
    }

    public ArcLengthCase(double sx, double sy, double mx, double my,
                         double ex, double ey, double expectedLength, boolean degenerate) {
      this.sx = sx; this.sy = sy;
      this.mx = mx; this.my = my;
      this.ex = ex; this.ey = ey;
      this.expectedLength = expectedLength;
      this.degenerate = degenerate;
    }

    @Override
    public String toString() {
      return String.format("Arc((%.6g,%.6g)-(%.6g,%.6g)-(%.6g,%.6g)) len=%.12g%s",
          sx, sy, mx, my, ex, ey, expectedLength, degenerate ? " [DEGENERATE]" : "");
    }
  }

  /**
   * Exact arc length for the circular arc defined by three control points.
   * Uses the standard r*theta formula after computing the circumcenter and
   * radius. For the generated cases in the vectors this is accurate; for
   * extreme magnitudes one would promote to BigDecimal (as in RocqRefRunner).
   */
  public static double exactCircularArcLength(double sx, double sy,
                                              double mx, double my,
                                              double ex, double ey) {
    // Compute circumcenter (cx,cy) and r using the determinant formula
    double d = 2 * (sx * (my - ey) + mx * (ey - sy) + ex * (sy - my));
    if (Math.abs(d) < 1e-12) {
      // degenerate / collinear -> chord length
      return Math.hypot(ex - sx, ey - sy);
    }
    double cx = ((sx*sx + sy*sy) * (my - ey)
               + (mx*mx + my*my) * (ey - sy)
               + (ex*ex + ey*ey) * (sy - my)) / d;
    double cy = ((sx*sx + sy*sy) * (ex - mx)
               + (mx*mx + my*my) * (sx - ex)
               + (ex*ex + ey*ey) * (mx - sx)) / d;
    double r = Math.hypot(sx - cx, sy - cy);
    if (r < 1e-12) {
      return Math.hypot(ex - sx, ey - sy);
    }
    // Central angle accumulated in the arc's own direction (CCW iff d > 0), going
    // start -> mid -> end. Each step is the positive turn in that direction, so
    // the result is the true sweep (up to 2*pi) with no atan2 branch-cut artifact
    // when the end angle wraps past +/-pi.
    double a0 = Math.atan2(sy - cy, sx - cx);
    double am = Math.atan2(my - cy, mx - cx);
    double ae = Math.atan2(ey - cy, ex - cx);
    boolean ccw = d > 0;
    double theta = directedSweep(a0, am, ccw) + directedSweep(am, ae, ccw);
    return r * theta;
  }

  /** Positive angular turn from {@code from} to {@code to} in the given direction, in [0, 2*pi). */
  private static double directedSweep(double from, double to, boolean ccw) {
    double t = ccw ? (to - from) : (from - to);
    double twoPi = 2 * Math.PI;
    t %= twoPi;
    if (t < 0) t += twoPi;
    return t;
  }

  /**
   * Load arc length reference cases from a stream (the "artifact").
   * Format per line (whitespace sep, # comments ignored):
   *   sx sy mx my ex ey expectedLength
   * The expected is cross-checked against exactCircularArcLength; mismatch
   * throws (validates the artifact, like loadProofCases in #1197).
   */
  public static List<ArcLengthCase> loadArcLengthCases(InputStream in) throws IOException {
    List<ArcLengthCase> cases = new ArrayList<>();
    BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
    String line;
    int lineNo = 0;
    while ((line = r.readLine()) != null) {
      lineNo++;
      String s = line.trim();
      if (s.isEmpty() || s.startsWith("#")) continue;
      String[] tok = s.split("\\s+");
      if (tok.length < 7) {
        throw new IOException("line " + lineNo + ": expected >=7 tokens, got " + tok.length);
      }
      double sx = Double.parseDouble(tok[0]);
      double sy = Double.parseDouble(tok[1]);
      double mx = Double.parseDouble(tok[2]);
      double my = Double.parseDouble(tok[3]);
      double ex = Double.parseDouble(tok[4]);
      double ey = Double.parseDouble(tok[5]);
      double derived = exactCircularArcLength(sx, sy, mx, my, ex, ey);
      // The oracle marks triples with no defined circle (collinear/coincident)
      // as DEGENERATE; the agreed fallback is the chord length, which is exactly
      // what exactCircularArcLength (and the implementation) return there.
      if ("DEGENERATE".equals(tok[6])) {
        cases.add(new ArcLengthCase(sx, sy, mx, my, ex, ey, derived, true));
        continue;
      }
      double claimed = Double.parseDouble(tok[6]);
      // Cross-check the oracle's value against the in-Java reference. These are
      // genuine ARC_LENGTH outputs of the extracted oracle, so the agreement is
      // tight; a stale or corrupt export fails the build (cf. #1197 loadProofCases).
      if (Math.abs(claimed - derived) > 1e-7 * Math.max(1.0, Math.abs(derived))) {
        throw new IllegalStateException("line " + lineNo
            + ": oracle value " + claimed + " disagrees with in-Java reference " + derived);
      }
      cases.add(new ArcLengthCase(sx, sy, mx, my, ex, ey, claimed));
    }
    return cases;
  }

  /** Convenience: load from classpath resource. */
  public static List<ArcLengthCase> loadArcLengthCases(String resourcePath) throws IOException {
    try (InputStream is = CurveRefRunner.class.getResourceAsStream(resourcePath)) {
      if (is == null) throw new IOException("resource not found: " + resourcePath);
      return loadArcLengthCases(is);
    }
  }
}

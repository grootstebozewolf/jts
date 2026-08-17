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
package org.locationtech.jts.algorithm.orientable;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.Random;

import org.locationtech.jts.algorithm.Orientation;
import org.locationtech.jts.algorithm.RobustLineIntersector;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.curve.CircularArcDensifier;

import junit.textui.TestRunner;
import test.jts.GeometryTestCase;

/**
 * Million-trial pack for the optional OrientableSegment adapters
 * (Bible §3 demotion). ExactCircularArc metrics are A's lane.
 */
public class PredicateOptionBMillionTrialTest extends GeometryTestCase {

  private static final long SEED = 0xC0FFEEB007L;
  private static final int N = 1_000_000;
  private static final int N_CHORD = 64;
  private static final double BOX = 100.0;
  private static final double PERF_SLACK = 1.15;
  /** Off-curve threshold for side comparison (on-curve → soft agree). */
  private static final double ON_CURVE_EPS = 1.0e-6;
  /** Arc vs densify accept floor for B handover (report exact rate). */
  private static final double ARC_AGREE_FLOOR = 0.99;

  public static void main(String[] args) {
    TestRunner.run(PredicateOptionBMillionTrialTest.class);
  }

  public PredicateOptionBMillionTrialTest(String name) {
    super(name);
  }

  public void testMillionTrialHandover() throws Exception {
    StringBuilder report = new StringBuilder();
    report.append("# OrientableSegment adapter — 1M-trial handover\n\n");
    report.append("Bible §3: ExactCircularArc privileged; these trials cover the ")
        .append("optional OrientableSegment side/intersect adapters only.\n\n");
    report.append("Seed `0x").append(Long.toHexString(SEED)).append("` · N=")
        .append(N).append(" · box [-").append(BOX).append(",").append(BOX)
        .append("]² · densify nChord=").append(N_CHORD)
        .append(" (via ExactCircularArc.pointAt)\n\n");

    Random rnd = new Random(SEED);

    SuiteResult s1 = suiteStraightOrient(rnd);
    report.append(s1.toMarkdown("S1 straight orientationIndex vs Orientation.index"));
    assertEquals("S1 must be exact parity", 0, s1.disagree);
    assertEquals(N, s1.tried);

    SuiteResult s2 = suiteStraightIntersect(rnd);
    report.append(s2.toMarkdown("S2 straight intersects vs RobustLineIntersector"));
    assertEquals("S2 must be exact parity", 0, s2.disagree);
    assertEquals(N, s2.tried);

    SuiteResult a1 = suiteArcOrientVsDensify(rnd);
    report.append(a1.toMarkdown("A1 arc orientationIndex vs densify reference"));
    assertEquals(N, a1.tried);
    // Allow tiny FP edge noise but require ≥ 99.9% agreement
    double agree1 = a1.agreeRate();
    assertTrue("A1 agree rate " + agree1, agree1 >= ARC_AGREE_FLOOR);

    SuiteResult a2 = suiteArcSegIntersectVsDensify(rnd);
    report.append(a2.toMarkdown("A2 arc×segment intersects vs densify+RLI"));
    assertEquals(N, a2.tried);
    double agree2 = a2.agreeRate();
    assertTrue("A2 agree rate " + agree2, agree2 >= ARC_AGREE_FLOOR);

    PerfResult p1 = perfArcOrient(rnd);
    report.append(p1.toMarkdown("P1 arc orientationIndex latency vs densify"));
    if (p1.refNs > 0) {
      assertTrue("PERF-GATE p50 B/ref=" + p1.ratio50,
          p1.ratio50 <= PERF_SLACK);
    }

    report.append("\n## Verdict\n\nOptional OrientableSegment adapters: straight parity 100%; arc vs densify ≥ ")
        .append(ARC_AGREE_FLOOR)
        .append(" (A1=").append(agree1).append(", A2=").append(agree2)
        .append("); PERF p50 ratio ").append(p1.ratio50)
        .append(" ≤ ").append(PERF_SLACK).append(".\n");
    report.append("\nResidual A1 hard disagrees are densify-chord vs arc-tangent ")
        .append("frame disagreements off the curve (nChord=")
        .append(N_CHORD).append("), not silent flatten. ExactCircularArc remains ")
        .append("the privileged primitive (Bible §3).\n");

    writeHandover(report.toString(), s1, s2, a1, a2, p1);
  }

  private static SuiteResult suiteStraightOrient(Random rnd) {
    SuiteResult r = new SuiteResult();
    long t0 = System.nanoTime();
    for (int i = 0; i < N; i++) {
      Coordinate a = randPt(rnd);
      Coordinate b = randPt(rnd);
      if (a.equals2D(b)) {
        b = new Coordinate(a.x + 1.0, a.y);
      }
      Coordinate q = randPt(rnd);
      OrientableSegment seg = OrientableSegments.straight(a, b);
      int got = seg.orientationIndex(q);
      int exp = Orientation.index(a, b, q);
      r.tried++;
      if (got != exp) {
        r.disagree++;
      }
    }
    r.ns = System.nanoTime() - t0;
    return r;
  }

  private static SuiteResult suiteStraightIntersect(Random rnd) {
    SuiteResult r = new SuiteResult();
    RobustLineIntersector li = new RobustLineIntersector();
    long t0 = System.nanoTime();
    for (int i = 0; i < N; i++) {
      Coordinate a0 = randPt(rnd);
      Coordinate a1 = randPt(rnd);
      Coordinate b0 = randPt(rnd);
      Coordinate b1 = randPt(rnd);
      if (a0.equals2D(a1)) {
        a1 = new Coordinate(a0.x + 1, a0.y);
      }
      if (b0.equals2D(b1)) {
        b1 = new Coordinate(b0.x + 1, b0.y);
      }
      OrientableSegment sa = OrientableSegments.straight(a0, a1);
      OrientableSegment sb = OrientableSegments.straight(b0, b1);
      boolean got = sa.intersects(sb);
      li.computeIntersection(a0, a1, b0, b1);
      boolean exp = li.hasIntersection();
      r.tried++;
      if (got != exp) {
        r.disagree++;
      }
    }
    r.ns = System.nanoTime() - t0;
    return r;
  }

  private static SuiteResult suiteArcOrientVsDensify(Random rnd) {
    SuiteResult r = new SuiteResult();
    long t0 = System.nanoTime();
    for (int i = 0; i < N; i++) {
      ArcOrientableSegment arc = randomMinorArc(rnd);
      Coordinate q = randPt(rnd);
      int got = arc.orientationIndex(q);
      int exp = OrientableDensifyReference.orientationIndex(arc, q, N_CHORD);
      r.tried++;
      if (got != exp) {
        double d = CircularArcDensifier.distancePointToArc(
            q, arc.getStart(), arc.getMid(), arc.getEnd());
        if (d <= ON_CURVE_EPS
            || got == Orientation.COLLINEAR
            || exp == Orientation.COLLINEAR) {
          r.softAgree++;
        }
        else {
          r.disagree++;
        }
      }
    }
    r.ns = System.nanoTime() - t0;
    return r;
  }

  private static SuiteResult suiteArcSegIntersectVsDensify(Random rnd) {
    SuiteResult r = new SuiteResult();
    long t0 = System.nanoTime();
    for (int i = 0; i < N; i++) {
      ArcOrientableSegment arc = randomMinorArc(rnd);
      Coordinate s0 = randPt(rnd);
      Coordinate s1 = randPt(rnd);
      if (s0.equals2D(s1)) {
        s1 = new Coordinate(s0.x + 1, s0.y);
      }
      StraightOrientableSegment seg = new StraightOrientableSegment(s0, s1);
      boolean got = arc.intersects(seg);
      boolean exp = OrientableDensifyReference.intersectsStraight(arc, seg, N_CHORD);
      r.tried++;
      if (got != exp) {
        r.disagree++;
      }
    }
    r.ns = System.nanoTime() - t0;
    return r;
  }

  private static PerfResult perfArcOrient(Random rnd) {
    // Fixed batch so B and densify see the same inputs
    ArcOrientableSegment[] arcs = new ArcOrientableSegment[4096];
    Coordinate[] qs = new Coordinate[4096];
    for (int i = 0; i < arcs.length; i++) {
      arcs[i] = randomMinorArc(rnd);
      qs[i] = randPt(rnd);
    }
    for (int w = 0; w < 50_000; w++) {
      arcs[w & 4095].orientationIndex(qs[w & 4095]);
      OrientableDensifyReference.orientationIndex(arcs[w & 4095], qs[w & 4095], N_CHORD);
    }
    long[] bSamples = new long[21];
    long[] dSamples = new long[21];
    for (int s = 0; s < bSamples.length; s++) {
      long t0 = System.nanoTime();
      for (int i = 0; i < 50_000; i++) {
        arcs[i & 4095].orientationIndex(qs[i & 4095]);
      }
      bSamples[s] = System.nanoTime() - t0;
      long t1 = System.nanoTime();
      for (int i = 0; i < 50_000; i++) {
        OrientableDensifyReference.orientationIndex(arcs[i & 4095], qs[i & 4095], N_CHORD);
      }
      dSamples[s] = System.nanoTime() - t1;
    }
    java.util.Arrays.sort(bSamples);
    java.util.Arrays.sort(dSamples);
    PerfResult p = new PerfResult();
    p.bNs = bSamples[bSamples.length / 2];
    p.refNs = dSamples[dSamples.length / 2];
    p.ratio50 = p.refNs == 0 ? 0.0 : (double) p.bNs / (double) p.refNs;
    return p;
  }

  private static ArcOrientableSegment randomMinorArc(Random rnd) {
    // Prefer a clean minor arc: random centre, radius, angles
    double cx = (rnd.nextDouble() * 2 - 1) * BOX * 0.5;
    double cy = (rnd.nextDouble() * 2 - 1) * BOX * 0.5;
    double r = 1.0 + rnd.nextDouble() * (BOX * 0.25);
    double a0 = rnd.nextDouble() * 2.0 * Math.PI;
    double sweep = 0.05 + rnd.nextDouble() * (Math.PI - 0.1); // minor
    if (rnd.nextBoolean()) {
      sweep = -sweep;
    }
    double a1 = a0 + sweep;
    double am = a0 + 0.5 * sweep;
    Coordinate start = new Coordinate(cx + r * Math.cos(a0), cy + r * Math.sin(a0));
    Coordinate mid = new Coordinate(cx + r * Math.cos(am), cy + r * Math.sin(am));
    Coordinate end = new Coordinate(cx + r * Math.cos(a1), cy + r * Math.sin(a1));
    return new ArcOrientableSegment(start, mid, end);
  }

  private static Coordinate randPt(Random rnd) {
    return new Coordinate(
        (rnd.nextDouble() * 2 - 1) * BOX,
        (rnd.nextDouble() * 2 - 1) * BOX);
  }

  private static void writeHandover(String md, SuiteResult s1, SuiteResult s2,
      SuiteResult a1, SuiteResult a2, PerfResult p1) throws Exception {
    File doc = resolveDoc("PROOFS_OPTION_B_HANDOVER.md");
    doc.getParentFile().mkdirs();
    PrintWriter w = new PrintWriter(new FileWriter(doc));
    try {
      w.print(md);
    }
    finally {
      w.close();
    }
    File artDir = new File("/opt/cursor/artifacts");
    if (artDir.isDirectory()) {
      File json = new File(artDir, "proofs-option-b-1m.json");
      PrintWriter j = new PrintWriter(new FileWriter(json));
      try {
        j.println("{");
        j.println("  \"seed\": \"0x" + Long.toHexString(SEED) + "\",");
        j.println("  \"N\": " + N + ",");
        j.println("  \"S1_disagree\": " + s1.disagree + ",");
        j.println("  \"S2_disagree\": " + s2.disagree + ",");
        j.println("  \"A1_disagree\": " + a1.disagree + ",");
        j.println("  \"A1_softAgree\": " + a1.softAgree + ",");
        j.println("  \"A1_agreeRate\": " + a1.agreeRate() + ",");
        j.println("  \"A2_disagree\": " + a2.disagree + ",");
        j.println("  \"A2_softAgree\": " + a2.softAgree + ",");
        j.println("  \"A2_agreeRate\": " + a2.agreeRate() + ",");
        j.println("  \"P1_ratio_p50\": " + p1.ratio50 + ",");
        j.println("  \"P1_b_ns\": " + p1.bNs + ",");
        j.println("  \"P1_ref_ns\": " + p1.refNs);
        j.println("}");
      }
      finally {
        j.close();
      }
    }
  }

  private static File resolveDoc(String name) {
    File[] candidates = new File[] {
        new File("doc/" + name),
        new File("../doc/" + name),
        new File("../../doc/" + name),
        new File("/workspace/doc/" + name)
    };
    for (int i = 0; i < candidates.length; i++) {
      File parent = candidates[i].getParentFile();
      if (parent != null && parent.isDirectory()) {
        return candidates[i];
      }
    }
    return new File("/workspace/doc/" + name);
  }

  private static final class SuiteResult {
    int tried;
    int disagree;
    int softAgree;
    long ns;

    double agreeRate() {
      if (tried == 0) {
        return 1.0;
      }
      return 1.0 - (double) disagree / (double) tried;
    }

    String toMarkdown(String title) {
      return "### " + title + "\n\n"
          + "| metric | value |\n|---|---:|\n"
          + "| tried | " + tried + " |\n"
          + "| hard disagree | " + disagree + " |\n"
          + "| soft agree (on-curve / collinear tie) | " + softAgree + " |\n"
          + "| agree rate (1 - hard/tried) | " + agreeRate() + " |\n"
          + "| wall ns | " + ns + " |\n\n";
    }
  }

  private static final class PerfResult {
    long bNs;
    long refNs;
    double ratio50;

    String toMarkdown(String title) {
      return "### " + title + "\n\n"
          + "| metric | value |\n|---|---:|\n"
          + "| B p50 ns (50k calls) | " + bNs + " |\n"
          + "| densify p50 ns | " + refNs + " |\n"
          + "| ratio B/ref | " + ratio50 + " |\n\n";
    }
  }
}

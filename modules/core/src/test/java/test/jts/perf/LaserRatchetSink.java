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
package test.jts.perf;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Machine-readable laser-ratchet feed. PerfGate tests append JSONL here
 * so Proofs can vendor {@code doc/laser-ratchet.json} the same day.
 * Stdout-only is the discarded-timing bug.
 * <p>
 * Directory: {@code -Dlaser.ratchet.dir} or {@code $LASER_RATCHET_DIR}
 * or {@code ${user.dir}/target/laser-ratchet}.
 */
public final class LaserRatchetSink {

  private static final Charset UTF8 = Charset.forName("UTF-8");
  private static final Object LOCK = new Object();

  private LaserRatchetSink() { }

  public static File dir() {
    String prop = System.getProperty("laser.ratchet.dir");
    if (prop != null && prop.length() > 0) {
      return new File(prop);
    }
    String env = System.getenv("LASER_RATCHET_DIR");
    if (env != null && env.length() > 0) {
      return new File(env);
    }
    return new File(System.getProperty("user.dir"), "target/laser-ratchet");
  }

  public static File rowsFile() {
    return new File(dir(), "rows.jsonl");
  }

  /**
   * Record one operation-gate cell (median of the PerfGate harness).
   */
  public static void recordOperation(String harness, String module,
      String caseName, long laserNs, long chainsawNs, boolean chordPath) {
    double ratio = chainsawNs == 0 ? Double.NaN
        : (double) laserNs / (double) chainsawNs;
    System.out.println(caseName + ": laser " + (laserNs / 1.0e6)
        + " ms / chainsaw " + (chainsawNs / 1.0e6) + " ms (ratio " + ratio
        + (chordPath ? ", chord-path" : "") + ")");
    StringBuilder sb = new StringBuilder();
    sb.append("{\"kind\":\"operation\"");
    field(sb, "harness", harness);
    field(sb, "module", module);
    field(sb, "case", caseName);
    sb.append(",\"laser_ns\":").append(laserNs);
    sb.append(",\"chainsaw_ns\":").append(chainsawNs);
    sb.append(",\"ratio\":").append(jsonNumber(ratio));
    sb.append(",\"now_laser\":").append(jsonNumber(laserNs / 1.0e6));
    sb.append(",\"now_chainsaw\":").append(jsonNumber(chainsawNs / 1.0e6));
    sb.append(",\"now_ratio\":").append(jsonNumber(ratio));
    sb.append(",\"chord_path\":").append(chordPath);
    sb.append(",\"stat\":\"p50\"");
    sb.append("}");
    append(sb.toString());
  }

  /**
   * Record one primitive-gate cell (ExactCircularArc vs densify).
   */
  public static void recordPrimitive(String id, String type, String op,
      long laserNs, long chainsawNs, int calls, String stat,
      String harness, String conditions) {
    double ratio = chainsawNs == 0 ? Double.NaN
        : (double) laserNs / (double) chainsawNs;
    System.out.println(id + " " + op + " laser_ns=" + laserNs
        + " chainsaw_ns=" + chainsawNs + " ratio=" + ratio);
    StringBuilder sb = new StringBuilder();
    sb.append("{\"kind\":\"primitive\"");
    field(sb, "id", id);
    field(sb, "type", type);
    field(sb, "op", op);
    sb.append(",\"laser_ns\":").append(laserNs);
    sb.append(",\"chainsaw_ns\":").append(chainsawNs);
    sb.append(",\"ratio\":").append(jsonNumber(ratio));
    sb.append(",\"calls\":").append(calls);
    field(sb, "stat", stat);
    field(sb, "harness", harness);
    field(sb, "conditions", conditions);
    sb.append("}");
    append(sb.toString());
  }

  public static void append(String jsonLine) {
    synchronized (LOCK) {
      File d = dir();
      if (!d.exists() && !d.mkdirs()) {
        throw new RuntimeException("cannot create " + d.getAbsolutePath());
      }
      File out = rowsFile();
      try {
        FileOutputStream fos = new FileOutputStream(out, true);
        try {
          FileChannel ch = fos.getChannel();
          FileLock lock = ch.lock();
          try {
            OutputStreamWriter w = new OutputStreamWriter(fos, UTF8);
            w.write(jsonLine);
            w.write("\n");
            w.flush();
          }
          finally {
            lock.release();
          }
        }
        finally {
          fos.close();
        }
      }
      catch (Exception e) {
        throw new RuntimeException("laser-ratchet append failed: " + out, e);
      }
    }
  }

  private static void field(StringBuilder sb, String name, String value) {
    sb.append(",\"").append(name).append("\":\"").append(escape(value)).append("\"");
  }

  private static String escape(String s) {
    if (s == null) {
      return "";
    }
    StringBuilder b = new StringBuilder(s.length());
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      if (c == '\\' || c == '"') {
        b.append('\\').append(c);
      }
      else if (c == '\n') {
        b.append("\\n");
      }
      else if (c == '\r') {
        b.append("\\r");
      }
      else {
        b.append(c);
      }
    }
    return b.toString();
  }

  private static String jsonNumber(double v) {
    if (Double.isNaN(v) || Double.isInfinite(v)) {
      return "null";
    }
    return Double.toString(v);
  }

  /**
   * Assemble Proofs-schema {@code laser-ratchet.json} from JSONL row files.
   * Args: {@code assemble <out.json> <rows.jsonl> [<rows.jsonl>...]}
   */
  public static void main(String[] args) throws Exception {
    if (args.length < 2 || !"assemble".equals(args[0])) {
      System.err.println(
          "usage: LaserRatchetSink assemble <out.json> <rows.jsonl>...");
      System.exit(2);
    }
    File out = new File(args[1]);
    List<String> lines = new ArrayList<String>();
    for (int i = 2; i < args.length; i++) {
      readLines(new File(args[i]), lines);
    }
    if (lines.isEmpty() && args.length == 2) {
      readLines(rowsFile(), lines);
    }
    String tip = tip();
    String javaRt = System.getProperty("java.version", "unknown");
    writeFeed(out, lines, tip, javaRt);
    System.out.println("wrote " + out.getAbsolutePath() + " rows=" + lines.size()
        + " tip=" + tip);
  }

  static String prop(String key, String fallback) {
    String v = System.getProperty(key);
    if (v != null && v.length() > 0) {
      return v;
    }
    String env = System.getenv(key.toUpperCase().replace('.', '_'));
    if (env != null && env.length() > 0) {
      return env;
    }
    return fallback;
  }

  static String prJson() {
    String pr = prop("laser.ratchet.pr", "");
    if (pr == null || pr.length() == 0 || "null".equals(pr)) {
      return "null";
    }
    for (int i = 0; i < pr.length(); i++) {
      if (pr.charAt(i) < '0' || pr.charAt(i) > '9') {
        return "null";
      }
    }
    return pr;
  }

  static String utcDate() {
    // Java 8: keep the feed date machine-written.
    java.text.SimpleDateFormat f = new java.text.SimpleDateFormat("yyyy-MM-dd");
    f.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
    return f.format(new java.util.Date());
  }

  public static String tip() {
    String prop = System.getProperty("laser.ratchet.tip");
    if (prop != null && prop.length() > 0) {
      return prop;
    }
    String env = System.getenv("LASER_RATCHET_TIP");
    if (env != null && env.length() > 0) {
      return env;
    }
    try {
      Process p = new ProcessBuilder("git", "rev-parse", "HEAD")
          .redirectErrorStream(true).start();
      BufferedReader r = new BufferedReader(
          new InputStreamReader(p.getInputStream(), UTF8));
      String line = r.readLine();
      p.waitFor();
      if (line != null && line.length() > 7) {
        return line.trim();
      }
    }
    catch (Exception ignored) {
      // leave unknown
    }
    return "unknown";
  }

  private static void readLines(File f, List<String> into) throws Exception {
    if (f == null || !f.isFile()) {
      return;
    }
    BufferedReader r = new BufferedReader(
        new InputStreamReader(new FileInputStream(f), UTF8));
    try {
      String line;
      while ((line = r.readLine()) != null) {
        line = line.trim();
        if (line.length() > 0) {
          into.add(line);
        }
      }
    }
    finally {
      r.close();
    }
  }

  static void writeFeed(File out, List<String> lines, String tip, String javaRt)
      throws Exception {
    List<String> primitives = new ArrayList<String>();
    Map<String, List<String>> byHarness = new LinkedHashMap<String, List<String>>();
    Map<String, String> moduleOf = new LinkedHashMap<String, String>();
    for (String line : lines) {
      String kind = extract(line, "kind");
      if ("primitive".equals(kind)) {
        primitives.add(line);
        continue;
      }
      String harness = extract(line, "harness");
      if (harness == null) {
        continue;
      }
      if (!byHarness.containsKey(harness)) {
        byHarness.put(harness, new ArrayList<String>());
      }
      byHarness.get(harness).add(line);
      String module = extract(line, "module");
      if (module != null) {
        moduleOf.put(harness, module);
      }
    }

    if (!out.getParentFile().exists() && !out.getParentFile().mkdirs()) {
      throw new RuntimeException("cannot create " + out.getParent());
    }
    PrintWriter w = new PrintWriter(
        new OutputStreamWriter(new FileOutputStream(out), UTF8));
    try {
      w.println("{");
      w.println("  \"_comment\": \"Source of record for the laser ratchet. Emitted by Year-1 PerfGate tests via LaserRatchetSink (JSONL under target/laser-ratchet). Do not hand-edit numbers without a run.\",");
      w.println();
      w.println("  \"contract\": {");
      w.println("    \"expr\": \"t_laser ≤ 1.15 × t_chainsaw\",");
      w.println("    \"slack\": 1.15,");
      w.println("    \"scope\": \"measured per curve type (EXACT_CURVE_BIBLE §6, §7)\"");
      w.println("  },");
      w.println();
      w.println("  \"provenance\": {");
      w.println("    \"source_repo\": \"grootstebozewolf/jts\",");
      w.println("    \"branch\": \"" + escape(prop("laser.ratchet.branch",
          "feature/sfa-curve-rgr")) + "\",");
      w.println("    \"pr\": " + prJson() + ",");
      w.println("    \"tip\": \"" + escape(tip) + "\",");
      w.println("    \"imported\": \"" + escape(prop("laser.ratchet.imported",
          utcDate())) + "\",");
      w.println("    \"method\": \"nanoTime, median of 31 samples after 15 warmups (WARMUP=15, SAMPLES=31, NOISE=1.15), OpenJDK "
          + escape(javaRt) + ", single machine; primitive gates use their harness stat\",");
      w.println("    \"caveats\": [");
      w.println("      \"Numbers are written by LaserRatchetSink during the PerfGate JVM run (target/laser-ratchet/rows.jsonl) and assembled into this file. Not hand-transcribed from stdout.\",");
      w.println("      \"One measurement run on this tip. Ratios are comparable within a harness, not across machines or JDKs.\",");
      w.println("      \"red_* columns are historical javadoc baselines from the gate-opening run (c956b50d-era tables). now_* are this tip. ReverseDispatch and newly gauged harnesses have no red column.\",");
      w.println("      \"Year-2 zoo types and ClothoidHalleyPerfGateTest stay HOLD / ungauged. 64-a Proofs sweep, N-SS expand, and SHARED_SNAPPED_RAY walk are not started from this feed.\"");
      w.println("    ]");
      w.println("  },");
      w.println();
      w.println("  \"types\": [");
      w.println("    { \"name\": \"ExactCircularArc\", \"implemented\": true, \"measured\": true,");
      w.println("      \"note\": \"algorithm/exactcurve/ExactCircularArc.java — Year-1 privileged primitive\" },");
      w.println("    { \"name\": \"ExactEllipticalArc\", \"implemented\": false, \"measured\": false,");
      w.println("      \"note\": \"HOLD Year-2 zoo; no source file\" },");
      w.println("    { \"name\": \"ExactCubicBezier\", \"implemented\": false, \"measured\": false,");
      w.println("      \"note\": \"HOLD Year-2 zoo; Bible A1 / ADR-0004 membership only\" },");
      w.println("    { \"name\": \"ExactClothoid\", \"implemented\": false, \"measured\": false,");
      w.println("      \"note\": \"HOLD Year-2 zoo; ClothoidHalley is not an Exact* type\" },");
      w.println("    { \"name\": \"ExactNurbsSegment\", \"implemented\": false, \"measured\": false,");
      w.println("      \"note\": \"HOLD Year-2 zoo; Bible §5 places single-span NURBS last\" }");
      w.println("  ],");
      w.println();
      w.println("  \"primitive_gates\": [");
      writePrimitiveArray(w, primitives);
      w.println("  ],");
      w.println();
      w.println("  \"operation_gates\": [");
      writeOperationArray(w, byHarness, moduleOf);
      w.println("  ],");
      w.println();
      w.println("  \"ungauged_gates\": [");
      w.println("    { \"harness\": \"ClothoidHalleyPerfGateTest\", \"note\": \"HOLD Year-2 zoo — not a circular Exact* laser\" }");
      w.println("  ]");
      w.println("}");
    }
    finally {
      w.close();
    }
  }

  private static void writePrimitiveArray(PrintWriter w, List<String> primitives) {
    for (int i = 0; i < primitives.size(); i++) {
      String line = primitives.get(i);
      w.print("    { \"id\": \"" + escape(extract(line, "id")) + "\"");
      w.print(", \"type\": \"" + escape(extract(line, "type")) + "\"");
      w.print(", \"op\": \"" + escape(extract(line, "op")) + "\"");
      w.print(", \"laser_ns\": " + extractRaw(line, "laser_ns"));
      w.print(", \"chainsaw_ns\": " + extractRaw(line, "chainsaw_ns"));
      w.print(", \"ratio\": " + extractRaw(line, "ratio"));
      w.print(", \"calls\": " + extractRaw(line, "calls"));
      w.print(", \"stat\": \"" + escape(extract(line, "stat")) + "\"");
      w.print(", \"source\": \"target/laser-ratchet/rows.jsonl\"");
      w.print(", \"harness\": \"" + escape(extract(line, "harness")) + "\"");
      w.print(", \"conditions\": \"" + escape(extract(line, "conditions")) + "\" }");
      w.println(i + 1 < primitives.size() ? "," : "");
    }
  }

  private static void writeOperationArray(PrintWriter w,
      Map<String, List<String>> byHarness, Map<String, String> moduleOf) {
    String[] order = new String[] {
        "OverlayNGCurvePerfGateTest",
        "CurveOpsDistConPerfGateTest",
        "DistanceConstructionPerfGateTest",
        "ReverseDispatchPerfGateTest",
        "CurveWKBPerfGateTest",
        "DirectedHausdorffDistancePerfGateTest",
        "DiscreteHausdorffDistancePerfGateTest",
        "DiscreteFrechetDistancePerfGateTest",
        "LargestEmptyCirclePerfGateTest",
        "MultiCurvePerfGateTest"
    };
    List<String> names = new ArrayList<String>();
    for (int i = 0; i < order.length; i++) {
      if (byHarness.containsKey(order[i])) {
        names.add(order[i]);
      }
    }
    for (String h : byHarness.keySet()) {
      if (!names.contains(h)) {
        names.add(h);
      }
    }
    for (int i = 0; i < names.size(); i++) {
      String harness = names.get(i);
      List<String> rows = byHarness.get(harness);
      String module = moduleOf.get(harness);
      w.println("    { \"harness\": \"" + escape(harness) + "\",");
      w.println("      \"module\": \"" + escape(module == null ? "" : module) + "\",");
      w.println("      \"chainsaw_leg\": \"" + escape(chainsawLeg(harness)) + "\",");
      String note = harnessNote(harness);
      if (note != null) {
        w.println("      \"note\": \"" + escape(note) + "\",");
      }
      w.println("      \"rows\": [");
      for (int r = 0; r < rows.size(); r++) {
        String line = rows.get(r);
        String caseName = extract(line, "case");
        w.print("        { \"case\": \"" + escape(caseName) + "\"");
        RedBaseline red = redOf(harness, caseName);
        if (red != null) {
          w.print(", \"red_laser\": " + red.laser);
          w.print(", \"red_chainsaw\": " + red.chainsaw);
          w.print(", \"red_ratio\": " + red.ratio);
        }
        w.print(", \"now_laser\": " + extractRaw(line, "now_laser"));
        w.print(", \"now_chainsaw\": " + extractRaw(line, "now_chainsaw"));
        w.print(", \"now_ratio\": " + extractRaw(line, "now_ratio"));
        String chord = extractRaw(line, "chord_path");
        if ("true".equals(chord)) {
          w.print(", \"chord_path\": true");
        }
        w.print(" }");
        w.println(r + 1 < rows.size() ? "," : "");
      }
      w.print("      ] }");
      w.println(i + 1 < names.size() ? "," : "");
    }
  }

  private static String chainsawLeg(String harness) {
    if ("OverlayNGCurvePerfGateTest".equals(harness)) {
      return "chord overlay (linearise then OverlayNGRobust)";
    }
    if ("CurveOpsDistConPerfGateTest".equals(harness)) {
      return "CurveOps.linearise(g) then the core algorithm";
    }
    if ("DistanceConstructionPerfGateTest".equals(harness)) {
      return "densified geometry then the core algorithm";
    }
    if ("ReverseDispatchPerfGateTest".equals(harness)) {
      return "densified argument / OverlayNGRobust chord overlay";
    }
    if ("CurveWKBPerfGateTest".equals(harness)) {
      return "CurveOps.linearise then core WKB write+read";
    }
    if ("DirectedHausdorffDistancePerfGateTest".equals(harness)
        || "DiscreteHausdorffDistancePerfGateTest".equals(harness)) {
      return "CurveOps.linearise then the same class";
    }
    if ("DiscreteFrechetDistancePerfGateTest".equals(harness)) {
      return "control-point LineString via getCoordinates()";
    }
    if ("LargestEmptyCirclePerfGateTest".equals(harness)) {
      return "n-gon of control points / toLinear then LEC";
    }
    if ("MultiCurvePerfGateTest".equals(harness)) {
      return "CurveOps.linearise then core length";
    }
    return "densify-then-core";
  }

  private static String harnessNote(String harness) {
    if ("ReverseDispatchPerfGateTest".equals(harness)) {
      return "no red baseline was transcribed — do not invent a red column";
    }
    if ("OverlayNGCurvePerfGateTest".equals(harness)) {
      return "red_* are the pre-laser overlay tables; now_* is this tip";
    }
    if ("CurveWKBPerfGateTest".equals(harness)
        || "DirectedHausdorffDistancePerfGateTest".equals(harness)
        || "DiscreteHausdorffDistancePerfGateTest".equals(harness)
        || "DiscreteFrechetDistancePerfGateTest".equals(harness)
        || "LargestEmptyCirclePerfGateTest".equals(harness)
        || "MultiCurvePerfGateTest".equals(harness)) {
      return "previously ungauged / missing from Proofs JSON; now_* from this tip; no red column";
    }
    return null;
  }

  private static final class RedBaseline {
    final String laser;
    final String chainsaw;
    final String ratio;
    RedBaseline(String laser, String chainsaw, String ratio) {
      this.laser = laser;
      this.chainsaw = chainsaw;
      this.ratio = ratio;
    }
  }

  /**
   * Historical red tables from the gate-opening javadoc (c956b50d-era).
   * Not re-measured this run. ReverseDispatch and newly gauged harnesses
   * are intentionally absent.
   */
  private static RedBaseline redOf(String harness, String caseName) {
    if ("OverlayNGCurvePerfGateTest".equals(harness)) {
      if ("disjoint CAP".equals(caseName)) return new RedBaseline("4.169", "0.139", "30.0");
      if ("nested CAP".equals(caseName)) return new RedBaseline("7.894", "0.646", "12.2");
      if ("nested CUP".equals(caseName)) return new RedBaseline("7.914", "0.563", "14.1");
      if ("crossing CAP".equals(caseName)) return new RedBaseline("3.712", "0.339", "11.0");
    }
    if ("CurveOpsDistConPerfGateTest".equals(harness)) {
      if ("distance far discs".equals(caseName)) return new RedBaseline("4.735", "4.000", "1.18");
      if ("distance arc-point".equals(caseName)) return new RedBaseline("0.071", "0.069", "1.02");
      if ("convexHull disc".equals(caseName)) return new RedBaseline("1.031", "1.064", "0.97");
      if ("convexHull half-arc".equals(caseName)) return new RedBaseline("0.369", "0.356", "1.04");
      if ("buffer disc +1".equals(caseName)) return new RedBaseline("0.802", "0.342", "2.35");
    }
    if ("DistanceConstructionPerfGateTest".equals(harness)) {
      if ("Hausdorff two discs".equals(caseName)) return new RedBaseline("29.215", "29.212", "1.00");
      if ("Hausdorff arc-baseline".equals(caseName)) return new RedBaseline("0.049", "0.049", "1.00");
      if ("nearest arc-point".equals(caseName)) return new RedBaseline("0.016", "0.012", "1.29");
      if ("MIC disc".equals(caseName)) return new RedBaseline("0.738", "0.386", "1.91");
    }
    return null;
  }

  static String extract(String json, String key) {
    String raw = extractRaw(json, key);
    if (raw == null) {
      return null;
    }
    if (raw.length() >= 2 && raw.charAt(0) == '"') {
      return unescape(raw.substring(1, raw.length() - 1));
    }
    return raw;
  }

  static String extractRaw(String json, String key) {
    String pat = "\"" + key + "\":";
    int i = json.indexOf(pat);
    if (i < 0) {
      return null;
    }
    i += pat.length();
    while (i < json.length() && json.charAt(i) == ' ') {
      i++;
    }
    if (i >= json.length()) {
      return null;
    }
    if (json.charAt(i) == '"') {
      int j = i + 1;
      StringBuilder b = new StringBuilder();
      b.append('"');
      while (j < json.length()) {
        char c = json.charAt(j);
        if (c == '\\' && j + 1 < json.length()) {
          b.append(c).append(json.charAt(j + 1));
          j += 2;
          continue;
        }
        b.append(c);
        if (c == '"') {
          break;
        }
        j++;
      }
      return b.toString();
    }
    int j = i;
    while (j < json.length()) {
      char c = json.charAt(j);
      if (c == ',' || c == '}' || c == ']') {
        break;
      }
      j++;
    }
    return json.substring(i, j).trim();
  }

  private static String unescape(String s) {
    StringBuilder b = new StringBuilder(s.length());
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      if (c == '\\' && i + 1 < s.length()) {
        b.append(s.charAt(i + 1));
        i++;
      }
      else {
        b.append(c);
      }
    }
    return b.toString();
  }
}

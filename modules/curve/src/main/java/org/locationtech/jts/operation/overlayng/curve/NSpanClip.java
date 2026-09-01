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
package org.locationtech.jts.operation.overlayng.curve;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.geom.curve.MultiSurface;
import org.locationtech.jts.operation.overlayng.OverlayNG;

/**
 * Even-n alternating assemble shared by H-FOUR (disc vs polygon)
 * and H-SHELL-N (two CompoundCurve shells). Each span is a typed
 * member list (one LineString, or a multi-member walk). A tangent
 * is a zero-length span so an odd cut that only fails to alternate
 * at that touch still assembles. CAP / CUP stitch one ring; SUB /
 * XOR pair faces. Zero-length spans are skipped when pairing
 * faces (no empty polygon). Not a noder.
 */
final class NSpanClip {

  private NSpanClip() { }

  /**
   * Ring order of nodes on a flattened edge list: edge index, then
   * parameter along that edge.
   */
  static final Comparator<TwoNodeClip.Node> RING_T =
      new Comparator<TwoNodeClip.Node>() {
        public int compare(TwoNodeClip.Node a, TwoNodeClip.Node b) {
          if (a.edge != b.edge) return a.edge < b.edge ? -1 : 1;
          return Double.compare(a.t, b.t);
        }
      };

  /**
   * CAP / CUP / SUB / XOR of two classified even-n span sets.
   * {@code aFirst} is whether operand A is the {@code aIn}/{@code aOut}
   * side (SUB only). A miss is {@code null}.
   */
  static Geometry overlay(int opCode, boolean aFirst,
      List<List<LineString>> aIn, List<List<LineString>> aOut,
      List<List<LineString>> bIn, List<List<LineString>> bOut,
      GeometryFactory f, double scale) {
    if (aIn == null || aOut == null || bIn == null || bOut == null) {
      return null;
    }
    if (aIn.size() != bIn.size() || aOut.size() != bOut.size()) return null;
    if (aIn.isEmpty() || aOut.isEmpty()) return null;
    if (opCode == OverlayNG.INTERSECTION) {
      return stitchRing(aIn, bIn, f, scale);
    }
    if (opCode == OverlayNG.UNION) {
      return stitchRing(aOut, bOut, f, scale);
    }
    if (opCode == OverlayNG.DIFFERENCE) {
      return multi(aFirst
          ? pairFaces(aOut, bIn, f, scale)
          : pairFaces(bOut, aIn, f, scale), f);
    }
    if (opCode == OverlayNG.SYMDIFFERENCE) {
      List<Polygon> ab = pairFaces(aOut, bIn, f, scale);
      List<Polygon> ba = pairFaces(bOut, aIn, f, scale);
      if (ab == null || ba == null) return null;
      List<Polygon> faces = new ArrayList<Polygon>();
      addFaces(faces, ab);
      addFaces(faces, ba);
      return multi(faces, f);
    }
    return null;
  }

  /**
   * Same assemble when each span is already a single LineString
   * (H-FOUR circle / ring pieces).
   */
  static Geometry overlayLines(int opCode, boolean aFirst,
      List<LineString> aIn, List<LineString> aOut,
      List<LineString> bIn, List<LineString> bOut,
      GeometryFactory f, double scale) {
    return overlay(opCode, aFirst, asSpans(aIn), asSpans(aOut),
        asSpans(bIn), asSpans(bOut), f, scale);
  }

  static List<List<LineString>> asSpans(List<LineString> pieces) {
    List<List<LineString>> out = new ArrayList<List<LineString>>();
    if (pieces == null) return out;
    for (int i = 0; i < pieces.size(); i++) {
      out.add(TwoNodeClip.listOf(pieces.get(i)));
    }
    return out;
  }

  static boolean alternates(boolean[] in) {
    if (in == null || in.length < 2) return false;
    boolean ok = true;
    for (int i = 0; i < in.length && ok; i++) {
      if (in[i] == in[(i + 1) % in.length]) {
        ok = false;
      }
    }
    return ok;
  }

  private static Polygon stitchRing(List<List<LineString>> a,
      List<List<LineString>> b, GeometryFactory f, double scale) {
    List<LineString> members = stitch(a, b, scale);
    if (members == null) return null;
    return TwoNodeClip.closeRing(members, f,
        Math.max(TwoNodeClip.PROPER_CROSS_FRAC * scale, 1.0e-12));
  }

  private static List<LineString> stitch(List<List<LineString>> a,
      List<List<LineString>> b, double scale) {
    double eps = Math.max(TwoNodeClip.PROPER_CROSS_FRAC * scale, 1.0e-12);
    List<LineString> out = new ArrayList<LineString>();
    boolean[] usedA = new boolean[a.size()];
    boolean[] usedB = new boolean[b.size()];
    Coordinate cur = startOf(b.get(0));
    Coordinate origin = cur;
    boolean wantA = false;
    int steps = a.size() + b.size();
    boolean miss = false;
    for (int k = 0; k < steps && !miss; k++) {
      List<List<LineString>> src = wantA ? a : b;
      boolean[] used = wantA ? usedA : usedB;
      int i = indexSpanAt(src, used, cur, eps);
      if (i < 0) {
        miss = true;
      }
      else {
        used[i] = true;
        List<LineString> piece = orientedSpan(src.get(i), cur, eps);
        addMembers(out, piece);
        cur = endOf(piece);
        wantA = !wantA;
      }
    }
    if (miss || cur.distance(origin) > eps) return null;
    return out;
  }

  private static List<Polygon> pairFaces(List<List<LineString>> a,
      List<List<LineString>> b, GeometryFactory f, double scale) {
    List<Polygon> faces = new ArrayList<Polygon>();
    double eps = Math.max(TwoNodeClip.PROPER_CROSS_FRAC * scale, 1.0e-12);
    boolean[] used = new boolean[b.size()];
    boolean miss = false;
    for (int i = 0; i < a.size() && !miss; i++) {
      List<LineString> p = a.get(i);
      if (!isDegenerate(p, eps)) {
        int match = indexSpanConnecting(b, used, endOf(p), startOf(p), eps);
        if (match < 0) {
          miss = true;
        }
        else {
          used[match] = true;
          List<LineString> members = new ArrayList<LineString>();
          addMembers(members, p);
          addMembers(members, orientedSpan(b.get(match), endOf(p), eps));
          Polygon face = TwoNodeClip.closeRing(members, f, eps);
          if (face == null) {
            miss = true;
          }
          else {
            faces.add(face);
          }
        }
      }
    }
    return miss ? null : faces;
  }

  /**
   * A tangent-as-span is a point edge: start equals end and the
   * walk has no length. closeRing already drops those members.
   */
  private static boolean isDegenerate(List<LineString> p, double eps) {
    if (p == null || p.isEmpty()) return true;
    if (startOf(p).distance(endOf(p)) > eps) return false;
    double len = 0.0;
    for (int i = 0; i < p.size(); i++) {
      len += p.get(i).getLength();
    }
    return len <= eps;
  }

  private static int indexSpanAt(List<List<LineString>> spans, boolean[] used,
      Coordinate at, double eps) {
    int found = -1;
    for (int i = 0; i < spans.size(); i++) {
      if (!used[i] && found < 0) {
        List<LineString> g = spans.get(i);
        if (startOf(g).distance(at) <= eps || endOf(g).distance(at) <= eps) {
          found = i;
        }
      }
    }
    return found;
  }

  private static int indexSpanConnecting(List<List<LineString>> spans,
      boolean[] used, Coordinate from, Coordinate to, double eps) {
    int found = -1;
    for (int i = 0; i < spans.size(); i++) {
      if (!used[i] && found < 0) {
        List<LineString> g = spans.get(i);
        boolean startFrom = startOf(g).distance(from) <= eps;
        boolean endFrom = endOf(g).distance(from) <= eps;
        if (startFrom || endFrom) {
          Coordinate other = startFrom ? endOf(g) : startOf(g);
          if (other.distance(to) <= eps) {
            found = i;
          }
        }
      }
    }
    return found;
  }

  private static List<LineString> orientedSpan(List<LineString> parts,
      Coordinate from, double eps) {
    if (startOf(parts).distance(from) <= eps) return parts;
    List<LineString> rev = new ArrayList<LineString>(parts.size());
    for (int i = parts.size() - 1; i >= 0; i--) {
      rev.add((LineString) parts.get(i).reverse());
    }
    return rev;
  }

  private static Coordinate startOf(List<LineString> parts) {
    return parts.get(0).getCoordinateN(0);
  }

  private static Coordinate endOf(List<LineString> parts) {
    LineString last = parts.get(parts.size() - 1);
    return last.getCoordinateN(last.getNumPoints() - 1);
  }

  private static void addMembers(List<LineString> dest, List<LineString> src) {
    for (int i = 0; i < src.size(); i++) {
      dest.add(src.get(i));
    }
  }

  private static void addFaces(List<Polygon> dest, List<Polygon> src) {
    if (src == null) return;
    for (int i = 0; i < src.size(); i++) {
      dest.add(src.get(i));
    }
  }

  private static Geometry multi(List<Polygon> faces, GeometryFactory f) {
    if (faces == null || faces.isEmpty()) return null;
    if (faces.size() == 1) return faces.get(0);
    return new MultiSurface(faces.toArray(new Polygon[0]), f);
  }
}

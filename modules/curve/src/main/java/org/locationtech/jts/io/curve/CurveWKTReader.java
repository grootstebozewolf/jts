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
package org.locationtech.jts.io.curve;

import java.io.IOException;
import java.io.StreamTokenizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.CoordinateSequence;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.LinearRing;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.geom.curve.CircularString;
import org.locationtech.jts.geom.curve.CurveGeometryFactory;
import org.locationtech.jts.geom.curve.ClothoidSegment;
import org.locationtech.jts.geom.curve.CompoundCurve;
import org.locationtech.jts.geom.curve.CurvePolygon;
import org.locationtech.jts.geom.curve.MultiCurve;
import org.locationtech.jts.geom.curve.MultiSurface;
import org.locationtech.jts.geom.curve.PolyhedralSurface;
import org.locationtech.jts.geom.curve.Tin;
import org.locationtech.jts.geom.curve.Triangle;
import org.locationtech.jts.io.Ordinate;
import org.locationtech.jts.io.ParseException;
import org.locationtech.jts.io.WKTConstants;
import org.locationtech.jts.io.WKTReader;

/**
 * A {@link WKTReader} subclass that recognises the ISO/IEC 13249-3
 * SQL/MM curve keywords via the {@code readOtherGeometryText} extension
 * point in core. Accepts both glued and spaced dimension suffixes
 * ({@code CIRCULARSTRINGZ} and {@code CIRCULARSTRING Z}), matching GEOS.
 * <ul>
 *   <li>{@code CIRCULARSTRING}</li>
 *   <li>{@code COMPOUNDCURVE}</li>
 *   <li>{@code CURVEPOLYGON}</li>
 *   <li>{@code MULTICURVE}</li>
 *   <li>{@code MULTISURFACE}</li>
 *   <li>{@code TRIANGLE}</li>
 *   <li>{@code POLYHEDRALSURFACE}</li>
 *   <li>{@code TIN}</li>
 * </ul>
 * <p>
 * This is a phase-1 implementation: composite types (CompoundCurve,
 * CurvePolygon, MultiCurve, MultiSurface) collapse member structure to
 * concatenated coordinates / linearised rings on read. The classes are
 * structurally simple wrappers over their parent geometry types so the
 * existing JTS algorithm suite continues to work, treating curves as
 * polylines and curve-bounded surfaces as polygons.
 */
public class CurveWKTReader extends WKTReader {

  private static final String L_PAREN = "(";
  private static final String R_PAREN = ")";
  private static final String COMMA = ",";

  /**
   * grammars-v4 WKT extension (#4847 / #4848). Opt-in: only this reader
   * recognises {@code CLOTHOID}, and only as a non-leading COMPOUNDCURVE
   * member. Core {@link WKTReader} still fails on the keyword.
   */
  private static final String CLOTHOID = "CLOTHOID";

  /**
   * Per §3.3 of the proposal: the typed coordinate is authoritative when
   * a CLOTHOID's analytical end disagrees with the next member's typed
   * start, but a warning is emitted if the drift exceeds {@code 1e-9}
   * (relative to chord length, with an absolute floor of {@code 1e-9} m).
   * Warnings accumulate here per-reader-instance; expose for callers
   * who want to surface them in tooling output.
   */
  private final List<String> warnings = new ArrayList<String>();

  /** Junction drift threshold relative to chord length (§3.3). */
  private static final double JUNCTION_DRIFT_REL = 1e-9;
  private static final double JUNCTION_DRIFT_ABS = 1e-9;

  /** Returns warnings accumulated across this reader's parses (junction
   *  drift, etc.). Read-only and additive across multiple read() calls. */
  public List<String> getWarnings() {
    return Collections.unmodifiableList(warnings);
  }

  /** Clears accumulated warnings. Call before a new parse if you want
   *  per-call warning isolation. */
  public void clearWarnings() {
    warnings.clear();
  }

  public CurveWKTReader() {
    super(new CurveGeometryFactory());
  }

  public CurveWKTReader(GeometryFactory geometryFactory) {
    super(geometryFactory);
  }

  @Override
  protected Geometry readOtherGeometryText(StreamTokenizer tokenizer, String type, EnumSet<Ordinate> ordinateFlags)
      throws IOException, ParseException {
    if (matchesType(type, WKTConstants.TRIANGLE)) {
      return readTriangleText(tokenizer, ordinateFlags);
    }
    if (matchesType(type, WKTConstants.POLYHEDRALSURFACE)) {
      return readPolyhedralSurfaceText(tokenizer, ordinateFlags);
    }
    if (matchesType(type, WKTConstants.TIN)) {
      return readTinText(tokenizer, ordinateFlags);
    }
    if (matchesType(type, WKTConstants.CIRCULARSTRING)) {
      return readCircularStringText(tokenizer, ordinateFlags);
    }
    if (matchesType(type, WKTConstants.COMPOUNDCURVE)) {
      return readCompoundCurveText(tokenizer, ordinateFlags);
    }
    if (matchesType(type, WKTConstants.CURVEPOLYGON)) {
      return readCurvePolygonText(tokenizer, ordinateFlags);
    }
    if (matchesType(type, WKTConstants.MULTICURVE)) {
      return readMultiCurveText(tokenizer, ordinateFlags);
    }
    if (matchesType(type, WKTConstants.MULTISURFACE)) {
      return readMultiSurfaceText(tokenizer, ordinateFlags);
    }
    return super.readOtherGeometryText(tokenizer, type, ordinateFlags);
  }

  /** Match a type keyword optionally followed by a Z, M or ZM suffix. */
  private static boolean matchesType(String type, String typeName) {
    if (!type.startsWith(typeName)) return false;
    String mod = type.substring(typeName.length());
    return mod.length() == 0 || mod.equals(WKTConstants.Z)
        || mod.equals(WKTConstants.M) || mod.equals(WKTConstants.ZM);
  }

  private Triangle readTriangleText(StreamTokenizer tokenizer, EnumSet<Ordinate> ordinateFlags)
      throws IOException, ParseException {
    Polygon p = readPolygonText(tokenizer, ordinateFlags);
    if (p.isEmpty()) return new Triangle(geometryFactory);
    return new Triangle((LinearRing) p.getExteriorRing(), geometryFactory);
  }

  private PolyhedralSurface readPolyhedralSurfaceText(StreamTokenizer tokenizer, EnumSet<Ordinate> ordinateFlags)
      throws IOException, ParseException {
    return new PolyhedralSurface(readPolygonArray(tokenizer, ordinateFlags), geometryFactory);
  }

  private Tin readTinText(StreamTokenizer tokenizer, EnumSet<Ordinate> ordinateFlags)
      throws IOException, ParseException {
    return new Tin(readPolygonArray(tokenizer, ordinateFlags), geometryFactory);
  }

  private Polygon[] readPolygonArray(StreamTokenizer tokenizer, EnumSet<Ordinate> ordinateFlags)
      throws IOException, ParseException {
    String tok = getNextEmptyOrOpener(tokenizer);
    if (tok.equals(WKTConstants.EMPTY)) return new Polygon[0];
    List<Polygon> polygons = new ArrayList<Polygon>();
    do {
      polygons.add(readPolygonText(tokenizer, ordinateFlags));
      tok = getNextCloserOrComma(tokenizer);
    } while (tok.equals(","));
    return polygons.toArray(new Polygon[0]);
  }

  private CircularString readCircularStringText(StreamTokenizer tokenizer, EnumSet<Ordinate> ordinateFlags)
      throws IOException, ParseException {
    LineString ls = readLineStringText(tokenizer, ordinateFlags);
    CoordinateSequence seq = ls.getCoordinateSequence();
    if (CircularString.isRefusedDiameterOnRamp(seq)) {
      throw parseErrorWithLine(tokenizer, CircularString.refusedDiameterMessage());
    }
    try {
      seq = CircularString.onAddOrRead(seq, csFactory);
    }
    catch (IllegalArgumentException e) {
      throw parseErrorWithLine(tokenizer, e.getMessage());
    }
    if (!CircularString.isValidControlCount(seq)) {
      throw parseErrorWithLine(tokenizer,
          "CIRCULARSTRING must have an odd number of points >= 3. "
          + "Four-item CIRCULARSTRING (A, B, C, A) is rejected "
          + "(EX-CS-4 / ADR min ring is out). ISO/IEC 13249-3.");
    }
    return new CircularString(seq, geometryFactory);
  }

  private CompoundCurve readCompoundCurveText(StreamTokenizer tokenizer, EnumSet<Ordinate> ordinateFlags)
      throws IOException, ParseException {
    String tok = getNextEmptyOrOpener(tokenizer);
    if (tok.equals(WKTConstants.EMPTY)) {
      return new CompoundCurve(createCoordinateSequenceEmpty(ordinateFlags), geometryFactory);
    }
    // Choose between SFA-structured form `((...), CIRCULARSTRING(...), ...)`
    // and the writer's flat round-trip form `(p, p, p, ...)`.
    String w = lookAheadWord(tokenizer);
    if (!w.equals(L_PAREN) && !isCurveMemberTag(w)) {
      List<Coordinate> coords = new ArrayList<Coordinate>();
      do {
        coords.add(getCoordinate(tokenizer, ordinateFlags, false));
      } while (getNextCloserOrComma(tokenizer).equals(","));
      return new CompoundCurve(csFactory.create(coords.toArray(new Coordinate[0])), geometryFactory);
    }
    List<LineString> mems = new ArrayList<LineString>();
    do {
      String peek = lookAheadWord(tokenizer);
      if (peek.equalsIgnoreCase(CLOTHOID)) {
        if (mems.isEmpty()) {
          throw parseErrorWithLine(tokenizer,
              "CLOTHOID may not be the first member of a COMPOUNDCURVE; "
              + "needs a preceding LineString or CircularString for start state");
        }
        getNextWord(tokenizer);
        LineString prev = mems.get(mems.size() - 1);
        mems.add(readClothoidSegmentText(tokenizer, prev));
      }
      else {
        LineString m = readCurveMember(tokenizer, ordinateFlags);
        // §3.3 — if the previous member was a CLOTHOID, its analytical
        // end is the cursor. The typed first coordinate of this member
        // should match it; the typed coord wins, but drift beyond the
        // threshold emits a warning.
        if (!mems.isEmpty()) {
          LineString prevMem = mems.get(mems.size() - 1);
          if (prevMem instanceof ClothoidSegment) {
            Coordinate[] cc = m.getCoordinates();
            if (cc.length >= 1) {
              checkJunctionDrift(((ClothoidSegment) prevMem).getEndCoordinate(),
                  cc, mems.size());
            }
          }
        }
        mems.add(m);
      }
      tok = getNextCloserOrComma(tokenizer);
    } while (tok.equals(","));
    return new CompoundCurve(mems.toArray(new LineString[0]), geometryFactory);
  }

  /** Reads {@code (k0, k1, L)} and inherits start state from {@code prev}. */
  private ClothoidSegment readClothoidSegmentText(StreamTokenizer tokenizer,
      LineString prev) throws IOException, ParseException {
    String tok = getNextWord(tokenizer);
    if (!tok.equals(L_PAREN)) {
      throw parseErrorWithLine(tokenizer, "Expected '(' after CLOTHOID, got " + tok);
    }
    double k0 = readClothoidScalar(tokenizer);
    expectClothoidComma(tokenizer);
    double k1 = readClothoidScalar(tokenizer);
    expectClothoidComma(tokenizer);
    double len = readClothoidScalar(tokenizer);
    String close = getNextWord(tokenizer);
    if (!close.equals(R_PAREN)) {
      throw parseErrorWithLine(tokenizer, "Expected ')' to close CLOTHOID, got " + close);
    }
    Coordinate start = ClothoidSegment.endPointOf(prev);
    double tangent = ClothoidSegment.endTangentOf(prev);
    return new ClothoidSegment(start, tangent, k0, k1, len, geometryFactory);
  }

  private double readClothoidScalar(StreamTokenizer tokenizer)
      throws IOException, ParseException {
    String s = getNextWord(tokenizer);
    try {
      return Double.parseDouble(s);
    }
    catch (NumberFormatException e) {
      throw parseErrorWithLine(tokenizer, "Invalid CLOTHOID scalar: " + s);
    }
  }

  private void expectClothoidComma(StreamTokenizer tokenizer)
      throws IOException, ParseException {
    String c = getNextWord(tokenizer);
    if (!c.equals(COMMA)) {
      throw parseErrorWithLine(tokenizer, "Expected ',' inside CLOTHOID body, got " + c);
    }
  }

  /**
   * §3.3 — drift check. Compares the typed first coordinate of
   * {@code memberCoords} against {@code analyticalEnd} (the previous
   * CLOTHOID's analytical end). Drift beyond {@code 1e-9} relative to
   * the new member's chord length (with an absolute floor of
   * {@code 1e-9} m) emits a warning. The typed coordinate is still
   * authoritative for the constructed geometry.
   */
  private void checkJunctionDrift(Coordinate analyticalEnd, Coordinate[] memberCoords,
                                  int newMemberIndex) {
    Coordinate typedStart = memberCoords[0];
    double dx = typedStart.x - analyticalEnd.x;
    double dy = typedStart.y - analyticalEnd.y;
    double drift = Math.hypot(dx, dy);
    double chord = chordLength(memberCoords);
    double threshold = Math.max(JUNCTION_DRIFT_REL * chord, JUNCTION_DRIFT_ABS);
    if (drift > threshold) {
      warnings.add(String.format(Locale.ROOT,
          "junction drift %.6e m at COMPOUNDCURVE member index %d "
          + "(typed start %s, analytical end of preceding CLOTHOID %s); "
          + "typed coordinate is authoritative per proposal §3.3",
          drift, newMemberIndex, typedStart, analyticalEnd));
    }
  }

  private static double chordLength(Coordinate[] coords) {
    if (coords.length < 2) return 0.0;
    Coordinate s = coords[0];
    Coordinate e = coords[coords.length - 1];
    return Math.hypot(e.x - s.x, e.y - s.y);
  }

  private CurvePolygon readCurvePolygonText(StreamTokenizer tokenizer, EnumSet<Ordinate> ordinateFlags)
      throws IOException, ParseException {
    String tok = getNextEmptyOrOpener(tokenizer);
    if (tok.equals(WKTConstants.EMPTY)) return new CurvePolygon(geometryFactory);
    // Rings may be a single curve member, or a parenthesised list of
    // members forming a CompoundCurve shell/hole (ISO/SQL-MM).
    LineString structuralShell = readCurvePolygonRing(tokenizer, ordinateFlags);
    List<LineString> structuralHoles = new ArrayList<LineString>();
    tok = getNextCloserOrComma(tokenizer);
    while (tok.equals(",")) {
      structuralHoles.add(readCurvePolygonRing(tokenizer, ordinateFlags));
      tok = getNextCloserOrComma(tokenizer);
    }
    return new CurvePolygon(structuralShell,
        structuralHoles.toArray(new LineString[0]), geometryFactory);
  }

  /**
   * One CurvePolygon ring: {@code CIRCULARSTRING(...)} /
   * {@code COMPOUNDCURVE(...)} / plain {@code (...)} coords, or a
   * compound list {@code (CIRCULARSTRING(...), (x y, ...), ...)}.
   */
  private LineString readCurvePolygonRing(StreamTokenizer tokenizer,
      EnumSet<Ordinate> ordinateFlags) throws IOException, ParseException {
    String w = lookAheadWord(tokenizer);
    if (w.equals(L_PAREN)) {
      getNextWord(tokenizer); // consume '('
      String inner = lookAheadWord(tokenizer);
      if (isTaggedCurveWord(inner) || inner.equalsIgnoreCase(CLOTHOID)) {
        List<LineString> members = new ArrayList<LineString>();
        do {
          members.add(readCurveMember(tokenizer, ordinateFlags));
          w = getNextCloserOrComma(tokenizer);
        } while (w.equals(","));
        if (members.size() == 1) {
          return members.get(0);
        }
        return new CompoundCurve(members.toArray(new LineString[0]),
            geometryFactory);
      }
      // Untagged coordinate ring; '(' already consumed.
      List<Coordinate> coordinates = new ArrayList<Coordinate>();
      do {
        coordinates.add(getCoordinate(tokenizer, ordinateFlags, false));
      } while (getNextCloserOrComma(tokenizer).equals(","));
      return geometryFactory.createLineString(
          coordinates.toArray(new Coordinate[0]));
    }
    return readCurveMember(tokenizer, ordinateFlags);
  }

  private static boolean isTaggedCurveWord(String w) {
    if (w == null) return false;
    String u = w.toUpperCase(Locale.ROOT);
    return u.equals(WKTConstants.CIRCULARSTRING)
        || u.equals(WKTConstants.COMPOUNDCURVE)
        || u.equals(WKTConstants.LINESTRING);
  }

  private MultiCurve readMultiCurveText(StreamTokenizer tokenizer, EnumSet<Ordinate> ordinateFlags)
      throws IOException, ParseException {
    String tok = getNextEmptyOrOpener(tokenizer);
    if (tok.equals(WKTConstants.EMPTY)) return new MultiCurve(new LineString[0], geometryFactory);
    List<LineString> members = new ArrayList<LineString>();
    do {
      members.add(readCurveMember(tokenizer, ordinateFlags));
      tok = getNextCloserOrComma(tokenizer);
    } while (tok.equals(","));
    return new MultiCurve(members.toArray(new LineString[0]), geometryFactory);
  }

  private MultiSurface readMultiSurfaceText(StreamTokenizer tokenizer, EnumSet<Ordinate> ordinateFlags)
      throws IOException, ParseException {
    String tok = getNextEmptyOrOpener(tokenizer);
    if (tok.equals(WKTConstants.EMPTY)) return new MultiSurface(new Polygon[0], geometryFactory);
    List<Polygon> members = new ArrayList<Polygon>();
    do {
      members.add(readSurfaceMember(tokenizer, ordinateFlags));
      tok = getNextCloserOrComma(tokenizer);
    } while (tok.equals(","));
    return new MultiSurface(members.toArray(new Polygon[0]), geometryFactory);
  }

  /** Reads a curve aggregate member: untagged {@code (...)}, tagged
   *  CIRCULARSTRING / COMPOUNDCURVE, or EMPTY. Returns a LineString. */
  private LineString readCurveMember(StreamTokenizer tokenizer, EnumSet<Ordinate> ordinateFlags)
      throws IOException, ParseException {
    String w = lookAheadWord(tokenizer);
    if (w.equals(L_PAREN)) return readLineStringText(tokenizer, ordinateFlags);
    if (w.equals(WKTConstants.EMPTY)) {
      getNextWord(tokenizer);
      return geometryFactory.createLineString(createCoordinateSequenceEmpty(ordinateFlags));
    }
    String type = getNextWord(tokenizer).toUpperCase(Locale.ROOT);
    if (type.equals(CLOTHOID)) {
      throw parseErrorWithLine(tokenizer,
          "CLOTHOID is not a top-level geometry and cannot stand alone as a "
          + "curve member; it is a non-leading COMPOUNDCURVE member only");
    }
    Geometry g = readGeometryTaggedText(tokenizer, type, ordinateFlags);
    if (g instanceof LineString) return (LineString) g;
    throw parseErrorWithLine(tokenizer, "Expected curve member but got " + type);
  }

  /** Reads a surface aggregate member: untagged polygon body or tagged CURVEPOLYGON. */
  private Polygon readSurfaceMember(StreamTokenizer tokenizer, EnumSet<Ordinate> ordinateFlags)
      throws IOException, ParseException {
    String w = lookAheadWord(tokenizer);
    if (w.equals(L_PAREN)) return readPolygonText(tokenizer, ordinateFlags);
    String type = getNextWord(tokenizer).toUpperCase(Locale.ROOT);
    Geometry g = readGeometryTaggedText(tokenizer, type, ordinateFlags);
    if (g instanceof Polygon) return (Polygon) g;
    throw parseErrorWithLine(tokenizer, "Expected surface member but got " + type);
  }

  private static boolean isCurveMemberTag(String w) {
    return w.equalsIgnoreCase(WKTConstants.CIRCULARSTRING)
        || w.equalsIgnoreCase(WKTConstants.COMPOUNDCURVE)
        || w.equalsIgnoreCase(CLOTHOID);
  }
}

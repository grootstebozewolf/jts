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
package org.locationtech.jts.io.curved;

import java.io.IOException;
import java.io.StreamTokenizer;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Collections;
import java.util.Locale;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.CoordinateSequence;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.LinearRing;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.geom.curved.CircularString;
import org.locationtech.jts.geom.curved.ClothoidSegment;
import org.locationtech.jts.geom.curved.CompoundCurve;
import org.locationtech.jts.geom.curved.CurvePolygon;
import org.locationtech.jts.geom.curved.MultiCurve;
import org.locationtech.jts.geom.curved.MultiSurface;
import org.locationtech.jts.geom.curved.PolyhedralSurface;
import org.locationtech.jts.geom.curved.Tin;
import org.locationtech.jts.geom.curved.Triangle;
import org.locationtech.jts.io.Ordinate;
import org.locationtech.jts.io.ParseException;
import org.locationtech.jts.io.WKTConstants;
import org.locationtech.jts.io.WKTReader;

/**
 * A {@link WKTReader} subclass that recognises the OGC SFA / ISO 19125-2
 * extended geometry types via the {@code readOtherGeometryText} extension
 * point in core:
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
public class CurvedWKTReader extends WKTReader {

  private static final String L_PAREN = "(";
  private static final String R_PAREN = ")";
  private static final String COMMA   = ",";
  /** JTS extension keyword (proposal: grammars-v4 #4847). Not in OGC SFA. */
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

  public CurvedWKTReader() {
    super();
  }

  public CurvedWKTReader(GeometryFactory geometryFactory) {
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
    return new CircularString(ls.getCoordinateSequence(), geometryFactory);
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
    Coordinate cursorPt = null;
    double cursorTangent = 0.0;
    boolean cursorIsAnalyticalClothoidEnd = false;
    do {
      String peek = lookAheadWord(tokenizer);
      if (peek.equalsIgnoreCase(CLOTHOID)) {
        if (cursorPt == null) {
          throw parseErrorWithLine(tokenizer,
              "CLOTHOID may not be the first member of a COMPOUNDCURVE; "
              + "needs a preceding segment for start state");
        }
        getNextWord(tokenizer); // consume CLOTHOID keyword
        ClothoidSegment cs = readClothoidSegmentText(tokenizer, cursorPt, cursorTangent);
        mems.add(cs);
        cursorPt = cs.getEndCoordinate();
        cursorTangent = cs.getEndTangent();
        cursorIsAnalyticalClothoidEnd = true;
      } else {
        LineString m = readCurveMember(tokenizer, ordinateFlags);
        Coordinate[] cc = m.getCoordinates();
        // §3.3 -- if the previous member was a CLOTHOID, the cursor holds
        // the clothoid's analytical end. The typed first coordinate of
        // this member should match it; the typed coord wins, but drift
        // beyond the threshold emits a warning.
        if (cursorIsAnalyticalClothoidEnd && cc.length >= 1) {
          checkJunctionDrift(cursorPt, cc, mems.size());
        }
        mems.add(m);
        if (cc.length >= 1) {
          cursorPt = cc[cc.length - 1];
          if (m instanceof CircularString && cc.length >= 3) {
            // Analytical arc-tangent at endpoint -- chord direction
            // (p_{n-2} -> p_{n-1}) under-rotates by half the arc angle
            // and would feed a downstream CLOTHOID a wrong start
            // tangent, producing a visible kink at the junction.
            cursorTangent = arcTangentAtEnd(
                cc[cc.length - 3], cc[cc.length - 2], cc[cc.length - 1]);
          } else if (cc.length >= 2) {
            Coordinate prev = cc[cc.length - 2];
            cursorTangent = Math.atan2(cursorPt.y - prev.y, cursorPt.x - prev.x);
          }
        }
        cursorIsAnalyticalClothoidEnd = false;
      }
      tok = getNextCloserOrComma(tokenizer);
    } while (tok.equals(","));
    return new CompoundCurve(mems.toArray(new LineString[0]), geometryFactory);
  }

  /** Reads {@code (k0, k1, L)} body of a CLOTHOID segment. */
  private ClothoidSegment readClothoidSegmentText(StreamTokenizer tokenizer,
      Coordinate startPt, double startTangent) throws IOException, ParseException {
    String tok = getNextWord(tokenizer);
    if (!tok.equals(L_PAREN)) {
      throw parseErrorWithLine(tokenizer, "Expected '(' after CLOTHOID, got " + tok);
    }
    double k0 = readScalar(tokenizer);
    expectComma(tokenizer);
    double k1 = readScalar(tokenizer);
    expectComma(tokenizer);
    double len = readScalar(tokenizer);
    String close = getNextWord(tokenizer);
    if (!close.equals(R_PAREN)) {
      throw parseErrorWithLine(tokenizer, "Expected ')' to close CLOTHOID, got " + close);
    }
    return new ClothoidSegment(startPt, startTangent, k0, k1, len, geometryFactory);
  }

  private double readScalar(StreamTokenizer tokenizer) throws IOException, ParseException {
    String s = getNextWord(tokenizer);
    if (s.equalsIgnoreCase("INF") || s.equalsIgnoreCase("INFINITY")) return Double.POSITIVE_INFINITY;
    if (s.equalsIgnoreCase("-INF") || s.equalsIgnoreCase("-INFINITY")) return Double.NEGATIVE_INFINITY;
    if (s.equalsIgnoreCase("NAN")) return Double.NaN;
    try { return Double.parseDouble(s); }
    catch (NumberFormatException e) {
      throw parseErrorWithLine(tokenizer, "Invalid scalar: " + s);
    }
  }

  private void expectComma(StreamTokenizer tokenizer) throws IOException, ParseException {
    String c = getNextWord(tokenizer);
    if (!c.equals(COMMA)) {
      throw parseErrorWithLine(tokenizer, "Expected ',' inside CLOTHOID body, got " + c);
    }
  }

  /**
   * §3.3 — drift check. Compares the typed first coordinate of {@code memberCoords}
   * against {@code analyticalEnd} (the previous CLOTHOID's analytical end stored
   * in the parser's cursor). Drift beyond {@code 1e-9} relative to the new
   * member's chord length (with an absolute floor of {@code 1e-9} m) emits a
   * warning. The typed coordinate is still authoritative for the constructed
   * geometry; the warning is purely informational.
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
      warnings.add(String.format(
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

  /**
   * Analytical tangent at the end of a 3-point circular arc. Computes the
   * circumcentre of {@code (p0, p1, p2)} and returns the heading of the
   * tangent at {@code p2} in the traversal direction implied by the
   * triple. Falls back to the chord direction if the points are collinear.
   */
  private static double arcTangentAtEnd(Coordinate p0, Coordinate p1, Coordinate p2) {
    double ax = (p0.x + p1.x) * 0.5;
    double ay = (p0.y + p1.y) * 0.5;
    double bx = (p1.x + p2.x) * 0.5;
    double by = (p1.y + p2.y) * 0.5;
    double dax = p1.y - p0.y;          // perpendicular to p0->p1
    double day = p0.x - p1.x;
    double dbx = p2.y - p1.y;          // perpendicular to p1->p2
    double dby = p1.x - p2.x;
    double det = dax * dby - day * dbx;
    if (Math.abs(det) < 1e-12) {
      return Math.atan2(p2.y - p1.y, p2.x - p1.x);
    }
    double t = ((bx - ax) * dby - (by - ay) * dbx) / det;
    double cx = ax + t * dax;
    double cy = ay + t * day;
    double rx = p2.x - cx;
    double ry = p2.y - cy;
    double cross = (p1.x - p0.x) * (p2.y - p1.y) - (p1.y - p0.y) * (p2.x - p1.x);
    if (cross >= 0) {
      // CCW traversal -- tangent rotated 90° CCW from outward radius
      return Math.atan2(rx, -ry);
    }
    // CW traversal -- tangent rotated 90° CW from outward radius
    return Math.atan2(-rx, ry);
  }

  private CurvePolygon readCurvePolygonText(StreamTokenizer tokenizer, EnumSet<Ordinate> ordinateFlags)
      throws IOException, ParseException {
    String tok = getNextEmptyOrOpener(tokenizer);
    if (tok.equals(WKTConstants.EMPTY)) return new CurvePolygon(geometryFactory);
    List<LinearRing> rings = new ArrayList<LinearRing>();
    do {
      Coordinate[] coords = readCurveMember(tokenizer, ordinateFlags).getCoordinates();
      rings.add(geometryFactory.createLinearRing(coords));
      tok = getNextCloserOrComma(tokenizer);
    } while (tok.equals(","));
    LinearRing shell = rings.remove(0);
    return new CurvePolygon(shell, rings.toArray(new LinearRing[0]), geometryFactory);
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
        || w.equalsIgnoreCase(WKTConstants.COMPOUNDCURVE);
  }
}

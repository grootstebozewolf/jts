/*
 * Copyright (c) 2016 Vivid Solutions.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * and Eclipse Distribution License v. 1.0 which accompanies this distribution.
 * The Eclipse Public License is available at http://www.eclipse.org/legal/epl-v20.html
 * and the Eclipse Distribution License is available at
 *
 * http://www.eclipse.org/org/documents/edl-v10.php.
 */

package org.locationtech.jtstest.testbuilder.geom;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.locationtech.jts.algorithm.PointLocator;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.LinearRing;
import org.locationtech.jts.geom.Location;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.geom.Polygonal;
import org.locationtech.jts.geom.curve.CircularString;
import org.locationtech.jts.geom.curve.ClothoidSegment;
import org.locationtech.jts.geom.curve.CompoundCurve;
import org.locationtech.jts.geom.curve.CurveGeometryFactory;
import org.locationtech.jts.geom.curve.CurvePolygon;
import org.locationtech.jts.geom.curve.Tin;
import org.locationtech.jts.geom.curve.Triangle;

public class GeometryCombiner 
{
  private GeometryFactory geomFactory;
  
  public GeometryCombiner(GeometryFactory geomFactory) {
    this.geomFactory = geomFactory;
  }

  public Geometry addPolygonRing(Geometry orig, Coordinate[] pts)
  {
    LinearRing ring = geomFactory.createLinearRing(pts);
    
    if (orig == null) {
      return geomFactory.createPolygon(ring, null);
    }
    if (! (orig instanceof Polygonal)) {
      return combine(orig, 
          geomFactory.createPolygon(ring, null));
    }
    // add the ring as either a hole or a shell
    Polygon polyContaining = findPolygonContaining(orig, pts[0]);
    if (polyContaining == null) {
      return combine(orig, geomFactory.createPolygon(ring, null));
    }
    
    // add ring as hole
    Polygon polyWithHole = addHole(polyContaining, ring);
    return replace(orig, polyContaining, polyWithHole);
  }
  
  public Geometry addLineString(Geometry orig, Coordinate[] pts)
  {
    LineString line = geomFactory.createLineString(pts);
    return combine(orig, line);
  }

  /**
   * Builds a {@link CircularString} through {@code pts}.
   * <p>
   * Honest factory only: {@code createCircularString}. Do not call
   * {@code createLineString} or {@code createCompoundCurve(CoordinateSequence)}
   * — those emit a control-point polyline whose CurveWKT is
   * {@code LINESTRING} / a COMPOUNDCURVE wrapping a LineString.
   * Even leftover or fewer than 3 points abort without adding.
   * <p>
   * The first component is returned as-is. {@link #combine} would be
   * safe for a single member ({@code buildGeometry} of one item returns
   * that item), but skipping it keeps the drawn triple off the
   * collection-rebuild path that boxed arcs into a plain MultiLineString
   * before {@link CurveGeometryFactory#buildGeometry} learned to upgrade.
   */
  public Geometry addCircularString(Geometry orig, Coordinate[] pts)
  {
    if (CircularString.isRefusedDiameterOnRamp(pts)) {
      throw new IllegalArgumentException(CircularString.refusedDiameterMessage());
    }
    if (!isValidCircularControl(pts)) {
      return orig;
    }
    CircularString arc = circularString(pts);
    if (orig == null || orig.isEmpty()) {
      return arc;
    }
    return combine(orig, arc);
  }

  /**
   * Builds a one-member {@link CompoundCurve} whose member is a
   * {@link CircularString} through {@code pts}.
   * <p>
   * Do not call {@code CurveGeometryFactory.createCompoundCurve(CoordinateSequence)}
   * here: on this branch that constructor is legacy and wraps the points as a
   * single plain {@link LineString}. WKT would still say {@code COMPOUNDCURVE}
   * but the member would be a control-point polyline, not an arc.
   * Honest construction is {@code createCircularString} then
   * {@code createCompoundCurve(new LineString[] { arc })}.
   * Even leftover (even count) or fewer than 3 points abort without adding.
   */
  public Geometry addCompoundCurve(Geometry orig, Coordinate[] pts)
  {
    if (!isValidCircularControl(pts)) {
      return orig;
    }
    return addCompoundCurve(orig, new Coordinate[][] { pts });
  }

  /**
   * Builds a {@link CompoundCurve} from joining {@link CircularString} pieces.
   * Each piece must have an odd count &ge; 3; adjacent pieces must share the
   * join point. Aborts (returns {@code orig} unchanged) on even leftover,
   * a short piece, or a disconnected join.
   */
  public Geometry addCompoundCurve(Geometry orig, Coordinate[][] pieces)
  {
    LineString[] members = circularStringMembers(pieces);
    if (members == null) {
      return orig;
    }
    CompoundCurve cc = curveFactory().createCompoundCurve(members);
    return combine(orig, cc);
  }

  /**
   * Builds a hole-free {@link CurvePolygon} whose shell is a closed
   * {@link CircularString} through {@code pts}. Unclosed or even leftover
   * input aborts; this does not emit a linearized {@link Polygon}.
   * Uses {@code createCurvePolygon(shell, null)}, not a CircularString-only
   * factory overload.
   */
  public Geometry addCurvePolygon(Geometry orig, Coordinate[] pts)
  {
    if (!isValidCircularControl(pts) || !isClosedRing(pts)) {
      return orig;
    }
    CircularString shell = circularString(pts);
    CurvePolygon poly = curveFactory().createCurvePolygon(shell, null);
    return combine(orig, poly);
  }

  /**
   * Builds a hole-free {@link CurvePolygon} from joining closed pieces.
   * One valid closed CircularString piece becomes a {@link CircularString}
   * shell; two or more pieces (CircularString and/or 2-point LineString)
   * become a {@link CompoundCurve} shell. Unclosed or degenerate input
   * aborts.
   */
  public Geometry addCurvePolygon(Geometry orig, Coordinate[][] pieces)
  {
    LineString[] members = curveShellMembers(pieces);
    if (members == null) {
      return orig;
    }
    LineString shell;
    if (members.length == 1) {
      shell = members[0];
    }
    else {
      shell = curveFactory().createCompoundCurve(members);
    }
    if (!isClosedShell(shell)) {
      return orig;
    }
    CurvePolygon poly = curveFactory().createCurvePolygon(shell, null);
    return combine(orig, poly);
  }

  /**
   * Appends a 2-point (or longer) LineString as a CompoundCurve member.
   * Used for the OGC first piece of a highway-entry clothoid.
   */
  public Geometry addCompoundCurveLine(Geometry orig, Coordinate[] pts)
  {
    if (pts == null || pts.length < 2) {
      return orig;
    }
    LineString line = geomFactory.createLineString(pts);
    return appendMembers(orig, new LineString[] { line });
  }

  /**
   * Appends a non-leading {@link ClothoidSegment} to an existing lineal
   * geometry. Start point, tangent and {@code k0} are inherited from the
   * previous member (0 after a line, 1/R after an arc). Leading clothoid
   * (no previous member) and {@code k0 == k1} abort.
   */
  public Geometry addClothoid(Geometry orig, double endKappa, double length)
  {
    LineString[] prev = asMembers(orig);
    if (prev == null || prev.length == 0) {
      return orig;
    }
    LineString last = prev[prev.length - 1];
    double k0 = ClothoidSegment.endKappaOf(last);
    if (Math.abs(endKappa - k0) < 1e-15 || length <= 0) {
      return orig;
    }
    ClothoidSegment cl = new ClothoidSegment(
        ClothoidSegment.endPointOf(last),
        ClothoidSegment.endTangentOf(last),
        k0, endKappa, length, curveFactory());
    return appendMembers(orig, new LineString[] { cl });
  }

  /**
   * Splits an odd-length CircularString control stream into consecutive
   * 3-point arc pieces (shared join). Returns {@code null} when the
   * stream is not a valid CircularString control sequence.
   */
  public static Coordinate[][] circularStringPieces(Coordinate[] pts)
  {
    if (!isValidCircularControl(pts)) {
      return null;
    }
    int n = (pts.length - 1) / 2;
    Coordinate[][] pieces = new Coordinate[n][];
    for (int i = 0; i < n; i++) {
      pieces[i] = new Coordinate[] {
          pts[2 * i], pts[2 * i + 1], pts[2 * i + 2]
      };
    }
    return pieces;
  }

  /**
   * Builds a Triangle (Polygon with a single closed 4-point ring,
   * no holes) from the three captured corner coordinates and combines
   * it with {@code orig}. The closing point (== first) is appended
   * automatically.
   */
  public Geometry addTriangle(Geometry orig, Coordinate[] corners)
  {
    if (corners.length < 3) {
      // Defensive: degrade to nothing rather than throw.
      return orig == null ? geomFactory.createGeometryCollection() : orig;
    }
    CurveGeometryFactory cgf = (geomFactory instanceof CurveGeometryFactory)
        ? (CurveGeometryFactory) geomFactory
        : new CurveGeometryFactory(geomFactory.getPrecisionModel(), geomFactory.getSRID());
    Coordinate[] ring = new Coordinate[] {
        corners[0], corners[1], corners[2], new Coordinate(corners[0])
    };
    LinearRing shell = geomFactory.createLinearRing(ring);
    Triangle tri = cgf.createTriangle(shell);
    return combine(orig, tri);
  }

  /**
   * Builds a Tin from {@code coords} interpreted as consecutive groups
   * of three corner coordinates (one triangular patch per triple). If
   * {@code orig} is null, the Tin is returned directly so the
   * subclass survives the "first geometry in the model" path; otherwise
   * the Tin is run through {@link #combine(Geometry, Geometry)} which
   * may degrade it to a {@link org.locationtech.jts.geom.MultiPolygon}
   * (a known phase-1 limitation tied to
   * {@link #extractElements(Geometry, boolean)} flattening collections).
   */
  public Geometry addTin(Geometry orig, Coordinate[] coords)
  {
    int n = coords.length / 3;
    if (n < 1) {
      return orig == null ? geomFactory.createGeometryCollection() : orig;
    }
    CurveGeometryFactory cgf = (geomFactory instanceof CurveGeometryFactory)
        ? (CurveGeometryFactory) geomFactory
        : new CurveGeometryFactory(geomFactory.getPrecisionModel(), geomFactory.getSRID());
    Polygon[] patches = new Polygon[n];
    for (int i = 0; i < n; i++) {
      Coordinate a = coords[3 * i];
      Coordinate b = coords[3 * i + 1];
      Coordinate c = coords[3 * i + 2];
      Coordinate[] ring = new Coordinate[] { a, b, c, new Coordinate(a) };
      LinearRing shell = geomFactory.createLinearRing(ring);
      patches[i] = cgf.createTriangle(shell);
    }
    Tin tin = cgf.createTin(patches);
    if (orig == null || orig.isEmpty()) return tin;
    return combine(orig, tin);
  }

  public Geometry addPoint(Geometry orig, Coordinate pt)
  {
    Point point = geomFactory.createPoint(pt);
    return combine(orig, point);
  }

  private CurveGeometryFactory curveFactory()
  {
    if (geomFactory instanceof CurveGeometryFactory) {
      return (CurveGeometryFactory) geomFactory;
    }
    return new CurveGeometryFactory(geomFactory.getPrecisionModel(), geomFactory.getSRID());
  }

  private CircularString circularString(Coordinate[] pts)
  {
    return curveFactory().createCircularString(
        geomFactory.getCoordinateSequenceFactory().create(pts));
  }

  /**
   * Returns CircularString members for {@code pieces}, or {@code null} when
   * any piece is even/short or adjacent pieces do not join.
   */
  private LineString[] circularStringMembers(Coordinate[][] pieces)
  {
    if (pieces == null || pieces.length == 0) {
      return null;
    }
    LineString[] members = new LineString[pieces.length];
    for (int i = 0; i < pieces.length; i++) {
      if (!isValidCircularControl(pieces[i])) {
        return null;
      }
      if (i > 0 && !joins(pieces[i - 1], pieces[i])) {
        return null;
      }
      members[i] = circularString(pieces[i]);
    }
    return members;
  }

  /**
   * Shell members for a CurvePolygon: odd-count CircularString pieces
   * and 2-point LineString pieces. Adjacent pieces must join.
   */
  private LineString[] curveShellMembers(Coordinate[][] pieces)
  {
    if (pieces == null || pieces.length == 0) {
      return null;
    }
    LineString[] members = new LineString[pieces.length];
    for (int i = 0; i < pieces.length; i++) {
      Coordinate[] pts = pieces[i];
      if (pts != null && pts.length == 2) {
        members[i] = geomFactory.createLineString(pts);
      }
      else if (isValidCircularControl(pts)) {
        members[i] = circularString(pts);
      }
      else {
        return null;
      }
      if (i > 0 && !joins(pieces[i - 1], pts)) {
        return null;
      }
    }
    return members;
  }

  private static boolean isValidCircularControl(Coordinate[] pts)
  {
    if (pts == null || pts.length < 3) {
      return false;
    }
    if (pts.length % 2 == 1) {
      return true;
    }
    return pts.length == 4 && pts[0].equals2D(pts[3]);
  }

  private static boolean isClosedRing(Coordinate[] pts)
  {
    return pts[0].equals2D(pts[pts.length - 1]);
  }

  private static boolean isClosedShell(LineString shell)
  {
    Coordinate[] pts = shell.getCoordinates();
    return pts.length >= 2 && pts[0].equals2D(pts[pts.length - 1]);
  }

  private static boolean joins(Coordinate[] prev, Coordinate[] next)
  {
    return prev[prev.length - 1].equals2D(next[0]);
  }

  private Geometry appendMembers(Geometry orig, LineString[] extra)
  {
    LineString[] prev = asMembers(orig);
    if (prev == null) {
      return orig;
    }
    LineString[] all = new LineString[prev.length + extra.length];
    System.arraycopy(prev, 0, all, 0, prev.length);
    System.arraycopy(extra, 0, all, prev.length, extra.length);
    return curveFactory().createCompoundCurve(all);
  }

  private static LineString[] asMembers(Geometry orig)
  {
    if (orig == null || orig.isEmpty()) {
      return new LineString[0];
    }
    if (orig instanceof CompoundCurve) {
      return ((CompoundCurve) orig).getMembers();
    }
    if (orig instanceof LineString) {
      return new LineString[] { (LineString) orig };
    }
    return null;
  }
  
  private static Polygon findPolygonContaining(Geometry geom, Coordinate pt)
  {
    PointLocator locator = new PointLocator();
    for (int i = 0; i < geom.getNumGeometries(); i++) {
      Polygon poly = (Polygon) geom.getGeometryN(i);
      int loc = locator.locate(pt, poly);
      if (loc == Location.INTERIOR)
        return poly;
    }
    return null;
  }
  
  public Polygon addHole(Polygon poly, LinearRing hole)
  {
    int nOrigHoles = poly.getNumInteriorRing();
    LinearRing[] newHoles = new LinearRing[nOrigHoles + 1];
    for (int i = 0; i < nOrigHoles; i++) {
      newHoles[i] = poly.getInteriorRingN(i);
    }
    newHoles[nOrigHoles] = hole;
    return geomFactory.createPolygon(poly.getExteriorRing(), newHoles);
  }
  
  public Geometry combine(Geometry orig, Geometry geom)
  {
    List origList = extractElements(orig, true);
    List geomList = extractElements(geom, true);
    origList.addAll(geomList);
    
    if (origList.size() == 0) {
      // return a clone of the orig geometry
      return (Geometry) orig.clone();
    }
    // Curve factory so a CircularString is not rebuilt as a LineString
    // (or boxed in a plain MultiLineString) when the model factory is core.
    return curveFactory().buildGeometry(origList);
  }
  
  public static List extractElements(Geometry geom, boolean skipEmpty)
  {
    List elem = new ArrayList();
    if (geom == null)
      return elem;
    
    for (int i = 0; i < geom.getNumGeometries(); i++) {
      Geometry elemGeom = geom.getGeometryN(i);
      if (skipEmpty && elemGeom.isEmpty())
        continue;
      elem.add(elemGeom);
    }
    return elem;
  }
  
  public static Geometry replace(Geometry parent, Geometry original, Geometry replacement)
  {
    List elem = extractElements(parent, false);
    Collections.replaceAll(elem, original, replacement);
    return parent.getFactory().buildGeometry(elem);
  }
}

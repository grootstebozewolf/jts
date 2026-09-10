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
package org.locationtech.jts.geom.curve;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.CoordinateFilter;
import org.locationtech.jts.geom.CoordinateSequence;
import org.locationtech.jts.geom.CoordinateSequenceFilter;
import org.locationtech.jts.geom.CoordinateSequences;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.IntersectionMatrix;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.LinearRing;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.io.curve.CurveWKTWriter;

/**
 * A polygon whose rings may be straight, circular, or compound curves.
 *
 * <p><b>Option-A spike (F-CP / FCP-DOVE):</b> a structural shell can be
 * supplied via the {@code (LineString structuralShell, ...)} constructor;
 * the legacy {@code Polygon.getExteriorRing()} still returns a flat
 * {@link LinearRing} derived from {@code structuralShell.getCoordinates()}
 * so existing jts-core callers keep working, and curve-aware callers go
 * through {@link #getExteriorCurve()} to retrieve the structural shell.
 * This is the leaning option in {@code SPEC_F_CP.md}; the spike here is
 * the smallest implementation that lets the F-CP red tests assert against
 * a real accessor instead of a placeholder.
 */
public class CurvePolygon extends Polygon implements Linearizable {
  private static final long serialVersionUID = 1L;

  /**
   * The structural shell as supplied by the caller — may be a
   * {@link LineString}, {@link LinearRing}, {@code CircularString}, or
   * {@code CompoundCurve}. {@code null} for the legacy construction path
   * that takes a {@link LinearRing} directly.
   */
  private final LineString structuralShell;

  /**
   * The structural holes as supplied by the caller, in the same order as the
   * legacy {@link LinearRing} holes. Never {@code null} once constructed;
   * empty for a shell-only polygon.
   */
  private final LineString[] structuralHoles;

  public CurvePolygon(LinearRing shell, LinearRing[] holes, GeometryFactory factory) {
    super(shell, holes, factory);
    this.structuralShell = shell;
    this.structuralHoles = holes == null ? new LineString[0] : holes.clone();
  }

  public CurvePolygon(GeometryFactory factory) {
    super(null, null, factory);
    this.structuralShell = null;
    this.structuralHoles = new LineString[0];
  }

  /**
   * Option-A constructor: accepts a structural shell (any {@link LineString} —
   * typically a {@code CircularString} or {@code CompoundCurve}) and derives
   * the legacy {@link LinearRing} the {@link Polygon} supertype requires from
   * the shell's control points, or passes a {@link LinearRing} straight
   * through.
   */
  public CurvePolygon(LineString structuralShell, LineString[] structuralHoles,
      GeometryFactory factory) {
    super(deriveLinearShell(structuralShell, factory),
        deriveLinearHoles(structuralHoles, factory), factory);
    this.structuralShell = structuralShell;
    this.structuralHoles = structuralHoles == null
        ? new LineString[0] : structuralHoles.clone();
  }

  /**
   * Derives the legacy flat holes from the structural ones, on the same
   * control-point basis as {@link #deriveLinearShell}.
   */
  private static LinearRing[] deriveLinearHoles(LineString[] structuralHoles,
      GeometryFactory factory) {
    if (structuralHoles == null) return null;
    LinearRing[] flat = new LinearRing[structuralHoles.length];
    for (int i = 0; i < structuralHoles.length; i++) {
      flat[i] = deriveLinearShell(structuralHoles[i], factory);
    }
    return flat;
  }

  /**
   * Derives the legacy flat shell from the structural one.
   * <p>
   * Deliberately uses the shell's <em>control points</em> rather than
   * {@link Linearizable#toLinear(double) toLinear(0.0)}. Densifying here would
   * change the coordinates {@code getExteriorRing()} has always reported,
   * making Option A a behavioural break rather than a purely additive change.
   * It also breaks WKT round-tripping: densified arc coordinates are
   * irrational, the writer rounds them to decimal, and reading them back
   * yields doubles that differ in the low bits, so {@code equalsExact} at
   * tolerance 0 fails.
   * <p>
   * Choosing the tolerance for a densified legacy view is a separate open
   * question (see {@code SPEC_F_CP.md}); curve-aware callers that want the
   * true arc should use {@link #getExteriorCurve()} and densify themselves.
   */
  private static LinearRing deriveLinearShell(LineString structuralShell, GeometryFactory factory) {
    if (structuralShell == null) return null;
    if (structuralShell instanceof LinearRing) return (LinearRing) structuralShell;
    return factory.createLinearRing(structuralShell.getCoordinates());
  }

  /**
   * Option-A structural accessor: returns the structural shell the caller
   * supplied (may be a {@code CompoundCurve}, {@code CircularString},
   * {@link LinearRing}, or plain {@link LineString}). Returns {@code null}
   * for an empty CurvePolygon.
   */
  public LineString getExteriorCurve() {
    return structuralShell;
  }

  /**
   * V-CP (#1195): validity uses densified rings so arc self-intersections
   * that are invisible on the control polygon are still detected. Chainsaw
   * densify is maintainable here; analytical arc/arc self-intersection can
   * replace this later without changing the public contract.
   */
  @Override
  public boolean isValid() {
    if (isEmpty()) {
      return true;
    }
    Envelope env = getEnvelopeInternal();
    double extent = Math.max(env.getWidth(), env.getHeight());
    double tol = (extent > 0.0 ? extent : 1.0) * 1.0e-4;
    Geometry flat = toLinear(tol);
    return flat.isValid();
  }

  /**
   * Option-A structural accessor for interior rings, the counterpart to
   * {@link #getExteriorCurve()}. Returns the hole as supplied by the caller,
   * which may be a {@code CircularString}, {@code CompoundCurve},
   * {@link LinearRing}, or plain {@link LineString}.
   *
   * @param n the hole index, as used by {@link #getInteriorRingN(int)}
   */
  public LineString getInteriorCurveN(int n) {
    return structuralHoles[n];
  }

  @Override
  public String getGeometryType() {
    return "CurvePolygon";
  }

  @Override
  protected CurvePolygon copyInternal() {
    GeometryFactory f = getFactory();
    if (isEmpty()) return new CurvePolygon(f);
    int holeCount = getNumInteriorRing();
    boolean anyCurved = structuralShell != null && !(structuralShell instanceof LinearRing);
    for (int i = 0; i < structuralHoles.length && !anyCurved; i++) {
      anyCurved = !(structuralHoles[i] instanceof LinearRing);
    }
    if (anyCurved) {
      LineString shellCopy = structuralShell == null
          ? null : (LineString) structuralShell.copy();
      LineString[] holeCopies = new LineString[structuralHoles.length];
      for (int i = 0; i < structuralHoles.length; i++) {
        holeCopies[i] = (LineString) structuralHoles[i].copy();
      }
      return new CurvePolygon(shellCopy, holeCopies, f);
    }
    LinearRing[] holes = new LinearRing[holeCount];
    for (int i = 0; i < holeCount; i++) {
      holes[i] = (LinearRing) getInteriorRingN(i).copy();
    }
    LinearRing shell = (LinearRing) getExteriorRing().copy();
    return new CurvePolygon(shell, holes, f);
  }

  /**
   * The boundary as the polygon's curve rings: the shell alone when there are
   * no holes, otherwise a {@link MultiCurve} of shell and holes.
   * <p>
   * The inherited {@code Polygon.getBoundary()} builds from the flat
   * {@code getExteriorRing()} view, so the boundary of a circle came back as
   * the inscribed quadrilateral -- the wrong length, and not even lying on the
   * polygon's edge.
   * <p>
   * An all-linear CurvePolygon defers to the inherited implementation, so its
   * boundary type and value are unchanged.
   */
  @Override
  public Geometry getBoundary() {
    if (isEmpty() || !hasCurveRing()) return super.getBoundary();
    if (structuralHoles.length == 0) {
      return (Geometry) structuralShell.copy();
    }
    LineString[] rings = new LineString[1 + structuralHoles.length];
    rings[0] = (LineString) structuralShell.copy();
    for (int i = 0; i < structuralHoles.length; i++) {
      rings[i + 1] = (LineString) structuralHoles[i].copy();
    }
    return new MultiCurve(rings, getFactory());
  }

  /** True if any structural ring is a curve rather than a plain line. */
  private boolean hasCurveRing() {
    if (isCurve(structuralShell)) return true;
    for (int i = 0; i < structuralHoles.length; i++) {
      if (isCurve(structuralHoles[i])) return true;
    }
    return false;
  }

  private static boolean isCurve(LineString ring) {
    return ring instanceof CircularString || ring instanceof CompoundCurve;
  }

  /**
   * Applies the filter to structural rings when they are not the same
   * object as the flat {@link LinearRing} views.
   * <p>
   * Inherited {@link Polygon#apply(CoordinateFilter)} visits only the
   * flat rings. A {@code CIRCULARSTRING} shell often shares Coordinate
   * objects with that view, so a translate already reached it. A
   * {@code COMPOUNDCURVE} shell (ISO/IEC 13249-3) does not: the flat
   * ring is the concatenated sequence, which omits each later member's
   * start. Applying here lets {@link CompoundCurve#apply} move every
   * control point, including those starts. Do not also apply to the
   * flat rings — shared coordinates would translate twice.
   * <p>
   * Affine translate is the signed type-honest case. Shear /
   * non-uniform scale is not signed as keeping a circular arc.
   */
  @Override
  public void apply(CoordinateFilter filter) {
    if (!applyToStructuralRings()) {
      super.apply(filter);
      return;
    }
    applyStructural(filter);
    syncFlatRingsFromStructural();
  }

  /**
   * Same structural-first walk as {@link #apply(CoordinateFilter)}.
   */
  @Override
  public void apply(CoordinateSequenceFilter filter) {
    if (!applyToStructuralRings()) {
      super.apply(filter);
      return;
    }
    if (structuralShell != null) {
      structuralShell.apply(filter);
    }
    if (!filter.isDone()) {
      for (int i = 0; i < structuralHoles.length; i++) {
        if (structuralHoles[i] != null) {
          structuralHoles[i].apply(filter);
          if (filter.isDone()) {
            break;
          }
        }
      }
    }
    syncFlatRingsFromStructural();
    if (filter.isGeometryChanged()) {
      geometryChanged();
    }
  }

  private void applyStructural(CoordinateFilter filter) {
    if (structuralShell != null) {
      structuralShell.apply(filter);
    }
    for (int i = 0; i < structuralHoles.length; i++) {
      if (structuralHoles[i] != null) {
        structuralHoles[i].apply(filter);
      }
    }
  }

  /**
   * True when a structural ring is a distinct object from the legacy
   * flat view ({@code CircularString} / {@code CompoundCurve}).
   */
  private boolean applyToStructuralRings() {
    if (structuralShell != null && structuralShell != getExteriorRing()) {
      return true;
    }
    for (int i = 0; i < structuralHoles.length; i++) {
      if (structuralHoles[i] != null
          && structuralHoles[i] != getInteriorRingN(i)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Copies structural control points onto the flat rings when those
   * sequences are not the same objects. Default-factory CircularString
   * shells already share Coordinates, so this is a no-op there.
   */
  private void syncFlatRingsFromStructural() {
    if (structuralShell != null && structuralShell != getExteriorRing()) {
      syncSequence(structuralShell.getCoordinateSequence(),
          getExteriorRing().getCoordinateSequence());
    }
    for (int i = 0; i < structuralHoles.length; i++) {
      if (structuralHoles[i] != null
          && structuralHoles[i] != getInteriorRingN(i)) {
        syncSequence(structuralHoles[i].getCoordinateSequence(),
            getInteriorRingN(i).getCoordinateSequence());
      }
    }
  }

  private static void syncSequence(CoordinateSequence src, CoordinateSequence dest) {
    int n = Math.min(src.size(), dest.size());
    for (int i = 0; i < n; i++) {
      CoordinateSequences.copyCoord(src, i, dest, i);
    }
  }

  /**
   * Invalidates the structural rings' cached envelopes along with this polygon's.
   * <p>
   * Without this a transformed CurvePolygon reported the envelope it used to have.
   * {@code geometryChanged()} propagates through
   * {@code apply(GeometryComponentFilter)}, which for a {@link Polygon} visits the
   * shell and holes -- the flat {@link LinearRing} views -- and never the
   * structural rings. But {@link #computeEnvelopeInternal()} reads the structural
   * shell, so the one cache that mattered was the one the reset missed. Combined
   * with {@code Geometry.copy()} copying cached envelopes onto the copy, a
   * translate of a circle whose envelope had been read reported
   * {@code Env[-5 : 5, -5 : 5]} for a ring sitting at 95..105.
   * <p>
   * Overriding here rather than {@code apply(GeometryComponentFilter)} is
   * deliberate: adding the structural rings to component enumeration would change
   * what every {@code GeometryComponentFilter} sees, including ones that count or
   * collect. Cache invalidation is the only thing that needs to reach them.
   * <p>
   * Resetting a ring that is also the flat shell is harmless -- invalidation is
   * idempotent -- so no identity check is needed.
   */
  @Override
  protected void geometryChangedAction() {
    super.geometryChangedAction();
    if (structuralShell != null) structuralShell.geometryChanged();
    for (int i = 0; i < structuralHoles.length; i++) {
      if (structuralHoles[i] != null) structuralHoles[i].geometryChanged();
    }
  }

  /**
   * The envelope of the structural shell, which bounds the whole polygon.
   * <p>
   * The inherited {@code Polygon.computeEnvelopeInternal()} uses the flat
   * {@code getExteriorRing()} view, so an arc ring bulging past an axis extreme
   * that is not a control point would be clipped.
   */
  @Override
  protected Envelope computeEnvelopeInternal() {
    if (structuralShell == null) return new Envelope();
    return new Envelope(structuralShell.getEnvelopeInternal());
  }

  /**
   * The area enclosed by the structural rings, so arc rings contribute the
   * area they actually sweep rather than the polygon through their control
   * points.
   * <p>
   * The inherited {@code Polygon.getArea()} shoelaces the flat
   * {@code getExteriorRing()} view, which for a circle expressed as two
   * semicircular arcs is the inscribed quadrilateral -- 8 instead of
   * {@code 4*pi} for radius 2.
   * <p>
   * Follows {@code Polygon} semantics: the magnitude of the shell's area less
   * the magnitude of each hole's, so the result is independent of ring
   * orientation.
   */
  @Override
  public double getArea() {
    if (isEmpty()) return 0.0;
    double area = Math.abs(signedRingArea(structuralShell));
    for (int i = 0; i < structuralHoles.length; i++) {
      area -= Math.abs(signedRingArea(structuralHoles[i]));
    }
    return area;
  }

  /**
   * The length of the structural rings, so an arc ring contributes its arc
   * length rather than the chords through its control points.
   * <p>
   * The inherited {@code Polygon.getLength()} sums the flat
   * {@code getExteriorRing()} view, which for a circle expressed as two
   * semicircular arcs is the inscribed square -- {@code 8*sqrt(2)} instead of
   * {@code 4*pi} for radius 2. That radius is where the defect hides, since
   * {@code 2*pi*r} and {@code pi*r^2} are both {@code 4*pi} there and the wrong
   * length sits next to a correct-looking area.
   * <p>
   * Each ring is asked for its own length, so the arc integration stays in
   * {@code CircularString.getLength()} and {@code CompoundCurve.getLength()}
   * where it already lived; this method only had the summation missing.
   * <p>
   * Equivalent to {@code getBoundary().getLength()} by the OGC definition of a
   * surface's length, and asserted to be so, but computed without building the
   * boundary geometry. An all-linear CurvePolygon defers to the inherited
   * implementation so its value is bit-for-bit unchanged.
   */
  @Override
  public double getLength() {
    if (isEmpty() || !hasCurveRing()) return super.getLength();
    double length = structuralShell.getLength();
    for (int i = 0; i < structuralHoles.length; i++) {
      length += structuralHoles[i].getLength();
    }
    return length;
  }

  /**
   * Signed area enclosed by one ring, via the contour integral
   * {@code 1/2 * integral(x dy - y dx)}. Arc pieces use the closed-form arc
   * term; straight pieces use the shoelace term. A CompoundCurve ring is the
   * sum over its members, which together close the loop.
   */
  private static double signedRingArea(LineString ring) {
    if (ring == null) return 0.0;
    if (ring instanceof CompoundCurve) {
      CompoundCurve cc = (CompoundCurve) ring;
      double area = 0.0;
      for (int i = 0; i < cc.getNumMembers(); i++) {
        area += signedRingArea(cc.getMemberN(i));
      }
      return area;
    }
    CoordinateSequence seq = ring.getCoordinateSequence();
    if (ring instanceof CircularString && seq.size() >= 3) {
      double area = 0.0;
      for (int i = 0; i + 2 < seq.size(); i += 2) {
        area += CircularArcDensifier.arcAreaContribution(
            seq.getCoordinate(i), seq.getCoordinate(i + 1), seq.getCoordinate(i + 2));
      }
      Coordinate closeMid = CircularArcDensifier.threePointCircleCloseMid(seq);
      if (closeMid != null) {
        area += CircularArcDensifier.arcAreaContribution(
            seq.getCoordinate(seq.size() - 2), closeMid, seq.getCoordinate(0));
      }
      return area;
    }
    return shoelace(seq);
  }

  /**
   * Shoelace contour term over consecutive vertices. Over a closed ring this is
   * the signed area; over an open piece it is that piece's contribution to the
   * enclosing contour.
   */
  private static double shoelace(CoordinateSequence seq) {
    double sum = 0.0;
    for (int i = 0; i + 1 < seq.size(); i++) {
      Coordinate p = seq.getCoordinate(i);
      Coordinate q = seq.getCoordinate(i + 1);
      sum += p.x * q.y - q.x * p.y;
    }
    return 0.5 * sum;
  }

  /**
   * Linearises every structural ring at the given tolerance, so an arc ring
   * becomes a densified polyline rather than the chord through its control
   * points. Delegates per ring to {@link Linearizable#toLinear(double)},
   * which is where the arc geometry and the tolerance semantics live.
   *
   * @param tolerance maximum chord error; {@code 0} selects the densifier's
   *                  default (see {@code CircularArcDensifier})
   * @return a plain {@link Polygon}; curve identity is deliberately dropped
   */
  @Override
  public Geometry toLinear(double tolerance) {
    GeometryFactory f = getFactory();
    if (isEmpty()) return f.createPolygon();
    LinearRing shell = linearise(structuralShell, tolerance, f);
    LinearRing[] flatHoles = new LinearRing[structuralHoles.length];
    for (int i = 0; i < structuralHoles.length; i++) {
      flatHoles[i] = linearise(structuralHoles[i], tolerance, f);
    }
    return f.createPolygon(shell, flatHoles);
  }

  /** Linearises one ring, leaving already-linear rings untouched. */
  private static LinearRing linearise(LineString ring, double tolerance,
      GeometryFactory factory) {
    if (ring instanceof LinearRing) return (LinearRing) ring;
    if (ring instanceof Linearizable) {
      LineString flat = (LineString) ((Linearizable) ring).toLinear(tolerance);
      return factory.createLinearRing(flat.getCoordinates());
    }
    return factory.createLinearRing(ring.getCoordinates());
  }


  // -- Arc-aware spatial operations (CRV-OPS) ------------------------------
  // The jts-core implementations walk getCoordinates(), which for a curve is
  // only the control points. Route them through a densified copy instead; see
  // CurveOps for the tolerance rationale and its limits.

  /**
   * Core {@code WKTWriter} refuses to flatten curved rings to untagged polygons.
   */
  @Override
  public String toText() {
    return new CurveWKTWriter().write(this);
  }

  @Override
  public Geometry convexHull() {
    return CurveOps.convexHull(this);
  }

  @Override
  public double distance(Geometry g) {
    return CurveOps.distance(this, g);
  }

  @Override
  public boolean isWithinDistance(Geometry g, double distance) {
    return CurveOps.isWithinDistance(this, g, distance);
  }

  @Override
  public Geometry buffer(double distance) {
    return CurveOps.buffer(this, distance);
  }

  @Override
  public Geometry buffer(double distance, int quadrantSegments) {
    return CurveOps.buffer(this, distance, quadrantSegments);
  }

  @Override
  public Geometry buffer(double distance, int quadrantSegments, int endCapStyle) {
    return CurveOps.buffer(this, distance, quadrantSegments, endCapStyle);
  }

  // -- Overlay (OVL-OPS) ---------------------------------------------------
  // Densified on both sides: overlay must node the linework, and the inherited
  // implementations node the chords through the control points. See CurveOps for
  // why no tolerance returns a curve type here, and for the OverlayNG
  // TopologyException this also resolves.

  @Override
  public Geometry intersection(Geometry other) {
    return CurveOps.intersection(this, other);
  }

  @Override
  public Geometry union(Geometry other) {
    return CurveOps.union(this, other);
  }

  @Override
  public Geometry difference(Geometry other) {
    return CurveOps.difference(this, other);
  }

  @Override
  public Geometry symDifference(Geometry other) {
    return CurveOps.symDifference(this, other);
  }

  // -- Spatial predicates (CRV-REL) -----------------------------------------
  // Each predicate is overridden individually: in this core they dispatch to
  // GeometryRelate statics rather than through this.relate(g), so no single
  // override carries the family -- see CurveOps. disjoint is inherited as
  // !intersects(g), so it follows the intersects override.

  @Override
  public IntersectionMatrix relate(Geometry g) {
    return CurveOps.relate(this, g);
  }

  @Override
  public boolean relate(Geometry g, String intersectionPattern) {
    return CurveOps.relate(this, g, intersectionPattern);
  }

  @Override
  public boolean intersects(Geometry g) {
    return CurveOps.intersects(this, g);
  }

  @Override
  public boolean touches(Geometry g) {
    return CurveOps.touches(this, g);
  }

  @Override
  public boolean crosses(Geometry g) {
    return CurveOps.crosses(this, g);
  }

  @Override
  public boolean within(Geometry g) {
    return CurveOps.within(this, g);
  }

  @Override
  public boolean contains(Geometry g) {
    return CurveOps.contains(this, g);
  }

  @Override
  public boolean overlaps(Geometry g) {
    return CurveOps.overlaps(this, g);
  }

  @Override
  public boolean covers(Geometry g) {
    return CurveOps.covers(this, g);
  }

  @Override
  public boolean coveredBy(Geometry g) {
    return CurveOps.coveredBy(this, g);
  }

  @Override
  public boolean equalsTopo(Geometry g) {
    return CurveOps.equalsTopo(this, g);
  }
}

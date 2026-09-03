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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

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
import org.locationtech.jts.io.curve.CurveWKTWriter;

/**
 * A connected sequence of {@link LineString} and {@link CircularString}
 * segments per SQL/MM ISO/IEC 13249-3 §4.2.13 / §7.10.1 (WKB type 9).
 *
 * <p>Members are preserved as-is so consumers (renderer, densifier,
 * WKT writer) can walk segment-by-segment instead of treating the
 * geometry as a flat polyline. The parent {@link LineString}'s
 * coordinate sequence is the concatenation of member coordinates
 * with shared junction points deduplicated, so existing JTS algorithms
 * that walk {@code getCoordinates()} still see a continuous polyline.
 */
public class CompoundCurve extends LineString implements Linearizable {
  private static final long serialVersionUID = 1L;

  private final LineString[] members;

  /**
   * Constructs a CompoundCurve from an explicit member list.
   * ISO/IEC 13249-3 requires each member to be a LineString or
   * CircularString and the end of each component (except the last)
   * to coincide with the start of the next. Constructors stay
   * permissive (JTS pattern); {@link #isValid()} and the WKT reader
   * enforce those rules.
   */
  public CompoundCurve(LineString[] members, GeometryFactory factory) {
    super(concatMembers(members, factory), factory);
    this.members = members == null ? new LineString[0] : members.clone();
  }

  /**
   * Legacy flat-coordinate constructor. Wraps the input as a single
   * LineString member. Kept so existing callers (and the WKT reader's
   * fallback path for the writer's flat round-trip form) keep working.
   */
  public CompoundCurve(CoordinateSequence points, GeometryFactory factory) {
    super(points, factory);
    if (points == null || points.size() == 0) {
      this.members = new LineString[0];
    } else {
      this.members = new LineString[] { factory.createLineString(points.copy()) };
    }
  }

  public int getNumMembers() {
    return members.length;
  }

  public LineString getMemberN(int i) {
    return members[i];
  }

  public LineString[] getMembers() {
    return members.clone();
  }

  /**
   * ISO/IEC 13249-3 SimpleCurve member: {@link LineString} (including
   * {@link org.locationtech.jts.geom.LinearRing}) or
   * {@link CircularString}. Nested {@code CompoundCurve} and
   * non-SQL/MM LineString subclasses (clothoid, Bézier, …) are not
   * SimpleCurves.
   */
  public static boolean isSqlMmSimpleCurve(LineString member) {
    if (member == null) {
      return false;
    }
    if (member instanceof CircularString) {
      return true;
    }
    if (member instanceof CompoundCurve) {
      return false;
    }
    return member.getClass() == LineString.class
        || member instanceof org.locationtech.jts.geom.LinearRing;
  }

  /**
   * True when consecutive SQL/MM SimpleCurve members share an endpoint
   * in XY (ISO/IEC 13249-3 CompoundCurve continuity). Empty members are
   * skipped. A single-member or empty chain is contiguous.
   * <p>
   * Non-SQL/MM members (clothoid extension) are not a SimpleCurve pair;
   * their junction is the grammars-v4 drift warning, not this check.
   */
  public static boolean areMembersContiguous(LineString[] members) {
    if (members == null || members.length <= 1) {
      return true;
    }
    LineString prev = null;
    for (int i = 0; i < members.length; i++) {
      LineString m = members[i];
      if (m == null || m.isEmpty()) {
        continue;
      }
      if (prev != null && isSqlMmSimpleCurve(prev) && isSqlMmSimpleCurve(m)) {
        Coordinate end = prev.getCoordinateN(prev.getNumPoints() - 1);
        Coordinate start = m.getCoordinateN(0);
        if (end == null || start == null || !end.equals2D(start)) {
          return false;
        }
      }
      prev = m;
    }
    return true;
  }

  /**
   * ISO/IEC 13249-3: empty is valid; otherwise every member is a
   * SimpleCurve, each CircularString has a legal control count, and
   * adjacent non-empty members are contiguous.
   */
  @Override
  public boolean isValid() {
    if (isEmpty()) {
      return true;
    }
    for (int i = 0; i < members.length; i++) {
      if (!isSqlMmSimpleCurve(members[i])) {
        return false;
      }
      if (!members[i].isValid()) {
        return false;
      }
    }
    return areMembersContiguous(members);
  }

  /**
   * Returns a new CompoundCurve with member {@code index} replaced by
   * {@code replacement}. Other members are reused (immutable). Used by
   * editors that mutate a single segment without rebuilding the whole
   * compound curve from scratch.
   */
  public CompoundCurve withMemberReplaced(int index, LineString replacement) {
    if (index < 0 || index >= members.length) {
      throw new IndexOutOfBoundsException("index=" + index + " size=" + members.length);
    }
    LineString[] copy = members.clone();
    copy[index] = replacement;
    GeometryFactory f = getFactory();
    if (f instanceof CurveGeometryFactory) {
      return ((CurveGeometryFactory) f).createCompoundCurve(copy);
    }
    return new CompoundCurve(copy, f);
  }

  @Override
  public String getGeometryType() {
    return "CompoundCurve";
  }

  @Override
  protected CompoundCurve copyInternal() {
    if (members.length == 0) {
      return new CompoundCurve(new LineString[0], getFactory());
    }
    LineString[] copies = new LineString[members.length];
    for (int i = 0; i < members.length; i++) {
      copies[i] = (LineString) members[i].copy();
    }
    return new CompoundCurve(copies, getFactory());
  }

  /**
   * Applies the filter to every member, including each circular
   * member's start.
   * <p>
   * The inherited {@link LineString#apply(CoordinateFilter)} walks only
   * the concatenated sequence. {@link #concatMembers} drops each later
   * member's start (the junction already stored as the previous member's
   * end). Those starts are distinct {@link Coordinate} objects, so a
   * translate moved mid and end of a {@code CIRCULARSTRING} (ISO/IEC
   * 13249-3) and left the start behind — the arc warped. Member-first
   * apply keeps the three-point arc and the type.
   * <p>
   * Affine <em>translate</em> is the signed type-honest case. This does
   * not claim that shear or non-uniform scale still describes a circular
   * arc. No Bézier I/O type is introduced.
   */
  @Override
  public void apply(CoordinateFilter filter) {
    if (members.length == 0) {
      super.apply(filter);
      return;
    }
    for (int i = 0; i < members.length; i++) {
      members[i].apply(filter);
    }
    syncConcatenatedSequence();
  }

  /**
   * Same member-first walk as {@link #apply(CoordinateFilter)}. The
   * concatenated sequence is then copied from the members so
   * {@code getCoordinates()} stays a continuous polyline. Do not apply
   * the filter to the parent sequence as well — those points are often
   * the same objects as the members', and a second pass would translate
   * twice.
   */
  @Override
  public void apply(CoordinateSequenceFilter filter) {
    if (members.length == 0) {
      super.apply(filter);
      return;
    }
    for (int i = 0; i < members.length; i++) {
      members[i].apply(filter);
      if (filter.isDone()) {
        break;
      }
    }
    syncConcatenatedSequence();
    if (filter.isGeometryChanged()) {
      geometryChanged();
    }
  }

  /**
   * Copies member coordinates into the concatenated parent sequence,
   * skipping each later member's start the same way
   * {@link #concatMembers} does. In-place so a {@code CurvePolygon}
   * flat ring that wraps this sequence stays aligned.
   */
  private void syncConcatenatedSequence() {
    CoordinateSequence dest = points;
    int k = 0;
    for (int i = 0; i < members.length; i++) {
      CoordinateSequence src = members[i].getCoordinateSequence();
      int from = (i == 0) ? 0 : 1;
      for (int j = from; j < src.size() && k < dest.size(); j++) {
        CoordinateSequences.copyCoord(src, j, dest, k);
        k++;
      }
    }
  }

  /**
   * §3.7 — type identity is required. Without this override a CompoundCurve
   * would compare equal to a plain LineString that happens to have the
   * same flat coord sequence, hiding member-structure differences.
   */
  @Override
  protected boolean isEquivalentClass(Geometry other) {
    return other instanceof CompoundCurve;
  }

  /**
   * §3.7 — compare member-by-member, delegating to each member's own
   * {@code equalsExact}. Two CompoundCurves with the same flat coord
   * sequence but different member structure (e.g. one CircularString
   * member vs three LineString chunks at the same coords) are different
   * geometries.
   */
  @Override
  public boolean equalsExact(Geometry other, double tolerance) {
    if (this == other) return true;
    if (!isEquivalentClass(other)) return false;
    CompoundCurve o = (CompoundCurve) other;
    if (members.length != o.members.length) return false;
    for (int i = 0; i < members.length; i++) {
      if (!members[i].equalsExact(o.members[i], tolerance)) return false;
    }
    return true;
  }

  /**
   * The union of the members' envelopes, so arc members contribute the arc's
   * extent rather than their control points'.
   */
  @Override
  protected Envelope computeEnvelopeInternal() {
    Envelope env = new Envelope();
    for (int i = 0; i < members.length; i++) {
      env.expandToInclude(members[i].getEnvelopeInternal());
    }
    return env;
  }

  /**
   * The sum of the members' lengths, so arc members contribute their true arc
   * length rather than the chords through their control points.
   * <p>
   * The inherited {@code LineString.getLength()} walks the concatenated
   * sequence as straight segments and understates any arc member.
   */
  @Override
  public double getLength() {
    double total = 0.0;
    for (int i = 0; i < members.length; i++) {
      total += members[i].getLength();
    }
    return total;
  }

  /**
   * Reverses the chain, staying a CompoundCurve.
   * <p>
   * Without this the inherited {@code LineString.reverseInternal()} rebuilds a
   * plain {@link LineString} from the concatenated sequence and the segment
   * structure is lost. Reversing a chain means reversing the member order
   * <em>and</em> each member, so the members still join end-to-start.
   */
  @Override
  protected CompoundCurve reverseInternal() {
    LineString[] reversed = new LineString[members.length];
    for (int i = 0; i < members.length; i++) {
      reversed[i] = members[members.length - 1 - i].reverse();
    }
    return new CompoundCurve(reversed, getFactory());
  }

  @Override
  public Geometry toLinear(double tolerance) {
    GeometryFactory f = getFactory();
    if (members.length == 0) return f.createLineString();
    List<Coordinate> all = new ArrayList<Coordinate>();
    for (int i = 0; i < members.length; i++) {
      LineString m = members[i];
      Geometry linMember;
      if (m instanceof Linearizable) {
        // Pin the member's own control points as must-include anchors so
        // the densified chord polyline passes exactly through every input
        // keypoint (start, mid and end of each 3-point arc, plus the
        // shared transition points in a multi-arc CircularString). Without
        // this, the densifier may sample around the arc and emit a chord
        // vertex that drifts a few epsilons off the input control point.
        List<Coordinate> anchors = Arrays.asList(m.getCoordinates());
        linMember = ((Linearizable) m).toLinear(tolerance, anchors);
      } else {
        // Plain LineString member: include every coordinate as-is, in
        // order — same walk we use for MultiLineString-style member
        // iteration so no straight segment ever gets orphaned.
        linMember = m;
      }
      Coordinate[] cc = linMember.getCoordinates();
      int start = (i == 0) ? 0 : 1;
      for (int j = start; j < cc.length; j++) {
        all.add(cc[j]);
      }
    }
    return f.createLineString(all.toArray(new Coordinate[0]));
  }

  private static CoordinateSequence concatMembers(LineString[] members, GeometryFactory factory) {
    if (members == null || members.length == 0) {
      return factory.getCoordinateSequenceFactory().create(new Coordinate[0]);
    }
    List<Coordinate> all = new ArrayList<Coordinate>();
    for (int i = 0; i < members.length; i++) {
      Coordinate[] cc = members[i].getCoordinates();
      int start = (i == 0) ? 0 : 1;
      for (int j = start; j < cc.length; j++) {
        all.add(cc[j]);
      }
    }
    return factory.getCoordinateSequenceFactory().create(all.toArray(new Coordinate[0]));
  }

  // -- Arc-aware spatial operations (CRV-OPS) ------------------------------
  // The jts-core implementations walk getCoordinates(), which for a curve is
  // only the control points. Route them through a densified copy instead; see
  // CurveOps for the tolerance rationale and its limits.

  /**
   * Core {@code WKTWriter} refuses to flatten arc members to untagged lines.
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

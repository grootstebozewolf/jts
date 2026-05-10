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
package org.locationtech.jts.geom.curved;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.CoordinateSequence;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;

/**
 * A connected sequence of {@link LineString} and {@link CircularString}
 * segments per OGC SFA / ISO 19125-2.
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
   * Constructs a CompoundCurve from an explicit member list. Each
   * member's last coordinate must equal the next member's first
   * coordinate; this is not enforced here (the WKT reader and
   * geometryJoin function are the producers and already maintain it).
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

  @Override
  public String getGeometryType() {
    return "CompoundCurve";
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
   * geometries. Length-mismatch or any member-level inequality fails fast.
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
   * Reverses the chain by reversing each member <em>and</em> walking the
   * resulting member array backward. Each member's own {@code reverse()}
   * preserves its subtype (CircularString stays a CircularString;
   * ClothoidSegment flips its κ signs and start state per §3.8 of the
   * proposal; plain LineStrings reverse normally), so the resulting
   * CompoundCurve has the same kinds of members in the opposite order.
   */
  @Override
  protected CompoundCurve reverseInternal() {
    if (members.length == 0) {
      return new CompoundCurve(new LineString[0], getFactory());
    }
    LineString[] reversed = new LineString[members.length];
    for (int i = 0; i < members.length; i++) {
      reversed[members.length - 1 - i] = (LineString) members[i].reverse();
    }
    return new CompoundCurve(reversed, getFactory());
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
}

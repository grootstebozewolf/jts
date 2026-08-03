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
}

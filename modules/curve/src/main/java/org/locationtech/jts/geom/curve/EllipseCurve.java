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
import java.util.List;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.CoordinateSequence;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;

/**
 * Preview Ellipse: an elliptic arc (or full ellipse) in the plane.
 * HOLD type 20 — not SIGNED I/O.
 * Parameters: centre, semi-major {@code a}, semi-minor {@code b},
 * rotation (radians), start and end angles (radians, relative to the
 * rotated major axis). A full ellipse has {@code endAngle = startAngle
 * + 2π} (stored as the pair; {@link #isFullEllipse()} detects a 2π sweep).
 * <p>
 * Parent {@link LineString} sequence holds the centre only for
 * envelope/identity anchors; the analytic parameters are authoritative.
 */
public class EllipseCurve extends LineString implements Linearizable {
  private static final long serialVersionUID = 1L;

  private final double centreX;
  private final double centreY;
  private final double centreZ;
  private final double semiMajor;
  private final double semiMinor;
  private final double rotation;
  private final double startAngle;
  private final double endAngle;

  public EllipseCurve(double centreX, double centreY, double centreZ,
      double semiMajor, double semiMinor, double rotation,
      double startAngle, double endAngle, GeometryFactory factory) {
    super(centreSeq(centreX, centreY, centreZ, factory), factory);
    if (!(semiMajor > 0.0) || !(semiMinor > 0.0)) {
      throw new IllegalArgumentException(
          "ELLIPSE semi-axes must be finite > 0");
    }
    this.centreX = centreX;
    this.centreY = centreY;
    this.centreZ = centreZ;
    this.semiMajor = semiMajor;
    this.semiMinor = semiMinor;
    this.rotation = rotation;
    this.startAngle = startAngle;
    this.endAngle = endAngle;
  }

  private static CoordinateSequence centreSeq(double x, double y, double z,
      GeometryFactory factory) {
    Coordinate c0 = new Coordinate(x, y);
    Coordinate c1 = new Coordinate(x, y);
    if (!Double.isNaN(z)) {
      c0.setZ(z);
      c1.setZ(z);
    }
    // LineString requires 0 or >= 2 points; centre is duplicated as anchor.
    return factory.getCoordinateSequenceFactory()
        .create(new Coordinate[] { c0, c1 });
  }

  public double getCentreX() { return centreX; }
  public double getCentreY() { return centreY; }
  public double getCentreZ() { return centreZ; }
  public double getSemiMajor() { return semiMajor; }
  public double getSemiMinor() { return semiMinor; }
  public double getRotation() { return rotation; }
  public double getStartAngle() { return startAngle; }
  public double getEndAngle() { return endAngle; }

  public boolean isFullEllipse() {
    double sweep = endAngle - startAngle;
    return Math.abs(Math.abs(sweep) - 2 * Math.PI) < 1.0e-12;
  }

  @Override
  public String getGeometryType() {
    return "EllipseCurve";
  }

  @Override
  protected EllipseCurve copyInternal() {
    return new EllipseCurve(centreX, centreY, centreZ, semiMajor, semiMinor,
        rotation, startAngle, endAngle, getFactory());
  }

  @Override
  protected boolean isEquivalentClass(Geometry other) {
    return other instanceof EllipseCurve;
  }

  @Override
  public Envelope getEnvelopeInternal() {
    Envelope env = new Envelope();
    // Rotated AABB of the full ellipse, then sample the arc for tightness.
    double c = Math.cos(rotation);
    double s = Math.sin(rotation);
    double dx = Math.hypot(semiMajor * c, semiMinor * s);
    double dy = Math.hypot(semiMajor * s, semiMinor * c);
    env.expandToInclude(centreX - dx, centreY - dy);
    env.expandToInclude(centreX + dx, centreY + dy);
    return env;
  }

  @Override
  public Geometry toLinear(double tolerance) {
    int n = samples(tolerance);
    List<Coordinate> pts = new ArrayList<Coordinate>(n + 1);
    double sweep = endAngle - startAngle;
    for (int i = 0; i <= n; i++) {
      double t = startAngle + sweep * ((double) i / n);
      pts.add(pointAt(t));
    }
    return getFactory().createLineString(pts.toArray(new Coordinate[0]));
  }

  Coordinate pointAt(double angle) {
    double cosR = Math.cos(rotation);
    double sinR = Math.sin(rotation);
    double x0 = semiMajor * Math.cos(angle);
    double y0 = semiMinor * Math.sin(angle);
    double x = centreX + x0 * cosR - y0 * sinR;
    double y = centreY + x0 * sinR + y0 * cosR;
    Coordinate c = new Coordinate(x, y);
    if (!Double.isNaN(centreZ)) {
      c.setZ(centreZ);
    }
    return c;
  }

  private int samples(double tolerance) {
    double peri = Math.PI * (3 * (semiMajor + semiMinor)
        - Math.sqrt((3 * semiMajor + semiMinor) * (semiMajor + 3 * semiMinor)));
    double frac = Math.abs(endAngle - startAngle) / (2 * Math.PI);
    double len = peri * Math.min(1.0, frac);
    if (!(tolerance > 0.0)) {
      return 32;
    }
    int n = (int) Math.ceil(len / Math.max(tolerance, 1.0e-9));
    if (n < 8) {
      return 8;
    }
    if (n > 256) {
      return 256;
    }
    return n;
  }
}

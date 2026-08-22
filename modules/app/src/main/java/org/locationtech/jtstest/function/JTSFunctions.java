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

package org.locationtech.jtstest.function;

import java.util.ArrayList;
import java.util.List;

import org.locationtech.jts.JTSVersion;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.curve.CircularString;
import org.locationtech.jts.geom.curve.ClothoidSegment;
import org.locationtech.jts.geom.curve.CompoundCurve;
import org.locationtech.jts.geom.curve.CurveGeometryFactory;
import org.locationtech.jts.geom.curve.CurvePolygon;
import org.locationtech.jts.geom.curve.Linearizable;
import org.locationtech.jts.operation.buffer.BufferOp;
import org.locationtech.jts.operation.buffer.BufferParameters;
import org.locationtech.jtstest.geomfunction.Metadata;

public class JTSFunctions 
{
  public static String jtsVersion(Geometry g)
  {
    return JTSVersion.CURRENT_VERSION.toString();
  }
  
  private static final double HEIGHT = 70;
  private static final double WIDTH = 150; //125;
  private static final double J_WIDTH = 30;
  private static final double J_RADIUS = J_WIDTH - 5;
  
  private static final double S_RADIUS = HEIGHT / 4;
  
  private static final double T_WIDTH = WIDTH - 2 * S_RADIUS - J_WIDTH;

  
  /**
   * J + T + S as real curve geometry: J and S are {@link CompoundCurve}s
   * whose bowls are {@link CircularString} members, T stays straight.
   * Assembled as a {@link org.locationtech.jts.geom.curve.MultiCurve}
   * so TestBuilder can paint each member (it already walks collections
   * and routes arcs through {@code CurveShapeWriter}).
   * <p>
   * Do not {@code union} the letters — overlay linearises. Do not build
   * the compounds with {@code createCompoundCurve(CoordinateSequence)}
   * — that ctor wraps a polyline member.
   */
  public static Geometry logoLines(Geometry g)
  {
    CurveGeometryFactory gf = curveFactory(g);
    LineString[] t = create_T(gf);
    return gf.createMultiCurve(new LineString[] {
        create_J(gf),
        t[0],
        t[1],
        create_S(gf)
    });
  }
  
  /**
   * Buffers the logo with {@link BufferOp}: CAP_SQUARE + JOIN_MITRE at
   * {@link BufferParameters#DEFAULT_MITRE_LIMIT}. Densify is
   * {@code toLinear(0.0)} (CircularArcDensifier 1% of radius) — named
   * CHORD-PATH / NAMED-APPROX, not a laser. Not clothoid.
   */
  public static Geometry logoBuffer(Geometry g, double distance)
  {
    Geometry lines = logoLines(g);
    // NAMED-APPROX / CHORD-PATH: 0.0 is CircularArcDensifier 1% of
    // radius, not "no densify".
    if (lines instanceof Linearizable) {
      lines = ((Linearizable) lines).toLinear(0.0);
    }
    BufferParameters bufParams = new BufferParameters();
    bufParams.setEndCapStyle(BufferParameters.CAP_SQUARE);
    bufParams.setJoinStyle(BufferParameters.JOIN_MITRE);
    bufParams.setMitreLimit(BufferParameters.DEFAULT_MITRE_LIMIT);
    return BufferOp.bufferOp(lines, distance, bufParams);
  }

  /** Offset from the {@link #logoLines} envelope to the inner halo edge. */
  static final double CLOTHOID_HALO_DEFAULT_DISTANCE = 12.0;

  /** Width of the polygonal halo band outside the inner edge. */
  static final double CLOTHOID_HALO_DEFAULT_BAND = 5.0;

  /**
   * Logo as curves plus a clothoid halo.
   * <p>
   * {@link #logoLines} stays a MultiCurve of CircularString / CompoundCurve
   * (ISO/IEC 13249-3). The halo is a G² clothoid-fillet frame around the
   * wordmark envelope, returned as a {@link CurvePolygon} of CompoundCurve
   * rings (LineString + ClothoidSegment). Not a densified LINESTRING.
   */
  @Metadata(description="logo as curves plus a clothoid halo.", curveAwareness="native")
  public static Geometry logoClothoid(Geometry g)
  {
    return clothoidHalo(g, CLOTHOID_HALO_DEFAULT_DISTANCE);
  }

  /**
   * Same mark as {@link #logoClothoid(Geometry)} at the default offset.
   */
  @Metadata(description="logo as curves plus a clothoid halo.")
  public static Geometry clothoidHalo(Geometry g)
  {
    return clothoidHalo(g, CLOTHOID_HALO_DEFAULT_DISTANCE);
  }

  /**
   * Clothoid-fillet halo around {@link #logoLines} at {@code distance}
   * from the wordmark envelope. Distance {@code <= 0} uses the default.
   * Result is a {@link CurvePolygon} of clothoid-fillet CompoundCurves.
   */
  @Metadata(description="logo as curves plus a clothoid halo.")
  public static Geometry clothoidHalo(Geometry g,
      @Metadata(title="Distance") double distance)
  {
    if (Double.isNaN(distance) || distance <= 0.0) {
      distance = CLOTHOID_HALO_DEFAULT_DISTANCE;
    }
    CurveGeometryFactory gf = curveFactory(g);
    // Envelope only — do not toLinear / flatten logoLines.
    Envelope logo = logoLines(g).getEnvelopeInternal();
    return namedClothoidHalo(gf, logo, distance, CLOTHOID_HALO_DEFAULT_BAND);
  }

  /**
   * Builds the clothoid-fillet frame as CompoundCurves (straights +
   * CLOTHOID corners). Returns a CurvePolygon band when both rings
   * construct; otherwise the outer CompoundCurve. Does not toLinear.
   */
  static Geometry namedClothoidHalo(CurveGeometryFactory gf, Envelope logo,
      double distance, double band)
  {
    if (band <= 0.0) {
      band = CLOTHOID_HALO_DEFAULT_BAND;
    }
    CompoundCurve outer = closedFillet(gf, expand(logo, distance + band));
    CompoundCurve inner = closedFillet(gf, expand(logo, distance));
    try {
      CurvePolygon bandPoly = gf.createCurvePolygon(outer,
          new LineString[] { inner.reverse() });
      if (bandPoly != null && !bandPoly.isEmpty()) {
        return bandPoly;
      }
    }
    catch (IllegalArgumentException ex) {
      // Control-point LinearRing rejected; keep the clothoid frame.
    }
    return outer;
  }

  private static Envelope expand(Envelope env, double d)
  {
    return new Envelope(env.getMinX() - d, env.getMaxX() + d,
        env.getMinY() - d, env.getMaxY() + d);
  }

  private static CompoundCurve closedFillet(CurveGeometryFactory gf, Envelope env)
  {
    return closeCompoundRing(gf, filletRectMembers(gf, env));
  }

  /**
   * {@link CurvePolygon} derives a {@link LinearRing} from control
   * points, which requires first.equals2D(last). Clothoid integration
   * leaves a sub-{@code 1e-8} gap that {@link #addStraight} would drop.
   */
  private static CompoundCurve closeCompoundRing(CurveGeometryFactory gf,
      LineString[] members)
  {
    if (members == null || members.length == 0) {
      return gf.createCompoundCurve(members);
    }
    Coordinate first = new Coordinate(members[0].getCoordinateN(0));
    LineString lastMember = members[members.length - 1];
    Coordinate last = lastMember.getCoordinateN(lastMember.getNumPoints() - 1);
    if (first.equals2D(last)) {
      return gf.createCompoundCurve(members);
    }
    LineString[] closed = new LineString[members.length + 1];
    System.arraycopy(members, 0, closed, 0, members.length);
    closed[members.length] = gf.createLineString(new Coordinate[] {
        new Coordinate(last), first
    });
    return gf.createCompoundCurve(closed);
  }

  /**
   * CCW rounded rectangle: four straights and four G² clothoid corners
   * (entry κ:0→κ, exit κ:0). Each clothoid turns π/4 so the pair is a
   * 90° fillet with no circular arc. Not a certified offset.
   */
  private static LineString[] filletRectMembers(CurveGeometryFactory gf,
      Envelope env)
  {
    double minX = env.getMinX();
    double minY = env.getMinY();
    double maxX = env.getMaxX();
    double maxY = env.getMaxY();
    double w = maxX - minX;
    double h = maxY - minY;

    double L = Math.min(16.0, 0.18 * Math.min(w, h));
    if (L < 4.0) {
      L = Math.max(2.0, 0.12 * Math.min(w, h));
    }
    double[] fit = fitClothoidCorner(gf, L, w, h);
    double kappa = fit[0];
    L = fit[1];
    double ix = fit[2];
    double iy = fit[3];

    List<LineString> members = new ArrayList<LineString>();
    Coordinate bottomStart = new Coordinate(minX + ix, minY);
    Coordinate bottomEnd = new Coordinate(maxX - ix, minY);
    addStraight(members, gf, bottomStart, bottomEnd);
    Coordinate afterBr = addClothoidCorner(members, gf, bottomEnd, 0.0, kappa, L);

    Coordinate rightEnd = new Coordinate(maxX, maxY - iy);
    addStraight(members, gf, afterBr, rightEnd);
    Coordinate afterTr = addClothoidCorner(members, gf, rightEnd, Math.PI / 2.0, kappa, L);

    Coordinate topEnd = new Coordinate(minX + ix, maxY);
    addStraight(members, gf, afterTr, topEnd);
    Coordinate afterTl = addClothoidCorner(members, gf, topEnd, Math.PI, kappa, L);

    Coordinate leftEnd = new Coordinate(minX, minY + iy);
    addStraight(members, gf, afterTl, leftEnd);
    Coordinate afterBl = addClothoidCorner(members, gf, leftEnd, -Math.PI / 2.0, kappa, L);

    addStraight(members, gf, afterBl, bottomStart);
    return members.toArray(new LineString[0]);
  }

  /**
   * Chooses κ so each half-corner turns π/4, shrinking L until the
   * fillet insets fit inside the rectangle.
   * @return {@code {kappa, L, insetX, insetY}}
   */
  private static double[] fitClothoidCorner(CurveGeometryFactory gf, double L,
      double w, double h)
  {
    double[] inset = new double[2];
    double kappa = (Math.PI / 2.0) / L;
    measureCornerInset(gf, 0.0, kappa, L, inset);
    int guard = 0;
    while ((inset[0] > 0.42 * w || inset[1] > 0.42 * h) && L > 2.0 && guard < 8) {
      L *= 0.7;
      kappa = (Math.PI / 2.0) / L;
      measureCornerInset(gf, 0.0, kappa, L, inset);
      guard++;
    }
    return new double[] { kappa, L, inset[0], inset[1] };
  }

  private static void measureCornerInset(CurveGeometryFactory gf, double heading,
      double kappa, double L, double[] inset)
  {
    ClothoidSegment entry = new ClothoidSegment(new Coordinate(0, 0), heading,
        0.0, kappa, L, gf);
    ClothoidSegment exit = new ClothoidSegment(entry.getEndCoordinate(),
        entry.getEndTangent(), kappa, 0.0, L, gf);
    inset[0] = exit.getEndCoordinate().x;
    inset[1] = exit.getEndCoordinate().y;
  }

  private static Coordinate addClothoidCorner(List<LineString> members,
      CurveGeometryFactory gf, Coordinate start, double heading,
      double kappa, double L)
  {
    ClothoidSegment entry = new ClothoidSegment(new Coordinate(start), heading,
        0.0, kappa, L, gf);
    ClothoidSegment exit = new ClothoidSegment(entry.getEndCoordinate(),
        entry.getEndTangent(), kappa, 0.0, L, gf);
    members.add(entry);
    members.add(exit);
    return exit.getEndCoordinate();
  }

  private static void addStraight(List<LineString> members, GeometryFactory gf,
      Coordinate a, Coordinate b)
  {
    if (a.distance(b) < 1.0e-8) {
      return;
    }
    members.add(gf.createLineString(new Coordinate[] {
        new Coordinate(a), new Coordinate(b)
    }));
  }

  private static CompoundCurve create_J(CurveGeometryFactory gf)
  {
    LineString stem = gf.createLineString(new Coordinate[] {
        new Coordinate(0, HEIGHT),
        new Coordinate(J_WIDTH, HEIGHT),
        new Coordinate(J_WIDTH, J_RADIUS)
    });
    // Quarter-circle hook: centre (J_WIDTH - J_RADIUS, J_RADIUS),
    // from the stem foot through the south-east mid to the base.
    double midOff = J_RADIUS / Math.sqrt(2.0);
    CircularString hook = circularString(gf,
        new Coordinate(J_WIDTH, J_RADIUS),
        new Coordinate(J_WIDTH - J_RADIUS + midOff, J_RADIUS - midOff),
        new Coordinate(J_WIDTH - J_RADIUS, 0));
    LineString base = gf.createLineString(new Coordinate[] {
        new Coordinate(J_WIDTH - J_RADIUS, 0),
        new Coordinate(0, 0)
    });
    return gf.createCompoundCurve(new LineString[] { stem, hook, base });
  }
  
  private static LineString[] create_T(CurveGeometryFactory gf)
  {
    LineString tTop = gf.createLineString(new Coordinate[] {
        new Coordinate(J_WIDTH, HEIGHT),
        new Coordinate(WIDTH - S_RADIUS - 5, HEIGHT)
    });
    LineString tStem = gf.createLineString(new Coordinate[] {
        new Coordinate(J_WIDTH + 0.5 * T_WIDTH, HEIGHT),
        new Coordinate(J_WIDTH + 0.5 * T_WIDTH, 0)
    });
    return new LineString[] { tTop, tStem };
  }

  private static CompoundCurve create_S(CurveGeometryFactory gf)
  {
    double centreX = WIDTH - S_RADIUS;
    
    LineString top = gf.createLineString(new Coordinate[] {
        new Coordinate(WIDTH, HEIGHT),
        new Coordinate(centreX, HEIGHT)
    });
    CircularString bowlTop = circularString(gf,
        new Coordinate(centreX, HEIGHT),
        new Coordinate(centreX - S_RADIUS, HEIGHT - S_RADIUS),
        new Coordinate(centreX, HEIGHT / 2));
    CircularString bowlBottom = circularString(gf,
        new Coordinate(centreX, HEIGHT / 2),
        new Coordinate(centreX + S_RADIUS, S_RADIUS),
        new Coordinate(centreX, 0));
    LineString bottom = gf.createLineString(new Coordinate[] {
        new Coordinate(centreX, 0),
        new Coordinate(WIDTH - 2 * S_RADIUS, 0)
    });
    return gf.createCompoundCurve(new LineString[] {
        top, bowlTop, bowlBottom, bottom
    });
  }

  private static CircularString circularString(CurveGeometryFactory gf,
      Coordinate a, Coordinate b, Coordinate c)
  {
    return gf.createCircularString(
        gf.getCoordinateSequenceFactory().create(new Coordinate[] { a, b, c }));
  }

  private static CurveGeometryFactory curveFactory(Geometry g)
  {
    GeometryFactory gf = FunctionsUtil.getFactoryOrDefault(g);
    if (gf instanceof CurveGeometryFactory) {
      return (CurveGeometryFactory) gf;
    }
    return new CurveGeometryFactory(gf.getPrecisionModel(), gf.getSRID());
  }

}

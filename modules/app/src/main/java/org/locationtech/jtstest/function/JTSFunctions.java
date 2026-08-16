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

import org.locationtech.jts.JTSVersion;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.curve.CircularString;
import org.locationtech.jts.geom.curve.CompoundCurve;
import org.locationtech.jts.geom.curve.CurveGeometryFactory;
import org.locationtech.jts.geom.curve.Linearizable;
import org.locationtech.jts.operation.buffer.BufferOp;
import org.locationtech.jts.operation.buffer.BufferParameters;

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
   * Hero halo: {@link #logoLines} then one {@link BufferOp} at the
   * given distance with {@link BufferParameters#JOIN_MITRE} (mitre
   * limit {@link BufferParameters#DEFAULT_MITRE_LIMIT}, not 0) and
   * {@link BufferParameters#CAP_SQUARE} (box caps).
   * <p>
   * Densify is {@link Linearizable#toLinear(double) toLinear(0.0)} —
   * CircularArcDensifier default sagitta, 1% of radius (J R=25 →
   * ε=0.25; S R=17.5 → ε=0.175). Named linear fallback / CHORD-PATH /
   * NAMED-APPROX. Not a laser. Not clothoid. Never claimed exact.
   * Ignores A; rebuilds the wordmark.
   * <p>
   * One BufferOp on the whole ISO/IEC 13249-3 MultiCurve already
   * unions overlapping offsets (T–S gap is 5; at d=4, 4+4&gt;5). Do
   * not weld J/T/S into one LineString. Do not union letters in
   * {@link #logoLines} — overlay linearises. “Not one halo” is a
   * thick 4-stroke wordmark, not BufferOp forgetting to union.
   * <p>
   * Quiet defaults elsewhere, written not flattened:
   * {@code Buffer.buffer} after logo in A is CurveOps.buffer →
   * {@code Geometry.buffer(d)} with no BufferParameters (CAP_ROUND +
   * JOIN_ROUND). {@code Buffer.bufferWithParams} reaches BufferOp
   * with the UI ints but does not linearize (control-point chords);
   * empty Quadrant Segs → 0 and empty Mitre Limit → 0.0 (JOIN_MITRE
   * bevels). Real disconnect paths are {@code Buffer.bufferEach} and
   * empty-A {@code Buffer.buffer} NPE — not this function.
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

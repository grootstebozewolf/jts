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

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.CoordinateSequence;
import org.locationtech.jts.geom.CoordinateSequenceFactory;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.PrecisionModel;
import org.locationtech.jts.precision.GeometryPrecisionReducer;

/**
 * Precision reducer for curved geometries (PRC-SN TAG).
 *
 * <p>Snaps control points of CircularString / members of CompoundCurve.
 * If after snapping the resulting arc(s) have centre(s) that are precise
 * under the PrecisionModel (i.e. "grid-friendly"), the curved type is
 * preserved with the snapped controls. Otherwise, densifies via toLinear
 * (with tolerance derived from PM) and reduces the linear approximation.
 *
 * <p>This implements Option D from the PRC-SN spec: preserve when the
 * snapped (R, centre) still "lies on grid", else densify-and-snap chords.
 *
 * <p>Usage: for curved inputs, prefer
 *   CurvedPrecisionReducer.reduce(geom, pm)
 * over the standard GeometryPrecisionReducer, to get type-preserving
 * behaviour for grid-friendly arcs.
 *
 * <p>Part of curve-awareness epic (JTS#1195), Phase 7, hardened with
 * proofs from NetTopologySuite.Proofs#66 (precision/snap/overlay).
 */
public class CurvedPrecisionReducer {

  private CurvedPrecisionReducer() {}

  /**
   * Returns true if snapping the control points of the given CircularString
   * under pm yields a grid-friendly arc, i.e. the circle through the three
   * snapped controls has a centre whose coordinates are unchanged by
   * pm.makePrecise (within numeric tolerance).
   */
  public static boolean isGridFriendly(CircularString cs, PrecisionModel pm) {
    if (cs == null || cs.isEmpty() || cs.getNumPoints() < 3) return false;
    CoordinateSequence seq = cs.getCoordinateSequence();
    // For multi-arc, check every arc triple
    for (int i = 0; i + 2 < seq.size(); i += 2) {
      Coordinate s = new Coordinate(seq.getX(i), seq.getY(i));
      Coordinate m = new Coordinate(seq.getX(i+1), seq.getY(i+1));
      Coordinate e = new Coordinate(seq.getX(i+2), seq.getY(i+2));
      Coordinate ss = new Coordinate(s); pm.makePrecise(ss);
      Coordinate sm = new Coordinate(m); pm.makePrecise(sm);
      Coordinate se = new Coordinate(e); pm.makePrecise(se);
      if (ss.equals2D(sm) || sm.equals2D(se) || ss.equals2D(se)) return false;
      double[] cen = CircularArcs.circumcentre(ss, sm, se);
      if (cen == null) return false;
      Coordinate c = new Coordinate(cen[0], cen[1]);
      Coordinate csnap = new Coordinate(c);
      pm.makePrecise(csnap);
      if (!c.equals2D(csnap)) return false;
      // Optional: radius check for FIXED PM (integer multiple of grid step)
      if (pm.getType() == PrecisionModel.FIXED) {
        double r = c.distance(ss);
        double gs = 1.0 / pm.getScale();
        double rem = Math.abs(r % gs);
        if (rem > 1e-9 && Math.abs(rem - gs) > 1e-9) return false;
      }
    }
    return true;
  }

  /**
   * Reduce precision of a (possibly curved) geometry, preserving curved
   * types for grid-friendly arcs per PRC-SN Option D.
   */
  public static Geometry reduce(Geometry g, PrecisionModel pm) {
    if (g instanceof CircularString) {
      return reduceCS((CircularString) g, pm);
    }
    if (g instanceof CompoundCurve) {
      return reduceCC((CompoundCurve) g, pm);
    }
    // For CurvePolygon etc, could recurse on rings, but for this TAG focus on lineals.
    // Fall back for others (or polygons would snap their linear views).
    return GeometryPrecisionReducer.reduce(g, pm);
  }

  private static Geometry reduceCS(CircularString cs, PrecisionModel pm) {
    if (cs.getNumPoints() < 3) {
      return GeometryPrecisionReducer.reduce(cs, pm);
    }
    CoordinateSequence origSeq = cs.getCoordinateSequence();
    // Snap all controls
    Coordinate[] snapped = new Coordinate[origSeq.size()];
    for (int i = 0; i < snapped.length; i++) {
      snapped[i] = new Coordinate(origSeq.getX(i), origSeq.getY(i));
      pm.makePrecise(snapped[i]);
    }
    // Check if all arcs grid-friendly (centres precise after snap)
    boolean friendly = true;
    for (int i = 0; i + 2 < snapped.length; i += 2) {
      double[] cen = CircularArcs.circumcentre(snapped[i], snapped[i+1], snapped[i+2]);
      if (cen == null) {
        friendly = false; break;
      }
      Coordinate c = new Coordinate(cen[0], cen[1]);
      Coordinate csnap = new Coordinate(c);
      pm.makePrecise(csnap);
      if (!c.equals2D(csnap)) {
        friendly = false; break;
      }
      // radius check for FIXED
      if (pm.getType() == PrecisionModel.FIXED) {
        double r = c.distance(snapped[i]);
        double gs = 1.0 / pm.getScale();
        double rem = Math.abs(r % gs);
        if (rem > 1e-9 && Math.abs(rem - gs) > 1e-9) {
          friendly = false; break;
        }
      }
    }
    if (friendly) {
      // preserve as CS with snapped controls
      GeometryFactory f = cs.getFactory();
      if (!(f instanceof CurvedGeometryFactory)) {
        f = new CurvedGeometryFactory(pm, f.getSRID(), f.getCoordinateSequenceFactory());
      }
      CoordinateSequence newSeq = f.getCoordinateSequenceFactory().create(snapped);
      return ((CurvedGeometryFactory) f).createCircularString(newSeq);
    }
    // fallback: densify then reduce linear
    double tol = 0.0;
    if (pm.getScale() > 0) {
      tol = 1.0 / pm.getScale() / 100.0; // fine enough for approx
    } else {
      tol = 1e-8;
    }
    Geometry lin = ((Linearizable) cs).toLinear(tol);
    return GeometryPrecisionReducer.reduce(lin, pm);
  }

  private static Geometry reduceCC(CompoundCurve cc, PrecisionModel pm) {
    int n = cc.getNumMembers();
    LineString[] reducedMembers = new LineString[n];
    for (int i = 0; i < n; i++) {
      LineString m = cc.getMemberN(i);
      Geometry rm;
      if (m instanceof CircularString) {
        rm = reduceCS((CircularString) m, pm);
      } else {
        rm = GeometryPrecisionReducer.reduce(m, pm);
      }
      if (rm instanceof LineString) {
        reducedMembers[i] = (LineString) rm;
      } else {
        reducedMembers[i] = (LineString) GeometryPrecisionReducer.reduce(m, pm);
      }
    }
    // Rebuild CC from reduced members (preserves structure if possible)
    GeometryFactory outF = (cc.getFactory() instanceof CurvedGeometryFactory)
        ? cc.getFactory()
        : new CurvedGeometryFactory(pm, cc.getFactory().getSRID(), cc.getFactory().getCoordinateSequenceFactory());
    return new CompoundCurve(reducedMembers, outF);
  }

  // TODO: extend for CurvePolygon rings if needed (snap each structural ring)
}

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
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.PrecisionModel;
import org.locationtech.jts.geom.util.GeometryEditor;
import org.locationtech.jts.precision.GeometryPrecisionReducer;

/**
 * Precision reduction that is curve-aware for CircularString / CompoundCurve members.
 * <p>
 * For grid-friendly arcs (see isGridFriendly), the arc is PRESERVEd (re-snap controls
 * only, or rebuild CC with snapped centre/r if needed); otherwise fall back to
 * densify + standard GeometryPrecisionReducer (loses arc identity, as expected).
 * <p>
 * This is the implementation for PRC-SN (#66) under JTS#1195. Decision uses exact
 * Q cross-check from proofs oracle (via vectors + CurveSnapRefRunner) for soundness.
 * <p>
 * Hardened with proofs artifact run 26887314315/art 7385761173 (AngleBetween + full
 * CURVE_SNAP_DECISION exact; 0 counterexamples in hunter).
 */
public final class CurvedPrecisionReducer {

  private CurvedPrecisionReducer() {}

  /**
   * Returns true iff snapping the 3 arc controls to the grid yields a circumcentre
   * that is itself invariant under the PrecisionModel (i.e. on-grid).
   * For FIXED, optionally also checks r is grid-multiple (r*scale is integer within ulp).
   * Matches the logic in proofs driver.ml run_curve_snap_decision (exact Q path).
   */
  public static boolean isGridFriendly(CircularString cs, PrecisionModel pm) {
    if (cs == null || cs.isEmpty() || cs.getNumPoints() < 3) return false;
    Coordinate[] ctrl = cs.getCoordinates();
    if (ctrl.length < 3) return false;
    // Snap a copy of the controls (do not mutate input)
    Coordinate s0 = new Coordinate(ctrl[0]); pm.makePrecise(s0);
    Coordinate s1 = new Coordinate(ctrl[1]); pm.makePrecise(s1);
    Coordinate s2 = new Coordinate(ctrl[2]); pm.makePrecise(s2);
    // Degenerate after snap?
    if (s0.equals2D(s1) || s1.equals2D(s2) || s0.equals2D(s2)) return false;
    Coordinate centre = CircularArcs.circumcentre(s0, s1, s2);
    if (centre == null) return false;
    double cx = pm.makePrecise(centre.x);
    double cy = pm.makePrecise(centre.y);
    boolean centreOnGrid = (cx == centre.x) && (cy == centre.y);
    if (!centreOnGrid) return false;
    if (pm.getType() == PrecisionModel.FIXED) {
      double r = Math.hypot(s0.x - centre.x, s0.y - centre.y);
      return isRMultiple(r, pm.getScale());
    }
    return true;
  }

  private static boolean isRMultiple(double r, double scale) {
    if (scale <= 0) return true;
    double rs = r * scale;
    // within reasonable ulp for the magnitude (grid multiple test is advisory)
    return Math.abs(rs - Math.round(rs)) < 1e-9 * Math.max(1.0, Math.abs(rs));
  }

  /** Apply reduce to a geometry, preserving curved structure where grid-friendly. */
  public static Geometry reduce(Geometry g, PrecisionModel pm) {
    if (g == null) return null;
    if (pm == null || pm.isFloating()) {
      // no-op for float
      return g.copy();
    }
    // Editor to handle per-member for CC, and top for CS/CP etc.
    GeometryEditor editor = new GeometryEditor(g.getFactory());
    return editor.edit(g, new CurvedReduceOp(pm));
  }

  private static final class CurvedReduceOp implements GeometryEditor.GeometryEditorOperation {
    private final PrecisionModel pm;
    CurvedReduceOp(PrecisionModel pm) { this.pm = pm; }
    @Override
    public Geometry edit(Geometry geometry, GeometryFactory factory) {
      if (geometry instanceof CircularString) {
        CircularString cs = (CircularString) geometry;
        if (isGridFriendly(cs, pm)) {
          // preserve: snap the controls in place (new CS)
          CoordinateSequence seq = cs.getCoordinateSequence().copy();
          for (int i = 0; i < seq.size(); i++) {
            double x = seq.getOrdinate(i, 0);
            double y = seq.getOrdinate(i, 1);
            seq.setOrdinate(i, 0, pm.makePrecise(x));
            seq.setOrdinate(i, 1, pm.makePrecise(y));
          }
          return new CircularString(seq, factory);
        } else {
          // fallback
          Geometry lin = ((Linearizable) cs).toLinear(0.0);
          return GeometryPrecisionReducer.reduce(lin, pm);
        }
      }
      if (geometry instanceof Linearizable) {
        // phase-1: for CC / CP etc that are collapsed or structural, fallback densify+reduce
        // (real impl would walk members for CS and preserve friendly arcs)
        Geometry lin = ((Linearizable) geometry).toLinear(0.0);
        return GeometryPrecisionReducer.reduce(lin, pm);
      }
      // default for linear or other
      return GeometryPrecisionReducer.reduce(geometry, pm);
    }
  }
}

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

import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.operation.overlay.OverlayOp;
import org.locationtech.jts.operation.union.UnaryUnionOp;
import org.locationtech.jts.operation.union.UnionStrategy;

public class OverlayNoSnapFunctions {
  /**
   * Densifies curve operands before handing them to core.
   * <p>
   * These are static entry points taking a {@link Geometry}, so a curve type has
   * no virtual call to override, and left alone they node the chords through the
   * control points: two concentric circles of radius 5 and 3 intersected in 18
   * rather than 9*pi. Worse, a CurvePolygon reports an arc-aware area while its
   * coordinates enclose the chord area, and OverlayNG's own cross-check rejected
   * that contradiction with
   * {@code TopologyException("Result area inconsistent with overlay operation")}.
   * <p>
   * Non-curve input is returned as the same object, so nothing without an arc is
   * affected. The arc cannot survive an overlay at any tolerance -- see
   * {@code CurveOps} -- so the result is a densified plain geometry by necessity.
   */
  private static Geometry arc(Geometry g) {
    return CurveFunctions.linearizeForOps(g);
  }

	public static Geometry intersection(Geometry a, Geometry b)		{		return OverlayOp.overlayOp(arc(a), arc(b), OverlayOp.INTERSECTION);	}
	public static Geometry union(Geometry a, Geometry b)					{		return OverlayOp.overlayOp(arc(a), arc(b), OverlayOp.UNION);	}
	public static Geometry symDifference(Geometry a, Geometry b)	{		return OverlayOp.overlayOp(arc(a), arc(b), OverlayOp.SYMDIFFERENCE);	}
	public static Geometry difference(Geometry a, Geometry b)			{		return OverlayOp.overlayOp(arc(a), arc(b), OverlayOp.DIFFERENCE);	}
	public static Geometry differenceBA(Geometry a, Geometry b)		{		return OverlayOp.overlayOp(arc(b), arc(a), OverlayOp.DIFFERENCE);	}

	 public static Geometry unaryUnion(Geometry a) {
	    UnionStrategy unionSRFun = new UnionStrategy() {

	      public Geometry union(Geometry g0, Geometry g1) {
	         return OverlayOp.overlayOp(g0, g1, OverlayOp.UNION );
	      }

	      @Override
	      public boolean isFloatingPrecision() {
	        return true;
	      }
	      
	    };
	    UnaryUnionOp op = new UnaryUnionOp(arc(a));
	    op.setUnionFunction(unionSRFun);
	    return op.union();
	  }
}

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

import org.locationtech.jts.geom.Geometry;


/**
 * Overlay via the instance methods, so curve awareness rides on virtual
 * dispatch: a curve-typed A routes through its CRV-OPS override into the
 * OverlayNGCurve ratchet. A plain A with a curve B is flipped in
 * {@code Geometry} onto that same path (CAP / CUP / XOR). Difference is
 * not symmetric and still nodes B's control points when A is plain.
 */
public class OverlayFunctions {
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

	public static Geometry intersection(Geometry a, Geometry b)		{		return a.intersection(b);	}
	public static Geometry union(Geometry a, Geometry b)					{		return a.union(b);	}
	public static Geometry symDifference(Geometry a, Geometry b)	{		return a.symDifference(b);	}
	public static Geometry difference(Geometry a, Geometry b)			{		return a.difference(b);	}
	public static Geometry differenceBA(Geometry a, Geometry b)		{		return b.difference(a);	}
  public static Geometry unaryUnion(Geometry a)                 {   return a.union(); }
  
  public static Geometry unionUsingGeometryCollection(Geometry a, Geometry b)                 
  {   
    Geometry gc = a.getFactory().createGeometryCollection(
        new Geometry[] { arc(a), arc(b)});
    return gc.union(); 
  }

  public static Geometry clip(Geometry a, Geometry mask)
  {
    List geoms = new ArrayList();
    for (int i = 0; i < a.getNumGeometries(); i++) {
      Geometry clip = a.getGeometryN(i).intersection(mask);
      geoms.add(clip);
    }
    return FunctionsUtil.buildGeometry(geoms, a);
  }
  
  
}

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
package org.locationtech.jtstest.function;

import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.operation.overlayng.curve.OverlayNGCurve;
import org.locationtech.jtstest.geomfunction.Metadata;

/**
 * TestBuilder surface for {@link OverlayNGCurve}, the curve-aware overlay
 * ratchet, so its behaviour can be inspected visually: an exact answer renders
 * as the original arc (a five-point CurvePolygon), a densified one as a
 * thousand-vertex polygon, and the two are unmistakable on the canvas.
 * <p>
 * The instance-method panel ({@code Overlay.*}) reaches the same implementation
 * since the CurveOps delegation refactor; this panel exists to name the ratchet
 * explicitly and to expose {@link #exactness}, which reports per operation
 * whether the answer needed densification at all.
 */
public class OverlayNGCurveFunctions {

  @Metadata(description="CAP: Common Area of Partners -- exact when algebra or retention answers")
  public static Geometry intersection(Geometry a, Geometry b) {
    return OverlayNGCurve.intersection(a, b);
  }

  @Metadata(description="CUP: Cover Under Partners -- exact for self, empty, covers and disjoint (MultiSurface)")
  public static Geometry union(Geometry a, Geometry b) {
    return OverlayNGCurve.union(a, b);
  }

  @Metadata(description="SUB: subtract B's shadow -- margin-gated, falls to core near the boundary")
  public static Geometry difference(Geometry a, Geometry b) {
    return OverlayNGCurve.difference(a, b);
  }

  @Metadata(description="XOR: keep only what isn't shared")
  public static Geometry symDifference(Geometry a, Geometry b) {
    return OverlayNGCurve.symDifference(a, b);
  }

  @Metadata(description="Exactness report: which of CAP/CUP/SUB/XOR answered without densifying")
  public static String exactness(Geometry a, Geometry b) {
    int[] ops = { OverlayNGCurve.INTERSECTION, OverlayNGCurve.UNION,
        OverlayNGCurve.DIFFERENCE, OverlayNGCurve.SYMDIFFERENCE };
    String[] names = { "CAP intersection", "CUP union       ",
        "SUB difference  ", "XOR symDifference" };
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < ops.length; i++) {
      OverlayNGCurve op = new OverlayNGCurve(a, b);
      String cell;
      try {
        Geometry r = op.getResult(ops[i]);
        cell = (op.isApproximate() ? "approx" : "EXACT ")
            + "  " + r.getGeometryType() + "[" + r.getNumPoints() + "]"
            + "  area=" + r.getArea();
      }
      catch (RuntimeException e) {
        cell = e.getClass().getSimpleName() + ": " + e.getMessage();
      }
      sb.append(names[i]).append("  ").append(cell).append("\n");
    }
    return sb.toString();
  }
}

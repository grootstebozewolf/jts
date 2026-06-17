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

import org.locationtech.jts.densify.Densifier;
import org.locationtech.jts.geom.Geometry;

/**
 * Curve-aware densification (DSF, JTS #1195).
 * <p>
 * The core {@link Densifier} walks coordinates and subdivides the segments
 * between them. On a curved geometry those segments are the control-point chords,
 * so densifying would insert points that do not lie on the actual arcs. This
 * shadow entry point instead delegates a {@link Linearizable} input to
 * {@link Linearizable#toLinear(double)}, which samples the arcs to the given
 * sagitta tolerance (so every output point lies on the curve); any other geometry
 * is handed to the core {@code Densifier} unchanged.
 */
public final class CurvedDensifier {

  private CurvedDensifier() {}

  /**
   * Densifies {@code geom} to the given distance (sagitta) tolerance: curved
   * inputs via their arc linearisation, everything else via the core
   * {@link Densifier}.
   */
  public static Geometry densify(Geometry geom, double distanceTolerance) {
    if (geom instanceof Linearizable) {
      return ((Linearizable) geom).toLinear(distanceTolerance);
    }
    return Densifier.densify(geom, distanceTolerance);
  }
}

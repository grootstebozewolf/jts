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
package org.locationtech.jts.geom.ext;

import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LinearRing;
import org.locationtech.jts.geom.Polygon;

/**
 * Dumb stand-in: a CurvePolygon is stored as a Polygon whose rings are
 * linearized LinearRings. Curve structure is lost.
 */
public class CurvePolygon extends Polygon {
  private static final long serialVersionUID = 1L;

  public CurvePolygon(LinearRing shell, LinearRing[] holes, GeometryFactory factory) {
    super(shell, holes, factory);
  }

  public CurvePolygon(GeometryFactory factory) {
    super(null, null, factory);
  }

  @Override
  public String getGeometryType() {
    return "CurvePolygon";
  }
}

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
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.MultiLineString;

/** Dumb stand-in: MultiCurve is a MultiLineString of (possibly tagged) line strings. */
public class MultiCurve extends MultiLineString {
  private static final long serialVersionUID = 1L;

  public MultiCurve(LineString[] members, GeometryFactory factory) {
    super(members, factory);
  }

  @Override
  public String getGeometryType() {
    return "MultiCurve";
  }
}

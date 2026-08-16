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
package org.locationtech.jts.io.curve;

import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.curve.CurveGeometryFactory;
import org.locationtech.jts.io.WKBReader;

/**
 * A {@link WKBReader} that defaults to a {@link CurveGeometryFactory}
 * so ISO/OGC SQL/MM type codes 8–12 construct first-class curve types.
 * The core reader already recognises those codes and delegates
 * construction to the factory; this subclass is the convenience
 * no-arg constructor.
 */
public class CurveWKBReader extends WKBReader {

  public CurveWKBReader() {
    super(new CurveGeometryFactory());
  }

  public CurveWKBReader(GeometryFactory geometryFactory) {
    super(geometryFactory);
  }
}

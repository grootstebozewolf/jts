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
 * so ISO/IEC 13249-3 SQL/MM type codes 8–12 construct first-class
 * curve types from both WKB flavours (ISO {@code type+1000/2000/3000}
 * and Extended EWKB high bits). Z / M / ZM coordinates survive.
 * Flavour is detected on read the way GEOS {@code WKBReader.cpp} does.
 * There is no {@code wkbCurve} / {@code wkbSurface}. WKB 15–17
 * (Triangle / PolyhedralSurface / TIN) are not added here — GEO-TIN
 * waits Architect SIGN. Unknown types throw.
 * The core reader already recognises codes 8–12 and delegates
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

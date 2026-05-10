/*
 * Copyright (c) 2026 grootstebozewolf
 * Adapted from a 2020 contribution by Jeroen Bloemscheer to a JTS fork
 * (the `CIRCULARSTRING` branch).
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * and Eclipse Distribution License v. 1.0 which accompanies this distribution.
 * The Eclipse Public License is available at http://www.eclipse.org/legal/epl-v20.html
 * and the Eclipse Distribution License is available at
 *
 * http://www.eclipse.org/org/documents/edl-v10.php.
 */
package org.locationtech.jtstest.testbuilder.ui.tools;

import org.locationtech.jtstest.testbuilder.model.GeometryType;

/**
 * Stream-style mouse-draw tool for {@link
 * org.locationtech.jts.geom.curved.CircularString} geometries. Each
 * captured triple of points becomes one circular arc.
 */
public class CircularStringTool extends AbstractStreamDrawTool {

  private static CircularStringTool singleton = null;

  public static CircularStringTool getInstance() {
    if (singleton == null)
      singleton = new CircularStringTool();
    return singleton;
  }

  private CircularStringTool() {
  }

  @Override
  protected int getGeometryType() {
    return GeometryType.CIRCULARSTRING;
  }
}

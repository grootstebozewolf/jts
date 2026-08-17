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

/**
 * Optional thin OrientableSegment adapters ({@code doc/EXACT_CURVE_BIBLE.md} §3).
 * <p>
 * Public surface: {@link OrientableSegment}, {@link OrientableSegments}.
 * Implementations are package-private and compose
 * {@link org.locationtech.jts.algorithm.exactcurve.ExactCircularArc}.
 * This package is not the centre of curve design.
 */
package org.locationtech.jts.algorithm.orientable;

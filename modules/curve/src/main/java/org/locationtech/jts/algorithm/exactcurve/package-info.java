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
 * ExactCurve* family. Year 1 locks {@link
 * org.locationtech.jts.algorithm.exactcurve.ExactCircularArc} as the
 * privileged pure primitive. Sibling types (Bézier, ellipse, clothoid,
 * single-span NURBS) are Year 2 and must not force complexity onto the
 * circular atom.
 * <p>
 * Canonical architecture: {@code doc/EXACT_CURVE_BIBLE.md}.
 */
package org.locationtech.jts.algorithm.exactcurve;

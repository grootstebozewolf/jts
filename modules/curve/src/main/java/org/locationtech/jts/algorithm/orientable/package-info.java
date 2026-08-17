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
 * Optional OrientableSegment adapters. Demoted under
 * {@code doc/EXACT_CURVE_BIBLE.md} §3: {@code ExactCircularArc} is the
 * privileged primitive; this package must not become the centre of
 * curve design. Adapters compose Exact* types; they do not own
 * length, sweep, area, or densify.
 */
package org.locationtech.jts.algorithm.orientable;

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
package org.locationtech.jts.algorithm.rocq;

import junit.framework.TestCase;

/**
 * Phase 5 in-process FFI smoke. Skips when {@code libntsrocq} is not on
 * the loader path (CI default). Set {@code NTS_ROCQ_LIB} or put
 * {@code libntsrocq.so} on {@code java.library.path} / {@code LD_LIBRARY_PATH}
 * to run the kernel checks.
 */
public class RocqNativeTest extends TestCase {

    public RocqNativeTest(String name) {
        super(name);
    }

    public void testUnavailableIsSafe() {
        // Must not throw just because the native library is absent.
        RocqNative.isAvailable();
    }

    public void testOrientFilteredCCW() {
        if (!RocqNative.isAvailable()) {
            return;
        }
        assertEquals(RocqNative.ORIENT_POS,
            RocqNative.orientSignFiltered(0, 0, 1, 0, 0, 1));
    }

    public void testOrientExactEscalation() {
        if (!RocqNative.isAvailable()) {
            return;
        }
        int filtered = RocqNative.orientSignFiltered(0, 0, 1, 0, 0, 1);
        if (filtered == RocqNative.ORIENT_UNCERTAIN) {
            int exact = RocqNative.orientSignExact(0, 0, 1, 0, 0, 1);
            assertTrue(exact == RocqNative.ORIENT_POS
                || exact == RocqNative.ORIENT_NEG
                || exact == RocqNative.ORIENT_ZERO);
        }
        else {
            assertEquals(RocqNative.ORIENT_POS, filtered);
        }
    }

    public void testInCircleInside() {
        if (!RocqNative.isAvailable()) {
            return;
        }
        // Triangle (0,0)-(2,0)-(1,1), query (1,-0.5) is outside.
        double det = RocqNative.inCircle(0, 0, 2, 0, 1, 1, 1, -0.5);
        assertTrue(det < 0);
    }
}

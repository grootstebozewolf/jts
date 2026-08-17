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
package org.locationtech.jts.geom.curve;

import java.util.logging.Level;
import java.util.logging.Logger;

import org.locationtech.jts.geom.Geometry;

/**
 * Explicit policy for when curve inputs densify to chords.
 * <p>
 * MMF contract (#1195 Option B): there is <b>no silent linearization</b>.
 * The default mode is {@link #LINEARIZED} (chainsaw earth), but every
 * densify of a {@link Linearizable} must log a warning. Exact / preserve
 * paths refuse densify here and leave kits / Option B noding to answer.
 * <p>
 * Thread-local override lets TestBuilder / a single call site opt into
 * {@link #PRESERVE} without changing process default.
 */
public enum CurveLinearizationStrategy {

  /**
   * Explicit chord fallback. Default. Densify is allowed and <b>must</b>
   * warn.
   */
  LINEARIZED,

  /**
   * Keep curve identity. {@link CurveOps#linearise(Geometry)} returns the
   * input unchanged when it is {@link Linearizable}; callers that need
   * chords must set {@link #LINEARIZED} deliberately.
   */
  PRESERVE;

  private static final Logger LOG = Logger.getLogger(
      CurveLinearizationStrategy.class.getName());

  private static final ThreadLocal<CurveLinearizationStrategy> OVERRIDE =
      new ThreadLocal<CurveLinearizationStrategy>();

  private static volatile CurveLinearizationStrategy processDefault = LINEARIZED;

  /**
   * Process-wide default. Starts as {@link #LINEARIZED}.
   *
   * @return the default strategy
   */
  public static CurveLinearizationStrategy getDefault() {
    return processDefault;
  }

  /**
   * Sets the process-wide default.
   *
   * @param strategy the strategy; {@code null} resets to {@link #LINEARIZED}
   */
  public static void setDefault(CurveLinearizationStrategy strategy) {
    processDefault = strategy == null ? LINEARIZED : strategy;
  }

  /**
   * Effective strategy for this thread (override or process default).
   *
   * @return the effective strategy
   */
  public static CurveLinearizationStrategy current() {
    CurveLinearizationStrategy o = OVERRIDE.get();
    return o != null ? o : processDefault;
  }

  /**
   * Thread-local override for the duration of a call stack.
   *
   * @param strategy override, or {@code null} to clear
   */
  public static void setThreadOverride(CurveLinearizationStrategy strategy) {
    if (strategy == null) {
      OVERRIDE.remove();
    }
    else {
      OVERRIDE.set(strategy);
    }
  }

  /**
   * Clears the thread-local override.
   */
  public static void clearThreadOverride() {
    OVERRIDE.remove();
  }

  /**
   * Logs the mandatory linearization warning when a curve is densified.
   *
   * @param g the curve being linearized
   * @param op a short op name for the message (e.g. {@code "CurveOps.linearise"})
   */
  public static void warnLinearized(Geometry g, String op) {
    if (g == null) {
      return;
    }
    if (!LOG.isLoggable(Level.WARNING)) {
      return;
    }
    String type = g.getGeometryType();
    LOG.warning(op + ": linearizing " + type
        + " under CurveLinearizationStrategy." + current()
        + " (explicit strategy; not a silent flatten)");
  }
}

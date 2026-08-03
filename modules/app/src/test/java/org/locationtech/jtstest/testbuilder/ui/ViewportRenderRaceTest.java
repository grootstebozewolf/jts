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
package org.locationtech.jtstest.testbuilder.ui;

import java.awt.Dimension;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;
import junit.textui.TestRunner;

/**
 * TB-VP: the render worker must survive running before the panel is laid out.
 * <p>
 * Reported from a visual-QA session as
 * {@code NullPointerException: Cannot read field "height" because
 * "this.viewSize" is null} on {@code Thread-0}, thrown from
 * {@code Viewport.updateModelToViewTransform} under
 * {@code GridElement.drawAxes}. The sequence is a startup race:
 * {@code RendererSwingWorker} renders on a background thread, and if a render
 * is triggered before Swing has laid the panel out, {@code update(Dimension)}
 * has never run and {@code viewSize} is still null -- but
 * {@code getModelToViewTransform()} lazily builds the transform and
 * dereferences {@code viewSize.height}. The exception kills that render pass;
 * the canvas recovers on the next layout, which is why it reads as a flicker
 * plus a stack trace rather than a broken app.
 * <p>
 * The fix initialises {@code viewSize} to an empty {@code Dimension}: every
 * transform and query is then well-defined (if useless) before layout, and the
 * first real {@code update} replaces it. A pre-layout render draws nothing
 * visible into a 0x0 view, which is exactly what it should draw.
 * <p>
 * Constructed with a null panel deliberately: the failure path never touches
 * the panel, and keeping Swing out of it makes the test runnable headless.
 */
public class ViewportRenderRaceTest extends TestCase {

  public static void main(String[] args) { TestRunner.run(suite()); }
  public static Test suite() { return new TestSuite(ViewportRenderRaceTest.class); }
  public ViewportRenderRaceTest(String name) { super(name); }

  /** The reported crash: transform requested before any layout. */
  public void testTransformBeforeLayoutDoesNotThrow() {
    Viewport vp = new Viewport(null);
    assertNotNull("a pre-layout transform must exist rather than NPE",
        vp.getModelToViewTransform());
  }

  /** The call GridElement.drawAxes actually makes. */
  public void testToViewBeforeLayoutDoesNotThrow() {
    Viewport vp = new Viewport(null);
    assertNotNull("toView must be answerable before layout",
        vp.toView(new org.locationtech.jts.geom.Coordinate(1, 2)));
  }

  /**
   * Guard: a real layout still produces a working transform afterwards.
   * Uses a real panel because {@code update} notifies it; constructing a
   * JPanel without showing a window is fine headless.
   */
  public void testUpdateAfterLayoutStillWorks() {
    Viewport vp = new Viewport(
        new org.locationtech.jtstest.testbuilder.GeometryEditPanel());
    vp.getModelToViewTransform();               // pre-layout call first
    vp.update(new Dimension(800, 600));
    // Property assertion rather than a fixed pixel: the viewport applies an
    // initial model origin of its own, so the y-flip and unit scale are what a
    // real layout guarantees -- ten model units up is ten view pixels down.
    java.awt.geom.Point2D low = vp.toView(new org.locationtech.jts.geom.Coordinate(0, 0));
    java.awt.geom.Point2D high = vp.toView(new org.locationtech.jts.geom.Coordinate(0, 10));
    assertEquals("y axis flips and scale is 1 after layout",
        10.0, low.getY() - high.getY(), 1.0e-9);
  }
}

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
/* Adapted from a 2020 contribution by Jeroen Bloemscheer to a JTS fork
 * (the `CIRCULARSTRING` branch). */
package org.locationtech.jtstest.testbuilder.ui.tools;

import java.awt.Shape;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.geom.GeneralPath;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.List;

import org.locationtech.jts.awt.PointTransformation;
import org.locationtech.jts.awt.curve.CircularArcRenderer;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jtstest.testbuilder.GeometryEditPanel;
import org.locationtech.jtstest.testbuilder.model.GeometryType;

/**
 * Click-to-control mouse-draw tool for an ISO/IEC 13249-3 SQL/MM
 * {@link org.locationtech.jts.geom.curve.CircularString}. Each captured
 * triple of points is one circular arc (odd control count &ge; 3).
 *
 * <p>Same A-blue new-draw as the first component: chord until click 3
 * is the unique circle through the triple. Drag must not stream extra
 * vertices (that is LineString stream-draw). After a commit, the next
 * new-draw starts clean. Escape cancels the in-progress band; already
 * committed {@code CIRCULARSTRING} members stay. Never even
 * {@code CIRCULARSTRING} in A. Never flatten.
 *
 * <p>Overrides {@link #getShape()} so that the in-progress preview of a
 * complete (start, mid, mouse) triple is an arc — using the same
 * {@link CircularArcRenderer} that
 * {@link org.locationtech.jts.awt.curve.CurveShapeWriter} uses for
 * finished geometry — not a leftover chord.
 */
public class CircularStringTool extends AbstractStreamDrawTool {

  private static CircularStringTool singleton = null;

  private boolean cancelling = false;

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

  @Override
  boolean isStreamAddOnDrag() {
    return false;
  }

  @Override
  public void activate(GeometryEditPanel panel) {
    super.activate(panel);
    panel.setFocusable(true);
    panel.addKeyListener(this);
    panel.requestFocusInWindow();
  }

  @Override
  public void deactivate() {
    if (panel() != null) {
      panel().removeKeyListener(this);
    }
    cancelling = false;
    super.deactivate();
  }

  @Override
  public void mousePressed(MouseEvent e) {
    if (panel() != null) {
      panel().requestFocusInWindow();
    }
    super.mousePressed(e);
  }

  @Override
  public void keyPressed(KeyEvent e) {
    if (isCancelKey(e.getKeyCode())) {
      e.consume();
      cancelInProgress();
    }
  }

  /** Only Escape cancels an in-progress CircularString. */
  static boolean isCancelKey(int keyCode) {
    return keyCode == KeyEvent.VK_ESCAPE;
  }

  /**
   * Log auto-switch is not the lock. Cancel must not steal Input
   * via {@code showInfoTab} / {@code displayInfo(..., true)}.
   */
  static boolean cancelStealsInputTab() {
    return false;
  }

  static boolean cancelCallsDisplayInfo() {
    return false;
  }

  /**
   * After commit or Escape, the next A-blue new-draw starts with zero
   * captured points — same first-draw: chord until click 3 is the
   * unique circle.
   */
  static int newDrawCapturedCount() {
    return 0;
  }

  /**
   * ISO/IEC 13249-3 SQL/MM {@code CIRCULARSTRING}: odd control count
   * &ge; 3. Even count is invalid. Never emit even {@code CIRCULARSTRING}.
   */
  static boolean isValidCircularStringCount(int n) {
    return n >= 3 && n % 2 == 1;
  }

  /**
   * Points consumed by complete (start, mid, end) triples in a band of
   * size {@code n} (captured plus tentative mouse).
   */
  static int completeArcPointCount(int n) {
    if (n < 3) {
      return 0;
    }
    return n % 2 == 1 ? n : n - 1;
  }

  static boolean previewHasTrailingChord(int n) {
    return n >= 2 && completeArcPointCount(n) < n;
  }

  /**
   * Controls that may land in A. ISO/IEC 13249-3 forbids even
   * {@code CIRCULARSTRING}. A trailing even leftover is dropped; fewer
   * than 3 points abort. Never flatten.
   */
  static List<Coordinate> controlsForCommit(List<?> captured) {
    List<Coordinate> coords = new ArrayList<Coordinate>();
    if (captured == null) {
      return coords;
    }
    for (Object o : captured) {
      coords.add((Coordinate) o);
    }
    if (coords.size() >= 3 && coords.size() % 2 == 0) {
      coords.remove(coords.size() - 1);
    }
    if (coords.size() < 3) {
      return new ArrayList<Coordinate>();
    }
    return coords;
  }

  /**
   * Escape is the only caller. Drops the in-progress band without
   * {@code addComponent}. Already committed A stays.
   */
  private void cancelInProgress() {
    if (getCoordinates().isEmpty()) {
      return;
    }
    cancelling = true;
    try {
      finishGesture();
    } catch (Exception ignored) {
      // In-progress band is dropped. Do not commit.
    } finally {
      cancelling = false;
    }
  }

  /**
   * ISO/IEC 13249-3: a {@code CIRCULARSTRING} must contain an odd
   * number of points &ge; 3 (each consecutive (start, mid, end) triple
   * defines one arc). If the user finishes on an even count, the
   * trailing point is not anchored to a complete arc, so we drop it
   * before committing rather than emit an even {@code CIRCULARSTRING}.
   * If fewer than 3 points were captured we abort without committing.
   *
   * <p>After this commit, {@link LineBandTool#finishGesture} clears
   * the band so the next new-draw starts clean — same A-blue gesture:
   * chord until click 3 is the unique circle. Escape sets
   * {@code cancelling} so the in-progress second is dropped and the
   * first {@code CIRCULARSTRING} stays.
   *
   * <p>The rubber-band preview already renders a complete triple as an
   * arc and any trailing leftover as a chord, so the visual cue and
   * the commit semantics agree. Never flatten.
   */
  @Override
  protected void bandFinished() throws Exception {
    if (cancelling) {
      return;
    }
    if (panel().getModel() == null) return;
    panel().getGeomModel().setGeometryType(getGeometryType());

    List<Coordinate> coords = controlsForCommit(getCoordinates());
    if (coords.isEmpty() || !isValidCircularStringCount(coords.size())) {
      return;
    }

    geomModel().addComponent(coords);
    panel().updateGeom();
  }

  /**
   * Same A-blue new-draw: a 2-point band is a chord; a complete
   * (start, mid, mouse) triple is the unique circle through those
   * ISO/IEC 13249-3 controls, not a leftover chord. Trailing even
   * leftover stays a chord hint until dropped on commit.
   */
  @Override
  protected Shape getShape() {
    List<Coordinate> captured = new ArrayList<Coordinate>();
    for (Object o : getCoordinates()) captured.add((Coordinate) o);
    if (captured.isEmpty()) return null;
    if (tentativeCoordinate != null) captured.add(tentativeCoordinate);
    int n = captured.size();

    // Number of points consumed by complete arcs: largest odd value
    // <= n that is also >= 3 (each new triple starts at an even index
    // and shares its first point with the previous triple's end).
    int arcPts = completeArcPointCount(n);

    GeneralPath path = new GeneralPath();
    PointTransformation pt = new PointTransformation() {
      @Override
      public void transform(Coordinate model, Point2D view) {
        Point2D out = toView(model);
        view.setLocation(out.getX(), out.getY());
      }
    };

    Point2D first = toView(captured.get(0));
    path.moveTo((float) first.getX(), (float) first.getY());

    if (arcPts >= 3) {
      for (int i = 0; i + 2 < arcPts; i += 2) {
        CircularArcRenderer.appendArc(path,
            captured.get(i),
            captured.get(i + 1),
            captured.get(i + 2),
            pt);
      }
    }

    // Trailing partial: lineTo every captured point past the last arc end.
    int trailingStart = (arcPts >= 3) ? arcPts : 1;
    for (int i = trailingStart; i < n; i++) {
      Point2D v = toView(captured.get(i));
      path.lineTo((float) v.getX(), (float) v.getY());
    }

    drawVertices(path, captured);
    return path;
  }

  /** Square markers at each captured control point. Mirrors
   *  {@link LineBandTool}'s vertex decoration so the user can see
   *  which points anchor the arcs. */
  private void drawVertices(GeneralPath path, List<Coordinate> coords) {
    for (Coordinate coord : coords) {
      Point2D p = toView(coord);
      path.moveTo((float) (p.getX() - 2), (float) (p.getY() - 2));
      path.lineTo((float) (p.getX() + 2), (float) (p.getY() - 2));
      path.lineTo((float) (p.getX() + 2), (float) (p.getY() + 2));
      path.lineTo((float) (p.getX() - 2), (float) (p.getY() + 2));
      path.lineTo((float) (p.getX() - 2), (float) (p.getY() - 2));
    }
  }
}

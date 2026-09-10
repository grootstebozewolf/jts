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
/*
 * AI Disclosure (Eclipse Foundation GenAI Guidelines):
 * AI-generated portions are dedicated to CC0-1.0; human-reviewed.
 *
 * SPDX-License-Identifier: (EPL-2.0 OR EDL-1.0) AND CC0-1.0
 * Assisted-by: xAI Grok (grok-4.3)
 * Assisted-by: Claude (Opus-4.7)
 */
package org.locationtech.jtstest.testbuilder;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.util.Locale;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.SwingConstants;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.CoordinateSequence;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.curve.CircularString;
import org.locationtech.jts.geom.curve.ClothoidSegment;
import org.locationtech.jts.geom.curve.CompoundCurve;
import org.locationtech.jts.geom.curve.CurveGeometryFactory;
import org.locationtech.jtstest.testbuilder.model.GeometryEditModel;
import org.locationtech.jtstest.testbuilder.model.TestBuilderModel;

/**
 * Day-1 parameter editor for {@link ClothoidSegment} members of a
 * top-level {@link CompoundCurve} on Geometry A or B. Mirrors
 * {@link StatsPanel}'s simple {@code setModel} + {@code refresh}
 * pattern so {@link JTSTestBuilderFrame} can wire it like any other
 * tab in {@code inputTabbedPane}.
 *
 * <p>Editable parameters: κ₀, κ₁, L, θ_start (degrees), and the
 * start coordinate (x, y). Read-only derived values: end coordinate,
 * end tangent, and the IFC clothoid constant {@code A}. Commit is
 * on the explicit <em>Apply</em> button — per-keystroke commit was
 * considered but rejected for Day 1 because mid-typing values like
 * "0.00" would constantly invalidate.
 *
 * <p>Day-1 scope: single top-level CompoundCurve on Geometry A or B.
 * Nested CompoundCurves inside a GeometryCollection are not yet
 * supported by the editor's selector — the underlying {@link
 * CompoundCurve#withMemberReplaced} works at any depth, the
 * limitation is purely in this UI.
 */
public class ClothoidPanel extends JPanel {
  private static final long serialVersionUID = 1L;

  private TestBuilderModel tbModel;

  private final JComboBox<ClothoidRef> selector = new JComboBox<ClothoidRef>();

  private final JTextField fldStartKappa = new JTextField(12);
  private final JTextField fldEndKappa   = new JTextField(12);
  private final JTextField fldLength     = new JTextField(12);
  private final JTextField fldStartTheta = new JTextField(12);
  private final JTextField fldStartX     = new JTextField(12);
  private final JTextField fldStartY     = new JTextField(12);

  private final JLabel    lblA           = new JLabel(" ");
  private final JLabel    lblEndX        = new JLabel(" ");
  private final JLabel    lblEndY        = new JLabel(" ");
  private final JLabel    lblEndTheta    = new JLabel(" ");

  private final JButton   btnApply       = new JButton("Apply");
  private final JButton   btnAutoDerive  = new JButton("Auto-derive κ from neighbours");
  private final JTextField fldInsertL    = new JTextField("20", 8);
  private final JButton   btnInsertSpiral = new JButton("Insert spiral before next arc");
  private final JLabel    lblStatus      = new JLabel(" ");

  /** Identifies one ClothoidSegment inside the model: which geometry
   *  (0 = A, 1 = B), which member index inside the top-level
   *  CompoundCurve. */
  private static final class ClothoidRef {
    final int geomIndex;
    final int memberIndex;
    final ClothoidSegment segment;
    ClothoidRef(int g, int m, ClothoidSegment s) {
      this.geomIndex = g; this.memberIndex = m; this.segment = s;
    }
    @Override public String toString() {
      return String.format(Locale.ROOT, "Geom %s · member %d  κ:%s→%s L=%s",
          geomIndex == 0 ? "A" : "B", memberIndex,
          fmt(segment.getStartKappa()), fmt(segment.getEndKappa()),
          fmt(segment.getLength()));
    }
    private static String fmt(double v) {
      if (v == 0.0) return "0";
      double abs = Math.abs(v);
      if (abs >= 0.001 && abs < 1e6) return String.format(Locale.ROOT, "%.4g", v);
      return String.format(Locale.ROOT, "%.3e", v);
    }
  }

  public ClothoidPanel() {
    setLayout(new BorderLayout());
    add(buildEditor(), BorderLayout.CENTER);
    selector.addActionListener(e -> loadFromSelected());
    btnApply.addActionListener(e -> applyChanges());
    btnAutoDerive.addActionListener(e -> autoDeriveAndLoad());
    btnInsertSpiral.addActionListener(e -> insertSpiralBeforeNextArc());
  }

  public void setModel(TestBuilderModel m) {
    this.tbModel = m;
    refresh();
  }

  public void refresh() {
    rebuildSelector();
  }

  // -- UI construction --------------------------------------------

  private JPanel buildEditor() {
    JPanel root = new JPanel(new BorderLayout(4, 4));
    root.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

    // Top: selector
    JPanel top = new JPanel(new BorderLayout(4, 4));
    top.add(new JLabel("Clothoid: "), BorderLayout.WEST);
    top.add(selector, BorderLayout.CENTER);
    root.add(top, BorderLayout.NORTH);

    // Centre: parameter grid
    JPanel grid = new JPanel(new GridBagLayout());
    GridBagConstraints c = new GridBagConstraints();
    c.insets = new Insets(3, 6, 3, 6);
    c.anchor = GridBagConstraints.WEST;
    int row = 0;

    addLabeledField(grid, c, row++, "κ₀ (start curvature, 1/m)", fldStartKappa);
    addLabeledField(grid, c, row++, "κ₁ (end curvature, 1/m)",   fldEndKappa);
    addLabeledField(grid, c, row++, "L (length, m)",              fldLength);
    addLabeledField(grid, c, row++, "θ_start (degrees)",          fldStartTheta);
    addLabeledField(grid, c, row++, "start x",                    fldStartX);
    addLabeledField(grid, c, row++, "start y",                    fldStartY);
    addRow(grid, c, row++, "");
    addReadOnly(grid, c, row++, "A = √(L / |κ₁ − κ₀|)", lblA);
    addReadOnly(grid, c, row++, "end x (derived)",       lblEndX);
    addReadOnly(grid, c, row++, "end y (derived)",       lblEndY);
    addReadOnly(grid, c, row++, "θ_end (derived, deg)",  lblEndTheta);

    root.add(new JScrollPane(grid), BorderLayout.CENTER);

    // Bottom: actions + status
    JPanel bottom = new JPanel(new BorderLayout(4, 4));
    JPanel actions = new JPanel(new GridLayout(2, 1, 0, 4));
    JPanel editButtons = new JPanel();
    editButtons.add(btnApply);
    editButtons.add(Box.createHorizontalStrut(10));
    editButtons.add(btnAutoDerive);
    JPanel insertRow = new JPanel();
    insertRow.add(new JLabel("Insert spiral L (m):"));
    insertRow.add(fldInsertL);
    insertRow.add(btnInsertSpiral);
    actions.add(editButtons);
    actions.add(insertRow);
    bottom.add(actions, BorderLayout.NORTH);
    lblStatus.setForeground(new Color(80, 80, 80));
    lblStatus.setBorder(BorderFactory.createEmptyBorder(4, 4, 0, 4));
    bottom.add(lblStatus, BorderLayout.SOUTH);
    root.add(bottom, BorderLayout.SOUTH);

    return root;
  }

  private void addLabeledField(JPanel grid, GridBagConstraints c, int row,
                               String label, JTextField field) {
    c.gridy = row;
    c.gridx = 0;
    c.fill = GridBagConstraints.NONE;
    grid.add(new JLabel(label), c);
    c.gridx = 1;
    c.fill = GridBagConstraints.HORIZONTAL;
    c.weightx = 1.0;
    grid.add(field, c);
    c.weightx = 0;
  }

  private void addReadOnly(JPanel grid, GridBagConstraints c, int row,
                           String label, JLabel value) {
    c.gridy = row;
    c.gridx = 0;
    c.fill = GridBagConstraints.NONE;
    grid.add(new JLabel(label), c);
    c.gridx = 1;
    c.fill = GridBagConstraints.HORIZONTAL;
    value.setHorizontalAlignment(SwingConstants.LEFT);
    value.setForeground(new Color(60, 60, 60));
    grid.add(value, c);
  }

  private void addRow(JPanel grid, GridBagConstraints c, int row, String label) {
    c.gridy = row;
    c.gridx = 0;
    grid.add(new JLabel(label), c);
  }

  // -- model interaction -----------------------------------------

  private void rebuildSelector() {
    DefaultComboBoxModel<ClothoidRef> model = new DefaultComboBoxModel<ClothoidRef>();
    if (tbModel != null) {
      GeometryEditModel gem = tbModel.getGeometryEditModel();
      addClothoidsFromTopLevel(model, 0, gem.getGeometry(0));
      addClothoidsFromTopLevel(model, 1, gem.getGeometry(1));
    }
    selector.setModel(model);
    if (model.getSize() == 0) {
      clearFields();
      lblStatus.setText("No top-level CompoundCurve with ClothoidSegment members on A or B.");
    } else {
      selector.setSelectedIndex(0);
      loadFromSelected();
    }
  }

  /** Rebuild the dropdown after a mutation, then re-select the item
   *  matching {@code (geomIndex, memberIndex)} so iterative editing on
   *  the same segment is uninterrupted. Falls back to index 0 if the
   *  segment no longer exists (e.g. user replaced the geometry). */
  private void refreshSelectorPreservingSelection(int geomIndex, int memberIndex) {
    DefaultComboBoxModel<ClothoidRef> model = new DefaultComboBoxModel<ClothoidRef>();
    if (tbModel != null) {
      GeometryEditModel gem = tbModel.getGeometryEditModel();
      addClothoidsFromTopLevel(model, 0, gem.getGeometry(0));
      addClothoidsFromTopLevel(model, 1, gem.getGeometry(1));
    }
    selector.setModel(model);
    if (model.getSize() == 0) {
      clearFields();
      return;
    }
    int restored = -1;
    for (int i = 0; i < model.getSize(); i++) {
      ClothoidRef r = model.getElementAt(i);
      if (r.geomIndex == geomIndex && r.memberIndex == memberIndex) {
        restored = i;
        break;
      }
    }
    selector.setSelectedIndex(restored >= 0 ? restored : 0);
    loadFromSelected();
  }

  private static void addClothoidsFromTopLevel(DefaultComboBoxModel<ClothoidRef> model,
                                               int geomIdx, Geometry g) {
    if (!(g instanceof CompoundCurve)) return;
    CompoundCurve cc = (CompoundCurve) g;
    for (int i = 0; i < cc.getNumMembers(); i++) {
      LineString m = cc.getMemberN(i);
      if (m instanceof ClothoidSegment) {
        model.addElement(new ClothoidRef(geomIdx, i, (ClothoidSegment) m));
      }
    }
  }

  private void loadFromSelected() {
    ClothoidRef ref = (ClothoidRef) selector.getSelectedItem();
    if (ref == null) { clearFields(); return; }
    ClothoidSegment cs = ref.segment;
    fldStartKappa.setText(formatExact(cs.getStartKappa()));
    fldEndKappa.setText(formatExact(cs.getEndKappa()));
    fldLength.setText(formatExact(cs.getLength()));
    fldStartTheta.setText(formatExact(Math.toDegrees(cs.getStartTangent())));
    fldStartX.setText(formatExact(cs.getStartCoordinate().x));
    fldStartY.setText(formatExact(cs.getStartCoordinate().y));
    lblA.setText(formatExact(cs.getClothoidConstantA()));
    lblEndX.setText(formatExact(cs.getEndCoordinate().x));
    lblEndY.setText(formatExact(cs.getEndCoordinate().y));
    lblEndTheta.setText(String.format(Locale.ROOT, "%.6f", Math.toDegrees(cs.getEndTangent())));
    btnAutoDerive.setEnabled(canAutoDerive(ref));
    lblStatus.setText("Editing " + ref.toString());
  }

  private void clearFields() {
    for (JTextField f : new JTextField[]{
        fldStartKappa, fldEndKappa, fldLength, fldStartTheta, fldStartX, fldStartY }) {
      f.setText("");
    }
    lblA.setText(" ");
    lblEndX.setText(" ");
    lblEndY.setText(" ");
    lblEndTheta.setText(" ");
    btnAutoDerive.setEnabled(false);
  }

  private void applyChanges() {
    ClothoidRef ref = (ClothoidRef) selector.getSelectedItem();
    if (ref == null || tbModel == null) return;
    try {
      double k0 = parseLocaleTolerant(fldStartKappa.getText());
      double k1 = parseLocaleTolerant(fldEndKappa.getText());
      double L  = parseLocaleTolerant(fldLength.getText());
      double thetaDeg = parseLocaleTolerant(fldStartTheta.getText());
      double x0 = parseLocaleTolerant(fldStartX.getText());
      double y0 = parseLocaleTolerant(fldStartY.getText());

      Geometry g = tbModel.getGeometryEditModel().getGeometry(ref.geomIndex);
      if (!(g instanceof CompoundCurve)) {
        lblStatus.setText("Geometry has changed; reselect a clothoid.");
        rebuildSelector();
        return;
      }
      CompoundCurve cc = (CompoundCurve) g;
      // Read the *current* segment from the live geometry, not the
      // ClothoidRef's cached snapshot. ref.segment is only valid at the
      // moment the dropdown was built; after each Apply we replace the
      // member, so on the second Apply ref.segment.getEnd* would be the
      // *original* segment's state and the cascade would be computed
      // from the wrong reference frame.
      LineString currentMember = cc.getMemberN(ref.memberIndex);
      if (!(currentMember instanceof ClothoidSegment)) {
        lblStatus.setText("Selected member is no longer a Clothoid; reselect.");
        rebuildSelector();
        return;
      }
      ClothoidSegment current = (ClothoidSegment) currentMember;
      GeometryFactory gf = (cc.getFactory() instanceof CurveGeometryFactory)
          ? cc.getFactory() : new CurveGeometryFactory();
      ClothoidSegment newSeg = new ClothoidSegment(
          new Coordinate(x0, y0), Math.toRadians(thetaDeg), k0, k1, L, gf);

      // Cascade rigid-frame transform: translate-only leaves a tangent
      // kink at the next junction whenever a κ or L change rotates the
      // clothoid's end tangent. We instead apply the rigid frame map
      //   T(p) = newEnd + R(Δθ) · (p − oldEnd)
      // to every downstream member, so position *and* heading match at
      // the junction. Members keep their subtype: ClothoidSegment moves
      // its start state and rotates startTangent by Δθ; CircularString
      // and plain LineString translate-and-rotate every coord (rigid
      // map preserves circle radius and chord lengths, so the type
      // stays valid).
      Coordinate oldEnd = current.getEndCoordinate();
      double oldTheta = current.getEndTangent();
      Coordinate newEndPt = newSeg.getEndCoordinate();
      double newTheta = newSeg.getEndTangent();
      double dx = newEndPt.x - oldEnd.x;
      double dy = newEndPt.y - oldEnd.y;
      double dTheta = newTheta - oldTheta;

      LineString[] members = cc.getMembers();
      members[ref.memberIndex] = newSeg;
      int transformed = 0;
      boolean shifted = dx != 0.0 || dy != 0.0 || dTheta != 0.0;
      if (shifted) {
        double cos = Math.cos(dTheta);
        double sin = Math.sin(dTheta);
        for (int i = ref.memberIndex + 1; i < members.length; i++) {
          members[i] = transformMember(members[i], oldEnd, newEndPt,
              cos, sin, dTheta, gf);
          transformed++;
        }
      }
      CompoundCurve newCc = compoundCurve(members, gf);
      tbModel.getGeometryEditModel().setGeometry(ref.geomIndex, newCc);
      // Refresh the dropdown so its label reflects the new κ / L (the
      // toString() formatter snapshots from the cached ClothoidSegment),
      // and so the form fields show the freshly applied values rather
      // than stale text. We restore the user's selection by index so
      // they can keep iterating on the same segment.
      refreshSelectorPreservingSelection(ref.geomIndex, ref.memberIndex);
      String shiftSuffix = transformed == 0
          ? ""
          : String.format(Locale.ROOT,
              "  (cascade Δ=%.3f m, Δθ=%.3f° to %d downstream member%s)",
              Math.hypot(dx, dy), Math.toDegrees(dTheta),
              transformed, transformed == 1 ? "" : "s");
      lblStatus.setText(String.format(Locale.ROOT,
          "Applied. End: (%.6f, %.6f) θ_end=%.4f°%s",
          newSeg.getEndCoordinate().x, newSeg.getEndCoordinate().y,
          Math.toDegrees(newSeg.getEndTangent()), shiftSuffix));
    } catch (NumberFormatException nfe) {
      lblStatus.setText("Number format error: " + nfe.getMessage());
    } catch (IllegalArgumentException iae) {
      lblStatus.setText("Invalid: " + iae.getMessage());
    }
  }

  // -- auto-derive κ from neighbours -----------------------------

  /** Auto-derive is meaningful when the clothoid sits between two members
   *  whose curvatures we can determine: a {@code LineString} contributes
   *  κ = 0 at its junction; a {@code CircularString} contributes κ = ±1/R.
   *  A clothoid neighbour contributes its own start- or end-κ. */
  private boolean canAutoDerive(ClothoidRef ref) {
    if (ref == null || tbModel == null) return false;
    Geometry g = tbModel.getGeometryEditModel().getGeometry(ref.geomIndex);
    if (!(g instanceof CompoundCurve)) return false;
    CompoundCurve cc = (CompoundCurve) g;
    int prev = ref.memberIndex - 1, next = ref.memberIndex + 1;
    return prev >= 0 && next < cc.getNumMembers();
  }

  private void autoDeriveAndLoad() {
    ClothoidRef ref = (ClothoidRef) selector.getSelectedItem();
    if (ref == null || tbModel == null) return;
    Geometry g = tbModel.getGeometryEditModel().getGeometry(ref.geomIndex);
    if (!(g instanceof CompoundCurve)) return;
    CompoundCurve cc = (CompoundCurve) g;
    int idx = ref.memberIndex;
    if (idx == 0 || idx == cc.getNumMembers() - 1) return;
    LineString prev = cc.getMemberN(idx - 1);
    LineString next = cc.getMemberN(idx + 1);
    double k0 = endKappaOf(prev);
    double k1 = startKappaOf(next);
    if (Double.isNaN(k0) || Double.isNaN(k1) || k0 == k1) {
      lblStatus.setText("Cannot auto-derive: neighbours give κ₀ = κ₁ (no transition needed).");
      return;
    }
    fldStartKappa.setText(formatExact(k0));
    fldEndKappa.setText(formatExact(k1));
    lblStatus.setText(String.format(Locale.ROOT,
        "Auto-derived from neighbours: κ₀ = %s, κ₁ = %s. Adjust L and click Apply.",
        formatExact(k0), formatExact(k1)));
  }

  /** End-of-member curvature: 0 for plain LineString; ±1/R for an arc
   *  derived from the last 3 control points; ClothoidSegment.endKappa
   *  for a clothoid neighbour. */
  private static double endKappaOf(LineString m) {
    if (m instanceof ClothoidSegment) return ((ClothoidSegment) m).getEndKappa();
    if (m instanceof org.locationtech.jts.geom.curve.CircularString) {
      Coordinate[] cc = m.getCoordinates();
      if (cc.length >= 3) {
        return signedCurvature(cc[cc.length - 3], cc[cc.length - 2], cc[cc.length - 1]);
      }
    }
    return 0.0;  // plain LineString
  }

  private static double startKappaOf(LineString m) {
    if (m instanceof ClothoidSegment) return ((ClothoidSegment) m).getStartKappa();
    if (m instanceof org.locationtech.jts.geom.curve.CircularString) {
      Coordinate[] cc = m.getCoordinates();
      if (cc.length >= 3) {
        return signedCurvature(cc[0], cc[1], cc[2]);
      }
    }
    return 0.0;
  }

  /** Signed 2-D curvature 1/R from three points; positive for CCW
   *  traversal, negative for CW. NaN if colinear. */
  private static double signedCurvature(Coordinate p0, Coordinate p1, Coordinate p2) {
    double ax = (p0.x + p1.x) * 0.5, ay = (p0.y + p1.y) * 0.5;
    double bx = (p1.x + p2.x) * 0.5, by = (p1.y + p2.y) * 0.5;
    double dax = p1.y - p0.y, day = p0.x - p1.x;
    double dbx = p2.y - p1.y, dby = p1.x - p2.x;
    double det = dax * dby - day * dbx;
    if (Math.abs(det) < 1e-12) return Double.NaN;
    double t = ((bx - ax) * dby - (by - ay) * dbx) / det;
    double cx = ax + t * dax, cy = ay + t * day;
    double r = Math.hypot(p1.x - cx, p1.y - cy);
    if (r < 1e-12) return Double.NaN;
    double cross = (p1.x - p0.x) * (p2.y - p1.y) - (p1.y - p0.y) * (p2.x - p1.x);
    return (cross >= 0 ? +1.0 : -1.0) / r;
  }

  /** Inserts a CLOTHOID transition (spiral easement) at the first
   *  tangent-continuous {@code LineString → CircularString} junction
   *  found in geometry A, falling back to B. The straight is retreated
   *  by the classical tangent distance {@code k = x_e − R·sin(L/(2R))};
   *  the arc and everything after it are shifted/rotated by the same
   *  rigid frame transform we use for cascade-on-Apply, so the spiral
   *  end mates G2 with the (rotated) arc start.
   *
   *  <p>Fails gracefully with a status message when: no eligible
   *  junction exists; the existing junction has a tangent kink (so
   *  spiral fitting is ill-defined); the requested L is too long for
   *  the available straight; arc control points are colinear. */
  private void insertSpiralBeforeNextArc() {
    if (tbModel == null) return;
    double L;
    try {
      L = parseLocaleTolerant(fldInsertL.getText());
    } catch (NumberFormatException nfe) {
      lblStatus.setText("Insert L must be a number.");
      return;
    }
    if (!(L > 0) || !Double.isFinite(L)) {
      lblStatus.setText("Insert L must be positive and finite.");
      return;
    }

    int targetGi = -1;
    int targetIdx = -1;
    CompoundCurve targetCc = null;
    for (int gi = 0; gi < 2; gi++) {
      Geometry g = tbModel.getGeometryEditModel().getGeometry(gi);
      if (!(g instanceof CompoundCurve)) continue;
      CompoundCurve cc = (CompoundCurve) g;
      int idx = findFirstStraightToArcJunction(cc);
      if (idx >= 0) { targetGi = gi; targetIdx = idx; targetCc = cc; break; }
    }
    if (targetCc == null) {
      lblStatus.setText("No tangent-continuous LineString → CircularString junction in A or B.");
      return;
    }

    LineString prev = targetCc.getMemberN(targetIdx - 1);
    CircularString arc = (CircularString) targetCc.getMemberN(targetIdx);
    Coordinate[] pc = prev.getCoordinates();
    Coordinate P0 = pc[pc.length - 1];
    Coordinate Pp = pc[pc.length - 2];
    double sx = P0.x - Pp.x, sy = P0.y - Pp.y;
    double slen = Math.hypot(sx, sy);
    double dxh = sx / slen, dyh = sy / slen;
    double thetaDir = Math.atan2(dyh, dxh);

    Coordinate[] ac = arc.getCoordinates();
    double[] cir = circumcircle(ac[0], ac[1], ac[2]);
    if (cir == null) {
      lblStatus.setText("Arc control points are colinear; cannot fit a circle.");
      return;
    }
    double R = cir[2];
    double crossDN = dxh * (cir[1] - P0.y) - dyh * (cir[0] - P0.x);
    int sign = (crossDN >= 0) ? +1 : -1;
    double kappa1 = sign / R;

    GeometryFactory gf = (targetCc.getFactory() instanceof CurveGeometryFactory)
        ? targetCc.getFactory() : new CurveGeometryFactory();

    // Local-frame end of an L-spiral going κ:0→κ₁ from the origin: read
    // it off a temporary ClothoidSegment so we don't duplicate the
    // Simpson-rule integrator. xe is along the start tangent, |θ_e| is
    // the heading rotation; both are sign-symmetric except for ye which
    // we don't actually need for the cascade transform.
    ClothoidSegment temp = new ClothoidSegment(new Coordinate(0, 0), 0, 0, kappa1, L, gf);
    double xe = temp.getEndCoordinate().x;
    double thetaeMag = Math.abs(temp.getEndTangent());

    double k = xe - R * Math.sin(thetaeMag);
    if (k <= 0) {
      lblStatus.setText(String.format(Locale.ROOT,
          "L=%.3f gives k=%.4f ≤ 0; pick a larger L (need k > 0).", L, k));
      return;
    }
    if (k >= slen) {
      lblStatus.setText(String.format(Locale.ROOT,
          "L=%.3f needs k=%.3f m of straight, but last segment is only %.3f m. Pick smaller L.",
          L, k, slen));
      return;
    }

    Coordinate Ps = new Coordinate(P0.x - k * dxh, P0.y - k * dyh);
    ClothoidSegment spiral = new ClothoidSegment(Ps, thetaDir, 0, kappa1, L, gf);
    Coordinate spiralEnd = spiral.getEndCoordinate();
    double dTheta = spiral.getEndTangent() - thetaDir;
    double cos = Math.cos(dTheta);
    double sin = Math.sin(dTheta);

    LineString[] oldMembers = targetCc.getMembers();
    LineString[] newMembers = new LineString[oldMembers.length + 1];
    for (int i = 0; i < targetIdx - 1; i++) {
      newMembers[i] = oldMembers[i];
    }
    Coordinate[] newPrevCoords = new Coordinate[pc.length];
    System.arraycopy(pc, 0, newPrevCoords, 0, pc.length - 1);
    newPrevCoords[pc.length - 1] = Ps;
    newMembers[targetIdx - 1] = gf.createLineString(newPrevCoords);
    newMembers[targetIdx] = spiral;
    for (int i = targetIdx; i < oldMembers.length; i++) {
      newMembers[i + 1] = transformMember(oldMembers[i], P0, spiralEnd,
          cos, sin, dTheta, gf);
    }

    CompoundCurve newCc = compoundCurve(newMembers, gf);
    tbModel.getGeometryEditModel().setGeometry(targetGi, newCc);
    refreshSelectorPreservingSelection(targetGi, targetIdx);
    lblStatus.setText(String.format(Locale.ROOT,
        "Inserted L=%.3f spiral (κ:0→%s) at member %d on geom %s. k=%.3f, Δθ=%.3f°",
        L, formatExact(kappa1), targetIdx, targetGi == 0 ? "A" : "B",
        k, Math.toDegrees(dTheta)));
  }

  /** Scans for the first index {@code i} where member {@code i-1} is a
   *  plain LineString and member {@code i} is a CircularString, AND the
   *  tangent of the straight matches the arc's start tangent within
   *  ~3°. Returns -1 if nothing eligible is found. */
  private static int findFirstStraightToArcJunction(CompoundCurve cc) {
    for (int i = 1; i < cc.getNumMembers(); i++) {
      LineString cur = cc.getMemberN(i);
      LineString prev = cc.getMemberN(i - 1);
      if (cur instanceof ClothoidSegment) continue;
      if (!(cur instanceof CircularString)) continue;
      if (prev instanceof CircularString) continue;
      if (prev instanceof ClothoidSegment) continue;
      if (isG1Continuous(prev, (CircularString) cur)) return i;
    }
    return -1;
  }

  /** True if {@code prev}'s end tangent matches {@code arc}'s start
   *  tangent within ~3°. The arc tangent at its start is computed
   *  analytically from the circumcircle, then disambiguated CCW vs CW
   *  by checking which sense points toward the second control point.
   *  Position match is required to within 1 mm. */
  private static boolean isG1Continuous(LineString prev, CircularString arc) {
    Coordinate[] pc = prev.getCoordinates();
    if (pc.length < 2) return false;
    Coordinate[] ac = arc.getCoordinates();
    if (ac.length < 3) return false;
    Coordinate P0 = pc[pc.length - 1];
    if (Math.hypot(ac[0].x - P0.x, ac[0].y - P0.y) > 1e-3) return false;
    Coordinate Pp = pc[pc.length - 2];
    double sx = P0.x - Pp.x, sy = P0.y - Pp.y;
    double slen = Math.hypot(sx, sy);
    if (slen == 0) return false;
    double dx = sx / slen, dy = sy / slen;
    double[] cir = circumcircle(ac[0], ac[1], ac[2]);
    if (cir == null) return false;
    double rxh = (ac[0].x - cir[0]) / cir[2];
    double ryh = (ac[0].y - cir[1]) / cir[2];
    double tCcwX = -ryh, tCcwY = rxh;
    double tCwX  =  ryh, tCwY  = -rxh;
    double mx = ac[1].x - ac[0].x, my = ac[1].y - ac[0].y;
    double tx, ty;
    if (mx * tCcwX + my * tCcwY >= 0) { tx = tCcwX; ty = tCcwY; }
    else                              { tx = tCwX;  ty = tCwY;  }
    return dx * tx + dy * ty > 0.999;  // ≈ within 2.6°
  }

  /** Centre and radius of the circle through three points, or
   *  {@code null} if the points are colinear. Same formula as in
   *  {@link org.locationtech.jtstest.testbuilder.ui.GeometryLocationsWriter},
   *  but exposed here so we don't introduce a UI ↔ UI dependency. */
  private static double[] circumcircle(Coordinate a, Coordinate b, Coordinate c) {
    double ax = a.x - c.x, ay = a.y - c.y;
    double bx = b.x - c.x, by = b.y - c.y;
    double d = 2.0 * (ax * by - ay * bx);
    if (Math.abs(d) < 1e-12) return null;
    double ux = ((ax * ax + ay * ay) * by - (bx * bx + by * by) * ay) / d;
    double uy = ((bx * bx + by * by) * ax - (ax * ax + ay * ay) * bx) / d;
    double cx = c.x + ux, cy = c.y + uy;
    return new double[] { cx, cy, Math.hypot(ux, uy) };
  }

  /** Applies the rigid frame map {@code T(p) = newEnd + R(Δθ)·(p−oldEnd)}
   *  to a CompoundCurve member, preserving subtype. ClothoidSegment moves
   *  its start state and rotates {@code startTangent} by Δθ; CircularString
   *  and LineString transform every coord. The map is a rigid motion so
   *  arc radii and chord lengths are preserved (the subtype stays valid).
   *  cos/sin are precomputed by the caller -- one trig pair per Apply, not
   *  per member. */
  private static LineString transformMember(LineString m,
      Coordinate oldEnd, Coordinate newEnd,
      double cos, double sin, double dTheta,
      GeometryFactory gf) {
    if (m instanceof ClothoidSegment) {
      ClothoidSegment cs = (ClothoidSegment) m;
      Coordinate np = mapPoint(cs.getStartCoordinate(), oldEnd, newEnd, cos, sin);
      return new ClothoidSegment(
          np, cs.getStartTangent() + dTheta,
          cs.getStartKappa(), cs.getEndKappa(),
          cs.getLength(), gf);
    }
    Coordinate[] cc = m.getCoordinates();
    Coordinate[] r = new Coordinate[cc.length];
    for (int i = 0; i < cc.length; i++) {
      r[i] = mapPoint(cc[i], oldEnd, newEnd, cos, sin);
    }
    if (m instanceof CircularString && gf instanceof CurveGeometryFactory) {
      CoordinateSequence seq = gf.getCoordinateSequenceFactory().create(r);
      return ((CurveGeometryFactory) gf).createCircularString(seq);
    }
    return gf.createLineString(r);
  }

  /** Single-point rigid frame map. Centralised so {@link #transformMember}
   *  and any future callers stay consistent. */
  private static Coordinate mapPoint(Coordinate p, Coordinate oldEnd, Coordinate newEnd,
                                     double cos, double sin) {
    double rx = p.x - oldEnd.x;
    double ry = p.y - oldEnd.y;
    return new Coordinate(
        newEnd.x + cos * rx - sin * ry,
        newEnd.y + sin * rx + cos * ry);
  }

  /** Honest {@link CurveGeometryFactory#createCompoundCurve(LineString[])}
   *  when the factory is a curve factory; otherwise the LineString[]
   *  constructor. Never the legacy CoordinateSequence polyline wrap. */
  private static CompoundCurve compoundCurve(LineString[] members, GeometryFactory gf) {
    if (gf instanceof CurveGeometryFactory) {
      return ((CurveGeometryFactory) gf).createCompoundCurve(members);
    }
    return new CompoundCurve(members, gf);
  }

  /** Locale-independent numeric format that round-trips through
   *  {@link Double#parseDouble}. Always uses dot decimal separator
   *  (so editing in nl_NL / de_DE / fr_FR locales doesn't produce
   *  comma output that {@code parseDouble} would reject). 12
   *  significant digits is enough to capture the analytical-end
   *  precision of a clothoid's parameters with float-noise margin. */
  private static String formatExact(double v) {
    if (v == 0.0) return "0";
    if (v == (long) v && Math.abs(v) < 1e15) return Long.toString((long) v);
    return String.format(Locale.ROOT, "%.12g", v)
        .replaceAll("0+$", "")
        .replaceAll("\\.$", "");
  }

  /** Locale-tolerant parse: accepts dot or comma as decimal separator,
   *  trims whitespace. We always WRITE with dot via
   *  {@link #formatExact}, but a user typing in their system locale
   *  on a Dutch / German / French machine may habitually use comma —
   *  accept that gracefully on the read side. */
  private static double parseLocaleTolerant(String s) {
    if (s == null) throw new NumberFormatException("null");
    return Double.parseDouble(s.trim().replace(',', '.'));
  }
}

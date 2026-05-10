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
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.ArrayList;
import java.util.List;
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
import org.locationtech.jts.geom.curved.CircularString;
import org.locationtech.jts.geom.curved.ClothoidSegment;
import org.locationtech.jts.geom.curved.CompoundCurve;
import org.locationtech.jts.geom.curved.CurvedGeometryFactory;
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
    JPanel buttons = new JPanel();
    buttons.add(btnApply);
    buttons.add(Box.createHorizontalStrut(10));
    buttons.add(btnAutoDerive);
    bottom.add(buttons, BorderLayout.NORTH);
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
      GeometryFactory gf = (cc.getFactory() instanceof CurvedGeometryFactory)
          ? cc.getFactory() : new CurvedGeometryFactory();
      ClothoidSegment newSeg = new ClothoidSegment(
          new Coordinate(x0, y0), Math.toRadians(thetaDeg), k0, k1, L, gf);

      // Cascade-translate: if the new clothoid's end has moved relative to
      // the old clothoid's end, translate all subsequent members by the
      // same delta so the chain stays connected. The relative shape of
      // downstream members is preserved -- a CAD-style edit. Tangent
      // continuity at the next junction is *not* restored automatically;
      // if a κ change rotates the end tangent, the user can fix the next
      // junction with auto-derive on the next clothoid.
      Coordinate oldEnd = ref.segment.getEndCoordinate();
      Coordinate newEndPt = newSeg.getEndCoordinate();
      double dx = newEndPt.x - oldEnd.x;
      double dy = newEndPt.y - oldEnd.y;

      LineString[] members = cc.getMembers();
      members[ref.memberIndex] = newSeg;
      int translated = 0;
      if (dx != 0.0 || dy != 0.0) {
        for (int i = ref.memberIndex + 1; i < members.length; i++) {
          members[i] = translateMember(members[i], dx, dy, gf);
          translated++;
        }
      }
      CompoundCurve newCc = new CompoundCurve(members, gf);
      tbModel.getGeometryEditModel().setGeometry(ref.geomIndex, newCc);
      String shiftSuffix = translated == 0
          ? ""
          : String.format(Locale.ROOT, "  (cascaded shift Δ=%.3f m to %d downstream member%s)",
              Math.hypot(dx, dy), translated, translated == 1 ? "" : "s");
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
    if (m instanceof org.locationtech.jts.geom.curved.CircularString) {
      Coordinate[] cc = m.getCoordinates();
      if (cc.length >= 3) {
        return signedCurvature(cc[cc.length - 3], cc[cc.length - 2], cc[cc.length - 1]);
      }
    }
    return 0.0;  // plain LineString
  }

  private static double startKappaOf(LineString m) {
    if (m instanceof ClothoidSegment) return ((ClothoidSegment) m).getStartKappa();
    if (m instanceof org.locationtech.jts.geom.curved.CircularString) {
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

  /** Translates a CompoundCurve member by (dx, dy), preserving subtype.
   *  ClothoidSegment moves only its start state -- the integration
   *  recomputes a new end at the corresponding offset. CircularString
   *  and LineString move every coord. No-op if the delta is zero. */
  private static LineString translateMember(LineString m, double dx, double dy,
                                            GeometryFactory gf) {
    if (dx == 0.0 && dy == 0.0) return m;
    if (m instanceof ClothoidSegment) {
      ClothoidSegment cs = (ClothoidSegment) m;
      Coordinate sp = cs.getStartCoordinate();
      return new ClothoidSegment(
          new Coordinate(sp.x + dx, sp.y + dy),
          cs.getStartTangent(), cs.getStartKappa(), cs.getEndKappa(),
          cs.getLength(), gf);
    }
    Coordinate[] cc = m.getCoordinates();
    Coordinate[] r = new Coordinate[cc.length];
    for (int i = 0; i < cc.length; i++) {
      r[i] = new Coordinate(cc[i].x + dx, cc[i].y + dy);
    }
    if (m instanceof CircularString && gf instanceof CurvedGeometryFactory) {
      CoordinateSequence seq = gf.getCoordinateSequenceFactory().create(r);
      return ((CurvedGeometryFactory) gf).createCircularString(seq);
    }
    return gf.createLineString(r);
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

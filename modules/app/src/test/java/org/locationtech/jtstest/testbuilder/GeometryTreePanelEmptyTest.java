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
package org.locationtech.jtstest.testbuilder;

import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;
import junit.textui.TestRunner;

/**
 * FCP-S (issue #3): inspector zoom / next-node must not CCE or NPE on the
 * empty-state {@code DefaultMutableTreeNode("No geometry shown")}.
 * Covers {@link GeometryTreePanel#getSelectedGeometry()} and
 * {@link GeometryTreePanel#moveToNextNode(int)}.
 */
public class GeometryTreePanelEmptyTest extends TestCase {

  public static void main(String[] args) { TestRunner.run(suite()); }
  public static Test suite() { return new TestSuite(GeometryTreePanelEmptyTest.class); }
  public GeometryTreePanelEmptyTest(String name) { super(name); }

  /**
   * Empty panel with the default root selected: zoom path must return null,
   * not throw ClassCastException.
   */
  public void testEmptyPanelSelectedRootHasNullGeometry() {
    GeometryTreePanel panel = new GeometryTreePanel();
    Object root = panel.tree.getModel().getRoot();
    assertTrue("empty-state root is a plain DefaultMutableTreeNode",
        root instanceof DefaultMutableTreeNode);
    assertFalse("empty-state root is not a GeometricObjectNode",
        root instanceof GeometricObjectNode);
    panel.tree.setSelectionPath(new TreePath(root));
    assertNull("empty-tree selection must be null geometry, not a CCE",
        panel.getSelectedGeometry());
  }

  /**
   * Next/prev on the empty-state root (parentPath == null) must no-op.
   */
  public void testEmptyPanelMoveToNextNodeIsNoOp() {
    GeometryTreePanel panel = new GeometryTreePanel();
    Object root = panel.tree.getModel().getRoot();
    TreePath rootPath = new TreePath(root);
    panel.tree.setSelectionPath(rootPath);
    panel.moveToNextNode(1);
    panel.moveToNextNode(-1);
    assertEquals("next/prev on empty root must stay on that path",
        rootPath, panel.tree.getSelectionPath());
    assertNull(panel.getSelectedGeometry());
  }
}

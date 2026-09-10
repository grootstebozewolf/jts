/*
 * Copyright (c) 2016 Vivid Solutions.
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

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.SystemColor;
import java.awt.event.ActionEvent;
import java.awt.event.FocusEvent;
import java.awt.event.MouseEvent;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;
import javax.swing.border.BevelBorder;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;
import javax.swing.event.ChangeEvent;

import org.locationtech.jtstest.testbuilder.controller.JTSTestBuilderController;
import org.locationtech.jtstest.testbuilder.event.ValidPanelEvent;
import org.locationtech.jtstest.testbuilder.event.ValidPanelListener;
import org.locationtech.jtstest.testbuilder.model.GeometryEvent;
import org.locationtech.jtstest.testbuilder.model.TestBuilderModel;
import org.locationtech.jtstest.testbuilder.model.TestCaseEdit;
import org.locationtech.jtstest.testbuilder.ui.SwingUtil;



/**
 * @version 1.7
 */
public class TestCasePanel extends JPanel {
  TestCaseEdit testCase;
  //---------------------------------------------
  BorderLayout borderLayout1 = new BorderLayout();
  BorderLayout editFrameLayout = new BorderLayout();
  JPanel editFramePanel = new JPanel();
  GeometryEditPanel editPanel = new GeometryEditPanel();
  ButtonGroup geometryType = new ButtonGroup();
  ButtonGroup editMode = new ButtonGroup();
  ButtonGroup partType = new ButtonGroup();
  Border border4;
  JPanel editGroupPanel = new JPanel();
  JTabbedPane jTabbedPane1 = new JTabbedPane();
  JPanel btnPanel = new JPanel();
  JPanel relateTabPanel = new JPanel();
  JButton btnRunTests = new JButton();
  RelatePanel relatePanel = new RelatePanel();
  BorderLayout borderLayout2 = new BorderLayout();
  //GeometryEditControlPanel editCtlPanel = new GeometryEditControlPanel();
  BorderLayout borderLayout3 = new BorderLayout();
  JPanel jPanel1 = new JPanel();
  JTextField txtDesc = new JTextField();
  GridBagLayout gridBagLayout1 = new GridBagLayout();
  SpatialFunctionPanel spatialFunctionPanel = new SpatialFunctionPanel();
  private int currentTestCaseIndex = 0;
  private int maxTestCaseIndex = 0;
  private boolean initialized = false;
  JPanel casePrecisionModelPanel = new JPanel();
  JPanel namePanel = new JPanel();
  JLabel testCaseIndexLabel = new JLabel();
  GridBagLayout gridBagLayout2 = new GridBagLayout();
  GridBagLayout gridBagLayout3 = new GridBagLayout();
  JLabel precisionModelLabel = new JLabel();
  ValidPanel validPanel = new ValidPanel();
  JPanel statusBarPanel = new JPanel();
  JLabel lblMousePos = new JLabel();
  JLabel lblPrecisionModel = new JLabel();
  JLabel lblStatus = new JLabel();
  ScalarFunctionPanel scalarFunctionPanel = new ScalarFunctionPanel();
  
  JPanel jPanelReveal = new JPanel();
  JSpinner spStretchDist = new JSpinner(new SpinnerNumberModel(5, 0, 99999, 1));
  JCheckBox cbRevealTopo = new JCheckBox();

  private TestBuilderModel tbModel;
  

  /**
   *  Construct the frame
   */
  public TestCasePanel() {
    try {
      jbInit();
    }
    catch (Exception ex) {
      ex.printStackTrace();
    }
    initialized = true;
  }

  public void setModel(TestBuilderModel tbModel) 
  { 
  	this.tbModel = tbModel; 
  	editPanel.setModel(tbModel);
    // hook up other beans
    //editCtlPanel.setModel(tbModel);

  }
  
  public void setCurrentTestCaseIndex(int currentTestCaseIndex) {
    this.currentTestCaseIndex = currentTestCaseIndex;
    updateTestCaseIndexLabel();
  }

  public void setMaxTestCaseIndex(int maxTestCaseIndex) {
    this.maxTestCaseIndex = maxTestCaseIndex;
    updateTestCaseIndexLabel();
  }

  public GeometryEditPanel getGeometryEditPanel() {
    return editPanel;
  }

  public SpatialFunctionPanel getSpatialFunctionPanel() {
    return spatialFunctionPanel;
  }

  public ScalarFunctionPanel getScalarFunctionPanel() {
    return scalarFunctionPanel;
  }

  void setTestCase(TestCaseEdit testCase) {
    this.testCase = testCase;
    tbModel.getGeometryEditModel().setTestCase(testCase);
    relatePanel.setTestCase(testCase);
//    spatialFunctionPanel.setTestCase(testCase);
    validPanel.setTestCase(testCase);
//    scalarFunctionPanel.setTestCase(testCase);
    txtDesc.setText(testCase.getName());
  }

  void editPanel_mouseMoved(MouseEvent e) {
    String cursorPos = editPanel.cursorLocationString(e.getPoint());
  	lblMousePos.setText(cursorPos);
//    System.out.println(cursorPos);
  }

  void btnRunTests_actionPerformed(ActionEvent e) {
    relatePanel.runTests();
  }

  void editPanel_geometryChanged(GeometryEvent e) {
    relatePanel.clearResults();
//    scalarFunctionPanel.clearResults();
  }
  void validPanel_setHighlightPerformed(ValidPanelEvent e) {
    editPanel.setHighlightPoint(validPanel.getMarkPoint());
    editPanel.forceRepaint();
  }

  void txtDesc_focusLost(FocusEvent e) {
    testCase.setName(txtDesc.getText());
  }

  void jTabbedPane1_stateChanged(ChangeEvent e) 
  {
    boolean isFunction = jTabbedPane1.getSelectedComponent() == spatialFunctionPanel;
    /*
    // don't bother being clever about what user should see
    // code is buggy anyway - next line is checking wrong panel
    // Plus, should now synch Layer List UI when doing this
    
    editPanel.setShowingResult(isFunction);
    editPanel.setShowingGeometryA(! isFunction
         || spatialFunctionPanel.shouldShowGeometryA());
    editPanel.setShowingGeometryB(! isFunction
         || spatialFunctionPanel.shouldShowGeometryB());
*/
    
    editPanel.setHighlightPoint(null);
    if (jTabbedPane1.getSelectedComponent() == validPanel) {
      editPanel.setHighlightPoint(validPanel.getMarkPoint());
    }
    if (initialized) {
      //avoid infinite loop
      if (isFunction)
        JTSTestBuilderFrame.instance().showResultWKTTab();
    }
  }

  public void setPrecisionModelDescription(String description) {
    precisionModelLabel.setText(description);
    lblPrecisionModel.setText(" PM: " + description);
  }

  /**
   *  Component initialization
   */
  private void jbInit() throws Exception {
    //---------------------------------------------------
    border4 = BorderFactory.createBevelBorder(BevelBorder.LOWERED, Color.white,
        Color.white, new Color(93, 93, 93), new Color(134, 134, 134));
    setLayout(borderLayout1);
    editGroupPanel.setLayout(borderLayout3);
    editPanel.addMouseMotionListener(
      new java.awt.event.MouseMotionAdapter() {

        public void mouseMoved(MouseEvent e) {
          editPanel_mouseMoved(e);
        }
        public void mouseDragged(MouseEvent e) {
          editPanel_mouseMoved(e);
        }
      });
    relateTabPanel.setLayout(borderLayout2);
    btnRunTests.setToolTipText("");
    btnRunTests.setText("Run");
    btnRunTests.addActionListener(
      new java.awt.event.ActionListener() {

        public void actionPerformed(ActionEvent e) {
          btnRunTests_actionPerformed(e);
        }
      });    
    validPanel.addValidPanelListener(
        new ValidPanelListener() {
          public void setHighlightPerformed(ValidPanelEvent e) {
            validPanel_setHighlightPerformed(e);
          }
        });
    jPanel1.setLayout(gridBagLayout1);
    txtDesc.addFocusListener(
      new java.awt.event.FocusAdapter() {

        public void focusLost(FocusEvent e) {
          txtDesc_focusLost(e);
        }
      });
    jTabbedPane1.addChangeListener(
      new javax.swing.event.ChangeListener() {

        public void stateChanged(ChangeEvent e) {
          jTabbedPane1_stateChanged(e);
        }
      });
    //testCaseIndexLabel.setBorder(BorderFactory.createLoweredBevelBorder());
    testCaseIndexLabel.setBorder(new EmptyBorder(0,4,0,0));
    testCaseIndexLabel.setToolTipText("");
    testCaseIndexLabel.setText("0 of 0");
    casePrecisionModelPanel.setLayout(gridBagLayout2);
    namePanel.setLayout(gridBagLayout3);
    precisionModelLabel.setBorder(BorderFactory.createLoweredBevelBorder());
    precisionModelLabel.setToolTipText("Precision Model");
    precisionModelLabel.setText("");

    txtDesc.setBackground(Color.white);
    lblMousePos.setBackground(SystemColor.text);
    lblMousePos.setBorder(BorderFactory.createLoweredBevelBorder());
    lblMousePos.setPreferredSize(new Dimension(21, 21));
    lblMousePos.setHorizontalAlignment(SwingConstants.RIGHT);
    lblPrecisionModel.setBackground(SystemColor.text);
    lblPrecisionModel.setBorder(BorderFactory.createLoweredBevelBorder());
//    txtSelectedPoint.setEditable(false);
    lblPrecisionModel.setText("Sel Pt:");
    
    editFramePanel.setLayout(editFrameLayout);
    editFramePanel.add(editPanel, BorderLayout.CENTER);
    editFramePanel.setBorder(BorderFactory.createBevelBorder(1));
    
    add(editGroupPanel, BorderLayout.CENTER);
    editGroupPanel.add(editFramePanel, BorderLayout.CENTER);
    editGroupPanel.add(statusBarPanel, BorderLayout.SOUTH);
 
    JCheckBox cbDisplayAB = new JCheckBox();
    cbDisplayAB.setSelected(true);
    cbDisplayAB.setToolTipText("Display A and B");
     cbDisplayAB.addActionListener(new java.awt.event.ActionListener() {
        public void actionPerformed(ActionEvent e) {
          JTSTestBuilderController.editPanel().setShowingInput(cbDisplayAB.isSelected());
        }
      });
    JLabel lblDisplayAB = new JLabel();
    lblDisplayAB.setIcon(AppIcons.GEOFUNC_BINARY);
    
    JCheckBox cbDisplayGrid = new JCheckBox();
    cbDisplayGrid.setSelected(true);
    cbDisplayGrid.setToolTipText("Display Grid");
    cbDisplayGrid.addActionListener(new java.awt.event.ActionListener() {
        public void actionPerformed(ActionEvent e) {
          JTSTestBuilderController.editPanel().setShowingGrid(cbDisplayGrid.isSelected());
        }
      });
    JLabel lblDisplayGrid = new JLabel();
    lblDisplayGrid.setIcon(AppIcons.EDIT_GRID);
    
    cbRevealTopo.setToolTipText("Reveal Topology - visualize topological detail by stretching geometries");
    spStretchDist.setToolTipText("Stretch Distance (pixels)");
    spStretchDist.setMaximumSize(new Dimension(20,20));
    ((JSpinner.DefaultEditor) spStretchDist.getEditor()).getTextField().setColumns(2);
    jPanelReveal.setLayout(new BoxLayout(jPanelReveal, BoxLayout.LINE_AXIS));
    jPanelReveal.add(Box.createHorizontalGlue());
    jPanelReveal.add(cbRevealTopo);
    jPanelReveal.add(spStretchDist);
    jPanelReveal.add(Box.createHorizontalStrut(8));
    jPanelReveal.add(cbDisplayAB);
    jPanelReveal.add(lblDisplayAB);
    jPanelReveal.add(cbDisplayGrid);
    jPanelReveal.add(lblDisplayGrid);
    jPanelReveal.add(Box.createHorizontalGlue());
    jPanelReveal.setBorder(BorderFactory.createLoweredBevelBorder());

    JButton btnSaveImage = SwingUtil.createButton(
        AppIcons.SAVE_IMAGE, AppStrings.TIP_SAVE_IMAGE,   
        new java.awt.event.ActionListener() {
          public void actionPerformed(ActionEvent e) {
            if (SwingUtil.isCtlKeyPressed(e)) {
              JTSTestBuilder.controller().saveImageAsPNG();
            } else {
              JTSTestBuilder.controller().saveImageToClipboard();
            }
        }});
    
    lblStatus.setBorder(new EmptyBorder(0, 8, 0, 4));
    lblStatus.setText("");
    lblStatus.setHorizontalAlignment(SwingConstants.LEFT);
    org.locationtech.jtstest.testbuilder.ui.AutomationIds.set(
        lblStatus,
        org.locationtech.jtstest.testbuilder.ui.AutomationIds.STATUS_CURVE_STRATEGY);
    reserveStatusRoom();

    JPanel panelCase = new JPanel();
    panelCase.setLayout(new BorderLayout());
    panelCase.setBorder(BorderFactory.createLoweredBevelBorder());
    panelCase.add(btnSaveImage, BorderLayout.EAST);
    panelCase.add(testCaseIndexLabel, BorderLayout.WEST);

    JPanel statusCell = new JPanel(new BorderLayout());
    statusCell.setBorder(BorderFactory.createLoweredBevelBorder());
    statusCell.add(lblStatus, BorderLayout.CENTER);
    statusCell.setMinimumSize(lblStatus.getMinimumSize());

    statusBarPanel.setLayout(new GridBagLayout());
    GridBagConstraints gbc = new GridBagConstraints();
    gbc.gridy = 0;
    gbc.fill = GridBagConstraints.BOTH;
    gbc.weighty = 1;
    gbc.gridx = 0;
    gbc.weightx = 0;
    statusBarPanel.add(panelCase, gbc);
    gbc.gridx = 1;
    gbc.weightx = 1;
    statusBarPanel.add(statusCell, gbc);
    gbc.gridx = 2;
    gbc.weightx = 0.4;
    statusBarPanel.add(jPanelReveal, gbc);
    gbc.gridx = 3;
    gbc.weightx = 0.3;
    statusBarPanel.add(lblPrecisionModel, gbc);
    gbc.gridx = 4;
    gbc.weightx = 0.3;
    statusBarPanel.add(lblMousePos, gbc);
    
    add(jTabbedPane1, BorderLayout.WEST);
    //jTabbedPane1.add(editCtlPanel, "Edit");
    jTabbedPane1.setOpaque(true);
    jTabbedPane1.setBackground(AppColors.BACKGROUND);
    
    JTabbedPane tabFunctions = new JTabbedPane();
    tabFunctions.setOpaque(true);
    tabFunctions.setBackground(AppColors.BACKGROUND);
    tabFunctions.add(spatialFunctionPanel,  "Geometry");
    tabFunctions.add(scalarFunctionPanel,   "Scalar");
    
    jTabbedPane1.add(tabFunctions, "Function");
    jTabbedPane1.add(relateTabPanel, "Predicate");
    jTabbedPane1.add(validPanel, "Valid/Mark");

    
    relateTabPanel.add(relatePanel, BorderLayout.CENTER);
    relateTabPanel.add(btnPanel, BorderLayout.NORTH);
    btnPanel.add(btnRunTests, null);
  }

  /**
   * Bottom status-bar message (the Case / PM strip), not the Log tab.
   * Does not select Log or steal the Input tab. The visible label is
   * sized so {@code CurvePolygon cancelled.} is not clipped.
   */
  public void setStatus(String s) {
    lblStatus.setText(s == null ? "" : s);
    reserveStatusRoom();
  }

  public String getStatus() {
    return lblStatus.getText();
  }

  /**
   * Keep enough strip width for the lock string. A tooltip is not
   * the lock — the visible label must not clip.
   */
  private void reserveStatusRoom() {
    String room = "CurvePolygon cancelled.";
    FontMetrics fm = lblStatus.getFontMetrics(lblStatus.getFont());
    Insets in = lblStatus.getInsets();
    int w = fm.stringWidth(room) + in.left + in.right;
    int h = Math.max(21, fm.getHeight() + in.top + in.bottom);
    Dimension size = new Dimension(w, h);
    lblStatus.setMinimumSize(size);
    lblStatus.setPreferredSize(size);
    if (lblStatus.getParent() != null) {
      lblStatus.getParent().setMinimumSize(size);
    }
  }

  boolean isStatusFullyVisible() {
    String t = lblStatus.getText();
    if (t == null || t.isEmpty()) {
      return true;
    }
    int need = lblStatus.getFontMetrics(lblStatus.getFont()).stringWidth(t)
        + lblStatus.getInsets().left + lblStatus.getInsets().right;
    return lblStatus.getWidth() >= need;
  }

  void layoutStatusBar(int width) {
    Dimension pref = getPreferredSize();
    setSize(Math.max(width, pref.width), Math.max(pref.height, 200));
    validate();
    doLayout();
    editGroupPanel.doLayout();
    statusBarPanel.doLayout();
    if (lblStatus.getParent() != null) {
      lblStatus.getParent().doLayout();
    }
  }

  private void updateTestCaseIndexLabel() {
    testCaseIndexLabel.setText(AppStrings.LABEL_TEST_CASE + " " + currentTestCaseIndex + " of " + maxTestCaseIndex);
  }

  public double getStretchSize() {
    return ((Integer) spStretchDist.getValue()).intValue();
  }
}


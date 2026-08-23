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
package org.locationtech.jtstest.testbuilder.ui.tools;

import java.awt.Shape;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.geom.Ellipse2D;
import java.awt.geom.GeneralPath;
import java.awt.geom.Point2D;
import javax.swing.SwingUtilities;

import org.locationtech.jts.awt.GeometryCollectionShape;
import org.locationtech.jts.awt.PointTransformation;
import org.locationtech.jts.awt.curve.CurveShapeWriter;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jtstest.testbuilder.AppCursors;
import org.locationtech.jtstest.testbuilder.GeometryEditPanel;
import org.locationtech.jtstest.testbuilder.geom.GeometryLocation;
import org.locationtech.jtstest.testbuilder.geom.GeometryVertexMover;


/**
 * @version 1.7
 */
public class EditVertexTool 
extends IndicatorTool 
{
  private static EditVertexTool instance = null;

  //Point2D currentIndicatorLoc = null;
  Coordinate currentVertexLoc = null;
  
  private Coordinate selectedVertexLocation = null;
  private Coordinate[] adjVertices = null;

  private final CircularStringInsertGesture circularStringInsert =
      new CircularStringInsertGesture();

  public static EditVertexTool getInstance() {
    if (instance == null)
      instance = new EditVertexTool();
    return instance;
  }

  private EditVertexTool() {
    super(AppCursors.EDIT_VERTEX);
  }

  CircularStringInsertGesture circularStringInsertGesture() {
    return circularStringInsert;
  }

  @Override
  public void activate(GeometryEditPanel panel) {
    super.activate(panel);
    if (panel != null) {
      panel.setFocusable(true);
      panel.addKeyListener(this);
      panel.requestFocusInWindow();
    }
  }

  @Override
  public void deactivate() {
    cancelCircularStringInsert();
    if (panel() != null) {
      panel().removeKeyListener(this);
    }
    super.deactivate();
  }

  @Override
  public void keyPressed(KeyEvent e) {
    if (CircularStringInsertGesture.isCancelKey(e.getKeyCode())) {
      e.consume();
      cancelCircularStringInsert();
    }
  }

  public void mousePressed(MouseEvent e) {
    if (circularStringInsert.isPending()) {
      currentVertexLoc = toModelSnapped(e.getPoint());
      circularStringInsert.setPreview(currentVertexLoc);
      return;
    }
  	currentVertexLoc = null;
    if (SwingUtilities.isRightMouseButton(e))
      return;
    
    // initiate moving a vertex
    Coordinate mousePtModel = toModelCoordinate(e.getPoint());
    double tolModel = getModelSnapTolerance();

    selectedVertexLocation = geomModel().locateVertexPt(mousePtModel, tolModel);
    if (selectedVertexLocation != null) {
      adjVertices = geomModel().findAdjacentVertices(selectedVertexLocation);
      currentVertexLoc = selectedVertexLocation;
      redrawIndicator();
    }
  }

  public void mouseReleased(MouseEvent e) {
    if (circularStringInsert.isPending()) {
      return;
    }
    if (SwingUtilities.isRightMouseButton(e))
      return;
    
    clearIndicator();
    // finish the move of the vertex
    if (selectedVertexLocation != null) {
      Coordinate newLoc = toModelSnapped(e.getPoint());
      geomModel().moveVertex(selectedVertexLocation, newLoc);
    }
  }

  public void mouseDragged(MouseEvent e) {
    if (circularStringInsert.isPending()) {
      currentVertexLoc = toModelSnapped(e.getPoint());
      circularStringInsert.setPreview(currentVertexLoc);
      redrawIndicator();
      return;
    }
  	currentVertexLoc = toModelSnapped(e.getPoint());
    if (selectedVertexLocation != null)
      redrawIndicator();
  }

  @Override
  public void mouseMoved(MouseEvent e) {
    super.mouseMoved(e);
    if (circularStringInsert.isPending()) {
      currentVertexLoc = toModelSnapped(e.getPoint());
      circularStringInsert.setPreview(currentVertexLoc);
      redrawIndicator();
    }
  }

  public void mouseClicked(MouseEvent e) {
    if (circularStringInsert.isPending()) {
      commitCircularStringInsert(toModelSnapped(e.getPoint()));
      return;
    }

    if (! SwingUtilities.isRightMouseButton(e))
      return;

    clearIndicator();
    selectedVertexLocation = null;
    currentVertexLoc = null;
    adjVertices = null;
    
    Coordinate mousePtModel = toModelCoordinate(e.getPoint());
    double tolModel = getModelSnapTolerance();

    boolean isMove = ! e.isControlDown();
    if (isMove) {
      GeometryLocation geomLoc = geomModel().locateNonVertexPoint(mousePtModel, tolModel);
      //System.out.println("Testing: insert vertex at " + geomLoc);
      if (geomLoc != null) {
        if (geomLoc.isCircularStringComponent()) {
          startCircularStringInsert(geomLoc);
          return;
        }
        geomModel().setGeometry(geomLoc.insert());
      }
    }
    else {  // is a delete
      GeometryLocation geomLoc = geomModel().locateVertex(mousePtModel, tolModel);
      //System.out.println("Testing: delete vertex at " + geomLoc);
      if (geomLoc != null) {
        geomModel().setGeometry(geomLoc.delete());
      }
    }
  }

  /**
   * First click on a CircularString: red overlay only. A stays the
   * live odd ISO/IEC 13249-3 CS. Overlay is not written to B.
   */
  private void startCircularStringInsert(GeometryLocation geomLoc) {
    if (!circularStringInsert.begin(geomLoc)) {
      return;
    }
    currentVertexLoc = geomLoc.getCoordinate();
    if (panel() != null) {
      panel().requestFocusInWindow();
    }
    redrawIndicator();
  }

  private void commitCircularStringInsert(Coordinate second) {
    Geometry edited = circularStringInsert.commit(second);
    if (edited != null) {
      clearIndicator();
      geomModel().setGeometry(edited);
      currentVertexLoc = null;
      return;
    }
    if (circularStringInsert.isPending()) {
      circularStringInsert.setPreview(second);
      currentVertexLoc = second;
      redrawIndicator();
    }
  }

  private void cancelCircularStringInsert() {
    if (!circularStringInsert.isPending()) {
      return;
    }
    circularStringInsert.cancel();
    clearIndicator();
    currentVertexLoc = null;
  }

  protected Shape getShape() 
  {
    if (circularStringInsert.isPending()) {
      return circularStringInsertOverlayShape();
    }
  	GeometryCollectionShape ind = new GeometryCollectionShape();
  	Point2D currentIndicatorLoc = toView(currentVertexLoc);
  	ind.add(getIndicatorCircle(currentIndicatorLoc));
    Shape curvePreview = circularStringPreviewShape();
    if (curvePreview != null) {
      ind.add(curvePreview);
      return ind;
    }
  	if (adjVertices != null) {
  		for (int i = 0; i < adjVertices.length; i++) {
  	    GeneralPath line = new GeneralPath();
  	    line.moveTo((float) currentIndicatorLoc.getX(), (float) currentIndicatorLoc.getY());
  	    Point2D pt = toView(adjVertices[i]);
  	    line.lineTo((float) pt.getX(), (float) pt.getY());
  	    ind.add(line);
  		}
  	}
  	return ind;
  }

  /**
   * Red overlay for the in-progress pair. IndicatorTool paints
   * {@link CircularStringInsertGesture#overlayColor} (BAND red). Not B.
   */
  private Shape circularStringInsertOverlayShape() {
    Coordinate first = circularStringInsert.getFirst();
    Coordinate second = currentVertexLoc != null
        ? currentVertexLoc
        : circularStringInsert.getPreviewSecond();
    if (first == null || second == null) {
      return null;
    }
    GeometryCollectionShape ind = new GeometryCollectionShape();
    Point2D a = toView(first);
    Point2D b = toView(second);
    ind.add(getIndicatorCircle(a));
    ind.add(getIndicatorCircle(b));
    GeneralPath line = new GeneralPath();
    line.moveTo((float) a.getX(), (float) a.getY());
    line.lineTo((float) b.getX(), (float) b.getY());
    ind.add(line);
    return ind;
  }

  /**
   * Rubber-band the whole CircularString as arcs (not chords to adjacent
   * controls). Same move as {@link #mouseReleased}.
   */
  private Shape circularStringPreviewShape() {
    if (selectedVertexLocation == null || currentVertexLoc == null) {
      return null;
    }
    Geometry g = geomModel().getGeometry();
    if (g == null) {
      return null;
    }
    String type = g.getGeometryType();
    if (!"CircularString".equals(type) && !"CompoundCurve".equals(type)) {
      return null;
    }
    Geometry preview = GeometryVertexMover.move(g, selectedVertexLocation, currentVertexLoc);
    CurveShapeWriter writer = new CurveShapeWriter(new PointTransformation() {
      public void transform(Coordinate src, Point2D dest) {
        Point2D view = toView(src);
        dest.setLocation(view.getX(), view.getY());
      }
    });
    return writer.toShape(preview);
  }

  private static final double IND_CIRCLE_RADIUS = 10.0;

  protected Shape getIndicatorCircle(Point2D p) {
    return new Ellipse2D.Double(p.getX() - (IND_CIRCLE_RADIUS / 2), p.getY()
        - (IND_CIRCLE_RADIUS / 2), IND_CIRCLE_RADIUS, IND_CIRCLE_RADIUS);
  }

}

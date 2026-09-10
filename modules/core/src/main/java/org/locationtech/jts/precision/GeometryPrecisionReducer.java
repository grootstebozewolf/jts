/*
 * Copyright (c) 2016 Martin Davis.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * and Eclipse Distribution License v. 1.0 which accompanies this distribution.
 * The Eclipse Public License is available at http://www.eclipse.org/legal/epl-v20.html
 * and the Eclipse Distribution License is available at
 *
 * http://www.eclipse.org/org/documents/edl-v10.php.
 */
package org.locationtech.jts.precision;

import org.locationtech.jts.densify.Densifier;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.CoordinateSequence;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.PrecisionModel;
import org.locationtech.jts.geom.util.GeometryEditor;

/**
 * Reduces the precision of a {@link Geometry}
 * according to the supplied {@link PrecisionModel},
 * ensuring that the result is valid (unless specified otherwise).
 * <p>
 * By default the geometry precision model is not changed.
 * This can be overridden by using {@link #setChangePrecisionModel(boolean)}.
 *  
 * <h4>Topological Precision Reduction</h4>
 * 
 * The default mode of operation ensures the reduced result is topologically valid
 * (i.e. {@link Geometry#isValid()} is true).
 * To ensure this polygonal geometry is reduced in a topologically valid fashion
 * (technically, by using snap-rounding).
 * Note that this may change polygonal geometry structure
 * (e.g. two polygons separated by a distance below the specified precision
 * will be merged into a single polygon).
 * Duplicate vertices are removed.
 * This mode is invoked by the static method {@link #reduce(Geometry, PrecisionModel)}.
 * <p>
 * Normally, collapsed linear components (e.g. lines collapsing to a point) 
 * are not included in the result. 
 * This behavior can be changed 
 * by setting {@link #setRemoveCollapsedComponents(boolean)} to <code>false</code>,
 * or by using the static method {@link #reduceKeepCollapsed(Geometry, PrecisionModel)}.
 * <p>
 * In general input must be valid geometry, or an {@link IllegalArgumentException} 
 * will be thrown. However if the invalidity is "mild" or very small then it
 * may be eliminated by precision reduction.
 * 
 * 
 * <h4>Pointwise Precision Reduction</h4>
 * 
 * Alternatively, geometry can be reduced pointwise by using {@link #setPointwise(boolean)}.
 * Linear and point geometry are always reduced pointwise (i.e. without further change to 
 * topology or structure), since this does not change validity.
 * Invalid inputs are allowed.
 * Duplicate vertices are preserved.
 * Collapsed components are always included in the result.
 * The result geometry may be invalid.
 * <p>
 * This mode is invoked by the static method {@link #reducePointwise(Geometry, PrecisionModel)}.
 *
 * @version 1.12
 */
public class GeometryPrecisionReducer
{
	/**
	 * Reduces precision of a geometry, ensuring output geometry is valid.
   * Collapsed linear and polygonal components are removed.
   * Duplicate vertices are removed. 
   * The geometry precision model is not changed.
   * <p>
   * Invalid input geometry may cause an error, 
   * unless the invalidity is below the scale of the precision reduction.
	 * 
	 * @param g the geometry to reduce
	 * @param precModel the precision model to use
	 * @return the reduced geometry
   * @throws IllegalArgumentException if the reduction fails due to invalid input geometry
	 */
	public static Geometry reduce(Geometry g, PrecisionModel precModel)
	{
    Geometry curveReduced = reduceCurveIfPresent(g, precModel);
    if (curveReduced != null) {
      return curveReduced;
    }
		GeometryPrecisionReducer reducer = new GeometryPrecisionReducer(precModel);
		return reducer.reduce(g);
	}

  /**
   * PRC-SN (#1195): snap CircularString controls; keep CircularString when
   * the snapped circumcentre also lies on the grid, otherwise densify then
   * reduce. Detection by geometry type name so core does not import jts-curve.
   */
  private static Geometry reduceCurveIfPresent(Geometry g, PrecisionModel pm) {
    if (g == null || !"CircularString".equals(g.getGeometryType())) {
      return null;
    }
    if (!(g instanceof LineString) || g.getNumPoints() < 3) {
      return null;
    }
    LineString cs = (LineString) g;
    CoordinateSequence seq = cs.getCoordinateSequence();
    // Only single-arc 3-control case for preserve path
    if (cs.getNumPoints() != 3) {
      Geometry dens = Densifier.densify(g, densifyTol(g));
      GeometryPrecisionReducer reducer = new GeometryPrecisionReducer(pm);
      return reducer.reduce(dens);
    }
    Coordinate[] snapped = new Coordinate[3];
    for (int i = 0; i < 3; i++) {
      snapped[i] = seq.getCoordinate(i).copy();
      pm.makePrecise(snapped[i]);
    }
    if (isCollinear(snapped[0], snapped[1], snapped[2])) {
      GeometryPrecisionReducer reducer = new GeometryPrecisionReducer(pm);
      return reducer.reduce(Densifier.densify(g, densifyTol(g)));
    }
    if (!isCentreOnGrid(snapped[0], snapped[1], snapped[2], pm)) {
      GeometryPrecisionReducer reducer = new GeometryPrecisionReducer(pm);
      return reducer.reduce(Densifier.densify(g, densifyTol(g)));
    }
    try {
      java.lang.reflect.Method m = g.getFactory().getClass()
          .getMethod("createCircularString", CoordinateSequence.class);
      CoordinateSequence newSeq = g.getFactory().getCoordinateSequenceFactory()
          .create(snapped);
      Object out = m.invoke(g.getFactory(), newSeq);
      if (out instanceof Geometry) {
        return (Geometry) out;
      }
    }
    catch (ReflectiveOperationException ex) {
      // rebuild via same class constructor
      try {
        java.lang.reflect.Constructor<?> ctor = g.getClass().getConstructor(
            CoordinateSequence.class, GeometryFactory.class);
        CoordinateSequence newSeq = g.getFactory().getCoordinateSequenceFactory()
            .create(snapped);
        return (Geometry) ctor.newInstance(newSeq, g.getFactory());
      }
      catch (ReflectiveOperationException ex2) {
        return null;
      }
    }
    return null;
  }

  private static double densifyTol(Geometry g) {
    Envelope env = g.getEnvelopeInternal();
    double extent = Math.max(env.getWidth(), env.getHeight());
    return (extent > 0.0 ? extent : 1.0) * 1.0e-6;
  }

  private static boolean isCollinear(Coordinate a, Coordinate b, Coordinate c) {
    return Math.abs((b.x - a.x) * (c.y - a.y) - (b.y - a.y) * (c.x - a.x)) < 1.0e-15;
  }

  private static boolean isCentreOnGrid(Coordinate a, Coordinate b, Coordinate c,
      PrecisionModel pm) {
    double det = 2 * (a.x * (b.y - c.y) + b.x * (c.y - a.y) + c.x * (a.y - b.y));
    if (det == 0.0) {
      return false;
    }
    double a2 = a.x * a.x + a.y * a.y;
    double b2 = b.x * b.x + b.y * b.y;
    double c2 = c.x * c.x + c.y * c.y;
    double cx = (a2 * (b.y - c.y) + b2 * (c.y - a.y) + c2 * (a.y - b.y)) / det;
    double cy = (a2 * (c.x - b.x) + b2 * (a.x - c.x) + c2 * (b.x - a.x)) / det;
    Coordinate centre = new Coordinate(cx, cy);
    Coordinate snapped = centre.copy();
    pm.makePrecise(snapped);
    return centre.equals2D(snapped);
  }
	
  /**
   * Reduces precision of a geometry, ensuring output polygonal geometry is valid,
   * and preserving collapsed linear elements.
   * Duplicate vertices are removed.
   * The geometry precision model is not changed.
   * <p>
   * Invalid input geometry may cause an error, 
   * unless the invalidity is below the scale of the precision reduction.
   * 
   * @param g the geometry to reduce
   * @param precModel the precision model to use
   * @return the reduced geometry
   * @throws IllegalArgumentException if the reduction fails due to invalid input geometry
   */
  public static Geometry reduceKeepCollapsed(Geometry geom, PrecisionModel pm) {
    GeometryPrecisionReducer reducer = new GeometryPrecisionReducer(pm);
    reducer.setRemoveCollapsedComponents(false);
    return reducer.reduce(geom);
  }
  
	/**
	 * Reduce precision of a geometry in a pointwise way. 
   * All input geometry elements are preserved in the output, 
   * including invalid polygons and collapsed polygons and linestrings.
   * The output may not be valid, due to collapse or self-intersection.
   * Duplicate vertices are not removed.
   * The geometry precision model is not changed.
   * <p>
   * Invalid input geometry is allowed.
	 * 
	 * @param g the geometry to reduce
	 * @param precModel the precision model to use
	 * @return the reduced geometry
	 */
	public static Geometry reducePointwise(Geometry g, PrecisionModel precModel)
	{
		GeometryPrecisionReducer reducer = new GeometryPrecisionReducer(precModel);
		reducer.setPointwise(true);
		return reducer.reduce(g);
	}
	
  private PrecisionModel targetPM;
  private boolean removeCollapsed = true;
  private boolean changePrecisionModel = false;
  private boolean isPointwise = false;

  public GeometryPrecisionReducer(PrecisionModel pm)
  {
    targetPM = pm;
  }

  /**
   * Sets whether the reduction will result in collapsed components
   * being removed completely, or simply being collapsed to an (invalid)
   * Geometry of the same type.
   * The default is to remove collapsed components.
   *
   * @param removeCollapsed if <code>true</code> collapsed components will be removed
   */
  public void setRemoveCollapsedComponents(boolean removeCollapsed)
  {
    this.removeCollapsed = removeCollapsed;
  }

  /**
   * Sets whether the {@link PrecisionModel} of the new reduced Geometry
   * will be changed to be the {@link PrecisionModel} supplied to
   * specify the precision reduction.
   * <p>  
   * The default is to <b>not</b> change the precision model
   *
   * @param changePrecisionModel if <code>true</code> the precision model of the created Geometry will be the
   * the precisionModel supplied in the constructor.
   */
  public void setChangePrecisionModel(boolean changePrecisionModel)
  {
    this.changePrecisionModel = changePrecisionModel;
  }

  /**
   * Sets whether the precision reduction will be done 
   * in pointwise fashion only.  
   * Pointwise precision reduction reduces the precision
   * of the individual coordinates only, but does
   * not attempt to recreate valid topology.
   * This is only relevant for geometries containing polygonal components.
   * 
   * @param isPointwise if reduction should be done pointwise only
   */
  public void setPointwise(boolean isPointwise)
  {
    this.isPointwise = isPointwise;
  }

  /**
   * Reduces the precision of a geometry, 
   * according to the specified strategy of this reducer.
   * 
   * @param geom the geometry to reduce
   * @return the precision-reduced geometry
   * @throws IllegalArgumentException if the reduction fails due to invalid input geometry is invalid
   */
  public Geometry reduce(Geometry geom)
  {
    Geometry reduced;
    if (isPointwise) {
      reduced = PointwisePrecisionReducerTransformer.reduce(geom, targetPM);
    }
    else {
      reduced = PrecisionReducerTransformer.reduce(geom, targetPM, removeCollapsed);
    }
    
    // TODO: incorporate this in the Transformer above
    if (changePrecisionModel) {
      return changePM(reduced, targetPM);
    }
    return reduced;
  }
  
  /**
   * Duplicates a geometry to one that uses a different PrecisionModel,
   * without changing any coordinate values.
   * 
   * @param geom the geometry to duplicate
   * @param newPM the precision model to use
   * @return the geometry value with a new precision model
   */
  private Geometry changePM(Geometry geom, PrecisionModel newPM)
  {
  	GeometryEditor geomEditor = createEditor(geom.getFactory(), newPM);
  	// this operation changes the PM for the entire geometry tree
  	return geomEditor.edit(geom, new GeometryEditor.NoOpGeometryOperation());
  }
  
  private GeometryEditor createEditor(GeometryFactory geomFactory, PrecisionModel newPM)
  {
    // no need to change if precision model is the same
  	if (geomFactory.getPrecisionModel() == newPM)
  		return new GeometryEditor();
  	// otherwise create a geometry editor which changes PrecisionModel
  	GeometryFactory newFactory = createFactory(geomFactory, newPM);
  	GeometryEditor geomEdit = new GeometryEditor(newFactory);
    return geomEdit;
  }
  
  private GeometryFactory createFactory(GeometryFactory inputFactory, PrecisionModel pm)
  {
    GeometryFactory newFactory 
  	= new GeometryFactory(pm, 
  			inputFactory.getSRID(),
  			inputFactory.getCoordinateSequenceFactory());
    return newFactory;
  }
  
}

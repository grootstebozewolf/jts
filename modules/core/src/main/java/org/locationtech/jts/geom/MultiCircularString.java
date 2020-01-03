/*
 * Copyright (c) 2016 Vivid Solutions.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Eclipse Distribution License v. 1.0 which accompanies this distribution.
 * The Eclipse Public License is available at http://www.eclipse.org/legal/epl-v10.html
 * and the Eclipse Distribution License is available at
 *
 * http://www.eclipse.org/org/documents/edl-v10.php.
 */
package org.locationtech.jts.geom;

import org.locationtech.jts.operation.BoundaryOp;

/**
 * Models a collection of {@link CircularString}s.
 * <p>
 * Any collection of CircularStrings is a valid MultiCircularString.
 *
 *@version 1.7
 */
public class MultiCircularString 
	extends GeometryCollection
	implements Arc
	{
  private static final long serialVersionUID = 8166665132445433741L;
  /**
   *  Constructs a <code>MultiCircularString</code>.
   *
   *@param  CircularStrings     the <code>CircularString</code>s for this <code>MultiCircularString</code>
   *      , or <code>null</code> or an empty array to create the empty geometry.
   *      Elements may be empty <code>CircularString</code>s, but not <code>null</code>
   *      s.
   *@param  precisionModel  the specification of the grid of allowable points
   *      for this <code>MultiCircularString</code>
   *@param  SRID            the ID of the Spatial Reference System used by this
   *      <code>MultiCircularString</code>
   * @deprecated Use GeometryFactory instead
   */
  public MultiCircularString(CircularString[] CircularStrings, PrecisionModel precisionModel, int SRID) {
    super(CircularStrings, new GeometryFactory(precisionModel, SRID));
  }

  /**
   * @param CircularStrings
   *            the <code>CircularString</code>s for this <code>MultiCircularString</code>,
   *            or <code>null</code> or an empty array to create the empty
   *            geometry. Elements may be empty <code>CircularString</code>s,
   *            but not <code>null</code>s.
   */
  public MultiCircularString(CircularString[] CircularStrings, GeometryFactory factory) {
    super(CircularStrings, factory);
  }

  public int getDimension() {
    return 1;
  }

  public int getBoundaryDimension() {
    if (isClosed()) {
      return Dimension.FALSE;
    }
    return 0;
  }

  public String getGeometryType() {
    return "MultiCircularString";
  }

  public boolean isClosed() {
    if (isEmpty()) {
      return false;
    }
    for (int i = 0; i < geometries.length; i++) {
      if (!((CircularString) geometries[i]).isClosed()) {
        return false;
      }
    }
    return true;
  }

  /**
   * Gets the boundary of this geometry.
   * The boundary of a Circularal geometry is always a zero-dimensional geometry (which may be empty).
   *
   * @return the boundary geometry
   * @see Geometry#getBoundary
   */
  public Geometry getBoundary()
  {
    return (new BoundaryOp(this)).getBoundary();
  }

  /**
   * Creates a {@link MultiCircularString} in the reverse
   * order to this object.
   * Both the order of the component CircularStrings
   * and the order of their coordinate sequences
   * are reversed.
   *
   * @return a {@link MultiCircularString} in the reverse order
   */
  public Geometry reverse()
  {
    int nCirculars = geometries.length;
    CircularString[] revCirculars = new CircularString[nCirculars];
    for (int i = 0; i < geometries.length; i++) {
      revCirculars[nCirculars - 1 - i] = (CircularString)geometries[i].reverse();
    }
    return getFactory().createMultiCircularString(revCirculars);
  }
  
  protected MultiCircularString copyInternal() {
    CircularString[] CircularStrings = new CircularString[this.geometries.length];
    for (int i = 0; i < CircularStrings.length; i++) {
      CircularStrings[i] = (CircularString) this.geometries[i].copy();
    }
    return new MultiCircularString(CircularStrings, factory);
  }

  public boolean equalsExact(Geometry other, double tolerance) {
    if (!isEquivalentClass(other)) {
      return false;
    }
    return super.equalsExact(other, tolerance);
  }

  protected int getSortIndex() {
    return Geometry.SORTINDEX_MULTICircularSTRING;
  }
}


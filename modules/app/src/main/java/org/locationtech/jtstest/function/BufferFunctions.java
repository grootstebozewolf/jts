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
package org.locationtech.jtstest.function;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.geom.util.GeometryMapper;
import org.locationtech.jts.geom.util.GeometryMapper.MapOp;
import org.locationtech.jts.geom.util.LinearComponentExtracter;
import org.locationtech.jts.noding.SegmentString;
import org.locationtech.jts.operation.buffer.BufferCurveSetBuilder;
import org.locationtech.jts.operation.buffer.BufferInputLineSimplifier;
import org.locationtech.jts.operation.buffer.BufferOp;
import org.locationtech.jts.operation.buffer.BufferParameters;
import org.locationtech.jts.operation.buffer.OffsetCurveBuilder;
import org.locationtech.jts.operation.buffer.VariableBuffer;
import org.locationtech.jts.operation.buffer.validate.BufferResultValidator;
import org.locationtech.jtstest.geomfunction.Metadata;


public class BufferFunctions {
	
	public static String bufferDescription = "Buffers a geometry by a distance";
	
	@Metadata(description="Buffer a geometry by a distance")
  public static Geometry buffer(Geometry g, double distance)    {   return g.buffer(distance);  }

	public static Geometry bufferWithParams(Geometry g, 
	    Double distance,
	    @Metadata(title="Quadrant Segs")
			Integer quadrantSegments, 
      @Metadata(title="Cap style")
			Integer capStyle, 
      @Metadata(title="Join style")
			Integer joinStyle, 
      @Metadata(title="Mitre limit")
			Double mitreLimit)	
	{
	    double dist = 0;
	    if (distance != null) dist = distance.doubleValue();
	    
	    BufferParameters bufParams = new BufferParameters();
	    if (quadrantSegments != null)	bufParams.setQuadrantSegments(quadrantSegments.intValue());
	    if (capStyle != null)	bufParams.setEndCapStyle(capStyle.intValue());
	    if (joinStyle != null) 	bufParams.setJoinStyle(joinStyle.intValue());
	    if (mitreLimit != null) 	bufParams.setMitreLimit(mitreLimit.doubleValue());
	    
	    return BufferOp.bufferOp(g, dist, bufParams);
	}
	
	public static Geometry bufferWithSimplify(Geometry g, Double distance,
	    @Metadata(title="Simplify factor")
			Double simplifyFactor)	
	{
	    double dist = 0;
	    if (distance != null) dist = distance.doubleValue();
	    
	    BufferParameters bufParams = new BufferParameters();
	    if (simplifyFactor != null)	bufParams.setSimplifyFactor(simplifyFactor.doubleValue());
	    
	    return BufferOp.bufferOp(g, dist, bufParams);
	}
	
	public static Geometry bufferCurve(Geometry g, double distance)	
	{		
    return buildCurveSet(g, distance, new BufferParameters());
	}
	
	public static Geometry bufferCurveWithParams(Geometry g, 
      Double distance,
      @Metadata(title="Quadrant Segs")
      Integer quadrantSegments, 
      @Metadata(title="Cap style")
      Integer capStyle, 
      @Metadata(title="Join style")
      Integer joinStyle, 
      @Metadata(title="Mitre limit")
      Double mitreLimit)  	
	{
    double dist = 0;
    if (distance != null) dist = distance.doubleValue();
    
    BufferParameters bufParams = new BufferParameters();
    if (quadrantSegments != null)	bufParams.setQuadrantSegments(quadrantSegments.intValue());
    if (capStyle != null)	bufParams.setEndCapStyle(capStyle.intValue());
    if (joinStyle != null) 	bufParams.setJoinStyle(joinStyle.intValue());
    if (mitreLimit != null) 	bufParams.setMitreLimit(mitreLimit.doubleValue());

    Geometry input = linearizeForBuffer(g, dist);
    return buildCurveSet(input, dist, bufParams);
	}

  /**
   * The buffer curve builder is chord-based, so a curve must be densified
   * first. The tolerance is tied to the buffer distance rather than chosen:
   * deviation that is small relative to the offset cannot show up in the
   * result. Use {@code Curve.toLinear} to linearise at a tolerance of your own.
   */
  private static Geometry linearizeForBuffer(Geometry g, double bufferDistance) {
    double tol = Math.max(0.001, Math.abs(bufferDistance) / 100.0);
    return CurveFunctions.linearize(g, tol);
  }

  private static Geometry buildCurveSet(Geometry g, double dist, BufferParameters bufParams)
  {
    // --- now construct curve
    BufferCurveSetBuilder ocsb = new BufferCurveSetBuilder(g, dist, 
        g.getFactory().getPrecisionModel(),
        bufParams);
    List curves = ocsb.getCurves();
    
    List lines = new ArrayList();
    for (Iterator i = curves.iterator(); i.hasNext(); ) {
    	SegmentString ss = (SegmentString) i.next();
    	Coordinate[] pts = ss.getCoordinates();
    	lines.add(g.getFactory().createLineString(pts));
    }
    Geometry curve = g.getFactory().buildGeometry(lines);
    return curve;
  }

	public static Geometry bufferLineSimplifier(Geometry g, double distance)	
	{   
    return buildBufferLineSimplifiedSet(g, distance);
	}

  private static Geometry buildBufferLineSimplifiedSet(Geometry g, double distance)
  {
    List simpLines = new ArrayList();

    List lines = new ArrayList();
    LinearComponentExtracter.getLines(g, lines);
    for (Iterator i = lines.iterator(); i.hasNext(); ) {
    	LineString line = (LineString) i.next();
    	Coordinate[] pts = line.getCoordinates();
    	simpLines.add(g.getFactory().createLineString(BufferInputLineSimplifier.simplify(pts, distance)));
    }
    Geometry simpGeom = g.getFactory().buildGeometry(simpLines);
    return simpGeom;
  }

  /**
   * Validates against the same linearised geometry the buffer effectively used.
   * g.buffer() is arc-aware (CRV-OPS densifies the arc), but
   * BufferResultValidator measures from coordinates -- the chords -- and the
   * arc of CIRCULARSTRING (1 0, 1 1, 0 1) bulges 0.207 outside its chords, so
   * the validator rejected a correct 0.1 buffer as "too small (0.066)". One
   * function was comparing two different geometries and blaming the answer.
   */
  public static Geometry bufferValidated(Geometry g, double distance)
  {
    Geometry input = CurveFunctions.linearizeForOps(g);
    Geometry buf = input.buffer(distance);
    String errMsg = BufferResultValidator.isValidMsg(input, distance, buf);
    if (errMsg != null)
      throw new IllegalStateException("Buffer Validation error: " + errMsg);
    return buf;
  }

  public static Geometry bufferValidatedGeom(Geometry g, double distance)
  {
    Geometry input = CurveFunctions.linearizeForOps(g);
    Geometry buf = input.buffer(distance);
    BufferResultValidator validator = new BufferResultValidator(input, distance, buf);
    boolean isValid = validator.isValid();
    return validator.getErrorIndicator();
  }

  public static Geometry singleSidedBufferCurve(Geometry geom, double distance) {
    BufferParameters bufParam = new BufferParameters();
    bufParam.setSingleSided(true);
    OffsetCurveBuilder ocb = new OffsetCurveBuilder(
        geom.getFactory().getPrecisionModel(), bufParam
        );
    Coordinate[] pts = ocb.getLineCurve(geom.getCoordinates(), distance);
    Geometry curve = geom.getFactory().createLineString(pts);
    return curve;
  }
  
  public static Geometry singleSidedBuffer(Geometry geom, double distance) {
    BufferParameters bufParams = new BufferParameters();
    bufParams.setSingleSided(true);
    return BufferOp.bufferOp(geom, distance, bufParams);
  }
  
  public static Geometry bufferEach(Geometry g, final double distance)
  {
    return GeometryMapper.map(g, new MapOp() {

      public Geometry map(Geometry g)
      {
        return g.buffer(distance);
      }
      
    });
  }

  public static Geometry bufferAndInverse(Geometry g, double distance) {
    return g.buffer(distance).buffer(-distance);
  }
  
  @Metadata(description="Buffer a line by a distance varying along the line")
  public static Geometry variableBuffer(Geometry line,
      @Metadata(title="Start distance")
      double startDist,
      @Metadata(title="End distance")
      double endDist) {
    if (line instanceof Polygon) {
      line = ((Polygon) line).getExteriorRing();
    }
    return VariableBuffer.buffer(line, startDist, endDist);
  }
  
  @Metadata(description="Buffer a line by a distance varying along the line, with distances for start/end and the middle")
  public static Geometry variableBufferMid(Geometry line,
      @Metadata(title="Start distance")
      double startDist,
      @Metadata(title="Middle distance")
      double midDist)  
  {
    if (line instanceof Polygon) {
      line = ((Polygon) line).getExteriorRing();
    }
    return VariableBuffer.buffer(line, startDist, midDist, startDist);
  }
  
  public static Geometry bufferRadius(Geometry radiusLine) {
    double distance = radiusLine.getLength();
    Coordinate centrePt = radiusLine.getCoordinate();
    Point centre = radiusLine.getFactory().createPoint(centrePt);
    return centre.buffer(distance);
  }
}

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
package org.locationtech.jts.triangulate;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.CoordinateArrays;
import org.locationtech.jts.geom.CoordinateList;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryCollection;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.MultiLineString;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.index.kdtree.KdNode;
import org.locationtech.jts.index.kdtree.KdTree;
import org.locationtech.jts.triangulate.quadedge.QuadEdgeSubdivision;
import org.locationtech.jts.triangulate.quadedge.Vertex;


/**
 * A utility class which creates Delaunay Triangulations
 * from collections of points and extract the resulting 
 * triangulation edges or triangles as geometries. 
 * 
 * @author Martin Davis
 *
 */
public class DelaunayTriangulationBuilder 
{
	/**
	 * Extracts the unique {@link Coordinate}s from the given {@link Geometry}.
	 * Uses exact equality (tolerance = 0).
	 * @param geom the geometry to extract from
	 * @return a List of the unique Coordinates
	 */
	public static CoordinateList extractUniqueCoordinates(Geometry geom)
	{
		if (geom == null)
			return new CoordinateList();
		
		Coordinate[] coords = geom.getCoordinates();
		return unique(coords, 0.0);
	}
	
	public static CoordinateList unique(Coordinate[] coords)
	{
		return unique(coords, 0.0);
	}
	
	/**
	 * Extracts unique coordinates using the given tolerance for snapping near-coincident points.
	 * When tolerance > 0, uses KdTree to deduplicate sites within tolerance.
	 * This helper can be used directly for testing tolerance-based deduplication on Coordinate arrays.
	 * @param coords the coordinates
	 * @param tolerance the tolerance distance; 0.0 means exact uniqueness
	 * @return CoordinateList of unique (snapped) sites
	 */
	public static CoordinateList unique(Coordinate[] coords, double tolerance)
	{
		if (coords == null || coords.length == 0)
			return new CoordinateList();
		if (tolerance <= 0.0) {
			Coordinate[] coordsCopy = CoordinateArrays.copyDeep(coords);
			Arrays.sort(coordsCopy);
			CoordinateList coordList = new CoordinateList(coordsCopy, false);
			return coordList;
		}
		// tolerance > 0: use KdTree to snap near-coincident points
		KdTree kdTree = new KdTree(tolerance);
		for (int i = 0; i < coords.length; i++) {
			kdTree.insert(coords[i]);
		}
		// query full extent to retrieve the distinct snapped nodes
		Envelope env = envelope(java.util.Arrays.asList(coords));
		env.expandBy(tolerance + 1.0); // ensure all nodes are covered
		List<KdNode> nodes = kdTree.query(env);
		CoordinateList coordList = new CoordinateList();
		for (KdNode node : nodes) {
			coordList.add(node.getCoordinate(), false);
		}
		return coordList;
	}
	
	/**
	 * Converts all {@link Coordinate}s in a collection to {@link Vertex}es.
	 * @param coords the coordinates to convert
	 * @return a List of Vertex objects
	 */
	public static List toVertices(Collection coords)
	{
		List verts = new ArrayList();
		for (Iterator i = coords.iterator(); i.hasNext(); ) {
			Coordinate coord = (Coordinate) i.next();
			verts.add(new Vertex(coord));
		}
		return verts;
	}

	/**
	 * Computes the {@link Envelope} of a collection of {@link Coordinate}s.
	 * 
	 * @param coords a List of Coordinates
	 * @return the envelope of the set of coordinates
	 */
	public static Envelope envelope(Collection coords)
	{
		Envelope env = new Envelope();
		for (Iterator i = coords.iterator(); i.hasNext(); ) {
			Coordinate coord = (Coordinate) i.next();
			env.expandToInclude(coord);
		}
		return env;
	}
	
	private Collection siteCoords;
	private double tolerance = 0.0;
	private QuadEdgeSubdivision subdiv = null;
	
	/**
	 * Creates a new triangulation builder.
	 *
	 */
	public DelaunayTriangulationBuilder()
	{
	}
	
	/**
	 * Sets the sites (vertices) which will be triangulated.
	 * All vertices of the given geometry will be used as sites.
	 * Duplicate removal (exact or tolerance-based) is performed in create().
	 * 
	 * @param geom the geometry from which the sites will be extracted.
	 */
	public void setSites(Geometry geom)
	{
		if (geom == null) {
			siteCoords = new ArrayList();
			return;
		}
		// store raw; dedup (considering tolerance) happens at create time
		Coordinate[] coords = geom.getCoordinates();
		siteCoords = new ArrayList(java.util.Arrays.asList(coords));
	}
	
	/**
	 * Sets the sites (vertices) which will be triangulated
	 * from a collection of {@link Coordinate}s.
	 * 
	 * @param coords a collection of Coordinates.
	 */
	public void setSites(Collection coords)
	{
		// store raw; dedup (considering tolerance) happens at create time
		siteCoords = (coords == null) ? new ArrayList() : new ArrayList(coords);
	}
	
	/**
	 * Sets the snapping tolerance which will be used
	 * to improved the robustness of the triangulation computation.
	 * A tolerance of 0.0 specifies that no snapping will take place.
	 * 
	 * @param tolerance the tolerance distance to use
	 */
	public void setTolerance(double tolerance)
	{
		this.tolerance = tolerance;
	}
	
	private void create()
	{
		if (subdiv != null) return;
		
		Coordinate[] coords = CoordinateArrays.toCoordinateArray(siteCoords);
		CoordinateList uniqueSiteCoords = unique(coords, tolerance);
		Envelope siteEnv = envelope(uniqueSiteCoords);
		List vertices = toVertices(uniqueSiteCoords);
		subdiv = new QuadEdgeSubdivision(siteEnv, tolerance);
		IncrementalDelaunayTriangulator triangulator = new IncrementalDelaunayTriangulator(subdiv);
		triangulator.insertSites(vertices);
	}
	
	/**
	 * Gets the {@link QuadEdgeSubdivision} which models the computed triangulation.
	 * 
	 * @return the subdivision containing the triangulation
	 */
	public QuadEdgeSubdivision getSubdivision()
	{
		create();
		return subdiv;
	}
	
	/**
	 * Gets the edges of the computed triangulation as a {@link MultiLineString}.
	 * 
	 * @param geomFact the geometry factory to use to create the output
	 * @return the edges of the triangulation
	 */
	public Geometry getEdges(GeometryFactory geomFact)
	{
		create();
		return subdiv.getEdges(geomFact);
	}
	
	/**
	 * Gets the faces of the computed triangulation as a {@link GeometryCollection} 
	 * of {@link Polygon}.
	 * 
	 * @param geomFact the geometry factory to use to create the output
	 * @return the faces of the triangulation
	 */
	public Geometry getTriangles(GeometryFactory geomFact)
	{
		create();
		return subdiv.getTriangles(geomFact);
	}
}

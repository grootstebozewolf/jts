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

package org.locationtech.jtstest.testbuilder.geom;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.curve.CircularString;
import org.locationtech.jts.geom.util.GeometryEditor;

public class GeometryVertexInserter 
{
  /**
   * LineString add-vertex: insert one coordinate. A {@link CircularString}
   * is ISO/IEC 13249-3: control count (WKT tokens) must stay odd and
   * &ge; 3. One-click +1 makes an even sequence. That door is closed —
   * use {@link #insertPair}. This method does not write a
   * CircularString (returns {@code geom} unchanged). Not #83 chord-mid.
   */
  public static Geometry insert(Geometry geom, 
      LineString line, 
      int segIndex,
      Coordinate newVertex)
  {
    if (line instanceof CircularString) {
      return geom;
    }
    GeometryEditor editor = new GeometryEditor();
    editor.setCopyUserData(true);
    return editor.edit(geom, new InsertVertexOperation(line, segIndex, newVertex));
  }

  /**
   * Two-click CircularString insert. Commits both click controls
   * (+2) so the ISO/IEC 13249-3 odd &ge; 3 WKT-token count stays odd.
   * Does not invent a chord midpoint. Refuses coincident consecutive
   * controls (degenerate arc triple). Returns {@code null} when the
   * pair must not be written.
   */
  public static Geometry insertPair(Geometry geom,
      LineString line,
      int segIndex,
      Coordinate first,
      Coordinate second)
  {
    if (!canInsertPair(line, segIndex, first, second)) {
      return null;
    }
    GeometryEditor editor = new GeometryEditor();
    editor.setCopyUserData(true);
    return editor.edit(geom, new InsertPairOperation(line, segIndex, first, second));
  }

  /**
   * Pair is writable only on a CircularString, only when both controls
   * are distinct from each other and from the neighbouring consecutive
   * controls, and only when the result count stays odd and &ge; 3.
   */
  public static boolean canInsertPair(LineString line, int segIndex,
      Coordinate first, Coordinate second)
  {
    if (!(line instanceof CircularString)) {
      return false;
    }
    if (first == null || second == null) {
      return false;
    }
    Coordinate[] coords = line.getCoordinates();
    if (segIndex < 0 || segIndex + 1 >= coords.length) {
      return false;
    }
    if (first.equals2D(coords[segIndex])) {
      return false;
    }
    if (first.equals2D(second)) {
      return false;
    }
    if (second.equals2D(coords[segIndex + 1])) {
      return false;
    }
    int n = coords.length + 2;
    return n >= 3 && (n % 2) == 1;
  }

  /**
   * Chord midpoint of {@code a} and {@code b}. #83 used this as an
   * invented control. That is not this door — callers must not insert
   * it unless the user clicked it.
   */
  public static Coordinate chordMidpoint(Coordinate a, Coordinate b)
  {
    return new Coordinate((a.x + b.x) / 2.0, (a.y + b.y) / 2.0);
  }
  
  private static class InsertVertexOperation
    extends GeometryEditor.CoordinateOperation
  {
    private LineString line;
    private int segIndex;
    private Coordinate newVertex;
    
    public InsertVertexOperation(LineString line, int segIndex, Coordinate newVertex)
    {
      this.line = line;
      this.segIndex = segIndex;
      this.newVertex = newVertex;
    }
    
    public Coordinate[] edit(Coordinate[] coords,
        Geometry geometry)
    {
      if (geometry != line) return coords;

      Coordinate[] newPts = new Coordinate[coords.length + 1];
      for (int i = 0; i < coords.length; i++) {
        int actualIndex = i > segIndex ? i + 1 : i;
        newPts[actualIndex] = (Coordinate) coords[i].clone();
      }
      newPts[segIndex + 1] = (Coordinate) newVertex.clone();
      return newPts;
    }
  }

  private static class InsertPairOperation
    extends GeometryEditor.CoordinateOperation
  {
    private LineString line;
    private int segIndex;
    private Coordinate first;
    private Coordinate second;

    public InsertPairOperation(LineString line, int segIndex,
        Coordinate first, Coordinate second)
    {
      this.line = line;
      this.segIndex = segIndex;
      this.first = first;
      this.second = second;
    }

    public Coordinate[] edit(Coordinate[] coords, Geometry geometry)
    {
      if (geometry != line) return coords;

      Coordinate[] newPts = new Coordinate[coords.length + 2];
      int j = 0;
      for (int i = 0; i <= segIndex; i++) {
        newPts[j++] = (Coordinate) coords[i].clone();
      }
      newPts[j++] = (Coordinate) first.clone();
      newPts[j++] = (Coordinate) second.clone();
      for (int i = segIndex + 1; i < coords.length; i++) {
        newPts[j++] = (Coordinate) coords[i].clone();
      }
      return newPts;
    }
  }
}

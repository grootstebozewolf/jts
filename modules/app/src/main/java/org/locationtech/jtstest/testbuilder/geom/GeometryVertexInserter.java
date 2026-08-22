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
  public static Geometry insert(Geometry geom, 
      LineString line, 
      int segIndex,
      Coordinate newVertex)
  {
    GeometryEditor editor = new GeometryEditor();
    editor.setCopyUserData(true);
    return editor.edit(geom, new InsertVertexOperation(line, segIndex, newVertex));
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
      if (geometry instanceof CircularString) {
        return insertOnCircularString(coords);
      }

      Coordinate[] newPts = new Coordinate[coords.length + 1];
      for (int i = 0; i < coords.length; i++) {
        int actualIndex = i > segIndex ? i + 1 : i;
        newPts[actualIndex] = (Coordinate) coords[i].clone();
      }
      newPts[segIndex + 1] = (Coordinate) newVertex.clone();
      return newPts;
    }

    /**
     * A CircularString control count must stay odd and >= 3. Inserting one
     * point (LineString behaviour) makes an even sequence that is not a
     * valid CircularString. Split the clicked arc (A, M, B) at the new
     * vertex C into (A, mid(A,C), C) and (C, mid(C,B), B): net +2.
     */
    private Coordinate[] insertOnCircularString(Coordinate[] coords)
    {
      int arcStart = (segIndex / 2) * 2;
      if (arcStart + 2 >= coords.length) {
        return coords;
      }
      Coordinate a = coords[arcStart];
      Coordinate b = coords[arcStart + 2];
      Coordinate c = (Coordinate) newVertex.clone();
      Coordinate m1 = midpoint(a, c);
      Coordinate m2 = midpoint(c, b);

      Coordinate[] newPts = new Coordinate[coords.length + 2];
      int j = 0;
      for (int i = 0; i < arcStart; i++) {
        newPts[j++] = (Coordinate) coords[i].clone();
      }
      newPts[j++] = (Coordinate) a.clone();
      newPts[j++] = m1;
      newPts[j++] = c;
      newPts[j++] = m2;
      newPts[j++] = (Coordinate) b.clone();
      for (int i = arcStart + 3; i < coords.length; i++) {
        newPts[j++] = (Coordinate) coords[i].clone();
      }
      return newPts;
    }

    private static Coordinate midpoint(Coordinate p0, Coordinate p1)
    {
      return new Coordinate((p0.x + p1.x) / 2.0, (p0.y + p1.y) / 2.0);
    }
  }

  
}

/*
 * Copyright (c) 2019 Martin Davis.
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

import java.util.ArrayList;
import java.util.List;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.CoordinateList;
import org.locationtech.jts.geom.CoordinateSequence;
import org.locationtech.jts.geom.CoordinateSequenceFilter;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryCollection;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.curve.CircularString;
import org.locationtech.jts.geom.curve.ClothoidSegment;
import org.locationtech.jts.geom.curve.CompoundCurve;
import org.locationtech.jts.geom.curve.CurvePolygon;

public class SegmentExtracter {

  public static Geometry extract(Geometry geom, Geometry aoi) {
    if (geom == null || aoi == null) {
      return null;
    }
    Envelope env = aoi.getEnvelopeInternal();
    List<Geometry> parts = new ArrayList<Geometry>();
    collect(geom, env, parts);
    if (parts.isEmpty()) {
      return geom.getFactory().createMultiLineString();
    }
    if (geom instanceof CompoundCurve) {
      Geometry packed = GeometryElementLocater.packCompoundCurveExtract(
          (CompoundCurve) geom, parts);
      if (packed != null) {
        return packed;
      }
    }
    if (parts.size() == 1) {
      return parts.get(0);
    }
    return geom.getFactory().buildGeometry(parts);
  }

  private static void collect(Geometry geom, Envelope aoi, List<Geometry> parts) {
    if (geom instanceof CompoundCurve) {
      CompoundCurve cc = (CompoundCurve) geom;
      for (int i = 0; i < cc.getNumMembers(); i++) {
        collect(cc.getMemberN(i), aoi, parts);
      }
      return;
    }
    if (geom instanceof CurvePolygon) {
      CurvePolygon cp = (CurvePolygon) geom;
      LineString shell = cp.getExteriorCurve();
      if (shell != null) {
        collect(shell, aoi, parts);
      }
      for (int i = 0; i < cp.getNumInteriorRing(); i++) {
        LineString hole = cp.getInteriorCurveN(i);
        if (hole != null) {
          collect(hole, aoi, parts);
        }
      }
      return;
    }
    if (geom instanceof GeometryCollection) {
      for (int i = 0; i < geom.getNumGeometries(); i++) {
        collect(geom.getGeometryN(i), aoi, parts);
      }
      return;
    }
    // Whole member inside the box: keep the original object so
    // packCompoundCurveExtract can reassemble by identity.
    if (aoi.contains(geom.getEnvelopeInternal())) {
      parts.add(geom);
      return;
    }
    if (geom instanceof ClothoidSegment) {
      if (aoi.intersects(geom.getEnvelopeInternal())) {
        parts.add(geom);
      }
      return;
    }
    if (geom instanceof CircularString) {
      collectCircularString((CircularString) geom, aoi, parts);
      return;
    }
    SegmentExtracterFilter filter = new SegmentExtracterFilter(aoi);
    geom.apply(filter);
    Geometry extracted = filter.getGeometry(geom.getFactory());
    if (extracted != null && !extracted.isEmpty()) {
      parts.add(extracted);
    }
  }

  /**
   * Consecutive control pairs of a CircularString are chords, not the
   * arc. Extract intersecting 3-control windows as CircularString and
   * merge adjacent windows.
   */
  private static void collectCircularString(CircularString cs, Envelope aoi,
      List<Geometry> parts) {
    Coordinate[] pts = cs.getCoordinates();
    if (pts.length < 3) {
      return;
    }
    CoordinateList run = null;
    for (int i = 0; i + 2 < pts.length; i += 2) {
      Envelope arcEnv = new Envelope(pts[i]);
      arcEnv.expandToInclude(pts[i + 1]);
      arcEnv.expandToInclude(pts[i + 2]);
      if (aoi.intersects(arcEnv)) {
        if (run == null) {
          run = new CoordinateList();
          run.add(pts[i], false);
        }
        run.add(pts[i + 1], false);
        run.add(pts[i + 2], false);
      }
      else if (run != null) {
        parts.add(circularString(cs.getFactory(), run));
        run = null;
      }
    }
    if (run != null) {
      parts.add(circularString(cs.getFactory(), run));
    }
  }

  private static Geometry circularString(GeometryFactory factory, CoordinateList run) {
    Coordinate[] pts = run.toCoordinateArray();
    return factory.createCircularString(
        factory.getCoordinateSequenceFactory().create(pts));
  }

  public static class SegmentExtracterFilter implements CoordinateSequenceFilter
  {
    private Envelope aoi;
    List<Coordinate[]> segSeq = new ArrayList<Coordinate[]>();
    CoordinateList coords;
    int lastIndex;

    public SegmentExtracterFilter(Envelope aoi) {
      this.aoi = aoi;
    }

    public Geometry getGeometry(GeometryFactory factory) {
      List<Geometry> lines = new ArrayList<Geometry>();
      for (Coordinate[] pts : segSeq) {
        Geometry line = factory.createLineString(pts);
        lines.add(line);
      }
      if (lines.size() == 1) 
        return lines.get(0);
      return factory.createMultiLineString(GeometryFactory.toLineStringArray(lines));
    }

    @Override
    public void filter(CoordinateSequence seq, int i) {
      if (i == 0) {
        clearCoords();
        return;
      }
      Coordinate p0 = seq.getCoordinate(i-1);
      Coordinate p1 = seq.getCoordinate(i);
      if (aoi.intersects(p0, p1)) {
        addSeg(i, p0, p1);
        //segSeq.add(new Coordinate[] { p0.copy(), p1.copy() });
      }
      if (i == seq.size() - 1) {
        saveCoords();
      }
    }

    private void addSeg(int index, Coordinate p0, Coordinate p1) {
      if (lastIndex < index - 1) {
        saveCoords();
      }
      if (coords == null) {
        coords = new CoordinateList();
      }
      coords.add(p0, false);
      coords.add(p1, false);
      lastIndex = index;
    }

    private void saveCoords() {
      if (coords != null) {
        segSeq.add(coords.toCoordinateArray());
        coords = null;
      }
    }

    private void clearCoords() {
      coords = null;
      lastIndex = 0;
    }

    @Override
    public boolean isDone() {
      return false;
    }

    @Override
    public boolean isGeometryChanged() {
      return false;
    }
    
  }
}

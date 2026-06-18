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
package org.locationtech.jts.geom.curved;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.CoordinateSequence;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.geom.impl.CoordinateArraySequence;

import junit.framework.TestCase;
import junit.textui.TestRunner;

/**
 * R-CONT (#1195) — arc-aware named predicates for the operand combinations that
 * R-PR (areal/areal) did not cover: a single point against a curved area, and an
 * open curved line against a curved area (where {@code crosses} becomes
 * meaningful). Built on the same exact arc primitives as {@link ArcRelate}.
 * Verified by analytic ground truth (point/disk, segment/disk) and a densified
 * cross-check against JTS core on tessellated geometries.
 */
public class ArcRelateContainsTest extends TestCase {

  public static void main(String[] args) { TestRunner.run(ArcRelateContainsTest.class); }
  public ArcRelateContainsTest(String name) { super(name); }

  private static final GeometryFactory GF = new GeometryFactory();

  // ---------- builders ----------

  private static CoordinateSequence disk(double cx, double cy, double r) {
    return seq(new double[][]{ {cx+r,cy},{cx,cy+r},{cx-r,cy},{cx,cy-r},{cx+r,cy} });
  }
  private static CoordinateSequence chordLine(double x0,double y0,double x1,double y1) {
    return seq(new double[][]{ {x0,y0}, {0.5*(x0+x1),0.5*(y0+y1)}, {x1,y1} });   // collinear triple
  }
  private static CoordinateSequence arcLine(double sx,double sy,double mx,double my,double ex,double ey) {
    return seq(new double[][]{ {sx,sy},{mx,my},{ex,ey} });
  }
  private static CoordinateSequence seq(double[][] xy) {
    Coordinate[] c = new Coordinate[xy.length];
    for (int i=0;i<xy.length;i++) c[i]=new Coordinate(xy[i][0],xy[i][1]);
    return new CoordinateArraySequence(c);
  }

  // ---------- point vs curved area ----------

  public void testPointInCurvedAreaCurated() {
    CoordinateSequence d = disk(0,0,5);
    assertTrue(ArcRelate.containsPoint(d, 0, 0));        // centre
    assertTrue(ArcRelate.intersectsPoint(d, 0, 0));
    assertFalse(ArcRelate.touchesPoint(d, 0, 0));
    assertTrue(ArcRelate.touchesPoint(d, 5, 0));         // on boundary
    assertFalse(ArcRelate.containsPoint(d, 5, 0));
    assertTrue(ArcRelate.intersectsPoint(d, 5, 0));
    assertFalse(ArcRelate.intersectsPoint(d, 9, 0));     // outside
    assertFalse(ArcRelate.containsPoint(d, 9, 0));
  }

  public void testPointInCurvedAreaAnalytic() {
    Random rnd = new Random(7L);
    int checked = 0;
    for (int it=0; it<3000; it++) {
      double cx=rnd.nextDouble()*10-5, cy=rnd.nextDouble()*10-5, r=1+rnd.nextDouble()*5;
      double px=rnd.nextDouble()*24-12, py=rnd.nextDouble()*24-12;
      double dist=Math.hypot(px-cx,py-cy);
      final double M=0.2;
      CoordinateSequence d = disk(cx,cy,r);
      if (dist < r-M) {
        checked++;
        assertTrue("inside->contains", ArcRelate.containsPoint(d,px,py));
        assertTrue("inside->intersects", ArcRelate.intersectsPoint(d,px,py));
        assertFalse("inside->!touches", ArcRelate.touchesPoint(d,px,py));
      } else if (dist > r+M) {
        checked++;
        assertFalse("outside->!contains", ArcRelate.containsPoint(d,px,py));
        assertFalse("outside->!intersects", ArcRelate.intersectsPoint(d,px,py));
      }
    }
    assertTrue("enough point cases ("+checked+")", checked>1500);
  }

  // ---------- open line vs curved area ----------

  public void testLineAreaCurated() {
    CoordinateSequence d = disk(0,0,5);
    // chord crossing the disk (enters at -5, exits at +5)
    CoordinateSequence cross = chordLine(-10,0, 10,0);
    assertTrue(ArcRelate.crossesLine(d, cross));
    assertTrue(ArcRelate.intersectsLine(d, cross));
    assertFalse(ArcRelate.containsLine(d, cross));
    assertFalse(ArcRelate.touchesLine(d, cross));
    assertFalse(ArcRelate.disjointLine(d, cross));
    // chord fully inside
    CoordinateSequence inside = chordLine(-2,0, 2,1);
    assertTrue(ArcRelate.containsLine(d, inside));
    assertFalse(ArcRelate.crossesLine(d, inside));
    assertTrue(ArcRelate.intersectsLine(d, inside));
    // chord fully outside
    CoordinateSequence outside = chordLine(10,10, 20,20);
    assertTrue(ArcRelate.disjointLine(d, outside));
    assertFalse(ArcRelate.intersectsLine(d, outside));
    assertFalse(ArcRelate.crossesLine(d, outside));
    // chord tangent to the top of the disk at (0,5): touches, does not cross
    CoordinateSequence tangent = chordLine(-3,5, 3,5);
    assertTrue(ArcRelate.touchesLine(d, tangent));
    assertFalse(ArcRelate.crossesLine(d, tangent));
    assertTrue(ArcRelate.intersectsLine(d, tangent));
    assertFalse(ArcRelate.containsLine(d, tangent));
    // ARC line that dips through the disk: outside endpoints, apex inside -> crosses
    CoordinateSequence arcCross = arcLine(-8,0, 0,3, 8,0);
    assertTrue(ArcRelate.crossesLine(d, arcCross));
    assertTrue(ArcRelate.intersectsLine(d, arcCross));
    assertFalse(ArcRelate.containsLine(d, arcCross));
  }

  /** Random chord vs disk with analytic segment/circle ground truth (general position). */
  public void testRandomChordVsDiskAnalytic() {
    Random rnd = new Random(4242L);
    int checked = 0;
    for (int it=0; it<3000; it++) {
      double cx=rnd.nextDouble()*8-4, cy=rnd.nextDouble()*8-4, r=1.5+rnd.nextDouble()*4;
      double x0=rnd.nextDouble()*24-12, y0=rnd.nextDouble()*24-12;
      double x1=rnd.nextDouble()*24-12, y1=rnd.nextDouble()*24-12;
      double d0=Math.hypot(x0-cx,y0-cy), d1=Math.hypot(x1-cx,y1-cy);
      double dmin=distToSeg(cx,cy, x0,y0, x1,y1);
      final double M=0.3;
      boolean in0 = d0 < r-M, out0 = d0 > r+M, in1 = d1 < r-M, out1 = d1 > r+M;
      CoordinateSequence area = disk(cx,cy,r), line = chordLine(x0,y0,x1,y1);
      if (in0 && in1) {                                   // segment fully inside
        checked++;
        assertTrue("within->contains", ArcRelate.containsLine(area,line));
        assertFalse("within->!crosses", ArcRelate.crossesLine(area,line));
        assertTrue("within->intersects", ArcRelate.intersectsLine(area,line));
      } else if ((in0 && out1) || (out0 && in1)) {        // one end in, one out -> crosses
        checked++;
        assertTrue("inout->crosses", ArcRelate.crossesLine(area,line));
        assertTrue("inout->intersects", ArcRelate.intersectsLine(area,line));
        assertFalse("inout->!contains", ArcRelate.containsLine(area,line));
        assertFalse("inout->!disjoint", ArcRelate.disjointLine(area,line));
      } else if (out0 && out1 && dmin > r+M) {            // both out, clears the disk
        checked++;
        assertTrue("clear->disjoint", ArcRelate.disjointLine(area,line));
        assertFalse("clear->!intersects", ArcRelate.intersectsLine(area,line));
        assertFalse("clear->!crosses", ArcRelate.crossesLine(area,line));
      } else if (out0 && out1 && dmin < r-M) {            // both out, passes through -> crosses
        checked++;
        assertTrue("through->crosses", ArcRelate.crossesLine(area,line));
        assertTrue("through->intersects", ArcRelate.intersectsLine(area,line));
        assertFalse("through->!contains", ArcRelate.containsLine(area,line));
      }
    }
    assertTrue("enough chord cases ("+checked+")", checked>800);
  }

  /** Densified cross-check of line/area vs JTS core on tessellated geometries. */
  public void testDensifiedLineAreaCrossCheck() {
    Random rnd = new Random(2025L);
    int checked = 0;
    for (int it=0; it<2000; it++) {
      double cx=rnd.nextDouble()*8-4, cy=rnd.nextDouble()*8-4, r=2+rnd.nextDouble()*4;
      double x0=rnd.nextDouble()*24-12, y0=rnd.nextDouble()*24-12;
      double x1=rnd.nextDouble()*24-12, y1=rnd.nextDouble()*24-12;
      double d0=Math.hypot(x0-cx,y0-cy), d1=Math.hypot(x1-cx,y1-cy);
      double dmin=distToSeg(cx,cy, x0,y0, x1,y1);
      final double M=0.5;
      boolean general = (d0<r-M||d0>r+M) && (d1<r-M||d1>r+M) && (dmin<r-M || dmin>r+M);
      if (!general) continue;
      checked++;
      CoordinateSequence area = disk(cx,cy,r), line = chordLine(x0,y0,x1,y1);
      Polygon pa = GF.createPolygon(densifyRing(area, 720));
      LineString ls = GF.createLineString(densifyLine(line, 720));
      assertEquals("intersects", pa.intersects(ls), ArcRelate.intersectsLine(area,line));
      assertEquals("disjoint",   pa.disjoint(ls),   ArcRelate.disjointLine(area,line));
      assertEquals("crosses",    ls.crosses(pa),    ArcRelate.crossesLine(area,line));
      assertEquals("within",     ls.within(pa),     ArcRelate.containsLine(area,line));
    }
    assertTrue("enough cross-checked cases ("+checked+")", checked>400);
  }

  /** Two areal shells never "cross". */
  public void testArealCrossesIsFalse() {
    assertFalse(ArcRelate.crosses(disk(0,0,3), disk(4,0,3)));   // overlap
    assertFalse(ArcRelate.crosses(disk(0,0,5), disk(0,0,1)));   // contains
    assertFalse(ArcRelate.crosses(disk(0,0,2), disk(10,0,2)));  // disjoint
  }

  // ---------- tessellation + geometry helpers ----------

  private static double distToSeg(double px,double py, double x1,double y1, double x2,double y2) {
    double dx=x2-x1, dy=y2-y1, l2=dx*dx+dy*dy;
    if (l2==0.0) return Math.hypot(px-x1,py-y1);
    double t=Math.max(0,Math.min(1,((px-x1)*dx+(py-y1)*dy)/l2));
    return Math.hypot(px-(x1+t*dx), py-(y1+t*dy));
  }

  private static Coordinate[] densifyRing(CoordinateSequence ring, int perCircle) {
    List<Coordinate> out = tessellate(ring, perCircle, true);
    out.add(out.get(0));
    return out.toArray(new Coordinate[0]);
  }
  private static Coordinate[] densifyLine(CoordinateSequence line, int perCircle) {
    List<Coordinate> out = tessellate(line, perCircle, false);
    int n=line.size();
    out.add(new Coordinate(line.getX(n-1), line.getY(n-1)));   // include the final endpoint
    return out.toArray(new Coordinate[0]);
  }

  private static List<Coordinate> tessellate(CoordinateSequence s, int perCircle, boolean ring) {
    List<Coordinate> out = new ArrayList<Coordinate>();
    int n=s.size();
    for (int i=0;i+2<n;i+=2) {
      double sx=s.getX(i),sy=s.getY(i),mx=s.getX(i+1),my=s.getY(i+1),ex=s.getX(i+2),ey=s.getY(i+2);
      double det=2*(sx*(my-ey)+mx*(ey-sy)+ex*(sy-my));
      double el1=Math.hypot(mx-sx,my-sy), el2=Math.hypot(ex-mx,ey-my);
      if (Math.abs(det) <= 1e-9*el1*el2) { out.add(new Coordinate(sx,sy)); continue; }   // chord
      double s2=sx*sx+sy*sy,m2=mx*mx+my*my,e2=ex*ex+ey*ey;
      double cx=(s2*(my-ey)+m2*(ey-sy)+e2*(sy-my))/det;
      double cy=(s2*(ex-mx)+m2*(sx-ex)+e2*(mx-sx))/det;
      double rr=Math.hypot(sx-cx,sy-cy);
      double a0=Math.atan2(sy-cy,sx-cx),am=Math.atan2(my-cy,mx-cx),ae=Math.atan2(ey-cy,ex-cx);
      boolean ccw=det>0;
      double theta=sweep(a0,am,ccw)+sweep(am,ae,ccw);
      int steps=Math.max(2,(int)Math.ceil(theta/(2*Math.PI)*perCircle));
      for (int k=0;k<steps;k++){ double ang=a0+(ccw?1:-1)*theta*k/steps; out.add(new Coordinate(cx+rr*Math.cos(ang),cy+rr*Math.sin(ang))); }
    }
    return out;
  }

  private static double sweep(double from,double to,boolean ccw){ double t=ccw?(to-from):(from-to); t%=2*Math.PI; if(t<0)t+=2*Math.PI; return t; }
}

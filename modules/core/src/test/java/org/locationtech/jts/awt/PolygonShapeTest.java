package org.locationtech.jts.awt;

import java.awt.Shape;
import java.awt.geom.PathIterator;
import java.awt.geom.Point2D;

import org.locationtech.jts.awt.PointTransformation;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.CircularString;

import junit.textui.TestRunner;
import test.jts.GeometryTestCase;

public class PolygonShapeTest extends GeometryTestCase {

  public static void main(String args[]) {
    TestRunner.run(PolygonShapeTest.class);
  }

  public PolygonShapeTest(String name) { super(name); }
  
  public void testFlatness() {
    Geometry geom = read("POLYGON ((100 200, 200 200, 200 100, 100 100, 100 200))");
    ShapeWriter sw = new ShapeWriter();
    Shape shp = sw.toShape(geom);
    
    Geometry geom2 = ShapeReader.read(shp, 0.5, geom.getFactory());
    Geometry geomExpected = read("POLYGON ((100 -200, 200 -200, 200 -100, 100 -100, 100 -200))");
    assertTrue(geomExpected.equalsExact(geom2));
  }
  
  public void testEmptyHole() {
    Geometry geom = read("POLYGON ((100 200, 200 200, 200 100, 100 100, 100 200), EMPTY)");
    ShapeWriter sw = new ShapeWriter();
    Shape shp = sw.toShape(geom);
    
    Geometry geom2 = ShapeReader.read(shp, 0.5, geom.getFactory());
    Geometry geomExpected = read("POLYGON ((100 -200, 200 -200, 200 -100, 100 -100, 100 -200))");
    assertTrue(geomExpected.equalsExact(geom2));
  }

  public void testCircularStringToShapeArc() {
    // Drives the real shipped ShapeWriter.toShape(CircularString) code path
    // (including colinear branch and non-identity transform, as used by GeometryPainter)
    GeometryFactory gf = new GeometryFactory();

    // Non-colinear arc (should produce cubic via circumcentre + k formula)
    CircularString csArc = gf.createCircularString(new Coordinate[] {
      new Coordinate(0, 0),
      new Coordinate(10, 10),
      new Coordinate(20, 0)
    });
    ShapeWriter sw = new ShapeWriter();
    Shape shp = sw.toShape(csArc);
    assertTrue("toShape must return non-null for arc", shp != null);
    PathIterator pi = shp.getPathIterator(null);
    int segCount = 0, cubicCount = 0;
    float[] buf = new float[6];
    while (!pi.isDone()) {
      int type = pi.currentSegment(buf);
      segCount++;
      if (type == PathIterator.SEG_CUBICTO) cubicCount++;
      pi.next();
    }
    System.out.println("Arc (identity): segs=" + segCount + " cubics=" + cubicCount);
    assertTrue("arc must produce segments", segCount > 0);
    assertTrue("non-colinear arc should use cubic", cubicCount > 0);

    // Colinear case (should take lineTo branch)
    CircularString csLine = gf.createCircularString(new Coordinate[] {
      new Coordinate(0, 0),
      new Coordinate(10, 0),
      new Coordinate(20, 0)
    });
    shp = sw.toShape(csLine);
    pi = shp.getPathIterator(null);
    segCount = 0; cubicCount = 0;
    while (!pi.isDone()) {
      int type = pi.currentSegment(buf);
      segCount++;
      if (type == PathIterator.SEG_CUBICTO) cubicCount++;
      pi.next();
    }
    System.out.println("Colinear (identity): segs=" + segCount + " cubics=" + cubicCount);
    assertTrue("colinear must produce segments", segCount > 0);
    // Under identity the cross product may be exactly 0 -> line branch

    // Non-identity transform (scale + translate) - exercises the fixed model-space colinear decision
    PointTransformation xform = new PointTransformation() {
      public void transform(Coordinate model, Point2D view) {
        view.setLocation(model.x * 3.7 + 100, model.y * 3.7 + 50);  // non-trivial
      }
    };
    ShapeWriter swX = new ShapeWriter(xform);
    shp = swX.toShape(csArc);
    assertTrue("transformed arc must produce shape", shp != null);
    pi = shp.getPathIterator(null);
    segCount = 0; cubicCount = 0;
    while (!pi.isDone()) {
      int type = pi.currentSegment(buf);
      segCount++;
      if (type == PathIterator.SEG_CUBICTO) cubicCount++;
      pi.next();
    }
    System.out.println("Arc (transformed): segs=" + segCount + " cubics=" + cubicCount);
    assertTrue("transformed must produce segments", segCount > 0);

    // Colinear under transform too
    shp = swX.toShape(csLine);
    pi = shp.getPathIterator(null);
    segCount = 0; cubicCount = 0;
    while (!pi.isDone()) {
      int type = pi.currentSegment(buf);
      segCount++;
      if (type == PathIterator.SEG_CUBICTO) cubicCount++;
      pi.next();
    }
    System.out.println("Colinear (transformed): segs=" + segCount + " cubics=" + cubicCount);
    assertTrue("transformed colinear must produce segments", segCount > 0);
  }
}

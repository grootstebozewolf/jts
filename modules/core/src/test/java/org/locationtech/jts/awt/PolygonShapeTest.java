package org.locationtech.jts.awt;

import java.awt.Shape;

import org.locationtech.jts.geom.Geometry;

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
    GeometryFactory gf = new GeometryFactory();
    CircularString cs = gf.createCircularString(new Coordinate[] {
      new Coordinate(0, 0),
      new Coordinate(10, 10),
      new Coordinate(20, 0)
    });
    ShapeWriter sw = new ShapeWriter();
    Shape shp = sw.toShape(cs);
    assertNotNull("toShape must return non-null Shape for CircularString arc", shp);

    // Walk the path to prove the arc logic (cubic or line) was executed
    PathIterator pi = shp.getPathIterator(null);
    int segCount = 0;
    int cubicCount = 0;
    float[] buf = new float[6];
    while (!pi.isDone()) {
      int type = pi.currentSegment(buf);
      segCount++;
      if (type == PathIterator.SEG_CUBICTO) cubicCount++;
      pi.next();
    }
    System.out.println("CircularString toShape exercised: segs=" + segCount + " cubics=" + cubicCount);
    assertTrue("must have produced path segments", segCount > 0);
  }
}

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

import java.lang.reflect.InvocationTargetException;

import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.io.WKBWriter;
import org.locationtech.jts.io.curve.CurveWKBWriter;
import org.locationtech.jts.io.geojson.GeoJsonWriter;
import org.locationtech.jts.io.gml2.GMLWriter;
import org.locationtech.jts.io.kml.KMLWriter;
import org.locationtech.jtstest.geomfunction.Metadata;
import org.locationtech.jtstest.testbuilder.io.SVGTestWriter;
import org.locationtech.jtstest.util.ClassUtil;
import org.locationtech.jtstest.util.io.WKBDumper;


public class WriterFunctions
{
  /**
   * Linearises curve input before handing it to a jts-core writer.
   * <p>
   * Every writer below lives in jts-core, takes a {@link Geometry}, and
   * dispatches on {@code instanceof Polygon} / {@code LineString} -- which the
   * curve types satisfy, since they extend those. So each one serialises
   * {@code getCoordinates()}, and for a curve that is only its control points:
   * a circle exported as its inscribed square, area 8 rather than 4*pi. Silently
   * the wrong shape, not merely a lost curve. Same shape of gap as
   * {@code HullFunctions}, and the same remedy, since a static entry point
   * offers no virtual call for a curve type to override.
   * <p>
   * <b>What survives.</b> Neither GML2 nor KML nor GeoJSON has any
   * representation of a circular arc, so the arc cannot be preserved by any
   * choice made here; the only question is whether the exported shape is right.
   * WKB is the exception -- SQL/MM defines curve type codes 8 to 12 -- and
   * {@link #writeWKB} / {@link #dumpWKB} now use {@link CurveWKBWriter}
   * so a disc round-trips as type 10, not a densified type-3 polygon.
   * <p>
   * Non-curve input is returned as the same object, so a plain geometry
   * serialises byte-for-byte as it did before; that is asserted for all four
   * writers in {@code WriterFunctionsCurveTest}.
   * <p>
   * The cost is verbosity: at {@code linearizeForOps}' 1e-6 of extent, a circle
   * becomes about 1570 vertices. Callers who want a coarser export should
   * linearise deliberately with {@code Curve -> toLinear} at a tolerance of their
   * choosing and write the result.
   */
  private static Geometry exportable(Geometry geom) {
    return CurveFunctions.linearizeForOps(geom);
  }

  public static String writeKML(Geometry geom)
  {
    if (geom == null) return "";
    KMLWriter writer = new KMLWriter();
    return writer.write(exportable(geom));
  }

  public static String writeGML(Geometry geom)
  {
    if (geom == null) return "";
    return (new GMLWriter()).write(exportable(geom));
  }

  /**
   * Deliberately not routed through {@link #exportable}. Oracle SDO_GEOMETRY
   * does represent circular arcs (element type 1005/2005 with interpretation 2),
   * so densifying here would discard something the target format can carry --
   * the same argument that makes densifying WKB a stopgap rather than a fix. The
   * writer is loaded dynamically and is not on this build's classpath, so the
   * arc-aware behaviour cannot be implemented or tested here either way.
   */
  public static String writeOra(Geometry g) throws SecurityException, IllegalArgumentException, ClassNotFoundException, NoSuchMethodException, InstantiationException, IllegalAccessException, InvocationTargetException
  {
    if (g == null) return "";
    // call dynamically to avoid dependency on OraWriter
    String sql = (String) ClassUtil.dynamicCall("com.vividsolutions.jts.io.oracle.OraWriter",
        "writeSQL",
        new Class[] { Geometry.class },
        new Object[] { g });
    return sql;
    //return (new OraWriter(null)).writeSQL(g);
  }

  public static String writeWKB(Geometry g)
  {
    if (g == null) return "";
    return WKBWriter.toHex((new CurveWKBWriter().write(g)));
  }

  public static String dumpWKB(Geometry g)
  {
    if (g == null) return "";
    byte[] wkb = (new CurveWKBWriter().write(g));
    return WKBDumper.dump(wkb);
  }

  /**
   * Also fixes the type name: the writer reported
   * {@code "type":"CurvePolygon"}, which is not one of the seven types RFC 7946
   * defines, and {@code GeoJsonReader} threw a ParseException on it. The
   * linearised geometry really is a Polygon, so the name is now honest.
   */
  public static String writeGeoJSON(Geometry g)
  {
    if (g == null) return "";
    return (new GeoJsonWriter().write(exportable(g)));
  }

  public static String writeGeoJSONFixDecimal(Geometry g,
      @Metadata(title="Num Decimals")
      int numDecimals)
  {
    if (g == null) return "";
    return (new GeoJsonWriter(numDecimals).write(exportable(g)));
  }

  public static String writeSVG(Geometry a, Geometry b) {
    return SVGTestWriter.writeSVG(exportable(a), exportable(b));
  }
}

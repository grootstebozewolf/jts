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
package org.locationtech.jts.io;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.EnumSet;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.CoordinateSequence;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryCollection;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.LinearRing;
import org.locationtech.jts.geom.MultiLineString;
import org.locationtech.jts.geom.MultiPoint;
import org.locationtech.jts.geom.MultiPolygon;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.util.Assert;

/**
 * Writes a {@link Geometry} into Well-Known Binary format.
 * Supports use of an {@link OutStream}, which allows easy use
 * with arbitrary byte stream sinks.
 * <p>
 * The WKB format is specified in the 
 * OGC <A HREF="http://portal.opengeospatial.org/files/?artifact_id=829"><i>Simple Features for SQL
 * specification</i></a> (section 3.3.2.6).
 * <p>
 * There are a few cases which are not specified in the standard.
 * The implementation uses a representation which is compatible with
 * other common spatial systems (notably, PostGIS).
 * <ul>
 * <li>{@link LinearRing}s are written as {@link LineString}s</li>
 * <li>Empty geometries are output as follows:
 * <ul>
 * <li><b>Point</b>: a <code>WKBPoint</code> with <code>NaN</code> ordinate values</li> 
 * <li><b>LineString</b>: a <code>WKBLineString</code> with zero points</li>
 * <li><b>Polygon</b>: a <code>WKBPolygon</code> with zero rings</li>
 * <li><b>Multigeometries</b>: a <code>WKBMulti</code> geometry of appropriate type with zero elements</li>
 * <li><b>GeometryCollections</b>: a <code>WKBGeometryCollection</code> with zero elements</li>
 * </ul></li>
 * </ul>
 * <p>
 * This implementation supports two WKB flavours, matching GEOS
 * {@code WKBWriter} ({@code setFlavor}):
 * <ul>
 * <li>{@link WKBConstants#wkbExtended} (default) — PostGIS / SFSQL
 * Extended WKB. Z / M / SRID are high bits
 * {@code 0x80000000} / {@code 0x40000000} / {@code 0x20000000}.</li>
 * <li>{@link WKBConstants#wkbIso} — ISO/IEC 13249-3. Dimension is
 * {@code type+1000} (Z), {@code +2000} (M), {@code +3000} (ZM).
 * ISO has no SRID embedding.</li>
 * </ul>
 * The default flavour is Extended. ISO is emitted only when
 * {@link #setFlavor(int)} is called with {@link WKBConstants#wkbIso}.
 * SQL/MM types 8–12 are not flattened to {@code LineString} /
 * {@code Polygon}; the core writer throws unless a subclass
 * (for example {@code CurveWKBWriter}) handles them.
 * {@link LinearRing} → {@link LineString} is the only type collapse.
 * <p>
 * SRID output is optimized, if specified. 
 * Only the top-level geometry has the SRID included.
 * This assumes that all geometries in a collection have the same SRID as 
 * the collection (which is the JTS convention).
 * <p>
 * This class supports reuse of a single instance to read multiple
 * geometries. This class is not thread-safe; each thread should create its own
 * instance.
 * 
 * <h3>Syntax</h3>
 * The following syntax specification describes the version of Well-Known Binary
 * supported by JTS.
 * <p>
 * <i>The specification uses a syntax language similar to that used in
 * the C language.  Bitfields are specified from high-order to low-order bits.</i>
 * <p>
 * <blockquote><pre>
 * 
 * <b>byte</b> = 1 byte
 * <b>uint32</b> = 32 bit unsigned integer (4 bytes)
 * <b>double</b> = double precision number (8 bytes)
 * 
 * abstract Point { }
 * 
 * Point2D extends Point {
 * 	<b>double</b> x;
 * 	<b>double</b> y;
 * }
 * 
 * Point3D extends Point {
 * 	<b>double</b> x;
 * 	<b>double</b> y;
 * 	<b>double</b> z;
 * }
 * 
 * LinearRing {
 * 	<b>uint32</b> numPoints;
 * 	Point points[numPoints];
 * }
 * 
 * enum wkbGeometryType {
 * 	wkbPoint = 1,
 * 	wkbLineString = 2,
 * 	wkbPolygon = 3,
 * 	wkbMultiPoint = 4,
 * 	wkbMultiLineString = 5,
 * 	wkbMultiPolygon = 6,
 * 	wkbGeometryCollection = 7,
 * 	wkbCircularString = 8,
 * 	wkbCompoundCurve = 9,
 * 	wkbCurvePolygon = 10,
 * 	wkbMultiCurve = 11,
 * 	wkbMultiSurface = 12
 * }
 * 
 * enum byteOrder {
 * 	wkbXDR = 0,	// Big Endian
 * 	wkbNDR = 1 	// Little Endian
 * }
 * 
 * WKBType {
 * 	<b>uint32</b> wkbGeometryType : 8; // values from enum wkbGeometryType
 * }
 * 
 * EWKBType {
 * 	<b>uint32</b> is3D : 1; 	// 0 = 2D, 1 = 3D
 * 	<b>uint32</b> noData1 : 1; 
 * 	<b>uint32</b> hasSRID : 1;  	// 0, no, 1 = yes
 * 	<b>uint32</b> noData2 : 21; 
 * 	<b>uint32</b> wkbGeometryType : 8; // values from enum wkbGeometryType
 * }
 * 
 * abstract WKBGeometry {
 * 	<b>byte</b> byteOrder;		// values from enum byteOrder
 * 	EWKBType wkbType
 * 	[ <b>uint32</b> srid; ] 	// only if hasSRID = yes
 * }
 * 
 * WKBPoint extends WKBGeometry {
 * 	Point point;
 * }
 * 
 * WKBLineString extends WKBGeometry {
 * 	<b>uint32</b> numCoords;
 * 	Point points[numCoords];
 * }
 * 
 * WKBPolygon extends WKBGeometry {
 * 	<b>uint32</b> numRings;
 * 	LinearRing rings[numRings];
 * }
 * 
 * WKBMultiPoint extends WKBGeometry {
 * 	<b>uint32</b> numElems;
 * 	WKBPoint elems[numElems];
 * }
 * 
 * WKBMultiLineString extends WKBGeometry {
 * 	<b>uint32</b> numElems;
 * 	WKBLineString elems[numElems];
 * }
 * 
 * wkbMultiPolygon extends WKBGeometry {
 * 	<b>uint32</b> numElems;
 * 	WKBPolygon elems[numElems];
 * }
 * 
 * WKBGeometryCollection extends WKBGeometry {
 * 	<b>uint32</b> numElems;
 * 	WKBGeometry elems[numElems];
 * }
 * 
 * </pre></blockquote>
 * <p>
 * Subclasses may override {@link #writeOtherGeometry} to emit ISO/OGC
 * types the core writer does not construct (SQL/MM codes 8–12). The
 * hook runs before the {@code instanceof} ladder so a
 * {@code CircularString} is not written as a {@code LineString}.
 * Helpers used to write byte order, type, coordinates, and nested
 * members are {@code protected}.
 * @see WKBReader
 */
public class WKBWriter
{
  /**
   * Converts a byte array to a hexadecimal string.
   * 
   * @param bytes
   * @return a string of hexadecimal digits
   * 
   * @deprecated
   */
  public static String bytesToHex(byte[] bytes)
  {
    return toHex(bytes);
  }

  /**
   * Converts a byte array to a hexadecimal string.
   * 
   * @param bytes a byte array
   * @return a string of hexadecimal digits
   */
  public static String toHex(byte[] bytes)
  {
    StringBuffer buf = new StringBuffer();
    for (int i = 0; i < bytes.length; i++) {
      byte b = bytes[i];
      buf.append(toHexDigit((b >> 4) & 0x0F));
      buf.append(toHexDigit(b & 0x0F));
    }
    return buf.toString();
  }

  private static char toHexDigit(int n)
  {
    if (n < 0 || n > 15)
      throw new IllegalArgumentException("Nibble value out of range: " + n);
    if (n <= 9)
      return (char) ('0' + n);
    return (char) ('A' + (n - 10));
  }

  private EnumSet<Ordinate> outputOrdinates;
  private int outputDimension = 2;
  private int byteOrder;
  private boolean includeSRID = false;
  private int flavor = WKBConstants.wkbExtended;
  private ByteArrayOutputStream byteArrayOS = new ByteArrayOutputStream();
  private OutStream byteArrayOutStream = new OutputStreamOutStream(byteArrayOS);
  // holds output data values
  private byte[] buf = new byte[8];

  /**
   * Creates a writer that writes {@link Geometry}s with
   * output dimension = 2 and BIG_ENDIAN byte order
   */
  public WKBWriter() {
    this(2, ByteOrderValues.BIG_ENDIAN);
  }

  /**
   * Creates a writer that writes {@link Geometry}s with
   * the given dimension (2 or 3) for output coordinates
   * and {@link ByteOrderValues#BIG_ENDIAN} byte order.
   * If the input geometry has a small coordinate dimension,
   * coordinates will be padded with {@link Coordinate#NULL_ORDINATE}.
   *
   * @param outputDimension the coordinate dimension to output (2 or 3)
   */
  public WKBWriter(int outputDimension) {
    this(outputDimension, ByteOrderValues.BIG_ENDIAN);
  }

  /**
   * Creates a writer that writes {@link Geometry}s with
   * the given dimension (2 or 3) for output coordinates
   * and {@link ByteOrderValues#BIG_ENDIAN} byte order. This constructor also
   * takes a flag to control whether srid information will be
   * written.
   * If the input geometry has a smaller coordinate dimension,
   * coordinates will be padded with {@link Coordinate#NULL_ORDINATE}.
   *
   * @param outputDimension the coordinate dimension to output (2 or 3)
   * @param includeSRID indicates whether SRID should be written
   */
  public WKBWriter(int outputDimension, boolean includeSRID) {
    this(outputDimension, ByteOrderValues.BIG_ENDIAN, includeSRID);
  }
  
  /**
   * Creates a writer that writes {@link Geometry}s with
   * the given dimension (2 or 3) for output coordinates
   * and byte order
   * If the input geometry has a small coordinate dimension,
   * coordinates will be padded with {@link Coordinate#NULL_ORDINATE}.
   *
   * @param outputDimension the coordinate dimension to output (2 or 3)
   * @param byteOrder the byte ordering to use
   */
  public WKBWriter(int outputDimension, int byteOrder) {
      this(outputDimension, byteOrder, false);
  }
  
  /**
   * Creates a writer that writes {@link Geometry}s with
   * the given dimension (2 to 4) for output coordinates
   * and byte order. This constructor also takes a flag to 
   * control whether srid information will be written.
   * If the input geometry has a small coordinate dimension,
   * coordinates will be padded with {@link Coordinate#NULL_ORDINATE}.
   * The output follows the following rules:
   * <ul>
   *   <li>If the specified <b>output dimension is 3</b> and the <b>z is measure flag
   *   is set to true</b>, the Z value of coordinates will be written if it is present
   * (i.e. if it is not <code>Double.NaN</code>)</li>
   *   <li>If the specified <b>output dimension is 3</b> and the <b>z is measure flag
   *   is set to false</b>, the Measure value of coordinates will be written if it is present
   * (i.e. if it is not <code>Double.NaN</code>)</li>
   *   <li>If the specified <b>output dimension is 4</b>, the Z value of coordinates will
   *   be written even if it is not present when the Measure value is present. The Measure
   *   value of coordinates will be written if it is present
   * (i.e. if it is not <code>Double.NaN</code>)</li>
   * </ul>
   * See also {@link #setOutputOrdinates(EnumSet)}
   *
   * @param outputDimension the coordinate dimension to output (2 to 4)
   * @param byteOrder the byte ordering to use
   * @param includeSRID indicates whether SRID should be written
   */
  public WKBWriter(int outputDimension, int byteOrder, boolean includeSRID) {
      this.outputDimension = outputDimension;
      this.byteOrder = byteOrder;
      this.includeSRID = includeSRID;
      
      if (outputDimension < 2 || outputDimension > 4)
        throw new IllegalArgumentException("Output dimension must be 2 to 4");

      this.outputOrdinates = EnumSet.of(Ordinate.X, Ordinate.Y);
      if (outputDimension > 2)
        outputOrdinates.add(Ordinate.Z);
      if (outputDimension > 3)
        outputOrdinates.add(Ordinate.M);
  }

  /**
   * Sets the {@link Ordinate} that are to be written. Possible members are:
   * <ul>
   * <li>{@link Ordinate#X}</li>
   * <li>{@link Ordinate#Y}</li>
   * <li>{@link Ordinate#Z}</li>
   * <li>{@link Ordinate#M}</li>
   * </ul>
   * Values of {@link Ordinate#X} and {@link Ordinate#Y} are always assumed and not
   * particularly checked for.
   *
   * @param outputOrdinates A set of {@link Ordinate} values
   */
  public void setOutputOrdinates(EnumSet<Ordinate> outputOrdinates) {

    this.outputOrdinates.remove(Ordinate.Z);
    this.outputOrdinates.remove(Ordinate.M);

    if (this.outputDimension == 3) {
      if (outputOrdinates.contains(Ordinate.Z))
        this.outputOrdinates.add(Ordinate.Z);
      else if (outputOrdinates.contains(Ordinate.M))
        this.outputOrdinates.add(Ordinate.M);
    }
    if (this.outputDimension == 4) {
      if (outputOrdinates.contains(Ordinate.Z))
        this.outputOrdinates.add(Ordinate.Z);
      if (outputOrdinates.contains(Ordinate.M))
        this.outputOrdinates.add(Ordinate.M);
    }
  }

  /**
   * Gets a bit-pattern defining which ordinates should be
   * @return an ordinate bit-pattern
   * @see #setOutputOrdinates(EnumSet)
   */
  public EnumSet<Ordinate> getOutputOrdinates() {
    return this.outputOrdinates;
  }

  /**
   * Returns the WKB flavour this writer will emit.
   * Default is {@link WKBConstants#wkbExtended}.
   *
   * @return {@link WKBConstants#wkbExtended} or {@link WKBConstants#wkbIso}
   */
  public int getFlavor() {
    return flavor;
  }

  /**
   * Sets the WKB flavour this writer will emit.
   * Default is Extended; ISO is used only when this is set to
   * {@link WKBConstants#wkbIso}.
   *
   * @param newFlavor {@link WKBConstants#wkbIso} or {@link WKBConstants#wkbExtended}
   * @throws IllegalArgumentException if {@code newFlavor} is not a known flavour
   */
  public void setFlavor(int newFlavor) {
    if (newFlavor != WKBConstants.wkbIso && newFlavor != WKBConstants.wkbExtended) {
      throw new IllegalArgumentException("Invalid WKB output flavour");
    }
    this.flavor = newFlavor;
  }

  /**
   * Writes a {@link Geometry} into a byte array.
   *
   * @param geom the geometry to write
   * @return the byte array containing the WKB
   */
  public byte[] write(Geometry geom)
  {
    try {
      byteArrayOS.reset();
      write(geom, byteArrayOutStream);
    }
    catch (IOException ex) {
      throw new RuntimeException("Unexpected IO exception: " + ex.getMessage());
    }
    return byteArrayOS.toByteArray();
  }

  /**
   * Writes a {@link Geometry} to an {@link OutStream}.
   *
   * @param geom the geometry to write
   * @param os the out stream to write to
   * @throws IOException if an I/O error occurs
   */
  public void write(Geometry geom, OutStream os) throws IOException
  {
    // evaluate the ordinates actually present in the geometry
    EnumSet<Ordinate> actualOutputOrdinates = this.outputOrdinates;
    if (!geom.isEmpty()) {
      CheckOrdinatesFilter cof = new CheckOrdinatesFilter(this.outputOrdinates);
      geom.apply(cof);
      actualOutputOrdinates = cof.getOutputOrdinates();
    }

    if (writeOtherGeometry(geom, actualOutputOrdinates, os)) {
      return;
    }

    if (isSqlMmCurveType(geom)) {
      throw new IllegalArgumentException(
          "WKB type " + geom.getGeometryType()
              + " requires CurveWKBWriter; core WKBWriter must not flatten"
              + " SQL/MM 8–12 (signed I/O) or preview HOLD 18–21"
              + " to LineString or Polygon");
    }

    if (geom instanceof Point)
      writePoint((Point) geom, actualOutputOrdinates, os);
    // LinearRings will be written as LineStrings
    else if (geom instanceof LineString)
      writeLineString((LineString) geom, actualOutputOrdinates, os);
    else if (geom instanceof Polygon)
      writePolygon((Polygon) geom, actualOutputOrdinates, os);
    else if (geom instanceof MultiPoint)
      writeGeometryCollection(WKBConstants.wkbMultiPoint, 
          (MultiPoint) geom, actualOutputOrdinates, os);
    else if (geom instanceof MultiLineString)
      writeGeometryCollection(WKBConstants.wkbMultiLineString,
          (MultiLineString) geom, actualOutputOrdinates, os);
    else if (geom instanceof MultiPolygon)
      writeGeometryCollection(WKBConstants.wkbMultiPolygon,
          (MultiPolygon) geom, actualOutputOrdinates, os);
    else if (geom instanceof GeometryCollection)
      writeGeometryCollection(WKBConstants.wkbGeometryCollection,
          (GeometryCollection) geom, actualOutputOrdinates, os);
    else {
      Assert.shouldNeverReachHere("Unknown Geometry type");
    }
  }

  /**
   * Hook for subclasses to write geometry types the core dispatch does
   * not handle (ISO/IEC 13249-3 SQL/MM codes 8–12). Called before the
   * {@code instanceof} ladder. Return {@code true} if the geometry was
   * written; {@code false} to continue with the core path.
   * If this returns {@code false} for a type 8–12 geometry, the writer
   * throws rather than flattening to {@code LineString} / {@code Polygon}.
   */
  protected boolean writeOtherGeometry(Geometry geom,
      EnumSet<Ordinate> outputOrdinates, OutStream os) throws IOException {
    return false;
  }

  /**
   * Signed SQL/MM 8–12 plus preview HOLD 18–21, identified by
   * {@link Geometry#getGeometryType()} so core does not import
   * {@code jts-curve}. 18–21 are not SIGNED I/O. LinearRing is not
   * included; it remains the only allowed collapse (to LineString).
   */
  private static boolean isSqlMmCurveType(Geometry geom) {
    String type = geom.getGeometryType();
    return WKTConstants.CIRCULARSTRING.equalsIgnoreCase(type)
        || WKTConstants.COMPOUNDCURVE.equalsIgnoreCase(type)
        || WKTConstants.CURVEPOLYGON.equalsIgnoreCase(type)
        || WKTConstants.MULTICURVE.equalsIgnoreCase(type)
        || WKTConstants.MULTISURFACE.equalsIgnoreCase(type)
        || WKTConstants.CLOTHOID.equalsIgnoreCase(type)
        || "ClothoidSegment".equalsIgnoreCase(type)
        || WKTConstants.BEZIER.equalsIgnoreCase(type)
        || "BezierCurve".equalsIgnoreCase(type)
        || WKTConstants.ELLIPSE.equalsIgnoreCase(type)
        || "EllipseCurve".equalsIgnoreCase(type)
        || WKTConstants.NURBS.equalsIgnoreCase(type)
        || "NurbsCurve".equalsIgnoreCase(type);
  }

  private void writePoint(Point pt, EnumSet<Ordinate> outputOrdinates, OutStream os) throws IOException
  {
    writeByteOrder(os);
    writeGeometryType(WKBConstants.wkbPoint, outputOrdinates, pt, os);
    if (pt.getCoordinateSequence().size() == 0) {
      // write empty point as NaNs (extension to OGC standard)
      writeNaNs(outputOrdinates, os);
    } else {
      writeCoordinateSequence(pt.getCoordinateSequence(), outputOrdinates, false, os);
    }
  }

  private void writeLineString(LineString line, EnumSet<Ordinate> outputOrdinates, OutStream os)
      throws IOException
  {
    writeByteOrder(os);
    writeGeometryType(WKBConstants.wkbLineString, outputOrdinates, line, os);
    writeCoordinateSequence(line.getCoordinateSequence(), outputOrdinates, true, os);
  }

  private void writePolygon(Polygon poly, EnumSet<Ordinate> outputOrdinates, OutStream os) throws IOException
  {
    writeByteOrder(os);
    writeGeometryType(WKBConstants.wkbPolygon, outputOrdinates, poly, os);
    //--- write empty polygons with no rings (OCG extension)
    if (poly.isEmpty()) {
      writeInt(0, os);
      return;
    }
    writeInt(poly.getNumInteriorRing() + 1, os);
    writeCoordinateSequence(poly.getExteriorRing().getCoordinateSequence(), outputOrdinates, true, os);
    for (int i = 0; i < poly.getNumInteriorRing(); i++) {
      writeCoordinateSequence(poly.getInteriorRingN(i).getCoordinateSequence(), outputOrdinates, true,
          os);
    }
  }

  private void writeGeometryCollection(int geometryType, GeometryCollection gc, EnumSet<Ordinate> outputOrdinates,
      OutStream os) throws IOException
  {
    writeByteOrder(os);
    writeGeometryType(geometryType, outputOrdinates, gc, os);
    writeInt(gc.getNumGeometries(), os);
    boolean originalIncludeSRID = this.includeSRID;
    this.includeSRID = false;
    for (int i = 0; i < gc.getNumGeometries(); i++) {
      write(gc.getGeometryN(i), os);
    }
    this.includeSRID = originalIncludeSRID;
  }

  /**
   * Writes a typed collection: header, child count, then each child as
   * a nested WKB geometry. Child SRIDs are omitted, matching Multi*
   * writing.
   */
  protected void writeTypedCollection(int geometryType, Geometry parent,
      Geometry[] children, EnumSet<Ordinate> outputOrdinates, OutStream os)
      throws IOException {
    writeByteOrder(os);
    writeGeometryType(geometryType, outputOrdinates, parent, os);
    writeInt(children == null ? 0 : children.length, os);
    boolean originalIncludeSRID = this.includeSRID;
    this.includeSRID = false;
    if (children != null) {
      for (int i = 0; i < children.length; i++) {
        write(children[i], os);
      }
    }
    this.includeSRID = originalIncludeSRID;
  }

  protected void writeByteOrder(OutStream os) throws IOException
  {
    if (byteOrder == ByteOrderValues.LITTLE_ENDIAN)
      buf[0] = WKBConstants.wkbNDR;
    else
      buf[0] = WKBConstants.wkbXDR;
    os.write(buf, 1);
  }

  protected void writeGeometryType(int geometryType, EnumSet<Ordinate> outputOrdinates, Geometry g, OutStream os)
      throws IOException
  {
    int typeInt = geometryType;
    if (flavor == WKBConstants.wkbIso) {
      // ISO/IEC 13249-3: type+1000 Z, +2000 M, +3000 ZM. No SRID.
      if (outputDimension > 2) {
        if (outputOrdinates.contains(Ordinate.Z)) {
          typeInt += 1000;
        }
        if (outputOrdinates.contains(Ordinate.M)) {
          typeInt += 2000;
        }
      }
      writeInt(typeInt, os);
      return;
    }
    if (flavor != WKBConstants.wkbExtended) {
      throw new IllegalArgumentException("Unknown WKB flavor");
    }

    int ordinals = 0;
    if (outputOrdinates.contains(Ordinate.Z)) {
      ordinals = ordinals | 0x80000000;
    }

    if (outputOrdinates.contains(Ordinate.M)) {
      ordinals = ordinals | 0x40000000;
    }

    int flag3D = (outputDimension > 2) ? ordinals : 0;
    typeInt = geometryType | flag3D;
    typeInt |= includeSRID ? 0x20000000 : 0;
    writeInt(typeInt, os);
    if (includeSRID) {
        writeInt(g.getSRID(), os);
    }
  }

  protected void writeInt(int intValue, OutStream os) throws IOException
  {
    ByteOrderValues.putInt(intValue, buf, byteOrder);
    os.write(buf, 4);
  }

  /**
   * Writes one IEEE-754 double in the writer byte order.
   * Used by preview HOLD curve payloads (e.g. Clothoid parameters).
   */
  protected void writeDouble(double value, OutStream os) throws IOException
  {
    ByteOrderValues.putDouble(value, buf, byteOrder);
    os.write(buf, 8);
  }

  protected void writeCoordinateSequence(CoordinateSequence seq, EnumSet<Ordinate> outputOrdinates, boolean writeSize, OutStream os)
      throws IOException
  {
    if (writeSize)
      writeInt(seq.size(), os);

    boolean hasZ = outputOrdinates.contains(Ordinate.Z);
    boolean hasM = outputOrdinates.contains(Ordinate.M);
    for (int i = 0; i < seq.size(); i++) {
      writeCoordinate(seq, hasZ, hasM, i, os);
    }
  }

  private void writeCoordinate(CoordinateSequence seq, boolean hasZ, boolean hasM, int index, OutStream os)
  throws IOException
  {
    ByteOrderValues.putDouble(seq.getX(index), buf, byteOrder);
    os.write(buf, 8);
    ByteOrderValues.putDouble(seq.getY(index), buf, byteOrder);
    os.write(buf, 8);
    
    // only write 3rd dim if caller has requested it for this writer
    if (hasZ) {
      // if 3rd dim is requested, only write it if the CoordinateSequence provides it
      double ordVal = seq.getZ(index);
      ByteOrderValues.putDouble(ordVal, buf, byteOrder);
      os.write(buf, 8);
    }
    // only write 4th dim if caller has requested it for this writer
    if (hasM) {
      // if 4th dim is requested, only write it if the CoordinateSequence provides it
      double ordVal = seq.getM(index);
      ByteOrderValues.putDouble(ordVal, buf, byteOrder);
      os.write(buf, 8);
    }
  }
  
  private void writeNaNs(EnumSet<Ordinate> outputOrdinates, OutStream os)
      throws IOException
  {
    int dims = outputOrdinates.size();
    for (int i = 0; i < dims; i++) {
      ByteOrderValues.putDouble(Double.NaN, buf, byteOrder);
      os.write(buf, 8);
    }
  }
}

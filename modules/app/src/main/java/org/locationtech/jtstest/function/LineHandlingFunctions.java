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
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.locationtech.jts.dissolve.LineDissolver;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.CoordinateSequence;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryCollection;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.MultiLineString;
import org.locationtech.jts.geom.curve.CircularString;
import org.locationtech.jts.geom.curve.CompoundCurve;
import org.locationtech.jts.geom.curve.CurveGeometryFactory;
import org.locationtech.jts.geom.curve.MultiCurve;
import org.locationtech.jts.geom.util.LinearComponentExtracter;
import org.locationtech.jts.operation.linemerge.LineMerger;
import org.locationtech.jts.operation.linemerge.LineSequencer;
import org.locationtech.jtstest.geomfunction.Metadata;

public class LineHandlingFunctions {
	
  public static Geometry mergeLines(Geometry g)
  {
    LineMerger merger = new LineMerger();
    merger.add(g);
    Collection lines = merger.getMergedLineStrings();
    return g.getFactory().buildGeometry(lines);
  }
  
  public static Geometry sequenceLines(Geometry g)
  {
    return LineSequencer.sequence(g);
  }
  
  public static Geometry extractLines(Geometry g)
  {
    List lines = LinearComponentExtracter.getLines(g);
    return g.getFactory().buildGeometry(lines);
  }
  public static Geometry extractSegments(Geometry g)
  {
    List lines = LinearComponentExtracter.getLines(g);
    List segments = new ArrayList();
    for (Iterator it = lines.iterator(); it.hasNext(); ) {
      LineString line = (LineString) it.next();
      for (int i = 1; i < line.getNumPoints(); i++) {
        LineString seg = g.getFactory().createLineString(
            new Coordinate[] { line.getCoordinateN(i-1), line.getCoordinateN(i) }       
          );
        segments.add(seg);
      }
    }
    return g.getFactory().buildGeometry(segments);
  }
  public static Geometry extractChains(Geometry g, int maxChainSize)
  {
    List lines = LinearComponentExtracter.getLines(g);
    List chains = new ArrayList();
    for (Iterator it = lines.iterator(); it.hasNext(); ) {
      LineString line = (LineString) it.next();
      for (int i = 0; i < line.getNumPoints() - 1; i += maxChainSize) {
        LineString chain = extractChain(line, i, maxChainSize);
        chains.add(chain);
      }
    }
    return g.getFactory().buildGeometry(chains);
  }
  
  private static LineString extractChain(LineString line, int index, int maxChainSize)
  {
    int size = maxChainSize + 1;
    if (index + size > line.getNumPoints()) 
      size = line.getNumPoints() - index;
    Coordinate[] pts = new Coordinate[size];
    for (int i = 0; i < size; i++) {
      pts[i] = line.getCoordinateN(index + i);
    }
    return line.getFactory().createLineString(pts);
  }
  
  public static Geometry dissolve(Geometry geom)
  {
    return LineDissolver.dissolve(geom);
  }

  /**
   * Trims line A to geometry B.
   * Equivalent to the projection of B onto A.
   * 
   * @param a line to trim
   * @param b trimming geometry
   * @return line A trimmed to B
   */
  @Metadata(description="Trim line A to geometry B")
  public static Geometry trim(Geometry a, Geometry b) {
    return LinearReferencingFunctions.project(a, b);
  }

  /**
   * Endpoint-matching merge that preserves arc identity, unlike
   * {@link #mergeLines} which collapses CircularStrings to polylines.
   * Members of the input that share endpoints are joined into longer
   * connected chains; each chain is emitted as the most specific type
   * that fits its members:
   * <ul>
   *   <li>all-{@code LineString} chain   → a single {@code LineString}</li>
   *   <li>all-{@code CircularString} chain → a single concatenated
   *       {@code CircularString} (5/7/… points)</li>
   *   <li>mixed chain                    → a {@code CompoundCurve}
   *       carrying the original member subtypes</li>
   * </ul>
   * Non-linear members (Points, Polygons, etc.) are passed through
   * untouched. CompoundCurve inputs are expanded back to their members
   * so re-joining is idempotent. Endpoint matching is 2D (Z ignored).
   */
  @Metadata(description="Endpoint-merge curve members of a collection (arc-aware sibling of mergeLines)")
  public static Geometry mergeCurves(Geometry g) {
    if (g == null || g.isEmpty()) return g;
    GeometryFactory factory = g.getFactory();

    List<LineString> linear = new ArrayList<LineString>();
    List<Geometry> nonLinear = new ArrayList<Geometry>();
    collectLinearMembers(g, linear, nonLinear);

    if (linear.size() < 2 && nonLinear.isEmpty()) return g;

    List<List<LineString>> chains = buildChains(linear);

    List<Geometry> results = new ArrayList<Geometry>();
    for (List<LineString> chain : chains) {
      results.add(emitChain(chain, factory));
    }
    results.addAll(nonLinear);

    if (results.size() == 1) return results.get(0);
    return factory.buildGeometry(results);
  }

  private static void collectLinearMembers(Geometry g, List<LineString> linear, List<Geometry> nonLinear) {
    if (g.isEmpty()) return;
    // CompoundCurve must come before LineString -- it's a LineString subclass
    // but we want to expand its members so re-joining is idempotent.
    if (g instanceof CompoundCurve) {
      CompoundCurve cc = (CompoundCurve) g;
      for (int i = 0; i < cc.getNumMembers(); i++) {
        LineString m = cc.getMemberN(i);
        if (!m.isEmpty()) linear.add(m);
      }
      return;
    }
    if (g instanceof LineString) {
      linear.add((LineString) g);
      return;
    }
    if (g instanceof MultiLineString || g instanceof MultiCurve || g.getClass() == GeometryCollection.class) {
      for (int i = 0; i < g.getNumGeometries(); i++) {
        collectLinearMembers(g.getGeometryN(i), linear, nonLinear);
      }
      return;
    }
    nonLinear.add(g);
  }

  private static List<List<LineString>> buildChains(List<LineString> members) {
    List<List<LineString>> chains = new ArrayList<List<LineString>>();
    Set<LineString> unused = new LinkedHashSet<LineString>(members);
    while (!unused.isEmpty()) {
      LineString seed = unused.iterator().next();
      unused.remove(seed);
      Deque<LineString> chain = new ArrayDeque<LineString>();
      chain.add(seed);

      Coordinate head = first(seed);
      Coordinate tail = last(seed);

      // Extend forward from chain tail.
      while (true) {
        LineString next = popMatching(unused, tail);
        if (next == null) break;
        if (last(next).equals(tail) && !first(next).equals(tail)) {
          next = reverseCurve(next);
        }
        chain.addLast(next);
        tail = last(next);
      }
      // Extend backward from chain head.
      while (true) {
        LineString prev = popMatching(unused, head);
        if (prev == null) break;
        if (first(prev).equals(head) && !last(prev).equals(head)) {
          prev = reverseCurve(prev);
        }
        chain.addFirst(prev);
        head = first(prev);
      }
      chains.add(new ArrayList<LineString>(chain));
    }
    return chains;
  }

  private static LineString popMatching(Set<LineString> unused, Coordinate at) {
    Iterator<LineString> it = unused.iterator();
    while (it.hasNext()) {
      LineString m = it.next();
      if (first(m).equals(at) || last(m).equals(at)) {
        it.remove();
        return m;
      }
    }
    return null;
  }

  private static Geometry emitChain(List<LineString> chain, GeometryFactory factory) {
    if (chain.size() == 1) return chain.get(0);
    boolean allLine = true;
    boolean allArc = true;
    for (LineString m : chain) {
      if (m instanceof CircularString) allLine = false;
      else allArc = false;
    }
    if (allLine) return concatLineStrings(chain, factory);
    if (allArc) return concatCircularStrings(chain, factory);
    return new CompoundCurve(chain.toArray(new LineString[0]), factory);
  }

  private static LineString concatLineStrings(List<LineString> chain, GeometryFactory factory) {
    List<Coordinate> all = new ArrayList<Coordinate>();
    for (int i = 0; i < chain.size(); i++) {
      Coordinate[] cc = chain.get(i).getCoordinates();
      int start = (i == 0) ? 0 : 1;
      for (int j = start; j < cc.length; j++) all.add(cc[j]);
    }
    return factory.createLineString(all.toArray(new Coordinate[0]));
  }

  private static CircularString concatCircularStrings(List<LineString> chain, GeometryFactory factory) {
    List<Coordinate> all = new ArrayList<Coordinate>();
    for (int i = 0; i < chain.size(); i++) {
      Coordinate[] cc = chain.get(i).getCoordinates();
      int start = (i == 0) ? 0 : 1;
      for (int j = start; j < cc.length; j++) all.add(cc[j]);
    }
    CoordinateSequence seq = factory.getCoordinateSequenceFactory()
        .create(all.toArray(new Coordinate[0]));
    if (factory instanceof CurveGeometryFactory) {
      return ((CurveGeometryFactory) factory).createCircularString(seq);
    }
    return new CircularString(seq, factory);
  }

  private static LineString reverseCurve(LineString ls) {
    Coordinate[] c = ls.getCoordinates();
    Coordinate[] r = new Coordinate[c.length];
    for (int i = 0; i < c.length; i++) r[i] = c[c.length - 1 - i];
    GeometryFactory f = ls.getFactory();
    if (ls instanceof CircularString) {
      CoordinateSequence seq = f.getCoordinateSequenceFactory().create(r);
      if (f instanceof CurveGeometryFactory) {
        return ((CurveGeometryFactory) f).createCircularString(seq);
      }
      return new CircularString(seq, f);
    }
    return f.createLineString(r);
  }

  private static Coordinate first(LineString ls) { return ls.getCoordinateN(0); }
  private static Coordinate last(LineString ls)  { return ls.getCoordinateN(ls.getNumPoints() - 1); }

}

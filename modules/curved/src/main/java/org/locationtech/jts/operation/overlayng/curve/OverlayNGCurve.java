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
package org.locationtech.jts.operation.overlayng.curve;

import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.IntersectionMatrix;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.curved.CompoundCurve;
import org.locationtech.jts.geom.curved.CurveOps;
import org.locationtech.jts.geom.curved.CurvePolygon;

/**
 * Curve-aware overlay: the four set operations on geometries whose rings may be
 * arcs.
 * <p>
 * <b>Naming (NTSC0001).</b> This type is {@code OverlayNGCurve}, never
 * {@code OverlayNGCurved}.
 * <p>
 * <b>The four operations.</b>
 * <table border="1">
 * <caption>Operation mnemonics</caption>
 * <tr><th>Sticky</th><th>Symbol</th><th>Method</th><th>Phrase</th><th>Self-op</th></tr>
 * <tr><td>CAP</td><td>&cap;</td><td>{@link #intersection}</td>
 *     <td>Common Area of Partners -- only where both stand</td>
 *     <td>A &cap; A &rarr; A</td></tr>
 * <tr><td>CUP</td><td>&cup;</td><td>{@link #union}</td>
 *     <td>Cover Under Partners -- either side fills the cup</td>
 *     <td>A &cup; A &rarr; A</td></tr>
 * <tr><td>SUB</td><td>&#8726;</td><td>{@link #difference}</td>
 *     <td>Subtract B's shadow -- erase the second from the first</td>
 *     <td>A &#8726; A &rarr; &empty;</td></tr>
 * <tr><td>XOR</td><td>&Delta;</td><td>{@link #symDifference}</td>
 *     <td>eXclusive OR -- keep only what isn't shared</td>
 *     <td>A &Delta; A &rarr; &empty;</td></tr>
 * </table>
 * <p>
 * CAP and CUP keep me, SUB and XOR empty me, and the empty partner is the fifth
 * guard.
 * <p>
 * <b>The ratchet.</b> Answers are reached in order of cost, cheapest first, and
 * each stage that can answer exactly does so:
 * <ol>
 * <li><b>G5</b> -- an empty operand. Structural, no densification.</li>
 * <li><b>G1&ndash;G4</b> -- the operands are the same geometry. Structural, no
 *     densification (<b>F1</b>: fast before fat).</li>
 * <li><b>R1</b> -- one operand covers the other, or they are disjoint, so the
 *     answer <em>is</em> one of the operands and can be returned untouched. The
 *     predicate is evaluated on densified copies, so this densifies to
 *     <em>decide</em> but never to <em>answer</em>.</li>
 * <li>Otherwise, densify both and delegate to core, flagging the result
 *     approximate (<b>R2</b>).</li>
 * </ol>
 * The distinction in stage 3 is the one that matters: an exact answer chosen by a
 * tolerance-bounded decision is still exact, but the decision can be wrong for
 * operands closer together than {@link CurveOps#TOLERANCE_FRACTION}. That is a
 * narrower exposure than approximating every answer, and it is the same trade the
 * prepared-geometry filters in the app's {@code OverlayNGOptFunctions} make.
 * <p>
 * <b>Known limitation: a polygon whose vertices lie exactly on the arc.</b> The
 * densified ring is <em>inscribed</em>, so it passes just inside such a vertex --
 * 1.6e-18 inside, at the top of a radius-5 circle. R1 therefore correctly declines
 * to call the polygon covered, the operation falls through to core, and core
 * rejects the near-coincident vertices with
 * {@code TopologyException: found non-noded intersection}. Nothing here can fix
 * that: an inscribed approximation cannot contain a point on the curve it
 * approximates. A circumscribed or straddling densification would move the problem
 * rather than remove it. This is the case an arc-aware noder exists to handle.
 * <p>
 * <b>Not yet implemented: V1 and V2.</b> The wound check (reject multi-wound or
 * self-crossing structural shells) and the hole-nest check (holes properly
 * interior to the shell) are specified for Phase 0 but deliberately absent here
 * rather than present and untested. Validation that rejects input is exactly the
 * kind of code that must not be written ahead of its tests: a false rejection
 * turns valid geometry into an exception. {@code V3}, the type gate, is
 * implemented and covered -- plain input takes the same path and matches stock
 * {@code OverlayNG} bit for bit.
 */
public class OverlayNGCurve {

  /** CAP: Common Area of Partners -- only where both stand. */
  public static final int INTERSECTION =
      org.locationtech.jts.operation.overlayng.OverlayNG.INTERSECTION;
  /** CUP: Cover Under Partners -- either side fills the cup. */
  public static final int UNION =
      org.locationtech.jts.operation.overlayng.OverlayNG.UNION;
  /** SUB: subtract B's shadow. */
  public static final int DIFFERENCE =
      org.locationtech.jts.operation.overlayng.OverlayNG.DIFFERENCE;
  /** XOR: keep only what isn't shared. */
  public static final int SYMDIFFERENCE =
      org.locationtech.jts.operation.overlayng.OverlayNG.SYMDIFFERENCE;

  /** CAP -- only where both stand. */
  public static Geometry intersection(Geometry a, Geometry b) {
    return overlay(a, b, INTERSECTION);
  }

  /** CUP -- either side fills the cup. */
  public static Geometry union(Geometry a, Geometry b) {
    return overlay(a, b, UNION);
  }

  /** SUB -- erase the second from the first. */
  public static Geometry difference(Geometry a, Geometry b) {
    return overlay(a, b, DIFFERENCE);
  }

  /** XOR -- keep only what isn't shared. */
  public static Geometry symDifference(Geometry a, Geometry b) {
    return overlay(a, b, SYMDIFFERENCE);
  }

  public static Geometry overlay(Geometry a, Geometry b, int opCode) {
    return new OverlayNGCurve(a, b).getResult(opCode);
  }

  private final Geometry a;
  private final Geometry b;
  private boolean isApproximate;

  public OverlayNGCurve(Geometry a, Geometry b) {
    this.a = a;
    this.b = b;
  }

  /**
   * True if the last {@link #getResult(int)} call reached its answer by densifying
   * an arc, so the answer is accurate only to {@link CurveOps#TOLERANCE_FRACTION}.
   * <p>
   * False when the answer was exact: an algebraic identity (G1&ndash;G4), an empty
   * operand (G5), or an operand returned unchanged (R1). In the R1 case the
   * <em>answer</em> is exact even though the <em>decision</em> to return it was
   * made on densified copies.
   */
  public boolean isApproximate() {
    return isApproximate;
  }

  public Geometry getResult(int opCode) {
    isApproximate = false;

    Geometry byEmpty = emptyPartner(opCode);        // G5
    if (byEmpty != null) return byEmpty;

    if (isSameGeometry(a, b)) return selfOp(opCode); // G1-G4, before any densify

    Geometry retained = retainOperand(opCode);      // R1
    if (retained != null) return retained;

    isApproximate = true;                           // R2
    return org.locationtech.jts.operation.overlayng.OverlayNG.overlay(
        CurveOps.linearise(a), CurveOps.linearise(b), opCode);
  }

  // -- G5: nothing in the room --------------------------------------------

  /** @return the answer, or {@code null} if neither operand is empty */
  private Geometry emptyPartner(int opCode) {
    boolean aEmpty = a == null || a.isEmpty();
    boolean bEmpty = b == null || b.isEmpty();
    if (!aEmpty && !bEmpty) return null;
    if (aEmpty && bEmpty) return empty(opCode);
    if (aEmpty) {
      // Nothing on the left: CAP and SUB vanish, CUP and XOR are just B.
      return (opCode == INTERSECTION || opCode == DIFFERENCE) ? empty(opCode) : b.copy();
    }
    // Nothing on the right: only CAP vanishes.
    return opCode == INTERSECTION ? empty(opCode) : a.copy();
  }

  // -- G1..G4: self-operations -------------------------------------------

  private Geometry selfOp(int opCode) {
    // CAP and CUP keep me; SUB and XOR empty me.
    return (opCode == INTERSECTION || opCode == UNION) ? a.copy() : empty(opCode);
  }

  /**
   * Structural identity, which is <em>not</em> {@code equalsExact}.
   * <p>
   * {@code Polygon.equalsExact} compares the flat rings, and a
   * {@link CurvePolygon} presents its control points as that ring. So two
   * CurvePolygons -- one with a {@code CIRCULARSTRING} ring, one with a straight
   * ring through the same control points -- are {@code equalsExact} while
   * enclosing {@code 25*pi} and 50 respectively. Treating that pair as a
   * self-operation would return the circle as the intersection of a circle with a
   * diamond. The structural ring comparison is what rules that out.
   * <p>
   * The class check is belt and braces rather than load-bearing:
   * {@code Geometry.equalsExact} already begins with {@code isEquivalentClass}, an
   * exact class-name comparison, so a CurvePolygon is never {@code equalsExact} to
   * a plain Polygon. It is kept because relying on the internals of an inherited
   * equality method to enforce a correctness guard is not a bet worth taking.
   */
  static boolean isSameGeometry(Geometry g1, Geometry g2) {
    if (g1 == g2) return true;
    if (g1 == null || g2 == null) return false;
    if (g1.getClass() != g2.getClass()) return false;
    if (!g1.equalsExact(g2)) return false;
    if (g1 instanceof CurvePolygon) {
      CurvePolygon p1 = (CurvePolygon) g1, p2 = (CurvePolygon) g2;
      if (!isSameRing(p1.getExteriorCurve(), p2.getExteriorCurve())) return false;
      if (p1.getNumInteriorRing() != p2.getNumInteriorRing()) return false;
      for (int i = 0; i < p1.getNumInteriorRing(); i++) {
        if (!isSameRing(p1.getInteriorCurveN(i), p2.getInteriorCurveN(i))) return false;
      }
      return true;
    }
    if (g1 instanceof CompoundCurve) {
      CompoundCurve c1 = (CompoundCurve) g1, c2 = (CompoundCurve) g2;
      if (c1.getNumMembers() != c2.getNumMembers()) return false;
      for (int i = 0; i < c1.getNumMembers(); i++) {
        if (!isSameRing(c1.getMemberN(i), c2.getMemberN(i))) return false;
      }
      return true;
    }
    return true;
  }

  private static boolean isSameRing(LineString r1, LineString r2) {
    if (r1 == r2) return true;
    if (r1 == null || r2 == null) return false;
    if (r1.getClass() != r2.getClass()) return false;
    return r1.equalsExact(r2);
  }

  // -- R1: retention when the answer is an operand -----------------------

  /**
   * Returns an operand untouched when the topology makes it the whole answer.
   * <p>
   * The predicate runs on densified copies, so the <em>decision</em> is bounded by
   * {@link CurveOps#TOLERANCE_FRACTION} while the <em>answer</em> is exact.
   * Cases left to core deliberately: a disjoint CUP or XOR, whose answer is both
   * operands together and so needs a multi-surface result rather than an operand.
   *
   * @return the answer, or {@code null} to fall through to core
   */
  private Geometry retainOperand(int opCode) {
    IntersectionMatrix im = CurveOps.linearise(a).relate(CurveOps.linearise(b));
    if (!im.isIntersects()) {
      if (opCode == INTERSECTION) return empty(opCode);
      if (opCode == DIFFERENCE) return a.copy();
      return null;                       // CUP and XOR need both operands
    }
    if (im.isCovers()) {
      if (opCode == INTERSECTION) return b.copy();
      if (opCode == UNION) return a.copy();
      return null;
    }
    if (im.isCoveredBy()) {
      if (opCode == INTERSECTION) return a.copy();
      if (opCode == UNION) return b.copy();
      if (opCode == DIFFERENCE) return empty(opCode);
      return null;
    }
    return null;
  }

  // -- helpers ------------------------------------------------------------

  /**
   * An empty result of the dimension the operation yields: the lower of the two
   * for CAP, the higher otherwise, following the OGC result-dimension rule.
   */
  private Geometry empty(int opCode) {
    int dimA = a == null ? 2 : a.getDimension();
    int dimB = b == null ? 2 : b.getDimension();
    int dim = opCode == INTERSECTION ? Math.min(dimA, dimB) : Math.max(dimA, dimB);
    Geometry source = a != null ? a : b;
    return source.getFactory().createEmpty(dim);
  }
}

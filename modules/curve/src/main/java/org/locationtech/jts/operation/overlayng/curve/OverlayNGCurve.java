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

import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.IntersectionMatrix;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.geom.curve.CompoundCurve;
import org.locationtech.jts.geom.curve.CurveOps;
import org.locationtech.jts.geom.curve.CurvePolygon;
import org.locationtech.jts.geom.curve.Linearizable;
import org.locationtech.jts.geom.curve.MultiSurface;

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
 * each stage that can answer exactly does so. A stage that would be
 * <em>slower</em> than the locationtech/jts chord baseline -- linearise at
 * {@link CurveOps#TOLERANCE_FRACTION}, then {@code OverlayNGRobust} -- is
 * refused, and the baseline runs instead (<b>PERF-GATE</b>):
 * <ol>
 * <li><b>G5</b> -- an empty operand. Structural, no densification.</li>
 * <li><b>G1&ndash;G4</b> -- the operands are the same geometry. Structural, no
 *     densification (<b>F1</b>: fast before fat).</li>
 * <li><b>R0</b> -- the true envelopes do not intersect. The geometries are
 *     disjoint, so the answer is empty, an operand, or both operands as a
 *     {@link MultiSurface}. No densification. Arc envelopes are the AABB of
 *     the arc, not of the control points, so this is exact.</li>
 * <li><b>R1</b> -- only when one envelope covers the other (or both operands
 *     are plain). One operand covers the other, or they are disjoint inside a
 *     shared AABB, so the answer <em>is</em> an operand. The predicate runs on
 *     copies densified at {@link #DECIDE_TOLERANCE_FRACTION}, so this densifies
 *     to <em>decide</em> but never to <em>answer</em>. Crossing-shaped
 *     envelopes skip this stage: paying a fine {@code relate} and then falling
 *     through was measured 11&times; slower than the chord overlay alone.</li>
 * <li><b>R1.5</b> -- both operands are circular discs and they cross at two
 *     proper nodes. The answer is a {@link CurvePolygon} of two circular
 *     arcs (lens, blob, crescent) or a {@link MultiSurface} of two crescents.
 *     Nested discs (0 nodes, one strictly inside the other) are the
 *     annulus: SUB the outer with the inner as a hole, XOR the same.
 *     Closed form; no densification. 1 intersection, a tangent nest,
 *     a mixed CompoundCurve nest ({@code CC-NEST-ANNULUS}), or
 *     a non-disc, falls through without paying this path.</li>
 * <li><b>R1.6</b> -- one operand is a circular disc and the other is a
 *     plain Polygon (no curve rings, no holes), and they meet at two
 *     proper line–circle nodes. The answer is a {@link CurvePolygon}
 *     (or a {@link MultiSurface} for XOR) that keeps the surviving arcs.
 *     Closed form; no densification. An even run of 4+ alternating
 *     line–circle nodes is the same assemble with n spans. Any other
 *     pair returns {@code null} without paying this path.</li>
 * <li><b>R1.7</b> -- one operand is a hole-free {@link CurvePolygon} whose
 *     shell is a mixed {@link org.locationtech.jts.geom.curve.CompoundCurve}
 *     (LineString + CircularString: a half-disc or stadium) and the other
 *     is a circular disc or a plain Polygon, meeting at two proper nodes.
 *     The answer is a {@link CurvePolygon} whose shell is a CompoundCurve
 *     of the surviving pieces (or a {@link MultiSurface} for XOR). A
 *     LineString member stays a segment. Closed form; no densification.
 *     Complementary half-discs of the same circle (shared diameter)
 *     are CAP empty / CUP the disc / SUB the first half. Same-circle
 *     half-discs whose diameters are perpendicular assemble as sectors.
 *     Collinear same-side half-discs are identity, nested, a
 *     half-lens, or a point-touch. Any other two hole-free
 *     CompoundCurve shells with exactly two proper nodes walk the
 *     surviving pieces; 0 / 1 node is containment or a disjoint
 *     touch. An even 4+ alternating cut of two CompoundCurve shells
 *     is the H-FOUR n-span assemble. Two crossings plus a tangent
 *     is the same assemble with the touch as a zero-length span.
 *     A same-outer hole-inside pair
 *     is the holed / unholed / hole polygon. A different-outer hole
 *     whose outers already clip composes: hole strictly inside the
 *     outer CAP is punched, hole strictly outside is ignored on
 *     CAP. A hole that meets or crosses the other outer shares
 *     the clip edge (a bite, not an interior punch). Two holes
 *     that cross are a noder. Collinear overlap, mixed labels,
 *     or a line-only shell return {@code null} without paying
 *     this path.</li>
 * <li><b>R-LL</b> -- one operand is a {@link org.locationtech.jts.geom.curve.CircularString}
 *     (or a lineal CompoundCurve of LineString + CircularString) and the
 *     other is a plain LineString. Line–circle nodes are exact. CAP is
 *     the node Point / MultiPoint; CUP / SUB / XOR keep CircularString
 *     pieces. A three-point LineString is not an arc. Two CircularStrings,
 *     a polygon, or 3+ nodes on one arc window return {@code null}
 *     without paying this path.</li>
 * <li><b>R-AA</b> -- both operands are a {@link org.locationtech.jts.geom.curve.CircularString}
 *     (or a lineal CompoundCurve of LineString + CircularString). Nodes
 *     are the circle–circle hits that lie on both sweeps, not the
 *     control-chord crossings. CAP is the node Point / MultiPoint; CUP /
 *     SUB / XOR keep CircularString pieces. Same-circle overlap is
 *     angular-interval overlay on that circle. A three-point
 *     LineString, or 3+ nodes on one sweep window of different
 *     circles, return {@code null} without paying this path.</li>
 * <li>Otherwise, densify both at the ops tolerance and delegate to core,
 *     flagging the result approximate (<b>R2</b>). This <em>is</em> the chord
 *     baseline.</li>
 * </ol>
 * R1.5–R1.7 share package-private {@code TwoNodeClip} for the two-node
 * walk (hits, ring / member walk, CAP / CUP / SUB / XOR). Even-n
 * assemble is {@code NSpanClip}. R1.7 dispatch is
 * {@code CompoundCurveShellOverlay} (hole / half-disc / two-shell /
 * vs disc or polygon). R-LL and R-AA reuse the same intersection
 * primitives. Each rung keeps its own shape dispatch.
 * The distinction in R0/R1 is the one that matters: an exact answer chosen by a
 * tolerance-bounded decision is still exact, but the decision can be wrong for
 * operands closer together than the decide-tolerance. That is a narrower
 * exposure than approximating every answer, and it is the same trade the
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

  /**
   * Densification fraction used only to <em>decide</em> covers / disjoint in
   * R1, as a fraction of the geometry's extent. Coarser than
   * {@link CurveOps#TOLERANCE_FRACTION} (1e-6) so a retention attempt stays
   * cheaper than noding the fine chords. The answer, when retention fires, is
   * still the original operand.
   */
  static final double DECIDE_TOLERANCE_FRACTION = 1.0e-3;

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
   * operand (G5), envelope-disjoint (R0), an operand returned unchanged (R1), or
   * an operand with no arc in it at all, which is handed to core untouched, or
   * two crossing circular discs answered as arcs (R1.5), or two
   * nested discs answered as the annulus, or a disc
   * clipped by a plain polygon at line–circle nodes (R1.6), or a
   * CompoundCurve-shelled CurvePolygon clipped at two nodes (R1.7), or a
   * CircularString noded against a LineString (R-LL), or two
   * CircularStrings noded at circle–circle hits on both sweeps (R-AA),
   * including same-circle angular-interval overlay, complementary
   * half-discs, perpendicular same-circle half-disc sectors, a
   * two-node walk of two CompoundCurve shells, an even 4+
   * alternating cut of two CompoundCurve shells, an odd cut
   * whose only non-alternation is a tangent (degenerate NSpan),
   * a same-outer
   * hole-inside pair, a different-outer hole composed from a
   * certified outer clip, and an even 4+ line–circle cut of a disc
   * by a plain polygon. In
   * the R1 case the <em>answer</em> is exact even though the <em>decision</em>
   * to return it was made on densified copies.
   * <p>
   * So this is false for every plain-geometry overlay, which is part of V3: routing
   * plain input through this class must be indistinguishable from calling stock
   * {@code OverlayNG}, honesty flag included.
   */
  public boolean isApproximate() {
    return isApproximate;
  }

  public Geometry getResult(int opCode) {
    isApproximate = false;

    Geometry byEmpty = emptyPartner(opCode);        // G5
    if (byEmpty != null) return byEmpty;

    if (isSameGeometry(a, b)) return selfOp(opCode); // G1-G4, before any densify

    Geometry byEnvelope = disjointByEnvelope(opCode); // R0
    if (byEnvelope != null) return byEnvelope;

    if (shouldAttemptRetention(opCode)) {
      Geometry retained = retainOperand(opCode);    // R1, only when it can win
      if (retained != null) return retained;
    }

    Geometry discs = CircularDiscOverlay.overlay(a, b, opCode); // R1.5
    if (discs != null) return discs;

    Geometry discPoly = CircularDiscPolygonOverlay.overlay(a, b, opCode); // R1.6
    if (discPoly != null) return discPoly;

    Geometry shellClip = CompoundCurveShellOverlay.overlay(a, b, opCode); // R1.7
    if (shellClip != null) return shellClip;

    Geometry lineClip = CircularLineOverlay.overlay(a, b, opCode); // R-LL
    if (lineClip != null) return lineClip;

    Geometry arcClip = CircularArcOverlay.overlay(a, b, opCode); // R-AA
    if (arcClip != null) return arcClip;

    // R2. The chord baseline: densify at the ops tolerance and run core.
    // Approximate only if something was actually densified: for operands with
    // no arc, linearise returns them unchanged and core's answer is exact, so
    // flagging it would be a false warning -- and a flag that cries wolf on plain
    // input is one callers learn to ignore.
    isApproximate = CurveOps.tolerance(a) > 0.0 || CurveOps.tolerance(b) > 0.0;
    // OverlayNGRobust rather than bare OverlayNG: since this class now backs the
    // instance methods (CurveOps routes intersection/union/difference/
    // symDifference here), the fall-through must keep the snapping fallbacks
    // Geometry's own overlay path has -- a noding failure on hard input should
    // degrade to a snapped answer, not to a TopologyException the plain-geometry
    // path would have survived.
    return org.locationtech.jts.operation.overlayng.OverlayNGRobust.overlay(
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

  // -- R0: envelope-disjoint, exact, no densify --------------------------

  /**
   * When the true envelopes do not intersect the geometries cannot. Arc
   * envelopes cover the arc, so this is not the control-polygon false
   * disjoint that CRV-REL closed.
   *
   * @return the disjoint answer, or {@code null} if the envelopes meet or
   *         a non-polygonal CUP/XOR needs core to build the combination
   */
  private Geometry disjointByEnvelope(int opCode) {
    Envelope ea = a.getEnvelopeInternal();
    Envelope eb = b.getEnvelopeInternal();
    if (ea.intersects(eb)) return null;
    return disjointAnswer(opCode);
  }

  /**
   * True when a retention attempt is allowed to run. Crossing-shaped
   * envelopes (they meet, neither covers) skip R1: the fine-densify
   * {@code relate} plus boundary-distance was measured slower than the
   * chord overlay, and then still fell through to it.
   * <p>
   * SUB when A's envelope covers B's (and not the reverse) is an annulus:
   * R1 cannot return an operand, so trying it is the same wasted relate
   * the crossing gate already refused. B covering A can still be empty
   * and is worth the attempt.
   * <p>
   * Plain operands always attempt -- their relate is cheap and V3 requires
   * the same exact short-circuits stock OverlayNG already offers.
   */
  private boolean shouldAttemptRetention(int opCode) {
    if (CurveOps.tolerance(a) <= 0.0 && CurveOps.tolerance(b) <= 0.0) return true;
    Envelope ea = a.getEnvelopeInternal();
    Envelope eb = b.getEnvelopeInternal();
    if (opCode == DIFFERENCE && ea.covers(eb) && !eb.covers(ea)) {
      return false;
    }
    return ea.covers(eb) || eb.covers(ea);
  }

  // -- R1: retention when the answer is an operand -----------------------

  /**
   * Returns an operand untouched when the topology makes it the whole answer.
   * <p>
   * The predicate runs on copies densified at {@link #DECIDE_TOLERANCE_FRACTION},
   * so the <em>decision</em> is bounded by that tolerance while the
   * <em>answer</em> is exact. Cases left to core deliberately: a disjoint CUP
   * or XOR of non-polygons, whose answer is both operands together and so
   * needs a multi-geometry result rather than an operand.
   *
   * @return the answer, or {@code null} to fall through to core
   */
  private Geometry retainOperand(int opCode) {
    Geometry la = lineariseToDecide(a);
    Geometry lb = lineariseToDecide(b);

    if (!isDecisive(la, lb)) return null;

    IntersectionMatrix im = la.relate(lb);
    if (!im.isIntersects()) {
      return disjointAnswer(opCode);
    }
    if (im.isCovers()) {
      if (opCode == INTERSECTION) return b.copy();
      if (opCode == UNION) return a.copy();
      return null;                       // SUB is an annulus, XOR likewise
    }
    if (im.isCoveredBy()) {
      if (opCode == INTERSECTION) return a.copy();
      if (opCode == UNION) return b.copy();
      if (opCode == DIFFERENCE) return empty(opCode);
      return null;
    }
    return null;
  }

  /**
   * True if the operands are far enough apart, boundary to boundary, that the
   * densification cannot have changed the topological verdict.
   * <p>
   * Every retention decision is made on inscribed copies, which lie up to
   * {@link #decideTolerance(Geometry)} <em>inside</em> the true arcs. So a
   * verdict can only be wrong within a band of that width: two arcs that truly
   * touch can look disjoint, and a geometry that truly pokes out can look
   * covered. Requiring the boundaries to be separated by more than the summed
   * decide-tolerance puts the decision outside that band, and anything closer
   * falls through to core -- which is also approximate, but computes the sliver
   * rather than dropping it.
   * <p>
   * This matters most for SUB and least for CAP, because the failure modes have
   * opposite polarity. A wrong disjoint verdict makes SUB return {@code a}
   * unchanged -- it fails to erase, and the result looks entirely plausible, so
   * nothing downstream can detect it. The same wrong verdict makes CAP return
   * empty, which under-claims by a sliver of order tolerance times chord length.
   * It matters for the new disjoint CUP and XOR path more than either: two
   * operands that truly touch have a single connected union, and returning a
   * two-member MultiSurface would be wrong in <em>topology</em>, not just area.
   * <p>
   * Plain operands have zero tolerance, so the gate is satisfied trivially and
   * non-curve behaviour is unchanged.
   */
  private boolean isDecisive(Geometry la, Geometry lb) {
    double margin = decideTolerance(a) + decideTolerance(b);
    if (margin <= 0.0) return true;
    return la.getBoundary().distance(lb.getBoundary()) > margin;
  }

  /**
   * The disjoint answer: empty CAP, A for SUB, both operands for CUP/XOR.
   *
   * @return the answer, or {@code null} if CUP/XOR cannot hold both operands
   */
  private Geometry disjointAnswer(int opCode) {
    if (opCode == INTERSECTION) return empty(opCode);
    if (opCode == DIFFERENCE) return a.copy();
    // Disjoint CUP and XOR are both operands side by side, which a MultiSurface
    // holds exactly, arcs intact. Null falls through if they are not polygonal.
    return bothOperands();
  }

  /**
   * The densification tolerance R1 uses to decide, and therefore the maximum
   * distance by which that decision's copies deviate from the true arc. Zero
   * for a geometry with no arc.
   */
  static double decideTolerance(Geometry g) {
    if (CurveOps.tolerance(g) <= 0.0) return 0.0;
    Envelope env = g.getEnvelopeInternal();
    double extent = Math.max(env.getWidth(), env.getHeight());
    return (extent > 0.0 ? extent : 1.0) * DECIDE_TOLERANCE_FRACTION;
  }

  private static Geometry lineariseToDecide(Geometry g) {
    if (CurveOps.tolerance(g) <= 0.0) return g;
    return ((Linearizable) g).toLinear(decideTolerance(g));
  }

  /**
   * The two operands as one geometry, arcs intact -- the exact answer for a
   * disjoint CUP or XOR.
   *
   * @return a MultiSurface, or {@code null} if either operand is not polygonal,
   *         in which case core is the honest fallback
   */
  private Geometry bothOperands() {
    if (!(a instanceof Polygon) || !(b instanceof Polygon)) return null;
    return new MultiSurface(
        new Polygon[] { (Polygon) a.copy(), (Polygon) b.copy() }, a.getFactory());
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

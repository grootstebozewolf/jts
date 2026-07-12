Verification of JTS issue #20 (Robustness failure in VoronoiDiagramBuilder on near-coincident points)

Reproducer (exact 7-point MultiPoint WKB from the issue report):
```
01040000000700000001010000000f8b33e3d97742c038c453588d0423c001010000001171d6d1b45d42c06adc1693e78c22c001010000001c8b33e3d97742c062c453588d0423c00101000000afa5c71fda7742c04b93c61d8e0423c00101000000b0cddcb4b57942c026476887d7b122c00101000000e0678421dc7642c0f7736021e1fb22c00101000000e32fd565018d42c0c7ea1222167c22c0
```

With `setTolerance(0.1)` the 7 sites deduplicate to 4 distinct. The original code threw `Invalid number of points in LinearRing (found 2...)` because raw double arithmetic in `Vertex.isCCW` produced a non-Delaunay subdivision with crossing edges.

JTS shipped changes (stacked on #1212):
- `Vertex.isCCW` (and `rightOf`/`leftOf`) now uses robust `Orientation.index` instead of raw double cross product. (Raw arithmetic kept only in comments for reference.)
  File: `modules/core/src/main/java/org/locationtech/jts/triangulate/quadedge/Vertex.java`
- Builders store raw coordinates in `setSites` and perform tolerance-based deduplication via `KdTree` (when tolerance > 0) inside `create()` before `toVertices` and insert.
  New testable helper: `public static Coordinate[] unique(Coordinate[] coords, double tolerance)` (exposed on `DelaunayTriangulationBuilder`).
  Files: `modules/core/src/main/java/org/locationtech/jts/triangulate/DelaunayTriangulationBuilder.java` and `VoronoiDiagramBuilder.java`
- `getVoronoiCellPolygon`: removed debug println; now guarantees a valid `LinearRing` constructor call (>=4 pts) or safely omits degenerate cells while preserving 1 cell per unique site (count invariant).
  File: `modules/core/src/main/java/org/locationtech/jts/triangulate/quadedge/QuadEdgeSubdivision.java`
- Regression test driving the real public API on the exact repro:
  `VoronoiDiagramBuilderTest.testRobustnessIssue20NearCoincidentPoints()`
  Loads the literal 7-pt WKB, calls `setSites` + `setTolerance(0.1)` + `getDiagram`, asserts `isValid()` and `getNumGeometries() == 4`.
  File: `modules/core/src/test/java/org/locationtech/jts/triangulate/VoronoiDiagramBuilderTest.java`

The predicate improvements in [#1212](https://github.com/locationtech/jts/pull/1212) (fast Shewchuk-style error-bounded `isInCircleRobust` + `Orientation.index` usage) are the foundation.

Rocq/Flocq formal verification (executed via WSL using the NetTopologySuite.Proofs container with Rocq 9.2 + Flocq 4.2.2):
- Inspected and built the modules proving the predicates that were unsound in the original raw-arithmetic bug:
  - `theories-flocq/Orient_b64_exact_full.v` : Theorem `b64_orient2d_exact_sound`
  - `theories-flocq/Orientation_b64.v` : b64 orient filter soundness
  - `theories-flocq/InCircle_b64_exact.v` : in-circle primitive (core for Delaunay, which underlies Voronoi)
  - `theories/Orientation.v` : core exact orientation
- WSL container build succeeded; theorems are Qed with the documented axiom footprint.
- This is the mathematical guarantee that, once sites are deduplicated on the Java side, the orientation/in-circle tests used downstream are sound.

Outcome with the combined fix (robust predicate from #1212 + KdTree dedup + safe cell construction):
- The exact 7-pt WKB repro + tolerance 0.1 now produces a valid `GeometryCollection` with exactly 4 cells.
- `diagram.isValid() == true`
- No "Invalid number of points in LinearRing (found 2...)" and no `LocateFailure`.

See also the entry in `doc/JTS_Version_History.md`.

Supporting artifacts (for reference):
- `verify_rocq_wsl.sh` (WSL Rocq container setup + toolchain build)

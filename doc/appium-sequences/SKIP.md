# Appium sequence skip list

Upstream golden failures or non-Exec / promote failures. Screenshots under `/opt/cursor/artifacts/appium/` when captured.
Bugs noted for example of use — **not fixed in this story**.

| ClaimId | Reason |
|---|---|
| `TB-AP-DIFF-SKIP` | no public static Geometry method |
| `TB-AP-ORIENTATION-SKIP` | predicates only (boolean) |
| `TB-AP-ORIENTATIONFP-SKIP` | predicates only (boolean) |
| `TB-AP-POINTLOCATION-SKIP` | no Geometry return |
| `TB-AP-PREPAREDGEOMETRY-SKIP` | boolean/prepared API |
| `TB-AP-SPATIALPREDICATE-SKIP` | boolean predicates |
| `TB-AP-SPATIALPREDICATENG-SKIP` | boolean predicates |
| `TB-AP-WRITER-SKIP` | string/IO writers |
| `TB-AP-CREATESHAPE-SKIP` | fontGlyphSerif needs string; grid uncertain — deferred |
| `TB-AP-EDIT-SKIP` | addHole needs second geometry hole |
| `TB-AP-METRIC-SKIP` | segmentLengths sampling — nonstandard return |
| `TB-AP-SPATIALINDEX-SKIP` | kdTreeQuery needs pts+query env |
| `TB-AP-USERDATA-SKIP` | length may not return Geometry |
| `TB-AP-CREATEFRACTALSHAPE-KOCHSNOWFLAKE` | promote: polygon=EXC:RuntimeException;disc=EXC:RuntimeException;circle=EXC:RuntimeException;half-moon=EXC:RuntimeException; |
| `TB-AP-LABELLING-LABELPOINT` | promote: Labelling.labelPoint |
| `TB-AP-LINEARREFERENCING-EXTRACTPOINT` | promote: polygon=EXC:IllegalArgumentException;disc=EXC:IllegalArgumentException;circle=EXC:IllegalArgumentException;half-moon=EXC:IllegalArgumentException; |
| `TB-AP-OVERLAYCOMMONBITSREMOVED-UNION` | promote: OverlayCommonBitsRemoved.union |
| `TB-AP-OVERLAYENHANCEDPRECISION-UNION` | promote: OverlayEnhancedPrecision.union |
| `TB-AP-OVERLAYNGSNAPPING-UNION` | promote: polygon=OK;disc=EXC:TopologyException;circle=OK;half-moon=OK; |
| `TB-AP-TESTCASEGEOMETRY-BUFFERMITREDJOIN` | promote: TestCaseGeometry.bufferMitredJoin |

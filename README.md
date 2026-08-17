JTS Topology Suite
==================

The JTS Topology Suite is a Java library for creating and manipulating vector geometry.  It also provides a comprehensive set of geometry test cases, and the TestBuilder GUI application for working with and visualizing geometry and JTS functions.

![JTS wordmark as curves plus a buffer halo](jts_logo.png)

JTS wordmark as curves plus a buffer halo (`logoLines` + `logoBuffer`: `toLinear` + `BufferOp`). Named linear fallback / CHORD-PATH. Not a laser.

<!-- HERO: 2017 jts_logo.png is the door image. It is logoLines +
     logoBuffer (toLinear + BufferOp): named linear fallback / CHORD-PATH,
     not a laser. Do not caption a clothoid halo. #55 clothoidHalo is not
     this door. Do not commit mkt1_1920x1080.png as a clothoid. -->

This fork treats SQL/MM ISO/IEC 13249-3 curve types 8–12 (`CIRCULARSTRING`, `COMPOUNDCURVE`, `CURVEPOLYGON`, `MULTICURVE`, `MULTISURFACE`) as first-class geometry. WKB/WKT ISO and EXTENDED Z/M/ZM landed in [#51](https://github.com/grootstebozewolf/jts/pull/51) (`CircularStringZ=1008`, …). Types 15–17 still unknown and throw. Core `WKBWriter` refuses to flatten 8–12. TestBuilder lives in `modules/app`.

### User path

Build this tree and run TestBuilder. Load a `CIRCULARSTRING` — it must stay a `CIRCULARSTRING` (smooth arc), not a flattened `LINESTRING`. There is no Maven Central curve artifact for this work.

* [User Guide](USING.md)
* [JTS TestBuilder](doc/JTSTestBuilder.md)

### Developer path

Working branch / source of truth: [`feature/sfa-curve-rgr`](https://github.com/grootstebozewolf/jts/tree/feature/sfa-curve-rgr). `mvn clean install` from the [Developing Guide](DEVELOPING.md). Curve work lives in the curve module. Do not silently flatten. Named linear fallback only if named. MMF Option B quality gate: [doc/MMF_OPTION_B.md](doc/MMF_OPTION_B.md) (draft [PR #61](https://github.com/grootstebozewolf/jts/pull/61)).

* [Developing Guide](DEVELOPING.md)
* [Contributing Guide](CONTRIBUTING.md)

[![GitHub Action Status](https://github.com/locationtech/jts/workflows/GitHub%20CI/badge.svg)](https://github.com/locationtech/jts/actions) 

[![Join the chat at https://gitter.im/locationtech/jts](https://badges.gitter.im/locationtech/jts.svg)](https://gitter.im/locationtech/jts?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)

JTS is a project in the [LocationTech](https://www.locationtech.org) working group of the Eclipse Foundation.

![LocationTech](locationtech_mark.png) 

## Requirements

Currently JTS targets Java 8 and above.

## Resources

### Code
* [This fork](https://github.com/grootstebozewolf/jts) — working branch [`feature/sfa-curve-rgr`](https://github.com/grootstebozewolf/jts/tree/feature/sfa-curve-rgr)
* [Upstream LocationTech](https://github.com/locationtech/jts)
* [Maven Central group](https://mvnrepository.com/artifact/org.locationtech.jts) (upstream releases; this fork has no curve artifact there)

### Websites
* [LocationTech Home](https://locationtech.org/projects/technology.jts)
* [GitHub web site](https://locationtech.github.io/jts/)

### Communication
* [Mailing List](https://accounts.eclipse.org/mailing-list/jts-dev)
* [Gitter Channel](https://gitter.im/locationtech/jts)

### Forums
* [Stack Overflow](https://stackoverflow.com/questions/tagged/jts)
* [GIS Stack Exchange](https://gis.stackexchange.com/questions/tagged/jts-topology-suite)

## License

JTS is open source software.  It is dual-licensed under:

* [Eclipse Public License 2.0](https://www.eclipse.org/legal/epl-v20.html)
* [Eclipse Distribution License 1.0](https://www.eclipse.org/org/documents/edl-v10.php) (a BSD Style License)

See also:

* [License details](LICENSES.md)
* Licensing [FAQ](FAQ-LICENSING.md)

## Documentation

* [**Javadoc**](https://locationtech.github.io/jts/javadoc) for the latest version of JTS
* [**FAQ**](https://locationtech.github.io/jts/jts-faq.html) - Frequently Asked Questions 
* [**User Guide**](USING.md) - Installing and using JTS 
* [**Tools**](doc/TOOLS.md) - Guide to tools included with JTS
* [**Developing Guide**](DEVELOPING.md) - how to build and develop for JTS
* [**Upgrade Guide**](MIGRATION.md) - How to migrate from previous versions of JTS

## History

* [**Version History**](https://github.com/locationtech/jts/blob/master/doc/JTS_Version_History.md)
* History from the previous JTS SourceForge repo is in the branch [`_old/history`](https://github.com/locationtech/jts/tree/_old/history)
* Older versions of JTS can be found on SourceForge
* There is an archive of distros of older versions [here](https://github.com/dr-jts/jts-versions)

## Contributing

If you are interested in contributing to JTS please read the [**Contributing Guide**](CONTRIBUTING.md).

## Downstream Projects

### Derivatives (ports to other languages)
* [**GEOS**](https://trac.osgeo.org/geos) - C++
* [**NetTopologySuite**](https://github.com/NetTopologySuite/NetTopologySuite) - .NET
* [**JSTS**](https://github.com/bjornharrtell/jsts) - JavaScript
* [**dart_jts**](https://github.com/moovida/dart_jts) - Dart

### Via GEOS
* [**Shapely**](https://github.com/Toblerity/Shapely) - Python wrapper of GEOS
* [**R-GEOS**](https://cran.r-project.org/web/packages/rgeos/index.html) - R wrapper of GEOS
* [**rgeo**](https://github.com/rgeo/rgeo) - Ruby wrapper of GEOS
* [**GEOSwift**](https://github.com/GEOSwift/GEOSwift)- Swift library using GEOS

There are many projects using GEOS - for a list see the [GEOS wiki](https://trac.osgeo.org/geos/wiki/Applications).



# JTS 2003–2005 guides (LaTeX)

Sources for the three in-tree manuals. Rebuild with `pdflatex` (TeX Live):

```
cd doc/latex
make
```

That writes:

- `doc/JTS Developer Guide.pdf`
- `doc/JTS TestBuilder & TestRunner User Guide.pdf`
- `doc/JTS Technical Specs.pdf`

Chapter plans follow the 2003–2005 Amyuni conversions. Prose SoT
tracks MMF draft [#61](https://github.com/grootstebozewolf/jts/pull/61)
on fork [#7](https://github.com/grootstebozewolf/jts/pull/7)
(`feature/sfa-curve-rgr`). Public DHD two-pair lock via `0ca71b`
(APEX 3.967640600249787; two circular discs). Guide JAR pin stays
`61eb3377` for figure rasters. CircularString / CompoundCurve /
CurvePolygon. Cite ISO/IEC 13249-3 for WKB 8–12 only. Preview
18–21 is not SIGNED I/O and not the curve SoT. No 13249-3 DOI;
no JTS DOI. DIS is not the 2016 IS.

Figure-slot frame lock:

- Guide shots are 4:3 at 1600×1200, full TestBuilder window, from
  `JTSTestBuilder-pr7.jar` @ `61eb3377`. Rasters live in
  `doc/latex/figures/`.
- Filled (batch 1): UG-1 / DG-1 draw CircularString; UG-2 A-blue /
  B-red icons; UG-3 CompoundCurve; UG-4 CurvePolygon disc; UG-7 /
  DG-3 / TS-2 disc hull \(25\pi\); TS-3 single-arc hull \(12.5\pi\);
  UG-8 / TS-6 compound H-CC hull 61.59119 (CurvePolygon with
  CircularString).
- Still empty (do not invent): DG-2 / TS-1 WKB; DG-4 / TS-4 /
  TS-5 / UG-5 / UG-6 D-HF. Slot id only; no caption on unshot work.
- MKT-1 is 16:9 1920×1080 canvas-only and is **not** a manual
  figure. Do not put it in these guides.

`doc/JTS_Version_History.md` is a separate concise-claims file; do
not edit it from this rebuild.

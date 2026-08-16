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
for D-HF is `cd3d70db` (#7 HEAD): public DHD two-pair lock via
`0ca71b` (APEX 3.967640600249787; discs 10.0). Guide JAR pin stays
`61eb3377` (the four later commits `03076dcf`, `91404d94`,
`097c9f44`, `cd3d70db` are docs-only). Do not claim the TestBuilder
JAR is from `cd3d70db`.

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

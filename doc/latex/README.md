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

Chapter plans follow the 2003–2005 Amyuni conversions. Curve, I/O,
overlay, and Hausdorff claims match the code on this tree.

Figure-slot frame lock:

- Guide shots (DG-1..4, TS-1..5, UG-1..7): empty 4:3 holes at
  1600×1200, full TestBuilder window. Slot id only; no caption on
  unshot work. Do not invent screenshots.
- MKT-1 is 16:9 1920×1080 canvas-only and is **not** a manual
  figure. Do not put it in these guides.

`doc/JTS_Version_History.md` is a separate concise-claims file; do
not edit it from this rebuild.

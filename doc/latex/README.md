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
overlay, and Hausdorff claims match the code on this tree. Named
figure holes are empty 4:3 slots (target raster 1600×1200, full
TestBuilder window). Do not invent screenshots.

`doc/JTS_Version_History.md` is a separate concise-claims file; do
not edit it from this rebuild.

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

- Guide shots (DG-1..4, TS-1..6, UG-1..8): empty 4:3 holes at
  1600×1200, full TestBuilder window. Slot id only; no caption on
  unshot work. Do not invent screenshots. UG-8 / TS-6 are the
  compound-hull holes (area 61.59119, CircularString shell).
- MKT-1 is 16:9 1920×1080 canvas-only and is **not** a manual
  figure. Do not put it in these guides.

`doc/JTS_Version_History.md` is a separate concise-claims file; do
not edit it from this rebuild.

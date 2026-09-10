# Rebuild the three 2003-2005 JTS manuals from LaTeX.
# Requires pdflatex (TeX Live). Run from this directory:
#   make
# Output PDFs replace the in-tree copies under doc/.

LATEX      ?= pdflatex
LATEXFLAGS ?= -interaction=nonstopmode -halt-on-error
DOC        := ..

.PHONY: all clean developer user specs

all: developer user specs

developer-guide.pdf: developer-guide.tex jtsguide.sty
	$(LATEX) $(LATEXFLAGS) developer-guide.tex
	$(LATEX) $(LATEXFLAGS) developer-guide.tex

user-guide.pdf: user-guide.tex jtsguide.sty
	$(LATEX) $(LATEXFLAGS) user-guide.tex
	$(LATEX) $(LATEXFLAGS) user-guide.tex

technical-specs.pdf: technical-specs.tex jtsguide.sty
	$(LATEX) $(LATEXFLAGS) technical-specs.tex
	$(LATEX) $(LATEXFLAGS) technical-specs.tex

developer: developer-guide.pdf
	cp -f developer-guide.pdf "$(DOC)/JTS Developer Guide.pdf"

user: user-guide.pdf
	cp -f user-guide.pdf "$(DOC)/JTS TestBuilder & TestRunner User Guide.pdf"

specs: technical-specs.pdf
	cp -f technical-specs.pdf "$(DOC)/JTS Technical Specs.pdf"

clean:
	rm -f *.aux *.log *.out *.toc developer-guide.pdf user-guide.pdf technical-specs.pdf

#!/usr/bin/env python3
"""Turn the web app's variable webfonts into the static TTFs Android needs.

The web app loads Orbitron and Exo 2 from Google Fonts as *variable* woff2 files. Android cannot
read woff2, and Compose picks a face by weight — so this script decompresses each family and
instantiates the exact weights the app uses, writing them into `android/app/src/main/res/font/`.

The output is byte-reproducible from `parity/web/fonts/*.woff2`, which are the same files the web
harness feeds the browser during capture, so reference and native render the same letterforms.

Usage:  python3 verification/make-fonts.py
Requires: pip install fonttools brotli
"""
from __future__ import annotations

import pathlib
import sys

try:
    from fontTools.ttLib import TTFont
    from fontTools.varLib import instancer
    from fontTools.ttLib.woff2 import decompress
except ImportError:  # pragma: no cover - developer machine without the tooling
    sys.exit("missing fonttools/brotli — run: pip install fonttools brotli")

ROOT = pathlib.Path(__file__).resolve().parent.parent
SOURCE = ROOT / "parity/web/fonts"
TARGET = ROOT / "android/app/src/main/res/font"

# family → (source woff2, axis tag, weights to instantiate)
FAMILIES = {
    "orbitron": (SOURCE / "orbitron-900.woff2", "wght", [400, 500, 600, 700, 800, 900]),
    "exo2": (SOURCE / "exo-2-400.woff2", "wght", [300, 400, 500, 600, 700]),
}


def instantiate(source: pathlib.Path, axis: str, weight: int, out: pathlib.Path) -> None:
    font = TTFont(source)
    if axis in (font.get("fvar").axes if font.get("fvar") else []):
        font = instancer.instantiateVariableFont(font, {axis: weight}, inplace=False, updateFontNames=True)
    # Android maps the numeric weight from OS/2; make sure it matches the file name.
    font["OS/2"].usWeightClass = weight
    out.parent.mkdir(parents=True, exist_ok=True)
    font.save(out)
    print(f"  {out.name:24} {weight:>3}  {out.stat().st_size / 1024:6.1f} KB  {font['maxp'].numGlyphs} glyphs")


def main() -> None:
    # Decompress the variable fonts once, into the scratch dir.
    workdir = pathlib.Path("/tmp/gadget-fonts")
    workdir.mkdir(parents=True, exist_ok=True)
    for family, (woff2, axis, weights) in FAMILIES.items():
        if not woff2.exists():
            sys.exit(f"missing source font {woff2} — run the parity capture setup first")
        variable = workdir / f"{family}-variable.ttf"
        decompress(str(woff2), str(variable))
        print(f"{family}: {variable.stat().st_size / 1024:.1f} KB variable TTF")
        for weight in weights:
            instantiate(variable, axis, weight, TARGET / f"{family}_{weight}.ttf")


if __name__ == "__main__":
    main()

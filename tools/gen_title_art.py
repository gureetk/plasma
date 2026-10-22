#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-or-later
"""Renders the sample title art (a title's logo image, brief 4.14) the way boards 14 and 16 set it
in CSS, so the detail pages can show it as an image like a provider's logo art.

Usage: gen_title_art.py <out-dir>
Needs fontTools with brotli (to read the hand-off's woff2) and Pillow.
"""
import io, os, sys
from fontTools.ttLib import TTFont
from PIL import Image, ImageDraw, ImageFont

root = os.path.join(os.path.dirname(__file__), "..")

def font_bytes(name):
    font = TTFont(os.path.join(root, "docs/design/fonts", name))
    font.flavor = None
    buf = io.BytesIO()
    font.save(buf)
    return buf.getvalue()

def tracked(draw, x, y, text, data, size, tracking, fill):
    f = ImageFont.truetype(io.BytesIO(data), size)
    for ch in text:
        draw.text((x, y), ch, font=f, fill=fill)
        x += f.getlength(ch) + tracking

def save(img, height, path):
    img = img.crop((0, 0, img.getbbox()[2] + 2, height))
    img.save(path, optimize=True)
    print(os.path.basename(path), img.size)

out = sys.argv[1]

# Board 14: "THE LAST" 34 px, tracking 19, #EDE6D6; "LIGHTHOUSE" 118 px, tracking 7, line-height .95.
# The image is as tall as the two CSS line boxes (41 + 2 + 112 px), so what follows keeps the board's
# spacing; the y offsets put the baselines where the browser does.
cormorant = font_bytes("cormorant-garamond-latin-600-normal.woff2")
img = Image.new("RGBA", (900, 155), (0, 0, 0, 0))
d = ImageDraw.Draw(img)
tracked(d, 0, -2, "THE LAST", cormorant, 34, 19, (0xED, 0xE6, 0xD6, 255))
tracked(d, -2, 27, "LIGHTHOUSE", cormorant, 118, 7, (255, 255, 255, 255))
save(img, 155, os.path.join(out, "fx_titleart_lighthouse.png"))

# Board 16: "ATLAS / STATION" in Unbounded 300, 92 px, tracking 12, line-height 1.02 (two 94 px
# lines). The board's screenshot sets it about 5 % narrower than this font file does at 92 px, so the
# art is drawn at 88 px to match the screenshot.
unbounded = font_bytes("unbounded-latin-300-normal.woff2")
img = Image.new("RGBA", (1000, 188), (0, 0, 0, 0))
d = ImageDraw.Draw(img)
tracked(d, 0, -11, "ATLAS", unbounded, 88, 11.5, (255, 255, 255, 255))
tracked(d, 0, 83, "STATION", unbounded, 88, 11.5, (255, 255, 255, 255))
save(img, 188, os.path.join(out, "fx_titleart_atlas.png"))

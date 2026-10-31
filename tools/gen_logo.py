#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-or-later
"""Draws Plasma's logo (the owner's choice of 30 October 2026): the Flow mark, a disc cut by three
gentle waves, and the wave wordmark, Instrument Sans 600 cut once through its x-height by the same
kind of wave, in white (#F5F5F7, for dark backgrounds) and graphite (#0B0B0D, for light ones).

Usage: tools/gen_logo.py [font.woff2] [out-dir]
  (defaults: docs/design/fonts/instrument-sans-latin-600-normal.woff2 and docs/images)
Needs: pip install fonttools brotli uharfbuzz skia-pathops
"""
import io
import math
import os
import sys

import pathops
import uharfbuzz as hb
from fontTools.pens.basePen import BasePen
from fontTools.pens.svgPathPen import SVGPathPen
from fontTools.pens.transformPen import TransformPen
from fontTools.ttLib import TTFont

FONT = sys.argv[1] if len(sys.argv) > 1 else 'docs/design/fonts/instrument-sans-latin-600-normal.woff2'
OUT = sys.argv[2] if len(sys.argv) > 2 else 'docs/images'
os.makedirs(OUT, exist_ok=True)

# ---------------------------------------------------------------- outlines and path operations
_tt = TTFont(FONT)
_buf = io.BytesIO(); _tt.flavor = None; _tt.save(_buf)
_blob = hb.Blob(_buf.getvalue()); _face = hb.Face(_blob); _hbfont = hb.Font(_face)
_gs = _tt.getGlyphSet()
UPM = 1000
XH, ASC, DESC = 520, 720, -205   # x-height (with overshoot), ascender of l, descender of p


def circle(cx, cy, r):
    k = 0.5522847498 * r
    p = pathops.Path()
    p.moveTo(cx + r, cy)
    p.cubicTo(cx + r, cy + k, cx + k, cy + r, cx, cy + r)
    p.cubicTo(cx - k, cy + r, cx - r, cy + k, cx - r, cy)
    p.cubicTo(cx - r, cy - k, cx - k, cy - r, cx, cy - r)
    p.cubicTo(cx + k, cy - r, cx + r, cy - k, cx + r, cy)
    p.close()
    return p


def union(*paths):
    builder = pathops.OpBuilder()
    for p in paths:
        builder.add(p, pathops.PathOp.UNION)
    return builder.resolve()


def diff(a, *bs):
    builder = pathops.OpBuilder()
    builder.add(a, pathops.PathOp.UNION)
    for b in bs:
        builder.add(b, pathops.PathOp.DIFFERENCE)
    return builder.resolve()


def to_d(path, nd=2):
    """SVG path data; SVGPathPen expands pathops' TrueType-style implied points."""
    pen = SVGPathPen(None, ntos=lambda v: (f'{v:.{nd}f}').rstrip('0').rstrip('.'))
    path.draw(pen)
    return pen.getCommands()


def _glyph_path(ch, size, x, y):
    """Outline of one glyph as a pathops Path, baseline at (x, y)."""
    p = pathops.Path()
    inner = p.getPen()

    class Split(BasePen):   # resolves TrueType implied on-curve points before pathops sees them
        def _moveTo(self, pt): inner.moveTo(pt)
        def _lineTo(self, pt): inner.lineTo(pt)
        def _curveToOne(self, a, b, c): inner.curveTo(a, b, c)
        def _qCurveToOne(self, a, b): inner.qCurveTo(a, b)
        def _closePath(self): inner.closePath()
        def _endPath(self): inner.endPath()

    tp = TransformPen(Split(None), (size / UPM, 0, 0, -size / UPM, x, y))
    _gs[ch].draw(tp)
    return p


def moved(path, sx, tx, ty, sy=None):
    """A copy of [path] scaled by sx (and sy) then moved by (tx, ty)."""
    sy = sx if sy is None else sy
    out = pathops.Path()
    path.draw(TransformPen(out.getPen(), (sx, 0, 0, sy, tx, ty)))
    return out


def wordmark_path(text='plasma', size=100.0, tracking_em=-0.032):
    """Kerned wordmark as one pathops Path, baseline y=0. Returns (path, advance)."""
    buf = hb.Buffer(); buf.add_str(text); buf.guess_segment_properties()
    hb.shape(_hbfont, buf, {'kern': True, 'liga': True})
    order = _tt.getGlyphOrder()
    s = size / UPM; x = 0.0
    parts = []
    n = len(buf.glyph_infos)
    for i, (info, pos) in enumerate(zip(buf.glyph_infos, buf.glyph_positions)):
        parts.append(_glyph_path(order[info.codepoint], size, x + pos.x_offset * s, -pos.y_offset * s))
        x += pos.x_advance * s
        if i < n - 1:
            x += tracking_em * size
    return union(*parts), x


# ---------------------------------------------------------------- the Flow mark
def poly(pts):
    p = pathops.Path()
    p.moveTo(*pts[0])
    for pt in pts[1:]:
        p.lineTo(*pt)
    p.close()
    return p


def wave_band(y0, thick, amp, period, phase, x0=-4, x1=104, step=0.5, taper=0.0):
    """A horizontal band whose centre line is a sine wave. taper>0 thins it toward the right."""
    xs = [x0 + i * step for i in range(int((x1 - x0) / step) + 1)]
    top, bot = [], []
    for x in xs:
        c = y0 + amp * math.sin(2 * math.pi * x / period + phase)
        t = thick * (1 - taper * (x - x0) / (x1 - x0))
        top.append((x, c - t / 2)); bot.append((x, c + t / 2))
    return poly(top + bot[::-1])


def mark_flow(gaps=3, gap=8.0, amp=4.2, period=110, phase=0.6):
    """Scanlines that have started to move: a disc cut by three waves. Plasma, aurora, signal."""
    D = 100
    disc = circle(50, 50, 50)
    b = (D - gaps * gap) / (gaps + 1)
    cuts = [wave_band((i + 1) * b + i * gap + gap / 2, gap, amp, period, phase) for i in range(gaps)]
    return diff(disc, *cuts), D, D


# ---------------------------------------------------------------- the wave wordmark
def wave_wordmark(size=100, gap=0.052, amp=0.028, period=1.35, phase=2.2, y_shift=0.0):
    """Instrument Sans wordmark with one wave cut through the middle of the x-height."""
    wm, adv = wordmark_path(size=size)
    x0, y0, x1, y1 = wm.bounds
    yc = -XH / 1000 * size / 2 + y_shift * size
    cut = wave_band(yc, gap * size, amp * size, period * size, phase, x0=x0 - 5, x1=x1 + 5, step=0.5)
    return diff(wm, cut)


# ---------------------------------------------------------------- the horizontal lockup
WHITE, GRAPHITE = '#F5F5F7', '#0B0B0D'
S = 100
WAVE = dict(gap=0.055, amp=0.03, period=1.6, phase=2.6)
MARK, _, _ = mark_flow()                               # 100 x 100
WORD = wave_wordmark(size=S, **WAVE)                    # baseline y = 0
PLAIN, _ = wordmark_path(size=S)
WB = PLAIN.bounds
TOP, BOT = -ASC / 1000 * S, -DESC / 1000 * S            # ascender and descender lines
X_MID = -XH / 1000 * S / 2


def horizontal():
    mh = 0.92 * S
    m = moved(MARK, mh / 100, 0, X_MID - mh / 2)
    w = moved(WORD, 1, mh + 0.26 * S - WB[0], 0)
    return union(m, w), (0, TOP, mh + 0.26 * S + (WB[2] - WB[0]), BOT)


def svg(path, box, col, title='Plasma'):
    x0, y0, x1, y1 = box
    return (f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="{x0:.2f} {y0:.2f} {x1 - x0:.2f} {y1 - y0:.2f}" '
            f'role="img" aria-label="{title}"><path fill="{col}" d="{to_d(path)}"/></svg>')


H_PATH, H_BOX = horizontal()
for tone, col in (('white', WHITE), ('graphite', GRAPHITE)):
    with open(f'{OUT}/plasma-logo-horizontal-{tone}.svg', 'w') as f:
        f.write(svg(H_PATH, H_BOX, col) + '\n')
    with open(f'{OUT}/plasma-mark-{tone}.svg', 'w') as f:
        f.write(svg(MARK, (0, 0, 100, 100), col) + '\n')
print(f'{OUT}/plasma-logo-horizontal-white.svg, -graphite.svg, plasma-mark-white.svg, -graphite.svg')

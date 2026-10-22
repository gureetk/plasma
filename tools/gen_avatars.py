#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-or-later
"""Makes the sample profile pictures for Who's Watching (board 05): square crops of the hand-off's
art, framed as the board frames each 200 px circle, at twice that size so they stay sharp when the
focused avatar grows.

Usage: gen_avatars.py <out-dir>
"""
import os, sys
from PIL import Image

root = os.path.join(os.path.dirname(__file__), "..")
assets = os.path.join(root, "docs/design/assets")
SIZE = 200  # the board's circle, in board pixels
OUT = 400

def cover(src, x_percent, y_percent):
    """CSS object-fit: cover into a SIZE square, at object-position x% y%."""
    img = Image.open(os.path.join(assets, src)).convert("RGB")
    scale = SIZE / min(img.width, img.height)
    w, h = img.width * scale, img.height * scale
    left = (w - SIZE) * x_percent / 100 / scale
    top = (h - SIZE) * y_percent / 100 / scale
    return img.crop((round(left), round(top), round(left + SIZE / scale), round(top + SIZE / scale)))

def placed(src, left, top, width, height):
    """An image placed at (left, top) at width x height inside the SIZE square, as the board does."""
    img = Image.open(os.path.join(assets, src)).convert("RGB")
    scale = width / img.width
    x0, y0 = -left / scale, -top / scale
    return img.crop((round(x0), round(y0), round(x0 + SIZE / scale), round(y0 + SIZE / scale)))

def save(img, name, out):
    path = os.path.join(out, f"fx_avatar_{name}.jpg")
    img.resize((OUT, OUT), Image.LANCZOS).save(path, quality=88, optimize=True)
    print(os.path.basename(path))

out = sys.argv[1]
save(cover("aurora2.jpg", 58, 35), "maya", out)
save(cover("lighthouse3.jpg", 64, 42), "daniel", out)
save(placed("series_tumble.jpg", -60, -248, 340, 510), "kids", out)

#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-or-later
"""Makes the sample episode stills for Atlas Station, season 2 (board 16): crops of the hand-off's
scenes, tinted with CSS hue-rotate and saturate exactly as the board does.

Usage: gen_episode_stills.py <out-dir>
"""
import math, os, sys
import numpy as np
from PIL import Image

root = os.path.join(os.path.dirname(__file__), "..")
assets = os.path.join(root, "docs/design/assets")

def hue_rotate(deg):
    c, s = math.cos(math.radians(deg)), math.sin(math.radians(deg))
    return np.array([
        [0.213 + c * 0.787 - s * 0.213, 0.715 - c * 0.715 - s * 0.715, 0.072 - c * 0.072 + s * 0.928],
        [0.213 - c * 0.213 + s * 0.143, 0.715 + c * 0.285 + s * 0.140, 0.072 - c * 0.072 - s * 0.283],
        [0.213 - c * 0.213 - s * 0.787, 0.715 - c * 0.715 + s * 0.715, 0.072 + c * 0.928 + s * 0.072],
    ])

def saturate(v):
    return np.array([
        [0.213 + 0.787 * v, 0.715 - 0.715 * v, 0.072 - 0.072 * v],
        [0.213 - 0.213 * v, 0.715 + 0.285 * v, 0.072 - 0.072 * v],
        [0.213 - 0.213 * v, 0.715 - 0.715 * v, 0.072 + 0.928 * v],
    ])

def still(src, left, top, width, height, matrix=None):
    """The 336 × 189 frame of `src` drawn at (left, top) and (width × height), like the board's <img>."""
    img = Image.open(os.path.join(assets, src)).convert("RGB").resize((width, height), Image.LANCZOS)
    img = img.crop((-left, -top, -left + 336, -top + 189))
    if matrix is not None:
        a = np.asarray(img).astype(np.float64) / 255.0
        a = np.clip(a @ matrix.T, 0, 1)
        img = Image.fromarray((a * 255 + 0.5).astype(np.uint8))
    return img

episodes = [
    still("orbit2.jpg", -40, -300, 960, 540),
    still("aurora2.jpg", -150, -30, 640, 360, saturate(1.1) @ hue_rotate(150)),
    still("orbit2.jpg", -616, -269, 1075, 605),
    still("city2.jpg", -150, -110, 640, 360, saturate(0.75) @ hue_rotate(165)),
    still("orbit2.jpg", -392, -414, 1075, 605),
    still("motor2.jpg", -300, -170, 700, 394, hue_rotate(25)),
    # Episodes 7 and 8 aren't on the board; more of the same scenes.
    still("aurora2.jpg", -20, -120, 640, 360, saturate(0.9) @ hue_rotate(200)),
    still("orbit2.jpg", -200, -60, 960, 540, hue_rotate(-20)),
]
out = sys.argv[1]
for i, img in enumerate(episodes, start=1):
    path = os.path.join(out, f"fx_ep_atlas_s2e{i}.jpg")
    img.save(path, quality=88, optimize=True)
    print(os.path.basename(path), img.size)

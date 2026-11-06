#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-or-later
"""Puts Plasma's logo (the owner's choice of 30 October 2026) into the app, from the SVGs
tools/gen_logo.py draws in docs/images:

- the launcher icon: an adaptive icon, the white Flow mark at 52 dp on the 108 dp canvas over the
  graphite dock-tile fill (linear-gradient(#232327, #18181B)), with a monochrome layer for themed
  icons;
- the TV banner: the horizontal lockup, white, 62 % of the banner's width, on the same fill, as PNGs
  at each density (320 x 180 dp).

Usage: tools/gen_android_logo.py [res-dir]   (default: app/src/main/res)
Needs Playwright's Chromium (preinstalled in the build container) for the banner's PNGs.
"""
import os
import re
import subprocess
import sys
import tempfile

RES = sys.argv[1] if len(sys.argv) > 1 else 'app/src/main/res'
IMAGES = 'docs/images'
WHITE = '#F5F5F7'
TOP, BOTTOM = '#232327', '#18181B'


def path_of(svg_file):
    """The one path's data in one of gen_logo.py's SVGs, and its viewBox."""
    with open(svg_file) as f:
        svg = f.read()
    box = [float(v) for v in re.search(r'viewBox="([^"]+)"', svg).group(1).split()]
    return re.search(r' d="([^"]+)"', svg).group(1), box


def write(name, text):
    os.makedirs(os.path.dirname(name), exist_ok=True)
    with open(name, 'w') as f:
        f.write(text)
    print(name)


HEADER = '<?xml version="1.0" encoding="utf-8"?>\n'

# ---------------------------------------------------------------- the launcher icon
mark, (mx, my, mw, mh) = path_of(f'{IMAGES}/plasma-mark-white.svg')
CANVAS, MARK = 108.0, 52.0
scale = MARK / mw
offset = (CANVAS - MARK) / 2


def mark_vector(color, comment):
    return (
        HEADER
        + f'<!-- {comment} Drawn by tools/gen_android_logo.py from docs/images/plasma-mark-white.svg. -->\n'
        + '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
        + f'    android:width="{CANVAS:g}dp" android:height="{CANVAS:g}dp"\n'
        + f'    android:viewportWidth="{CANVAS:g}" android:viewportHeight="{CANVAS:g}">\n'
        + f'    <group android:translateX="{offset:g}" android:translateY="{offset:g}" android:scaleX="{scale:g}" android:scaleY="{scale:g}">\n'
        + f'        <path android:fillColor="{color}" android:pathData="{mark}" />\n'
        + '    </group>\n'
        + '</vector>\n'
    )


write(f'{RES}/drawable/ic_launcher_foreground.xml', mark_vector('#FFF5F5F7', "Plasma's Flow mark, 52 dp on the adaptive icon's 108 dp canvas."))
write(f'{RES}/drawable/ic_launcher_monochrome.xml', mark_vector('#FFFFFFFF', 'The Flow mark alone, for themed icons.'))
write(
    f'{RES}/drawable/ic_launcher_background.xml',
    HEADER
    + "<!-- The dock tile's graphite fill, lighter at the top (linear-gradient(#232327, #18181B)). -->\n"
    + '<shape xmlns:android="http://schemas.android.com/apk/res/android" android:shape="rectangle">\n'
    + f'    <gradient android:angle="270" android:startColor="#FF{TOP[1:]}" android:endColor="#FF{BOTTOM[1:]}" />\n'
    + '</shape>\n',
)
write(
    f'{RES}/mipmap-anydpi-v26/ic_launcher.xml',
    HEADER
    + '<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">\n'
    + '    <background android:drawable="@drawable/ic_launcher_background" />\n'
    + '    <foreground android:drawable="@drawable/ic_launcher_foreground" />\n'
    + '    <monochrome android:drawable="@drawable/ic_launcher_monochrome" />\n'
    + '</adaptive-icon>\n',
)

# ---------------------------------------------------------------- the TV banner
lockup, (lx, ly, lw, lh) = path_of(f'{IMAGES}/plasma-logo-horizontal-white.svg')
BANNER_W, BANNER_H, SHARE = 320, 180, 0.62
DENSITIES = {'mdpi': 1, 'hdpi': 1.5, 'xhdpi': 2, 'xxhdpi': 3}
width = BANNER_W * SHARE
height = width * lh / lw
page = f'''<!doctype html><html><head><style>
html,body{{margin:0;width:{BANNER_W}px;height:{BANNER_H}px;background:linear-gradient({TOP},{BOTTOM});}}
svg{{position:absolute;left:{(BANNER_W - width) / 2}px;top:{(BANNER_H - height) / 2}px;width:{width}px;height:{height}px}}
</style></head><body><svg xmlns="http://www.w3.org/2000/svg" viewBox="{lx} {ly} {lw} {lh}"><path fill="{WHITE}" d="{lockup}"/></svg></body></html>'''
script = r'''
const { chromium } = require('playwright');
(async () => {
  const fs = require('fs');
  const [html, out, scale] = process.argv.slice(2);
  const browser = await chromium.launch();
  const page = await browser.newPage({ viewport: { width: %d, height: %d }, deviceScaleFactor: Number(scale) });
  await page.setContent(fs.readFileSync(html, 'utf8'));
  await page.screenshot({ path: out });
  await browser.close();
})();
''' % (BANNER_W, BANNER_H)
with tempfile.TemporaryDirectory() as tmp:
    html, js = os.path.join(tmp, 'banner.html'), os.path.join(tmp, 'banner.js')
    with open(html, 'w') as f:
        f.write(page)
    with open(js, 'w') as f:
        f.write(script)
    for density, factor in DENSITIES.items():
        out = os.path.abspath(f'{RES}/drawable-{density}/banner.png')
        os.makedirs(os.path.dirname(out), exist_ok=True)
        subprocess.run(['node', js, html, out, str(factor)], check=True, cwd=tmp)
        print(out)

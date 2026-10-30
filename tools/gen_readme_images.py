#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-or-later
"""Makes the README's screenshots from the screens' reference images (1920 x 1080 PNGs from the
Robolectric screenshot tests), so they show the app as it renders, on its sample data.

Usage: tools/gen_readme_images.py      (from the repository's root; needs Pillow)
Run it after the reference images of these screens change.
"""
import os
from PIL import Image

# README name -> reference image
SCREENS = {
    "home": "feature/home/src/test/screenshots/01-home.png",
    "continue-watching": "feature/home/src/test/screenshots/02-home-rows.png",
    "live-tv": "feature/livetv/src/test/screenshots/07-live-tv.png",
    "guide": "feature/guide/src/test/screenshots/08-guide.png",
    "player": "feature/player/src/test/screenshots/09-player.png",
    "multiview": "feature/player/src/test/screenshots/12-multiview.png",
    "movie": "feature/vod/src/test/screenshots/14-movie-detail.png",
}

os.makedirs("docs/images", exist_ok=True)
for name, source in SCREENS.items():
    image = Image.open(source).convert("RGB").resize((1280, 720), Image.LANCZOS)
    image.save(f"docs/images/{name}.jpg", quality=84, optimize=True, progressive=True)
    print(f"docs/images/{name}.jpg")

#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-or-later
"""Makes the website's pictures in site/images from the repository's own:

- the screens, from their reference images (1920 x 1080 PNGs from the Robolectric screenshot
  tests, on the made-up sample data), as WebP at two widths for each picture's srcset;
- the phone remote's two pages, from board 33;
- the social image (social.jpg, 1200 x 630) that links to the site show, from Home's;
- the logo and the favicon, from the SVGs tools/gen_logo.py draws in docs/images;
- wave-cut.svg, the mask the site's headlines are cut with: the logo's wave as a gap in a
  line of type, as style.css's .cut places it.

Usage: tools/site_images.py      (from the repository's root; needs Pillow with WebP)
Run it after one of these screens changes on purpose, as with tools/gen_readme_images.py.
"""
import math
import os
import re

from PIL import Image, ImageDraw

OUT = 'site/images'
BOARD_33 = 'docs/design/screens/33-phone-remote.jpg'

# name -> (reference image, area to keep or None for all of it, widths to write)
SCREENS = {
    'home': ('feature/home/src/test/screenshots/01-home.png', None, (960, 1920)),
    'live-tv': ('feature/livetv/src/test/screenshots/07-live-tv.png', None, (960, 1920)),
    'zap-banner': ('feature/player/src/test/screenshots/11-zap-banner-and-reminder.png', (0, 600, 1920, 1080), (960, 1920)),
    'guide': ('feature/guide/src/test/screenshots/08-guide.png', None, (960, 1920)),
    'player': ('feature/player/src/test/screenshots/09-player.png', None, (960, 1920)),
    'multiview': ('feature/player/src/test/screenshots/12-multiview.png', None, (960, 1920)),
    'movie': ('feature/vod/src/test/screenshots/14-movie-detail.png', None, (960, 1920)),
    'home-rows': ('feature/home/src/test/screenshots/02-home-rows.png', None, (960, 1920)),
    'recordings': ('feature/recordings/src/test/screenshots/17-recordings.png', None, (960, 1920)),
    'apps': ('feature/apps/src/test/screenshots/04-apps.png', None, (960, 1920)),
    'replace-home': ('feature/settings/src/test/screenshots/24-replace-google-tv-home.png', None, (960, 1920)),
    'screensaver': ('feature/home/src/test/screenshots/06-screensaver.png', None, (960, 1920)),
    'profiles': ('feature/profiles/src/test/screenshots/05-profiles.png', (192, 168, 1728, 1032), (600, 1200)),
    'parental-pin': ('feature/profiles/src/test/screenshots/25-parental-pin.png', (288, 180, 1632, 936), (600, 1200)),
    'welcome': ('feature/settings/src/test/screenshots/00-setup-welcome.png', None, (960, 1920)),
    'add-source': ('feature/settings/src/test/screenshots/21-add-a-source.png', None, (960, 1920)),
    'home-screen': ('feature/settings/src/test/screenshots/23-home-screen-setup.png', None, (960, 1920)),
}

# Board 33's two phones: left edge, and the page's area on the board (380 x 780, corners 60 px).
PHONES = {'phone-remote': 596, 'phone-channels': 1016}
PHONE_TOP, PHONE_W, PHONE_H, PHONE_RADIUS = 236, 380, 780, 60

QUALITY = 82


def write_webp(im, name, quality=QUALITY):
    path = os.path.join(OUT, name + '.webp')
    im.save(path, 'WEBP', quality=quality, method=6)
    print(f'{path}  {im.width} x {im.height}  {os.path.getsize(path) // 1024} KB')


def screens():
    for name, (source, area, widths) in SCREENS.items():
        im = Image.open(source).convert('RGB')
        if area:
            im = im.crop(area)
        for width in widths:
            sized = im if width == im.width else im.resize((width, round(im.height * width / im.width)), Image.LANCZOS)
            write_webp(sized, f'{name}-{width}')


def phones():
    board = Image.open(BOARD_33).convert('RGB')
    for name, left in PHONES.items():
        phone = board.crop((left, PHONE_TOP, left + PHONE_W, PHONE_TOP + PHONE_H)).convert('RGBA')
        # The rounded corners, drawn four times larger and brought down, for smooth edges.
        big = Image.new('L', (PHONE_W * 4, PHONE_H * 4), 0)
        ImageDraw.Draw(big).rounded_rectangle((0, 0, PHONE_W * 4 - 1, PHONE_H * 4 - 1), radius=PHONE_RADIUS * 4, fill=255)
        phone.putalpha(big.resize((PHONE_W, PHONE_H), Image.LANCZOS))
        write_webp(phone, name, quality=88)


def social():
    """1200 x 630, as link previews show: Home, a little of its top and bottom left out."""
    im = Image.open(SCREENS['home'][0]).convert('RGB').resize((1200, 675), Image.LANCZOS)
    top = (675 - 630) // 2
    path = os.path.join(OUT, 'social.jpg')
    im.crop((0, top, 1200, top + 630)).save(path, quality=86, optimize=True, progressive=True)
    print(f'{path}  1200 x 630  {os.path.getsize(path) // 1024} KB')


def logos():
    for color in ('graphite', 'white'):
        with open(f'docs/images/plasma-logo-horizontal-{color}.svg') as f:
            svg = f.read()
        write_text(f'plasma-logo-{color}.svg', svg)
    # The favicon is the launcher icon's look: the white Flow mark on the dock tile's graphite.
    with open('docs/images/plasma-mark-white.svg') as f:
        mark = re.search(r' d="([^"]+)"', f.read()).group(1)
    write_text('favicon.svg', (
        '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 100 100">'
        '<defs><linearGradient id="tile" x1="0" y1="0" x2="0" y2="1">'
        '<stop offset="0" stop-color="#232327"/><stop offset="1" stop-color="#18181B"/></linearGradient></defs>'
        '<rect width="100" height="100" rx="22" fill="url(#tile)"/>'
        f'<path fill="#F5F5F7" transform="translate(18 18) scale(.64)" d="{mark}"/></svg>\n'))


def wave_cut():
    """A mask for one line of display type: opaque but for the logo's wave (gen_logo.py's WAVE:
    0.055 em thick, 0.03 em high, 1.6 em long, phase 2.6 from the text's left edge).

    In 1/100 em. The box is the line (line-height 0.92) and 0.14 em of padding above and below,
    1.2 em in all. Instrument Sans's ascent is 0.97 em, its descent 0.25 and its x-height 0.51, so
    the baseline is at 0.14 + (0.92 + 0.97 - 0.25) / 2 = 0.96 em and the middle of the x-height at
    0.705 em. The text starts 3.2 em into the image (mask-position -3.2em), which is 36 em wide.
    """
    width, height, left = 3600, 120, 320
    middle, amplitude, gap, period, phase = 70.5, 3.0, 5.5, 160.0, 2.6

    def center(x):
        return middle + amplitude * math.sin(2 * math.pi * (x - left) / period + phase)

    xs = [i * 5.0 for i in range(width // 5 + 1)]
    edge = [(x, center(x) - gap / 2) for x in xs] + [(x, center(x) + gap / 2) for x in reversed(xs)]
    band = 'M' + ' L'.join('%g,%g' % (round(x, 1), round(y, 2)) for x, y in edge) + ' Z'
    write_text('wave-cut.svg', (
        f'<svg xmlns="http://www.w3.org/2000/svg" width="{width}" height="{height}" viewBox="0 0 {width} {height}">'
        f'<path fill="#000" fill-rule="evenodd" d="M0,0 H{width} V{height} H0 Z {band}"/></svg>\n'))


def write_text(name, text):
    path = os.path.join(OUT, name)
    with open(path, 'w') as f:
        f.write(text)
    print(f'{path}  {os.path.getsize(path) // 1024} KB')


if __name__ == '__main__':
    os.makedirs(OUT, exist_ok=True)
    screens()
    phones()
    social()
    logos()
    wave_cut()

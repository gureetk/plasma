#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-or-later
"""Checks the website in site/ before it's published: that every page's links, pictures and
styles point at files that are there (and at ids that are there, for links to a part of a page),
that the stylesheet's files are there, that every picture has words for screen readers and its
size, and that a WebP picture's size is what its page says. The site's own address
(https://plasma.gureet.ca/, in each page's canonical link and og:image) counts as the site.

Usage: tools/site_check.py [site-dir]      (default: site; Python's standard library only)
The Website workflow runs it on every pull request that changes the site.
"""
import os
import re
import struct
import sys
from html.parser import HTMLParser
from urllib.parse import urljoin, urlparse

SITE = sys.argv[1] if len(sys.argv) > 1 else 'site'
ADDRESS = 'https://plasma.gureet.ca/'
LOCAL = 'https://site.invalid/'


class Page(HTMLParser):
    def __init__(self):
        super().__init__(convert_charrefs=True)
        self.refs, self.ids, self.images = [], set(), []

    def handle_starttag(self, tag, attrs):
        a = dict(attrs)
        if 'id' in a:
            self.ids.add(a['id'])
        for name in ('href', 'src'):
            if a.get(name) is not None:
                self.refs.append((tag, a[name]))
        if a.get('srcset'):
            for part in a['srcset'].split(','):
                url, *size = part.split()
                self.refs.append((tag, url))
                if size and size[0].endswith('w'):
                    self.images.append(('srcset', url, int(size[0][:-1]), None, None))
        if tag == 'meta' and a.get('property') == 'og:image':
            self.refs.append(('meta', a.get('content', '')))
        if tag == 'img':
            self.images.append(('img', a.get('src'), a.get('width'), a.get('height'), a.get('alt')))


def webp_size(path):
    """A WebP's width and height, from its first chunk (VP8, VP8L or VP8X)."""
    with open(path, 'rb') as f:
        head = f.read(30)
    kind = head[12:16]
    if kind == b'VP8X':
        return 1 + int.from_bytes(head[24:27], 'little'), 1 + int.from_bytes(head[27:30], 'little')
    if kind == b'VP8L':
        bits = struct.unpack('<I', head[21:25])[0]
        return (bits & 0x3FFF) + 1, ((bits >> 14) & 0x3FFF) + 1
    if kind == b'VP8 ':
        w, h = struct.unpack('<HH', head[26:30])
        return w & 0x3FFF, h & 0x3FFF
    return None


def local_file(url):
    """The file in the site a resolved address stands for, or None if it's outside the site."""
    for root in (LOCAL, ADDRESS):
        if url.startswith(root):
            rel = urlparse(url).path[len(urlparse(root).path):]
            path = os.path.join(SITE, rel)
            return os.path.join(path, 'index.html') if rel == '' or rel.endswith('/') else path
    return None


def main():
    problems, used = [], set()
    pages = sorted(f for f in os.listdir(SITE) if f.endswith('.html'))
    parsed = {}
    for name in pages:
        page = Page()
        with open(os.path.join(SITE, name), encoding='utf-8') as f:
            page.feed(f.read())
        parsed[name] = page

    for name, page in parsed.items():
        # GitHub Pages serves 404.html at any missing address; its <base> finds its links from the root.
        here = LOCAL if name in ('index.html', '404.html') else LOCAL + name
        for tag, ref in page.refs:
            if ref.startswith('http://'):
                problems.append(f'{name}: {ref} is plain http')
            url = urljoin(here, ref)
            target = local_file(url.split('#')[0])
            if target is None:
                continue
            if not os.path.isfile(target):
                problems.append(f'{name}: <{tag}> {ref} points at nothing ({target})')
                continue
            used.add(os.path.normpath(target))
            fragment = urlparse(url).fragment
            other = os.path.relpath(target, SITE)
            if fragment and other in parsed and fragment not in parsed[other].ids:
                problems.append(f'{name}: {ref} points at no id "{fragment}" in {other}')
        for kind, src, width, height, alt in page.images:
            target = local_file(urljoin(here, src or ''))
            if kind == 'img' and not alt:
                problems.append(f'{name}: <img src="{src}"> has no alt text')
            if kind == 'img' and not (width and height):
                problems.append(f'{name}: <img src="{src}"> has no width and height')
            if not target or not os.path.isfile(target) or not target.endswith('.webp'):
                continue
            size = webp_size(target)
            if kind == 'srcset' and size and size[0] != width:
                problems.append(f'{name}: srcset says {src} is {width}w, and it is {size[0]} wide')
            if kind == 'img' and size and width and height:
                if abs(size[0] / size[1] - int(width) / int(height)) > 0.01:
                    problems.append(f'{name}: {src} is {size[0]} x {size[1]}, not the {width} x {height} its page says')

    with open(os.path.join(SITE, 'style.css'), encoding='utf-8') as f:
        css = f.read()
    for ref in re.findall(r'url\(\s*["\']?([^"\')]+)', css):
        target = os.path.normpath(os.path.join(SITE, ref))
        if not os.path.isfile(target):
            problems.append(f'style.css: url({ref}) points at nothing')
        used.add(target)

    # Files nothing points at only make the site bigger.
    for folder, _, files in os.walk(SITE):
        for file in files:
            path = os.path.normpath(os.path.join(folder, file))
            if file.endswith(('.html', '.css', '.txt')) or path in used:
                continue
            problems.append(f'{path}: nothing points at it')

    for problem in problems:
        print(problem)
    print(f'{len(pages)} pages checked: ' + (f'{len(problems)} problems' if problems else 'no problems'))
    return 1 if problems else 0


if __name__ == '__main__':
    sys.exit(main())

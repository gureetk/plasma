#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-or-later
"""Convert the inline SVG icons used on the design boards into Android vector drawables.

Usage: svg2vector.py icons.json out_dir
icons.json maps a drawable name to an inline <svg ...>...</svg> string copied from a board.
Stroke icons (fill="none" stroke="currentColor") become white strokes; filled shapes become white fills.
Tint them in Compose; never hard-code colours in feature code.
"""
import json, re, sys, xml.etree.ElementTree as ET
from pathlib import Path

def num(v, d=0.0):
    try: return float(v)
    except (TypeError, ValueError): return d

def fmt(x):
    s = f"{x:.3f}".rstrip("0").rstrip(".")
    return s if s not in ("-0", "") else "0"

def rect_path(x, y, w, h, rx, ry):
    if rx == 0 and ry == 0:
        return f"M{fmt(x)},{fmt(y)}h{fmt(w)}v{fmt(h)}h{fmt(-w)}Z"
    rx = min(rx or ry, w / 2); ry = min(ry or rx, h / 2)
    return (f"M{fmt(x+rx)},{fmt(y)}h{fmt(w-2*rx)}a{fmt(rx)},{fmt(ry)} 0 0 1 {fmt(rx)},{fmt(ry)}"
            f"v{fmt(h-2*ry)}a{fmt(rx)},{fmt(ry)} 0 0 1 {fmt(-rx)},{fmt(ry)}h{fmt(-(w-2*rx))}"
            f"a{fmt(rx)},{fmt(ry)} 0 0 1 {fmt(-rx)},{fmt(-ry)}v{fmt(-(h-2*ry))}a{fmt(rx)},{fmt(ry)} 0 0 1 {fmt(rx)},{fmt(-ry)}Z")

def element_path(el):
    tag = el.tag.split('}')[-1]
    a = el.attrib
    if tag == "path": return a.get("d", "")
    if tag == "circle":
        cx, cy, r = num(a.get("cx")), num(a.get("cy")), num(a.get("r"))
        return f"M{fmt(cx-r)},{fmt(cy)}a{fmt(r)},{fmt(r)} 0 1 0 {fmt(2*r)},0a{fmt(r)},{fmt(r)} 0 1 0 {fmt(-2*r)},0"
    if tag == "ellipse":
        cx, cy, rx, ry = (num(a.get(k)) for k in ("cx", "cy", "rx", "ry"))
        return f"M{fmt(cx-rx)},{fmt(cy)}a{fmt(rx)},{fmt(ry)} 0 1 0 {fmt(2*rx)},0a{fmt(rx)},{fmt(ry)} 0 1 0 {fmt(-2*rx)},0"
    if tag == "rect":
        return rect_path(num(a.get("x")), num(a.get("y")), num(a.get("width")), num(a.get("height")),
                         num(a.get("rx")), num(a.get("ry")))
    if tag == "line":
        return f"M{a.get('x1','0')},{a.get('y1','0')}L{a.get('x2','0')},{a.get('y2','0')}"
    if tag in ("polyline", "polygon"):
        pts = re.findall(r"-?[\d.]+", a.get("points", ""))
        pairs = [f"{pts[i]},{pts[i+1]}" for i in range(0, len(pts) - 1, 2)]
        return "M" + "L".join(pairs) + ("Z" if tag == "polygon" else "")
    return ""

def paint(el, inherited):
    style = dict(inherited)
    for k in ("fill", "stroke", "stroke-width", "stroke-linecap", "stroke-linejoin", "opacity", "fill-opacity", "stroke-opacity"):
        if k in el.attrib: style[k] = el.attrib[k]
    return style

def convert(svg_text):
    svg_text = re.sub(r'\s(aria-hidden|class|role)="[^"]*"', "", svg_text)
    root = ET.fromstring(svg_text)
    vb = [num(v) for v in root.attrib.get("viewBox", "0 0 24 24").split()]
    w = num(root.attrib.get("width"), vb[2]); h = num(root.attrib.get("height"), vb[3])
    base = paint(root, {"fill": "black", "stroke": "none", "stroke-width": "1"})
    out = [f'<vector xmlns:android="http://schemas.android.com/apk/res/android"',
           f'    android:width="{fmt(w/2)}dp" android:height="{fmt(h/2)}dp"',
           f'    android:viewportWidth="{fmt(vb[2])}" android:viewportHeight="{fmt(vb[3])}">']
    def walk(node, inherited):
        for el in node:
            st = paint(el, inherited)
            tag = el.tag.split('}')[-1]
            if tag == "g":
                walk(el, st); continue
            d = element_path(el)
            if not d: continue
            attrs = [f'android:pathData="{d}"']
            fill = st.get("fill", "black"); stroke = st.get("stroke", "none")
            op = num(st.get("opacity"), 1.0)
            if fill not in ("none", "transparent"):
                attrs.append('android:fillColor="#FFFFFFFF"')
                fo = num(st.get("fill-opacity"), 1.0) * op
                if fo < 1: attrs.append(f'android:fillAlpha="{fmt(fo)}"')
            if stroke not in ("none", "transparent"):
                attrs.append('android:strokeColor="#FFFFFFFF"')
                attrs.append(f'android:strokeWidth="{st.get("stroke-width", "1")}"')
                if st.get("stroke-linecap"): attrs.append(f'android:strokeLineCap="{st["stroke-linecap"]}"')
                if st.get("stroke-linejoin"): attrs.append(f'android:strokeLineJoin="{st["stroke-linejoin"]}"')
                so = num(st.get("stroke-opacity"), 1.0) * op
                if so < 1: attrs.append(f'android:strokeAlpha="{fmt(so)}"')
            out.append("    <path " + "\n        ".join(attrs) + " />")
    walk(root, base)
    out.append("</vector>")
    return "\n".join(out) + "\n"

if __name__ == "__main__":
    icons = json.load(open(sys.argv[1])); outdir = Path(sys.argv[2]); outdir.mkdir(parents=True, exist_ok=True)
    for name, svg in icons.items():
        (outdir / f"{name}.xml").write_text(convert(svg))
    print(f"wrote {len(icons)} icons to {outdir}")

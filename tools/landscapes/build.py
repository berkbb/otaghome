"""Writes the landscape vectors into the app and renders a 16:9 contact sheet for review.

Usage: build.py <drawable dir> <preview dir> <module> [<module> ...]
"""
import importlib
import os
import subprocess
import sys
import xml.etree.ElementTree as ET

from PIL import Image, ImageDraw, ImageFont

A = '{http://schemas.android.com/apk/res/android}'
AAPT = '{http://schemas.android.com/aapt}attr'


def col(c):
    c = c.lstrip('#')
    return '#' + c[-6:], (int(c[:2], 16) / 255 if len(c) == 8 else 1)


def to_svg(xml_path, svg_path):
    root = ET.parse(xml_path).getroot()
    defs, body, n = [], [], [0]
    for ch in root.iter('path'):
        d = ch.get(A + 'pathData')
        attrs = []
        aapt = ch.find(AAPT)
        if aapt is not None:
            g = aapt.find('gradient'); n[0] += 1; gid = f'g{n[0]}'
            stops = ''.join(f'<stop offset="{it.get(A + "offset")}" stop-color="{col(it.get(A + "color"))[0]}" '
                            f'stop-opacity="{col(it.get(A + "color"))[1]:.3f}"/>' for it in g.findall('item'))
            if g.get(A + 'type') == 'radial':
                defs.append(f'<radialGradient id="{gid}" gradientUnits="userSpaceOnUse" cx="{g.get(A + "centerX")}" '
                            f'cy="{g.get(A + "centerY")}" r="{g.get(A + "gradientRadius")}">{stops}</radialGradient>')
            else:
                defs.append(f'<linearGradient id="{gid}" gradientUnits="userSpaceOnUse" x1="{g.get(A + "startX")}" '
                            f'y1="{g.get(A + "startY")}" x2="{g.get(A + "endX")}" y2="{g.get(A + "endY")}">{stops}</linearGradient>')
            attrs.append(f'fill="url(#{gid})"')
        elif ch.get(A + 'fillColor'):
            c, a = col(ch.get(A + 'fillColor')); attrs.append(f'fill="{c}" fill-opacity="{a:.3f}"')
        else:
            attrs.append('fill="none"')
        if ch.get(A + 'strokeColor'):
            c, a = col(ch.get(A + 'strokeColor'))
            attrs.append(f'stroke="{c}" stroke-opacity="{a:.3f}" stroke-width="{ch.get(A + "strokeWidth", "1")}" '
                         f'stroke-linecap="{ch.get(A + "strokeLineCap", "butt")}"')
        body.append(f'<path d="{d}" {" ".join(attrs)}/>')
    # Square canvas with the picture centred so the square Quick Look thumbnail keeps all of it
    open(svg_path, 'w').write(
        '<svg xmlns="http://www.w3.org/2000/svg" width="1920" height="1920" viewBox="0 -420 1920 1920">'
        f'<defs><clipPath id="c"><rect width="1920" height="1080"/></clipPath>{"".join(defs)}</defs>'
        f'<g clip-path="url(#c)">{"".join(body)}</g></svg>')


def main():
    drawable, preview = sys.argv[1], sys.argv[2]
    os.makedirs(preview, exist_ok=True)
    names = []
    for module in sys.argv[3:]:
        for name, scene in importlib.import_module(module).SCENES.items():
            path = os.path.join(drawable, f'bg_tr_{name}.xml')
            open(path, 'w').write(scene().xml())
            names.append(name)
            svg = os.path.join(preview, f'{name}.svg')
            to_svg(path, svg)
            subprocess.run(['qlmanage', '-t', '-s', '960', '-o', preview, svg], capture_output=True)
            print(f'{name}: {os.path.getsize(path) // 1024} KB')
    font = ImageFont.truetype('/System/Library/Fonts/Supplemental/Arial.ttf', 20)
    cols = 4
    sheet = Image.new('RGB', (cols * 490 + 10, ((len(names) + cols - 1) // cols) * 300 + 10), (24, 24, 28))
    draw = ImageDraw.Draw(sheet)
    for i, name in enumerate(names):
        im = Image.open(os.path.join(preview, f'{name}.svg.png')).convert('RGB')
        w, h = im.size
        tile = im.crop((0, round(h * 420 / 1920), w, round(h * 1500 / 1920))).resize((480, 270))
        x, y = 10 + (i % cols) * 490, 10 + (i // cols) * 300
        sheet.paste(tile, (x, y))
        draw.text((x + 4, y + 272), name, fill=(235, 235, 235), font=font)
    sheet.save(os.path.join(preview, 'sheet.png'))


main()

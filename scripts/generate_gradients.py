#!/usr/bin/env python3
"""
Generate gradient drawables for Android from colors.xml.

Usage:
    python3 scripts/generate_gradients.py

This script reads app/src/main/res/values/colors.xml to collect color families
for shade 500 and 700, computes hue ordering, then generates neighbor and
complement gradients in drawable/ (day, shade500) and drawable-night/ (night, shade700).
"""
import xml.etree.ElementTree as ET
import os
import colorsys

def parse_colors(colors_xml):
    tree = ET.parse(colors_xml)
    root = tree.getroot()
    mapping = {}
    for elem in root.findall('color'):
        name = elem.get('name')
        text = elem.text.strip()
        mapping[name] = text
    return mapping

def hex_to_rgb(hexstr):
    hexstr = hexstr.lstrip('#')
    if len(hexstr) == 8:
        hexstr = hexstr[2:]
    r = int(hexstr[0:2], 16) / 255.0
    g = int(hexstr[2:4], 16) / 255.0
    b = int(hexstr[4:6], 16) / 255.0
    return r, g, b

def main():
    script_dir = os.path.dirname(__file__)
    res_base = os.path.abspath(os.path.join(script_dir, '..', 'app', 'src', 'main', 'res'))
    colors_xml = os.path.join(res_base, 'values', 'colors.xml')
    drawable_dir = os.path.join(res_base, 'drawable')
    drawable_night_dir = os.path.join(res_base, 'drawable-night')
    os.makedirs(drawable_dir, exist_ok=True)
    os.makedirs(drawable_night_dir, exist_ok=True)

    colors = parse_colors(colors_xml)
    families = [fam for fam in (name[:-4] for name in colors if name.endswith('_500'))
                if f'{fam}_700' in colors]

    hues = {}
    for fam in families:
        rgb = hex_to_rgb(colors[f'{fam}_500'])
        h, _, _ = colorsys.rgb_to_hsv(*rgb)
        hues[fam] = h

    sorted_fams = sorted(families, key=lambda f: hues[f])
    combos = []
    for i in range(len(sorted_fams) - 1):
        combos.append((sorted_fams[i], sorted_fams[i+1]))
    half = len(sorted_fams) // 2
    for i in range(half):
        combos.append((sorted_fams[i], sorted_fams[i+half]))

    xml_template = '''<shape xmlns:android="http://schemas.android.com/apk/res/android">
    <gradient
        android:angle="360"
        android:startColor="@color/{start}_SHADE"
        android:endColor="@color/{end}_SHADE"
        android:type="linear" />
</shape>
'''

    for start, end in combos:
        filename = f'bg_{start}_{end}_gradient.xml'
        path_day = os.path.join(drawable_dir, filename)
        path_night = os.path.join(drawable_night_dir, filename)
        if not os.path.exists(path_day):
            content_day = xml_template.format(start=start, end=end).replace('SHADE', '500')
            with open(path_day, 'w', encoding='utf-8') as f:
                f.write(content_day)
            print(f'Created {path_day}')
        else:
            print(f'Skipped existing {path_day}')
        if not os.path.exists(path_night):
            content_night = xml_template.format(start=start, end=end).replace('SHADE', '700')
            with open(path_night, 'w', encoding='utf-8') as f:
                f.write(content_night)
            print(f'Created {path_night}')
        else:
            print(f'Skipped existing {path_night}')

if __name__ == '__main__':
    main()
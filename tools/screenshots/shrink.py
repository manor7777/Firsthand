#!/usr/bin/env python3
"""Downsizes the emulator screenshots (1080 px wide) to 540 px so the repo and README stay light."""
import glob
from PIL import Image

for p in sorted(glob.glob("docs/screenshots/*.png")):
    im = Image.open(p)
    w, h = im.size
    if w > 540:
        im = im.resize((540, round(h * 540 / w)), Image.LANCZOS)
    im.save(p, optimize=True)
    print(p, im.size)

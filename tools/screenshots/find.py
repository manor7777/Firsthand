#!/usr/bin/env python3
"""Finds a UI element in a `uiautomator dump` and prints its centre as "x y" (exit 1 if not found).

  find.py ui.xml "My News"   a node whose text or content-description equals it (any case), else one that contains it
  find.py ui.xml --card      the first big clickable node in the content area (a story card)
"""
import re, sys
import xml.etree.ElementTree as ET


def bounds(n):
    m = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", n.get("bounds", ""))
    return tuple(int(x) for x in m.groups()) if m else None


def main():
    path, what = sys.argv[1], " ".join(sys.argv[2:])
    nodes = [n for n in ET.parse(path).getroot().iter("node") if bounds(n)]
    if not nodes:
        sys.exit(1)
    screen = max((bounds(n) for n in nodes), key=lambda b: (b[2] - b[0]) * (b[3] - b[1]))
    sw, sh = screen[2] - screen[0], screen[3] - screen[1]

    def centre(n):
        l, t, r, b = bounds(n)
        print((l + r) // 2, (t + b) // 2)
        sys.exit(0)

    if what == "--card":
        for n in nodes:
            l, t, r, b = bounds(n)
            if n.get("clickable") == "true" and (r - l) > 0.7 * sw and (b - t) > 0.07 * sh and (t + b) / 2 > 0.25 * sh and (t + b) / 2 < 0.9 * sh:
                centre(n)
        sys.exit(1)

    want = what.strip().lower()
    def labels(n):
        return [n.get("text", "").strip().lower(), n.get("content-desc", "").strip().lower()]
    for n in nodes:
        if want in labels(n):
            centre(n)
    for n in nodes:
        if want and any(want in l for l in labels(n) if l):
            centre(n)
    sys.exit(1)


main()

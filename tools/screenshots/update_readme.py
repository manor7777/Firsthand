#!/usr/bin/env python3
"""Fills the screenshots section of README.md (between the markers) with a table of whichever screenshots exist."""
import os, re, sys

SHOTS = [  # file (without .png), caption
    ("01-home", "Home"), ("02-article", "Reader"), ("03-my-news", "My News"), ("05-sources", "Your sources"),
    ("06-add-source", "Add a source"), ("08-my-news-exampletown", "A local paper's stories"), ("09-article-source", "Writers and share"),
]
DIR = "docs/screenshots"
start, end = "<!-- screenshots:start -->", "<!-- screenshots:end -->"

have = [(f, c) for f, c in SHOTS if os.path.exists(f"{DIR}/{f}.png")]
if not have:
    sys.exit("no screenshots found")
cells = [f'<td align="center"><img src="{DIR}/{f}.png" width="190" alt="{c}"><br><sub>{c}</sub></td>' for f, c in have]
rows = ["<tr>" + "".join(cells[i:i + 4]) + "</tr>" for i in range(0, len(cells), 4)]
block = f"{start}\n<table>\n" + "\n".join(rows) + f"\n</table>\n{end}"

text = open("README.md").read()
new, n = re.subn(re.escape(start) + r".*?" + re.escape(end), lambda _: block, text, flags=re.S)
if n != 1:
    sys.exit("screenshot markers not found in README.md")
open("README.md", "w").write(new)
print(f"README updated with {len(have)} screenshots")

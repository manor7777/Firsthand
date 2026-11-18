#!/usr/bin/env python3
"""One-off diagnostic (run by CI, which has open internet): why can't the reader get a story's text?
Finds ONE story in a feed by a title keyword, fetches its page with the app's user agent and with a browser one, and prints a
structural summary (status, size, where the paragraphs live, JSON-LD keys). Nothing from the article is saved."""
import collections, json, re, sys, urllib.request
from html.parser import HTMLParser

FEED, KEYWORD = sys.argv[1], sys.argv[2].lower()
APP_UA = "Mozilla/5.0 (Android) Firsthand/personal"
BROWSER_UA = "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36"

def get(url, ua):
    try:
        with urllib.request.urlopen(urllib.request.Request(url, headers={"User-Agent": ua, "Accept": "text/html,*/*"}), timeout=30) as r:
            return r.status, r.read().decode("utf-8", "replace"), r.geturl()
    except urllib.error.HTTPError as e:
        return e.code, e.read().decode("utf-8", "replace")[:2000], url
    except Exception as e:
        return 0, str(e), url

st, feed, _ = get(FEED, APP_UA)
print("feed:", st, len(feed))
items = re.findall(r"<item>.*?</item>", feed, re.S)
pick = next((i for i in items if KEYWORD in i.lower()), items[0] if items else "")
link = re.search(r"<link>\s*(?:<!\[CDATA\[)?\s*(https?://[^<\s\]]+)", pick)
title = re.search(r"<title>\s*(?:<!\[CDATA\[)?\s*([^<\]]+)", pick)
print("story:", title.group(1).strip() if title else "?")
print("has content:encoded in feed item:", "content:encoded" in pick)
url = link.group(1)
print("url:", url)

class P(HTMLParser):
    def __init__(s):
        super().__init__(); s.stack = []; s.chains = collections.Counter(); s.samples = {}; s.ld = []; s.in_ld = False; s.p_depth = None; s.buf = ""
        s.tags = collections.Counter()
    def handle_starttag(s, tag, attrs):
        a = dict(attrs); s.tags[tag] += 1
        if tag in ("br", "img", "meta", "link", "input", "hr"): return
        s.stack.append((tag, (a.get("class") or "")[:70], a.get("itemprop", "")))
        if tag == "script" and "ld+json" in (a.get("type") or ""): s.in_ld = True; s.buf = ""
        if tag == "p": s.p_depth = len(s.stack); s.buf = ""
    def handle_data(s, d):
        if s.in_ld or s.p_depth: s.buf += d
    def handle_endtag(s, tag):
        if tag in ("br", "img", "meta", "link", "input", "hr") or not s.stack: return
        if tag == "script" and s.in_ld: s.ld.append(s.buf); s.in_ld = False
        if tag == "p" and s.p_depth:
            t = re.sub(r"\s+", " ", s.buf).strip()
            if len(t) >= 60:
                chain = " > ".join(f"{t_}.{c.split()[0]}" if c else t_ for t_, c, _ in s.stack[:-1] if c or t_ in ("article", "main", "section"))
                s.chains[chain] += 1; s.samples.setdefault(chain, t[:70])
            s.p_depth = None
        while s.stack:
            if s.stack.pop()[0] == tag: break

for name, ua in (("app UA", APP_UA), ("browser UA", BROWSER_UA)):
    code, body, final = get(url, ua)
    print(f"\n== {name}: HTTP {code}, {len(body)} bytes, final url {'same' if final == url else final}")
    if code != 200:
        print("   body starts:", re.sub(r"\s+", " ", body)[:300]); continue
    p = P(); p.feed(body)
    print("   <article> tags:", p.tags["article"], " <p> tags:", p.tags["p"], " <script> tags:", p.tags["script"])
    print("   'surpassed expectations'-style phrase in raw html:", any(w in body.lower() for w in re.findall(r"[a-z]{6,}", KEYWORD)))
    print("   paragraphs of 60+ chars grouped by their classed ancestors (count, chain, sample):")
    for chain, n in p.chains.most_common(6): print(f"     {n:3d}  {chain or '(no classed ancestor)'}  | {p.samples[chain]}")
    for raw in p.ld[:4]:
        try:
            d = json.loads(raw); d = d if isinstance(d, list) else [d]
            for o in d:
                if isinstance(o, dict): print("   ld+json:", o.get("@type"), sorted(o.keys())[:14], "articleBody len:", len(str(o.get("articleBody", ""))))
        except Exception as e: print("   ld+json unparsable:", str(e)[:60])
    print("   consent/paywall hints:", [w for w in ("consent", "paywall", "subscribe", "captcha", "access denied", "enable javascript") if w in body.lower()])

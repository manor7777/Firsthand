# One-off diagnostic: which BBC topic tags appear on the top search results for each keyword?
import json, re, sys, urllib.parse, urllib.request, collections
UA = "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36"

def get(url):
    return urllib.request.urlopen(urllib.request.Request(url, headers={"User-Agent": UA}), timeout=30).read().decode("utf8", "replace")

def initial(html):
    i = html.find("window.__INITIAL_DATA__")
    if i < 0: return None
    i = html.index('"', html.index("=", i)); j = i + 1
    while html[j] != '"':
        j += 2 if html[j] == "\\" else 1
    return json.loads(json.loads(html[i:j + 1]))

def widgets(d, name):
    return [v.get("data") for v in d.get("data", {}).values() if isinstance(v, dict) and v.get("name") == name]

out = []
for term in ["diabetes", "exampletown", "disability", "jeremy clarkson"]:
    q = urllib.parse.quote(term)
    urls = []
    for page in (1, 2):
        try:
            d = initial(get(f"https://www.bbc.co.uk/search?q={q}&d=NEWS_GNL" + (f"&page={page}" if page > 1 else "")))
            for w in widgets(d, "search-results"):
                for it in (w.get("initialResults", {}).get("items") or []):
                    u = it.get("url", "").split("?")[0]
                    if "/news/articles/" in u and u not in urls: urls.append(u)
        except Exception as e:
            out.append(f"{term}: search page {page} failed: {e}")
    out.append(f"\n== {term}: {len(urls)} article urls from search")
    counts = collections.Counter(); ids = {}
    for u in urls[:12]:
        try:
            d = initial(get(u))
            for a in widgets(d, "article"):
                for t in a.get("topics", []) or []:
                    counts[t["title"]] += 1; ids[t["title"]] = t.get("id")
        except Exception as e:
            out.append(f"  article fail {u}: {e}")
    for name, n in counts.most_common(15):
        out.append(f"  {n:2d}x {name}  [{ids[name]}]")
    exact = [n for n in counts if n.strip().lower() == term]
    out.append(f"  EXACT MATCH: {exact or 'none'}")
open("fixtures/probe2/resolve.txt", "w").write("\n".join(out))
print("\n".join(out))

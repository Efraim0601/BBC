#!/usr/bin/env python3
"""
Génère le support projetable de l'atelier pratique.

    python3 tools/guide/build-atelier.py

Sortie : frontend/public/guide/atelier.html — une page autonome, navigable au
clavier, imprimable à raison d'une diapositive par page. Le contenu vit dans
atelier.py ; les captures sont celles du guide (dossier img/).
"""
from __future__ import annotations

import html
import os
import re
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from atelier import DECK  # noqa: E402
from build import webp_size  # noqa: E402  (même lecture d'en-tête WebP)

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
OUT = os.path.join(ROOT, "frontend", "public", "guide", "atelier.html")
IMG_DIR = os.path.join(ROOT, "frontend", "public", "guide", "img")

E = html.escape
MISSING: list[str] = []


def inline(text: str) -> str:
    out = E(text)
    out = re.sub(r"\*\*(.+?)\*\*", r"<b>\1</b>", out)
    out = re.sub(r"`(.+?)`", r"<code>\1</code>", out)
    out = re.sub(r"__(.+?)__", r'<span class="ui">\1</span>', out)
    return out


def shot(name: str, alt: str = "") -> str:
    path = os.path.join(IMG_DIR, f"fr-{name}.webp")
    if not os.path.exists(path):
        MISSING.append(name)
        return ""
    size = webp_size(path)
    dims = f' width="{size[0]}" height="{size[1]}"' if size else ""
    return (f'<div class="shot"><img src="img/fr-{E(name)}.webp" alt="{E(alt)}"{dims} '
            f'loading="lazy" decoding="async"></div>')


# --------------------------------------------------------------------- gabarits
def slide_cover(s) -> str:
    m = DECK["meta"]
    return f"""<section class="slide cover">
  <div class="cover-inner">
    <div class="eyebrow">{E(m['school'])}</div>
    <h1>{E(m['title'])}</h1>
    <p class="lead">{E(m['subtitle'])}</p>
    <div class="cover-meta">
      <div><span>Durée</span>{E(m['duration'])}</div>
      <div><span>Public</span>{E(m['audience'])}</div>
    </div>
    <div class="cover-hint">Flèches ← → pour naviguer · <b>S</b> pour le sommaire · <b>P</b> pour imprimer</div>
  </div>
</section>"""


def slide_agenda(s) -> str:
    rows = "".join(
        f'<tr class="{"is-break" if not r[3] and r[1] in ("Pause", "Déjeuner") else ""}">'
        f"<td class=\"t\">{E(r[0])}</td><td>{inline(r[1])}</td>"
        f'<td class="d">{E(r[2])}</td><td class="a">{E(r[3])}</td></tr>'
        for r in s["rows"])
    sub = f' <span class="agenda-hours">{E(s["sub"])}</span>' if s.get("sub") else ""
    return f"""<section class="slide">
  <div class="head"><div class="eyebrow">Programme</div><h2>{E(s['title'])}{sub}</h2></div>
  <div class="body agenda"><table>{rows}</table></div>
</section>"""


def slide_section(s) -> str:
    plan = "".join(f"<li>{inline(p)}</li>" for p in s.get("plan", []))
    return f"""<section class="slide section-open">
  <div class="body">
    <div class="mod-badge">Module {E(s['num'])}</div>
    <h2 class="big">{E(s['title'])}</h2>
    <div class="chips">
      <span class="chip time">⏱ {E(s['time'])}</span>
      <span class="chip">{E(s.get('audience', ''))}</span>
    </div>
    <p class="goal"><span>Objectif</span>{inline(s['goal'])}</p>
    <ul class="plan">{plan}</ul>
  </div>
</section>"""


def slide_demo(s) -> str:
    steps = "".join(f"<li>{inline(x)}</li>" for x in s["steps"])
    img = shot(s["img"], s["title"]) if s.get("img") else ""
    return f"""<section class="slide">
  <div class="head">
    <div class="eyebrow">Module {E(s['num'])} · Démonstration animateur</div>
    <h2>{E(s['title'])}</h2>
  </div>
  <div class="body split">
    <ol class="steps">{steps}</ol>
    {img}
  </div>
</section>"""


def slide_exercise(s) -> str:
    brief = "".join(f"<li>{inline(x)}</li>" for x in s["brief"])
    success = "".join(f"<li>{inline(x)}</li>" for x in s.get("success", []))
    return f"""<section class="slide exercise">
  <div class="head">
    <div class="eyebrow">Module {E(s['num'])} · À vous de jouer</div>
    <h2>{E(s['title'])}</h2>
    <div class="chip time">⏱ {E(s.get('duration', ''))}</div>
  </div>
  <div class="body split">
    <div><div class="lbl">Consigne</div><ol class="steps">{brief}</ol></div>
    <div class="success"><div class="lbl ok">C'est réussi si…</div><ul>{success}</ul></div>
  </div>
</section>"""


def slide_pitfall(s) -> str:
    rows = "".join(f"<tr><td class=\"q\">{inline(a)}</td><td>{inline(b)}</td></tr>" for a, b in s["rows"])
    return f"""<section class="slide">
  <div class="head">
    <div class="eyebrow">Module {E(s['num'])} · Pièges fréquents</div>
    <h2>{E(s['title'])}</h2>
  </div>
  <div class="body"><table class="pitfalls">{rows}</table></div>
</section>"""


def slide_note(s) -> str:
    lines = "".join(f"<li>{inline(x)}</li>" for x in s.get("lines", []))
    table = ""
    if s.get("table"):
        head = "".join(f"<th>{inline(c)}</th>" for c in s["table"]["head"])
        body = "".join("<tr>" + "".join(f"<td>{inline(c)}</td>" for c in r) + "</tr>"
                       for r in s["table"]["rows"])
        table = f'<table class="accounts"><thead><tr>{head}</tr></thead><tbody>{body}</tbody></table>'
    return f"""<section class="slide">
  <div class="head">
    <div class="eyebrow">{E(s.get('kicker', ''))}</div>
    <h2>{E(s['title'])}</h2>
  </div>
  <div class="body">{table}<ul class="points">{lines}</ul></div>
</section>"""


def slide_pause(s) -> str:
    return f"""<section class="slide pause">
  <div class="body">
    <div class="pause-time">⏱ {E(s['time'])}</div>
    <h2 class="big">{E(s['title'])}</h2>
    <p class="lead">{inline(s.get('line', ''))}</p>
  </div>
</section>"""


def slide_closing(s) -> str:
    steps = "".join(
        f'<li><span class="when">{inline(w)}</span><span class="what">{inline(t)}</span></li>'
        for w, t in s["steps"])
    return f"""<section class="slide">
  <div class="head"><div class="eyebrow">Clôture</div><h2>{E(s['title'])}</h2></div>
  <div class="body">
    <ul class="roadmap">{steps}</ul>
    <p class="closing-note">{inline(s.get('closing_note', ''))}</p>
  </div>
</section>"""


BUILDERS = {
    "cover": slide_cover, "agenda": slide_agenda, "section": slide_section,
    "demo": slide_demo, "exercise": slide_exercise, "pitfall": slide_pitfall,
    "note": slide_note, "pause": slide_pause, "closing": slide_closing,
}


def outline() -> str:
    """Sommaire cliquable — n'affiche que les ouvertures de module et les pauses."""
    items = []
    for i, s in enumerate(DECK["slides"]):
        if s["type"] == "section":
            items.append(f'<li><button data-go="{i}"><span class="n">{E(s["num"])}</span>'
                         f'{E(s["title"])}<span class="t">{E(s["time"])}</span></button></li>')
        elif s["type"] == "pause":
            items.append(f'<li class="brk"><button data-go="{i}">{E(s["title"])}'
                         f'<span class="t">{E(s["time"])}</span></button></li>')
    return "".join(items)


def main() -> None:
    slides = "".join(BUILDERS[s["type"]](s) for s in DECK["slides"])
    with open(os.path.join(os.path.dirname(__file__), "atelier.css"), encoding="utf-8") as f:
        css = f.read()
    with open(os.path.join(os.path.dirname(__file__), "atelier.js"), encoding="utf-8") as f:
        js = f.read()

    doc = f"""<!doctype html>
<html lang="fr">
<head>
<meta charset="utf-8">
<title>{E(DECK['meta']['title'])} — BBC SMS</title>
<meta name="viewport" content="width=device-width, initial-scale=1">
<link rel="icon" type="image/png" href="/bbc-logo.png">
<link rel="preconnect" href="https://fonts.googleapis.com">
<link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
<link href="https://fonts.googleapis.com/css2?family=Manrope:wght@400;500;600;700;800&family=Fraunces:opsz,wght@9..144,400;9..144,600;9..144,700&display=swap" rel="stylesheet">
<style>{css}</style>
</head>
<body>
<div class="deck" id="deck">{slides}</div>

<nav class="bar">
  <button id="prev" title="Précédent (←)">←</button>
  <button id="next" title="Suivant (→)">→</button>
  <button id="menu" title="Sommaire (S)">Sommaire</button>
  <div class="count"><span id="pos">1</span> / {len(DECK['slides'])}</div>
  <a class="guide-link" href="/guide/" target="_blank" rel="noopener">Guide</a>
</nav>
<div class="progress"><div id="bar"></div></div>

<div class="overlay" id="overlay">
  <div class="outline">
    <h3>Sommaire de la journée</h3>
    <ul>{outline()}</ul>
  </div>
</div>

<script>{js}</script>
</body>
</html>
"""
    with open(OUT, "w", encoding="utf-8") as f:
        f.write(doc)
    print(f"✓ {OUT} ({len(doc)//1024} ko)")
    print(f"  {len(DECK['slides'])} diapositives")
    if MISSING:
        print("  ! captures manquantes :", ", ".join(sorted(set(MISSING))))


if __name__ == "__main__":
    main()

#!/usr/bin/env python3
"""
Génère le guide utilisateur BBC SMS à partir d'une source unique.

    python3 tools/guide/build.py

Sorties :
  · frontend/public/guide/index.html  — guide interactif bilingue (menu Aide)
  · GUIDE_UTILISATEUR.md              — même contenu, version française, dans le dépôt

Le contenu vit dans content.py ; les captures d'écran dans
frontend/public/guide/img/ (voir capture.js).
"""
from __future__ import annotations

import html
import os
import re
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from content import GUIDE  # noqa: E402

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
HTML_OUT = os.path.join(ROOT, "frontend", "public", "guide", "index.html")
MD_OUT = os.path.join(ROOT, "GUIDE_UTILISATEUR.md")
IMG_DIR = os.path.join(ROOT, "frontend", "public", "guide", "img")

E = html.escape


def tr(node, lang: str) -> str:
    """Texte d'un nœud bilingue {fr, en} (repli sur le français)."""
    if node is None:
        return ""
    if isinstance(node, str):
        return node
    return node.get(lang) or node.get("fr") or ""


# --------------------------------------------------------------------- inline
INLINE_HTML = [
    (re.compile(r"\*\*(.+?)\*\*"), r"<b>\1</b>"),
    (re.compile(r"`(.+?)`"), r"<code>\1</code>"),
    (re.compile(r"__(.+?)__"), r'<span class="ui">\1</span>'),
]


def inline_html(text: str) -> str:
    out = E(text)
    for pattern, repl in INLINE_HTML:
        out = pattern.sub(repl, out)
    return out


def inline_md(text: str) -> str:
    # __libellé d'interface__ → **libellé** (le Markdown n'a pas de style « UI »)
    return re.sub(r"__(.+?)__", r"**\1**", text)


def plain(text: str) -> str:
    """Texte nu, pour les attributs alt : aucun balisage n'y est interprété."""
    return re.sub(r"\*\*(.+?)\*\*|__(.+?)__|`(.+?)`",
                  lambda m: m.group(1) or m.group(2) or m.group(3), text)


def shot_exists(name: str) -> bool:
    return os.path.exists(os.path.join(IMG_DIR, f"fr-{name}.webp"))


def webp_size(path: str):
    """Dimensions d'un WebP (VP8, VP8L ou VP8X) — sans dépendance externe.

    Les attributs width/height réservent la place de l'image avant son
    chargement : sans eux, les captures qui arrivent décalent le texte et les
    liens d'ancre tombent à côté.
    """
    try:
        with open(path, "rb") as f:
            head = f.read(32)
        if head[:4] != b"RIFF" or head[8:12] != b"WEBP":
            return None
        fmt = head[12:16]
        if fmt == b"VP8 ":
            w = int.from_bytes(head[26:28], "little") & 0x3FFF
            h = int.from_bytes(head[28:30], "little") & 0x3FFF
            return w, h
        if fmt == b"VP8L":
            bits = int.from_bytes(head[21:25], "little")
            return (bits & 0x3FFF) + 1, ((bits >> 14) & 0x3FFF) + 1
        if fmt == b"VP8X":
            w = int.from_bytes(head[24:27], "little") + 1
            h = int.from_bytes(head[27:30], "little") + 1
            return w, h
    except OSError:
        return None
    return None


MISSING: list[str] = []


def check_shot(name: str) -> None:
    if name and not shot_exists(name) and name not in MISSING:
        MISSING.append(name)


# ----------------------------------------------------------------------- HTML
def figure_html(name: str, caption, lang: str, extra: str = "") -> str:
    check_shot(name)
    cap = tr(caption, lang)
    cap_html = f'<figcaption>{inline_html(cap)}</figcaption>' if cap else ""
    size = webp_size(os.path.join(IMG_DIR, f"{lang}-{name}.webp")) or webp_size(
        os.path.join(IMG_DIR, f"fr-{name}.webp"))
    dims = f' width="{size[0]}" height="{size[1]}"' if size else ""
    return (
        f'<figure class="shot {extra}">'
        f'<img data-shot="{E(name)}" alt="{E(plain(cap))}"{dims} loading="lazy" decoding="async">'
        f"{cap_html}</figure>"
    )


def block_html(b: dict, lang: str) -> str:
    kind = b.get("type", "p")

    if kind == "p":
        return f"<p>{inline_html(tr(b, lang))}</p>"

    if kind == "h":
        hid = f' id="{E(b["id"])}-{lang}"' if b.get("id") else ""
        return f"<h3{hid}>{inline_html(tr(b, lang))}</h3>"

    if kind == "note":
        tone = b.get("tone", "info")
        label = {
            "info": {"fr": "À savoir", "en": "Good to know"},
            "tip": {"fr": "Astuce", "en": "Tip"},
            "warn": {"fr": "Attention", "en": "Watch out"},
            "limit": {"fr": "Limite actuelle", "en": "Current limitation"},
        }[tone]
        return (
            f'<div class="note {tone}"><div class="note-label">{E(tr(label, lang))}</div>'
            f"<div>{inline_html(tr(b, lang))}</div></div>"
        )

    if kind == "list":
        items = "".join(f"<li>{inline_html(tr(i, lang))}</li>" for i in b["items"])
        return f"<ul>{items}</ul>"

    if kind == "figure":
        return figure_html(b["img"], b.get("caption"), lang)

    if kind == "steps":
        title = tr(b.get("title"), lang)
        head = f'<div class="steps-title">{inline_html(title)}</div>' if title else ""
        parts = []
        for n, item in enumerate(b["items"], start=1):
            body = f'<div class="step-body">{inline_html(tr(item, lang))}</div>'
            img = figure_html(item["img"], item.get("caption"), lang) if item.get("img") else ""
            parts.append(
                f'<li class="step"><div class="step-n">{n}</div>'
                f'<div class="step-main">{body}{img}</div></li>'
            )
        return f'<div class="steps">{head}<ol class="steplist">{"".join(parts)}</ol></div>'

    if kind == "table":
        head = "".join(f"<th>{inline_html(c)}</th>" for c in tr(b["head"], lang))
        rows = "".join(
            "<tr>" + "".join(f"<td>{inline_html(c)}</td>" for c in row) + "</tr>"
            for row in tr(b["rows"], lang)
        )
        cap = tr(b.get("caption"), lang)
        cap_html = f'<div class="tcap">{inline_html(cap)}</div>' if cap else ""
        return f'<div class="tablewrap">{cap_html}<table><thead><tr>{head}</tr></thead><tbody>{rows}</tbody></table></div>'

    if kind == "check":
        title = {"fr": "Fiche de test — je sais faire", "en": "Test sheet — I can do it"}
        items = "".join(
            f'<li><span class="box"></span><span>{inline_html(tr(i, lang))}</span></li>'
            for i in b["items"]
        )
        return f'<div class="check"><div class="check-title">{E(tr(title, lang))}</div><ul>{items}</ul></div>'

    raise ValueError(f"bloc inconnu : {kind}")


def anchor(ch_id: str, lang: str) -> str:
    """Les deux versions linguistiques coexistent dans la page : les identifiants
    doivent rester uniques, sinon les liens du sommaire visent la copie masquée."""
    return ch_id if lang == "fr" else f"{ch_id}-en"


def chapter_html(ch: dict, lang: str) -> str:
    blocks = "".join(block_html(b, lang) for b in ch["blocks"])
    sub = tr(ch.get("subtitle"), lang)
    sub_html = f'<p class="sub">{inline_html(sub)}</p>' if sub else ""
    who = tr(ch.get("who"), lang)
    who_html = (
        f'<div class="who"><span class="who-label">'
        f'{E("Pour qui" if lang == "fr" else "Who it is for")}</span>{inline_html(who)}</div>'
        if who else ""
    )
    return (
        f'<section id="{E(anchor(ch["id"], lang))}">'
        f'<div class="eyebrow">{E(ch.get("num",""))}</div>'
        f'<h2>{inline_html(tr(ch["title"], lang))}</h2>'
        f"{sub_html}{who_html}{blocks}</section>"
    )


def toc_html(lang: str) -> str:
    out = []
    for group in GUIDE["parts"]:
        out.append(f'<h4>{E(tr(group["title"], lang))}</h4>')
        for ch in group["chapters"]:
            out.append(
                f'<a href="#{E(anchor(ch["id"], lang))}"><span class="n">{E(ch.get("num",""))}</span>'
                f'{E(tr(ch["title"], lang))}</a>'
            )
    return "".join(out)


def build_html() -> str:
    meta = GUIDE["meta"]
    body = {}
    for lang in ("fr", "en"):
        parts = [chapter_html(ch, lang) for g in GUIDE["parts"] for ch in g["chapters"]]
        body[lang] = "".join(parts)

    with open(os.path.join(os.path.dirname(__file__), "style.css"), encoding="utf-8") as f:
        css = f.read()
    with open(os.path.join(os.path.dirname(__file__), "guide.js"), encoding="utf-8") as f:
        js = f.read()

    def hero(lang):
        return (
            f'<div class="hero"><div class="eyebrow">{E(tr(meta["kicker"], lang))}</div>'
            f'<h1>{E(tr(meta["title"], lang))}</h1>'
            f'<p class="lead">{inline_html(tr(meta["lead"], lang))}</p></div>'
        )

    return f"""<!doctype html>
<html lang="fr" data-lang="fr">
<head>
<meta charset="utf-8">
<title>{E(tr(meta["title"], "fr"))} — BBC SMS</title>
<meta name="viewport" content="width=device-width, initial-scale=1">
<meta name="description" content="{E(tr(meta["lead"], "fr"))}">
<link rel="icon" type="image/png" href="/bbc-logo.png">
<link rel="preconnect" href="https://fonts.googleapis.com">
<link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
<link href="https://fonts.googleapis.com/css2?family=Manrope:wght@400;500;600;700;800&family=Fraunces:opsz,wght@9..144,400;9..144,600;9..144,700&display=swap" rel="stylesheet">
<style>{css}</style>
</head>
<body>
<header class="topbar">
  <button class="menubtn" id="menubtn" aria-label="Menu">☰</button>
  <div class="brand">
    <img src="/bbc-logo.png" alt="BBC">
    <div>
      <div class="bt">BBC SMS</div>
      <div class="bs l-fr">Guide utilisateur</div>
      <div class="bs l-en">User guide</div>
    </div>
  </div>
  <div class="spacer"></div>
  <div class="langsw" role="group" aria-label="Langue">
    <button data-lang="fr" aria-pressed="true">FR</button>
    <button data-lang="en" aria-pressed="false">EN</button>
  </div>
  <a class="applink" href="/"><span class="l-fr">Ouvrir l'application</span><span class="l-en">Open the app</span></a>
</header>

<div class="wrap">
  <nav class="toc" id="toc">
    <div class="l-fr">{toc_html("fr")}</div>
    <div class="l-en">{toc_html("en")}</div>
  </nav>
  <main>
    <div class="l-fr">{hero("fr")}{body["fr"]}</div>
    <div class="l-en">{hero("en")}{body["en"]}</div>
    <footer class="foot">
      <span class="l-fr">Guide généré depuis <code>tools/guide/</code> — captures prises sur le jeu de démonstration.</span>
      <span class="l-en">Guide generated from <code>tools/guide/</code> — screenshots taken on the demo dataset.</span>
    </footer>
  </main>
</div>

<div class="lightbox" id="lightbox"><img alt=""></div>
<script>{js}</script>
</body>
</html>
"""


# ------------------------------------------------------------------- Markdown
def block_md(b: dict) -> str:
    kind = b.get("type", "p")
    if kind == "p":
        return inline_md(tr(b, "fr")) + "\n"
    if kind == "h":
        return f"### {inline_md(tr(b, 'fr'))}\n"
    if kind == "note":
        label = {"info": "À savoir", "tip": "Astuce", "warn": "Attention",
                 "limit": "Limite actuelle"}[b.get("tone", "info")]
        return f"> **{label}** — {inline_md(tr(b, 'fr'))}\n"
    if kind == "list":
        return "".join(f"- {inline_md(tr(i, 'fr'))}\n" for i in b["items"])
    if kind == "figure":
        check_shot(b["img"])
        cap = tr(b.get("caption"), "fr")
        return (f"![{plain(cap)}](frontend/public/guide/img/fr-{b['img']}.webp)\n"
                + (f"*{inline_md(cap)}*\n" if cap else ""))
    if kind == "steps":
        out = []
        title = tr(b.get("title"), "fr")
        if title:
            out.append(f"**{inline_md(title)}**\n")
        for n, item in enumerate(b["items"], start=1):
            out.append(f"{n}. {inline_md(tr(item, 'fr'))}\n")
            if item.get("img"):
                check_shot(item["img"])
                cap = tr(item.get("caption"), "fr") or f"Étape {n}"
                out.append(f"\n   ![{plain(cap)}](frontend/public/guide/img/fr-{item['img']}.webp)\n")
                out.append(f"   *{inline_md(cap)}*\n\n")
        return "".join(out)
    if kind == "table":
        head = tr(b["head"], "fr")
        rows = tr(b["rows"], "fr")
        out = []
        cap = tr(b.get("caption"), "fr")
        if cap:
            out.append(f"*{inline_md(cap)}*\n\n")
        out.append("| " + " | ".join(inline_md(c) for c in head) + " |\n")
        out.append("|" + "---|" * len(head) + "\n")
        for row in rows:
            out.append("| " + " | ".join(inline_md(c) for c in row) + " |\n")
        return "".join(out)
    if kind == "check":
        out = ["**Fiche de test — je sais faire**\n\n"]
        out += [f"- [ ] {inline_md(tr(i, 'fr'))}\n" for i in b["items"]]
        return "".join(out)
    raise ValueError(kind)


def build_md() -> str:
    meta = GUIDE["meta"]
    out = [f"# {tr(meta['title'], 'fr')}\n", f"\n{inline_md(tr(meta['lead'], 'fr'))}\n"]
    out.append(
        "\n> Version interactive et bilingue (FR/EN) dans l'application : menu **Aide** "
        "ou `/guide/`. Ce fichier et la version web sont générés depuis "
        "`tools/guide/content.py` (`python3 tools/guide/build.py`).\n"
    )

    out.append("\n## Sommaire\n\n")
    for group in GUIDE["parts"]:
        out.append(f"**{tr(group['title'], 'fr')}**\n\n")
        for ch in group["chapters"]:
            anchor = ch["id"]
            out.append(f"- [{ch.get('num','')} {tr(ch['title'], 'fr')}](#{anchor})\n")
        out.append("\n")

    for group in GUIDE["parts"]:
        for ch in group["chapters"]:
            out.append(f'\n<a id="{ch["id"]}"></a>\n')
            out.append(f"\n## {ch.get('num','')} {tr(ch['title'], 'fr')}\n\n")
            sub = tr(ch.get("subtitle"), "fr")
            if sub:
                out.append(f"*{inline_md(sub)}*\n\n")
            who = tr(ch.get("who"), "fr")
            if who:
                out.append(f"**Pour qui :** {inline_md(who)}\n\n")
            for b in ch["blocks"]:
                out.append(block_md(b) + "\n")
    return "".join(out)


def main() -> None:
    html_doc = build_html()
    md_doc = build_md()
    with open(HTML_OUT, "w", encoding="utf-8") as f:
        f.write(html_doc)
    with open(MD_OUT, "w", encoding="utf-8") as f:
        f.write(md_doc)
    n_ch = sum(len(g["chapters"]) for g in GUIDE["parts"])
    print(f"✓ {HTML_OUT} ({len(html_doc)//1024} ko)")
    print(f"✓ {MD_OUT} ({len(md_doc)//1024} ko)")
    print(f"  {n_ch} chapitres")
    if MISSING:
        print("  ! captures manquantes :", ", ".join(MISSING))


if __name__ == "__main__":
    main()

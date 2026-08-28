#!/usr/bin/env python3
"""Build role-aware BBC SMS help pages and Markdown operator manuals."""

from __future__ import annotations

import html
import os
from pathlib import Path

from role_guides import ROLE_GUIDES, workflow_navigation


ROOT = Path(__file__).resolve().parents[2]
WEB_OUT = ROOT / "frontend" / "public" / "guide" / "roles"
MD_OUT = ROOT / "docs" / "role-guides"
E = html.escape


def tr(value, lang: str) -> str:
    if isinstance(value, str):
        return value
    return value.get(lang) or value.get("en") or value.get("fr") or ""


def role_nav(lang: str, active: str | None = None) -> str:
    links = []
    for guide in ROLE_GUIDES:
        cls = ' class="active" aria-current="page"' if guide["slug"] == active else ""
        links.append(
            f'<a href="./{E(guide["slug"])}.html"{cls}>{E(tr(guide["title"], lang))}</a>'
        )
    links.append(
        f'<a href="../index.html">{E("Guide complet" if lang == "fr" else "Complete guide")}</a>'
    )
    return "".join(links)


def workflow_html(flow: dict, lang: str, number: int) -> str:
    steps = "".join(f"<li>{E(tr(step, lang))}</li>" for step in flow["steps"])
    note = ""
    if flow.get("note"):
        label = "À retenir" if lang == "fr" else "Remember"
        note = f'<div class="note"><b>{label}.</b> {E(tr(flow["note"], lang))}</div>'
    navigation_label = "Comment y accéder" if lang == "fr" else "How to get there"
    navigation = E(tr(workflow_navigation(flow), lang))
    return (
        '<article class="workflow">'
        f'<div class="workflow-head"><span>{number:02d}</span><div><h3>{E(tr(flow["title"], lang))}</h3></div></div>'
        f'<div class="workflow-path"><b>{navigation_label}</b><p>{navigation}</p></div>'
        f'<ol>{steps}</ol>{note}</article>'
    )


def page_panel(guide: dict, lang: str) -> str:
    lang_name = "Français" if lang == "fr" else "English"
    verified = "Vérifié sur l’application locale" if lang == "fr" else "Verified in the local application"
    verified_detail = "28-08-2026 · guide utilisateur vérifié" if lang == "fr" else "28-08-2026 · verified user guide"
    permissions = "".join(
        f'<tr><th>{E(tr(label, lang))}</th><td>{E(tr(detail, lang))}</td></tr>'
        for label, detail in guide["permissions"]
    )
    workflows = "".join(
        workflow_html(flow, lang, i) for i, flow in enumerate(guide["workflows"], 1)
    )
    boundaries = "".join(f"<li>{E(tr(item, lang))}</li>" for item in guide["boundaries"])
    checks = "".join(
        f'<li><span aria-hidden="true">□</span>{E(tr(item, lang))}</li>'
        for item in guide["verification"]
    )
    gaps = ""
    if guide["known_gaps"]:
        gap_items = "".join(f"<li>{E(tr(item, lang))}</li>" for item in guide["known_gaps"])
        gaps_title = "Limitations connues" if lang == "fr" else "Known limitations"
        gaps_intro = (
            "Ces limites ont été observées pendant les vérifications. Ne contournez pas le problème; contactez l’administrateur."
            if lang == "fr"
            else "These limitations were observed during verification. Do not work around them; contact the administrator."
        )
        gaps = f'<section class="gaps"><h2>{gaps_title}</h2><p>{gaps_intro}</p><ul>{gap_items}</ul></section>'
    labels = {
        "scope": "Périmètre" if lang == "fr" else "Scope",
        "can": "Ce que ce rôle peut faire" if lang == "fr" else "What this role can do",
        "work": "Procédures quotidiennes" if lang == "fr" else "Daily procedures",
        "limits": "Limites à respecter" if lang == "fr" else "Boundaries to respect",
        "check": "Contrôle rapide" if lang == "fr" else "Quick verification",
    }
    return f"""
    <main class="lang-panel" data-lang-panel="{lang}" lang="{lang}">
      <section class="hero">
        <div class="eyebrow">BBC SMS · {lang_name}</div>
        <h1>{E(tr(guide["title"], lang))}</h1>
        <p class="lead">{E(tr(guide["subtitle"], lang))}</p>
        <div class="verification"><span>✓</span><div><b>{verified}</b><small>{verified_detail}</small></div></div>
      </section>
      <section class="intro-grid">
        <div><h2>{labels["scope"]}</h2><p>{E(tr(guide["scope"], lang))}</p></div>
        <div><h2>{"Mission" if lang == "fr" else "Purpose"}</h2><p>{E(tr(guide["summary"], lang))}</p></div>
      </section>
      <section><h2>{labels["can"]}</h2><div class="table-wrap"><table><tbody>{permissions}</tbody></table></div></section>
      <section><h2>{labels["work"]}</h2><div class="workflow-grid">{workflows}</div></section>
      <section class="two-col"><div><h2>{labels["limits"]}</h2><ul>{boundaries}</ul></div><div class="check"><h2>{labels["check"]}</h2><ul>{checks}</ul></div></section>
{gaps}
    </main>"""


def build_page(guide: dict) -> str:
    title = E(tr(guide["title"], "en"))
    return f"""<!doctype html>
<html lang="en" data-lang="en">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <meta name="description" content="{E(tr(guide["subtitle"], "en"))}">
  <title>{title} · BBC SMS</title>
  <link rel="icon" type="image/png" href="../../bbc-logo.png">
  <link rel="stylesheet" href="../role-guide.css">
</head>
<body>
  <header class="topbar">
    <a class="brand" href="./index.html"><img src="../../bbc-logo.png" alt="BBC"><span>BBC SMS<br><small>Role guides</small></span></a>
    <nav class="role-nav" aria-label="Role guides"><div data-nav="fr">{role_nav("fr", guide["slug"])}</div><div data-nav="en">{role_nav("en", guide["slug"])}</div></nav>
    <div class="lang-switch" aria-label="Language"><button data-set-lang="fr">FR</button><button data-set-lang="en">EN</button></div>
  </header>
  <div class="page-shell">{page_panel(guide, "fr")}{page_panel(guide, "en")}</div>
  <footer>BBC SMS · Bayo Bilingual Complex · <a href="../index.html">Complete user guide</a></footer>
  <script src="../role-guide.js"></script>
</body>
</html>
"""


def build_index() -> str:
    cards_en = "".join(
        f'<a class="role-card" href="./{E(g["slug"])}.html"><h2>{E(tr(g["title"], "en"))}</h2><p>{E(tr(g["subtitle"], "en"))}</p><span>Open guide →</span></a>'
        for g in ROLE_GUIDES
    )
    cards_fr = "".join(
        f'<a class="role-card" href="./{E(g["slug"])}.html"><h2>{E(tr(g["title"], "fr"))}</h2><p>{E(tr(g["subtitle"], "fr"))}</p><span>Ouvrir le guide →</span></a>'
        for g in ROLE_GUIDES
    )
    return f"""<!doctype html><html lang="en" data-lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>Role guides · BBC SMS</title><link rel="icon" href="../../bbc-logo.png"><link rel="stylesheet" href="../role-guide.css"></head><body><header class="topbar"><a class="brand" href="./index.html"><img src="../../bbc-logo.png" alt="BBC"><span>BBC SMS<br><small>Role guides</small></span></a><div class="lang-switch"><button data-set-lang="fr">FR</button><button data-set-lang="en">EN</button></div></header><div class="page-shell"><main class="lang-panel index" data-lang-panel="fr" lang="fr"><section class="hero"><div class="eyebrow">BBC SMS</div><h1>Guides par rôle</h1><p class="lead">Choisissez votre rôle pour afficher uniquement les procédures et limites qui vous concernent.</p></section><div class="role-grid">{cards_fr}</div></main><main class="lang-panel index" data-lang-panel="en" lang="en"><section class="hero"><div class="eyebrow">BBC SMS</div><h1>Guides by role</h1><p class="lead">Choose your role to see only the procedures and boundaries that apply to you.</p></section><div class="role-grid">{cards_en}</div></main></div><footer>BBC SMS · <a href="../index.html">Complete user guide</a></footer><script src="../role-guide.js"></script></body></html>"""


def build_markdown(guide: dict) -> str:
    lang = "en"
    lines = [
        f'# {tr(guide["title"], lang)}',
        "",
        f'*{tr(guide["subtitle"], lang)}*',
        "",
        f'**Scope:** {tr(guide["scope"], lang)}',
        "",
        tr(guide["summary"], lang),
        "",
        "## What this role can do",
        "",
    ]
    for label, detail in guide["permissions"]:
        lines.append(f'- **{tr(label, lang)}:** {tr(detail, lang)}')
    lines += ["", "## Daily procedures", ""]
    for flow in guide["workflows"]:
        lines += [
            f'### {tr(flow["title"], lang)}',
            "",
            f'**How to get there:** {tr(workflow_navigation(flow), lang)}',
            "",
        ]
        lines.extend(f'{i}. {tr(step, lang)}' for i, step in enumerate(flow["steps"], 1))
        if flow.get("note"):
            lines += ["", f'> **Remember:** {tr(flow["note"], lang)}']
        lines.append("")
    lines += ["## Boundaries", ""] + [f'- {tr(item, lang)}' for item in guide["boundaries"]]
    lines += ["", "## Quick verification", ""] + [f'- [ ] {tr(item, lang)}' for item in guide["verification"]]
    if guide["known_gaps"]:
        lines += ["", "## Known limitations", "", "These limitations were observed during verification. Do not work around them; contact the administrator.", ""] + [f'- {tr(item, lang)}' for item in guide["known_gaps"]]
    lines += ["", "---", "", "User guide verified against the local application on 28 August 2026.", ""]
    return "\n".join(lines)


def main() -> None:
    WEB_OUT.mkdir(parents=True, exist_ok=True)
    MD_OUT.mkdir(parents=True, exist_ok=True)
    (WEB_OUT / "index.html").write_text(build_index(), encoding="utf-8")
    for guide in ROLE_GUIDES:
        (WEB_OUT / f'{guide["slug"]}.html').write_text(build_page(guide), encoding="utf-8")
        (MD_OUT / f'{guide["slug"]}.md').write_text(build_markdown(guide), encoding="utf-8")
    print(f"Built {len(ROLE_GUIDES)} role pages and Markdown guides.")


if __name__ == "__main__":
    main()

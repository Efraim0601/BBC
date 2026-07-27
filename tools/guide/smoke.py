#!/usr/bin/env python3
"""
Contrôle de bon fonctionnement — parcourt l'API module par module et vérifie
que chaque écran de l'atelier a bien de quoi s'afficher.

    python3 tools/guide/smoke.py [http://localhost:8080]

À lancer sur la pile de démonstration après seed-demo.py. Sortie : une ligne
par vérification, puis un décompte. Code de sortie non nul si une vérification
échoue — utilisable avant un atelier ou un déploiement.
"""
from __future__ import annotations

import json
import sys
import urllib.error
import urllib.request

BASE = (sys.argv[1] if len(sys.argv) > 1 else "http://localhost:8080").rstrip("/")
API = BASE + "/api"

OK, KO = [], []


def call(method, path, body=None, token=None, expect=200):
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(API + path, data=data, method=method)
    req.add_header("Content-Type", "application/json")
    if token:
        req.add_header("Authorization", "Bearer " + token)
    try:
        with urllib.request.urlopen(req, timeout=30) as r:
            raw = r.read().decode()
            return r.status, (json.loads(raw) if raw else None)
    except urllib.error.HTTPError as e:
        return e.code, None
    except Exception:  # noqa: BLE001
        return 0, None


def check(label, condition, detail=""):
    (OK if condition else KO).append(label)
    print(f"  {'✓' if condition else '✗'} {label}{(' — ' + detail) if detail and not condition else ''}")


def login(user):
    status, res = call("POST", "/auth/login", {"username": user, "password": "password"})
    return (res or {}).get("accessToken")


def main():
    print("Comptes")
    tokens = {u: login(u) for u in ("principal", "econome", "parent1")}
    for user, tok in tokens.items():
        check(f"connexion {user}", bool(tok))
    if not all(tokens.values()):
        sys.exit("Connexions impossibles — la pile tourne-t-elle ?")
    T, E, P = tokens["principal"], tokens["econome"], tokens["parent1"]

    print("\nParamétrage")
    _, sections = call("GET", "/setup/sections", token=T)
    _, classes = call("GET", "/setup/classes", token=T)
    _, subjects = call("GET", "/setup/subjects", token=T)
    _, matrix = call("GET", "/settings/permissions", token=T)
    _, holidays = call("GET", "/settings/holidays", token=T)
    _, catalog = call("GET", "/settings/discipline-catalog", token=T)
    check("sections", bool(sections))
    check("classes", bool(classes))
    check("matières", bool(subjects))
    check("matrice des permissions", bool(matrix and matrix.get("roles") and matrix.get("modules")))
    check("jours fériés", bool(holidays))
    check("catalogue discipline", bool(catalog))

    print("\nCommunauté")
    _, students = call("GET", "/students", token=T)
    _, staff = call("GET", "/staff", token=T)
    _, depts = call("GET", "/hr/departments", token=T)
    _, leaves = call("GET", "/hr/leaves", token=T)
    check("élèves inscrits", bool(students) and len(students) >= 10, f"{len(students or [])} élève(s)")
    check("élèves rattachés à une classe", any(s.get("className") for s in (students or [])))
    check("personnel", bool(staff))
    check("départements", bool(depts))
    check("congés", bool(leaves))

    student = next((s for s in (students or []) if s.get("className") == "4ème"), (students or [None])[0])
    sid = student["id"] if student else None

    print("\nPédagogie")
    _, board = call("GET", "/attendance/board", token=T)
    check("tableau de présence", bool(board) and (board["present"] + board["late"] + board["absent"]) > 0,
          "aucun pointage du jour")
    status, bulletin = call("GET", f"/academic/students/{sid}/bulletin?sequence=1", token=T)
    check("bulletin d'un élève", status == 200 and bool(bulletin and bulletin.get("lines")))
    status, pv = call("GET", "/academic/classes/4%C3%A8me/pv?sequence=1", token=T)
    check("procès-verbal de classe", status == 200 and bool(pv and pv.get("rows")))
    _, incidents = call("GET", "/discipline", token=T)
    check("incidents de discipline", bool(incidents))
    _, entries = call("GET", "/coursebook?className=4%C3%A8me", token=T)
    check("cahier de textes", bool(entries))
    _, slots = call("GET", "/timetable?className=4%C3%A8me", token=T)
    check("emploi du temps", bool(slots))

    print("\nFinance et paiement progressif")
    _, channels = call("GET", "/finance/channels", token=E)
    codes = {c["code"] for c in (channels or [])}
    check("canaux de paiement", {"OM", "MOMO", "MPGS"} <= codes, f"présents : {sorted(codes)}")
    check("coordonnées publiées", any(c.get("accountRef") for c in (channels or [])))
    _, grids = call("GET", "/finance/fees/config", token=E)
    check("grille de frais", bool(grids))
    check("surcharge par classe", any(g.get("classId") for g in (grids or [])))
    check("tranches nommées et datées",
          any(t.get("label") and t.get("dueOn") for g in (grids or []) for t in (g.get("tranches") or [])))
    status, stmt = call("GET", f"/finance/students/{sid}/statement", token=E)
    check("situation d'un élève", status == 200 and bool(stmt and stmt.get("tranches")))
    _, payments = call("GET", "/finance/payments", token=E)
    check("encaissements enregistrés", bool(payments))
    check("référence de transaction conservée", any(p.get("reference") for p in (payments or [])))
    status, _ = call("POST", "/finance/payments",
                     {"studentId": sid, "amount": 1000, "method": "OM"}, token=E)
    check("paiement mobile refusé sans référence", status == 400, f"HTTP {status}")
    _, debtors = call("GET", "/finance/debtors", token=E)
    check("liste des débiteurs", isinstance(debtors, list))

    print("\nCommunication et ressources")
    _, events = call("GET", "/events", token=T)
    _, notices = call("GET", "/messages", token=T)
    check("événements", bool(events))
    check("correspondance", bool(notices))
    cls4 = next((c for c in (classes or []) if c["name"] == "4ème"), None)
    if cls4:
        _, sup = call("GET", f"/classkit/supplies/classes/{cls4['id']}", token=T)
        _, books = call("GET", f"/classkit/books/classes/{cls4['id']}", token=T)
        check("fournitures publiées", bool(sup and sup.get("published") and sup.get("items")))
        check("manuels publiés", bool(books and books.get("published") and books.get("items")))

    print("\nDossier élève")
    status, journey = call("GET", f"/journey/students/{sid}", token=T)
    check("parcours scolaire", status == 200 and bool(journey and journey.get("entries")))
    status, health = call("GET", f"/health/students/{sid}", token=T)
    check("dossier santé", status == 200 and bool(health and health.get("record")))
    status, docs = call("GET", f"/documents/students/{sid}", token=T)
    check("documents", status == 200 and bool(docs and docs.get("documents")))

    print("\nPilotage")
    _, alerts = call("GET", "/alerts", token=T)
    _, fin = call("GET", "/reports/finance", token=T)
    _, demo = call("GET", "/reports/demographics", token=T)
    check("alertes", isinstance(alerts, list))
    check("rapport financier", bool(fin))
    check("démographie", bool(demo) and demo.get("total", 0) > 0)

    print("\nPortail parent")
    _, children = call("GET", "/parent/children", token=P)
    check("enfants rattachés", bool(children))
    if children:
        kid = children[0]["studentId"]
        status, fees = call("GET", f"/parent/children/{kid}/fees", token=P)
        check("situation de scolarité", status == 200 and bool(fees))
        check("solde cohérent avec la liste",
              bool(fees) and fees["balance"] == children[0]["balance"],
              f"{(fees or {}).get('balance')} ≠ {children[0]['balance']}")
        _, pchan = call("GET", "/parent/payment-channels", token=P)
        check("moyens de paiement visibles", bool(pchan))
        status, grades = call("GET", f"/parent/children/{kid}/grades", token=P)
        check("notes de l'enfant", status == 200 and isinstance(grades, list))
        other = next((s["id"] for s in (students or []) if s["id"] != kid), None)
        status, _ = call("GET", f"/parent/children/{other}/fees", token=P)
        check("cloisonnement d'un autre enfant", status == 403, f"HTTP {status}")

    print("\nSécurité")
    status, _ = call("GET", "/students")
    check("appel sans jeton refusé", status in (401, 403), f"HTTP {status}")
    status, _ = call("GET", "/students", token=P)
    check("parent bloqué hors de son portail", status == 403, f"HTTP {status}")

    print(f"\n{len(OK)} vérification(s) réussie(s), {len(KO)} échec(s)")
    if KO:
        print("Échecs :", ", ".join(KO))
        sys.exit(1)


if __name__ == "__main__":
    main()

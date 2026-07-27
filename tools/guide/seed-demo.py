#!/usr/bin/env python3
"""
Enrichit la base DÉMO avec un jeu de données de documentation.

But : les captures d'écran du guide utilisateur doivent montrer des écrans
remplis (élèves, notes, paiements, incidents, emploi du temps…) et non des
listes vides. Le script est idempotent dans les grandes lignes : relancé, il
ajoute au plus quelques doublons sans casser la base.

    python3 tools/guide/seed-demo.py [http://localhost:8080]

⚠ À n'exécuter QUE sur la pile de démonstration (make demo), jamais en prod.
"""
from __future__ import annotations

import json
import random
import sys
import urllib.error
import urllib.request
from datetime import date, timedelta

BASE = (sys.argv[1] if len(sys.argv) > 1 else "http://localhost:8080").rstrip("/")
API = BASE + "/api"
random.seed(20260727)

TOKEN = ""


def call(method: str, path: str, body=None, quiet=False, token=None, headers=None):
    url = API + path
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(url, data=data, method=method)
    req.add_header("Content-Type", "application/json")
    auth = token if token is not None else TOKEN
    if auth:
        req.add_header("Authorization", "Bearer " + auth)
    for k, v in (headers or {}).items():
        req.add_header(k, v)
    try:
        with urllib.request.urlopen(req, timeout=60) as r:
            raw = r.read().decode()
            return json.loads(raw) if raw else None
    except urllib.error.HTTPError as e:
        if not quiet:
            print(f"  ! {method} {path} -> {e.code} {e.read().decode()[:180]}")
        return None
    except Exception as e:  # noqa: BLE001
        if not quiet:
            print(f"  ! {method} {path} -> {e}")
        return None


def login():
    global TOKEN
    res = call("POST", "/auth/login", {"username": "principal", "password": "password"})
    if not res:
        sys.exit("Connexion impossible — la pile démo tourne-t-elle ?")
    TOKEN = res["accessToken"]
    print("· connecté en tant que", res["user"]["displayName"])


# --------------------------------------------------------------------------- utils
def d(offset_days: int) -> str:
    return (date.today() + timedelta(days=offset_days)).isoformat()


NOMS = [
    "ABANDA", "BELLO", "DJOUMESSI", "ETOUNDI", "FOTSO", "HAMADOU", "KAMGA", "KOUAM",
    "MBALLA", "NANA", "NDONGO", "NGUEMA", "OUSMANOU", "SADJO", "TCHOUPO", "WANKO",
    "ABOUBAKAR", "BAYIHA", "DJOKO", "ESSOMBA", "FONKOU", "IBRAHIM", "KENFACK",
]
PRENOMS_M = ["Arnaud", "Boris", "Cédric", "Didier", "Emmanuel", "Franck", "Ibrahim",
             "Junior", "Landry", "Marcel", "Nabil", "Olivier", "Patrick", "Rodrigue"]
PRENOMS_F = ["Aïcha", "Brenda", "Carine", "Danielle", "Estelle", "Fadimatou", "Grace",
             "Hortense", "Ines", "Josiane", "Larissa", "Mireille", "Nadège", "Pauline"]


def eleve_row(i: int) -> dict:
    sex = "M" if i % 2 == 0 else "F"
    prenom = random.choice(PRENOMS_M if sex == "M" else PRENOMS_F)
    nom = random.choice(NOMS)
    return {
        "name": f"{nom} {prenom}",
        "sex": sex,
        "niu": f"2501{random.randint(10000, 99999)}",
        "dob": f"{random.randint(2008, 2014)}-{random.randint(1, 12):02d}-{random.randint(1, 28):02d}",
        "birthplace": random.choice(["MAROUA", "GAROUA", "YAOUNDÉ", "DOUALA", "KOUSSERI"]),
        "repeats": i % 9 == 0,
        "parentName": f"{nom} {random.choice(PRENOMS_M)}",
        "parentPhone": f"+237 6{random.randint(70, 99)} {random.randint(10, 99)} {random.randint(10, 99)} {random.randint(10, 99)}",
    }


# --------------------------------------------------------------------------- steps
def seed_departments(staff):
    print("· départements")
    existing = {x["name"] for x in (call("GET", "/hr/departments") or [])}
    for name in ["Sciences", "Lettres & Langues", "Administration", "Vie scolaire"]:
        if name not in existing:
            call("POST", "/hr/departments", {"name": name})
    return {x["name"]: x["id"] for x in (call("GET", "/hr/departments") or [])}


STAFF = [
    ("NGONO Mireille", "F", "Permanent", "m.ngono@bbc.cm", ["form_teacher", "teacher"], "4ème", "Sciences", 340000, 0),
    ("TCHOUPO Landry", "M", "Permanent", "l.tchoupo@bbc.cm", ["teacher"], "", "Sciences", 300000, 0),
    ("BELLO Fadimatou", "F", "Permanent", "f.bello@bbc.cm", ["form_teacher", "teacher"], "3ème", "Lettres & Langues", 315000, 0),
    ("KAMGA Patrick", "M", "Vacataire", "p.kamga@bbc.cm", ["teacher"], "", "Lettres & Langues", 0, 4500),
    ("ESSOMBA Carine", "F", "Permanent", "c.essomba@bbc.cm", ["prefect"], "", "Vie scolaire", 260000, 0),
    ("SADJO Ibrahim", "M", "Vacataire", "i.sadjo@bbc.cm", ["teacher"], "", "Sciences", 0, 4000),
    ("NANA Josiane", "F", "Permanent", "j.nana@bbc.cm", ["teacher"], "", "Administration", 280000, 0),
]


def seed_staff(depts):
    print("· personnel")
    existing = {e["name"] for e in (call("GET", "/staff") or [])}
    for name, sex, typ, mail, roles, form_class, dept, sal, hourly in STAFF:
        if name in existing:
            continue
        call("POST", "/staff", {
            "name": name, "sex": sex, "type": typ, "email": mail,
            "phone": f"+237 6{random.randint(70, 99)} {random.randint(10, 99)} {random.randint(10, 99)} {random.randint(10, 99)}",
            "roles": roles, "formClass": form_class,
            "departmentId": depts.get(dept),
            "monthlySalary": sal, "hourlyRate": hourly,
            "createLogin": False,
        })
    return call("GET", "/staff") or []


def seed_leaves(staff):
    print("· congés")
    if call("GET", "/hr/leaves"):
        return
    for emp, typ, start, end, reason in [
        (staff[0]["id"], "annual", d(20), d(27), "Congé annuel"),
        (staff[1]["id"], "sick", d(-4), d(-2), "Certificat médical"),
        (staff[2]["id"], "maternity", d(35), d(120), "Congé de maternité"),
    ]:
        call("POST", "/hr/leaves", {"employeeId": emp, "type": typ,
                                    "startDate": start, "endDate": end, "reason": reason})


def seed_students(classes):
    print("· élèves")
    counts = {c["name"]: c["studentCount"] for c in (call("GET", "/setup/classes") or [])}
    plan = {"4ème": 18, "3ème": 16, "6ème": 14, "CM2": 12, "Form 4": 12}
    for cls, target in plan.items():
        cid = classes.get(cls)
        if not cid:
            continue
        missing = target - counts.get(cls, 0)
        if missing <= 0:
            continue
        rows = [eleve_row(i) for i in range(missing)]
        res = call("POST", "/students/import", {"classId": cid, "rows": rows})
        if res:
            print(f"    {cls}: +{res.get('created', 0)}")


def seed_class_teachers(classes, staff):
    print("· enseignants par classe")
    by_name = {e["name"]: e["id"] for e in staff}
    mapping = {
        "4ème": ["NGONO Mireille", "TCHOUPO Landry", "KAMGA Patrick"],
        "3ème": ["BELLO Fadimatou", "SADJO Ibrahim"],
        "6ème": ["NANA Josiane", "TCHOUPO Landry"],
    }
    for cls, names in mapping.items():
        cid = classes.get(cls)
        ids = [by_name[n] for n in names if n in by_name]
        if cid and ids:
            call("PUT", f"/setup/classes/{cid}/teachers", {"employeeIds": ids})


def seed_timetable(subjects, staff):
    print("· emploi du temps (4ème)")
    codes = [s["code"] for s in subjects][:6] or ["MATH"]
    teachers = [e["id"] for e in staff][:3]
    plan = [
        (0, 0), (0, 1), (0, 3), (1, 0), (1, 2), (2, 0), (2, 1),
        (3, 1), (3, 3), (4, 0), (4, 2), (4, 4),
    ]
    for n, (day, slot) in enumerate(plan):
        call("PUT", "/timetable/slot", {
            "className": "4ème", "dayIdx": day, "slotIdx": slot,
            "subjectCode": codes[n % len(codes)],
            "teacherId": teachers[n % len(teachers)] if teachers else None,
            "room": f"S{(n % 4) + 1}",
        }, quiet=True)


def seed_grades(subjects):
    print("· notes (séquences 1 & 2)")
    students = [s for s in (call("GET", "/students") or []) if s["className"] in ("4ème", "3ème")]
    codes = [s["code"] for s in subjects][:7]
    posted = 0
    for st in students:
        for seq in (1, 2):
            for code in codes:
                mark = round(random.uniform(6.5, 17.5), 1)
                if call("POST", "/academic/grades", {
                    "studentId": st["id"], "subjectCode": code,
                    "sequence": seq, "mark": mark,
                }, quiet=True) is not None:
                    posted += 1
    print(f"    {posted} notes")


DEVICE_ID = "c3da1bb3-2c8c-43b5-a8f1-b61e2fd1819f"
DEVICE_KEY = "dev-key-bbc-portal-a"


def seed_attendance():
    """
    Journal de présence du jour.

    Le pointage passe normalement par le lecteur d'empreintes
    (POST /api/devices/{id}/attendance). Cet endpoint est actuellement en échec
    (« No tenant bound to the current request »), on repasse donc par la saisie
    manuelle /attendance/mark, ce qui suppose le droit presence:write.
    """
    print("· présences du jour")
    call("PUT", "/settings/permissions",
         {"updates": [{"roleCode": "principal", "module": "presence", "level": "write"}]},
         quiet=True)
    login()  # le jeton porte la matrice de permissions : le rafraîchir
    students = call("GET", "/students") or []
    done = 0
    for i, st in enumerate(students):
        if i % 11 == 0:
            status, t, late = "absent", None, 0            # ~9 % d'absents
        elif i % 7 == 0:
            minute = 50 + (i % 10)
            status, t, late = "late", f"07:{minute:02d}", minute - 30   # ~14 % de retards
        else:
            status, t, late = "present", f"07:{(i % 25) + 5:02d}", 0
        res = call("POST", "/attendance/mark", {
            "studentId": st["id"], "date": d(0), "status": status,
            "checkInTime": t, "lateMinutes": late,
        }, quiet=True)
        done += 1 if res else 0
    print(f"    {done} pointages")


def econome_token() -> str:
    res = call("POST", "/auth/login", {"username": "econome", "password": "password"}, token="")
    return res["accessToken"] if res else ""


def seed_finance():
    """Encaissements et dépenses : seul le rôle économe a le droit d'écriture."""
    print("· finance (compte économe)")
    tok = econome_token()
    if not tok:
        print("    ! compte économe indisponible — étape ignorée")
        return
    students = call("GET", "/students") or []
    methods = ["Espèces", "Mobile Money", "Virement"]
    if len(call("GET", "/finance/payments", token=tok) or []) < 5:
        for i, st in enumerate(students):
            if i % 5 == 4:            # 20 % d'élèves sans aucun versement (débiteurs)
                continue
            tranches = 1 if i % 3 == 0 else 2
            for t in range(1, tranches + 1):
                call("POST", "/finance/payments", {
                    "studentId": st["id"],
                    "amount": 40000 if t == 1 else 30000,
                    "method": methods[i % 3],
                    "tranche": t,
                    "paidOn": d(-random.randint(1, 28)),
                }, quiet=True, token=tok)
    if len(call("GET", "/finance/expenses", token=tok) or []) < 3:
        for spent, cat, label, amount in [
            (d(-3), "energie", "Facture ENEO — juillet", 185000),
            (d(-7), "fournitures", "Ramettes A4 et craie", 62000),
            (d(-12), "maintenance", "Réparation groupe électrogène", 145000),
            (d(-18), "eau", "Facture CAMWATER", 48000),
            (d(-22), "transport", "Carburant bus scolaire", 96000),
            (d(-25), "internet", "Abonnement fibre", 55000),
        ]:
            call("POST", "/finance/expenses", {"spentOn": spent, "category": cat,
                                               "label": label, "amount": amount},
                 quiet=True, token=tok)


def seed_discipline():
    print("· discipline")
    if call("GET", "/discipline"):
        return
    students = [s for s in (call("GET", "/students") or []) if s["className"] == "4ème"][:5]
    faits = [
        ("Retard", "Arrivée à 08h15 sans justificatif.", "Avertissement verbal"),
        ("Conduite", "Bavardages répétés pendant le cours de mathématiques.", "Avertissement écrit"),
        ("Tenue", "Uniforme incomplet (cravate manquante).", "Avertissement verbal"),
        ("Absence", "Absence non justifiée en EPS.", "Convocation parent"),
    ]
    for i, st in enumerate(students[:4]):
        typ, desc, sanction = faits[i % len(faits)]
        call("POST", "/discipline", {
            "studentRef": st["matricule"], "incidentDate": d(-i - 1),
            "type": typ, "description": desc, "sanction": sanction,
        }, quiet=True)


def seed_coursebook(subjects):
    print("· cahier de textes")
    if call("GET", "/coursebook?className=4%C3%A8me"):
        return
    codes = [s["code"] for s in subjects][:4] or ["MATH"]
    lecons = [
        ("Théorème de Pythagore — démonstration et applications.", "Exercices 12 à 18 p. 84", 3),
        ("Le récit au passé : imparfait et passé simple.", "Rédaction d'un court récit (15 lignes)", 2),
        ("La cellule : structure et fonctions.", "Schéma annoté à rendre", 4),
        ("Present perfect vs simple past.", "Workbook p. 27", 5),
        ("Les grandes puissances économiques.", None, None),
    ]
    for i, (contenu, devoir, due) in enumerate(lecons):
        call("POST", "/coursebook", {
            "className": "4ème", "subjectCode": codes[i % len(codes)],
            "entryDate": d(-i), "content": contenu,
            "homework": devoir, "dueDate": d(due) if due else None,
        }, quiet=True)


def seed_classkit(classes):
    print("· fournitures & manuels")
    cid = classes.get("4ème")
    if not cid:
        return
    view = call("GET", f"/classkit/supplies/classes/{cid}")
    if view is not None and not view.get("items"):
        for label, qty, note in [
            ("Cahier 200 pages", 6, "grands carreaux"),
            ("Stylos bleu / rouge / vert", 3, None),
            ("Trousse de géométrie complète", 1, None),
            ("Blouse de laboratoire", 1, "cours de SVT"),
            ("Ramette de papier A4", 1, "contribution classe"),
        ]:
            call("POST", f"/classkit/supplies/classes/{cid}/items",
                 {"label": label, "quantity": qty, "note": note}, quiet=True)
        call("POST", f"/classkit/supplies/classes/{cid}/publish", {"published": True})
    view = call("GET", f"/classkit/books/classes/{cid}")
    if view is not None and not view.get("items"):
        for label, price, author, mandatory in [
            ("Mathématiques 4e — collection Excellence", 6500, "CIAM", True),
            ("Français : textes et méthodes 4e", 5800, "Hatier", True),
            ("SVT 4e — programme camerounais", 6200, "EDICEF", True),
            ("English for Cameroon — Form 4", 5000, "Macmillan", False),
        ]:
            call("POST", f"/classkit/books/classes/{cid}/items",
                 {"label": label, "price": price, "author": author, "mandatory": mandatory}, quiet=True)
        call("POST", f"/classkit/books/classes/{cid}/publish", {"published": True})


def seed_events():
    print("· événements")
    existing = {e["title"] for e in (call("GET", "/events") or [])}
    for title, typ, when, desc, audience, targets in [
        ("Réunion parents-professeurs", "meeting", d(9),
         "Rencontre trimestrielle avec les professeurs principaux, salle polyvalente à 15h.", "all", []),
        ("Journée culturelle bilingue", "culture", d(21),
         "Défilé, sketches et chorale — participation de toutes les classes.", "all", []),
        ("Devoir surveillé de mathématiques", "exam", d(4),
         "Épreuve de 2 heures, salle S2.", "classes", ["4ème", "3ème"]),
    ]:
        if title in existing:
            continue
        call("POST", "/events", {"title": title, "type": typ, "eventDate": when,
                                 "description": desc, "audience": audience, "targetClasses": targets})


def seed_health_docs_journey():
    print("· santé / documents / parcours")
    # Toute la 4ème : le guide illustre ces modules sur cette classe, et le
    # sélecteur d'élève y ouvre n'importe quel élève sans tomber sur un dossier vide.
    students = [s for s in (call("GET", "/students") or []) if s["className"] == "4ème"]
    if not students:
        return
    for st in students:
        sid = st["id"]
        call("PUT", f"/health/students/{sid}/record", {
            "bloodGroup": random.choice(["O+", "A+", "B+", "AB+"]),
            "allergies": "Aucune allergie connue.",
            "conditions": "RAS",
            "vaccinations": "Carnet à jour (BCG, DTC, ROR).",
            "doctorName": "Dr. NKOU Sylvain", "doctorPhone": "+237 677 11 22 33",
            "heightCm": random.randint(140, 172), "weightKg": random.randint(38, 62),
        }, quiet=True)
        call("POST", f"/health/students/{sid}/visits", {
            "visitDate": d(-6), "reason": "Céphalées", "treatment": "Repos + paracétamol, retour en classe.",
        }, quiet=True)
        call("POST", f"/health/students/{sid}/activities", {
            "name": random.choice(["Football", "Chorale", "Club scientifique"]),
            "category": random.choice(["sport", "art", "club"]),
            "role": "Membre", "season": "2025-2026",
        }, quiet=True)
        call("POST", f"/documents/students/{sid}/files", {
            "kind": "birth", "title": "Acte de naissance", "note": "Copie certifiée conforme",
        }, quiet=True)
        call("POST", f"/documents/students/{sid}/files", {
            "kind": "report", "title": "Bulletin année précédente", "note": "Transmis par la famille",
        }, quiet=True)
        call("POST", f"/journey", {
            "studentId": sid, "academicYear": "2024-2025",
            "className": st["className"], "result": "promoted",
            "generalAverage": round(random.uniform(10, 15), 2),
            "rank": random.randint(1, 20), "classSize": 25,
            "decision": "Admis en classe supérieure",
        }, quiet=True)


def seed_holidays():
    print("· calendrier")
    if call("GET", "/settings/holidays"):
        return
    for day, label in [(d(3), "Fête de la Jeunesse"), (d(14), "Assomption"),
                       (d(30), "Congé de mi-trimestre")]:
        call("POST", "/settings/holidays", {"date": day, "label": label}, quiet=True)


def seed_alerts():
    print("· scan des alertes")
    call("POST", "/alerts/scan", {})


def main():
    login()
    classes = {c["name"]: c["id"] for c in (call("GET", "/setup/classes") or [])}
    subjects = call("GET", "/setup/subjects") or []
    depts = seed_departments(None)
    staff = seed_staff(depts)
    seed_leaves(staff)
    seed_students(classes)
    classes = {c["name"]: c["id"] for c in (call("GET", "/setup/classes") or [])}
    seed_class_teachers(classes, staff)
    seed_timetable(subjects, staff)
    seed_grades(subjects)
    seed_attendance()
    seed_finance()
    seed_discipline()
    seed_coursebook(subjects)
    seed_classkit(classes)
    seed_events()
    seed_health_docs_journey()
    seed_holidays()
    seed_alerts()
    print("✓ jeu de données de documentation prêt")


if __name__ == "__main__":
    main()

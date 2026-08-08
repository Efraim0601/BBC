# -*- coding: utf-8 -*-
"""
Source unique du guide utilisateur BBC SMS.

Le texte est bilingue : chaque nœud porte une clé `fr` et une clé `en`.
`build.py` en tire le guide web (bilingue) et GUIDE_UTILISATEUR.md (français).

Conventions d'écriture dans les textes :
  **gras**      mise en valeur
  __libellé__   libellé exact d'un bouton, onglet ou champ de l'application
  `code`        nom de fichier, colonne d'import, identifiant technique

Types de blocs disponibles : p, h, list, note (info|tip|warn|limit), steps,
figure, table, check.
"""
from chapters_start import CH_PRISE_EN_MAIN, CH_ROLES
from chapters_setup import CH_PARAMETRES, CH_ELEVES, CH_PERSONNEL
from chapters_teaching import CH_PRESENCE, CH_ACADEMIQUE, CH_DISCIPLINE, CH_CAHIER, CH_EMPLOI
from chapters_ops import (
    CH_FINANCE, CH_EVENEMENTS, CH_CORRESPONDANCE, CH_FOURNITURES,
    CH_PARCOURS_SCOLAIRE, CH_PASSAGE_DE_CLASSE, CH_SANTE, CH_DOCUMENTS,
)
from chapters_steering import CH_PILOTAGE, CH_PARENT, CH_RENTREE, CH_FAQ, CH_ANNEXES

GUIDE = {
    "meta": {
        "kicker": {"fr": "Bayo Bilingual Complex — Maroua", "en": "Bayo Bilingual Complex — Maroua"},
        "title": {"fr": "Guide utilisateur BBC SMS", "en": "BBC SMS user guide"},
        "lead": {
            "fr": "Un tutoriel par module : chaque procédure est décrite étape par étape, avec la capture "
                  "d'écran correspondante. Commencez par le chapitre 1, puis lisez le chapitre qui correspond "
                  "à votre fonction — chacun se termine par une fiche de test pour valider votre prise en main.",
            "en": "One tutorial per module: every procedure is described step by step, with the matching "
                  "screenshot. Start with chapter 1, then read the chapter matching your role — each ends with a "
                  "test sheet so you can confirm you are up to speed.",
        },
    },
    "parts": [
        {
            "title": {"fr": "Démarrer", "en": "Getting started"},
            "chapters": [CH_PRISE_EN_MAIN, CH_ROLES],
        },
        {
            "title": {"fr": "Communauté", "en": "Community"},
            "chapters": [CH_PARAMETRES, CH_ELEVES, CH_PERSONNEL],
        },
        {
            "title": {"fr": "Pédagogie", "en": "Education"},
            "chapters": [CH_PRESENCE, CH_ACADEMIQUE, CH_DISCIPLINE, CH_CAHIER, CH_EMPLOI],
        },
        {
            "title": {"fr": "Opérations", "en": "Operations"},
            "chapters": [
                CH_FINANCE, CH_EVENEMENTS, CH_CORRESPONDANCE, CH_FOURNITURES,
                CH_PARCOURS_SCOLAIRE, CH_PASSAGE_DE_CLASSE, CH_SANTE, CH_DOCUMENTS,
            ],
        },
        {
            "title": {"fr": "Pilotage & familles", "en": "Steering & families"},
            "chapters": [CH_PILOTAGE, CH_PARENT],
        },
        {
            "title": {"fr": "Aller plus loin", "en": "Going further"},
            "chapters": [CH_RENTREE, CH_FAQ, CH_ANNEXES],
        },
    ],
}

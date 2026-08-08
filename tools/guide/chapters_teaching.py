# -*- coding: utf-8 -*-
"""Chapitres 6 à 10 — présence, académique, discipline, cahier de textes, emploi du temps."""

CH_PRESENCE = {
    "id": "presence",
    "num": "6",
    "title": {"fr": "Présence", "en": "Attendance"},
    "subtitle": {
        "fr": "Tableau temps réel du lecteur d'empreintes, journal du jour et historique.",
        "en": "Real-time board fed by the fingerprint reader, daily journal and history.",
    },
    "who": {"fr": "Préfet, vie scolaire, direction ; lecture pour les enseignants.",
            "en": "Prefect, school life, management; read-only for teachers."},
    "blocks": [
        {"type": "p", "fr":
            "Le module affiche les pointages **au fil de l'eau** : chaque passage de badge remonte à l'écran sans "
            "rafraîchir la page. Il ne demande aucune saisie quotidienne.",
         "en":
            "The module shows check-ins **as they happen**: every badge scan reaches the screen without reloading "
            "the page. It requires no daily data entry."},
        {"type": "figure", "img": "50-presence-tableau",
         "caption": {"fr": "En haut : le lecteur et le dernier élève scanné. À droite : les quatre indicateurs et le flux en direct.",
                     "en": "Top: the reader and the last student scanned. Right: the four indicators and the live feed."}},

        {"type": "h", "fr": "6.1 Lire le tableau", "en": "6.1 Reading the board"},
        {"type": "table",
         "head": {"fr": ["Élément", "Signification"], "en": ["Element", "Meaning"]},
         "rows": {"fr": [
             ["Carte lecteur", "État de la liaison et **dernier élève scanné** : nom, classe, heure et statut."],
             ["Taux de présence", "(présents + retards) ÷ total des pointages du jour."],
             ["Retards du jour", "Arrivées après l'heure de début des cours, avec le nombre de minutes."],
             ["Absents du jour", "Élèves attendus sans pointage."],
             ["Scans en direct", "Les douze derniers passages, du plus récent au plus ancien."],
         ], "en": [
             ["Reader card", "Link status and **last student scanned**: name, class, time and status."],
             ["Attendance rate", "(present + late) ÷ total check-ins for the day."],
             ["Lates today", "Arrivals after the school start time, with the number of minutes."],
             ["Absent today", "Expected students with no check-in."],
             ["Live scans", "The last twelve scans, most recent first."],
         ]}},
        {"type": "note", "tone": "info", "fr":
            "Le statut **retard** se calcule à partir de __Paramètres → Général → Début des cours__. "
            "Les **week-ends** et les **jours fériés** du calendrier ne produisent jamais de retard ni d'absence.",
         "en":
            "The **late** status is computed from __Settings → General → School start__. **Weekends** and calendar "
            "**holidays** never produce lateness or absence."},

        {"type": "h", "fr": "6.2 Le journal de présence", "en": "6.2 The presence journal"},
        {"type": "steps", "items": [
            {"fr": "Faites défiler jusqu'au __Journal de présence__ : une ligne par élève, avec matricule, classe, "
                   "heure de scan, statut, source du pointage et minutes de retard.",
             "en": "Scroll to the __Presence journal__: one row per student, with ID, class, scan time, status, "
                   "source and minutes late.",
             "img": "51-presence-journal",
             "caption": {"fr": "Journal du jour, filtrable par classe et par statut.",
                         "en": "Today's journal, filterable by class and status."}},
            {"fr": "Filtrez par **classe** puis par **statut** (Présents / Retards / Absents), ou cherchez un élève "
                   "par nom ou matricule. Les lignes restent triées par classe puis par nom.",
             "en": "Filter by **class** then by **status** (Present / Late / Absent), or search a student by name or "
                   "ID. Rows stay sorted by class then name."},
        ]},

        {"type": "h", "fr": "6.3 Consulter une journée passée", "en": "6.3 Look up a past day"},
        {"type": "steps", "items": [
            {"fr": "Choisissez une date dans le sélecteur en haut à droite de l'écran. Le tableau et le journal "
                   "rechargent la journée demandée.",
             "en": "Pick a date in the selector at the top right of the screen. The board and the journal reload "
                   "that day.",
             "img": "52-presence-historique",
             "caption": {"fr": "Historique : la même vue, sur une date antérieure.",
                         "en": "History: the same view, on an earlier date."}},
            {"fr": "La mise à jour en direct ne concerne que la **journée du jour** : sur une date passée, la vue "
                   "reste figée, ce qui est le comportement attendu pour une consultation.",
             "en": "Live updates only apply to **today**: on a past date the view stays frozen, which is what you "
                   "want when consulting history."},
        ]},
        {"type": "note", "tone": "limit", "fr":
            "Il n'existe pas d'écran de **saisie manuelle** des présences : les pointages proviennent du lecteur "
            "d'empreintes installé au portail (ou d'un appel technique équivalent). Pour un relevé mensuel par "
            "élève, utilisez __Rapports → Présence mensuelle__ (chapitre 18).",
         "en":
            "There is no **manual entry** screen for attendance: check-ins come from the fingerprint reader at the "
            "gate (or an equivalent technical call). For a monthly per-student summary use __Reports → Monthly "
            "attendance__ (chapter 18)."},
        {"type": "check", "items": [
            {"fr": "Lire le taux de présence du jour et le nombre de retards.",
             "en": "Read today's attendance rate and the number of lates."},
            {"fr": "Filtrer le journal sur une classe puis sur « Retards ».",
             "en": "Filter the journal on one class then on “Late”."},
            {"fr": "Afficher la journée d'hier.", "en": "Display yesterday."},
            {"fr": "Modifier l'heure de début des cours et vérifier l'effet sur le seuil de retard.",
             "en": "Change the school start time and check the effect on the lateness threshold."},
        ]},
    ],
}


CH_ACADEMIQUE = {
    "id": "academique",
    "num": "7",
    "title": {"fr": "Académique — bulletins et procès-verbaux", "en": "Academic — report cards and master sheets"},
    "subtitle": {
        "fr": "Bulletin individuel par séquence, appréciation, validation, PV de classe et impression en lot.",
        "en": "Individual report card per sequence, appreciation, validation, class master sheet and batch printing.",
    },
    "who": {"fr": "Direction, censeur, professeurs principaux (droit __Académique : Complet__ pour valider).",
            "en": "Management, dean of studies, form teachers (__Academic: Write__ to validate)."},
    "blocks": [
        {"type": "p", "fr":
            "Le module comporte deux onglets : __Bulletin__ (un élève) et __Procès-verbal__ (toute la classe). "
            "Les deux travaillent sur le couple **classe + séquence** choisi dans la barre d'outils.",
         "en":
            "The module has two tabs: __Report card__ (one student) and __Master sheet__ (the whole class). Both "
            "work on the **class + sequence** pair chosen in the toolbar."},

        {"type": "h", "fr": "7.1 Ouvrir un bulletin", "en": "7.1 Open a report card"},
        {"type": "steps", "items": [
            {"fr": "Choisissez la **classe** dans la liste déroulante, puis la **séquence** (Séq. 1 à 6) sur la "
                   "ligne de boutons.",
             "en": "Pick the **class** in the drop-down, then the **sequence** (Seq. 1 to 6) on the button row.",
             "img": "60-academique-choix-classe",
             "caption": {"fr": "Barre d'outils : classe à gauche, séquence à droite.",
                         "en": "Toolbar: class on the left, sequence on the right."}},
            {"fr": "La liste des élèves de la classe apparaît à gauche, avec un champ de recherche.",
             "en": "The class roster appears on the left, with a search box.",
             "img": "61-academique-liste-eleves",
             "caption": {"fr": "Liste de classe — cliquez un élève pour afficher son bulletin.",
                         "en": "Class roster — click a student to open their report card."}},
            {"fr": "Cliquez l'élève : son bulletin s'affiche à droite, en-tête officiel compris.",
             "en": "Click the student: their report card opens on the right, official header included.",
             "img": "62-academique-bulletin",
             "caption": {"fr": "Bulletin complet — matières, coefficients, notes, pondération, moyennes et rang.",
                         "en": "Full report card — subjects, coefficients, marks, weighting, averages and rank."}},
        ]},
        {"type": "table",
         "head": {"fr": ["Bloc", "Contenu"], "en": ["Block", "Content"]},
         "rows": {"fr": [
             ["En-tête", "Autorité de tutelle, nom et ville de l'école, séquence, badge « Validé » ou « En attente »."],
             ["Identité", "Nom de l'élève, classe et rang sur l'effectif."],
             ["Tableau", "Une ligne par matière : coefficient, note /20, note pondérée et appréciation automatique."],
             ["Synthèse", "Moyenne de l'élève, rang, moyenne de la classe."],
             ["Appréciation générale", "Zone de saisie libre tant que le bulletin n'est pas validé."],
             ["Visa", "Emplacement du visa et du cachet du principal."],
         ], "en": [
             ["Header", "Supervising authority, school name and city, sequence, “Validated” or “Awaiting” badge."],
             ["Identity", "Student name, class and rank out of the class size."],
             ["Table", "One row per subject: coefficient, mark /20, weighted mark and automatic appreciation."],
             ["Summary", "Student average, rank, class average."],
             ["General appreciation", "Free-text area while the report card is not validated."],
             ["Signature", "Space for the principal's signature and seal."],
         ]}},
        {"type": "note", "tone": "info", "fr":
            "L'appréciation par matière est déduite de la note : Excellent (≥ 16), Très bien (≥ 14), Bien (≥ 12), "
            "Assez bien (≥ 10), Passable (≥ 8), Insuffisant en dessous.",
         "en":
            "The per-subject appreciation is derived from the mark: Excellent (≥ 16), Very good (≥ 14), Good (≥ 12), "
            "Fair (≥ 10), Pass (≥ 8), Insufficient below."},

        {"type": "h", "fr": "7.2 Valider et imprimer", "en": "7.2 Validate and print"},
        {"type": "steps", "items": [
            {"fr": "Rédigez l'**appréciation générale** dans la zone prévue.",
             "en": "Write the **general appreciation** in the dedicated area."},
            {"fr": "Cliquez __Valider le bulletin__ : le badge passe à « Validé », l'appréciation est figée et le "
                   "visa affiche « Bulletin validé ».",
             "en": "Click __Validate report card__: the badge switches to “Validated”, the appreciation is frozen "
                   "and the signature area reads “Validated”."},
            {"fr": "__Imprimer__ ouvre la boîte d'impression du navigateur avec une mise en page dédiée (menus et "
                   "barres masqués). Choisissez « Enregistrer au format PDF » pour archiver.",
             "en": "__Print__ opens the browser print dialog with a dedicated layout (menus and bars hidden). Choose "
                   "“Save as PDF” to archive."},
            {"fr": "__Tous les bulletins de la classe__ charge et met en page les bulletins de **tous** les élèves, "
                   "puis lance une seule impression. Les bulletins bloqués pour impayés sont automatiquement exclus.",
             "en": "__All class report cards__ loads and lays out **every** student's report card, then triggers a "
                   "single print job. Report cards blocked for unpaid fees are automatically excluded."},
        ]},

        {"type": "h", "fr": "7.3 Bulletin bloqué pour impayés", "en": "7.3 Report card blocked for unpaid fees"},
        {"type": "p", "fr":
            "Si l'élève a un solde de frais impayé, le bulletin s'affiche mais porte un bandeau rouge « Bulletin "
            "verrouillé ». La validation et l'impression sont désactivées jusqu'au règlement.",
         "en":
            "If the student has an outstanding fee balance, the report card is displayed but carries a red "
            "“Report card locked” banner. Validation and printing are disabled until payment."},
        {"type": "figure", "img": "64-academique-bulletin-bloque",
         "caption": {"fr": "Bulletin verrouillé : le blocage vient du solde suivi dans le module Finance.",
                     "en": "Locked report card: the block comes from the balance tracked in the Finance module."}},
        {"type": "note", "tone": "tip", "fr":
            "Pour débloquer, enregistrez le versement dans __Finance → Nouveau paiement__ (chapitre 11) puis "
            "rouvrez le bulletin : le bandeau disparaît immédiatement.",
         "en":
            "To unlock, record the payment in __Finance → New payment__ (chapter 11) then reopen the report card: "
            "the banner disappears at once."},

        {"type": "h", "fr": "7.4 Le procès-verbal de classe", "en": "7.4 The class master sheet"},
        {"type": "steps", "items": [
            {"fr": "Onglet __Procès-verbal__, vérifiez la classe et la séquence, puis cliquez __Charger le PV__.",
             "en": "__Master sheet__ tab, check the class and sequence, then click __Load master sheet__.",
             "img": "63-academique-pv",
             "caption": {"fr": "Classement de la classe par moyenne, avec la moyenne générale en haut à droite.",
                         "en": "Class ranking by average, with the class average at the top right."}},
            {"fr": "Le tableau donne le rang, l'élève et sa moyenne, trié du premier au dernier. __Imprimer__ "
                   "produit le document à afficher ou à archiver.",
             "en": "The table gives rank, student and average, sorted from first to last. __Print__ produces the "
                   "document to post or archive."},
        ]},

        {"type": "h", "fr": "7.5 Maternelle et primaire : le bulletin APC",
         "en": "7.5 Kindergarten and primary: the competency report card"},
        {"type": "p", "fr":
            "Dès que la classe choisie relève de la **maternelle** ou du **primaire**, l'application affiche le "
            "bulletin par **compétences** (APC) au lieu du bulletin par matières : six compétences, leurs "
            "sous-compétences, et pour chacune les types d'évaluation (orale, écrite, pratique, savoir-être) avec "
            "leur barème.",
         "en":
            "As soon as the selected class belongs to **kindergarten** or **primary**, the app shows the "
            "**competency-based** (APC) report card instead of the subject-based one: six competencies, their "
            "sub-competencies, and for each the evaluation types (oral, written, practical, attitude) with their "
            "scales."},
        {"type": "p", "fr":
            "Le barème n'est pas unique : il change de **sous-système** et de **classe**. L'application choisit le "
            "bon d'après la classe de l'élève, sans réglage — y compris en mode « Tous les parcours », où aucun "
            "sous-système n'est actif.",
         "en":
            "There is no single scale: it changes with the **sub-system** and the **class**. The app picks the right "
            "one from the student's class, with nothing to configure — including in “All parcours” mode, where no "
            "sub-system is active."},
        {"type": "table",
         "head": {"fr": ["Sous-système", "Barème", "Classes", "Total"],
                  "en": ["Sub-system", "Scale", "Classes", "Total"]},
         "rows": {
            "fr": [
                ["Francophone", "Niveau I", "SIL, CP", "280"],
                ["Francophone", "Niveaux II et III", "CE1, CE2, CM1, CM2", "300"],
                ["Anglophone", "Level 1", "Class 1, Class 2", "280"],
                ["Anglophone", "Levels 2 et 3", "Class 3 à Class 6", "360"],
            ],
            "en": [
                ["Francophone", "Level I", "SIL, CP", "280"],
                ["Francophone", "Levels II and III", "CE1, CE2, CM1, CM2", "300"],
                ["Anglophone", "Level 1", "Class 1, Class 2", "280"],
                ["Anglophone", "Levels 2 and 3", "Class 3 to Class 6", "360"],
            ],
         },
         "caption": {"fr": "Les quatre barèmes officiels transcrits des classeurs de l'établissement.",
                     "en": "The four official scales transcribed from the school's workbooks."}},
        {"type": "p", "fr":
            "Les colonnes suivent elles aussi le modèle d'origine : le francophone évalue par **unité "
            "d'apprentissage** (UA1 à UA8, réparties sur trois trimestres) avec une note et une cote pour chacune ; "
            "l'anglophone évalue **mensuellement** (1 à 8), totalise chaque trimestre, puis porte un total annuel et "
            "l'échelle d'appréciation officielle (C/SNA, B/SPA, A/SA, A+/Expert).",
         "en":
            "The columns follow the source templates too: the Francophone one evaluates by **learning unit** (UA1 to "
            "UA8, spread over three terms) with a mark and a grade for each; the Anglophone one evaluates "
            "**monthly** (1 to 8), totals each term, then carries an annual total and the official appreciation "
            "scale (C/SNA, B/SPA, A/SA, A+/Expert)."},
        {"type": "note", "tone": "info", "fr":
            "En compétence 6, les activités sportives des apprenants **aptes** et **inaptes** sont deux lignes "
            "alternatives : un élève relève de l'une ou de l'autre, et une seule compte dans le total — c'est ce qui "
            "donne 40 points à la compétence et non 60. Le bulletin le signale sur la ligne concernée.",
         "en":
            "In competency 6, the sports activities for **able** and **physically challenged** learners are two "
            "alternative rows: a student falls under one or the other, and only one counts towards the total — which "
            "is why the competency is worth 40 marks and not 60. The report card flags the row accordingly."},
        {"type": "note", "tone": "limit", "fr":
            "La **maternelle** n'a pas de modèle propre dans les documents de l'établissement : elle reprend pour "
            "l'instant le barème de la première année du primaire, dans son sous-système. Fournissez le modèle "
            "maternelle pour qu'il soit transcrit à son tour.",
         "en":
            "**Kindergarten** has no template of its own in the school's documents: for now it reuses the "
            "first-year primary scale of its sub-system. Provide the kindergarten template and it will be "
            "transcribed too."},
        {"type": "note", "tone": "limit", "fr":
            "Le bulletin APC est aujourd'hui une **feuille imprimable conforme** : les cases de notes sont laissées "
            "vides pour un remplissage manuel. Les moyennes chiffrées automatiques restent réservées au secondaire.",
         "en":
            "The APC report card is currently a **conformant printable sheet**: the mark cells are left blank for "
            "manual completion. Automatic numeric averages remain a secondary-school feature."},
        {"type": "note", "tone": "limit", "fr":
            "Il n'y a pas encore d'écran de **saisie des notes** dans l'interface : les notes sont alimentées par "
            "l'intégration technique (import ou service de saisie). Ce module se concentre sur la **restitution** "
            "— bulletin, PV, validation, impression.",
         "en":
            "There is no **mark-entry** screen in the interface yet: marks are fed by the technical integration "
            "(import or entry service). This module focuses on **output** — report card, master sheet, validation, "
            "printing."},
        {"type": "check", "items": [
            {"fr": "Afficher le bulletin d'un élève pour la séquence 1.",
             "en": "Display a student's report card for sequence 1."},
            {"fr": "Saisir une appréciation générale et valider le bulletin.",
             "en": "Write a general appreciation and validate the report card."},
            {"fr": "Repérer un bulletin bloqué et expliquer pourquoi.",
             "en": "Spot a blocked report card and explain why."},
            {"fr": "Charger le PV de la classe et vérifier la cohérence du rang.",
             "en": "Load the class master sheet and check the rank is consistent."},
            {"fr": "Lancer l'impression de tous les bulletins de la classe.",
             "en": "Trigger printing of all the class report cards."},
        ]},
    ],
}


CH_DISCIPLINE = {
    "id": "discipline",
    "num": "8",
    "title": {"fr": "Discipline", "en": "Discipline"},
    "subtitle": {"fr": "Incidents, sanctions et notification immédiate des parents.",
                 "en": "Incidents, sanctions and immediate parent notification."},
    "who": {"fr": "Préfet, surveillance, direction.", "en": "Prefect, supervision, management."},
    "blocks": [
        {"type": "figure", "img": "70-discipline-liste",
         "caption": {"fr": "À gauche les incidents récents, à droite le panneau de notification des parents.",
                     "en": "Recent incidents on the left, parent notification panel on the right."}},

        {"type": "h", "fr": "8.1 Enregistrer un incident", "en": "8.1 Record an incident"},
        {"type": "steps", "items": [
            {"fr": "Cliquez __Nouvel incident__. Choisissez d'abord la **classe**, puis l'**élève** dans la liste "
                   "déroulante qui se remplit : la fiche de l'élève s'affiche pour confirmer votre choix.",
             "en": "Click __New incident__. First pick the **class**, then the **student** in the drop-down that "
                   "fills in: the student's card is displayed so you can confirm.",
             "img": "71-discipline-formulaire",
             "caption": {"fr": "Sélection classe → élève, puis date, motif, sanction et description.",
                         "en": "Class → student selection, then date, type, sanction and description."}},
            {"fr": "Renseignez la **date** (par défaut aujourd'hui), le **motif** et éventuellement la **sanction**. "
                   "Ces deux listes proviennent du catalogue __Paramètres → Discipline__ (§3.6).",
             "en": "Fill in the **date** (today by default), the **type** and optionally the **sanction**. Both "
                   "lists come from the __Settings → Discipline__ catalogue (§3.6)."},
            {"fr": "Ajoutez une **description** factuelle — c'est elle qui sera relue en conseil de discipline — "
                   "puis __Enregistrer__.",
             "en": "Add a factual **description** — it is what will be re-read at the discipline board — then __Save__."},
        ]},
        {"type": "p", "fr":
            "Chaque incident enregistré affiche l'élève, sa classe, un badge coloré selon le motif, la description, "
            "la date et la sanction. La croix rouge supprime l'incident (droit d'écriture requis).",
         "en":
            "Each recorded incident shows the student, their class, a colour badge for the type, the description, "
            "the date and the sanction. The red cross deletes it (write permission required)."},

        {"type": "h", "fr": "8.2 Notifier le parent", "en": "8.2 Notify the parent"},
        {"type": "steps", "items": [
            {"fr": "Cliquez l'icône **cloche** sur la ligne de l'incident : le panneau de droite se pré-remplit "
                   "avec l'élève et le modèle de message correspondant au motif.",
             "en": "Click the **bell** icon on the incident row: the right-hand panel is pre-filled with the student "
                   "and the message template matching the type.",
             "img": "72-discipline-notification",
             "caption": {"fr": "Message prêt à partir — modèle Absence, Retard, Convocation ou Fermeture.",
                         "en": "Message ready to send — Absence, Late, Summons or Closure template."}},
            {"fr": "Choisissez le **modèle** voulu ; le texte se réécrit avec le nom de l'élève et le compteur de "
                   "caractères se met à jour (utile pour le coût d'un SMS).",
             "en": "Choose the **template**; the text is rewritten with the student's name and the character counter "
                   "updates (handy for SMS cost)."},
            {"fr": "Envoyez par __SMS__ (numéro du contact principal) ou par __Envoyer__ (e-mail). Le résultat "
                   "s'affiche aussitôt : message envoyé, ou motif d'échec — le plus souvent « pas de téléphone "
                   "renseigné ».",
             "en": "Send via __SMS__ (main contact's number) or __Send__ (e-mail). The outcome is displayed at once: "
                   "message sent, or the reason for failure — most often “no phone number on file”."},
        ]},
        {"type": "note", "tone": "info", "fr":
            "Le destinataire est le contact principal de la fiche élève (père → mère → tuteur). Si l'envoi échoue "
            "faute de numéro, complétez la fiche dans __Élèves__ puis relancez.",
         "en":
            "The recipient is the main contact on the student record (father → mother → guardian). If sending fails "
            "for a missing number, complete the record in __Students__ then try again."},
        {"type": "check", "items": [
            {"fr": "Enregistrer un incident avec un motif du catalogue.",
             "en": "Record an incident using a catalogue type."},
            {"fr": "Ajouter une sanction personnalisée dans Paramètres et la retrouver ici.",
             "en": "Add a custom sanction in Settings and find it here."},
            {"fr": "Pré-remplir une notification depuis un incident et l'envoyer.",
             "en": "Pre-fill a notification from an incident and send it."},
            {"fr": "Lire le message de résultat et le comprendre.",
             "en": "Read the result message and understand it."},
        ]},
    ],
}


CH_CAHIER = {
    "id": "cahier-de-textes",
    "num": "9",
    "title": {"fr": "Cahier de textes", "en": "Coursebook"},
    "subtitle": {"fr": "Journal de classe et devoirs, jour par jour et par matière.",
                 "en": "Class log and homework, day by day and per subject."},
    "who": {"fr": "Enseignants et professeurs principaux ; consultation pour la direction.",
            "en": "Teachers and form teachers; read-only for management."},
    "blocks": [
        {"type": "steps", "items": [
            {"fr": "Choisissez la **classe** : les entrées sont regroupées par jour, du plus récent au plus ancien. "
                   "Deux compteurs indiquent le nombre d'entrées et le nombre de devoirs.",
             "en": "Pick the **class**: entries are grouped by day, most recent first. Two counters show the number "
                   "of entries and the number of homework assignments.",
             "img": "80-cahier-textes",
             "caption": {"fr": "Cahier de textes d'une classe : une carte par journée, une entrée par matière.",
                         "en": "A class coursebook: one card per day, one entry per subject."}},
            {"fr": "__Nouvelle entrée__ : choisissez la **matière** (limitée au sous-système de la classe), la "
                   "**date du cours**, puis décrivez le **contenu traité**.",
             "en": "__New entry__: choose the **subject** (limited to the class sub-system), the **lesson date**, "
                   "then describe the **content covered**.",
             "img": "81-cahier-textes-formulaire",
             "caption": {"fr": "Formulaire : contenu du cours, devoir facultatif et date de remise.",
                         "en": "Form: lesson content, optional homework and due date."}},
            {"fr": "Renseignez le **devoir à faire** et sa **date de remise** si nécessaire — ils apparaissent en "
                   "encadré sous le contenu du cours.",
             "en": "Fill in the **homework** and its **due date** if relevant — they appear in a box under the "
                   "lesson content."},
            {"fr": "__Enregistrer__. Les icônes crayon et corbeille de chaque entrée permettent de la corriger ou "
                   "de la supprimer.",
             "en": "__Save__. The pencil and bin icons on each entry let you correct or delete it."},
        ]},
        {"type": "note", "tone": "tip", "fr":
            "Renseigner le cahier au fil des séances prend deux minutes et rend service à tout le monde : "
            "les collègues remplaçants, les parents qui appellent, et l'inspection.",
         "en":
            "Filling the coursebook as you go takes two minutes and helps everyone: substitute colleagues, parents "
            "who call, and inspectors."},
        {"type": "check", "items": [
            {"fr": "Ajouter une entrée avec un devoir et une date de remise.",
             "en": "Add an entry with homework and a due date."},
            {"fr": "Corriger une entrée existante.", "en": "Edit an existing entry."},
            {"fr": "Vérifier que les matières proposées correspondent au sous-système de la classe.",
             "en": "Check the offered subjects match the class sub-system."},
        ]},
    ],
}


CH_EMPLOI = {
    "id": "emploi-du-temps",
    "num": "10",
    "title": {"fr": "Emploi du temps", "en": "Timetable"},
    "subtitle": {"fr": "Grille hebdomadaire par classe ; un enseignant ne peut pas être dans deux salles à la même heure.",
                 "en": "Weekly grid per class; a teacher cannot be in two rooms at the same time."},
    "who": {"fr": "Direction, censeur ; lecture pour les enseignants.",
            "en": "Management, dean of studies; read-only for teachers."},
    "blocks": [
        {"type": "p", "fr":
            "La grille couvre **six jours** (lundi → samedi) et **neuf créneaux** horaires, de 07:30 à 15:30. "
            "Chaque case porte la matière, l'enseignant et la salle, avec une couleur par matière.",
         "en":
            "The grid covers **six days** (Monday → Saturday) and **nine slots**, from 07:30 to 15:30. Each cell "
            "carries the subject, the teacher and the room, with one colour per subject."},
        {"type": "steps", "items": [
            {"fr": "Choisissez la **classe** : sa grille s'affiche. En lecture seule, l'affichage s'arrête là.",
             "en": "Pick the **class**: its grid appears. With read-only rights, that is all you get.",
             "img": "100-emploi-du-temps",
             "caption": {"fr": "Grille d'une classe ; les cases vides portent un « + » en mode édition.",
                         "en": "A class grid; empty cells show a “+” in edit mode."}},
            {"fr": "**Cliquez une case** — vide ou occupée — pour ouvrir l'éditeur de créneau sous la grille.",
             "en": "**Click a cell** — empty or filled — to open the slot editor under the grid.",
             "img": "101-emploi-du-temps-creneau",
             "caption": {"fr": "Éditeur : matière, enseignant et salle pour le jour et l'heure indiqués.",
                         "en": "Editor: subject, teacher and room for the given day and time."}},
            {"fr": "Renseignez la **matière** (liste filtrée sur le sous-système de la classe), l'**enseignant** et "
                   "la **salle** — le champ salle propose les salles déjà utilisées.",
             "en": "Fill in the **subject** (list filtered on the class sub-system), the **teacher** and the "
                   "**room** — the room field suggests rooms already in use."},
            {"fr": "__Enregistrer__. Sur un créneau existant, __Supprimer__ le libère.",
             "en": "__Save__. On an existing slot, __Delete__ frees it."},
        ]},
        {"type": "note", "tone": "warn", "fr":
            "**Un enseignant ne peut pas être dans deux salles à la même heure.** Si le professeur choisi assure "
            "déjà un cours sur ce créneau dans une autre classe, l'enregistrement est **refusé** : l'éditeur reste "
            "ouvert et affiche la classe, la matière et la salle qui l'occupent déjà. Corrigez l'enseignant ou "
            "l'heure — ou, si les deux classes sont réellement regroupées, cliquez __Forcer l'enregistrement__.",
         "en":
            "**A teacher cannot be in two rooms at the same time.** If the chosen teacher already has a class in "
            "that slot, the save is **refused**: the editor stays open and names the class, subject and room that "
            "already hold them. Change the teacher or the hour — or, if the two classes are genuinely merged, "
            "click __Force the save__."},
        {"type": "p", "fr":
            "Les chevauchements **déjà présents** dans la grille (import, saisie antérieure, enregistrement forcé) "
            "sont recalculés à chaque ouverture du module : un bandeau rouge en haut de page les liste tous — "
            "jour, heure, enseignant, puis les cours qui se chevauchent avec leur salle — et les cases concernées "
            "de la classe affichée sont cerclées de rouge avec un triangle d'alerte. Le bandeau disparaît de "
            "lui-même dès que le dernier chevauchement est résolu.",
         "en":
            "Clashes **already present** in the grid (import, earlier entry, forced save) are recomputed every time "
            "the module opens: a red banner at the top lists them all — day, hour, teacher, then the overlapping "
            "lessons with their rooms — and the affected cells of the displayed class are ringed in red with a "
            "warning triangle. The banner clears itself as soon as the last clash is resolved."},
        {"type": "check", "items": [
            {"fr": "Créer un créneau avec matière, enseignant et salle.",
             "en": "Create a slot with subject, teacher and room."},
            {"fr": "Tenter de placer un enseignant déjà occupé sur ce créneau et lire le refus.",
             "en": "Try to book a teacher who is already busy in that slot and read the refusal."},
            {"fr": "Forcer un regroupement de classes, puis retrouver le chevauchement dans le bandeau rouge.",
             "en": "Force a merged-class slot, then find the clash listed in the red banner."},
            {"fr": "Supprimer un créneau puis vérifier la case libérée.",
             "en": "Delete a slot then check the cell is free."},
        ]},
    ],
}

# -*- coding: utf-8 -*-
"""Chapitres 11 à 17 — finance, événements, correspondance, fournitures, parcours, santé, documents."""

CH_FINANCE = {
    "id": "finance",
    "num": "11",
    "title": {"fr": "Finance", "en": "Finance"},
    "subtitle": {
        "fr": "Encaissements et reçus, débiteurs, dépenses et grille des frais.",
        "en": "Payments and receipts, debtors, expenses and the fee grid.",
    },
    "who": {"fr": "Économe (écriture) ; direction en lecture. Les captures de ce chapitre sont prises avec un compte économe.",
            "en": "Bursar (write); management read-only. The screenshots in this chapter use a bursar account."},
    "blocks": [
        {"type": "p", "fr":
            "Quatre onglets : __Encaissements__, __Débiteurs__, __Dépenses__ et __Frais__. Chaque onglet charge "
            "ses données à la première visite, et le bouton __Exporter__ produit un CSV de l'onglet courant.",
         "en":
            "Four tabs: __Payments__, __Debtors__, __Expenses__ and __Fees__. Each tab loads its data on first "
            "visit, and __Export__ produces a CSV of the current tab."},
        {"type": "note", "tone": "info", "fr":
            "Un compte en **lecture seule** voit les mêmes chiffres, sans aucun bouton d'écriture : le bandeau "
            "« Lecture seule » le rappelle en haut de page.",
         "en":
            "A **read-only** account sees the same figures with no write buttons: the “Read-only” badge at the top "
            "of the page is a reminder."},

        {"type": "h", "fr": "11.1 Définir la grille des frais (à faire en premier)",
         "en": "11.1 Set the fee grid (do this first)"},
        {"type": "steps", "items": [
            {"fr": "Onglet __Frais__ → __Nouvelle grille__. Choisissez le **niveau** (Primaire ou Secondaire) et le "
                   "**sous-système** (Francophone, Anglophone, ou « Les deux »).",
             "en": "__Fees__ tab → __New grid__. Choose the **level** (Primary or Secondary) and the **sub-system** "
                   "(Francophone, English, or “Both”).",
             "img": "96-finance-grille-frais",
             "caption": {"fr": "Grille des frais : total annuel et découpage en tranches.",
                         "en": "Fee grid: annual total and split into installments."}},
            {"fr": "Saisissez le **total annuel**, puis les **tranches** (T1, T2, T3…). __Ajouter__ crée une "
                   "tranche supplémentaire, la croix en supprime une.",
             "en": "Enter the **annual total**, then the **installments** (T1, T2, T3…). __Add__ creates one more, "
                   "the cross removes one."},
            {"fr": "La somme des tranches doit **égaler** le total annuel : un rappel s'affiche sous les champs et "
                   "le serveur refuse un écart.",
             "en": "The installments must **add up** to the annual total: a reminder appears under the fields and "
                   "the server refuses a mismatch."},
        ]},
        {"type": "note", "tone": "warn", "fr":
            "Cette grille sert de référence à **tout le reste** : solde de chaque élève, liste des débiteurs, taux "
            "de recouvrement et blocage des bulletins. Renseignez-la avant le premier encaissement de l'année.",
         "en":
            "This grid is the reference for **everything else**: each student's balance, the debtor list, the "
            "recovery rate and report-card blocking. Set it up before the first payment of the year."},

        {"type": "h", "fr": "11.2 Encaisser un paiement", "en": "11.2 Record a payment"},
        {"type": "steps", "items": [
            {"fr": "Cliquez __Nouveau paiement__. Choisissez la **classe** puis l'**élève** : la seconde liste se "
                   "remplit après la première.",
             "en": "Click __New payment__. Choose the **class** then the **student**: the second list fills after "
                   "the first.",
             "img": "91-finance-nouveau-paiement",
             "caption": {"fr": "Fenêtre d'encaissement : élève, tranche, montant et méthode.",
                         "en": "Payment dialog: student, installment, amount and method."}},
            {"fr": "Sélectionnez la **tranche** (T1, T2, T3), saisissez le **montant** et choisissez la **méthode** : "
                   "Espèces, Mobile Money ou Virement.",
             "en": "Select the **installment** (T1, T2, T3), enter the **amount** and pick the **method**: Cash, "
                   "Mobile Money or Transfer."},
            {"fr": "__Générer le reçu__ enregistre le paiement et ouvre immédiatement le reçu numéroté.",
             "en": "__Generate receipt__ saves the payment and immediately opens the numbered receipt.",
             "img": "92-finance-recu",
             "caption": {"fr": "Reçu : numéro, date, tranche, montant, méthode et cachet de l'école.",
                         "en": "Receipt: number, date, installment, amount, method and school stamp."}},
            {"fr": "__Imprimer__ envoie le reçu à l'imprimante ou au PDF. Il reste consultable à tout moment via "
                   "l'icône reçu de l'historique.",
             "en": "__Print__ sends the receipt to the printer or to PDF. It stays available at any time from the "
                   "receipt icon in the history."},
        ]},
        {"type": "figure", "img": "90-finance-encaissements",
         "caption": {"fr": "Onglet Encaissements : indicateurs 30 jours, courbe des recettes et historique filtrable par méthode.",
                     "en": "Payments tab: 30-day indicators, revenue chart and history filterable by method."}},

        {"type": "h", "fr": "11.3 Suivre les débiteurs", "en": "11.3 Track debtors"},
        {"type": "steps", "items": [
            {"fr": "Onglet __Débiteurs__ : trois indicateurs — total impayé, montant déjà encaissé et **taux de "
                   "recouvrement** (encaissé ÷ attendu, sur toute l'école).",
             "en": "__Debtors__ tab: three indicators — total outstanding, amount already collected and **recovery "
                   "rate** (collected ÷ expected, school-wide).",
             "img": "93-finance-debiteurs",
             "caption": {"fr": "Liste des débiteurs, avec barre de progression et statut par élève.",
                         "en": "Debtor list, with a progress bar and a status per student."}},
            {"fr": "Filtrez par **classe** ou cherchez un élève par son nom. Chaque ligne indique l'attendu, le "
                   "payé, le solde et un statut : payé, partiel ou impayé.",
             "en": "Filter by **class** or search a student by name. Each row shows expected, paid, balance and a "
                   "status: paid, partial or unpaid."},
            {"fr": "__Exporter__ produit la liste de relance au format CSV.",
             "en": "__Export__ produces the follow-up list as CSV."},
        ]},

        {"type": "h", "fr": "11.4 Enregistrer une dépense", "en": "11.4 Record an expense"},
        {"type": "steps", "items": [
            {"fr": "Onglet __Dépenses__ → __Nouvelle dépense__ : date, **catégorie** (salaires, fournitures, "
                   "énergie, eau, maintenance, transport, cantine, internet, examens, sport, santé, divers), "
                   "libellé et montant.",
             "en": "__Expenses__ tab → __New expense__: date, **category** (salaries, supplies, energy, water, "
                   "maintenance, transport, canteen, internet, exams, sport, health, other), label and amount.",
             "img": "95-finance-depense-formulaire",
             "caption": {"fr": "Saisie d'une dépense — les trois champs sont obligatoires.",
                         "en": "Recording an expense — all three fields are required."}},
            {"fr": "Le journal affiche le total en pied de tableau et se filtre par catégorie. L'indicateur "
                   "__Poste principal__ met en évidence la catégorie la plus lourde.",
             "en": "The log shows the total in the footer and filters by category. The __Top category__ indicator "
                   "highlights the heaviest one.",
             "img": "94-finance-depenses",
             "caption": {"fr": "Journal des dépenses avec total et filtre par catégorie.",
                         "en": "Expense log with total and category filter."}},
            {"fr": "La corbeille supprime une dépense après confirmation ; les indicateurs 30 jours se recalculent "
                   "aussitôt.",
             "en": "The bin deletes an expense after confirmation; the 30-day indicators recompute at once."},
        ]},
        {"type": "check", "items": [
            {"fr": "Créer une grille de frais dont les tranches totalisent le montant annuel.",
             "en": "Create a fee grid whose installments add up to the annual total."},
            {"fr": "Encaisser un paiement et imprimer le reçu.",
             "en": "Record a payment and print the receipt."},
            {"fr": "Retrouver l'élève dans la liste des débiteurs et lire son solde.",
             "en": "Find the student in the debtor list and read their balance."},
            {"fr": "Enregistrer une dépense et vérifier l'effet sur le solde 30 jours.",
             "en": "Record an expense and check the effect on the 30-day balance."},
            {"fr": "Exporter la liste des débiteurs.", "en": "Export the debtor list."},
        ]},
    ],
}


CH_EVENEMENTS = {
    "id": "evenements",
    "num": "12",
    "title": {"fr": "Événements", "en": "Events"},
    "subtitle": {"fr": "Annonces de l'école et notification groupée des parents.",
                 "en": "School announcements and bulk parent notification."},
    "who": {"fr": "Direction, secrétariat.", "en": "Management, front office."},
    "blocks": [
        {"type": "figure", "img": "110-evenements-liste",
         "caption": {"fr": "Événements à venir et passés, avec l'état de notification de chacun.",
                     "en": "Upcoming and past events, each with its notification status."}},
        {"type": "steps", "items": [
            {"fr": "__Nouvel événement__ : **titre**, **type** (Réunion, Examen, Culturel, Annonce), **date** et "
                   "**description**.",
             "en": "__New event__: **title**, **type** (Meeting, Exam, Cultural, Announcement), **date** and "
                   "**description**.",
             "img": "111-evenements-formulaire",
             "caption": {"fr": "Formulaire : le public ciblé se choisit en bas de la fenêtre.",
                         "en": "Form: the target audience is chosen at the bottom of the dialog."}},
            {"fr": "Choisissez le **public** : __Toute l'école__, ou __Classes ciblées__ puis cochez les classes "
                   "concernées.",
             "en": "Choose the **audience**: __Whole school__, or __Selected classes__ then tick the classes concerned."},
            {"fr": "__Enregistrer__. L'événement rejoint la colonne « À venir », trié par date.",
             "en": "__Save__. The event joins the “Upcoming” column, sorted by date."},
            {"fr": "Cliquez __Notifier les parents__ sur la carte de l'événement. Le nombre de parents touchés "
                   "s'affiche à côté du badge « Notifié ».",
             "en": "Click __Notify parents__ on the event card. The number of parents reached is shown next to the "
                   "“Notified” badge."},
        ]},
        {"type": "list", "items": [
            {"fr": "Les quatre indicateurs du haut résument : événements à venir, notifiés, non notifiés et nombre "
                   "total de parents prévenus.",
             "en": "The four indicators at the top summarise: upcoming events, notified, not notified and total "
                   "parents reached."},
            {"fr": "Les événements passés restent consultables (cinq derniers), légèrement estompés.",
             "en": "Past events stay visible (last five), slightly dimmed."},
            {"fr": "Un événement ne peut être notifié **qu'une fois** : le bouton disparaît au profit du badge.",
             "en": "An event can only be notified **once**: the button gives way to the badge."},
        ]},
        {"type": "check", "items": [
            {"fr": "Créer un événement pour toute l'école et le notifier.",
             "en": "Create a whole-school event and notify it."},
            {"fr": "Créer un événement ciblé sur deux classes.",
             "en": "Create an event targeted at two classes."},
            {"fr": "Vérifier le compteur de parents notifiés.", "en": "Check the notified-parents counter."},
        ]},
    ],
}


CH_CORRESPONDANCE = {
    "id": "correspondance",
    "num": "13",
    "title": {"fr": "Carnet de correspondance", "en": "Correspondence book"},
    "subtitle": {"fr": "Notes individuelles école ↔ parents, avec accusé de lecture.",
                 "en": "Individual school ↔ parent notices, with read-acknowledgement."},
    "who": {"fr": "Professeurs principaux, vie scolaire, direction.",
            "en": "Form teachers, school life, management."},
    "blocks": [
        {"type": "p", "fr":
            "Là où le module Événements s'adresse à un groupe, la correspondance s'adresse à **un élève** : "
            "convocation, information, signalement d'absence, félicitations.",
         "en":
            "Where the Events module addresses a group, correspondence addresses **one student**: a summons, a "
            "notice, an absence report, congratulations."},
        {"type": "steps", "items": [
            {"fr": "Choisissez la **classe** dans le sélecteur de gauche, puis l'**élève**. L'historique de ses "
                   "notes s'affiche à droite, avec trois compteurs : notes, accusés en attente, notes signées.",
             "en": "Pick the **class** in the left selector, then the **student**. Their notice history appears on "
                   "the right, with three counters: notices, pending acknowledgements, signed notices.",
             "img": "120-correspondance",
             "caption": {"fr": "Correspondance d'un élève : chaque note porte sa catégorie et son état de lecture.",
                         "en": "One student's correspondence: each notice shows its category and read status."}},
            {"fr": "__Nouvelle note__ : choisissez la **catégorie** (Information, Convocation, Absence, Rappel, "
                   "Félicitations), l'**objet** et le **message**.",
             "en": "__New notice__: choose the **category** (Information, Summons, Absence, Reminder, Congrats), "
                   "the **subject** and the **message**.",
             "img": "121-correspondance-formulaire",
             "caption": {"fr": "Rédaction d'une note, avec la case « accusé de lecture requis ».",
                         "en": "Writing a notice, with the “read-acknowledgement required” box."}},
            {"fr": "Laissez cochée la case __Accusé de lecture requis__ pour les notes importantes, puis __Envoyer__.",
             "en": "Leave __Read-acknowledgement required__ ticked for important notices, then __Send__."},
            {"fr": "Quand le parent renvoie le carnet signé, cliquez __Marquer comme lu__ et saisissez le **nom du "
                   "parent signataire** : la note affiche alors « Lu / signé par … le … ».",
             "en": "When the parent returns the signed book, click __Mark as read__ and type the **signing parent's "
                   "name**: the notice then reads “Read / signed by … on …”."},
        ]},
        {"type": "note", "tone": "tip", "fr":
            "Le compteur __Accusés en attente__ donne en un coup d'œil les familles à relancer.",
         "en":
            "The __Pending acks__ counter shows at a glance which families need a reminder."},
        {"type": "check", "items": [
            {"fr": "Envoyer une convocation avec accusé de lecture.",
             "en": "Send a summons requiring acknowledgement."},
            {"fr": "Marquer une note comme lue au nom du parent.",
             "en": "Mark a notice as read on the parent's behalf."},
            {"fr": "Lire le compteur d'accusés en attente d'un élève.",
             "en": "Read a student's pending-acknowledgement counter."},
        ]},
    ],
}


CH_FOURNITURES = {
    "id": "fournitures",
    "num": "14",
    "title": {"fr": "Fournitures & manuels", "en": "Supplies & textbooks"},
    "subtitle": {"fr": "Listes de rentrée par classe, préparées puis publiées aux parents.",
                 "en": "Back-to-school lists per class, prepared then published to parents."},
    "who": {"fr": "Direction, professeurs principaux, économat.",
            "en": "Management, form teachers, bursary."},
    "blocks": [
        {"type": "p", "fr":
            "Deux listes distinctes par classe : les **fournitures** (cahiers, matériel) et les **manuels "
            "scolaires** (avec prix). Chaque liste a son propre état brouillon / publié.",
         "en":
            "Two separate lists per class: **supplies** (exercise books, equipment) and **textbooks** (with prices). "
            "Each list has its own draft / published state."},
        {"type": "steps", "items": [
            {"fr": "Choisissez l'onglet __Fournitures__ ou __Manuels scolaires__, puis la **classe**.",
             "en": "Choose the __Supplies__ or __School textbooks__ tab, then the **class**.",
             "img": "130-fournitures",
             "caption": {"fr": "Liste de fournitures : libellé, quantité et note par article.",
                         "en": "Supply list: label, quantity and note per item."}},
            {"fr": "Ajoutez les articles. Pour une **fourniture** : libellé, quantité, note. Pour un **manuel** : "
                   "titre, prix, matière, auteur / édition et case « manuel obligatoire ».",
             "en": "Add the items. For a **supply**: label, quantity, note. For a **textbook**: title, price, "
                   "subject, author / edition and the “mandatory textbook” box.",
             "img": "131-manuels",
             "caption": {"fr": "Liste des manuels : le total du coût est calculé automatiquement.",
                         "en": "Textbook list: the total cost is computed automatically."}},
            {"fr": "Le bandeau du haut indique l'état. Cliquez __Publier__ : la liste devient visible dans le "
                   "portail parent. __Dépublier__ la masque à nouveau.",
             "en": "The top banner shows the state. Click __Publish__: the list becomes visible in the parent "
                   "portal. __Unpublish__ hides it again."},
        ]},
        {"type": "note", "tone": "warn", "fr":
            "Tant qu'une liste est en **brouillon**, les parents ne la voient pas. Vérifiez prix et quantités avant "
            "de publier : la publication est immédiate.",
         "en":
            "While a list is a **draft**, parents cannot see it. Check prices and quantities before publishing: "
            "publication is immediate."},
        {"type": "check", "items": [
            {"fr": "Constituer la liste de fournitures d'une classe et la publier.",
             "en": "Build a class supply list and publish it."},
            {"fr": "Ajouter trois manuels avec prix et vérifier le total.",
             "en": "Add three textbooks with prices and check the total."},
            {"fr": "Vérifier la liste dans le portail parent, puis la dépublier.",
             "en": "Check the list in the parent portal, then unpublish it."},
        ]},
    ],
}


CH_PARCOURS_SCOLAIRE = {
    "id": "parcours-scolaire",
    "num": "15",
    "title": {"fr": "Parcours scolaire", "en": "School journey"},
    "subtitle": {"fr": "Historique pluriannuel d'un élève : classes, moyennes, décisions.",
                 "en": "A student's multi-year history: classes, averages, decisions."},
    "who": {"fr": "Direction, professeurs principaux, orientation.",
            "en": "Management, form teachers, guidance."},
    "blocks": [
        {"type": "steps", "items": [
            {"fr": "Choisissez la classe puis l'élève. Trois indicateurs résument son parcours : années suivies, "
                   "meilleure moyenne et nombre de redoublements.",
             "en": "Pick the class then the student. Three indicators summarise the journey: years tracked, best "
                   "average and number of repeats.",
             "img": "140-parcours-scolaire",
             "caption": {"fr": "Parcours d'un élève : indicateurs, courbe de progression et chronologie.",
                         "en": "A student's journey: indicators, progression chart and timeline."}},
            {"fr": "__Ajouter une année__ : année scolaire (ex. 2024-2025), classe, **résultat** (En cours, Admis, "
                   "Redoublé, Arrivée, Départ, Diplômé, Exclu), moyenne générale, rang, effectif et décision du "
                   "conseil de classe.",
             "en": "__Add a year__: school year (e.g. 2024-2025), class, **result** (In progress, Promoted, "
                   "Repeated, Transferred in, Transferred out, Graduated, Excluded), general average, rank, class "
                   "size and the class council decision."},
            {"fr": "Dès qu'au moins deux années portent une moyenne, une **courbe de progression** s'affiche "
                   "au-dessus de la chronologie.",
             "en": "As soon as at least two years carry an average, a **progression chart** appears above the timeline."},
            {"fr": "Chaque entrée de la chronologie se modifie (crayon) ou se supprime (croix). La pastille de "
                   "couleur reprend le résultat de l'année.",
             "en": "Each timeline entry can be edited (pencil) or deleted (cross). The coloured dot reflects the "
                   "year's result."},
        ]},
        {"type": "check", "items": [
            {"fr": "Ajouter une année antérieure avec moyenne et rang.",
             "en": "Add a previous year with average and rank."},
            {"fr": "Vérifier la courbe de progression sur deux années.",
             "en": "Check the progression chart over two years."},
        ]},
    ],
}


CH_SANTE = {
    "id": "sante",
    "num": "16",
    "title": {"fr": "Santé & vie scolaire", "en": "Health & school life"},
    "subtitle": {"fr": "Dossier médical, passages à l'infirmerie et activités extrascolaires.",
                 "en": "Medical record, infirmary visits and extracurricular activities."},
    "who": {"fr": "Infirmerie, vie scolaire, direction — données sensibles, accès à restreindre.",
            "en": "Infirmary, school life, management — sensitive data, restrict access."},
    "blocks": [
        {"type": "steps", "items": [
            {"fr": "Choisissez la classe puis l'élève. L'en-tête rappelle le groupe sanguin, le nombre de passages "
                   "à l'infirmerie et le nombre d'activités.",
             "en": "Pick the class then the student. The header recalls the blood group, the number of infirmary "
                   "visits and the number of activities.",
             "img": "141-sante",
             "caption": {"fr": "Dossier médical, passages à l'infirmerie et activités sur une seule page.",
                         "en": "Medical record, infirmary visits and activities on a single page."}},
            {"fr": "**Dossier médical** : groupe sanguin, taille, poids, allergies, pathologies, vaccinations, "
                   "médecin traitant et son téléphone. __Enregistrer le dossier__ valide l'ensemble.",
             "en": "**Medical record**: blood group, height, weight, allergies, conditions, vaccinations, attending "
                   "doctor and phone. __Save record__ commits the whole block."},
            {"fr": "**Passages à l'infirmerie** : date, motif et soins prodigués, puis __Ajouter__. Chaque passage "
                   "reste horodaté dans la liste.",
             "en": "**Infirmary visits**: date, reason and treatment given, then __Add__. Each visit stays "
                   "time-stamped in the list."},
            {"fr": "**Activités extrascolaires** : nom, catégorie (Club, Sport, Art, Autre), rôle et saison.",
             "en": "**Extracurricular activities**: name, category (Club, Sport, Art, Other), role and season."},
        ]},
        {"type": "note", "tone": "warn", "fr":
            "Les données de santé sont confidentielles. Réservez le droit d'écriture à l'infirmerie et n'accordez "
            "la lecture qu'aux personnes qui en ont besoin (matrice des permissions, chapitre 2).",
         "en":
            "Health data is confidential. Restrict write access to the infirmary and grant read access only to "
            "those who need it (permission matrix, chapter 2)."},
        {"type": "check", "items": [
            {"fr": "Compléter le dossier médical d'un élève et l'enregistrer.",
             "en": "Complete a student's medical record and save it."},
            {"fr": "Ajouter un passage à l'infirmerie.", "en": "Add an infirmary visit."},
            {"fr": "Inscrire l'élève à un club.", "en": "Enrol the student in a club."},
        ]},
    ],
}


CH_DOCUMENTS = {
    "id": "documents",
    "num": "17",
    "title": {"fr": "Documents & orientation", "en": "Documents & guidance"},
    "subtitle": {"fr": "Pièces du dossier administratif et décisions d'orientation.",
                 "en": "Administrative file items and guidance decisions."},
    "who": {"fr": "Secrétariat, direction, orientation.", "en": "Front office, management, guidance."},
    "blocks": [
        {"type": "steps", "items": [
            {"fr": "Choisissez la classe puis l'élève : deux compteurs indiquent le nombre de pièces et de "
                   "décisions d'orientation.",
             "en": "Pick the class then the student: two counters show the number of items and guidance decisions.",
             "img": "142-documents",
             "caption": {"fr": "Registre des pièces et décisions d'orientation d'un élève.",
                         "en": "A student's document register and guidance decisions."}},
            {"fr": "__Ajouter un document__ : **type** (acte de naissance, bulletin, certificat…), **titre**, "
                   "**référence ou URL** et une note. Si la référence est une adresse web, elle devient un lien "
                   "cliquable.",
             "en": "__Add a document__: **kind** (birth certificate, report card, certificate…), **title**, "
                   "**reference or URL** and a note. If the reference is a web address it becomes a clickable link."},
            {"fr": "__Ajouter une décision__ d'orientation : année scolaire, étape (« Orientation 3ème »), "
                   "recommandation, décision et date du conseil.",
             "en": "__Add a decision__: school year, stage (“Form 4 guidance”), recommendation, decision and council date."},
        ]},
        {"type": "note", "tone": "limit", "fr":
            "Le module tient un **registre** des pièces : il enregistre leur existence, leur type et une référence "
            "(numéro de classeur ou lien). Le téléversement de fichiers dans l'application n'est pas encore "
            "disponible — utilisez un lien vers votre espace de stockage.",
         "en":
            "The module keeps a **register** of items: it records their existence, kind and a reference (folder "
            "number or link). Uploading files into the application is not available yet — use a link to your "
            "storage space instead."},
        {"type": "check", "items": [
            {"fr": "Enregistrer deux pièces au dossier d'un élève.",
             "en": "Register two items in a student's file."},
            {"fr": "Ajouter une décision d'orientation datée.",
             "en": "Add a dated guidance decision."},
        ]},
    ],
}

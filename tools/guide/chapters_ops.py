# -*- coding: utf-8 -*-
"""Chapitres 11 à 18 — finance, événements, correspondance, fournitures, parcours,
passage de classe, santé, documents."""

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
            "Cinq onglets : __Encaissements__, __Débiteurs__, __Dépenses__, __Frais__ et __Moyens de paiement__. "
            "Chaque onglet charge ses données à la première visite, et le bouton __Exporter__ produit un CSV de "
            "l'onglet courant.",
         "en":
            "Five tabs: __Payments__, __Debtors__, __Expenses__, __Fees__ and __Payment methods__. Each tab loads "
            "its data on first visit, and __Export__ produces a CSV of the current tab."},
        {"type": "note", "tone": "info", "fr":
            "Un compte en **lecture seule** voit les mêmes chiffres, sans aucun bouton d'écriture : le bandeau "
            "« Lecture seule » le rappelle en haut de page.",
         "en":
            "A **read-only** account sees the same figures with no write buttons: the “Read-only” badge at the top "
            "of the page is a reminder."},

        {"type": "h", "fr": "11.1 Configurer les moyens de paiement",
         "en": "11.1 Configure the payment methods"},
        {"type": "p", "fr":
            "L'école encaisse par **espèces**, **Orange Money**, **MTN Mobile Money**, **SARA**, "
            "**carte bancaire (MPGS)** et **virement**. Chaque canal porte les coordonnées que les familles "
            "utiliseront pour payer : c'est cette configuration qui rend le paiement progressif possible depuis "
            "la maison.",
         "en":
            "The school collects by **cash**, **Orange Money**, **MTN Mobile Money**, **SARA**, "
            "**bank card (MPGS)** and **transfer**. Each channel carries the details families will use to pay: "
            "this configuration is what makes progressive payment from home possible."},
        {"type": "steps", "items": [
            {"fr": "Ouvrez l'onglet __Moyens de paiement__. Les six canaux sont livrés préconfigurés ; trois "
                   "interrupteurs les pilotent.",
             "en": "Open the __Payment methods__ tab. The six channels ship preconfigured; three switches drive them.",
             "img": "98-finance-moyens-paiement",
             "caption": {"fr": "Chaque canal : actif, visible des parents, référence obligatoire, et ses coordonnées.",
                         "en": "Each channel: enabled, shown to parents, reference required, and its account details."}},
            {"fr": "__Actif__ autorise l'encaissement par ce canal. __Visible des parents__ le publie dans le "
                   "portail parent avec ses coordonnées. __Référence obligatoire__ impose la saisie de l'identifiant "
                   "de transaction : laissez-la cochée pour OM, MoMo, SARA, MPGS et virement — c'est la preuve "
                   "du versement.",
             "en": "__Enabled__ allows collection through this channel. __Shown to parents__ publishes it in the "
                   "parent portal with its details. __Reference required__ forces the transaction ID to be entered: "
                   "keep it ticked for OM, MoMo, SARA, MPGS and transfers — it is the proof of payment."},
            {"fr": "Cliquez __Coordonnées__ pour saisir le **numéro à créditer** (Orange Money, MoMo, SARA), "
                   "l'**identifiant marchand** (MPGS) ou le **RIB** (virement), l'intitulé du compte et les "
                   "instructions affichées au parent, en français et en anglais.",
             "en": "Click __Details__ to enter the **number to credit** (Orange Money, MoMo, SARA), the **merchant ID** "
                   "(MPGS) or the **bank account** (transfer), the account name and the instructions shown to the "
                   "parent, in French and English.",
             "img": "99-finance-canal-coordonnees",
             "caption": {"fr": "Coordonnées et instructions : elles s'affichent telles quelles dans le portail parent.",
                         "en": "Details and instructions: they appear as-is in the parent portal."}},
            {"fr": "__Enregistrer__. Le parent voit immédiatement le canal dans son espace, onglet "
                   "__Frais & paiements__.",
             "en": "__Save__. The parent immediately sees the channel in their space, __Fees & payments__ tab."},
        ]},
        {"type": "note", "tone": "warn", "fr":
            "Ces canaux servent à **enregistrer et tracer** un versement, pas à le déclencher : le parent paie "
            "depuis son téléphone ou à la banque, puis transmet la référence à l'économat qui saisit "
            "l'encaissement. Aucun débit n'est initié par l'application.",
         "en":
            "These channels **record and trace** a payment, they do not trigger one: the parent pays from their "
            "phone or at the bank, then passes the reference to the bursary, who records the payment. The "
            "application never initiates a debit."},

        {"type": "h", "fr": "11.2 Définir la grille des frais (par niveau ou par classe)",
         "en": "11.2 Set the fee grid (per level or per class)"},
        {"type": "p", "fr":
            "Une grille décrit ce qu'un élève doit sur l'année et **comment ce montant se découpe en tranches**. "
            "Elle se définit à deux niveaux : une grille par **niveau** (le cas général) et, si les frais diffèrent, "
            "une **surcharge par classe** qui prime pour les élèves de cette classe.",
         "en":
            "A grid describes what a student owes for the year and **how that amount splits into installments**. "
            "It is defined at two levels: a grid per **level** (the general case) and, when fees differ, a "
            "**per-class override** that wins for the students of that class."},
        {"type": "steps", "items": [
            {"fr": "Onglet __Frais__ → __Nouvelle grille__. Le tableau distingue les grilles de niveau des "
                   "surcharges de classe, repérées par une pastille « classe ».",
             "en": "__Fees__ tab → __New grid__. The table separates level grids from class overrides, flagged with "
                   "a “class” chip.",
             "img": "96-finance-grille-frais",
             "caption": {"fr": "Grilles de l'établissement : par niveau, et la surcharge de la classe 4ème.",
                         "en": "The school's grids: per level, plus the override for class 4ème."}},
            {"fr": "Choisissez la portée : __Grille du niveau__ (niveau + sous-système) ou __Surcharge par "
                   "classe__, puis la classe concernée. Le niveau et le sous-système suivent alors automatiquement "
                   "la classe.",
             "en": "Choose the scope: __Level grid__ (level + sub-system) or __Per-class override__, then the class. "
                   "Level and sub-system then follow the class automatically.",
             "img": "97-finance-grille-classe",
             "caption": {"fr": "Surcharge de classe : total annuel et tranches nommées avec leur échéance.",
                         "en": "Class override: annual total and named installments with their due dates."}},
            {"fr": "Saisissez le **total annuel**, puis les **tranches** : un **libellé** (« Inscription », "
                   "« Tranche 2 »…), un **montant** et une **échéance**. __Ajouter une tranche__ en crée une de "
                   "plus, la croix en retire une. Il peut y en avoir autant que nécessaire.",
             "en": "Enter the **annual total**, then the **installments**: a **label** (“Registration”, "
                   "“Installment 2”…), an **amount** and a **due date**. __Add an installment__ creates one more, "
                   "the cross removes one. There can be as many as needed."},
            {"fr": "La somme des tranches doit **égaler** le total annuel : un rappel s'affiche sous les champs et "
                   "le serveur refuse un écart.",
             "en": "The installments must **add up** to the annual total: a reminder appears under the fields and "
                   "the server refuses a mismatch."},
            {"fr": "__Enregistrer__. Les soldes de tous les élèves concernés sont recalculés immédiatement, et la "
                   "corbeille d'une surcharge fait retomber la classe sur la grille de son niveau.",
             "en": "__Save__. The balances of all affected students are recomputed at once, and deleting an "
                   "override drops the class back to its level grid."},
        ]},
        {"type": "note", "tone": "info", "fr":
            "L'échéance sert au parent : une tranche non réglée après sa date apparaît **en retard** dans son "
            "espace, en rouge. Laissez le champ vide si l'école ne fixe pas de date.",
         "en":
            "The due date serves the parent: an unpaid installment past its date shows as **overdue** in their "
            "space, in red. Leave the field empty if the school sets no deadline."},
        {"type": "note", "tone": "warn", "fr":
            "Cette grille sert de référence à **tout le reste** : solde de chaque élève, liste des débiteurs, taux "
            "de recouvrement et blocage des bulletins. Renseignez-la avant le premier encaissement de l'année.",
         "en":
            "This grid is the reference for **everything else**: each student's balance, the debtor list, the "
            "recovery rate and report-card blocking. Set it up before the first payment of the year."},

        {"type": "h", "fr": "11.3 Encaisser un paiement", "en": "11.3 Record a payment"},
        {"type": "steps", "items": [
            {"fr": "Cliquez __Nouveau paiement__. Choisissez la **classe** puis l'**élève** : la seconde liste se "
                   "remplit après la première.",
             "en": "Click __New payment__. Choose the **class** then the **student**: the second list fills after "
                   "the first.",
             "img": "91-finance-nouveau-paiement",
             "caption": {"fr": "La situation de l'élève s'affiche : grille appliquée, reste à payer et tranches.",
                         "en": "The student's position appears: grid in force, outstanding balance and installments."}},
            {"fr": "La **situation de l'élève** apparaît aussitôt : grille appliquée (classe ou niveau), reste à "
                   "payer et **état de chaque tranche** — verte si réglée, rouge si en retard. La première tranche "
                   "non soldée est présélectionnée et le montant restant pré-rempli ; cliquez une autre tranche "
                   "pour changer.",
             "en": "The **student's position** appears immediately: grid in force (class or level), outstanding "
                   "balance and the **state of each installment** — green when settled, red when overdue. The first "
                   "unsettled installment is preselected and its remaining amount pre-filled; click another one to "
                   "change."},
            {"fr": "Ajustez le **montant** si le parent verse une somme partielle, et la **date** si l'encaissement "
                   "est antérieur.",
             "en": "Adjust the **amount** if the parent pays part of it, and the **date** if the payment is backdated."},
            {"fr": "Choisissez le **moyen de paiement** parmi les canaux actifs (§11.1). Pour Orange Money, MTN "
                   "MoMo, MPGS ou un virement, saisissez la **référence de transaction** communiquée par le "
                   "parent : sans elle, l'enregistrement est refusé. Le numéro du compte de l'école est rappelé "
                   "sous le champ.",
             "en": "Pick the **payment method** among the active channels (§11.1). For Orange Money, MTN MoMo, MPGS "
                   "or a transfer, enter the **transaction reference** provided by the parent: without it the "
                   "record is refused. The school account number is recalled under the field."},
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
         "caption": {"fr": "Onglet Encaissements : indicateurs 30 jours, courbe des recettes et historique — chaque ligne porte son canal et sa référence.",
                     "en": "Payments tab: 30-day indicators, revenue chart and history — each row carries its channel and reference."}},
        {"type": "note", "tone": "tip", "fr":
            "La référence saisie est reprise dans l'historique, dans l'export CSV et dans l'espace du parent : "
            "en cas de contestation, elle permet de retrouver la transaction chez l'opérateur.",
         "en":
            "The reference is repeated in the history, in the CSV export and in the parent's space: in case of a "
            "dispute it is what lets you trace the transaction with the operator."},

        {"type": "h", "fr": "11.4 Suivre les débiteurs", "en": "11.4 Track debtors"},
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

        {"type": "h", "fr": "11.5 Enregistrer une dépense", "en": "11.5 Record an expense"},
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
            {"fr": "Renseigner le numéro Orange Money et MTN MoMo de l'école, puis les rendre visibles des parents.",
             "en": "Fill in the school's Orange Money and MTN MoMo numbers, then make them visible to parents."},
            {"fr": "Créer une grille de niveau dont les tranches totalisent le montant annuel.",
             "en": "Create a level grid whose installments add up to the annual total."},
            {"fr": "Créer une surcharge pour une classe avec quatre tranches datées, et vérifier qu'un élève de "
                   "cette classe la suit.",
             "en": "Create an override for one class with four dated installments, and check that a student of that "
                   "class follows it."},
            {"fr": "Encaisser une tranche par Orange Money avec sa référence, et constater le refus si la référence "
                   "manque.",
             "en": "Record an installment via Orange Money with its reference, and observe the refusal when the "
                   "reference is missing."},
            {"fr": "Imprimer le reçu correspondant.", "en": "Print the matching receipt."},
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


CH_PASSAGE_DE_CLASSE = {
    "id": "passage-de-classe",
    "num": "16",
    "title": {"fr": "Passage de classe", "en": "Class promotion"},
    "subtitle": {"fr": "Fin d'année : proposition automatique, arbitrage du conseil, transfert réel des élèves.",
                 "en": "End of year: automatic proposal, council override, actual transfer of students."},
    "who": {"fr": "Direction et censeur pour l'exécution ; administrateur pour la configuration des règles.",
            "en": "Management and dean to run it; administrator to configure the rules."},
    "blocks": [
        {"type": "p", "fr":
            "Le module enchaîne trois opérations que l'école faisait jusque-là à la main : il **propose** une "
            "décision à partir de la moyenne annuelle, il laisse le conseil de classe **l'arbitrer**, puis il "
            "**applique** — l'élève change réellement de classe et son parcours s'écrit tout seul.",
         "en":
            "The module chains the three operations schools used to do by hand: it **proposes** a decision from the "
            "annual average, lets the class council **override** it, then **applies** it — the student really moves "
            "class and their journey is written automatically."},

        {"type": "h", "fr": "16.1 Configurer la progression", "en": "16.1 Configure the progression"},
        {"type": "steps", "items": [
            {"fr": "Ouvrez **Passage de classe → Progression & règles**. Le tableau liste les classes du parcours "
                   "actif, section par section.",
             "en": "Open **Class promotion → Progression & rules**. The table lists the classes of the active "
                   "parcours, section by section."},
            {"fr": "__Déduire automatiquement__ reconnaît les libellés officiels et enchaîne les classes : "
                   "SIL → CP → CE1 → … → CM2 → 6ème, Form 1 → Form 2, etc. La dernière classe d'un cycle bascule "
                   "sur la première du cycle suivant, dans le même sous-système.",
             "en": "__Auto-detect__ recognises the official labels and chains the classes: SIL → CP → CE1 → … → "
                   "CM2 → 6ème, Form 1 → Form 2, etc. The last class of a cycle moves on to the first of the next "
                   "cycle, within the same subsystem."},
            {"fr": "Relisez et corrigez : la colonne **Classe suivante** se change librement, l'**Ordre** fixe le "
                   "rang pédagogique, et **Classe de sortie** marque une fin de scolarité (Terminale, Upper Sixth) "
                   "où la réussite vaut « Diplômé » au lieu de « Admis ». Terminez par __Enregistrer__.",
             "en": "Review and correct: the **Next class** column is freely editable, **Order** sets the "
                   "pedagogical rank, and **Exit class** marks the end of schooling (Terminale, Upper Sixth) where "
                   "passing means “Graduated” instead of “Promoted”. Finish with __Save__."},
        ]},
        {"type": "note", "tone": "warn", "fr":
            "Une classe sans classe suivante et non marquée classe de sortie ne bloque rien, mais ses élèves seront "
            "tous proposés **à examiner** : l'application refuse de deviner où les envoyer.",
         "en":
            "A class with no next class and not marked as an exit class blocks nothing, but all its students will be "
            "proposed **to review**: the app refuses to guess where to send them."},

        {"type": "h", "fr": "16.2 Régler le seuil de décision", "en": "16.2 Set the decision threshold"},
        {"type": "p", "fr":
            "Sous le tableau de progression, les **règles de décision** disent à partir de quelle moyenne annuelle "
            "un élève est proposé admis. La règle la plus précise l'emporte : une règle de classe prime sur une "
            "règle de parcours, qui prime sur la règle générale de l'école.",
         "en":
            "Below the progression table, the **decision rules** state the annual average from which a student is "
            "proposed for promotion. The most specific rule wins: a class rule beats a parcours rule, which beats "
            "the school-wide rule."},
        {"type": "table",
         "head": {"fr": ["Réglage", "Effet"], "en": ["Setting", "Effect"]},
         "rows": {
            "fr": [
                ["Seuil", "Moyenne annuelle à atteindre pour être proposé **admis** (10/20 par défaut)."],
                ["Zone conseil", "Largeur, juste sous le seuil, où l'élève est proposé **à examiner** au lieu de "
                                 "redoublant — la décision revient explicitement au conseil."],
                ["Redoubl. max", "Nombre de redoublements déjà au parcours au-delà duquel plus aucun redoublement "
                                 "n'est proposé automatiquement (2 par défaut)."],
            ],
            "en": [
                ["Pass mark", "Annual average to reach to be proposed **promoted** (10/20 by default)."],
                ["Council zone", "Band just below the pass mark where the student is proposed **to review** instead "
                                 "of repeating — the decision explicitly goes back to the council."],
                ["Max repeats", "Number of repeats already on the journey beyond which no further repeat is "
                                "proposed automatically (2 by default)."],
            ],
         }},
        {"type": "note", "tone": "info", "fr":
            "La moyenne annuelle est la moyenne des séquences évaluées, chaque séquence étant pondérée par les "
            "**mêmes coefficients que les bulletins**, surcharge par classe comprise. La note qui décide du passage "
            "est donc exactement celle que l'élève lit sur son bulletin.",
         "en":
            "The annual average is the mean of the evaluated sequences, each weighted with the **same coefficients "
            "as the report cards**, per-class overrides included. The mark that decides promotion is therefore "
            "exactly the one the student reads on their report card."},

        {"type": "h", "fr": "16.3 Simuler puis appliquer", "en": "16.3 Simulate then apply"},
        {"type": "steps", "items": [
            {"fr": "Onglet **Passage de classe** : choisissez la classe. L'année qui se termine et l'année "
                   "d'accueil sont pré-remplies et restent modifiables.",
             "en": "**Promotion** tab: pick the class. The year ending and the receiving year are pre-filled and "
                   "remain editable."},
            {"fr": "Chaque élève affiche sa **moyenne annuelle**, son **rang**, ses redoublements antérieurs, et la "
                   "**proposition** de l'application avec sa justification en clair — « Moyenne 11,40/20 < seuil "
                   "12,00 », « en zone conseil », « 2 redoublements déjà au parcours ».",
             "en": "Each student shows their **annual average**, **rank**, previous repeats, and the app's "
                   "**proposal** with its justification in plain words — “Average 11.40/20 < pass mark 12.00”, "
                   "“in the council zone”, “2 repeats already on the journey”."},
            {"fr": "La colonne **Décision retenue** est libre : vous pouvez admettre un élève sous le seuil ou "
                   "faire redoubler un élève au-dessus. Dès que la décision s'écarte de la proposition, la ligne "
                   "passe en ambre et le **motif devient obligatoire** — il est conservé et consultable.",
             "en": "The **Final decision** column is free: you may promote a student below the pass mark or hold "
                   "back one above it. As soon as the decision differs from the proposal, the row turns amber and "
                   "the **reason becomes mandatory** — it is stored and auditable."},
            {"fr": "La **classe d'accueil** est celle du mapping ; changez-la au cas par cas pour une orientation "
                   "(3ème vers 2nde A ou 2nde C, par exemple).",
             "en": "The **receiving class** comes from the mapping; change it case by case for guidance decisions "
                   "(3ème to 2nde A or 2nde C, for instance)."},
            {"fr": "__Appliquer__ demande confirmation puis exécute d'un bloc : les admis changent de classe, les "
                   "redoublants sont marqués « redouble », les diplômés et les exclus sortent des effectifs "
                   "actifs. L'année qui se termine et l'année d'accueil s'inscrivent dans le **parcours scolaire** "
                   "de chaque élève (chapitre 15).",
             "en": "__Apply__ asks for confirmation then runs as one block: promoted students change class, "
                   "repeaters are flagged, graduated and excluded students leave the active roster. The closing "
                   "year and the receiving year are written into each student's **school journey** (chapter 15)."},
        ]},
        {"type": "note", "tone": "info", "fr":
            "Un élève sans aucune note est proposé **sans note** et n'est jamais appliqué tant qu'une décision n'a "
            "pas été choisie à la main. Le compteur __Appliquer (n)__ ne compte que les lignes réellement prêtes : "
            "décidées, motivées si nécessaire, et pourvues d'une classe d'accueil.",
         "en":
            "A student with no grade at all is proposed as **no grades** and is never applied until a decision is "
            "picked by hand. The __Apply (n)__ counter only counts genuinely ready rows: decided, justified where "
            "needed, and with a receiving class."},
        {"type": "note", "tone": "tip", "fr":
            "Traitez les classes **de la plus basse à la plus haute** ou dans l'ordre que vous voulez : peu importe. "
            "Un élève qui vient d'être admis en 6ème depuis le CM2 est automatiquement écarté de la simulation de la "
            "6ème — sa décision de l'année est déjà prise, le promouvoir une seconde fois lui ferait sauter une "
            "classe. Un bandeau signale combien d'élèves ont été écartés à ce titre.",
         "en":
            "Process the classes **from the lowest to the highest**, or in any order you like: it does not matter. "
            "A student just promoted into 6ème from CM2 is automatically excluded from the 6ème simulation — their "
            "decision for the year is already made, and promoting them twice would make them skip a class. A banner "
            "reports how many students were excluded on that basis."},
        {"type": "note", "tone": "warn", "fr":
            "Le passage déplace les élèves ; il ne remet pas les compteurs à zéro. C'est la **clôture de l'année** "
            "(§16.5) qui archive les notes et rouvre les scolarités — faites-la après avoir passé toutes les classes.",
         "en":
            "Promotion moves students; it does not reset the counters. It is the **year closure** (§16.5) that "
            "archives the grades and reopens the fees — run it once every class has been processed."},

        {"type": "h", "fr": "16.4 Contrôler ce qui a été fait", "en": "16.4 Audit what was done"},
        {"type": "p", "fr":
            "L'onglet **Historique** liste les 50 derniers lots : la classe, les deux années, les effectifs admis / "
            "redoublants / diplômés, le nombre d'arbitrages manuels et l'auteur. Chaque décision conserve la "
            "proposition d'origine à côté de la décision retenue et de son motif.",
         "en":
            "The **History** tab lists the last 50 batches: the class, both years, the promoted / repeating / "
            "graduated headcounts, the number of manual overrides and who ran it. Each decision keeps the original "
            "proposal alongside the final decision and its reason."},
        {"type": "note", "tone": "tip", "fr":
            "Réappliquer un lot sur la même classe est possible : les élèves déjà traités sont signalés, et les "
            "élèves qui ont quitté la classe sont simplement ignorés. C'est le moyen de rattraper une classe "
            "traitée à moitié en fin de conseil.",
         "en":
            "Re-applying a batch on the same class is possible: already-processed students are flagged, and "
            "students who left the class are simply skipped. That is how you finish a class left half-done at the "
            "end of a council."},

        {"type": "h", "fr": "16.5 Clôturer l'année", "en": "16.5 Close the year"},
        {"type": "p", "fr":
            "Une fois toutes les classes passées, la clôture remet l'établissement à zéro pour la rentrée. Trois "
            "ensembles de données n'ont aucune notion d'année et se mélangeraient sans elle : les **notes**, les "
            "**bulletins validés** et les **scolarités**. La clôture les archive sous le libellé de l'année écoulée, "
            "vide les tables de travail, rouvre une scolarité vierge au tarif de la nouvelle classe, puis bascule "
            "l'année courante de l'établissement.",
         "en":
            "Once every class has been processed, the closure resets the school for the new intake. Three data sets "
            "carry no notion of year and would otherwise mix: the **grades**, the **validated report cards** and the "
            "**school fees**. The closure archives them under the closing year's label, empties the working tables, "
            "reopens a blank fee statement at the new class rate, then switches the school's current year."},
        {"type": "steps", "items": [
            {"fr": "Onglet **Clôture de l'année**. L'écran annonce ce qui sera archivé — nombre de notes, de "
                   "bulletins validés, de scolarités — et surtout **combien d'élèves n'ont pas encore de décision** "
                   "de fin d'année, classe par classe.",
             "en": "**Year closure** tab. The screen states what will be archived — number of grades, validated "
                   "reports, fee statements — and above all **how many students have no end-of-year decision yet**, "
                   "class by class."},
            {"fr": "Tant qu'il reste des élèves sans décision, la clôture est **refusée** : archiver leurs notes "
                   "avant de les avoir fait passer reviendrait à jeter la base même du calcul. Retournez d'abord "
                   "dans l'onglet Passage de classe. La case __Clôturer malgré tout__ n'apparaît que pour les cas "
                   "assumés (élèves partis en cours d'année, par exemple).",
             "en": "As long as students have no decision, the closure is **refused**: archiving their grades before "
                   "promoting them would throw away the very basis of the calculation. Go back to the Promotion tab "
                   "first. The __Close anyway__ box only shows for deliberate cases (students who left mid-year, "
                   "for instance)."},
            {"fr": "Trois interrupteurs pilotent ce qui est fait : __Archiver les notes__, __Rouvrir les "
                   "scolarités__, __Basculer l'année courante__. Les décocher est possible mais l'écran prévient de "
                   "la conséquence — des notes qui se mélangent, ou des soldes de l'an dernier toujours dus.",
             "en": "Three switches drive what is done: __Archive the grades__, __Reopen the school fees__, __Switch "
                   "the current year__. Unticking them is allowed but the screen states the consequence — mixed "
                   "grades, or last year's balances still owed."},
            {"fr": "L'opération étant irréversible, il faut **retaper l'année** (par exemple `2025-2026`) pour "
                   "armer le bouton. Une année déjà clôturée ne peut pas l'être une seconde fois.",
             "en": "The operation cannot be undone, so you must **retype the year** (for example `2025-2026`) to arm "
                   "the button. A year already closed cannot be closed twice."},
        ]},
        {"type": "note", "tone": "info", "fr":
            "Rien n'est perdu. Les notes et les états de compte partent dans une archive datée, consultable en base, "
            "et **l'historique des encaissements reste intact** : seuls les cumuls de l'année sont remis à zéro. Le "
            "**parcours scolaire** de chaque élève (chapitre 15) conserve par ailleurs sa moyenne annuelle, son rang "
            "et la décision du conseil.",
         "en":
            "Nothing is lost. Grades and statements move to a dated archive, queryable in the database, and **the "
            "payment history stays intact**: only the year's running totals are reset. Each student's **school "
            "journey** (chapter 15) also keeps their annual average, rank and council decision."},
        {"type": "note", "tone": "limit", "fr":
            "Les bulletins des années archivées ne se rouvrent pas encore depuis l'application : la moyenne "
            "annuelle, le rang et la décision restent lisibles dans le parcours de l'élève, mais le détail note par "
            "note se relit en base.",
         "en":
            "Report cards of archived years cannot be reopened from the app yet: the annual average, rank and "
            "decision stay readable in the student's journey, but the mark-by-mark detail is read from the database."},

        {"type": "check", "items": [
            {"fr": "Déduire automatiquement la progression, puis corriger un enchaînement à la main.",
             "en": "Auto-detect the progression, then fix one chaining by hand."},
            {"fr": "Créer une règle de classe à 12/20 et vérifier qu'elle prime sur la règle de l'école.",
             "en": "Create a class rule at 12/20 and check it beats the school rule."},
            {"fr": "Simuler une classe, admettre un élève sous le seuil avec motif, appliquer, puis retrouver "
                   "l'arbitrage dans l'historique et le parcours de l'élève.",
             "en": "Simulate a class, promote a student below the pass mark with a reason, apply, then find the "
                   "override in the history and in the student's journey."},
            {"fr": "Tenter la clôture avec une classe non passée, constater le refus, puis clôturer une fois "
                   "toutes les classes traitées et vérifier que l'année courante a basculé.",
             "en": "Try the closure with one class left unprocessed, see it refused, then close once every class is "
                   "done and check that the current year has switched."},
        ]},
    ],
}


CH_SANTE = {
    "id": "sante",
    "num": "17",
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
    "num": "18",
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

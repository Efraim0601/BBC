# -*- coding: utf-8 -*-
"""Chapitres 18 à 22 — pilotage, portail parent, scénario de rentrée, FAQ, annexes."""

CH_PILOTAGE = {
    "id": "pilotage",
    "num": "18",
    "title": {"fr": "Pilotage — tableau de bord, alertes, rapports",
              "en": "Steering — dashboard, alerts, reports"},
    "subtitle": {"fr": "Les trois écrans de la direction : la journée, les risques, les tendances.",
                 "en": "The three management screens: the day, the risks, the trends."},
    "who": {"fr": "Direction et censeur.", "en": "Management and dean of studies."},
    "blocks": [
        {"type": "h", "fr": "18.1 Tableau de bord", "en": "18.1 Dashboard"},
        {"type": "p", "fr":
            "Vue du jour, composée uniquement des blocs que vos droits autorisent : effectif et répartition, "
            "recettes et dépenses sur 30 jours, présence en direct, retardataires du jour et derniers encaissements.",
         "en":
            "Today's view, built only from the blocks your rights allow: enrolment and split, 30-day revenue and "
            "expenses, live attendance, today's late arrivals and the latest payments."},
        {"type": "figure", "img": "150-tableau-de-bord",
         "caption": {"fr": "Tableau de bord : indicateurs, courbe des recettes, répartition des effectifs et présence du jour.",
                     "en": "Dashboard: indicators, revenue chart, enrolment split and today's attendance."}},
        {"type": "note", "tone": "info", "fr":
            "Un compte qui n'a pas accès à Finance ne voit ni la courbe des recettes ni les encaissements : le "
            "tableau de bord se recompose automatiquement selon les droits.",
         "en":
            "An account without Finance access sees neither the revenue chart nor the payments: the dashboard "
            "reflows automatically according to rights."},

        {"type": "h", "fr": "18.2 Alertes proactives", "en": "18.2 Proactive alerts"},
        {"type": "p", "fr":
            "Le système détecte automatiquement quatre familles de risques : **chute de résultats**, **absences "
            "répétées**, **discipline** et **impayés**. Chaque alerte porte une gravité — Critique, À surveiller "
            "ou Information.",
         "en":
            "The system automatically detects four families of risk: **grade drop**, **repeated absences**, "
            "**discipline** and **unpaid fees**. Each alert carries a severity — Critical, Warning or Info."},
        {"type": "steps", "items": [
            {"fr": "Ouvrez __Alertes__. Les trois compteurs du haut donnent la volumétrie par gravité ; la ligne de "
                   "filtres permet d'isoler une famille.",
             "en": "Open __Alerts__. The three counters at the top give the volume per severity; the filter row "
                   "isolates one family.",
             "img": "151-alertes",
             "caption": {"fr": "File des alertes ouvertes, filtrable par type.",
                         "en": "Queue of open alerts, filterable by type."}},
            {"fr": "__Relancer le scan__ recalcule les alertes à la demande — utile après un import de notes ou une "
                   "campagne d'encaissement.",
             "en": "__Rescan__ recomputes the alerts on demand — useful after a mark import or a collection campaign."},
            {"fr": "__Vu__ marque l'alerte comme prise en compte (elle reste dans la liste, signalée). "
                   "__Résoudre__ la retire de la file.",
             "en": "__Ack__ marks the alert as acknowledged (it stays in the list, flagged). __Resolve__ removes it "
                   "from the queue."},
        ]},

        {"type": "h", "fr": "18.3 Rapports", "en": "18.3 Reports"},
        {"type": "p", "fr":
            "Analytique école entière, indépendante du parcours : bilan financier, démographie et présence "
            "mensuelle.",
         "en":
            "School-wide analytics, independent of the parcours: financial report, demographics and monthly "
            "attendance."},
        {"type": "figure", "img": "152-rapports",
         "caption": {"fr": "Rapports : recettes/dépenses/solde, taux de recouvrement, démographie par sexe, niveau et sous-système.",
                     "en": "Reports: revenue/expenses/balance, recovery rate, demographics by gender, level and sub-system."}},
        {"type": "steps", "items": [
            {"fr": "Les quatre indicateurs du haut donnent recettes, dépenses, solde net et **taux de recouvrement** "
                   "— le même chiffre que l'onglet Débiteurs de Finance.",
             "en": "The four indicators at the top give revenue, expenses, net balance and the **recovery rate** — "
                   "the same figure as the Debtors tab in Finance."},
            {"fr": "Pour la **présence mensuelle**, choisissez un mois puis cliquez __Charger__ : le tableau donne, "
                   "par élève, le nombre de présences, retards, absences et le taux.",
             "en": "For **monthly attendance**, pick a month then click __Load__: the table gives, per student, the "
                   "number of presences, lates, absences and the rate."},
            {"fr": "__Exporter__ produit un CSV et __Imprimer__ une version papier de la page.",
             "en": "__Export__ produces a CSV and __Print__ a paper version of the page."},
        ]},
        {"type": "check", "items": [
            {"fr": "Lire le taux de présence et le solde du jour sur le tableau de bord.",
             "en": "Read the attendance rate and today's balance on the dashboard."},
            {"fr": "Relancer un scan d'alertes et traiter une alerte.",
             "en": "Rescan alerts and process one."},
            {"fr": "Charger la présence mensuelle du mois en cours et l'exporter.",
             "en": "Load this month's attendance and export it."},
        ]},
    ],
}


CH_PARENT = {
    "id": "portail-parent",
    "num": "19",
    "title": {"fr": "Portail parent", "en": "Parent portal"},
    "subtitle": {"fr": "Ce que voit une famille — et ce qu'elle ne peut pas voir.",
                 "en": "What a family sees — and what it cannot see."},
    "who": {"fr": "Parents (compte créé depuis la fiche élève, §4.4).",
            "en": "Parents (account created from the student record, §4.4)."},
    "blocks": [
        {"type": "p", "fr":
            "Un compte parent se connecte à la **même adresse** que le personnel, avec le même écran de connexion, "
            "mais arrive directement sur son espace : il n'a ni menu latéral, ni choix de parcours, ni accès aux "
            "modules du personnel.",
         "en":
            "A parent account signs in at the **same address** as staff, on the same screen, but lands directly in "
            "their space: no side menu, no parcours picker, no access to staff modules."},
        {"type": "figure", "img": "160-parent-accueil",
         "caption": {"fr": "Accueil du portail : sélecteur d'enfants, taux de présence, solde et coordonnées de l'école.",
                     "en": "Portal home: child selector, attendance rate, balance and school contacts."}},
        {"type": "steps", "title": {"fr": "Les cinq onglets", "en": "The five tabs"}, "items": [
            {"fr": "__Vue d'ensemble__ — taux de présence, solde de frais avec son statut (à jour, partiel, "
                   "impayé), nombre d'évaluations et coordonnées de l'établissement.",
             "en": "__Overview__ — attendance rate, fee balance with its status (up to date, partial, unpaid), "
                   "number of assessments and school contacts."},
            {"fr": "__Frais & paiements__ — la **situation de scolarité** de l'enfant selon la grille de sa "
                   "classe : montant annuel, part déjà réglée, reste à payer, **échéancier tranche par tranche** "
                   "(réglée, partielle, à venir, en retard), **moyens de paiement** acceptés avec leurs "
                   "coordonnées, et l'historique des reçus avec leur référence de transaction.",
             "en": "__Fees & payments__ — the child's **fee position** according to their class grid: annual "
                   "amount, share already paid, outstanding balance, **installment-by-installment schedule** "
                   "(settled, partial, upcoming, overdue), accepted **payment methods** with their details, and the "
                   "receipt history with transaction references.",
             "img": "164-parent-frais",
             "caption": {"fr": "Frais & paiements : échéancier de la classe, comment payer, et les reçus déjà émis.",
                         "en": "Fees & payments: the class schedule, how to pay, and the receipts already issued."}},
            {"fr": "__Notes__ — le détail par matière, coefficient et séquence, avec la **moyenne pondérée** "
                   "calculée exactement comme sur le bulletin officiel.",
             "en": "__Grades__ — the detail per subject, coefficient and sequence, with the **weighted average** "
                   "computed exactly as on the official report card.",
             "img": "161-parent-notes",
             "caption": {"fr": "Notes de l'enfant sélectionné, avec moyenne pondérée en pied de tableau.",
                         "en": "The selected child's marks, with the weighted average in the footer."}},
            {"fr": "__Fournitures & manuels__ — les listes **publiées** par l'école pour la classe de l'enfant "
                   "(chapitre 14), avec le coût total des manuels.",
             "en": "__Supplies & textbooks__ — the lists **published** by the school for the child's class "
                   "(chapter 14), with the total textbook cost.",
             "img": "162-parent-fournitures",
             "caption": {"fr": "Listes de rentrée telles que les voit la famille.",
                         "en": "Back-to-school lists as the family sees them."}},
            {"fr": "__Boîte à suggestions__ — message à l'école, classé en Suggestion, Question, Réclamation ou "
                   "Remerciement. Le parent suit l'état de chacun de ses messages : en attente de lecture, lu, "
                   "traité, clôturé.",
             "en": "__Suggestion box__ — a message to the school, filed as Suggestion, Question, Complaint or "
                   "Thanks. The parent follows each message's state: awaiting review, read, answered, closed.",
             "img": "163-parent-suggestions",
             "caption": {"fr": "Boîte à suggestions : rédaction à gauche, historique et statuts à droite.",
                         "en": "Suggestion box: composer on the left, history and statuses on the right."}},
        ]},
        {"type": "note", "tone": "info", "fr":
            "Le parent **ne paie pas depuis l'application** : il règle par Orange Money, MTN MoMo, carte ou "
            "virement avec les coordonnées affichées, puis transmet la **référence de transaction** à l'économat, "
            "qui enregistre le versement. La situation se met à jour dès l'enregistrement.",
         "en":
            "The parent **does not pay inside the application**: they settle by Orange Money, MTN MoMo, card or "
            "transfer using the details shown, then pass the **transaction reference** to the bursary, who records "
            "it. The position updates as soon as the payment is recorded."},
        {"type": "note", "tone": "info", "fr":
            "Quand plusieurs enfants sont rattachés au même compte, une barre de sélection apparaît en haut : "
            "changer d'enfant recharge notes, listes et soldes.",
         "en":
            "When several children are linked to the same account, a selector bar appears at the top: switching "
            "child reloads marks, lists and balances."},
        {"type": "note", "tone": "warn", "fr":
            "Le cloisonnement est appliqué **par le serveur** : un parent ne peut atteindre que les données de ses "
            "propres enfants. Une adresse du personnel saisie à la main renvoie vers son portail. C'est aussi "
            "pourquoi le rôle parent ne peut recevoir aucun module du personnel (§2.2).",
         "en":
            "Isolation is enforced **by the server**: a parent can only reach their own children's data. Typing a "
            "staff URL by hand redirects to the portal. This is also why the parent role cannot be granted any "
            "staff module (§2.2)."},
        {"type": "check", "items": [
            {"fr": "Me connecter avec un compte parent et consulter les notes.",
             "en": "Sign in with a parent account and consult the marks."},
            {"fr": "Ouvrir __Frais & paiements__ et lire l'échéancier de la classe de l'enfant.",
             "en": "Open __Fees & payments__ and read the child's class schedule."},
            {"fr": "Retrouver le numéro Orange Money de l'école et les instructions de paiement.",
             "en": "Find the school's Orange Money number and the payment instructions."},
            {"fr": "Vérifier qu'un versement enregistré par l'économat apparaît dans « Mes versements ».",
             "en": "Check that a payment recorded by the bursary appears under “My payments”."},
            {"fr": "Basculer d'un enfant à l'autre.", "en": "Switch from one child to the other."},
            {"fr": "Vérifier qu'une liste non publiée n'apparaît pas.",
             "en": "Check that an unpublished list does not appear."},
            {"fr": "Envoyer un message depuis la boîte à suggestions.",
             "en": "Send a message from the suggestion box."},
            {"fr": "Saisir à la main l'adresse d'un module du personnel et constater le refus.",
             "en": "Type a staff module URL by hand and observe the refusal."},
        ]},
    ],
}


CH_RENTREE = {
    "id": "demarrer-une-annee",
    "num": "20",
    "title": {"fr": "Démarrer une nouvelle année", "en": "Starting a new school year"},
    "subtitle": {"fr": "L'ordre des opérations, de la base vide à la première journée de classe.",
                 "en": "The order of operations, from an empty database to the first day of school."},
    "who": {"fr": "Direction et administrateur — comptez une demi-journée à deux.",
            "en": "Management and administrator — allow half a day, two people."},
    "blocks": [
        {"type": "p", "fr":
            "Chaque étape dépend de la précédente. Suivre cet ordre évite les blocages classiques du type "
            "« impossible de créer une classe » ou « bulletin vide ».",
         "en":
            "Each step depends on the previous one. Following this order avoids the classic blockers such as "
            "“cannot create a class” or “empty report card”."},
        {"type": "steps", "items": [
            {"fr": "**Identité et horaires** — __Paramètres → Général__ : nom, ville, devise, autorité de tutelle "
                   "et surtout l'**heure de début des cours** (§3.4).",
             "en": "**Identity and hours** — __Settings → General__: name, city, currency, authority and above all "
                   "the **school start time** (§3.4)."},
            {"fr": "**Calendrier** — saisissez les jours fériés connus (§3.5).",
             "en": "**Calendar** — enter the known holidays (§3.5)."},
            {"fr": "**Sections puis classes** — pour chaque parcours : la section d'abord, les classes ensuite "
                   "(§3.1 et §3.2).",
             "en": "**Sections then classes** — for each parcours: the section first, then the classes (§3.1, §3.2)."},
            {"fr": "**Matières et coefficients** — importez le catalogue standard, ajustez, puis chargez les "
                   "coefficients par classe (§3.3).",
             "en": "**Subjects and coefficients** — import the standard catalogue, adjust, then load the per-class "
                   "coefficients (§3.3)."},
            {"fr": "**Catalogue discipline** — motifs et sanctions du règlement intérieur (§3.6).",
             "en": "**Discipline catalogue** — types and sanctions from the school rules (§3.6)."},
            {"fr": "**Messagerie** — configurez et testez le SMTP avant toute création de compte (§3.7).",
             "en": "**E-mail** — configure and test SMTP before creating any account (§3.7)."},
            {"fr": "**Rôles et permissions** — créez les rôles particuliers de l'établissement puis réglez la "
                   "matrice (chapitre 2).",
             "en": "**Roles and permissions** — create the school's specific roles then set the matrix (chapter 2)."},
            {"fr": "**Personnel** — import ou portail d'inscription, puis création des comptes de connexion "
                   "(chapitre 5).",
             "en": "**Staff** — import or registration portal, then create the login accounts (chapter 5)."},
            {"fr": "**Élèves** — import classe par classe depuis le registre officiel (§4.5).",
             "en": "**Students** — import class by class from the official register (§4.5)."},
            {"fr": "**Comptes parents** — créez-les au fil des inscriptions, en réutilisant le même identifiant "
                   "pour les fratries (§4.4).",
             "en": "**Parent accounts** — create them as enrolments come in, reusing the same username for siblings "
                   "(§4.4)."},
            {"fr": "**Grille des frais** — avant le premier encaissement (§11.1).",
             "en": "**Fee grid** — before the first payment (§11.1)."},
            {"fr": "**Emploi du temps** — classe par classe, en surveillant les conflits d'enseignant (chapitre 10).",
             "en": "**Timetable** — class by class, watching for teacher clashes (chapter 10)."},
            {"fr": "**Fournitures et manuels** — préparez les listes, puis publiez-les aux familles (chapitre 14).",
             "en": "**Supplies and textbooks** — prepare the lists, then publish them to families (chapter 14)."},
            {"fr": "**Premier jour** — la présence remonte toute seule ; discipline, cahier de textes et "
                   "correspondance s'utilisent au fil de l'eau.",
             "en": "**Day one** — attendance flows in on its own; discipline, coursebook and correspondence are "
                   "used as you go."},
        ]},
        {"type": "note", "tone": "tip", "fr":
            "Faites l'essai complet sur **un seul parcours** (par exemple Secondaire FR) avant de dérouler les "
            "autres : les erreurs de paramétrage se voient beaucoup plus vite sur un périmètre réduit.",
         "en":
            "Run the whole sequence on **one parcours** (say Secondary FR) before rolling out the others: "
            "configuration mistakes surface much faster on a small perimeter."},
        {"type": "check", "items": [
            {"fr": "Dérouler les 14 étapes sur un parcours de test sans blocage.",
             "en": "Run the 14 steps on a test parcours with no blocker."},
            {"fr": "Vérifier qu'un bulletin de ce parcours affiche bien des notes et un rang.",
             "en": "Check that a report card in this parcours shows marks and a rank."},
            {"fr": "Vérifier qu'un parent ne voit que son enfant.",
             "en": "Check that a parent only sees their own child."},
        ]},
    ],
}


CH_FAQ = {
    "id": "faq",
    "num": "21",
    "title": {"fr": "Questions fréquentes & dépannage", "en": "FAQ & troubleshooting"},
    "subtitle": {"fr": "Les blocages les plus courants et leur cause réelle.",
                 "en": "The most common blockers and their real cause."},
    "blocks": [
        {"type": "table",
         "head": {"fr": ["Symptôme", "Cause la plus fréquente et solution"],
                  "en": ["Symptom", "Most frequent cause and fix"]},
         "rows": {"fr": [
             ["Un module a disparu de mon menu.",
              "Vos droits ont changé. Vérifiez la matrice (__Paramètres → Permissions__) et **reconnectez-vous** : les droits sont chargés à la connexion."],
             ["Mes élèves / classes ont disparu.",
              "Vous n'êtes pas dans le bon **parcours**. Cliquez le parcours actif dans la barre supérieure, ou choisissez « Tous les parcours »."],
             ["Le bouton « Nouvelle classe » est grisé.",
              "Aucune **section** n'existe dans ce parcours. Créez-la d'abord (§3.1)."],
             ["Un élève n'apparaît pas dans une liste de classe.",
              "Il est « Non affecté » : ouvrez sa fiche et choisissez une classe (§4.3)."],
             ["Le bulletin est vide.",
              "Aucune note n'existe pour la séquence choisie, ou la classe n'a pas de matières avec coefficient (§3.3)."],
             ["Le bulletin est verrouillé.",
              "L'élève a un solde impayé. Enregistrez le versement dans Finance (§11.2), puis rouvrez le bulletin."],
             ["Des retards apparaissent un dimanche ou un jour férié.",
              "Le jour n'est pas déclaré dans le calendrier (§3.5). Les week-ends, eux, sont exclus d'office."],
             ["Tout le monde est en retard depuis ce matin.",
              "L'__heure de début des cours__ a été modifiée dans Paramètres → Général (§3.4)."],
             ["Le SMS au parent n'est pas parti.",
              "Le contact principal n'a pas de numéro. Complétez père / mère / tuteur sur la fiche élève (§4.3)."],
             ["Le nouvel employé n'a pas reçu ses identifiants.",
              "SMTP absent ou mal configuré, ou fiche sans e-mail. Testez l'envoi (§3.7) puis utilisez __Réinitialiser__ (§5.3)."],
             ["« Mot de passe oublié » : je ne reçois rien.",
              "Trois causes possibles : aucune adresse sur votre fiche personnel, compte parent (non couvert), ou messagerie hors service. Dans tous les cas **votre mot de passe actuel reste valable** ; l'administrateur réinitialise (§1.2 et §5.3)."],
             ["Les parents ne voient pas la liste de fournitures.",
              "La liste est restée en **brouillon**. Cliquez __Publier__ (chapitre 14)."],
             ["Impossible d'accorder un module au rôle parent.",
              "C'est volontaire et non contournable : le rôle parent est limité à son portail (§2.2)."],
             ["« Votre session a expiré ».",
              "Inactivité prolongée ou poste en veille. Reconnectez-vous ; le travail enregistré n'est pas perdu."],
             ["Un élève n'a pas le bon montant de frais.",
              "Il suit la grille de sa classe si elle existe, sinon celle de son niveau. Vérifiez qu'il est bien affecté à sa classe (§4.3) et regardez la portée des grilles (§11.2)."],
             ["Impossible d'enregistrer un paiement Orange Money.",
              "La référence de transaction est obligatoire pour ce canal, ou le canal a été désactivé dans __Finance → Moyens de paiement__ (§11.1)."],
             ["Le parent ne voit pas comment payer.",
              "Le canal n'est pas coché « Visible des parents », ou ses coordonnées sont vides (§11.1)."],
             ["Le taux de recouvrement semble faux.",
              "Il rapporte l'encaissé à l'attendu **de toute l'école**, d'après la grille des frais. Vérifiez que chaque niveau a bien sa grille (§11.1)."],
         ], "en": [
             ["A module vanished from my menu.",
              "Your rights changed. Check the matrix (__Settings → Permissions__) and **sign in again**: rights are loaded at sign-in."],
             ["My students / classes disappeared.",
              "You are not in the right **parcours**. Click the active parcours in the top bar, or choose “All parcours”."],
             ["The “New class” button is greyed out.",
              "No **section** exists in this parcours. Create one first (§3.1)."],
             ["A student is missing from a class list.",
              "They are “Unassigned”: open their record and pick a class (§4.3)."],
             ["The report card is empty.",
              "No marks exist for the chosen sequence, or the class has no subjects with coefficients (§3.3)."],
             ["The report card is locked.",
              "The student has an outstanding balance. Record the payment in Finance (§11.2), then reopen the report card."],
             ["Lateness appears on a Sunday or a holiday.",
              "The day is not declared in the calendar (§3.5). Weekends are excluded automatically."],
             ["Everybody is late since this morning.",
              "The __school start time__ was changed in Settings → General (§3.4)."],
             ["The SMS to the parent did not go out.",
              "The main contact has no number. Fill in father / mother / guardian on the student record (§4.3)."],
             ["The new employee did not receive credentials.",
              "SMTP missing or misconfigured, or no e-mail on the record. Test sending (§3.7) then use __Reset__ (§5.3)."],
             ["Parents cannot see the supply list.",
              "The list is still a **draft**. Click __Publish__ (chapter 14)."],
             ["I cannot grant a module to the parent role.",
              "This is deliberate and cannot be bypassed: the parent role is limited to its portal (§2.2)."],
             ["“Your session has expired”.",
              "Long inactivity or a sleeping computer. Sign in again; saved work is not lost."],
             ["A student has the wrong fee amount.",
              "They follow their class grid when it exists, otherwise their level grid. Check they are assigned to their class (§4.3) and review the scope of the grids (§11.2)."],
             ["An Orange Money payment cannot be recorded.",
              "The transaction reference is mandatory for that channel, or the channel was disabled in __Finance → Payment methods__ (§11.1)."],
             ["The parent cannot see how to pay.",
              "The channel is not ticked “Shown to parents”, or its details are empty (§11.1)."],
             ["The recovery rate looks wrong.",
              "It compares collected against expected **for the whole school**, based on the fee grid. Check that each level has its grid (§11.1)."],
         ]}},
    ],
}


CH_ANNEXES = {
    "id": "annexes",
    "num": "22",
    "title": {"fr": "Annexes", "en": "Appendices"},
    "subtitle": {"fr": "Glossaire, comptes de démonstration et mise à jour du guide.",
                 "en": "Glossary, demo accounts and updating the guide."},
    "blocks": [
        {"type": "h", "fr": "22.1 Glossaire", "en": "22.1 Glossary"},
        {"type": "table",
         "head": {"fr": ["Terme", "Définition"], "en": ["Term", "Definition"]},
         "rows": {"fr": [
             ["Parcours", "Niveau (Maternelle / Primaire / Secondaire) × sous-système (Francophone / Anglophone). Filtre global des données."],
             ["Section", "Regroupement de classes d'un même parcours, ex. « Primaire francophone »."],
             ["Séquence", "Période d'évaluation (1 à 6) sur laquelle porte un bulletin."],
             ["PV", "Procès-verbal : classement d'une classe par moyenne pour une séquence."],
             ["APC", "Approche par compétences — format de bulletin de la maternelle et du primaire."],
             ["Tranche", "Fraction du montant annuel des frais, avec son libellé et son échéance."],
             ["Canal de paiement", "Moyen accepté par l'école : espèces, Orange Money (OM), MTN Mobile Money (MOMO), carte bancaire (MPGS), virement."],
             ["Référence de transaction", "Identifiant fourni par l'opérateur (ID Orange Money, ID MoMo, n° d'autorisation MPGS) : la preuve du versement."],
             ["Matricule", "Identifiant interne de l'élève, attribué par le système."],
             ["NIU", "Identifiant unique national, saisi depuis le registre officiel."],
             ["Contact principal", "Responsable retenu pour les SMS : père, sinon mère, sinon tuteur."],
             ["Permanent / Vacataire", "Employé au salaire mensuel / payé au taux horaire."],
         ], "en": [
             ["Parcours", "Level (Kindergarten / Primary / Secondary) × sub-system (Francophone / English). Global data filter."],
             ["Section", "A group of classes within one parcours, e.g. “Francophone primary”."],
             ["Sequence", "Assessment period (1 to 6) a report card covers."],
             ["Master sheet", "Class ranking by average for one sequence."],
             ["APC", "Competency-based approach — the report card format for kindergarten and primary."],
             ["Installment", "A fraction of the annual fee amount, with its label and due date."],
             ["Payment channel", "A method the school accepts: cash, Orange Money (OM), MTN Mobile Money (MOMO), bank card (MPGS), transfer."],
             ["Transaction reference", "The identifier issued by the operator (Orange Money ID, MoMo ID, MPGS authorisation number): the proof of payment."],
             ["Student ID", "Internal student identifier, assigned by the system."],
             ["NIU", "National unique identifier, taken from the official register."],
             ["Main contact", "The adult used for SMS: father, else mother, else guardian."],
             ["Permanent / Contractor", "Employee on a monthly salary / paid by the hour."],
         ]}},

        {"type": "h", "fr": "22.2 Comptes de démonstration", "en": "22.2 Demo accounts"},
        {"type": "p", "fr":
            "La pile de démonstration (`make demo`) contient un jeu de données complet et trois comptes, tous avec "
            "le mot de passe `password`. Les captures de ce guide en sont issues.",
         "en":
            "The demo stack (`make demo`) ships a full dataset and three accounts, all with the password "
            "`password`. The screenshots in this guide come from it."},
        {"type": "table",
         "head": {"fr": ["Identifiant", "Rôle", "Sert à illustrer"], "en": ["Username", "Role", "Illustrates"]},
         "rows": {"fr": [
             ["`principal`", "Direction", "Tous les modules, écriture — sauf Finance en lecture."],
             ["`econome`", "Économe", "Finance en écriture : encaissements, dépenses, grille des frais."],
             ["`parent1`", "Parent", "Le portail parent et son cloisonnement."],
         ], "en": [
             ["`principal`", "Management", "All modules, write — except Finance in read-only."],
             ["`econome`", "Bursar", "Finance in write mode: payments, expenses, fee grid."],
             ["`parent1`", "Parent", "The parent portal and its isolation."],
         ]}},
        {"type": "note", "tone": "warn", "fr":
            "Ces comptes n'existent **que** dans le profil de démonstration. Une installation de production démarre "
            "avec le seul administrateur défini dans le fichier `.env`.",
         "en":
            "These accounts exist **only** in the demo profile. A production install starts with the single "
            "administrator defined in the `.env` file."},

        {"type": "h", "fr": "22.3 Support d'atelier", "en": "22.3 Workshop deck"},
        {"type": "p", "fr":
            "Un **support projetable** accompagne ce guide pour les séances de formation : "
            "`/guide/atelier.html`. Il déroule la journée module par module — objectif, démonstration "
            "animateur, exercice participants, critères de réussite et pièges fréquents. "
            "Flèches ← → pour naviguer, **S** pour le sommaire, **P** pour imprimer (une diapositive par page).",
         "en":
            "A **projectable deck** accompanies this guide for training sessions: `/guide/atelier.html`. "
            "It runs through the day module by module — objective, trainer demo, participant exercise, success "
            "criteria and common pitfalls. Arrows ← → to navigate, **S** for the outline, **P** to print "
            "(one slide per page)."},

        {"type": "h", "fr": "22.4 Mettre le guide à jour", "en": "22.4 Updating the guide"},
        {"type": "p", "fr":
            "Le guide est **généré**, il ne s'édite pas à la main. Tout vit dans `tools/guide/` :",
         "en":
            "The guide is **generated**, not hand-edited. Everything lives in `tools/guide/`:"},
        {"type": "list", "items": [
            {"fr": "`content.py` et `chapters_*.py` — le texte bilingue et l'enchaînement des procédures.",
             "en": "`content.py` and `chapters_*.py` — the bilingual text and the flow of the procedures."},
            {"fr": "`capture.js` — la campagne de captures d'écran, rejouée sur la pile de démonstration.",
             "en": "`capture.js` — the screenshot campaign, replayed against the demo stack."},
            {"fr": "`seed-demo.py` — le jeu de données de documentation (élèves, notes, paiements…).",
             "en": "`seed-demo.py` — the documentation dataset (students, marks, payments…)."},
            {"fr": "`build.py` — produit `frontend/public/guide/index.html` et `GUIDE_UTILISATEUR.md`.",
             "en": "`build.py` — produces `frontend/public/guide/index.html` and `GUIDE_UTILISATEUR.md`."},
            {"fr": "`atelier.py` et `build-atelier.py` — le support d'atelier projetable.",
             "en": "`atelier.py` and `build-atelier.py` — the projectable workshop deck."},
            {"fr": "Le mode d'emploi complet est dans `tools/guide/README.md`.",
             "en": "The full instructions are in `tools/guide/README.md`."},
        ]},
    ],
}

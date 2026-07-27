# -*- coding: utf-8 -*-
"""Chapitres 3 à 5 — paramètres, élèves, personnel."""

CH_PARAMETRES = {
    "id": "parametres",
    "num": "3",
    "title": {"fr": "Paramètres — la fondation de l'année", "en": "Settings — the foundation of the year"},
    "subtitle": {
        "fr": "Sections, classes, matières, identité de l'école, calendrier, catalogues, permissions et messagerie.",
        "en": "Sections, classes, subjects, school identity, calendar, catalogues, permissions and e-mail.",
    },
    "who": {"fr": "Direction et administrateurs (droit __Paramètres : Complet__).",
            "en": "Management and administrators (__Settings: Write__)."},
    "blocks": [
        {"type": "p", "fr":
            "Ce module conditionne tous les autres : sans classe, impossible d'inscrire un élève ; sans matière, "
            "pas de bulletin ; sans heure d'ouverture, pas de retard calculé. Traitez-le **en premier**, dans "
            "l'ordre des sept onglets ci-dessous.",
         "en":
            "This module conditions all the others: without a class you cannot enrol a student; without subjects "
            "there is no report card; without an opening time no lateness is computed. Do it **first**, following "
            "the seven tabs below."},
        {"type": "table",
         "head": {"fr": ["Onglet", "Contenu"], "en": ["Tab", "Content"]},
         "rows": {"fr": [
             ["Scolarité", "Sections → classes → matières et coefficients. Le squelette de l'école."],
             ["Général", "Identité de l'établissement, devise monétaire, horaires de cours, état du lecteur d'empreintes."],
             ["Calendrier", "Jours fériés et fermetures exceptionnelles."],
             ["Discipline", "Catalogue des types d'incident et des sanctions."],
             ["Permissions", "Matrice rôles × modules (chapitre 2)."],
             ["Rôles", "Création et libellés des rôles (chapitre 2)."],
             ["Messagerie", "Serveur SMTP et notifications e-mail."],
         ], "en": [
             ["Academics", "Sections → classes → subjects and coefficients. The backbone of the school."],
             ["General", "School identity, currency, school hours, fingerprint reader status."],
             ["Calendar", "Public holidays and exceptional closures."],
             ["Discipline", "Catalogue of incident types and sanctions."],
             ["Permissions", "Role × module matrix (chapter 2)."],
             ["Roles", "Role creation and labels (chapter 2)."],
             ["E-mail", "SMTP server and e-mail notifications."],
         ]}},

        {"type": "h", "fr": "3.1 Sections", "en": "3.1 Sections"},
        {"type": "p", "fr":
            "Une **section** regroupe les classes d'un même sous-système et d'un même niveau : « Primaire "
            "francophone », « Secondary English »… C'est le premier objet à créer.",
         "en":
            "A **section** groups the classes of one sub-system and one level: “Francophone primary”, "
            "“Secondary English”… It is the first object to create."},
        {"type": "steps", "items": [
            {"fr": "Ouvrez __Paramètres → Scolarité → Sections__. Le bandeau rappelle le parcours actif et "
                   "prévient que les autres parcours sont masqués.",
             "en": "Open __Settings → Academics → Sections__. The banner recalls the active parcours and warns that "
                   "other parcours are hidden.",
             "img": "10-parametres-scolarite-sections",
             "caption": {"fr": "Liste des sections, avec le nombre de classes rattachées à chacune.",
                         "en": "Section list, with the number of classes attached to each."}},
            {"fr": "Cliquez __Nouvelle section__, saisissez le libellé. Le sous-système et le niveau sont "
                   "**verrouillés sur le parcours actif** — c'est ce qui garantit la cohérence des données.",
             "en": "Click __New section__ and type the label. Sub-system and level are **locked to the active "
                   "parcours** — this is what keeps the data consistent.",
             "img": "11-parametres-section-formulaire",
             "caption": {"fr": "Création d'une section : seul le libellé est libre quand un parcours est actif.",
                         "en": "Creating a section: only the label is free while a parcours is active."}},
            {"fr": "__Enregistrer__. Les icônes de la colonne de droite permettent de renommer ou de supprimer. "
                   "Une section qui contient des classes ne peut pas être supprimée.",
             "en": "__Save__. The icons on the right let you rename or delete. A section that still holds classes "
                   "cannot be deleted."},
        ]},
        {"type": "note", "tone": "tip", "fr":
            "Pour créer des sections dans plusieurs parcours, changez de parcours dans la barre supérieure entre "
            "chaque création — ou passez en « Tous les parcours », où le sous-système et le niveau redeviennent "
            "modifiables dans le formulaire.",
         "en":
            "To create sections in several parcours, switch parcours in the top bar between creations — or use "
            "“All parcours”, where sub-system and level become editable in the form again."},

        {"type": "h", "fr": "3.2 Classes et enseignants", "en": "3.2 Classes and teachers"},
        {"type": "steps", "items": [
            {"fr": "Ouvrez l'onglet __Classes__. Le tableau affiche pour chaque classe sa section, son niveau, "
                   "son effectif et le nombre d'enseignants rattachés.",
             "en": "Open the __Classes__ tab. The table shows each class with its section, level, enrolment and the "
                   "number of teachers attached.",
             "img": "12-parametres-classes",
             "caption": {"fr": "Les classes du parcours actif. Le bouton __Modèle CSV__ télécharge un gabarit de saisie.",
                         "en": "The classes of the active parcours. __CSV template__ downloads a data-entry template."}},
            {"fr": "__Nouvelle classe__ : donnez le nom exact utilisé par l'école (« 6ème A », « Form 1 ») et "
                   "choisissez la section de rattachement.",
             "en": "__New class__: give the exact name used by the school (“6ème A”, “Form 1”) and pick the parent section.",
             "img": "13-parametres-classe-formulaire",
             "caption": {"fr": "Le nom de la classe est repris tel quel sur les bulletins et les listes.",
                         "en": "The class name is reused as-is on report cards and lists."}},
            {"fr": "Cliquez le **compteur d'enseignants** d'une ligne pour ouvrir le panneau d'affectation, "
                   "cochez les enseignants concernés puis __Enregistrer__. Une classe peut avoir zéro, un ou "
                   "plusieurs enseignants.",
             "en": "Click the **teacher counter** on a row to open the assignment panel, tick the relevant teachers "
                   "and __Save__. A class may have zero, one or several teachers.",
             "img": "14-parametres-classe-enseignants",
             "caption": {"fr": "Affectation des enseignants à une classe (liste issue du module Personnel).",
                         "en": "Assigning teachers to a class (list comes from the Staff module)."}},
        ]},
        {"type": "note", "tone": "warn", "fr":
            "Le bouton __Nouvelle classe__ reste inactif tant qu'aucune section n'existe dans le parcours courant. "
            "C'est la cause n°1 de blocage en début d'année : créez la section d'abord.",
         "en":
            "__New class__ stays disabled while no section exists in the current parcours. This is the number-one "
            "blocker at the start of the year: create the section first."},

        {"type": "h", "fr": "3.3 Matières et coefficients", "en": "3.3 Subjects and coefficients"},
        {"type": "p", "fr":
            "Chaque sous-système a **sa propre liste** de matières ; une matière peut aussi être déclarée commune "
            "aux deux. Le coefficient sert au calcul de la moyenne pondérée du bulletin.",
         "en":
            "Each sub-system has **its own list** of subjects; a subject can also be declared common to both. The "
            "coefficient drives the weighted average on the report card."},
        {"type": "steps", "items": [
            {"fr": "Ouvrez l'onglet __Matières__ et choisissez la liste à afficher : __Francophone__, "
                   "__Anglophone__ ou __Toutes__. Le compteur de chaque bouton indique le nombre de matières.",
             "en": "Open the __Subjects__ tab and choose which list to show: __Francophone__, __Anglophone__ or "
                   "__All__. The counter on each button shows how many subjects it holds.",
             "img": "15-parametres-matieres",
             "caption": {"fr": "Liste des matières du sous-système francophone, avec leur coefficient.",
                         "en": "Francophone subject list with coefficients."}},
            {"fr": "Le bouton __Importer N matières standard__ crée en une fois le catalogue officiel de "
                   "l'établissement (liste MATIÈRE EXCEL) pour la liste affichée. Les codes déjà présents sont ignorés.",
             "en": "The __Import N standard subjects__ button creates the school's official catalogue (MATIERE EXCEL "
                   "list) for the displayed list in one go. Codes that already exist are skipped."},
            {"fr": "__Nouvelle matière__ pour un ajout manuel : code court (MATH, ENG…), sous-système, nom FR, "
                   "nom EN et coefficient par défaut. Le code n'est plus modifiable après création.",
             "en": "__New subject__ for a manual addition: short code (MATH, ENG…), sub-system, FR name, EN name and "
                   "default coefficient. The code cannot be changed after creation.",
             "img": "16-parametres-matiere-formulaire",
             "caption": {"fr": "Formulaire matière : les deux libellés servent à l'affichage FR/EN.",
                         "en": "Subject form: the two labels drive the FR/EN display."}},
        ]},
        {"type": "p", "fr":
            "Le coefficient saisi ici est un **défaut**. En pratique une matière ne pèse pas pareil en 6ème et en "
            "Terminale : la carte __Coefficients par classe__, en bas de l'onglet, permet d'importer la grille "
            "réelle depuis un fichier Excel ou CSV.",
         "en":
            "The coefficient set here is a **default**. In practice a subject does not weigh the same in Form 1 and "
            "Upper Sixth: the __Per-class coefficients__ card at the bottom of the tab imports the real grid from an "
            "Excel or CSV file."},
        {"type": "figure", "img": "17-parametres-coefficients",
         "caption": {"fr": "Coefficients par classe : colonnes attendues — sous-système, code, matière, classe, coefficient.",
                     "en": "Per-class coefficients: expected columns — sub-system, code, subject, class, coefficient."}},
        {"type": "note", "tone": "info", "fr":
            "À l'import, les classes citées dans le fichier **doivent déjà exister**. Le rapport d'import indique "
            "le nombre de coefficients enregistrés, de matières créées au passage et de lignes ignorées, avec le "
            "motif ligne par ligne.",
         "en":
            "On import, the classes named in the file **must already exist**. The import report shows how many "
            "coefficients were saved, how many subjects were created along the way and how many rows were skipped, "
            "with a reason for each."},

        {"type": "h", "fr": "3.4 Général — identité et horaires", "en": "3.4 General — identity and hours"},
        {"type": "steps", "items": [
            {"fr": "Renseignez le nom de l'école, sa devise, la ville, le pays, le téléphone et l'e-mail : ces "
                   "informations sont imprimées sur les **bulletins** et les **reçus**, et affichées aux parents "
                   "dans leur portail.",
             "en": "Fill in the school name, motto, city, country, phone and e-mail: this information is printed on "
                   "**report cards** and **receipts**, and shown to parents in their portal.",
             "img": "18-parametres-general",
             "caption": {"fr": "Identité de l'établissement et horaires ; à droite, l'état du lecteur d'empreintes.",
                         "en": "School identity and hours; on the right, the fingerprint reader status."}},
            {"fr": "__Devise monétaire__ (XAF/FCFA par défaut) et __Autorité de tutelle__ (ex. « République du "
                   "Cameroun · MINESEC ») apparaissent en en-tête des documents officiels.",
             "en": "__Currency__ (XAF/FCFA by default) and __Authority__ (e.g. “Republic of Cameroon · MINESEC”) "
                   "appear in the header of official documents."},
            {"fr": "**__Début des cours__ est le seuil de retard** : tout pointage postérieur à cette heure est "
                   "compté en retard, avec le nombre de minutes. Réglez-le avant la rentrée.",
             "en": "**__School start__ is the lateness threshold**: any scan after this time counts as late, with the "
                   "number of minutes. Set it before the school year starts."},
            {"fr": "__Enregistrer__ ; un message vert confirme la prise en compte.",
             "en": "__Save__; a green message confirms."},
        ]},

        {"type": "h", "fr": "3.5 Calendrier — jours fériés", "en": "3.5 Calendar — holidays"},
        {"type": "steps", "items": [
            {"fr": "Ouvrez l'onglet __Calendrier__, saisissez une **date** et un **libellé** (« Fête de la "
                   "Jeunesse »), puis __Ajouter__.",
             "en": "Open the __Calendar__ tab, enter a **date** and a **label** (“Youth Day”), then __Add__.",
             "img": "19-parametres-calendrier",
             "caption": {"fr": "Jours fériés à gauche, ajout et rappel des horaires à droite.",
                         "en": "Holidays on the left, add form and hours reminder on the right."}},
            {"fr": "Un jour férié **ne génère jamais de retard ni d'absence**. Les samedis et dimanches sont "
                   "exclus automatiquement, sans avoir à les déclarer.",
             "en": "A holiday **never generates lateness or absence**. Saturdays and Sundays are excluded "
                   "automatically, with nothing to declare."},
        ]},

        {"type": "h", "fr": "3.6 Catalogue Discipline", "en": "3.6 Discipline catalogue"},
        {"type": "p", "fr":
            "Les listes déroulantes du module Discipline (chapitre 8) sont alimentées ici : à gauche les **types "
            "d'incident**, à droite les **sanctions**. Chaque entrée porte un libellé FR et un libellé EN.",
         "en":
            "The drop-downs of the Discipline module (chapter 8) are fed from here: **incident types** on the left, "
            "**sanctions** on the right. Each entry carries an FR and an EN label."},
        {"type": "figure", "img": "20-parametres-catalogue-discipline",
         "caption": {"fr": "Types d'incident et sanctions — ajoutez ceux du règlement intérieur de l'école.",
                     "en": "Incident types and sanctions — add the ones from the school rules."}},
        {"type": "note", "tone": "info", "fr":
            "Tant que le catalogue est vide, Discipline propose une liste de secours (Retard, Absence, Conduite, "
            "Tenue…). Dès qu'une entrée est créée, c'est votre catalogue qui prend le relais.",
         "en":
            "While the catalogue is empty, Discipline falls back to a built-in list (Late, Absence, Conduct, "
            "Uniform…). As soon as one entry exists, your catalogue takes over."},

        {"type": "h", "fr": "3.7 Messagerie (SMTP)", "en": "3.7 E-mail (SMTP)"},
        {"type": "steps", "items": [
            {"fr": "Cochez __Activer l'envoi d'e-mails__, puis renseignez l'hôte, le port, l'utilisateur et le mot "
                   "de passe du serveur SMTP, ainsi que l'adresse et le nom d'expéditeur.",
             "en": "Tick __Enable e-mail sending__, then fill in the SMTP host, port, username and password, plus "
                   "the sender address and name.",
             "img": "23-parametres-messagerie",
             "caption": {"fr": "Configuration SMTP à gauche, notifications et test d'envoi à droite.",
                         "en": "SMTP configuration on the left, notifications and send test on the right."}},
            {"fr": "Laissez __STARTTLS__ coché sauf indication contraire de votre hébergeur. Le mot de passe n'est "
                   "jamais réaffiché : laissez le champ vide pour conserver l'ancien.",
             "en": "Keep __STARTTLS__ ticked unless your provider says otherwise. The password is never displayed "
                   "again: leave the field empty to keep the current one."},
            {"fr": "__Enregistrer__, puis utilisez __Tester l'envoi__ avec votre propre adresse pour valider la "
                   "configuration avant de créer des comptes.",
             "en": "__Save__, then use __Test sending__ with your own address to validate the configuration before "
                   "creating accounts."},
            {"fr": "L'option __Création d'un utilisateur__ envoie automatiquement ses identifiants au nouvel "
                   "employé, si sa fiche porte un e-mail.",
             "en": "The __User created__ option automatically e-mails credentials to a new employee, when their "
                   "record carries an address."},
        ]},
        {"type": "note", "tone": "warn", "fr":
            "Sans SMTP configuré, la création de compte fonctionne quand même, mais **aucun identifiant n'est "
            "envoyé** : vous devez communiquer le mot de passe autrement. La réinitialisation en libre-service "
            "(§1.2) est alors indisponible.",
         "en":
            "Without SMTP, account creation still works but **no credentials are sent**: you must pass the password "
            "on by other means. Self-service reset (§1.2) is then unavailable."},

        {"type": "check", "items": [
            {"fr": "Créer une section puis une classe dans le parcours actif.",
             "en": "Create a section then a class in the active parcours."},
            {"fr": "Importer les matières standard et modifier un coefficient.",
             "en": "Import the standard subjects and change a coefficient."},
            {"fr": "Affecter deux enseignants à une classe.", "en": "Assign two teachers to a class."},
            {"fr": "Régler l'heure de début des cours et enregistrer.",
             "en": "Set the school start time and save."},
            {"fr": "Ajouter un jour férié et vérifier qu'il apparaît dans la liste.",
             "en": "Add a holiday and check it appears in the list."},
            {"fr": "Ajouter un motif d'incident et le retrouver dans Discipline.",
             "en": "Add an incident type and find it in Discipline."},
        ]},
    ],
}


CH_ELEVES = {
    "id": "eleves",
    "num": "4",
    "title": {"fr": "Élèves", "en": "Students"},
    "subtitle": {
        "fr": "Registre, fiche complète, comptes parents et import en masse du registre officiel.",
        "en": "Register, full record, parent accounts and bulk import of the official register.",
    },
    "who": {"fr": "Secrétariat, direction, préfet (lecture pour la plupart des autres rôles).",
            "en": "Front office, management, prefect (read-only for most other roles)."},
    "blocks": [
        {"type": "h", "fr": "4.1 Lire la liste", "en": "4.1 Reading the list"},
        {"type": "figure", "img": "30-eleves-liste",
         "caption": {"fr": "Le registre : recherche, filtres, tableau dense et fiche détaillée sous le tableau.",
                     "en": "The register: search, filters, dense table and detail panel below."}},
        {"type": "list", "items": [
            {"fr": "La **recherche** porte sur le nom, le matricule et le nom du parent.",
             "en": "**Search** covers name, student ID and parent name."},
            {"fr": "Le filtre **Classe** est groupé par série (toutes les « 4ème » ensemble) et affiche l'effectif "
                   "de chaque sous-classe.",
             "en": "The **Class** filter is grouped by grade (all “Form 4” together) and shows the enrolment of each section."},
            {"fr": "En mode « Tous les parcours », deux filtres supplémentaires apparaissent : **Système** "
                   "(Francophone / Anglophone) et **Niveau**.",
             "en": "In “All parcours” mode, two extra filters appear: **System** (Francophone / English) and **Level**."},
            {"fr": "La liste est triée par classe puis par nom ; le compteur au-dessus du tableau indique le "
                   "nombre de résultats.",
             "en": "The list is sorted by class then name; the counter above the table shows how many results match."},
            {"fr": "__Exporter liste__ produit un CSV des lignes affichées (matricule, nom, prénom, sexe, classe, "
                   "sous-système, niveau, parent, téléphone).",
             "en": "__Export list__ produces a CSV of the displayed rows (ID, last name, first name, sex, class, "
                   "sub-system, level, parent, phone)."},
        ]},

        {"type": "h", "fr": "4.2 La fiche élève", "en": "4.2 The student record"},
        {"type": "p", "fr":
            "Cliquez une ligne : la fiche s'ouvre sous le tableau avec l'en-tête d'identité, le contact parent, "
            "les comptes parents rattachés et l'état civil complet.",
         "en":
            "Click a row: the record opens under the table with the identity header, the parent contact, the linked "
            "parent accounts and the full civil status."},
        {"type": "figure", "img": "31-eleves-fiche",
         "caption": {"fr": "Fiche élève — matricule, classe, contact principal et comptes parents.",
                     "en": "Student record — ID, class, main contact and parent accounts."}},
        {"type": "note", "tone": "info", "fr":
            "Le **contact principal** affiché (et utilisé pour les SMS) est déduit automatiquement : père, sinon "
            "mère, sinon tuteur. Il suffit donc de renseigner correctement la section « Famille / tuteur ».",
         "en":
            "The **main contact** shown (and used for SMS) is derived automatically: father, else mother, else "
            "guardian. Filling the “Family / guardian” section correctly is enough."},

        {"type": "h", "fr": "4.3 Créer ou modifier un élève", "en": "4.3 Create or edit a student"},
        {"type": "steps", "items": [
            {"fr": "Cliquez __Nouvel élève__ (ou __Modifier__ sur une fiche ouverte). Le formulaire occupe toute "
                   "la page et se referme par la flèche en haut à gauche.",
             "en": "Click __New student__ (or __Edit__ on an open record). The form takes the whole page and closes "
                   "with the arrow at the top left.",
             "img": "33-eleves-formulaire",
             "caption": {"fr": "Bloc Identité : nom, prénom, sexe, naissance, NIU et case « Redouble ».",
                         "en": "Identity block: name, first name, sex, birth, NIU and the “Repeating” box."}},
            {"fr": "**Identité** — nom et prénom sont obligatoires. Le NIU (identifiant unique national) est "
                   "facultatif. Cochez __Redouble cette année__ le cas échéant.",
             "en": "**Identity** — last and first name are mandatory. The NIU (national unique ID) is optional. "
                   "Tick __Repeating this year__ if relevant."},
            {"fr": "**Scolarité** — choisissez la classe dans la liste déroulante. Le sous-système et le niveau en "
                   "sont déduits : il n'y a pas de saisie libre possible, donc pas de classe fantôme. "
                   "« — Non affecté — » reste valable pour une préinscription.",
             "en": "**Schooling** — pick the class from the drop-down. Sub-system and level are derived from it: no "
                   "free text, hence no ghost classes. “— Unassigned —” is fine for a pre-registration."},
            {"fr": "**Famille / tuteur** — trois blocs séparés (père, mère, tuteur) avec nom, téléphone et e-mail. "
                   "Le tuteur porte en plus un champ « lien / relation ».",
             "en": "**Family / guardian** — three separate blocks (father, mother, guardian) with name, phone and "
                   "e-mail. The guardian also has a “relation” field.",
             "img": "34-eleves-formulaire-famille",
             "caption": {"fr": "Coordonnées familiales : la ligne la plus complète devient le contact principal.",
                         "en": "Family contacts: the most complete line becomes the main contact."}},
            {"fr": "__Enregistrer__. La fiche créée est immédiatement sélectionnée dans la liste.",
             "en": "__Save__. The new record is immediately selected in the list."},
        ]},
        {"type": "note", "tone": "warn", "fr":
            "La suppression d'un élève (__Supprimer__ sur la fiche, avec confirmation) retire l'élève **et ses "
            "données associées** du registre. Les paiements déjà encaissés restent visibles en finance, mais sans "
            "nom d'élève.",
         "en":
            "Deleting a student (__Delete__ on the record, with confirmation) removes the student **and their "
            "related data** from the register. Payments already collected stay visible in Finance, but without a "
            "student name."},

        {"type": "h", "fr": "4.4 Créer un compte parent", "en": "4.4 Create a parent account"},
        {"type": "p", "fr":
            "C'est ce compte qui donne accès au **portail parent** (chapitre 19). Il se crée depuis la fiche de "
            "l'élève, pas depuis le module Personnel.",
         "en":
            "This account gives access to the **parent portal** (chapter 19). It is created from the student record, "
            "not from the Staff module."},
        {"type": "steps", "items": [
            {"fr": "Sur la fiche de l'élève, section __Comptes parents__, cliquez __Ajouter__.",
             "en": "On the student record, in __Parent accounts__, click __Add__.",
             "img": "32-eleves-compte-parent",
             "caption": {"fr": "Création du compte : nom affiché, identifiant et mot de passe.",
                         "en": "Account creation: display name, username and password."}},
            {"fr": "Saisissez le **nom complet du parent**, un **identifiant** de connexion et un **mot de passe**. "
                   "Le nom est prérempli avec le contact principal de l'élève.",
             "en": "Enter the **parent's full name**, a **username** and a **password**. The name is pre-filled from "
                   "the student's main contact."},
            {"fr": "Pour une **fratrie**, réutilisez le **même identifiant** sur la fiche du deuxième enfant : le "
                   "compte existant est simplement rattaché, sans mot de passe à ressaisir. La liste affiche alors "
                   "« 2 enfants ».",
             "en": "For **siblings**, reuse the **same username** on the second child's record: the existing account "
                   "is simply linked, with no password to re-enter. The list then shows “2 children”."},
            {"fr": "L'icône corbeille **détache** le parent de cet élève (le compte n'est pas supprimé s'il reste "
                   "rattaché à d'autres enfants).",
             "en": "The bin icon **unlinks** the parent from this student (the account survives if it is still "
                   "linked to other children)."},
        ]},

        {"type": "h", "fr": "4.5 Importer un registre complet", "en": "4.5 Import a full register"},
        {"type": "p", "fr":
            "L'import traite une classe à la fois et accepte le registre officiel tel quel : Excel (.xls, .xlsx), "
            "CSV, ou simple copier-coller depuis un tableur.",
         "en":
            "The import handles one class at a time and accepts the official register as-is: Excel (.xls, .xlsx), "
            "CSV, or a plain copy-paste from a spreadsheet."},
        {"type": "steps", "items": [
            {"fr": "Cliquez __Importer__. Choisissez la **classe cible** : une classe existante (filtrable par "
                   "système, niveau et série) ou __Nouvelle classe__, qui la crée à la volée avec sa section.",
             "en": "Click __Import__. Choose the **target class**: an existing one (filterable by system, level and "
                   "grade) or __New class__, which creates it on the fly together with its section.",
             "img": "35-eleves-import",
             "caption": {"fr": "Écran d'import : classe cible en haut, zone de données au milieu.",
                         "en": "Import screen: target class at the top, data area in the middle."}},
            {"fr": "Alimentez la zone de données : bouton __Fichier Excel / CSV__, collage direct, ou __Exemple__ "
                   "pour voir le format attendu. __Modèle CSV__ télécharge un gabarit vierge.",
             "en": "Feed the data area: __Excel / CSV file__ button, direct paste, or __Sample__ to see the expected "
                   "format. __CSV template__ downloads a blank template."},
            {"fr": "Vérifiez l'**aperçu** : chaque ligne reçoit une coche verte (importable) ou une croix rouge "
                   "(nom manquant). Le compteur indique « valides / total ».",
             "en": "Check the **preview**: each row gets a green tick (importable) or a red cross (missing name). "
                   "The counter shows “valid / total”.",
             "img": "36-eleves-import-apercu",
             "caption": {"fr": "Aperçu avant import — les lignes en rouge seront ignorées.",
                         "en": "Preview before import — red rows will be skipped."}},
            {"fr": "Cliquez __Importer N élève(s)__. Le rapport final indique le nombre de créations et, "
                   "ligne par ligne, le motif des lignes ignorées.",
             "en": "Click __Import N student(s)__. The final report shows how many were created and, row by row, why "
                   "the others were skipped."},
        ]},
        {"type": "table",
         "caption": {"fr": "Colonnes reconnues (l'en-tête est détecté automatiquement ; l'ordre importe peu).",
                     "en": "Recognised columns (the header row is auto-detected; order does not matter)."},
         "head": {"fr": ["Colonne", "Formats acceptés"], "en": ["Column", "Accepted formats"]},
         "rows": {"fr": [
             ["NIU", "Texte libre — identifiant unique national."],
             ["Nom et Prénom", "Nom complet dans une seule colonne, ou colonnes « Nom » et « Prénom » séparées."],
             ["Sexe", "M, F, Masculin, Féminin, Male, Female, garçon, fille."],
             ["Date de naissance", "`06 janvier 2011`, `2011-01-06` ou `06/01/2011`."],
             ["Lieu de naissance", "Texte libre."],
             ["Redouble", "OUI / NON, YES / NO, 1 / 0, VRAI / FAUX."],
             ["Parent / Téléphone", "Facultatifs — nom et numéro du responsable."],
         ], "en": [
             ["NIU", "Free text — national unique ID."],
             ["Full name", "Full name in one column, or separate “Last name” / “First name” columns."],
             ["Sex", "M, F, Masculin, Féminin, Male, Female, boy, girl."],
             ["Date of birth", "`06 janvier 2011`, `2011-01-06` or `06/01/2011`."],
             ["Birthplace", "Free text."],
             ["Repeats", "OUI / NON, YES / NO, 1 / 0, TRUE / FALSE."],
             ["Parent / Phone", "Optional — name and number of the responsible adult."],
         ]}},
        {"type": "note", "tone": "tip", "fr":
            "Si aucun en-tête n'est reconnu, le système suppose l'ordre du registre officiel : NIU, Nom et Prénom, "
            "Sexe, Date de naissance, Lieu de naissance, Redouble. Vérifiez toujours l'aperçu avant de valider.",
         "en":
            "If no header is recognised, the system assumes the official register order: NIU, Full name, Sex, Date "
            "of birth, Birthplace, Repeats. Always check the preview before confirming."},
        {"type": "check", "items": [
            {"fr": "Créer un élève avec père et mère renseignés, puis l'affecter à une classe.",
             "en": "Create a student with father and mother filled in, then assign a class."},
            {"fr": "Créer un compte parent et me connecter avec sur le portail.",
             "en": "Create a parent account and sign in with it on the portal."},
            {"fr": "Rattacher un deuxième enfant au même identifiant parent.",
             "en": "Link a second child to the same parent username."},
            {"fr": "Importer 3 lignes dans une classe et lire le rapport d'import.",
             "en": "Import 3 rows into a class and read the import report."},
            {"fr": "Exporter la liste filtrée d'une classe.", "en": "Export the filtered list of one class."},
        ]},
    ],
}


CH_PERSONNEL = {
    "id": "personnel",
    "num": "5",
    "title": {"fr": "Personnel & ressources humaines", "en": "Staff & human resources"},
    "subtitle": {
        "fr": "Annuaire, comptes de connexion, import, portail d'inscription, départements, congés et masse salariale.",
        "en": "Directory, login accounts, import, registration portal, departments, leave and payroll.",
    },
    "who": {"fr": "Direction et administration (droit __Personnel : Complet__).",
            "en": "Management and administration (__Staff: Write__)."},
    "blocks": [
        {"type": "p", "fr":
            "Le module s'organise en cinq onglets : __Annuaire__, __Candidatures__, __Départements__, __Congés__ "
            "et __Masse salariale__. Quatre indicateurs restent affichés en permanence : effectif, permanents, "
            "vacataires et masse salariale mensuelle.",
         "en":
            "The module has five tabs: __Directory__, __Applications__, __Departments__, __Leave__ and __Payroll__. "
            "Four indicators stay visible throughout: headcount, permanent staff, contractors and monthly payroll."},

        {"type": "h", "fr": "5.1 L'annuaire", "en": "5.1 The directory"},
        {"type": "figure", "img": "40-personnel-annuaire",
         "caption": {"fr": "Annuaire : recherche par nom ou code, filtre par rôle, tableau dense.",
                     "en": "Directory: search by name or code, filter by role, dense table."}},
        {"type": "p", "fr":
            "Les badges **P** (principal) et **PP** (professeur principal) signalent les responsabilités d'un coup "
            "d'œil. La colonne Rémunération affiche le salaire mensuel pour un permanent, le taux horaire pour un "
            "vacataire.",
         "en":
            "The **P** (principal) and **PP** (form teacher) badges flag responsibilities at a glance. The "
            "Compensation column shows the monthly salary for permanent staff, the hourly rate for contractors."},
        {"type": "figure", "img": "41-personnel-fiche",
         "caption": {"fr": "Fiche employé : contact, compte de connexion et rémunération.",
                     "en": "Employee record: contact, login account and compensation."}},

        {"type": "h", "fr": "5.2 Créer un employé", "en": "5.2 Create an employee"},
        {"type": "steps", "items": [
            {"fr": "__Nouvel employé__ ouvre le formulaire pleine page. **Identité & contact** : nom (obligatoire), "
                   "sexe, e-mail, téléphone.",
             "en": "__New employee__ opens the full-page form. **Identity & contact**: name (mandatory), sex, "
                   "e-mail, phone.",
             "img": "42-personnel-formulaire",
             "caption": {"fr": "Identité, contact et option de création du compte de connexion.",
                         "en": "Identity, contact and the login-account option."}},
            {"fr": "**Rôles** — cliquez autant de rôles que nécessaire ; ils proviennent du catalogue des rôles "
                   "(chapitre 2). Si vous cochez « professeur principal », un champ __Classe__ apparaît.",
             "en": "**Roles** — click as many roles as needed; they come from the role catalogue (chapter 2). If you "
                   "select “form teacher”, a __Form class__ field appears."},
            {"fr": "**Département** — rattachement facultatif, à créer au préalable dans l'onglet Départements.",
             "en": "**Department** — optional, to be created beforehand in the Departments tab."},
            {"fr": "**Contrat & rémunération** — choisissez __Permanent__ (salaire mensuel) ou __Vacataire__ "
                   "(taux horaire). Le champ affiché s'adapte au choix.",
             "en": "**Contract & compensation** — choose __Permanent__ (monthly salary) or __Contractor__ (hourly "
                   "rate). The displayed field follows your choice.",
             "img": "43-personnel-contrat",
             "caption": {"fr": "Le type de contrat détermine le mode de rémunération.",
                         "en": "The contract type determines the pay mode."}},
            {"fr": "Cochez éventuellement __Créer un compte de connexion__ — l'option ne s'active que si un e-mail "
                   "est renseigné —, puis __Enregistrer__.",
             "en": "Optionally tick __Create a login account__ — enabled only when an e-mail is present — then __Save__."},
        ]},

        {"type": "h", "fr": "5.3 Comptes de connexion et réinitialisation", "en": "5.3 Login accounts and reset"},
        {"type": "list", "items": [
            {"fr": "La fiche indique si l'employé possède un compte et affiche son **identifiant**. Le mot de passe "
                   "n'est jamais affiché.",
             "en": "The record states whether the employee has an account and shows the **username**. The password "
                   "is never displayed."},
            {"fr": "__Créer le compte__ (employé sans compte) génère les identifiants et les envoie par e-mail.",
             "en": "__Create account__ (employee without one) generates the credentials and e-mails them."},
            {"fr": "__Réinitialiser__ (employé avec compte) génère un nouveau mot de passe et l'envoie. C'est la "
                   "procédure à suivre pour un parent ou un employé sans e-mail qui a perdu son accès.",
             "en": "__Reset__ (employee with an account) generates a new password and sends it. This is the route "
                   "for a parent or an employee without an e-mail who lost access."},
            {"fr": "Le message de retour précise si l'e-mail est effectivement parti — utile pour diagnostiquer un "
                   "SMTP mal configuré.",
             "en": "The returned message states whether the e-mail actually went out — handy to diagnose a "
                   "misconfigured SMTP."},
        ]},

        {"type": "h", "fr": "5.4 Importer le personnel", "en": "5.4 Import staff"},
        {"type": "steps", "items": [
            {"fr": "__Importer__ ouvre le même type d'écran que pour les élèves : fichier Excel/CSV, collage ou "
                   "__Exemple__.",
             "en": "__Import__ opens the same kind of screen as for students: Excel/CSV file, paste or __Sample__.",
             "img": "44-personnel-import",
             "caption": {"fr": "Zone de données et rappel des colonnes reconnues.",
                         "en": "Data area and reminder of the recognised columns."}},
            {"fr": "Colonnes reconnues : `nom`, `sexe`, `type` (Permanent/Vacataire), `email`, `telephone`, "
                   "`roles` (séparés par `|`), `classe`, `departement`, `salaire_mensuel`, `taux_horaire`. "
                   "Des alias sont acceptés : *surveillant* → prefect, *caissier* → econome.",
             "en": "Recognised columns: `name`, `sex`, `type` (Permanent/Contractor), `email`, `phone`, `roles` "
                   "(separated by `|`), `class`, `department`, `monthly_salary`, `hourly_rate`. Aliases are "
                   "accepted: *surveillant* → prefect, *cashier* → econome."},
            {"fr": "L'option __Créer les comptes de connexion__ est **désactivée par défaut** pour un import massif : "
                   "activez-la seulement si le SMTP est prêt et que chaque ligne porte un e-mail.",
             "en": "The __Create login accounts__ option is **off by default** for a bulk import: switch it on only "
                   "when SMTP is ready and every row carries an e-mail."},
            {"fr": "Vérifiez l'aperçu, puis __Importer N employé(s)__.",
             "en": "Check the preview, then __Import N employee(s)__.",
             "img": "45-personnel-import-apercu",
             "caption": {"fr": "Aperçu de l'import du personnel avec validation ligne à ligne.",
                         "en": "Staff import preview with row-by-row validation."}},
        ]},

        {"type": "h", "fr": "5.5 Portail d'inscription et candidatures", "en": "5.5 Registration portal and applications"},
        {"type": "p", "fr":
            "Plutôt que de saisir cinquante fiches à la rentrée, ouvrez un **lien public temporaire** : chaque "
            "membre du personnel remplit lui-même ses informations, l'administration valide ensuite.",
         "en":
            "Rather than typing fifty records at the start of the year, open a **temporary public link**: each staff "
            "member fills in their own details, and the administration validates afterwards."},
        {"type": "steps", "items": [
            {"fr": "Onglet __Candidatures__ : cochez __Portail activé__. Un lien apparaît ; __Copier le lien__ le "
                   "place dans le presse-papiers.",
             "en": "__Applications__ tab: tick __Portal enabled__. A link appears; __Copy link__ puts it on the clipboard.",
             "img": "46-personnel-portail-candidatures",
             "caption": {"fr": "Activation du portail, lien public et file des candidatures.",
                         "en": "Portal switch, public link and application queue."}},
            {"fr": "Diffusez le lien (WhatsApp, e-mail, affichage). Le candidat remplit un formulaire simple : "
                   "nom, sexe, type de contrat, e-mail, téléphone, classe, département et rôles souhaités.",
             "en": "Share the link (WhatsApp, e-mail, notice board). The applicant fills a simple form: name, sex, "
                   "contract type, e-mail, phone, class, department and desired roles.",
             "img": "170-portail-personnel",
             "caption": {"fr": "Le formulaire public, vu par le candidat — aucune connexion requise.",
                         "en": "The public form as seen by the applicant — no sign-in required."}},
            {"fr": "Dans la file des candidatures, filtrez par statut (En attente, Acceptées, Finalisées, Refusées). "
                   "__Accepter__ ou __Refuser__ (avec motif) une demande en attente.",
             "en": "In the queue, filter by status (Pending, Accepted, Finalized, Rejected). __Accept__ or __Reject__ "
                   "(with a reason) a pending request."},
            {"fr": "Sur une candidature acceptée, __Finaliser__ ouvre la fenêtre de configuration : type de contrat, "
                   "département, salaire ou taux horaire, classe de PP, rôles définitifs et création éventuelle du "
                   "compte. C'est cette étape qui crée réellement la fiche employé.",
             "en": "On an accepted application, __Finalize__ opens the configuration dialog: contract type, "
                   "department, salary or hourly rate, form class, final roles and optional account creation. This "
                   "step is what actually creates the employee record."},
            {"fr": "Une fois le recrutement terminé, **désactivez le portail** ou utilisez __Régénérer le lien__ "
                   "pour invalider l'ancienne adresse.",
             "en": "Once onboarding is over, **disable the portal** or use __Regenerate link__ to invalidate the old address."},
        ]},
        {"type": "note", "tone": "warn", "fr":
            "Le lien contient un jeton secret : toute personne qui l'obtient peut déposer une candidature. Il ne "
            "donne toutefois **aucun accès aux données** de l'école et ne crée aucun compte tant qu'un "
            "administrateur n'a pas finalisé.",
         "en":
            "The link carries a secret token: anyone holding it can submit an application. It grants **no access to "
            "school data** and creates no account until an administrator finalizes it."},

        {"type": "h", "fr": "5.6 Départements, congés, masse salariale", "en": "5.6 Departments, leave, payroll"},
        {"type": "steps", "items": [
            {"fr": "__Départements__ — créez les entités de l'école (Sciences, Lettres, Administration…), désignez "
                   "un responsable et suivez l'effectif de chacune.",
             "en": "__Departments__ — create the school's units (Sciences, Languages, Administration…), name a head "
                   "and track headcount.",
             "img": "47-personnel-departements",
             "caption": {"fr": "Départements avec responsable et effectif.", "en": "Departments with head and headcount."}},
            {"fr": "__Congés__ — __Nouvelle demande__ : employé, type (annuel, maladie, maternité, sans solde, "
                   "autre), dates et motif. Le nombre de jours est calculé automatiquement.",
             "en": "__Leave__ — __New request__: employee, type (annual, sick, maternity, unpaid, other), dates and "
                   "reason. The number of days is computed automatically.",
             "img": "48-personnel-conges",
             "caption": {"fr": "File des congés : approuver (✓) ou refuser (✗) une demande en attente.",
                         "en": "Leave queue: approve (✓) or reject (✗) a pending request."}},
            {"fr": "__Masse salariale__ — récapitulatif trié du plus coûteux au moins coûteux, avec le total en "
                   "pied de tableau. Utile avant la préparation du budget.",
             "en": "__Payroll__ — a recap sorted from the most to the least expensive, with the total in the footer. "
                   "Useful when preparing the budget.",
             "img": "49-personnel-masse-salariale",
             "caption": {"fr": "Masse salariale mensuelle, permanents et vacataires confondus.",
                         "en": "Monthly payroll, permanent staff and contractors together."}},
        ]},
        {"type": "check", "items": [
            {"fr": "Créer un enseignant avec deux rôles et un salaire.",
             "en": "Create a teacher with two roles and a salary."},
            {"fr": "Créer son compte de connexion et vérifier le message d'envoi.",
             "en": "Create their login account and check the sending message."},
            {"fr": "Activer le portail, déposer une candidature de test, l'accepter puis la finaliser.",
             "en": "Enable the portal, submit a test application, accept then finalize it."},
            {"fr": "Créer un département et y rattacher deux employés.",
             "en": "Create a department and attach two employees to it."},
            {"fr": "Saisir une demande de congé et l'approuver.", "en": "Record a leave request and approve it."},
        ]},
    ],
}

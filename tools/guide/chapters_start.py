# -*- coding: utf-8 -*-
"""Chapitres 1 et 2 — prise en main, rôles et permissions."""

CH_PRISE_EN_MAIN = {
    "id": "prise-en-main",
    "num": "1",
    "title": {"fr": "Prise en main", "en": "Getting started"},
    "subtitle": {
        "fr": "Se connecter, choisir son parcours, se repérer dans l'écran d'accueil.",
        "en": "Sign in, pick your parcours, find your way around the home screen.",
    },
    "who": {"fr": "Tout le monde — 10 minutes.", "en": "Everyone — 10 minutes."},
    "blocks": [
        {"type": "p", "fr":
            "BBC SMS s'utilise depuis un navigateur (Chrome, Edge, Firefox ou Safari), sur ordinateur "
            "comme sur téléphone. Il n'y a rien à installer : l'adresse de l'école suffit.",
         "en":
            "BBC SMS runs in a browser (Chrome, Edge, Firefox or Safari), on a computer or a phone. "
            "Nothing to install: the school address is all you need."},

        {"type": "h", "fr": "1.1 Se connecter", "en": "1.1 Sign in"},
        {"type": "steps", "items": [
            {"fr": "Ouvrez l'adresse communiquée par l'établissement. L'écran de connexion s'affiche. "
                   "Le sélecteur __FR__ / __EN__ en haut à droite change la langue de l'interface : "
                   "votre choix est mémorisé sur ce poste.",
             "en": "Open the address given by the school. The sign-in screen appears. The __FR__ / __EN__ "
                   "switch at the top right changes the interface language; your choice is remembered on this device.",
             "img": "01-login",
             "caption": {"fr": "Écran de connexion — le panneau de gauche rappelle les grandes fonctions du système.",
                         "en": "Sign-in screen — the left panel recaps the main features of the system."}},
            {"fr": "Saisissez votre **identifiant** et votre **mot de passe**. L'icône en forme d'œil affiche "
                   "le mot de passe en clair pour vérifier une faute de frappe.",
             "en": "Enter your **username** and **password**. The eye icon reveals the password so you can check a typo.",
             "img": "03-login-rempli",
             "caption": {"fr": "Identifiants saisis, prêt à valider.", "en": "Credentials filled in, ready to submit."}},
            {"fr": "Cliquez sur __Se connecter__. Le personnel arrive sur le choix du parcours (§1.2), "
                   "un compte parent arrive directement sur son portail (chapitre 19).",
             "en": "Click __Sign in__. Staff land on the parcours picker (§1.2); a parent account goes straight "
                   "to the parent portal (chapter 19)."},
        ]},
        {"type": "note", "tone": "info", "fr":
            "La session reste ouverte 8 heures et se prolonge silencieusement pendant que vous travaillez. "
            "Après une longue inactivité, l'application vous ramène à l'écran de connexion avec le message "
            "« Votre session a expiré ».",
         "en":
            "A session lasts 8 hours and is silently renewed while you work. After a long idle period the app "
            "returns you to the sign-in screen with “Your session has expired”."},

        {"type": "h", "fr": "1.2 Mot de passe oublié", "en": "1.2 Forgotten password"},
        {"type": "steps", "items": [
            {"fr": "Sur l'écran de connexion, cliquez sur __Oublié ?__ à droite du champ mot de passe.",
             "en": "On the sign-in screen click __Forgot?__ next to the password field.",
             "img": "02-login-mot-de-passe-oublie",
             "caption": {"fr": "Demande de réinitialisation : seul l'identifiant est demandé.",
                         "en": "Reset request: only the username is required."}},
            {"fr": "Saisissez votre identifiant puis __Envoyer un nouveau mot de passe__. Si une adresse e-mail "
                   "est enregistrée sur votre **fiche personnel**, un mot de passe temporaire de 10 caractères y "
                   "est envoyé.",
             "en": "Enter your username then __Send a new password__. If an e-mail address is on your **staff "
                   "record**, a 10-character temporary password is sent to it."},
            {"fr": "Connectez-vous avec ce mot de passe temporaire. Demandez ensuite à l'administration de le "
                   "remplacer par un mot de passe définitif (__Personnel → fiche → Réinitialiser__, §5.3).",
             "en": "Sign in with that temporary password, then ask the administration to replace it with a "
                   "permanent one (__Staff → record → Reset__, §5.3)."},
        ]},
        {"type": "note", "tone": "warn", "fr":
            "Le message de confirmation est volontairement identique que le compte existe ou non : c'est une "
            "protection contre la découverte d'identifiants. Il ne signifie donc pas à lui seul qu'un e-mail est "
            "parti.",
         "en":
            "The confirmation message is deliberately the same whether or not the account exists — this prevents "
            "account enumeration. On its own it therefore does not mean an e-mail was actually sent."},
        {"type": "table",
         "caption": {"fr": "Qui peut réellement utiliser cette fonction, et ce qui se passe dans chaque cas.",
                     "en": "Who can actually use this feature, and what happens in each case."},
         "head": {"fr": ["Situation", "Résultat"], "en": ["Situation", "Outcome"]},
         "rows": {"fr": [
             ["Personnel **avec** e-mail sur sa fiche", "Mot de passe temporaire envoyé à cette adresse ; l'ancien cesse de fonctionner."],
             ["Personnel **sans** e-mail", "Rien n'est modifié. Passez par l'administrateur : __Personnel → fiche → Réinitialiser__ (§5.3)."],
             ["Compte **parent**", "Non couvert : un compte parent n'est pas rattaché à une fiche personnel. L'administrateur recrée le mot de passe depuis la fiche de l'élève (§4.4)."],
             ["Messagerie **non configurée ou en panne**", "**Votre mot de passe actuel reste valable** — rien n'est changé tant que l'e-mail n'est pas réellement parti. Contactez l'administration."],
         ], "en": [
             ["Staff **with** an e-mail on file", "A temporary password is sent to that address; the old one stops working."],
             ["Staff **without** an e-mail", "Nothing changes. Go through the administrator: __Staff → record → Reset__ (§5.3)."],
             ["**Parent** account", "Not covered: a parent account is not linked to a staff record. The administrator recreates the password from the student record (§4.4)."],
             ["E-mail **not configured or failing**", "**Your current password stays valid** — nothing changes unless the e-mail actually went out. Contact the administration."],
         ]}},
        {"type": "note", "tone": "info", "fr":
            "Côté administration : cette fonction dépend entièrement du **SMTP** de l'établissement "
            "(__Paramètres → Messagerie__, §3.7). Testez l'envoi après toute modification — sans messagerie "
            "opérationnelle, la réinitialisation en libre-service reste sans effet et tout passe par vous.",
         "en":
            "For administrators: this feature depends entirely on the school **SMTP** settings "
            "(__Settings → E-mail__, §3.7). Test sending after any change — without working e-mail, self-service "
            "reset has no effect and everything goes through you."},

        {"type": "h", "fr": "1.3 Choisir un parcours", "en": "1.3 Choose a parcours"},
        {"type": "p", "fr":
            "Un **parcours** est la combinaison d'un niveau (Maternelle, Primaire, Secondaire) et d'un "
            "sous-système (Francophone, Anglophone). C'est le concept central du système : il **filtre** partout "
            "les sections, classes, élèves et bulletins. Une classe créée en « Primaire FR » n'apparaît pas si "
            "vous travaillez en « Secondaire EN ».",
         "en":
            "A **parcours** is a level (Kindergarten, Primary, Secondary) combined with a sub-system (Francophone, "
            "English). It is the core concept of the system: it **filters** sections, classes, students and report "
            "cards everywhere. A class created in “Primary FR” is invisible while you work in “Secondary EN”."},
        {"type": "steps", "items": [
            {"fr": "Choisissez le niveau. Vous ne voyez que les niveaux autorisés pour votre compte.",
             "en": "Pick the level. You only see the levels your account is allowed to use.",
             "img": "04-parcours-niveau",
             "caption": {"fr": "Étape 1 — le niveau. Les administrateurs disposent en plus de « Tous les parcours ».",
                         "en": "Step 1 — the level. Administrators also get “All parcours”."}},
            {"fr": "Choisissez la section : __Francophone__ ou __Anglophone__. Si un seul sous-système vous est "
                   "autorisé, cette étape est sautée automatiquement.",
             "en": "Pick the section: __Francophone__ or __English__. If only one sub-system is allowed for you, "
                   "this step is skipped automatically.",
             "img": "05-parcours-section",
             "caption": {"fr": "Étape 2 — la section. Le bouton « Retour aux parcours » revient au niveau.",
                         "en": "Step 2 — the section. “Back to parcours” returns to the level step."}},
            {"fr": "Les administrateurs peuvent choisir __Tous les parcours__ pour voir l'école entière : "
                   "aucune donnée n'est masquée, et les listes affichent des filtres Système / Niveau "
                   "supplémentaires.",
             "en": "Administrators can choose __All parcours__ to see the whole school: nothing is hidden and lists "
                   "gain extra System / Level filters."},
        ]},
        {"type": "note", "tone": "tip", "fr":
            "Le parcours actif est affiché en permanence dans la barre supérieure. Cliquez dessus à tout moment "
            "pour en changer — c'est le premier réflexe quand des données semblent « avoir disparu ».",
         "en":
            "The active parcours is always shown in the top bar. Click it at any time to switch — that is the first "
            "thing to check when data appears to have “disappeared”."},

        {"type": "h", "fr": "1.4 Se repérer dans l'écran d'accueil", "en": "1.4 Find your way on the home screen"},
        {"type": "figure", "img": "06-accueil-applications",
         "caption": {"fr": "L'accueil : barre supérieure, menu latéral et indicateurs du jour.",
                     "en": "The home screen: top bar, side menu and today's indicators."}},
        {"type": "table",
         "head": {"fr": ["Zone", "Rôle"], "en": ["Area", "What it does"]},
         "rows": {"fr": [
             ["Barre supérieure", "Logo (retour à l'accueil), parcours actif, langue FR/EN, lien __Aide__ (ce guide), votre nom et __Se déconnecter__."],
             ["Bouton ☰", "Replie le menu latéral en icônes pour gagner de la place ; sur téléphone, il ouvre le menu."],
             ["Menu latéral", "Tous vos modules, regroupés en quatre pôles : Communauté, Pédagogie, Opérations, Pilotage."],
             ["Bandeau d'accueil", "Salutation, date du jour et raccourci vers le tableau de bord."],
             ["Indicateurs", "Effectif, revenus sur 30 jours, taux de présence et solde — uniquement ceux que vos droits autorisent."],
             ["Reprendre", "Les quatre derniers modules ouverts, pour y revenir en un clic."],
             ["Tous les modules", "Le catalogue complet, avec une courte description sous chaque module."],
         ], "en": [
             ["Top bar", "Logo (back home), active parcours, FR/EN language, __Help__ link (this guide), your name and __Sign out__."],
             ["☰ button", "Collapses the side menu to icons to gain room; on a phone it opens the menu."],
             ["Side menu", "All your modules, grouped into four areas: Community, Education, Operations, Steering."],
             ["Welcome banner", "Greeting, today's date and a shortcut to the dashboard."],
             ["Indicators", "Enrolment, 30-day revenue, attendance rate and balance — only those your rights allow."],
             ["Resume", "The last four modules you opened, one click away."],
             ["All modules", "The full catalogue, with a short description under each module."],
         ]}},
        {"type": "figure", "img": "07-accueil-modules",
         "caption": {"fr": "Le catalogue complet des modules, tel que le voit un compte principal.",
                     "en": "The full module catalogue as seen by a principal account."}},
        {"type": "note", "tone": "info", "fr":
            "Vous ne voyez que les modules autorisés par votre rôle. Un module absent du menu n'est pas un bug : "
            "c'est la matrice des permissions (chapitre 2) qui décide.",
         "en":
            "You only see the modules your role allows. A missing module is not a bug: the permission matrix "
            "(chapter 2) decides."},

        {"type": "h", "fr": "1.5 Quitter proprement", "en": "1.5 Sign out properly"},
        {"type": "list", "items": [
            {"fr": "__Se déconnecter__ (barre supérieure) ferme la session et efface les jetons de ce navigateur.",
             "en": "__Sign out__ (top bar) ends the session and clears the tokens from this browser."},
            {"fr": "Sur un poste partagé — secrétariat, salle des professeurs — déconnectez-vous systématiquement.",
             "en": "On a shared computer — front office, staff room — always sign out."},
        ]},
        {"type": "check", "items": [
            {"fr": "Me connecter et changer la langue FR ↔ EN.", "en": "Sign in and switch FR ↔ EN."},
            {"fr": "Choisir un parcours, puis en changer depuis la barre supérieure.",
             "en": "Pick a parcours, then switch it from the top bar."},
            {"fr": "Retrouver un module depuis le menu latéral et depuis le catalogue.",
             "en": "Find a module from the side menu and from the catalogue."},
            {"fr": "Ouvrir le guide via le lien __Aide__.", "en": "Open the guide from the __Help__ link."},
            {"fr": "Me déconnecter.", "en": "Sign out."},
        ]},
    ],
}


CH_ROLES = {
    "id": "roles-permissions",
    "num": "2",
    "title": {"fr": "Rôles, permissions et périmètre", "en": "Roles, permissions and scope"},
    "subtitle": {
        "fr": "Qui voit quoi, et pourquoi deux comptes n'ont pas le même menu.",
        "en": "Who sees what, and why two accounts do not get the same menu.",
    },
    "who": {"fr": "Direction et administrateurs — à lire avant de créer des comptes.",
            "en": "Management and administrators — read before creating accounts."},
    "blocks": [
        {"type": "p", "fr":
            "L'accès repose sur trois mécanismes indépendants qui se cumulent : le **rôle** (ce que vous avez le "
            "droit de faire), le **parcours** (les données que vous voyez) et, pour les parents, une restriction "
            "supplémentaire **à leurs seuls enfants**.",
         "en":
            "Access relies on three independent, cumulative mechanisms: the **role** (what you may do), the "
            "**parcours** (which data you see) and, for parents, a further restriction **to their own children only**."},

        {"type": "h", "fr": "2.1 Les trois niveaux d'accès", "en": "2.1 The three access levels"},
        {"type": "table",
         "head": {"fr": ["Niveau", "Ce que l'utilisateur peut faire"], "en": ["Level", "What the user can do"]},
         "rows": {"fr": [
             ["Aucun", "Le module n'apparaît ni dans le menu ni dans le catalogue ; l'URL directe est refusée."],
             ["Lecture", "Le module s'ouvre, les listes et les fiches sont consultables, mais aucun bouton d'écriture n'est affiché."],
             ["Complet", "Création, modification et suppression sont autorisées."],
         ], "en": [
             ["None", "The module appears neither in the menu nor in the catalogue; a direct URL is refused."],
             ["Read", "The module opens, lists and records can be consulted, but no write button is shown."],
             ["Write", "Create, edit and delete are allowed."],
         ]}},
        {"type": "note", "tone": "info", "fr":
            "Les droits sont vérifiés **côté serveur** à chaque appel : masquer un bouton n'est qu'un confort "
            "d'interface, contourner l'écran ne donne aucun accès supplémentaire.",
         "en":
            "Rights are enforced **on the server** for every call: hiding a button is only a UI convenience — "
            "bypassing the screen grants no extra access."},

        {"type": "h", "fr": "2.2 La matrice des permissions", "en": "2.2 The permission matrix"},
        {"type": "p", "fr":
            "Elle croise **chaque rôle** avec **chaque module**. Elle se trouve dans __Paramètres → Permissions__ "
            "et s'applique immédiatement, sans redémarrage.",
         "en":
            "It crosses **every role** with **every module**. It lives in __Settings → Permissions__ and applies "
            "immediately, with no restart."},
        {"type": "steps", "items": [
            {"fr": "Ouvrez __Paramètres → Permissions__. Chaque colonne est un rôle, chaque ligne un module. "
                   "La légende en haut à droite rappelle le code couleur : gris = Aucun, orange = Lecture, "
                   "vert = Complet.",
             "en": "Open __Settings → Permissions__. Each column is a role, each row a module. The legend at the top "
                   "right recalls the colour code: grey = None, amber = Read, green = Write.",
             "img": "21-parametres-permissions",
             "caption": {"fr": "La matrice complète : rôles intégrés et rôles personnalisés côte à côte.",
                         "en": "The full matrix: built-in and custom roles side by side."}},
            {"fr": "**Cliquez une cellule** pour faire défiler Aucun → Lecture → Complet → Aucun. "
                   "La modification est enregistrée aussitôt ; en cas d'échec réseau, la valeur du serveur est restaurée.",
             "en": "**Click a cell** to cycle None → Read → Write → None. The change is saved immediately; if the "
                   "network fails, the server value is restored."},
            {"fr": "L'utilisateur concerné voit le changement à sa **prochaine connexion** (ses droits voyagent "
                   "dans son jeton de session).",
             "en": "The affected user sees the change at their **next sign-in** (rights travel inside the session token)."},
        ]},
        {"type": "note", "tone": "warn", "fr":
            "La ligne du rôle **parent** est verrouillée : hors du module « parent », les cellules sont grisées et "
            "un message explique le refus. Un compte parent ne peut donc jamais recevoir l'accès aux modules du "
            "personnel, même par erreur de manipulation.",
         "en":
            "The **parent** role row is locked: outside the “parent” module the cells are greyed out and a message "
            "explains the refusal. A parent account can therefore never be granted staff module access, even by mistake."},

        {"type": "h", "fr": "2.3 Rôles intégrés et rôles personnalisés", "en": "2.3 Built-in and custom roles"},
        {"type": "p", "fr":
            "Les rôles **intégrés** (principal, censeur, économe, enseignant, professeur principal, parent…) ne "
            "peuvent pas être supprimés — seul leur libellé FR/EN est modifiable, pour coller au vocabulaire de "
            "l'établissement. Vous pouvez créer autant de rôles **personnalisés** que nécessaire.",
         "en":
            "**Built-in** roles (principal, dean of studies, bursar, teacher, form teacher, parent…) cannot be "
            "deleted — only their FR/EN labels can be changed to match the school's vocabulary. You may create as "
            "many **custom** roles as you need."},
        {"type": "steps", "title": {"fr": "Créer un rôle « Surveillant »", "en": "Create a “Supervisor” role"},
         "items": [
            {"fr": "Ouvrez __Paramètres → Rôles__. La colonne de gauche liste les rôles existants, avec la mention "
                   "« Intégré » ou « Personnalisé » sous chaque libellé.",
             "en": "Open __Settings → Roles__. The left column lists existing roles, each marked “Built-in” or “Custom”.",
             "img": "22-parametres-roles",
             "caption": {"fr": "Rôles existants à gauche, création d'un rôle personnalisé à droite.",
                         "en": "Existing roles on the left, custom role creation on the right."}},
            {"fr": "Dans __Nouveau rôle__, saisissez le libellé français (et anglais si vous le souhaitez), "
                   "puis __Créer__. Le code technique est déduit du libellé.",
             "en": "In __New role__, type the French label (and the English one if you wish), then __Create__. "
                   "The technical code is derived from the label."},
            {"fr": "Retournez dans __Permissions__ et donnez au nouveau rôle les accès voulus — par exemple "
                   "Discipline en **Complet** et Présence en **Lecture**.",
             "en": "Go back to __Permissions__ and grant the new role the access you want — for example Discipline "
                   "in **Write** and Attendance in **Read**."},
            {"fr": "Affectez enfin le rôle à un employé depuis __Personnel__ (chapitre 5). Un employé peut cumuler "
                   "plusieurs rôles ; le premier de la liste sert de rôle principal pour son compte.",
             "en": "Finally assign the role to an employee from __Staff__ (chapter 5). An employee may hold several "
                   "roles; the first one is the primary role of their account."},
        ]},
        {"type": "note", "tone": "tip", "fr":
            "Renommer un rôle intégré est souvent plus simple que d'en créer un : « Censeur » peut devenir "
            "« Directeur des études » sans rien changer aux droits déjà accordés.",
         "en":
            "Renaming a built-in role is often simpler than creating one: “Dean of studies” can become "
            "“Head of academics” without touching the rights already granted."},

        {"type": "h", "fr": "2.4 Le périmètre de données (parcours)", "en": "2.4 Data scope (parcours)"},
        {"type": "p", "fr":
            "Le rôle dit **ce que** l'on peut faire ; le parcours dit **sur quelles données**. Un enseignant du "
            "primaire francophone avec le droit « Académique : Complet » ne verra jamais les classes du secondaire "
            "anglophone. Les parcours autorisés d'un compte sont définis à sa création ; un compte sans restriction "
            "(administrateur) peut basculer sur « Tous les parcours ».",
         "en":
            "The role says **what** you may do; the parcours says **on which data**. A Francophone primary teacher "
            "with “Academic: Write” will never see Anglophone secondary classes. The allowed parcours of an account "
            "are set when it is created; an unrestricted account (administrator) can switch to “All parcours”."},
        {"type": "check", "items": [
            {"fr": "Ouvrir la matrice et lire la ligne d'un module que je gère.",
             "en": "Open the matrix and read the row of a module I manage."},
            {"fr": "Créer un rôle personnalisé et lui donner un droit d'écriture.",
             "en": "Create a custom role and grant it a write permission."},
            {"fr": "Constater qu'une cellule du rôle parent hors « parent » est refusée.",
             "en": "Check that a parent-role cell outside “parent” is refused."},
            {"fr": "Renommer un rôle intégré et retrouver le nouveau libellé dans Personnel.",
             "en": "Rename a built-in role and find the new label in Staff."},
        ]},
    ],
}

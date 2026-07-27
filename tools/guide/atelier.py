# -*- coding: utf-8 -*-
"""
Déroulé de l'atelier pratique BBC SMS — journée complète, tous rôles.

Source unique du support projetable (`frontend/public/guide/atelier.html`),
généré par build-atelier.py. Le guide utilisateur reste la référence de fond ;
ce fichier est le fil conducteur de l'animateur.

Types de diapositives :
  cover      page de garde
  agenda     déroulé de la journée
  section    ouverture d'un module (objectif + plan + minutage)
  demo       démonstration animateur (étapes numérotées, capture facultative)
  exercise   exercice participants (consigne + critères de réussite)
  pitfall    pièges fréquents et réponses
  note       message à faire passer
  pause      respiration
  closing    clôture

Balisage dans les textes : **gras**, `code`, __libellé d'interface__.
"""

DECK = {
    "meta": {
        "title": "Atelier pratique BBC SMS",
        "subtitle": "Prise en main du système de gestion scolaire — journée complète",
        "school": "Bayo Bilingual Complex — Maroua",
        "duration": "8 h 30 → 16 h 00",
        "audience": "Direction · Économat · Enseignants · Vie scolaire · Secrétariat",
    },

    "slides": [

        # ------------------------------------------------------------ ouverture
        {"type": "cover"},

        {"type": "agenda", "title": "Matin", "sub": "08h30 → 12h00",
         "rows": [
             ("08h30", "Accueil, objectifs, comptes de travail", "20 min", ""),
             ("08h50", "1 · Prise en main", "25 min", "tous"),
             ("09h15", "2 · Rôles & permissions", "20 min", "direction"),
             ("09h35", "3 · Paramètres : sections, classes, matières", "35 min", "direction"),
             ("10h10", "Pause", "15 min", ""),
             ("10h25", "4 · Élèves", "30 min", "secrétariat"),
             ("10h55", "5 · Personnel & RH", "25 min", "direction"),
             ("11h20", "6 · Présence", "15 min", "vie scolaire"),
             ("11h35", "7 · Académique : bulletins & PV", "25 min", "enseignants"),
             ("12h00", "Déjeuner", "60 min", ""),
         ]},

        {"type": "agenda", "title": "Après-midi", "sub": "13h00 → 16h00",
         "rows": [
             ("13h00", "8 · Discipline", "15 min", "vie scolaire"),
             ("13h15", "9 · Cahier de textes", "10 min", "enseignants"),
             ("13h25", "10 · Emploi du temps", "20 min", "direction"),
             ("13h45", "11 · Finance & moyens de paiement", "40 min", "économat"),
             ("14h25", "Pause", "10 min", ""),
             ("14h35", "12 · Événements & correspondance", "20 min", "secrétariat"),
             ("14h55", "13 · Fournitures & manuels", "10 min", "économat"),
             ("15h05", "14 · Parcours, santé, documents", "15 min", "secrétariat"),
             ("15h20", "15 · Pilotage", "15 min", "direction"),
             ("15h35", "16 · Portail parent", "15 min", "tous"),
             ("15h50", "Clôture : plan de démarrage", "10 min", "tous"),
         ]},

        {"type": "note", "title": "Comment nous allons travailler",
         "kicker": "Méthode",
         "lines": [
             "Chaque module suit le même rythme : **je montre**, **vous faites**, **on vérifie**.",
             "Vous travaillez sur l'**environnement de démonstration** — aucune donnée réelle n'est touchée.",
             "Une erreur n'a aucune conséquence : c'est le meilleur moment pour en faire.",
             "Le **guide utilisateur** (menu __Aide__) reprend chaque procédure, capture à l'appui : "
             "personne n'a besoin de prendre des notes.",
         ]},

        {"type": "note", "title": "Vos comptes de travail", "kicker": "Avant de commencer",
         "table": {
             "head": ["Identifiant", "Mot de passe", "Rôle", "Ce qu'il permet"],
             "rows": [
                 ["`principal`", "`password`", "Direction", "Tous les modules, sauf Finance en lecture"],
                 ["`econome`", "`password`", "Économat", "Finance en écriture : encaissements, frais, canaux"],
                 ["`parent1`", "`password`", "Parent", "Le portail parent, pour voir l'école côté famille"],
             ]},
         "lines": [
             "Trois postes suffisent par table : un compte direction, un compte économat, un compte parent.",
             "L'adresse de travail est projetée au tableau — **ne la confondez pas avec l'adresse de production**.",
         ]},

        # ------------------------------------------------------- 1. prise en main
        {"type": "section", "num": "1", "title": "Prise en main", "time": "25 min",
         "audience": "Tous",
         "goal": "Se connecter, comprendre le parcours, savoir où l'on est.",
         "plan": ["Démo : connexion et repères (7 min)",
                  "Exercice : votre première connexion (12 min)",
                  "Contrôle et questions (6 min)"]},

        {"type": "demo", "num": "1", "title": "Démonstration — se connecter",
         "img": "01-login",
         "steps": [
             "Ouvrir l'adresse, montrer le sélecteur __FR / EN__ : l'interface parle les deux langues.",
             "Saisir `principal` / `password`, valider.",
             "Insister : le **parcours** n'est pas un décor — il filtre toute l'application.",
         ]},

        {"type": "demo", "num": "1", "title": "Démonstration — le parcours",
         "img": "04-parcours-niveau",
         "steps": [
             "Choisir **Primaire**, puis **Francophone** : montrer que les listes se réduisent.",
             "Revenir au bandeau du haut, basculer sur **Tous les parcours**.",
             "Faire dire à la salle : « je ne vois pas mes élèves » → première question à se poser : **quel parcours ?**",
         ]},

        {"type": "exercise", "num": "1", "title": "À vous — première connexion",
         "duration": "12 min",
         "brief": [
             "Connectez-vous avec le compte qui correspond à votre fonction.",
             "Passez l'interface en anglais, puis revenez au français.",
             "Choisissez le parcours **Secondaire · Francophone**, puis basculez sur **Tous les parcours**.",
             "Ouvrez le guide par le bouton __Aide__ et retrouvez le chapitre de votre module.",
         ],
         "success": [
             "Vous savez changer de langue et de parcours sans quitter votre écran.",
             "Vous savez ouvrir le guide et y retrouver votre chapitre.",
         ]},

        {"type": "pitfall", "num": "1", "title": "Pièges — prise en main",
         "rows": [
             ("« Mes élèves ont disparu »", "Mauvais parcours actif. Cliquer le bandeau en haut, choisir « Tous les parcours »."),
             ("« Je n'ai pas ce module »", "C'est le rôle, pas un bug. On verra la matrice au module 2."),
             ("« Session expirée »", "Inactivité prolongée. Se reconnecter ; rien de saisi n'est perdu."),
         ]},

        # ------------------------------------------------------ 2. rôles & droits
        {"type": "section", "num": "2", "title": "Rôles & permissions", "time": "20 min",
         "audience": "Direction (les autres observent)",
         "goal": "Comprendre qui voit quoi, et savoir l'ajuster sans appeler un technicien.",
         "plan": ["Démo : la matrice (6 min)",
                  "Exercice : créer un rôle et l'attribuer (10 min)",
                  "Contrôle (4 min)"]},

        {"type": "demo", "num": "2", "title": "Démonstration — la matrice des permissions",
         "img": "21-parametres-permissions",
         "steps": [
             "__Paramètres → Permissions__ : une colonne par rôle, une ligne par module.",
             "Cliquer une cellule : **Aucun → Lecture → Complet**. L'effet est immédiat.",
             "Montrer la ligne **parent** : verrouillée hors de son portail — et expliquer pourquoi.",
             "Rappeler : l'utilisateur voit le changement **à sa prochaine connexion**.",
         ]},

        {"type": "exercise", "num": "2", "title": "À vous — un rôle « Surveillant »",
         "duration": "10 min",
         "brief": [
             "Créez le rôle **Surveillant** dans __Paramètres → Rôles__.",
             "Donnez-lui **Discipline : Complet** et **Présence : Lecture**.",
             "Tentez de donner **Académique** au rôle **parent** — observez le refus.",
         ],
         "success": [
             "Le rôle apparaît dans la matrice avec la mention « Personnalisé ».",
             "Les deux cellules sont au bon niveau.",
             "Vous savez expliquer pourquoi le rôle parent est bloqué.",
         ]},

        # -------------------------------------------------------- 3. paramétrage
        {"type": "section", "num": "3", "title": "Paramètres — la fondation", "time": "35 min",
         "audience": "Direction",
         "goal": "Construire le squelette de l'année : sections, classes, matières, horaires, calendrier.",
         "plan": ["Démo : sections → classes → matières (12 min)",
                  "Exercice : monter un parcours complet (18 min)",
                  "Contrôle (5 min)"]},

        {"type": "note", "title": "L'ordre compte", "kicker": "Règle d'or",
         "lines": [
             "**Section → Classe → Élève.** Une classe ne peut pas exister sans section, "
             "un élève ne peut pas être inscrit sans classe.",
             "C'est la cause numéro un des blocages de rentrée : le bouton __Nouvelle classe__ reste gris "
             "tant qu'aucune section n'existe **dans le parcours actif**.",
         ]},

        {"type": "demo", "num": "3", "title": "Démonstration — sections et classes",
         "img": "12-parametres-classes",
         "steps": [
             "__Paramètres → Scolarité → Sections__ : créer « Secondaire francophone ».",
             "Onglet __Classes__ : créer « 6ème A », rattachée à cette section.",
             "Cliquer le compteur d'enseignants : cocher deux professeurs, enregistrer.",
             "Onglet __Matières__ : bouton __Importer les matières standard__ — le catalogue officiel en un clic.",
         ]},

        {"type": "demo", "num": "3", "title": "Démonstration — horaires et calendrier",
         "img": "18-parametres-general",
         "steps": [
             "__Général__ : renseigner l'identité de l'école, la devise, l'autorité de tutelle.",
             "Insister sur **Début des cours** : c'est le **seuil de retard** du pointage.",
             "__Calendrier__ : ajouter un jour férié — aucun retard ne sera compté ce jour-là.",
         ]},

        {"type": "exercise", "num": "3", "title": "À vous — monter un parcours",
         "duration": "18 min",
         "brief": [
             "Créez une **section** dans votre parcours, puis **deux classes** dedans.",
             "Importez les **matières standard** de votre sous-système et changez un coefficient.",
             "Réglez l'**heure de début des cours** à 07h45.",
             "Ajoutez un **jour férié** au calendrier.",
         ],
         "success": [
             "Les deux classes apparaissent avec leur section et un effectif à zéro.",
             "La liste des matières est remplie et un coefficient a été modifié.",
             "Le jour férié figure au calendrier.",
         ]},

        {"type": "pitfall", "num": "3", "title": "Pièges — paramétrage",
         "rows": [
             ("__Nouvelle classe__ est gris", "Aucune section dans ce parcours. Créer la section d'abord."),
             ("La section est créée mais invisible", "Elle appartient à un autre parcours — vérifier le bandeau."),
             ("Les matières n'apparaissent pas", "Le filtre Francophone / Anglophone en haut de l'onglet."),
         ]},

        {"type": "pause", "title": "Pause", "time": "15 min",
         "line": "Au retour : on inscrit des élèves."},

        # -------------------------------------------------------------- 4. élèves
        {"type": "section", "num": "4", "title": "Élèves", "time": "30 min",
         "audience": "Secrétariat (tous suivent)",
         "goal": "Inscrire une classe entière depuis le registre officiel et ouvrir un compte parent.",
         "plan": ["Démo : fiche élève et import (10 min)",
                  "Exercice : importer une classe, créer un compte parent (15 min)",
                  "Contrôle (5 min)"]},

        {"type": "demo", "num": "4", "title": "Démonstration — la fiche élève",
         "img": "31-eleves-fiche",
         "steps": [
             "Cliquer une ligne : la fiche s'ouvre sous le tableau.",
             "Montrer **père / mère / tuteur** et expliquer le **contact principal** déduit automatiquement.",
             "C'est ce numéro qui recevra les SMS de discipline et les notifications.",
         ]},

        {"type": "demo", "num": "4", "title": "Démonstration — importer le registre",
         "img": "36-eleves-import-apercu",
         "steps": [
             "__Importer__ → choisir la classe cible (ou la créer à la volée).",
             "Coller le registre ou déposer le fichier Excel : l'en-tête est reconnu tout seul.",
             "Montrer l'**aperçu** : coche verte = importable, croix rouge = nom manquant.",
             "Importer, lire le **rapport** : créés, ignorés, et le motif de chaque ligne écartée.",
         ]},

        {"type": "exercise", "num": "4", "title": "À vous — inscrire et ouvrir l'accès famille",
         "duration": "15 min",
         "brief": [
             "Importez **cinq élèves** dans l'une de vos classes (utilisez le bouton __Exemple__ si besoin).",
             "Ouvrez la fiche du premier élève et complétez **père + téléphone**.",
             "Créez-lui un **compte parent** : nom, identifiant, mot de passe.",
             "Rattachez un **deuxième enfant** au même identifiant parent.",
         ],
         "success": [
             "Les cinq élèves apparaissent dans la classe choisie.",
             "La fiche affiche un contact principal et un compte parent.",
             "Le compte parent indique « 2 enfants ».",
         ]},

        # ----------------------------------------------------------- 5. personnel
        {"type": "section", "num": "5", "title": "Personnel & RH", "time": "25 min",
         "audience": "Direction",
         "goal": "Créer une fiche employé, ouvrir son compte, et laisser le personnel se déclarer lui-même.",
         "plan": ["Démo : fiche, compte, portail d'inscription (9 min)",
                  "Exercice : recruter en deux temps (12 min)",
                  "Contrôle (4 min)"]},

        {"type": "demo", "num": "5", "title": "Démonstration — fiche et compte",
         "img": "42-personnel-formulaire",
         "steps": [
             "__Nouvel employé__ : identité, e-mail, **rôles multiples**, département, contrat.",
             "Permanent = salaire mensuel ; Vacataire = taux horaire. Le champ s'adapte.",
             "Cocher __Créer un compte de connexion__ : les identifiants partent par e-mail.",
             "Sur une fiche existante : __Réinitialiser__ renvoie un mot de passe.",
         ]},

        {"type": "demo", "num": "5", "title": "Démonstration — le portail d'inscription",
         "img": "46-personnel-portail-candidatures",
         "steps": [
             "Onglet __Candidatures__ : activer le portail, copier le lien.",
             "Montrer le formulaire public : le personnel remplit lui-même sa fiche.",
             "Retour côté administration : **Accepter** puis **Finaliser** — salaire, rôles, compte.",
             "Insister : rien n'est créé tant que l'administration n'a pas finalisé.",
         ]},

        {"type": "exercise", "num": "5", "title": "À vous — recruter en deux temps",
         "duration": "12 min",
         "brief": [
             "Créez un **département** puis un **enseignant** rattaché, avec deux rôles.",
             "Activez le **portail d'inscription** et déposez une candidature de test depuis le lien public.",
             "Acceptez-la, puis **finalisez-la** en fixant un salaire et un rôle.",
         ],
         "success": [
             "L'enseignant figure à l'annuaire avec ses rôles et son département.",
             "La candidature est passée de « En attente » à « Finalisée ».",
             "Une fiche employé a été créée à partir de la candidature.",
         ]},

        # ------------------------------------------------------------ 6. présence
        {"type": "section", "num": "6", "title": "Présence", "time": "15 min",
         "audience": "Vie scolaire",
         "goal": "Lire le tableau du jour, filtrer, remonter dans l'historique.",
         "plan": ["Démo : tableau et journal (6 min)", "Exercice : filtrer et remonter (7 min)", "Contrôle (2 min)"]},

        {"type": "demo", "num": "6", "title": "Démonstration — le tableau du jour",
         "img": "50-presence-tableau",
         "steps": [
             "Les pointages remontent **en direct** : aucune saisie quotidienne.",
             "Le **retard** se calcule sur l'heure de début des cours réglée au module 3.",
             "Week-ends et jours fériés ne produisent jamais de retard.",
             "Le journal se filtre par classe puis par statut.",
         ]},

        {"type": "exercise", "num": "6", "title": "À vous — lire une journée",
         "duration": "7 min",
         "brief": [
             "Relevez le **taux de présence** et le nombre de retards du jour.",
             "Filtrez le journal sur une classe, puis sur « Retards ».",
             "Affichez la journée d'**hier**.",
         ],
         "success": ["Vous savez répondre à « qui était absent en 4ème hier ? » en moins de trente secondes."]},

        # ---------------------------------------------------------- 7. académique
        {"type": "section", "num": "7", "title": "Académique — bulletins & PV", "time": "25 min",
         "audience": "Enseignants et direction",
         "goal": "Éditer un bulletin, le valider, sortir le procès-verbal d'une classe.",
         "plan": ["Démo : bulletin, blocage, PV (10 min)", "Exercice : valider et imprimer (12 min)", "Contrôle (3 min)"]},

        {"type": "demo", "num": "7", "title": "Démonstration — le bulletin",
         "img": "62-academique-bulletin",
         "steps": [
             "Choisir la **classe** et la **séquence**, cliquer un élève.",
             "Lire le bulletin : coefficients, moyenne pondérée, rang, moyenne de classe.",
             "Saisir l'**appréciation générale**, puis __Valider le bulletin__.",
             "__Tous les bulletins de la classe__ : une seule impression pour tout le monde.",
         ]},

        {"type": "demo", "num": "7", "title": "Démonstration — bulletin bloqué",
         "img": "64-academique-bulletin-bloque",
         "steps": [
             "Ouvrir un élève débiteur : bandeau rouge, validation et impression désactivées.",
             "Expliquer le lien direct avec la Finance : le blocage tombe dès le versement enregistré.",
             "Onglet __Procès-verbal__ → __Charger le PV__ : le classement de la classe.",
         ]},

        {"type": "exercise", "num": "7", "title": "À vous — valider un bulletin",
         "duration": "12 min",
         "brief": [
             "Ouvrez le bulletin d'un élève de 4ème, séquence 1.",
             "Rédigez une appréciation et **validez**.",
             "Chargez le **procès-verbal** de la classe et vérifiez que le rang concorde.",
             "Repérez un élève dont le bulletin est **bloqué** et expliquez pourquoi.",
         ],
         "success": [
             "Le bulletin porte le badge « Validé ».",
             "Le PV affiche le même rang que le bulletin.",
             "Vous savez dire ce qu'il faut faire pour débloquer un bulletin.",
         ]},

        {"type": "note", "title": "Ce que le module ne fait pas encore", "kicker": "Honnêteté",
         "lines": [
             "Il n'y a **pas encore d'écran de saisie des notes** : elles sont alimentées par l'intégration technique.",
             "Le module sert à **restituer** : bulletin, procès-verbal, validation, impression.",
             "Pour la maternelle et le primaire, le bulletin **par compétences** est une feuille imprimable conforme, "
             "à remplir à la main.",
         ]},

        {"type": "pause", "title": "Déjeuner", "time": "60 min",
         "line": "Au retour : la vie scolaire, puis l'argent."},

        # ---------------------------------------------------------- 8. discipline
        {"type": "section", "num": "8", "title": "Discipline", "time": "15 min",
         "audience": "Vie scolaire",
         "goal": "Consigner un incident et prévenir la famille dans la foulée.",
         "plan": ["Démo : incident + notification (6 min)", "Exercice (7 min)", "Contrôle (2 min)"]},

        {"type": "demo", "num": "8", "title": "Démonstration — incident et SMS",
         "img": "71-discipline-formulaire",
         "steps": [
             "__Nouvel incident__ : classe → élève ; la fiche s'affiche pour confirmer.",
             "Motif et sanction viennent du **catalogue** réglé dans Paramètres.",
             "Cliquer la **cloche** sur l'incident : le message se pré-remplit avec le nom de l'élève.",
             "Envoyer par SMS : le résultat s'affiche immédiatement, succès ou motif d'échec.",
         ]},

        {"type": "exercise", "num": "8", "title": "À vous — consigner et notifier",
         "duration": "7 min",
         "brief": [
             "Enregistrez un incident avec un motif du catalogue et une sanction.",
             "Notifiez le parent depuis l'incident.",
             "Lisez le message de résultat et interprétez-le.",
         ],
         "success": ["L'incident est dans la liste et vous savez lire le retour d'envoi."]},

        # ------------------------------------------------------ 9. cahier de textes
        {"type": "section", "num": "9", "title": "Cahier de textes", "time": "10 min",
         "audience": "Enseignants",
         "goal": "Tenir le journal de classe et les devoirs, séance après séance.",
         "plan": ["Démo (4 min)", "Exercice (5 min)", "Contrôle (1 min)"]},

        {"type": "demo", "num": "9", "title": "Démonstration — une entrée",
         "img": "81-cahier-textes-formulaire",
         "steps": [
             "Choisir la classe : les entrées sont groupées par jour, la plus récente en haut.",
             "__Nouvelle entrée__ : matière, date, contenu traité.",
             "Ajouter le **devoir** et sa **date de remise** — il apparaît en encadré.",
         ]},

        {"type": "exercise", "num": "9", "title": "À vous — deux séances",
         "duration": "5 min",
         "brief": ["Saisissez deux séances dans votre classe, dont une avec un devoir daté.",
                   "Corrigez ensuite l'une des deux."],
         "success": ["Les deux séances apparaissent au bon jour, le devoir est visible."]},

        # ------------------------------------------------------ 10. emploi du temps
        {"type": "section", "num": "10", "title": "Emploi du temps", "time": "20 min",
         "audience": "Direction",
         "goal": "Construire une grille de classe et repérer les conflits d'enseignant.",
         "plan": ["Démo (7 min)", "Exercice (10 min)", "Contrôle (3 min)"]},

        {"type": "demo", "num": "10", "title": "Démonstration — la grille",
         "img": "101-emploi-du-temps-creneau",
         "steps": [
             "Choisir la classe : six jours, neuf créneaux de 07h30 à 15h30.",
             "Cliquer une case vide : matière, enseignant, salle.",
             "Programmer volontairement le même enseignant ailleurs à la même heure.",
             "Montrer le bandeau orange **Conflits détectés** : un avertissement, pas un blocage.",
         ]},

        {"type": "exercise", "num": "10", "title": "À vous — une matinée complète",
         "duration": "10 min",
         "brief": [
             "Remplissez la matinée du lundi d'une classe (trois créneaux).",
             "Provoquez un **conflit d'enseignant** et lisez l'avertissement.",
             "Supprimez un créneau.",
         ],
         "success": ["Trois créneaux posés, un conflit compris, une case libérée."]},

        # ------------------------------------------------------------ 11. finance
        {"type": "section", "num": "11", "title": "Finance & moyens de paiement", "time": "40 min",
         "audience": "Économat (direction en observation)",
         "goal": "Configurer Orange Money, MoMo et MPGS, définir les frais par classe, encaisser une tranche.",
         "plan": ["Démo : canaux, grille, encaissement (14 min)",
                  "Exercice : la chaîne complète (20 min)",
                  "Contrôle (6 min)"]},

        {"type": "note", "title": "Ce que le système fait — et ne fait pas", "kicker": "À dire d'emblée",
         "lines": [
             "L'application **n'initie aucun débit**. Le parent paie depuis son téléphone ou à la banque.",
             "Elle **enregistre et trace** le versement : canal, montant, tranche, **référence de transaction**.",
             "Le parent, lui, voit dans son espace ce qu'il doit, à quelle date, et par quel moyen payer.",
         ]},

        {"type": "demo", "num": "11", "title": "Démonstration — les moyens de paiement",
         "img": "98-finance-moyens-paiement",
         "steps": [
             "Onglet __Moyens de paiement__ : Espèces, Orange Money, MTN MoMo, carte MPGS, virement.",
             "Trois interrupteurs : **actif**, **visible des parents**, **référence obligatoire**.",
             "__Coordonnées__ : saisir le numéro à créditer, l'intitulé du compte, les instructions.",
             "Ces instructions s'afficheront **telles quelles** dans l'espace du parent.",
         ]},

        {"type": "demo", "num": "11", "title": "Démonstration — les frais par classe",
         "img": "97-finance-grille-classe",
         "steps": [
             "Onglet __Frais__ : une grille par **niveau**, surchargeable par **classe**.",
             "Créer une surcharge : total annuel, puis les tranches — **libellé, montant, échéance**.",
             "Autant de tranches que nécessaire : « Inscription », « Tranche 2 »…",
             "La somme des tranches doit égaler le total : le serveur refuse tout écart.",
         ]},

        {"type": "demo", "num": "11", "title": "Démonstration — encaisser une tranche",
         "img": "91-finance-nouveau-paiement",
         "steps": [
             "__Nouveau paiement__ : classe → élève. La **situation** s'affiche aussitôt.",
             "La première tranche non soldée est présélectionnée, le montant restant pré-rempli.",
             "Choisir **Orange Money** : le champ **référence** devient obligatoire.",
             "Valider sans référence → refus. Avec la référence → reçu numéroté, imprimable.",
         ]},

        {"type": "exercise", "num": "11", "title": "À vous — de la configuration au reçu",
         "duration": "20 min",
         "brief": [
             "Renseignez le **numéro Orange Money** de l'école et rendez le canal visible des parents.",
             "Créez une **surcharge de frais** pour une classe : 180 000 FCFA en quatre tranches datées.",
             "Encaissez la première tranche d'un élève **par Orange Money**, avec une référence.",
             "Essayez d'abord **sans** référence pour voir le refus.",
             "Imprimez le reçu, puis vérifiez l'élève dans l'onglet __Débiteurs__.",
         ],
         "success": [
             "Le canal Orange Money porte un numéro et est visible des parents.",
             "La classe suit sa propre grille (pastille « classe » dans le tableau).",
             "Le reçu porte le canal et la référence ; le solde de l'élève a diminué d'autant.",
         ]},

        {"type": "pitfall", "num": "11", "title": "Pièges — finance",
         "rows": [
             ("Encaissement refusé", "Référence de transaction manquante, ou canal désactivé."),
             ("Montant des frais inattendu", "L'élève suit la grille de sa classe si elle existe, sinon celle de son niveau."),
             ("Le parent ne voit pas comment payer", "Canal non coché « visible des parents », ou coordonnées vides."),
             ("Somme des tranches refusée", "Elle doit être exactement égale au total annuel."),
         ]},

        {"type": "pause", "title": "Pause", "time": "10 min",
         "line": "Au retour : la communication avec les familles."},

        # ------------------------------------------- 12. événements & correspondance
        {"type": "section", "num": "12", "title": "Événements & correspondance", "time": "20 min",
         "audience": "Secrétariat, direction",
         "goal": "Annoncer à un groupe, écrire à une famille, obtenir l'accusé de lecture.",
         "plan": ["Démo (8 min)", "Exercice (10 min)", "Contrôle (2 min)"]},

        {"type": "demo", "num": "12", "title": "Démonstration — annoncer et écrire",
         "img": "111-evenements-formulaire",
         "steps": [
             "__Événements__ : titre, type, date, puis **toute l'école** ou **classes ciblées**.",
             "__Notifier les parents__ : le compteur indique combien de familles ont été touchées.",
             "__Correspondance__ : classe → élève, catégorie, objet, message.",
             "Cocher **accusé de lecture requis** : la note reste « en attente » jusqu'à signature.",
         ]},

        {"type": "exercise", "num": "12", "title": "À vous — informer les familles",
         "duration": "10 min",
         "brief": [
             "Créez une **réunion parents-professeurs** pour toute l'école et notifiez-la.",
             "Créez un **devoir surveillé** ciblé sur deux classes.",
             "Envoyez une **convocation** à un élève, avec accusé de lecture.",
             "Marquez-la comme lue au nom du parent.",
         ],
         "success": ["L'événement porte le badge « Notifié » ; la note affiche « Lu / signé par … »."]},

        # ------------------------------------------------------- 13. fournitures
        {"type": "section", "num": "13", "title": "Fournitures & manuels", "time": "10 min",
         "audience": "Économat, professeurs principaux",
         "goal": "Préparer les listes de rentrée et les publier aux familles.",
         "plan": ["Démo (4 min)", "Exercice (5 min)", "Contrôle (1 min)"]},

        {"type": "demo", "num": "13", "title": "Démonstration — préparer puis publier",
         "img": "131-manuels",
         "steps": [
             "Deux listes par classe : **fournitures** (quantités) et **manuels** (prix, auteur, obligatoire).",
             "Le total du coût des manuels se calcule tout seul.",
             "Tant que la liste est en **brouillon**, les parents ne la voient pas.",
             "__Publier__ : elle apparaît immédiatement dans l'espace famille.",
         ]},

        {"type": "exercise", "num": "13", "title": "À vous — la liste de rentrée",
         "duration": "5 min",
         "brief": ["Composez la liste de fournitures d'une classe (trois articles) et publiez-la.",
                   "Ajoutez deux manuels avec leur prix et vérifiez le total."],
         "success": ["Le bandeau indique « Publié — visible par les parents »."]},

        # --------------------------------------------- 14. parcours / santé / docs
        {"type": "section", "num": "14", "title": "Parcours, santé, documents", "time": "15 min",
         "audience": "Secrétariat, infirmerie",
         "goal": "Tenir le dossier de l'élève au-delà de l'année en cours.",
         "plan": ["Démo (6 min)", "Exercice (7 min)", "Contrôle (2 min)"]},

        {"type": "demo", "num": "14", "title": "Démonstration — le dossier de l'élève",
         "img": "141-sante",
         "steps": [
             "__Parcours__ : une ligne par année — classe, résultat, moyenne, décision du conseil.",
             "__Santé__ : dossier médical, passages à l'infirmerie, activités extrascolaires.",
             "__Documents__ : registre des pièces et décisions d'orientation.",
             "Rappeler que les données de santé sont **confidentielles** : restreindre les droits.",
         ]},

        {"type": "exercise", "num": "14", "title": "À vous — compléter un dossier",
         "duration": "7 min",
         "brief": [
             "Ajoutez une **année antérieure** au parcours d'un élève, avec moyenne et rang.",
             "Complétez son **dossier médical** et enregistrez un passage à l'infirmerie.",
             "Enregistrez une **pièce** au dossier documentaire.",
         ],
         "success": ["La chronologie affiche l'année ajoutée ; les compteurs santé et documents ont bougé."]},

        # ----------------------------------------------------------- 15. pilotage
        {"type": "section", "num": "15", "title": "Pilotage", "time": "15 min",
         "audience": "Direction",
         "goal": "Lire la journée, traiter les alertes, sortir les chiffres de l'école.",
         "plan": ["Démo (6 min)", "Exercice (7 min)", "Contrôle (2 min)"]},

        {"type": "demo", "num": "15", "title": "Démonstration — les trois écrans",
         "img": "151-alertes",
         "steps": [
             "__Tableau de bord__ : la journée en un écran, recomposé selon vos droits.",
             "__Alertes__ : chute de résultats, absences, discipline, impayés — avec une gravité.",
             "__Relancer le scan__ après un import de notes ou une campagne d'encaissement.",
             "__Rapports__ : bilan financier, démographie, présence mensuelle, exportables.",
         ]},

        {"type": "exercise", "num": "15", "title": "À vous — piloter",
         "duration": "7 min",
         "brief": ["Relancez un scan d'alertes et traitez-en une (Vu, puis Résoudre).",
                   "Chargez la **présence mensuelle** du mois en cours et exportez-la."],
         "success": ["L'alerte a quitté la file ; le CSV est téléchargé."]},

        # ------------------------------------------------------- 16. portail parent
        {"type": "section", "num": "16", "title": "Portail parent", "time": "15 min",
         "audience": "Tous — c'est ce que voient les familles",
         "goal": "Voir l'école du point de vue d'une famille, et savoir ce qu'elle peut faire seule.",
         "plan": ["Démo (7 min)", "Exercice (6 min)", "Contrôle (2 min)"]},

        {"type": "demo", "num": "16", "title": "Démonstration — l'espace famille",
         "img": "164-parent-frais",
         "steps": [
             "Se connecter avec `parent1` : ni menu latéral, ni parcours — un espace dédié.",
             "__Frais & paiements__ : frais de la classe, part réglée, **échéancier** tranche par tranche.",
             "**Comment payer** : les numéros Orange Money et MoMo saisis au module 11.",
             "__Notes__, __Fournitures & manuels__, __Boîte à suggestions__.",
             "Saisir à la main une adresse du personnel : accès refusé, retour au portail.",
         ]},

        {"type": "exercise", "num": "16", "title": "À vous — dans la peau d'un parent",
         "duration": "6 min",
         "brief": [
             "Connectez-vous avec le compte parent et ouvrez __Frais & paiements__.",
             "Retrouvez la tranche que vous avez encaissée au module 11 et sa référence.",
             "Basculez d'un enfant à l'autre.",
             "Envoyez un message par la boîte à suggestions.",
         ],
         "success": [
             "Le versement du module 11 apparaît dans « Mes versements ».",
             "Le solde affiché correspond à celui vu côté économat.",
         ]},

        # ------------------------------------------------------------- clôture
        {"type": "note", "title": "Les cinq réflexes à retenir", "kicker": "Synthèse",
         "lines": [
             "**Le parcours d'abord** : une donnée « disparue » est presque toujours un parcours mal choisi.",
             "**Section → classe → élève** : l'ordre de la rentrée ne se contourne pas.",
             "**Le rôle décide de l'écran** : un module absent, c'est la matrice des permissions.",
             "**La grille de frais commande le solde**, le blocage du bulletin et ce que voit le parent.",
             "**Le guide est dans l'application** : bouton __Aide__, chapitre par module, captures à l'appui.",
         ]},

        {"type": "closing", "title": "Et maintenant ?",
         "steps": [
             ("Cette semaine", "Paramétrer l'établissement réel : identité, horaires, calendrier, sections, classes, matières."),
             ("Semaine suivante", "Importer le personnel et les élèves, ouvrir les comptes parents."),
             ("Avant la rentrée", "Grille des frais et moyens de paiement, emplois du temps, listes de fournitures."),
             ("Au quotidien", "Présence, discipline, cahier de textes, encaissements — au fil de l'eau."),
         ],
         "closing_note": "Le guide utilisateur reste accessible à tout moment depuis le bouton __Aide__ "
                         "de l'application. Chaque chapitre se termine par une fiche de test : c'est votre "
                         "auto-évaluation après l'atelier."},
    ],
}

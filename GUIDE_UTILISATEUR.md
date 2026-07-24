# Guide utilisateur — BBC SMS

**Bayo Bilingual Complex — Système de gestion scolaire**
Application bilingue (FR/EN), temps réel, accessible depuis un navigateur web.

> Ce guide s'adresse aux **utilisateurs finaux** (direction, économat, enseignants, parents).
> Pour l'installation et l'exploitation technique, voir [README.md](README.md) et [ARCHITECTURE.md](ARCHITECTURE.md).

---

## 1. Premiers pas

### 1.1 Se connecter
1. Ouvrez l'application dans votre navigateur (l'adresse vous est communiquée par l'établissement, ex. `https://votre-ecole…`).
2. Saisissez votre **identifiant** et votre **mot de passe**, puis validez.
3. Vous arrivez sur la **page d'accueil des applications** (la grille de modules).

> En mode démonstration, trois comptes existent (mot de passe `password`) :
> - **`principal`** — accès à tous les modules (Finance en lecture seule) ;
> - **`econome`** — Finance complète (encaissements, dépenses, débiteurs) ;
> - **`parent1`** — portail parent (suivi de ses enfants).

### 1.2 Changer la langue
Un bouton **FR / EN** dans la barre supérieure bascule instantanément toute l'interface entre français et anglais. Votre choix est conservé.

### 1.3 Se repérer dans l'écran
- **Barre supérieure** : nom de l'établissement, sélecteur de langue, votre profil et le bouton de **déconnexion**.
- **Accueil des applications** : les modules sont regroupés en 4 pôles — **Communauté**, **Pédagogie**, **Opérations**, **Pilotage**. Cliquez sur une tuile pour ouvrir le module.
- **Barre latérale** : une fois dans un module, elle reste affichée pour naviguer d'un module à l'autre sans repasser par l'accueil.

> **Vous ne voyez que les modules autorisés pour votre rôle.** Si une tuile n'apparaît pas, c'est que votre profil n'y a pas accès (voir §8).

### 1.4 Le « temps réel »
Certaines pages se mettent à jour **toutes seules**, sans rafraîchir : c'est notamment le cas du tableau de **Présence**, qui affiche chaque pointage au moment où il a lieu. Laissez simplement l'onglet ouvert.

---

## 2. Les rôles et qui fait quoi

| Rôle | Vocation | Accès typiques |
|---|---|---|
| **Principal / Direction** | Pilotage global | Tous les modules ; Finance en **lecture seule** ; gère la matrice de permissions |
| **Économe** | Gestion financière | Finance complète, frais, débiteurs, dépenses |
| **Enseignant** | Saisie pédagogique | Notes de ses classes, cahier de textes, présence |
| **Professeur principal (form teacher)** | Suivi de classe | Bulletins de sa classe, discipline, dossiers, alertes |
| **Préfet / Discipline** | Vie scolaire | Incidents, sanctions, correspondance parents |
| **Parent** | Suivi de l'enfant | Portail parent : notes, présence, frais, suggestions |

Les droits sont définis **module par module** sur trois niveaux : **aucun accès**, **lecture seule**, ou **lecture + écriture**. Ils sont modifiables depuis **Paramètres** (§8) et s'appliquent immédiatement.

---

## 3. Pôle Communauté

### 3.1 Élèves
Le référentiel central des élèves, parents et familles.
- **Lister / rechercher** un élève ; les listes sont denses et filtrables.
- **Ajouter / modifier** une fiche élève (état civil, classe, parents liés).
- **Lier un parent** à un ou plusieurs enfants (un parent peut suivre plusieurs élèves).

### 3.2 Parcours scolaire
La **timeline pluriannuelle** d'un élève : classes successives, redoublements, passages. Permet de retracer l'historique d'un élève sur plusieurs années.

### 3.3 Santé
Le **carnet médical** : infirmerie, antécédents, et inscription aux **clubs** et activités.

### 3.4 Documents
Le **dossier administratif** de l'élève : pièces justificatives, documents d'orientation et de scolarité.

### 3.5 Personnel / RH
Gestion du **personnel** : fiches employés, **départements** et **congés**. Réservé à la direction / RH.

---

## 4. Pôle Pédagogie

### 4.1 Académique (notes & bulletins)
Le cœur pédagogique.
- **Saisir les notes** par séquence et par matière.
- **Générer les bulletins** : moyennes, **rang**, appréciations, procès-verbaux (PV).
- **Validation** des bulletins par la direction.
- **Blocage en cas de dette** : un bulletin peut être retenu si la scolarité n'est pas à jour (lien avec la Finance).

> Les enseignants saisissent les notes de **leurs** classes ; les bulletins sont validés au niveau de l'établissement.

### 4.2 Présence (biométrie, temps réel)
Le suivi de présence, alimenté par le **lecteur d'empreintes** ou par **saisie manuelle**.
- Le tableau affiche, **en direct**, les arrivées (présent / en retard / absent) avec l'heure de pointage et le nombre de minutes de retard.
- **SMS automatiques** aux parents prévus à l'arrivée / en cas d'absence (selon configuration).
- Vous pouvez aussi **pointer manuellement** un élève depuis cette page.

### 4.3 Discipline
Le registre de **vie scolaire** : enregistrement des **incidents**, application de **sanctions graduées**, et notification des parents par SMS.

### 4.4 Cahier de textes
Le **cahier de textes & devoirs** : ce qui a été fait en classe, les devoirs donnés et leurs échéances. Renseigné par les enseignants.

---

## 5. Pôle Opérations

### 5.1 Finance
Module de gestion financière (écriture réservée à l'**économe**, lecture pour la direction).
- **Encaisser un paiement** : méthode, tranche, montant — un **reçu** est rattaché.
- **Configurer les frais** par niveau et sous-système (tranches de scolarité).
- **Situation d'un élève** : ce qui est payé / dû.
- **Débiteurs** : liste des élèves en retard de paiement.
- **Dépenses** et **synthèse des recettes**.

> Astuce : enregistrer un paiement met automatiquement à jour le **solde** de l'élève et débloque, le cas échéant, l'accès à son bulletin.

### 5.2 Emploi du temps
La **grille horaire** par classe (édition créneau par créneau), avec **détection automatique des conflits** (même enseignant ou même salle au même moment).

### 5.3 Événements
Création d'**annonces et d'événements** (réunions, examens, fêtes) avec **notification des parents** ciblée par classe ou audience.

### 5.4 Messages
Le **carnet de correspondance** numérique avec les parents : échanges écrits, mots et réponses.

---

## 6. Pôle Pilotage

### 6.1 Tableau de bord
La **vue d'ensemble** : KPIs adaptés à votre rôle (effectif, taux de présence du jour, indicateurs financiers).

### 6.2 Alertes
Repère les **élèves à risque** (présence, notes, finance) et émet des **alertes automatiques** pour permettre une action précoce.

### 6.3 Rapports
L'**analytique de l'établissement** : bilan financier, présence mensuelle, démographie. Sert au pilotage et aux comptes-rendus.

### 6.4 Paramètres
La configuration de l'établissement : **matrice de permissions** (rôles × modules), gestion des comptes et du **lecteur d'empreintes**. Voir §8.

---

## 7. Le portail parent

Connecté avec un compte **parent**, vous accédez uniquement aux informations de **vos enfants** :
- **Notes et bulletins** ;
- **Présence** (arrivées, retards, absences) ;
- **Frais** : situation des paiements et reçus ;
- **Boîte à suggestions** : pour adresser remarques et demandes à l'établissement.

Si vous avez plusieurs enfants, vous basculez de l'un à l'autre depuis le portail.

---

## 8. Administration (Paramètres)

Réservé à la direction.

### 8.1 Matrice de permissions
Un tableau **rôle × module**. Pour chaque croisement, choisissez **Aucun / Lecture / Écriture**. Les changements sont **appliqués immédiatement** : un utilisateur connecté voit son menu et ses droits évoluer à la prochaine action.

> Les contrôles d'accès sont **toujours vérifiés côté serveur**. Masquer une tuile ne suffit pas à donner un droit : c'est la matrice qui fait foi.

### 8.2 Comptes utilisateurs
Création et gestion des comptes du personnel et des parents, attribution des rôles.

### 8.3 Lecteur d'empreintes
Enregistrement et clé du **lecteur biométrique** utilisé par le module Présence.

---

## 9. Questions fréquentes

**Je ne vois pas un module.** Votre rôle n'y a pas accès. Demandez à la direction d'ajuster la matrice de permissions (§8.1).

**Le tableau de présence ne bouge pas.** Vérifiez que l'onglet est resté ouvert et que le lecteur (ou la saisie manuelle) envoie bien les pointages. La mise à jour est automatique, sans rafraîchir.

**Un bulletin est bloqué.** L'élève a probablement une dette de scolarité : vérifiez sa **situation** dans Finance (§5.1).

**Comment changer la langue ?** Bouton **FR / EN** en haut de l'écran (§1.2).

**J'ai oublié mon mot de passe.** Utilisez **Oublié ?** sur l'écran de connexion : un mot de passe temporaire est envoyé par e-mail si votre fiche personnel en a un et si le SMTP est configuré. Sinon, contactez l'administrateur, qui peut réinitialiser votre compte depuis Personnel.

---

## 10. Bonnes pratiques

- **Déconnectez-vous** en fin de journée sur un poste partagé.
- Saisissez les **notes** et la **présence** au fil de l'eau : les parents et le tableau de bord les voient en quasi temps réel.
- Avant d'éditer les bulletins, vérifiez que les **paiements** sont à jour pour éviter les blocages de dernière minute.
- Utilisez les **Rapports** en fin de période pour vos comptes-rendus de conseil.

---

*Document de référence fonctionnelle — à adapter au paramétrage réel de votre établissement.*

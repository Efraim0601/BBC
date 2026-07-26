# Guide utilisateur — BBC SMS

**Bayo Bilingual Complex — Système de gestion scolaire**  
Application bilingue (FR / EN), temps réel, accessible depuis un navigateur.

> Guide destiné aux **utilisateurs finaux** (direction, économat, enseignants, parents).  
> Installation technique : [README.md](README.md) · Architecture : [ARCHITECTURE.md](ARCHITECTURE.md).  
> Guide interactif dans l’application : menu **Aide** ou URL `/guide/`.

---

## Comment utiliser ce guide

1. Lisez la section **Premiers pas** (§1) une fois.
2. Suivez **module par module** le chapitre qui correspond à votre rôle.
3. À la fin de chaque module, cochez la **fiche de test** : elle valide que vous savez faire les gestes essentiels.
4. En cas de doute, la **FAQ** (§12) et le portail **Aide** (`/guide/`) reprennent les mêmes concepts.

**Convention des fiches de test**

| Symbole | Signification |
|---|---|
| ☐ | À faire pendant la formation / prise en main |
| ✓ | Critère de réussite attendu |

---

## 1. Premiers pas

### 1.1 Se connecter
1. Ouvrez l’adresse communiquée par l’établissement.
2. Saisissez **identifiant** et **mot de passe**.
3. Après connexion (personnel) : choisissez un **parcours** (Maternelle / Primaire / Secondaire × Francophone / Anglophone), ou **Tous les parcours** si vous êtes administrateur.
4. Vous arrivez sur la **grille des applications**.

### 1.2 Se repérer
- **Barre supérieure** : établissement, **parcours actif**, langue FR/EN, profil, déconnexion, lien **Aide**.
- **Applications** : modules regroupés en 4 pôles — Communauté, Pédagogie, Opérations, Pilotage.
- **Menu latéral** : navigation rapide une fois dans un module.

> Vous ne voyez que les modules autorisés pour votre rôle (matrice Paramètres).

### 1.3 Le parcours (concept clé)
Le parcours **filtre** sections, classes et élèves. Une section créée en « Primaire FR » n’apparaît pas si vous êtes en « Secondaire EN ».  
**Astuce direction** : utilisez **Tous les parcours** pour voir toute l’école, puis revenez à un parcours pour travailler proprement.

### Fiche de test — Premiers pas
| # | Action | Réussi si |
|---|---|---|
| ☐ 1 | Se connecter avec son compte | ✓ Accueil des applications affiché |
| ☐ 2 | Changer FR ↔ EN | ✓ Labels de l’interface changent |
| ☐ 3 | Changer de parcours (bandeau) | ✓ Listes élèves/classes se mettent à jour |
| ☐ 4 | Ouvrir **Aide** (`/guide/`) | ✓ Guide HTML bilingue s’affiche |
| ☐ 5 | Se déconnecter | ✓ Retour à l’écran de connexion |

---

## 2. Rôles et permissions (concept)

| Rôle | Vocation |
|---|---|
| **Principal** | Pilotage, Paramètres, tous modules |
| **Économe** | Finance (écriture) |
| **Enseignant / Prof. principal** | Notes, cahier, classe |
| **Préfet** | Vie scolaire, discipline, présence |
| **Rôles personnalisés** | Créés dans Paramètres → Rôles |
| **Parent** | **Uniquement** le portail parent (ses enfants) |

Niveaux : **Aucun** · **Lecture** · **Écriture**.  
Le rôle **Parent** ne peut pas recevoir Académique / Élèves / Finance : l’accès reste limité au portail (sécurité ligne à ligne sur ses enfants).

### Fiche de test — Rôles
| # | Action | Réussi si |
|---|---|---|
| ☐ 1 | Ouvrir Paramètres → Permissions | ✓ Matrice rôles × modules visible |
| ☐ 2 | Créer un rôle personnalisé (ex. Surveillant) | ✓ Apparaît dans Rôles (badge « Personnalisé ») |
| ☐ 3 | Lui donner `discipline:write` | ✓ Cellule passe en Complet |
| ☐ 4 | Tenter d’accorder `academic` au rôle Parent | ✓ Refusé / cellule bloquée |
| ☐ 5 | Affecter le nouveau rôle à un compte Personnel | ✓ L’utilisateur ne voit que ses modules |

---

## 3. Paramètres (Pilotage)

### 3.1 Scolarité (sections, classes, matières)
Ordre recommandé :
1. Créer une **section** (libellé + système + niveau — verrouillé au parcours actif).
2. Créer des **classes** rattachées à cette section.
3. Importer / créer les **matières** et coefficients.

### 3.2 Général
Identité de l’école : nom, contacts, devise, autorité, **heure d’ouverture** (seuil de retard) et heure de fin.

### 3.3 Calendrier scolaire
Jours fériés / fermetures : ces dates **ne génèrent pas de retards** au pointage biométrique (week-ends exclus aussi).

### 3.4 Catalogue Discipline
Personnalisez les listes **Motif** (type) et **Sanction**. Elles alimentent le module Discipline.

### 3.5 Messagerie SMTP
Nécessaire pour envoi d’identifiants et e-mails de test.

### Fiche de test — Paramètres
| # | Action | Réussi si |
|---|---|---|
| ☐ 1 | Créer section + classe dans le parcours actif | ✓ Classe listée, bouton Nouvelle classe actif |
| ☐ 2 | Régler l’heure d’ouverture (ex. 07:45) | ✓ Enregistré dans Général |
| ☐ 3 | Ajouter un jour férié | ✓ Visible dans Calendrier |
| ☐ 4 | Ajouter un motif « Bagarre » | ✓ Apparaît dans Discipline |
| ☐ 5 | Tester l’e-mail SMTP | ✓ Message de succès (si SMTP configuré) |

---

## 4. Module Élèves (Communauté)

### Concepts
- Fiche élève : état civil, classe, **père / mère / tuteur** (nom, téléphone, e-mail).
- Le **contact principal** (SMS) est dérivé automatiquement (père → mère → tuteur).
- **Comptes parents** : créés depuis la fiche (identifiant + mot de passe) → accès portail.
- Import Excel/CSV + **modèle** téléchargeable.
- Listes filtrées par parcours ; tri par classe.

### Fiche de test — Élèves
| # | Action | Réussi si |
|---|---|---|
| ☐ 1 | Créer un élève avec père + mère renseignés | ✓ Fiche enregistrée |
| ☐ 2 | L’affecter à une classe | ✓ Classe visible dans la liste |
| ☐ 3 | Créer un compte parent sur la fiche | ✓ Login parent fonctionne sur `/parent` |
| ☐ 4 | Télécharger le modèle CSV et importer 2 lignes | ✓ Élèves créés / rapport d’import |
| ☐ 5 | Créer un élève « Non affecté » | ✓ Visible dans le parcours (pas fantôme) |

---

## 5. Module Personnel / RH

### Concepts
Fiches employés, rôles multiples, e-mail / téléphone **validés**, création de compte de connexion.

### Fiche de test — Personnel
| # | Action | Réussi si |
|---|---|---|
| ☐ 1 | Créer un enseignant avec e-mail valide | ✓ Fiche OK |
| ☐ 2 | Saisir e-mail `test` | ✓ Refusé (validation) |
| ☐ 3 | Attribuer le rôle créé en §2 | ✓ Affiché sur la fiche |
| ☐ 4 | Créer le login + envoi e-mail | ✓ Compte utilisable (si SMTP) |

---

## 6. Module Présence

### Concepts
- Pointages lecteur / saisie ; tableau **temps réel**.
- **Retard** = arrivée après l’heure d’ouverture (Paramètres → Général).
- Week-ends et **jours fériés** : pas de retard.
- Historique : choisir une **date** dans l’en-tête.
- Liste triée / filtrable **par classe**.

### Fiche de test — Présence
| # | Action | Réussi si |
|---|---|---|
| ☐ 1 | Ouvrir Présence aujourd’hui | ✓ KPIs + journal |
| ☐ 2 | Filtrer une classe | ✓ Lignes filtrées |
| ☐ 3 | Choisir une date passée | ✓ Journal de ce jour |
| ☐ 4 | Modifier l’heure d’ouverture puis simuler un scan tardif | ✓ Statut « retard » cohérent |
| ☐ 5 | Ajouter un férié = aujourd’hui et scanner | ✓ Pas de retard compté |

---

## 7. Module Discipline

### Concepts
- Incident lié à un **matricule** : la fiche élève s’affiche automatiquement.
- Motifs / sanctions issus du **catalogue** Paramètres.
- Notification parent (SMS / Envoyer) : utilise le téléphone parent ; résultat affiché.

### Fiche de test — Discipline
| # | Action | Réussi si |
|---|---|---|
| ☐ 1 | Saisir un matricule existant | ✓ Nom + classe affichés |
| ☐ 2 | Enregistrer un incident avec motif catalogue | ✓ Dans la liste |
| ☐ 3 | Prefill « Notifier » puis SMS | ✓ Message de résultat (envoyé / pas de téléphone) |
| ☐ 4 | Ajouter une sanction personnalisée (Paramètres) | ✓ Disponible dans le formulaire |

---

## 8. Module Académique (notes & bulletins)

### Concepts
Saisie des notes par séquence, bulletins, rang, PV, validation direction.  
**Parents** : pas d’accès à ce module ; ils voient les notes de **leurs** enfants dans le portail.

### Fiche de test — Académique
| # | Action | Réussi si |
|---|---|---|
| ☐ 1 | Saisir une note pour un élève de sa classe | ✓ Note enregistrée |
| ☐ 2 | Générer le bulletin | ✓ Moyennes / rang cohérents |
| ☐ 3 | Se connecter en parent | ✓ Module Académique **absent** du menu |
| ☐ 4 | Sur le portail, ouvrir les notes de l’enfant | ✓ Uniquement cet enfant |

---

## 9. Autres modules (synthèse + tests courts)

### 9.1 Parcours scolaire
Timeline pluriannuelle d’un élève.  
**Test** : ☐ ouvrir un élève → ✓ historique de classes visible.

### 9.2 Santé / Documents / Fournitures
Carnet médical, pièces administratives, listes de fournitures & livres par classe.  
**Test** : ☐ publier une liste de livres → ✓ visible côté parent.

### 9.3 Cahier de textes
Leçons & devoirs.  
**Test** : ☐ ajouter une entrée → ✓ visible pour la classe.

### 9.4 Finance
Encaissements, frais, débiteurs, dépenses (écriture économe).  
**Test** : ☐ encaisser un paiement → ✓ reçu + solde mis à jour.

### 9.5 Emploi du temps / Événements / Messages
Grille horaire, annonces, correspondance.  
**Test** : ☐ créer un événement + notifier → ✓ compteur de destinataires.

### 9.6 Tableau de bord / Alertes / Rapports
Pilotage.  
**Test** : ☐ ouvrir Dashboard + un rapport mensuel → ✓ chiffres non vides si données présentes.

---

## 10. Portail parent

### Concepts
Compte `parent` → redirection `/parent`.  
Données **strictement** limitées aux enfants liés (`parent_student`).  
Notes, présence, frais, suggestions.

### Fiche de test — Portail parent
| # | Action | Réussi si |
|---|---|---|
| ☐ 1 | Connexion parent | ✓ Portail (pas la grille staff) |
| ☐ 2 | Basculer entre deux enfants (si liés) | ✓ Données changent |
| ☐ 3 | Consulter notes / présence / frais | ✓ Cohérent avec le staff |
| ☐ 4 | Appeler une URL staff (`/apps/academic`) | ✓ Accès refusé / redirection |

---

## 11. Scénario de bout en bout (direction)

Ordre conseillé pour une année neuve :

1. Paramètres → Général (identité + heure d’ouverture) + Calendrier (fériés).  
2. Scolarité : sections → classes → matières / coefficients.  
3. Catalogue Discipline (motifs / sanctions).  
4. Rôles personnalisés + matrice de permissions.  
5. Import élèves + création comptes parents.  
6. Personnel + emplois du temps.  
7. Présence / Discipline / Académique au quotidien.  
8. Finance (frais) avant les bulletins.

### Fiche de test — Bout en bout
| # | Action | Réussi si |
|---|---|---|
| ☐ 1 | Parcourir les 8 étapes ci-dessus sur un parcours test | ✓ Aucun blocage « données invisibles » |
| ☐ 2 | Parent voit uniquement son enfant | ✓ Pas de liste école |
| ☐ 3 | Bulletin généré pour une classe avec notes | ✓ PDF / écran bulletin OK |

---

## 12. FAQ

**Je ne vois pas un module.** Matrice Paramètres → Permissions.  
**Données « disparues ».** Vérifiez le **parcours** actif (bandeau) ou choisissez « Tous les parcours ».  
**Impossible de créer une classe.** Créez d’abord une **section** dans ce parcours.  
**Élève invisible.** Affectez une classe, ou restez dans le bon parcours (non affecté reste visible).  
**Parent voit toute l’école.** Ne doit plus arriver : rôle Parent bloqué hors portail ; signalez tout contournement.  
**Retards absurdes le dimanche.** Week-ends et fériés exclus ; vérifiez le calendrier.  
**Mot de passe oublié.** Lien « Oublié ? » (e-mail) ou reset via Personnel.  
**Langue.** Bouton FR / EN en haut.

---

## 13. Bonnes pratiques

- Déconnexion sur poste partagé.  
- Saisie notes / présence au fil de l’eau.  
- Vérifier les paiements avant validation des bulletins.  
- Former chaque rôle avec **sa** fiche de test uniquement.  
- Conserver ce guide à jour après chaque évolution Paramètres.

---

*Guide fonctionnel BBC SMS — formation et prise en main. Adapter les exemples au paramétrage réel de l’établissement.*

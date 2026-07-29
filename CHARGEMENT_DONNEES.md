# Guide — Démarrer l'application et charger les données de `documents/`

Ce guide explique **(1)** comment lancer BBC SMS en local et **(2)** comment charger,
section par section, les fichiers du dossier `documents/` (et `bulletins templates/`).

---

## 1. Démarrer l'application en local

> ⚠️ Les commandes Docker doivent être **lancées par vous** dans un terminal
> (l'assistant n'a pas la permission de démarrer les conteneurs).

### Option A — Instance propre pour vos vraies données (recommandé)

```bash
cd /home/wuwei/Documents/AFRILAND/BBC

# (une seule fois) repartir d'une base vierge — EFFACE les données existantes
make reset

# construire + démarrer (Postgres + backend + frontend) ; ~3-6 min au 1er build
make prod
```

Le fichier `.env` (déjà créé) définit l'admin de première connexion :

| Champ        | Valeur                    |
|--------------|---------------------------|
| Identifiant  | `admin`                   |
| Mot de passe | `Admin2026!`              |
| Établissement| Bilingual Bright College  |
| Année        | 2026-2027                 |

> L'admin n'est créé **que sur une base vierge**. Si vous n'avez pas fait
> `make reset` et que la base contenait déjà des comptes, utilisez ceux-là.

### Option B — Mode démo (données d'exemple, pour tester l'outil)

```bash
make demo   # logins : principal / econome / parent1  (mot de passe : password)
```

### Accès

| Service   | URL                     |
|-----------|-------------------------|
| Interface | http://localhost:8081   |
| API       | http://localhost:8080   |

### Commandes utiles

```bash
make ps       # état des conteneurs
make logs     # logs du backend (Ctrl-C pour quitter)
make down     # arrêter (garde la base)
make reset    # tout effacer (base comprise)
```

---

## 2. Quel document va dans quelle section ?

| Fichier(s) dans `documents/`                              | Nature réelle              | Où le charger dans l'app                         |
|----------------------------------------------------------|----------------------------|-------------------------------------------------|
| `5ème A_2025-2026.xls`, `4ème A_2025-2026.xls`, `FORM 2_2025-2026.xls` | **Registres d'élèves** (NIU, nom, sexe, naissance, lieu, redouble) | **Élèves → Importer** (§3) |
| `LISTE PROVISOIRE 20262027 BBC.xlsx`                      | Listes provisoires (mater./primaire), souvent quasi vides | **Élèves → Importer** (§3) |
| `bulletins templates/MATIERE EXCEL.xlsx`                 | Liste des matières FR/EN   | **Paramètres → Scolarité → Matières** (§4)       |
| `MISE EN PLACE 2026 BBC primaire.docx`                   | **Personnel** (enseignants) — PAS des élèves | **Personnel** (saisie, §5)          |
| `SIL-CP.docx`, `CLASS 5.docx`, `CLASS 6.docx`, `CM1.docx`, `CM2.docx`, `CE1-CE2.docx`, `Maternelle.docx` | Conditions d'admission + **fournitures** + **livres** par classe | **Fournitures / Manuels** (§6) |
| `bulletins templates/Bulletin *.xlsx`, `BULLETINS 2026.docx` | Modèles de bulletins   | Référence (module Bulletins, §7)                 |
| `bulletins templates/liste manuels scolaires *.pdf`      | Liste des manuels          | **Manuels** (parents, §6)                        |
| `bulletins templates/*coefficient*.pdf`, `SUBJECT-COEFFICIENTS-1.pdf` | Coefficients par classe | Référence (à saisir sur les matières, §4)     |

---

## 3. Charger les élèves (le cœur de la demande)

Les registres `.xls` sont importables **tels quels** — pas besoin de les convertir.

1. Menu **Élèves** → bouton **Importer**.
2. **Classe cible** :
   - *Nouvelle classe* → tapez le nom exactement comme sur le registre, ex. **`5e A`**,
     choisissez le **sous-système** (Francophone pour 5e/4e, Anglophone pour FORM 2)
     et le **niveau** (Secondaire). La classe **et sa section** sont créées automatiquement.
   - ou *Classe existante* si vous l'avez déjà créée.
3. **Données** → bouton **Fichier Excel / CSV** → sélectionnez le `.xls`
   (ex. `5ème A_2025-2026.xls`). La 1ʳᵉ feuille est lue automatiquement.
   *(Vous pouvez aussi copier-coller les lignes dans la zone de texte.)*
4. **Aperçu** : vérifiez NIU, Nom et prénom, Sexe, Naissance, Lieu, Redouble.
   Les lignes en rouge (sans nom) sont ignorées.
5. **Importer N élève(s)**.

### Colonnes reconnues automatiquement

Ce sont **exactement les champs de la fiche élève**. L'en-tête est détecté tout seul,
l'ordre n'a pas d'importance et une colonne absente reste simplement vide.

| Colonne (modèle CSV) | Champ de la fiche | Formats acceptés |
|---|---|---|
| `nom` | Identité → Nom | Obligatoire. Ou une colonne unique `Nom et Prénom` (1er mot = nom). |
| `prenom` | Identité → Prénom | Obligatoire (sauf colonne `Nom et Prénom`). |
| `sexe` | Identité → Sexe | M / F (Masculin, Féminin, Male, Female, garçon, fille). |
| `date_naissance` | Identité → Date de naissance | « 06 janvier 2011 », `2011-01-06` ou `06/01/2011`. |
| `lieu_naissance` | Identité → Lieu de naissance | Texte libre. |
| `niu` | Identité → NIU | Facultatif — identifiant unique national. |
| `redouble` | Identité → Redouble cette année | OUI / NON, 1 / 0, VRAI / FAUX. |
| `pere_nom`, `pere_telephone`, `pere_email` | Famille / tuteur → Père | Texte libre. |
| `mere_nom`, `mere_telephone`, `mere_email` | Famille / tuteur → Mère | Texte libre. |
| `tuteur_nom`, `tuteur_lien`, `tuteur_telephone`, `tuteur_email` | Famille / tuteur → Tuteur | `tuteur_lien` = oncle, grand-mère… |

La **classe** n'est pas une colonne : elle est choisie une fois pour tout le lot (étape 2).
Le bouton **Modèle CSV** (écran d'import) télécharge ce gabarit prérempli d'une ligne d'exemple,
et **Exporter liste** produit les mêmes colonnes — un export corrigé dans un tableur se réimporte tel quel.

Les registres officiels (`NIU`, `Nom et Prénom`, `Sexe`, `Date de naissance`,
`Lieu de naissance`, `Redouble`) restent reconnus tels quels.

### Correspondance des trois registres fournis

| Fichier                 | Classe à saisir | Sous-système | Niveau     |
|-------------------------|-----------------|--------------|------------|
| `5ème A_2025-2026.xls`  | `5e A`          | Francophone  | Secondaire |
| `4ème A_2025-2026.xls`  | `4e A`          | Francophone  | Secondaire |
| `FORM 2_2025-2026.xls`  | `Form 2`        | Anglophone   | Secondaire |

### Bon à savoir
- **Le nom est scindé automatiquement** : 1er mot = Nom de famille, le reste = Prénoms.
  Vérifiez/corrigez au besoin sur la fiche de l'élève.
- **NIU dupliqué** : un même NIU déjà présent est ignoré (ré-import sans doublon).
  Le **matricule** interne reste `BBC-xxxx` (le NIU officiel est conservé à part).
- **Rattacher les parents** : une fois l'élève créé, ouvrez sa fiche → *Comptes parents*
  → **Ajouter** (identifiant + mot de passe) pour activer le portail parent.

---

## 4. Charger les matières (`MATIERE EXCEL.xlsx`)

Les matières officielles FR/EN sont **déjà intégrées** dans l'application.

1. **Paramètres → Scolarité → onglet Matières**.
2. Sélectionnez la liste **FR** (ou **EN**).
3. Cliquez **Importer les matières standard** → crée toute la liste
   (les matières déjà présentes sont ignorées). Recommencez pour l'autre sous-système.
### Coefficients par classe (nouveau)

Les coefficients réels sont **par classe** (un même cours pèse différemment en 6e
et en 3e). Ils ont été extraits des PDF officiels vers un fichier prêt à importer :

> **`bulletins templates/Coefficients_BBC_2026-2027.xlsx`** (+ `.csv`)
> Format : `Sous-système | Code | Matière | Classe | Coefficient` — une ligne par
> (matière × classe). Feuille **Coefficients** (importable) + feuilles **Francophone**
> / **Anglophone** (lecture, matrice comme les PDF).
> Source : arrêté MINESEC 239/23 (francophone 6e→3e) + table anglophone FORM 1→U6.

**Import :** Paramètres → Scolarité → onglet **Matières** → carte
**« Coefficients par classe »** → **Importer (Excel/CSV)** → choisissez le fichier.

- La colonne **Classe** accepte un **niveau** (`5e`, `Form 2`) : il s'applique à
  **toutes** les classes qui commencent par ce libellé (`5e A`, `5e B`…).
- Les **matières manquantes sont créées automatiquement** au sous-système indiqué.
- **Les classes doivent déjà exister** (créez-les d'abord, ou via l'import élèves §3).
- Ces coefficients sont **utilisés dans les bulletins** (moyenne pondérée par classe) ;
  à défaut de coefficient par classe, le coefficient par défaut de la matière (1) s'applique.

> ⚠️ Ordre : importez d'abord les **classes** (ou les élèves §3), **puis** les coefficients,
> sinon les lignes seront « ignorées : aucune classe ». Le second cycle **francophone**
> (2nde→Tle) n'est pas dans les PDF fournis — à compléter à la main si besoin.

---

## 5. Charger le personnel (`MISE EN PLACE 2026 BBC primaire.docx`)

Ce document est la **mise en place du personnel** (enseignants), pas des élèves.
Il n'y a pas encore d'import en masse pour le personnel : saisissez chaque agent via
**Personnel → Nouveau**. Les identifiants de connexion peuvent être envoyés par e-mail
à la création. Vous pourrez ensuite affecter les enseignants aux classes dans
**Paramètres → Scolarité → Classes**.

*(Si vous le souhaitez, un import en masse du personnel peut être ajouté — voir §8.)*

---

## 6. Fournitures & manuels par classe

Les `.docx` de classe (`SIL-CP`, `CLASS 5/6`, `CM1`, `CM2`, `CE1-CE2`, `Maternelle`)
contiennent, pour chaque classe : conditions d'admission, **frais**, **liste de fournitures**
et **livres au programme**. Ils se saisissent dans le module **Fournitures / Manuels**
(ressources de classe), visibles par les parents. La `liste manuels scolaires *.pdf`
sert de référence pour les **manuels**.

---

## 7. Bulletins (référence)

`Bulletin francophones.xlsx`, `bulletin anglophone.xlsx` et `BULLETINS 2026.docx` sont
les **modèles de bulletins**. Les bulletins se génèrent dans l'application à partir des
notes saisies ; ces fichiers servent de référence de mise en page.

---

## 8. Dépannage

| Problème                                   | Solution                                                                 |
|--------------------------------------------|--------------------------------------------------------------------------|
| `docker stop` : *permission denied*        | `sudo snap restart docker`, puis relancez `make prod`.                   |
| Le build échoue / port occupé              | `make down` puis `make prod` ; vérifiez que 8080/8081/5433 sont libres.  |
| Connexion admin refusée                    | La base n'était pas vierge → faites `make reset` puis `make prod`.       |
| Le `.xls` ne s'importe pas                 | Vérifiez que la 1ʳᵉ feuille contient bien l'en-tête NIU/Nom et Prénom…   |
| Dates non reconnues                        | Formats acceptés : « JJ mois AAAA », `AAAA-MM-JJ`, `JJ/MM/AAAA`.         |
| Une classe apparaît en double              | Le nom doit être identique (ex. toujours `5e A`) à chaque import.        |

---

### Ordre conseillé
1. `make reset` + `make prod` → se connecter (`admin` / `Admin2026!`)
2. **Matières** (FR puis EN) — §4
3. **Élèves** classe par classe — §3
4. **Parents** sur les fiches élèves — §3
5. **Personnel**, **Fournitures/Manuels** — §5, §6

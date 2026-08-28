"""Single source for BBC SMS role-specific operational guides.

The content reflects the local production-copy walkthrough performed on
2026-08-28.  Each entry is bilingual so the in-app guide can follow the same
FR/EN convention as the application.  The standalone DOCX builder uses the
English copy and keeps exact UI labels and routes where they help operators.
"""

from __future__ import annotations


def t(fr: str, en: str) -> dict[str, str]:
    return {"fr": fr, "en": en}


ROLE_GUIDES: list[dict] = [
    {
        "slug": "administrator",
        "role_codes": ["administrator", "admin_maternelle", "admin_primary", "admin_secondary"],
        "title": t("Guide de l’administrateur", "Administrator guide"),
        "subtitle": t(
            "Configurer l’école, gérer les accès et superviser toutes les opérations.",
            "Configure the school, manage access, and supervise every operation.",
        ),
        "summary": t(
            "L’administrateur est le garant de la configuration et des habilitations. Le profil administrateur global couvre tous les parcours. Un administrateur de section suit les mêmes procédures, mais uniquement dans son cycle autorisé.",
            "The administrator owns configuration and access governance. The global administrator covers every parcours. A section administrator follows the same procedures, but only inside the assigned school level.",
        ),
        "scope": t(
            "École entière pour le rôle Administrator; cycle attribué pour les administrateurs Maternelle, Primaire ou Secondaire.",
            "Whole school for Administrator; assigned Kindergarten, Primary, or Secondary level for section administrators.",
        ),
        "permissions": [
            (t("Accès et responsabilités", "Access and responsibilities"), t("Créer les utilisateurs, attribuer les rôles et parcours, configurer les règles de rôle et consulter l’audit.", "Create users, assign roles and parcours, configure role rules, and review the audit trail.")),
            (t("Structure scolaire", "School structure"), t("Gérer sessions, trimestres/séquences, sections, classes, matières, associations classe-matière et groupes bilingues.", "Manage sessions, terms/sequences, sections, classes, subjects, class-subject assignments, and bilingual groups.")),
            (t("Élèves et familles", "Students and families"), t("Inscrire, importer, modifier, exporter et gérer les liens parent-enfant et l’accès au portail.", "Register, import, edit, export, and manage guardian-child links and portal access.")),
            (t("Personnel", "Staff"), t("Créer et modifier les employés, attribuer les rôles, gérer départements, congés et plusieurs catégories de documents.", "Create and edit employees, assign roles, and manage departments, leave, and multiple document categories.")),
            (t("Pédagogie", "Teaching and learning"), t("Superviser présence, notes, conseils, bulletins, cahier de textes, promotions et emplois du temps.", "Supervise attendance, grades, councils, report cards, coursebooks, promotions, and timetables.")),
            (t("Finance et pilotage", "Finance and oversight"), t("Accéder aux paiements, comptes, dépenses, paie, comptabilité, tableaux de bord, alertes et rapports.", "Access payments, accounts, expenses, payroll, accounting, dashboards, alerts, and reports.")),
        ],
        "workflows": [
            {
                "title": t("Se connecter et choisir le bon périmètre", "Sign in and choose the correct scope"),
                "route": "/parcours",
                "steps": [
                    t("Connectez-vous puis choisissez Tous les parcours pour une opération école-entière.", "Sign in, then choose All parcours for a whole-school operation."),
                    t("Choisissez un cycle et une section linguistique avant une opération liée à une classe.", "Choose a school level and language section before a class-specific operation."),
                    t("Vérifiez toujours le badge de parcours dans l’en-tête avant de modifier des données.", "Always verify the parcours badge in the header before changing data."),
                ],
            },
            {
                "title": t("Attribuer un rôle et ses parcours", "Assign a role and its parcours"),
                "route": "/access-control",
                "steps": [
                    t("Ouvrez Paramètres → Accès et responsabilités, puis l’onglet Utilisateurs.", "Open Settings → Access and responsibilities, then the Users tab."),
                    t("Sélectionnez l’utilisateur, son rôle principal et les parcours exacts nécessaires.", "Select the user, primary role, and only the parcours that are required."),
                    t("Prévisualisez les changements, indiquez un motif clair, puis enregistrez.", "Preview the changes, enter a clear reason, then save."),
                    t("Déconnectez-vous et testez le compte concerné dans chaque scénario autorisé et interdit.", "Sign out and test the affected account in every allowed and denied scenario."),
                ],
                "note": t("Préférez les règles du rôle. Les exceptions par utilisateur doivent rester rares, datées et auditées.", "Prefer role rules. User-specific exceptions should remain rare, dated, and audited."),
            },
            {
                "title": t("Configurer une année scolaire", "Configure an academic year"),
                "route": "/settings",
                "steps": [
                    t("Dans Sessions et périodes, créez ou vérifiez l’année scolaire courante.", "In Sessions and terms, create or verify the current academic year."),
                    t("Définissez les séquences, résultats trimestriels, résultat annuel et leurs intervalles de dates.", "Define sequences, trimester results, the annual result, and their date ranges."),
                    t("Vérifiez le calendrier, les fenêtres de saisie et les modèles de bulletin avant d’ouvrir les notes.", "Verify the calendar, grade-entry windows, and report-card templates before opening grade entry."),
                ],
            },
            {
                "title": t("Configurer classes, matières et groupes bilingues", "Configure classes, subjects, and bilingual groups"),
                "route": "/settings",
                "steps": [
                    t("Créez les classes dans le bon cycle et la bonne section linguistique.", "Create classes in the correct school level and language section."),
                    t("Créez les matières puis associez-les aux classes avec coefficients et enseignants responsables.", "Create subjects, then assign them to classes with coefficients and responsible teachers."),
                    t("Dans Lier les classes bilingues, associez les classes FR et EN qui partagent les mêmes élèves.", "In Link bilingual classes, pair the FR and EN classes that share the same pupils."),
                    t("Conservez des enseignants distincts par classe: le groupe partage l’effectif, pas automatiquement l’enseignant ni les notes.", "Keep separate teachers per class: the group shares the roster, not automatically the teacher or grades."),
                ],
            },
            {
                "title": t("Inscrire un élève ou importer une famille", "Register a student or import a family"),
                "route": "/students/new",
                "steps": [
                    t("Renseignez l’identité; sur mobile, la date peut être tapée en JJ/MM/AAAA avec insertion automatique des barres.", "Enter identity details; on mobile, the date can be typed as DD/MM/YYYY with automatic slash insertion."),
                    t("Choisissez la classe d’entrée. Pour une classe liée, le backend rattache l’élève au groupe commun.", "Choose the entry class. For a linked class, the backend attaches the student to the shared cohort."),
                    t("Ajoutez un ou plusieurs parents. L’e-mail reste facultatif tant que l’accès portail n’est pas activé.", "Add one or more guardians. Email remains optional until portal access is enabled."),
                    t("Pour un lot, utilisez Importer, téléchargez le modèle, prévisualisez toutes les lignes, puis confirmez.", "For a batch, use Import, download the template, preview every row, then confirm."),
                ],
            },
            {
                "title": t("Créer un employé et ses documents", "Create an employee and attach documents"),
                "route": "/staff/create",
                "steps": [
                    t("Créez l’employé depuis Personnel → Nouvel employé et attribuez son rôle et son cycle.", "Create the employee from Staff → New employee and assign the role and school level."),
                    t("Ajoutez autant de documents que nécessaire, chacun avec sa catégorie: CV, diplôme, identité, certificat ou autre.", "Add as many documents as required, each with its category: CV, diploma, identity, certificate, or other."),
                    t("Après création, ouvrez l’URL /staff/{id}; chaque document peut être prévisualisé et téléchargé.", "After creation, open /staff/{id}; each document can be previewed and downloaded."),
                ],
            },
            {
                "title": t("Publier un emploi du temps", "Publish a timetable"),
                "route": "/timetable",
                "steps": [
                    t("Choisissez la classe et vérifiez son modèle: titulaire quotidien ou enseignement par périodes.", "Choose the class and verify its model: daily homeroom or period-based teaching."),
                    t("Affectez matière, enseignant et salle à chaque créneau; corrigez tous les conflits signalés.", "Assign subject, teacher, and room to each slot; resolve every reported conflict."),
                    t("Pour des classes bilingues liées, respectez les périodes propres à chaque section et leurs enseignants distincts.", "For linked bilingual classes, respect each section’s time block and separate teachers."),
                    t("Publiez seulement après la prévisualisation. La présence secondaire dépend des occurrences publiées.", "Publish only after preview. Secondary attendance depends on published occurrences."),
                ],
            },
            {
                "title": t("Superviser notes, conseils et bulletins", "Supervise grades, councils, and report cards"),
                "route": "/academic",
                "steps": [
                    t("Contrôlez les feuilles soumises, retournez celles qui sont incomplètes et acceptez les feuilles conformes.", "Review submitted sheets, return incomplete ones, and accept compliant sheets."),
                    t("Vérifiez les présences finalisées, les données de conseil et les résultats calculés.", "Verify finalized attendance, council data, and calculated results."),
                    t("Validez le bulletin, générez le PDF officiel et utilisez le traitement par lot quand toute la classe est prête.", "Validate the report card, generate the official PDF, and use batch generation when the class is ready."),
                ],
            },
            {
                "title": t("Exécuter les opérations financières", "Run finance operations"),
                "route": "/finance",
                "steps": [
                    t("Utilisez Paiements pour encaisser et produire un reçu; choisissez toujours le compte réellement crédité.", "Use Payments to collect money and issue a receipt; always choose the account actually credited."),
                    t("Utilisez Comptes et mouvements pour les dépôts, retraits, transferts et rapprochements.", "Use Accounts and movements for deposits, withdrawals, transfers, and reconciliation."),
                    t("Utilisez Comptes élèves pour le solde, l’historique complet et le reçu consolidé.", "Use Student accounts for balances, full history, and consolidated receipts."),
                    t("Contrôlez dépenses, frais, paie, comptabilité et rapports avant toute clôture.", "Review expenses, fees, payroll, accounting, and reports before any close."),
                ],
            },
        ],
        "boundaries": [
            t("Ne partagez jamais un compte administrateur et n’utilisez pas ce rôle pour les opérations quotidiennes d’un autre métier.", "Never share an administrator account or use this role for another role’s daily work."),
            t("Ne donnez pas Accès et responsabilités au Principal, à l’Enseignant, au Comptable ou au Parent.", "Do not grant Access and responsibilities to Principals, Teachers, Accountants, or Parents."),
            t("Toute action critique doit porter un motif et être validée dans le bon parcours.", "Every critical action needs a reason and must be performed in the correct parcours."),
            t("Les suppressions historiques sont à éviter: préférez archivage, annulation, réouverture ou opération inverse auditée.", "Avoid deleting history: prefer archive, void, reopen, or an audited reversing operation."),
        ],
        "verification": [
            t("Un enseignant ne voit que ses classes et matières.", "A teacher sees only assigned classes and subjects."),
            t("Un principal ne voit que ses parcours et ne peut pas ouvrir Accès et responsabilités.", "A principal sees only assigned parcours and cannot open Access and responsibilities."),
            t("Le comptable couvre tous les parcours financiers sans accès aux permissions.", "The accountant covers all finance parcours without permission administration."),
            t("Le parent ne voit que ses enfants et les rubriques autorisées par la relation familiale.", "A parent sees only linked children and guardian-enabled sections."),
            t("Les liens, exports PDF/Excel, reçus, bulletins et documents s’ouvrent depuis leur URL correcte.", "Links, PDF/Excel exports, receipts, report cards, and documents open from their correct URL."),
        ],
        "known_gaps": [],
    },
    {
        "slug": "principal",
        "role_codes": ["principal", "principal_legacy_compat"],
        "title": t("Guide du principal", "Principal guide"),
        "subtitle": t("Piloter uniquement les parcours attribués, sans administrer les permissions.", "Lead only the assigned parcours without administering permissions."),
        "summary": t(
            "Le principal supervise les élèves, la pédagogie et les opérations de son ou de ses parcours. Les contrôles serveur refusent toute classe située hors de ce périmètre.",
            "The principal supervises students, teaching, and operations inside one or more assigned parcours. Server controls deny every class outside that scope.",
        ),
        "scope": t("Uniquement les cycles et sections linguistiques attribués par l’administrateur.", "Only the school levels and language sections assigned by the administrator."),
        "permissions": [
            (t("Élèves", "Students"), t("Consulter, rechercher et exporter les élèves du parcours; pas d’inscription ni d’import en masse.", "View, search, and export students in scope; no registration or bulk import.")),
            (t("Académique", "Academic"), t("Voir notes et résultats, examiner les paquets, consulter le conseil, valider/publier les bulletins; ne pas modifier les notes brutes des enseignants.", "View grades and results, review packets, view council input, and validate/publish report cards; do not edit teachers’ raw grades.")),
            (t("Présence et discipline", "Attendance and discipline"), t("Consulter listes et analyses de présence et gérer la discipline dans le parcours; la saisie de présence reste à l’enseignant responsable.", "View attendance rosters/analytics and manage discipline in scope; attendance marking remains with the responsible teacher.")),
            (t("Emploi du temps", "Timetable"), t("Consulter, préparer, publier, rouvrir et exporter les emplois du temps du parcours autorisé.", "View, prepare, publish, reopen, and export timetables inside the allowed parcours.")),
            (t("Finance", "Finance"), t("Consulter synthèse, comptes élèves, reçus consolidés, comptes de trésorerie et mouvements; écran principal en lecture seule.", "View overview, student accounts, consolidated receipts, treasury accounts, and movements; the main finance screen is read-only.")),
            (t("Personnel", "Staff"), t("Consulter et gérer le personnel du cycle actif ainsi que les agents communs sans cycle; aucun employé affecté uniquement à un autre cycle.", "View and manage staff in the active level plus shared staff without a level; employees assigned only to another level remain inaccessible.")),
            (t("Pilotage", "Oversight"), t("Consulter parcours, santé, documents, promotions, ressources, fournitures, alertes, tableaux de bord, rapports et réglages visibles.", "View journey, health, documents, promotions, resources, supplies, alerts, dashboards, reports, and visible settings.")),
        ],
        "workflows": [
            {
                "title": t("Choisir un parcours attribué", "Choose an assigned parcours"),
                "route": "/parcours",
                "steps": [
                    t("Après connexion, choisissez uniquement un cycle proposé puis Francophone ou English.", "After sign-in, choose one offered school level, then Francophone or English."),
                    t("Vérifiez le badge de l’en-tête avant de consulter une classe.", "Check the header badge before opening a class."),
                    t("Pour changer de responsabilité, cliquez le badge et choisissez un autre parcours attribué.", "To switch responsibility, click the badge and choose another assigned parcours."),
                ],
                "note": t("Si aucun parcours ne vous a été attribué, contactez l’administrateur; n’essayez pas de contourner l’écran de choix.", "If no parcours is assigned, contact the administrator; do not try to bypass the selector."),
            },
            {
                "title": t("Suivre les élèves du parcours", "Monitor students in scope"),
                "route": "/students",
                "steps": [
                    t("Utilisez le filtre de classe; seules les classes autorisées doivent apparaître.", "Use the class filter; only authorized classes should appear."),
                    t("Recherchez par nom ou matricule, ouvrez la fiche et consultez famille, documents, santé et parcours selon les permissions.", "Search by name or matricule, open the record, and view family, documents, health, and journey according to permissions."),
                    t("Exportez la liste en Excel ou PDF si nécessaire.", "Export the list to Excel or PDF when needed."),
                ],
            },
            {
                "title": t("Contrôler les notes et les bulletins", "Review grades and report cards"),
                "route": "/academic",
                "steps": [
                    t("Choisissez une classe et la période académique.", "Choose a class and academic period."),
                    t("Dans Saisie des notes, vérifiez l’état des feuilles et les blocages; n’éditez pas les notes à la place de l’enseignant.", "In Grade entry, review sheet status and blockers; do not edit grades on behalf of the teacher."),
                    t("Dans Aperçu de classe et Feuille maîtresse, contrôlez les résultats et anomalies.", "Use Class overview and Master sheet to review results and anomalies."),
                    t("Ouvrez le bulletin d’un élève, validez/publiez quand tout est complet, puis générez le PDF officiel.", "Open a student report card, validate/publish when complete, then generate the official PDF."),
                ],
            },
            {
                "title": t("Suivre présence et conseil", "Review attendance and council data"),
                "route": "/presence",
                "steps": [
                    t("Dans Appel, choisissez date, classe et, au secondaire, la période publiée.", "In Roll call, choose date, class, and—at Secondary—the published period."),
                    t("Consultez les listes et états; utilisez Analyses pour tendances, absences et retards.", "Review rosters and statuses; use Analytics for trends, absences, and lateness."),
                    t("Dans Académique → Présence et conseil, vérifiez l’intervalle de la séquence et les totaux issus des appels finalisés.", "In Academic → Attendance & council, verify the sequence date range and totals from finalized calls."),
                ],
            },
            {
                "title": t("Gérer discipline et cahier de textes", "Manage discipline and coursebook"),
                "route": "/discipline",
                "steps": [
                    t("Créez et suivez les incidents uniquement pour les élèves du parcours.", "Create and follow incidents only for students in scope."),
                    t("Utilisez les actions de convocation, clôture et notification conformément à la procédure de l’école.", "Use summon, close, and notification actions according to school procedure."),
                    t("Dans Cahier de textes, consultez ou complétez les entrées autorisées du parcours.", "In Coursebook, view or complete the authorized entries for the parcours."),
                ],
            },
            {
                "title": t("Gérer le personnel du parcours", "Manage staff in scope"),
                "route": "/staff",
                "steps": [
                    t("Ouvrez Personnel depuis le parcours actif; la liste contient les agents de ce cycle et les agents communs sans cycle.", "Open Staff from the active parcours; the list contains that level’s employees and shared staff without a level."),
                    t("Créez ou modifiez une fiche uniquement pour le cycle dont vous avez la responsabilité.", "Create or update a record only for the level you manage."),
                    t("Une URL vers un employé affecté uniquement à un autre cycle doit être refusée par le serveur.", "A URL targeting an employee assigned only to another level must be denied by the server."),
                ],
            },
            {
                "title": t("Publier l’emploi du temps du parcours", "Publish the parcours timetable"),
                "route": "/timetable",
                "steps": [
                    t("Choisissez une classe autorisée et contrôlez enseignants, matières, salles et conflits.", "Choose an authorized class and review teachers, subjects, rooms, and conflicts."),
                    t("Publiez ou rouvrez uniquement après validation de la direction.", "Publish or reopen only after management validation."),
                    t("Vérifiez ensuite l’emploi du temps enseignant et les occurrences de présence secondaire.", "Then verify teacher schedules and Secondary attendance occurrences."),
                ],
            },
            {
                "title": t("Consulter finance et pilotage", "Review finance and oversight"),
                "route": "/finance",
                "steps": [
                    t("Consultez les indicateurs financiers et les historiques sans créer de paiement ni de mouvement.", "Review finance indicators and histories without creating payments or movements."),
                    t("Dans Comptes élèves, filtrez par classe, ouvrez l’élève et préparez un reçu consolidé si nécessaire.", "In Student accounts, filter by class, open the student, and prepare a consolidated receipt if needed."),
                    t("Utilisez Tableaux de bord, Alertes et Rapports pour le suivi du parcours.", "Use Dashboards, Alerts, and Reports for parcours oversight."),
                ],
            },
        ],
        "boundaries": [
            t("Aucun accès à Accès et responsabilités; seul l’administrateur modifie rôles et permissions.", "No access to Access and responsibilities; only an administrator changes roles and permissions."),
            t("Aucune classe hors des parcours attribués, même par URL directe.", "No class outside assigned parcours, including direct URLs."),
            t("Pas d’inscription/import élève et pas de modification des notes brutes des enseignants.", "No student registration/import and no editing teachers’ raw grades."),
            t("La finance reste une consultation de direction; les encaissements et mouvements appartiennent au comptable.", "Finance remains an oversight view; collections and movements belong to the accountant."),
        ],
        "verification": [
            t("Le sélecteur d’élèves ne contient que les classes du parcours actif.", "The student selector contains only classes in the active parcours."),
            t("Une URL vers une classe hors périmètre est refusée par le serveur.", "A URL targeting an out-of-scope class is denied by the server."),
            t("Le bouton Nouvel élève et l’import ne sont pas disponibles.", "New student and import are unavailable."),
            t("Personnel s’ouvre, exclut les agents affectés uniquement aux autres cycles et refuse leur URL directe.", "Staff opens, excludes employees assigned only to other levels, and denies their direct URLs."),
            t("Un principal sans affectation voit un message Contacter l’administrateur et aucun bouton de parcours.", "A Principal without an assignment sees a Contact your administrator message and no parcours button."),
            t("Accès et responsabilités redirige vers l’accueil.", "Access and responsibilities redirects to the home page."),
            t("Finance indique lecture seule et la trésorerie n’autorise pas de mouvement.", "Finance shows read-only and Treasury does not allow a movement."),
        ],
        "known_gaps": [],
    },
    {
        "slug": "accountant",
        "role_codes": ["accountant", "econome", "finance_collector"],
        "title": t("Guide du comptable", "Accountant guide"),
        "subtitle": t("Encaisser, rapprocher la trésorerie, suivre les créances et gérer la paie sur tous les parcours.", "Collect payments, reconcile treasury, monitor receivables, and run payroll across every parcours."),
        "summary": t(
            "Le comptable est global: les finances doivent couvrir Maternelle, Primaire et Secondaire, en français et en anglais. Chaque paiement, dépense, dépôt, retrait ou transfert doit être rattaché au compte d’argent réellement touché.",
            "The accountant is global: finance covers Kindergarten, Primary, and Secondary in both language sections. Every payment, expense, deposit, withdrawal, or transfer must use the money account that was actually affected.",
        ),
        "scope": t("Tous les parcours; aucun accès à l’administration des permissions.", "All parcours; no permission administration."),
        "permissions": [
            (t("Encaissements", "Collections"), t("Créer des paiements, choisir le compte crédité, produire reçus, exporter et consulter l’historique.", "Create payments, choose the credited account, issue receipts, export, and review payment history.")),
            (t("Comptes élèves", "Student accounts"), t("Filtrer par classe, voir facturé/payé/solde/crédit et générer un reçu consolidé couvrant toutes les tranches.", "Filter by class, view billed/paid/balance/credit, and generate a consolidated receipt across all instalments.")),
            (t("Trésorerie", "Treasury"), t("Créer/archiver les comptes, enregistrer dépôts, retraits et transferts, puis rapprocher les soldes.", "Create/archive accounts, record deposits, withdrawals, and transfers, then reconcile balances.")),
            (t("Dépenses et frais", "Expenses and fees"), t("Enregistrer les dépenses payées depuis un compte, configurer grilles, types et plans de frais selon les habilitations actives.", "Record expenses paid from an account and configure fee grids, types, and plans when the related actions are enabled.")),
            (t("Paie", "Payroll"), t("Configurer périodes et composants, calculer, revoir, approuver, payer et produire les bulletins de paie.", "Configure periods and components, calculate, review, approve, pay, and issue payslips.")),
            (t("Personnel — consultation", "Staff — read only"), t("Consulter les fiches et salaires nécessaires à la paie; aucune création, modification ou import de personnel.", "View staff and salary records needed for payroll; no staff creation, editing, or import.")),
            (t("Comptabilité et rapports", "Accounting and reporting"), t("Consulter plan comptable, journaux, balance, grand livre, rapprochement et rapports financiers contextualisés.", "Use the chart of accounts, journals, trial balance, general ledger, reconciliation, and contextual finance reports.")),
        ],
        "workflows": [
            {
                "title": t("Enregistrer un paiement", "Record a payment"),
                "route": "/finance",
                "steps": [
                    t("Cliquez Nouveau paiement et choisissez d’abord la classe, puis l’élève.", "Click New payment and choose the class first, then the student."),
                    t("Saisissez le montant et la date; choisissez le mode utilisé par la famille.", "Enter amount and date; choose the method used by the family."),
                    t("Choisissez obligatoirement le compte caisse ou banque réellement crédité.", "Always choose the cash or bank account that actually received the money."),
                    t("Saisissez la référence si le mode l’exige, puis générez le reçu.", "Enter the reference when required, then generate the receipt."),
                    t("Dans la fenêtre du reçu, utilisez Télécharger PDF ou Imprimer.", "In the receipt dialog, use Download PDF or Print."),
                ],
                "note": t("Un ancien paiement peut afficher Compte crédité — si sa création précède la trésorerie intégrée; les nouveaux paiements doivent toujours renseigner le compte.", "A legacy payment can show Credited account — when it predates integrated treasury; every new payment must identify the account."),
            },
            {
                "title": t("Rechercher un paiement", "Find a payment"),
                "route": "/finance",
                "steps": [
                    t("Dans Paiements, filtrez par mode, date, reçu, élève, matricule, classe ou référence.", "In Payments, filter by method, date, receipt, student, matricule, class, or reference."),
                    t("Ouvrez Reçu pour vérifier l’élève, la tranche, le montant, le mode et la référence.", "Open Receipt to verify the student, instalment, amount, method, and reference."),
                    t("Utilisez Export pour un contrôle externe ou un rapprochement.", "Use Export for external review or reconciliation."),
                ],
            },
            {
                "title": t("Vérifier le compte d’un élève", "Review a student account"),
                "route": "/finance/student-accounts",
                "steps": [
                    t("Choisissez une classe puis cliquez Afficher les élèves; la recherche par nom ou matricule reste facultative.", "Choose a class and click Show students; name or matricule search is optional."),
                    t("Lisez l’étiquette: Solde dû affiche le montant restant; Payé intégralement affiche le total déjà payé.", "Read the status label: Balance due shows the remaining amount; Paid in full shows the total paid."),
                    t("Ouvrez l’élève pour voir Facturé, Payé, Solde dû, Crédit et toutes les transactions.", "Open the student to view Billed, Paid, Balance due, Credit, and every transaction."),
                    t("Cliquez Préparer le reçu consolidé, puis téléchargez ou imprimez le relevé complet.", "Click Prepare consolidated receipt, then download or print the complete statement."),
                ],
            },
            {
                "title": t("Enregistrer un dépôt ou un retrait", "Record a deposit or withdrawal"),
                "route": "/finance/treasury",
                "steps": [
                    t("Choisissez Dépôt ou Retrait, la date et le compte d’argent concerné.", "Choose Deposit or Withdrawal, the date, and the affected money account."),
                    t("Choisissez le compte de contrepartie comptable approprié.", "Choose the appropriate accounting counter-account."),
                    t("Saisissez montant, motif obligatoire et référence de bordereau/relevé.", "Enter amount, required reason, and deposit-slip/statement reference."),
                    t("Cliquez Enregistrer et comptabiliser; l’opération devient immuable et le solde est recalculé immédiatement.", "Click Record and post; the operation becomes immutable and the balance recalculates immediately."),
                ],
                "note": t("Une erreur ne se supprime pas: passez une opération inverse tracée et conservez les deux références.", "Do not delete an error: post a traceable reversing movement and retain both references."),
            },
            {
                "title": t("Transférer entre deux comptes", "Transfer between two accounts"),
                "route": "/finance/treasury",
                "steps": [
                    t("Choisissez Transfert interne, puis le compte source et le compte destination.", "Choose Internal transfer, then the source and destination accounts."),
                    t("Saisissez montant, motif et référence bancaire ou de caisse.", "Enter amount, reason, and bank/cash reference."),
                    t("Vérifiez que le total de trésorerie ne change pas: seul le partage entre comptes doit évoluer.", "Verify that total treasury is unchanged: only the distribution between accounts should move."),
                ],
            },
            {
                "title": t("Enregistrer une dépense", "Record an expense"),
                "route": "/finance",
                "steps": [
                    t("Ouvrez l’onglet Dépenses puis Nouvelle dépense.", "Open the Expenses tab, then New expense."),
                    t("Choisissez date, catégorie, libellé, montant et compte ayant réellement payé.", "Choose date, category, label, amount, and the account that actually paid."),
                    t("Enregistrez puis contrôlez l’écriture dans le journal et la baisse du solde du compte.", "Save, then verify the journal entry and the decrease in the account balance."),
                ],
            },
            {
                "title": t("Configurer les frais", "Configure fees"),
                "route": "/finance/fee-types",
                "steps": [
                    t("Définissez d’abord les types de frais réutilisables et leurs révisions.", "First define reusable fee types and their revisions."),
                    t("Créez un plan par session et périmètre; configurez les tranches et dates d’échéance.", "Create a plan by session and scope; configure instalments and due dates."),
                    t("Prévisualisez les charges avant génération et traitez ajustements et dérogations séparément.", "Preview charges before generation and handle adjustments and overrides separately."),
                    t("Contrôlez les soldes dans Comptes élèves et les documents émis.", "Verify balances in Student accounts and issued documents."),
                ],
            },
            {
                "title": t("Exécuter la paie", "Run payroll"),
                "route": "/finance/payroll",
                "steps": [
                    t("Créez une période de paie liée à une période comptable ouverte.", "Create a payroll period linked to an open accounting period."),
                    t("Configurez les composants de paie et leurs comptes; sans mapping, la revue doit rester bloquée.", "Configure payroll components and mappings; review must remain blocked when a mapping is missing."),
                    t("Prévisualisez les employés éligibles, puis créez et calculez le run.", "Preview eligible employees, then create and calculate the run."),
                    t("Faites revoir, approuver et payer par les acteurs distincts si la séparation des tâches est activée.", "Use separate reviewers, approvers, and payers when segregation of duties is enabled."),
                    t("Après paiement, contrôlez et régénérez les bulletins de paie si nécessaire.", "After payment, verify and regenerate payslips when necessary."),
                ],
            },
            {
                "title": t("Clôturer et rapprocher", "Reconcile and close"),
                "route": "/finance/accounting",
                "steps": [
                    t("Vérifiez la disponibilité du plan comptable, des mappings et des périodes.", "Verify chart-of-accounts, mapping, and period readiness."),
                    t("Contrôlez journaux, balance générale et grand livre.", "Review journals, trial balance, and general ledger."),
                    t("Rapprochez chaque compte avec relevé bancaire ou caisse avant clôture.", "Reconcile every account to its bank statement or cash count before close."),
                    t("Utilisez Rapports financiers avec le bon contexte de session et de classe.", "Use Finance reports with the correct session and class context."),
                ],
            },
        ],
        "boundaries": [
            t("Aucun accès à Accès et responsabilités.", "No access to Access and responsibilities."),
            t("Le profil comptable standard ne peut ni inscrire/importer un élève, ni modifier la structure scolaire, les matières ou l’emploi du temps.", "The standard Accountant profile cannot register/import students or change school structure, subjects, or timetables."),
            t("Personnel est strictement en lecture seule; la création et la modification appartiennent à l’administration ou à la direction autorisée.", "Staff is strictly read-only; creation and editing belong to administration or authorized management."),
            t("Ne supprimez jamais une transaction financière publiée; utilisez annulation, remboursement ou contre-écriture.", "Never delete a posted finance transaction; use void, refund, or reversal."),
            t("Une même personne ne doit pas calculer, revoir et approuver une paie si la séparation des tâches est requise.", "One person must not calculate, review, and approve payroll when segregation of duties is required."),
        ],
        "verification": [
            t("Les classes des six parcours apparaissent dans Comptes élèves.", "Classes from all six parcours appear in Student accounts."),
            t("Un paiement augmente immédiatement le compte crédité et produit un reçu avec l’élève réel.", "A payment immediately increases the credited account and produces a receipt naming the actual student."),
            t("Un dépôt et un retrait équilibrés ramènent la trésorerie au solde initial tout en laissant deux traces immuables.", "A balanced deposit and withdrawal restore the original treasury balance while leaving two immutable records."),
            t("Le reçu consolidé reprend toutes les transactions et le solde exact.", "The consolidated receipt includes every transaction and the exact balance."),
            t("Charges et Documents financiers chargent leurs données sans erreur d’autorisation.", "Charges and Financial documents load their data without an authorization error."),
            t("Le raccourci Personnel de la paie ouvre une liste en lecture seule; créer/modifier/importer reste absent.", "The payroll Staff shortcut opens a read-only list; create/edit/import remains unavailable."),
            t("Nouvel élève, import, Réglages et Emploi du temps ne sont pas proposés au profil comptable standard.", "New student, import, Settings, and Timetable are not offered to the standard Accountant profile."),
            t("Accès et responsabilités est bloqué.", "Access and responsibilities is blocked."),
        ],
        "known_gaps": [],
    },
    {
        "slug": "primary-teacher",
        "role_codes": ["teacher"],
        "title": t("Guide de l’enseignant primaire / maternelle", "Primary / Kindergarten teacher guide"),
        "subtitle": t("Travailler uniquement avec sa classe titulaire, y compris dans un groupe bilingue lié.", "Work only with the assigned homeroom class, including a linked bilingual cohort."),
        "summary": t(
            "En maternelle et au primaire, l’accès vient de l’affectation comme enseignant titulaire, pas de la présence dans l’emploi du temps. L’enseignant gère toutes les matières de sa classe et partage l’effectif de présence avec la classe bilingue liée.",
            "In Kindergarten and Primary, access comes from the homeroom assignment, not from timetable appearances. The teacher handles every subject in that class and shares the attendance roster with the linked bilingual class.",
        ),
        "scope": t("Classe(s) titulaire(s) active(s), dans le cycle et la section assignés.", "Active homeroom class(es) in the assigned level and language section."),
        "permissions": [
            (t("Élèves", "Students"), t("Voir et exporter uniquement les élèves de la classe titulaire.", "View and export only students in the homeroom class.")),
            (t("Notes", "Grades"), t("Saisir toutes les matières de sa classe, sauvegarder, soumettre et consulter les résultats.", "Enter all subjects for the homeroom class, save, submit, and review results.")),
            (t("Présence", "Attendance"), t("Faire l’appel quotidien du groupe, sauvegarder et finaliser.", "Take the cohort’s daily attendance, save, and finalize.")),
            (t("Conseil et bulletins", "Council and report cards"), t("Compléter les contributions du conseil, valider le bulletin de sa classe et générer le PDF officiel.", "Complete council input, validate the homeroom report card, and generate the official PDF.")),
            (t("Cahier de textes et communication", "Coursebook and communication"), t("Gérer le cahier de textes de la classe et utiliser Correspondance/Ressources quand disponibles.", "Manage the class coursebook and use Correspondence/Resources when available.")),
            (t("Emploi du temps", "Timetable"), t("Consulter son propre emploi du temps; pas de publication globale.", "View the personal timetable; no school-wide publishing.")),
        ],
        "workflows": [
            {
                "title": t("Vérifier son affectation", "Verify the homeroom assignment"),
                "route": "/students",
                "steps": [
                    t("Après connexion, choisissez le cycle et la section proposés.", "After sign-in, choose the offered school level and language section."),
                    t("Ouvrez Élèves: le filtre doit contenir uniquement la classe titulaire.", "Open Students: the filter should contain only the homeroom class."),
                    t("Si la liste est vide, demandez à l’administrateur de vérifier l’enseignant titulaire de la classe et les dates d’effet.", "If the list is empty, ask the administrator to verify the class homeroom teacher and effective dates."),
                ],
            },
            {
                "title": t("Saisir et soumettre les notes", "Enter and submit grades"),
                "route": "/academic",
                "steps": [
                    t("Ouvrez Saisie des notes, choisissez classe, séquence et matière.", "Open Grade entry and choose class, sequence, and subject."),
                    t("Saisissez une note sur l’échelle affichée, ou choisissez Absent/Exempté si applicable.", "Enter a mark on the displayed scale, or choose Absent/Exempt when applicable."),
                    t("Enregistrez le brouillon; corrigez tous les blocages avant l’envoi à la direction.", "Save the draft; resolve every blocker before sending it to management."),
                    t("Une feuille Acceptée et verrouillée ne peut plus être modifiée; contactez la direction pour un retour ou une réouverture.", "An Accepted and locked sheet cannot be edited; contact management for return or reopening."),
                ],
            },
            {
                "title": t("Faire l’appel quotidien", "Take daily attendance"),
                "route": "/presence",
                "steps": [
                    t("Choisissez la date et la classe ou le groupe bilingue affiché.", "Choose the date and the displayed class or bilingual cohort."),
                    t("Utilisez Tous présents, puis corrigez les absents, retards ou excusés.", "Use All present, then correct absences, lateness, or excused statuses."),
                    t("Le motif est facultatif, même pour une absence ou un statut excusé; ajoutez-le seulement si l’information est connue et utile.", "The reason is optional, including for an absence or excused status; add it only when the information is known and useful."),
                    t("Enregistrez pour conserver un brouillon, puis Finalisez quand l’appel est vérifié.", "Save to keep a draft, then Finalize once the roster is verified."),
                ],
            },
            {
                "title": t("Comprendre une classe bilingue liée", "Understand a linked bilingual class"),
                "route": "/presence",
                "steps": [
                    t("Le même élève apparaît dans les deux classes liées car l’inscription appartient à une cohorte commune.", "The same pupil appears in both linked classes because enrollment belongs to one shared cohort."),
                    t("L’appel est commun: par exemple CE1 A (FR) · Class 3 A (EN) affiche un seul effectif.", "Attendance is shared: for example CE1 A (FR) · Class 3 A (EN) shows one roster."),
                    t("Les notes, matières, enseignants et bulletins restent séparés par classe et section linguistique.", "Grades, subjects, teachers, and report cards remain separate by class and language section."),
                    t("Ne créez jamais deux dossiers élèves pour représenter les deux programmes.", "Never create two student records to represent the two programs."),
                ],
            },
            {
                "title": t("Compléter présence et conseil", "Complete attendance and council input"),
                "route": "/academic",
                "steps": [
                    t("Ouvrez Présence et conseil et choisissez la classe et la séquence.", "Open Attendance & council and choose the class and sequence."),
                    t("Vérifiez l’intervalle de dates; seuls les appels finalisés dans cet intervalle alimentent les totaux.", "Verify the date range; only finalized calls inside it feed the totals."),
                    t("Les absences non justifiées sont calculées automatiquement. Utilisez la correction manuelle uniquement si les appels finalisés sont inexacts.", "Unjustified absence is calculated automatically. Use manual correction only when finalized calls are inaccurate."),
                    t("Complétez travail, conduite, décision et observation, puis enregistrez ou soumettez.", "Complete work, conduct, decision, and observation, then save or submit."),
                ],
            },
            {
                "title": t("Valider et générer un bulletin", "Validate and generate a report card"),
                "route": "/academic",
                "page_break_before": True,
                "steps": [
                    t("Dans Bulletin, choisissez classe, période et élève.", "In Report card, choose class, period, and student."),
                    t("Contrôlez notes, moyenne, rang, présence, conseil et informations personnelles.", "Check grades, average, rank, attendance, council decision, and student details."),
                    t("Créez le brouillon si nécessaire, validez le bulletin, puis cliquez Générer le PDF officiel.", "Create the draft if needed, validate the report card, then click Generate official PDF."),
                    t("Pour une cohorte bilingue, répétez dans chaque classe afin de produire les deux bulletins distincts.", "For a bilingual cohort, repeat in each class to produce the two separate report cards."),
                ],
            },
            {
                "title": t("Tenir le cahier de textes", "Maintain the coursebook"),
                "route": "/coursebook",
                "steps": [
                    t("Choisissez votre classe puis créez l’entrée du cours ou du devoir.", "Choose the homeroom class, then create the lesson or homework entry."),
                    t("Renseignez date, matière, contenu et échéance éventuelle.", "Enter date, subject, content, and any due date."),
                    t("Relisez avant publication: ces informations peuvent être visibles par les familles.", "Review before publishing: this information may be visible to families."),
                ],
            },
        ],
        "boundaries": [
            t("Aucun accès aux autres classes, à la finance, au personnel, aux réglages ou aux permissions.", "No access to other classes, finance, staff, settings, or permissions."),
            t("L’affectation titulaire doit être active à la date de travail.", "The homeroom assignment must be active on the working date."),
            t("La présence d’une classe bilingue est partagée, mais une note saisie dans la classe FR ne devient pas une note EN.", "Bilingual attendance is shared, but a grade entered in the FR class does not become an EN grade."),
            t("Ne modifiez pas une feuille acceptée ni un appel finalisé sans procédure de réouverture.", "Do not change an accepted sheet or finalized attendance without the reopening process."),
        ],
        "verification": [
            t("Élèves affiche une seule classe titulaire et son effectif exact.", "Students shows only the homeroom class and exact roster."),
            t("Toutes les matières de la classe sont disponibles en saisie.", "All subjects for the homeroom class are available in grade entry."),
            t("La classe FR et sa classe EN liée affichent les mêmes matricules dans l’appel.", "The linked FR and EN classes show the same matricules in attendance."),
            t("L’appel peut être sauvegardé puis finalisé et devient verrouillé.", "Attendance can be saved, finalized, and then becomes locked."),
            t("Un bulletin validé peut produire un PDF officiel.", "A validated report card can generate an official PDF."),
        ],
        "known_gaps": [],
    },
    {
        "slug": "secondary-teacher",
        "role_codes": ["secondary_teacher", "form_teacher"],
        "title": t("Guide de l’enseignant secondaire", "Secondary teacher guide"),
        "subtitle": t("Distinguer l’enseignant de matière et le professeur principal sans élargir les notes autorisées.", "Distinguish subject-teacher and homeroom oversight without widening grade-edit access."),
        "summary": t(
            "Le même rôle secondaire prend ses droits dans deux sources: les matières/classes attribuées et, le cas échéant, la classe où l’enseignant est professeur principal. L’enseignant de matière saisit ses propres notes et appels; le professeur principal supervise toute sa classe sans modifier les notes de ses collègues.",
            "The Secondary role derives access from two sources: assigned class-subjects and, when applicable, the homeroom class. A subject teacher enters only assigned grades and attendance; a homeroom teacher oversees the whole class without editing colleagues’ grades.",
        ),
        "scope": t("Classes-matières attribuées et, pour le professeur principal, classe titulaire attribuée.", "Assigned class-subjects and, for a homeroom teacher, the assigned homeroom class."),
        "permissions": [
            (t("Élèves", "Students"), t("Voir les élèves des classes où une matière est attribuée; le professeur principal voit aussi toute sa classe titulaire.", "View students in assigned class-subjects; a homeroom teacher also sees the full homeroom class.")),
            (t("Notes de matière", "Subject grades"), t("Saisir et soumettre uniquement les matières explicitement attribuées.", "Enter and submit only explicitly assigned subjects.")),
            (t("Présence par période", "Period attendance"), t("Faire l’appel uniquement sur ses occurrences publiées dans l’emploi du temps.", "Take attendance only for the teacher’s published timetable occurrences.")),
            (t("Cahier de textes et emploi du temps", "Coursebook and timetable"), t("Gérer les entrées des classes-matières autorisées et consulter son planning publié.", "Manage authorized class-subject coursebook entries and view the personal published schedule.")),
            (t("Professeur principal", "Homeroom teacher"), t("Voir résultats, feuilles de toutes les matières, conseil et appels de la classe; valider/générer les bulletins et rouvrir un appel selon la règle.", "View class results, all subject sheets, council data, and class attendance; validate/generate report cards and reopen attendance when authorized.")),
            (t("Communication", "Communication"), t("Utiliser Correspondance et Tableau de bord selon les modules accordés.", "Use Correspondence and Dashboard when the modules are granted.")),
        ],
        "workflows": [
            {
                "title": t("Vérifier ses affectations", "Verify assignments"),
                "route": "/students",
                "steps": [
                    t("Dans Élèves, vérifiez que seules les classes avec matière attribuée ou classe titulaire apparaissent.", "In Students, verify that only assigned class-subjects or the homeroom class appear."),
                    t("Dans Académique → Saisie des notes, choisissez une classe: le sélecteur de matière doit contenir uniquement vos matières, sauf la vue de supervision titulaire.", "In Academic → Grade entry, choose a class: the subject selector should contain only assigned subjects, except in homeroom oversight view."),
                    t("Dans Emploi du temps, vérifiez vos cours publiés avant la première journée d’appel.", "In Timetable, verify published lessons before the first attendance day."),
                    t("Si une classe ou une matière manque, l’administrateur doit vérifier l’affectation classe-matière et ses dates.", "If a class or subject is missing, the administrator must verify the class-subject assignment and effective dates."),
                ],
            },
            {
                "title": t("Saisir les notes de sa matière", "Enter grades for an assigned subject"),
                "route": "/academic",
                "steps": [
                    t("Ouvrez Saisie des notes et choisissez classe, séquence et matière attribuée.", "Open Grade entry and choose the assigned class, sequence, and subject."),
                    t("Saisissez une note ou un statut pour chaque élève et ajoutez un commentaire si utile.", "Enter a mark or status for every student and add a comment when useful."),
                    t("Enregistrez le brouillon, corrigez les blocages puis envoyez à la direction pendant la fenêtre ouverte.", "Save the draft, resolve blockers, then send it to management while the window is open."),
                    t("Après acceptation, la feuille est verrouillée. Une correction passe par un retour/réouverture officiel.", "After acceptance, the sheet is locked. Corrections require an official return/reopen process."),
                ],
            },
            {
                "title": t("Faire l’appel d’une période publiée", "Take attendance for a published period"),
                "route": "/presence",
                "steps": [
                    t("Choisissez la date et la classe; seules vos périodes publiées doivent être proposées.", "Choose the date and class; only your published periods should be offered."),
                    t("Choisissez la période/matière exacte, utilisez Tous présents, puis corrigez les exceptions.", "Choose the exact period/subject, use All present, then correct exceptions."),
                    t("Le motif est facultatif, même pour une absence ou un statut excusé; enregistrez, relisez puis finalisez.", "The reason is optional, including for an absence or excused status; save, review, then finalize."),
                    t("Une matière attribuée sans occurrence publiée ne donne pas de période d’appel ce jour-là.", "An assigned subject without a published occurrence does not create an attendance period for that day."),
                ],
            },
            {
                "title": t("Tenir le cahier de textes", "Maintain the coursebook"),
                "route": "/coursebook",
                "steps": [
                    t("Choisissez une classe autorisée et créez une entrée liée à la matière enseignée.", "Choose an authorized class and create an entry for the taught subject."),
                    t("Renseignez séance, contenu, devoir et échéance, puis vérifiez la visibilité famille.", "Enter lesson, content, homework, and due date, then verify family visibility."),
                    t("Ne créez pas d’entrée pour une classe-matière qui ne vous est pas attribuée.", "Do not create an entry for an unassigned class-subject."),
                ],
            },
            {
                "title": t("Superviser sa classe comme professeur principal", "Oversee the homeroom class"),
                "route": "/academic",
                "steps": [
                    t("Utilisez Aperçu de classe et Feuille maîtresse pour contrôler toutes les matières.", "Use Class overview and Master sheet to review every subject."),
                    t("Dans Saisie des notes, les feuilles des collègues peuvent être visibles en lecture; elles ne deviennent pas modifiables.", "In Grade entry, colleagues’ sheets can be visible for review; they do not become editable."),
                    t("Dans Présence et conseil, consultez les totaux automatiques et les contributions; la saisie du conseil reste en lecture seule au secondaire sauf délégation explicite.", "In Attendance & council, review automatic totals and contributions; Secondary council input remains read-only without explicit delegation."),
                    t("Demandez la correction à l’enseignant de matière ou à la direction au lieu de modifier sa feuille.", "Request corrections from the subject teacher or management instead of editing the colleague’s sheet."),
                ],
            },
            {
                "title": t("Valider et générer les bulletins de sa classe", "Validate and generate homeroom report cards"),
                "route": "/academic",
                "steps": [
                    t("Ouvrez Bulletin, choisissez la classe titulaire, la période et l’élève.", "Open Report card and choose the homeroom class, period, and student."),
                    t("Contrôlez toutes les matières, moyenne, rang, présence, décision et identité.", "Review every subject, average, rank, attendance, decision, and identity data."),
                    t("Validez le bulletin lorsque les feuilles sont acceptées, puis générez le PDF officiel.", "Validate the report card once subject sheets are accepted, then generate the official PDF."),
                    t("Un enseignant de matière non titulaire ne dispose pas de cette génération classe-entière.", "A non-homeroom subject teacher does not receive this class-wide generation capability."),
                ],
            },
            {
                "title": t("Consulter et rouvrir la présence titulaire", "Review and reopen homeroom attendance"),
                "route": "/presence",
                "steps": [
                    t("Le professeur principal peut consulter toutes les périodes de sa classe pour le suivi.", "The homeroom teacher can view all periods for class oversight."),
                    t("Il peut rouvrir un appel finalisé lorsque la politique l’autorise et qu’un motif est fourni.", "The teacher can reopen finalized attendance when policy permits and a reason is supplied."),
                    t("Il ne doit pas marquer ni finaliser la période d’un collègue sans affectation ou délégation.", "The teacher must not mark or finalize a colleague’s period without an assignment or delegation."),
                ],
            },
        ],
        "boundaries": [
            t("L’accès élève ne suffit pas pour modifier toutes les matières d’une classe.", "Student-roster access does not grant edit access to every subject in the class."),
            t("Seule une occurrence publiée et attribuée autorise la saisie/finalisation de présence secondaire.", "Only an assigned published occurrence authorizes Secondary attendance marking/finalization."),
            t("Le professeur principal supervise mais ne remplace pas les enseignants de matière.", "The homeroom teacher oversees the class but does not replace subject teachers."),
            t("Aucun accès à finance, personnel, réglages ou permissions.", "No access to finance, staff, settings, or permissions."),
        ],
        "verification": [
            t("L’enseignant de Français en 6ème A voit uniquement Français dans Saisie des notes.", "The 6ème A French teacher sees only Français in Grade entry."),
            t("Pour une date donnée, l’appel affiche uniquement ses occurrences publiées.", "For a given date, attendance shows only the teacher’s published occurrences."),
            t("Le professeur principal voit toutes les matières mais une décision ACADEMIC_SUBJECT_GRADE_EDIT reste refusée pour celles des collègues.", "The homeroom teacher sees every subject, but ACADEMIC_SUBJECT_GRADE_EDIT remains denied for colleagues’ subjects."),
            t("Le conseil de classe est en lecture seule et le bulletin officiel peut être généré par le titulaire.", "Council input is read-only and the official report card can be generated by the homeroom teacher."),
            t("Un enseignant non titulaire n’a pas l’onglet Bulletin.", "A non-homeroom teacher does not have the Report card tab."),
        ],
        "known_gaps": [],
    },
    {
        "slug": "prefect",
        "role_codes": ["prefect"],
        "title": t("Guide du surveillant général", "Prefect guide"),
        "subtitle": t("Superviser la vie scolaire sur tous les parcours sans administrer la pédagogie ni les accès.", "Oversee school life across all parcours without administering teaching or access."),
        "summary": t(
            "Le surveillant général suit la présence, les incidents, les sanctions, les alertes et la communication de vie scolaire pour l’ensemble de l’établissement. Il consulte le contexte élève nécessaire au suivi, mais ne saisit pas les notes, ne décide pas les promotions et n’administre ni la finance, ni le personnel, ni les permissions.",
            "The Prefect monitors attendance, incidents, sanctions, alerts, and school-life communication across the school. The role can read the student context needed for follow-up, but does not enter grades, decide promotions, or administer finance, staff, or permissions.",
        ),
        "scope": t("Tous les parcours, uniquement pour la présence, la discipline et le suivi de vie scolaire.", "All parcours, limited to attendance, discipline, and school-life follow-up."),
        "permissions": [
            (t("Répertoire élèves", "Student directory"), t("Rechercher et consulter les élèves de tous les parcours pour identifier la classe, la famille et le contexte de suivi; aucune inscription ni import.", "Search and view students across all parcours to identify class, family, and follow-up context; no registration or import.")),
            (t("Présence", "Attendance"), t("Consulter les appels et analyses, traiter les anomalies, rapprocher les données et rouvrir/corriger avec motif lorsque la politique l’autorise.", "Review rosters and analytics, handle anomalies, reconcile data, and reopen/correct with a reason when policy permits.")),
            (t("Discipline", "Discipline"), t("Créer et suivre incidents, sanctions, convocations et notifications aux responsables.", "Create and follow incidents, sanctions, summonses, and guardian notifications.")),
            (t("Vie scolaire", "School life"), t("Consulter parcours, informations de santé non confidentielles, documents utiles et correspondance dans la limite du besoin de suivi.", "Review journey, non-confidential health information, relevant documents, and correspondence as needed for follow-up.")),
            (t("Alertes et rapports", "Alerts and reports"), t("Consulter les indicateurs de présence/discipline, traiter les alertes autorisées et produire le suivi opérationnel.", "Review attendance/discipline indicators, handle authorized alerts, and produce operational follow-up.")),
            (t("Ressources", "Resources"), t("Consulter les ressources et listes publiées; ne pas administrer les catalogues scolaires.", "View published resources and lists; do not administer school catalogues.")),
        ],
        "workflows": [
            {
                "title": t("Vérifier le périmètre école", "Verify school-wide scope"),
                "route": "/students",
                "steps": [
                    t("Après connexion, vérifiez que l’en-tête indique Tous les parcours.", "After sign-in, verify that the header says All parcours."),
                    t("Ouvrez Élèves: le filtre doit proposer toutes les classes sans bouton Nouvel élève ni Import.", "Open Students: the filter should offer every class without New student or Import controls."),
                    t("Recherchez un élève par nom ou matricule et contrôlez sa classe avant toute intervention.", "Search by name or matricule and verify the class before any intervention."),
                    t("Si les classes sont absentes, arrêtez le suivi et signalez un défaut de profil au responsable des permissions.", "If classes are missing, stop the workflow and report a role-profile defect to the permission administrator."),
                ],
            },
            {
                "title": t("Superviser la présence", "Oversee attendance"),
                "route": "/presence",
                "steps": [
                    t("Choisissez date et classe, puis ouvrez l’appel quotidien ou la période publiée concernée.", "Choose date and class, then open the relevant daily roster or published period."),
                    t("Contrôlez absences, retards, excusés, finalisation et éventuelles anomalies de pointage.", "Review absences, lateness, excused statuses, finalization, and any check-in anomalies."),
                    t("Une correction ou réouverture doit avoir un motif précis et conserver la trace de l’appel initial.", "A correction or reopening must include a precise reason and preserve the original attendance trace."),
                    t("L’enseignant responsable reste propriétaire du premier appel; n’intervenez que selon la procédure de l’établissement.", "The responsible teacher owns the initial roll call; intervene only under the school procedure."),
                ],
            },
            {
                "title": t("Enregistrer un incident disciplinaire", "Record a discipline incident"),
                "route": "/discipline",
                "steps": [
                    t("Cliquez Nouvel incident, puis choisissez la classe et l’élève exacts.", "Click New incident, then choose the exact class and student."),
                    t("Renseignez date, type, description factuelle et sanction éventuelle.", "Enter the date, type, factual description, and any sanction."),
                    t("Enregistrez, relisez l’historique puis envoyez la convocation ou notification validée.", "Save, review the history, then send the approved summons or notification."),
                    t("Ne recopiez pas d’informations médicales confidentielles dans la description.", "Do not copy confidential medical information into the description."),
                ],
            },
            {
                "title": t("Traiter les alertes", "Handle alerts"),
                "route": "/alerts",
                "steps": [
                    t("Filtrez d’abord Présence ou Discipline; le financier et les notes restent hors mandat.", "Filter Attendance or Discipline first; finance and grades remain outside the mandate."),
                    t("Ouvrez l’alerte, vérifiez l’élève et les faits sources, puis accusez réception.", "Open the alert, verify the student and source facts, then acknowledge it."),
                    t("Consignez l’action de suivi et clôturez uniquement lorsque la situation est réellement traitée.", "Record the follow-up action and close only when the situation has actually been handled."),
                ],
            },
            {
                "title": t("Consulter la vie scolaire d’un élève", "Review a student's school life"),
                "route": "/journey",
                "steps": [
                    t("Filtrez par classe, choisissez l’élève et confirmez son identité.", "Filter by class, choose the student, and confirm identity."),
                    t("Consultez uniquement le parcours, la présence, la discipline, la correspondance et les informations non confidentielles utiles.", "Review only the journey, attendance, discipline, correspondence, and relevant non-confidential information."),
                    t("Toute correction du dossier permanent doit être transmise à l’administrateur habilité.", "Send any permanent-record correction to an authorized administrator."),
                ],
            },
            {
                "title": t("Produire le suivi opérationnel", "Produce operational follow-up"),
                "route": "/reports",
                "steps": [
                    t("Choisissez le rapport de présence ou de discipline et la période exacte.", "Choose the attendance or discipline report and the exact period."),
                    t("Vérifiez le parcours, la classe et les filtres avant export.", "Verify parcours, class, and filters before export."),
                    t("Partagez le rapport uniquement avec les destinataires autorisés et selon la confidentialité requise.", "Share the report only with authorized recipients and under the required confidentiality."),
                ],
            },
        ],
        "boundaries": [
            t("Aucune saisie de notes, validation de bulletin ou décision de promotion.", "No grade entry, report-card validation, or promotion decision."),
            t("Aucune publication d’emploi du temps ni modification de la structure scolaire.", "No timetable publishing or school-structure changes."),
            t("Aucun accès à Finance, Personnel, Réglages ou Accès et responsabilités.", "No access to Finance, Staff, Settings, or Access and responsibilities."),
            t("Les informations médicales confidentielles et les données financières restent hors périmètre.", "Confidential medical and financial data remain out of scope."),
            t("Aucun effacement d’incident ou d’appel finalisé; utilisez correction, clôture ou réouverture traçable.", "Never erase an incident or finalized attendance; use a traceable correction, closure, or reopening."),
        ],
        "verification": [
            t("Toutes les classes apparaissent dans Élèves et dans l’appel, mais l’inscription/import est absent.", "All classes appear in Students and Attendance, while registration/import is absent."),
            t("Un incident peut être enregistré puis retrouvé dans l’historique avec son auteur.", "An incident can be saved and found in history with its author."),
            t("Les alertes de présence/discipline contiennent des données réelles et peuvent être suivies.", "Attendance/discipline alerts contain real data and can be followed up."),
            t("Académique est en consultation strictement nécessaire; les notes, bulletins et promotions ne sont pas modifiables.", "Academic access is limited to necessary oversight; grades, report cards, and promotions are not editable."),
            t("Les URL Finance, Personnel, Réglages et Permissions sont refusées.", "Finance, Staff, Settings, and Permissions URLs are denied."),
        ],
        "known_gaps": [
            t("Le profil Prefect local annonce Élèves, Présence, Discipline, Parcours, Santé, Documents et Correspondance, mais Élèves redirige et les autres écrans n’obtiennent aucune classe. Les modules hérités et les actions Permission Policy V2 doivent être alignés avant utilisation.", "The local Prefect profile advertises Students, Attendance, Discipline, Journey, Health, Documents, and Correspondence, but Students redirects and the other screens receive no classes. Legacy modules and Permission Policy V2 actions must be aligned before use."),
            t("Emploi du temps affiche une erreur d’autorisation et seulement un planning enseignant vide, alors que le module est annoncé en écriture. Le lien doit être retiré ou remplacé par une vue de supervision explicitement autorisée.", "Timetable shows an authorization error and only an empty teacher schedule even though the module is advertised as write-enabled. Remove the link or replace it with an explicitly authorized oversight view."),
            t("Promotion expose actuellement toutes les classes et des contrôles de décision modifiables au Prefect. Cet accès à haut risque ne fait pas partie du mandat et doit être refusé côté interface et serveur.", "Promotion currently exposes every class and editable decision controls to the Prefect. This high-risk access is outside the mandate and must be denied in both UI and server."),
            t("Tableau de bord, Alertes et Rapports s’ouvrent mais restent à zéro ou indiquent que les données sont indisponibles; leurs actions de lecture doivent être alignées avec le périmètre de vie scolaire.", "Dashboard, Alerts, and Reports open but remain at zero or say data is unavailable; their read actions must be aligned with the school-life scope."),
        ],
    },
    {
        "slug": "parent",
        "role_codes": ["parent"],
        "title": t("Guide du parent / tuteur", "Parent / guardian guide"),
        "subtitle": t("Suivre uniquement les enfants liés et les rubriques autorisées par l’établissement.", "Follow only linked children and the sections enabled by the school."),
        "summary": t(
            "Le portail parent regroupe scolarité, vie scolaire, frais, notes, fournitures, documents et messages. Chaque rubrique dépend du lien familial: un parent ne voit jamais un autre élève.",
            "The parent portal brings together academic journey, school life, fees, grades, supplies, documents, and messages. Every section depends on the guardian relationship: a parent never sees another student.",
        ),
        "scope": t("Enfant(s) explicitement lié(s) au compte avec accès portail actif.", "Child or children explicitly linked to the account with active portal access."),
        "permissions": [
            (t("Aperçu", "Overview"), t("Voir enfant, classe, état des frais, présence et nombre d’évaluations visibles.", "View child, class, fee status, attendance, and visible assessment count.")),
            (t("Parcours officiel", "Official journey"), t("Consulter les résultats et décisions publiés pour la famille.", "View published results and family-visible decisions.")),
            (t("Vie scolaire", "School life"), t("Voir présence finalisée, discipline visible, visites de santé non confidentielles, événements et correspondances.", "View finalized attendance, parent-visible discipline, non-confidential health visits, events, and correspondence.")),
            (t("Frais et paiements", "Fees and payments"), t("Voir montant facturé, payé, solde, échéancier, méthodes de paiement et reçus.", "View billed amount, paid amount, balance, schedule, payment methods, and receipts.")),
            (t("Notes et documents", "Grades and documents"), t("Voir les notes publiées, bulletins/documents mis à disposition, fournitures et manuels.", "View published grades, released report cards/documents, supplies, and textbooks.")),
            (t("Boîte à suggestions", "Suggestion box"), t("Envoyer suggestion, question, plainte ou remerciement concernant l’enfant sélectionné.", "Send a suggestion, question, complaint, or thanks about the selected child.")),
        ],
        "workflows": [
            {
                "title": t("Activer l’accès au portail", "Activate portal access"),
                "route": "/login",
                "steps": [
                    t("L’établissement ajoute une adresse e-mail au parent depuis la fiche élève et active Accès portail.", "The school adds the guardian’s email from the student record and enables Portal access."),
                    t("Le parent accepte l’invitation ou reçoit des identifiants selon le mode choisi.", "The guardian accepts the invitation or receives credentials according to the selected mode."),
                    t("Connectez-vous avec l’adresse/identifiant fourni et le mot de passe personnel.", "Sign in with the provided email/username and personal password."),
                    t("Si le parent n’a pas d’e-mail, son contact peut exister sans portail; l’accès peut être activé plus tard.", "When a guardian has no email, the contact can exist without portal access; access can be enabled later."),
                ],
            },
            {
                "title": t("Changer d’enfant", "Switch child"),
                "route": "/parent",
                "steps": [
                    t("Dans Mes enfants, cliquez la carte de l’enfant à consulter.", "Under My children, click the child to review."),
                    t("Vérifiez nom, matricule et classe affichés avant de lire une rubrique.", "Verify the displayed name, matricule, and class before reading a section."),
                    t("Toutes les rubriques suivantes se mettent à jour pour l’enfant sélectionné.", "Every following section updates for the selected child."),
                ],
            },
            {
                "title": t("Lire l’aperçu et le parcours officiel", "Read Overview and Official journey"),
                "route": "/parent",
                "steps": [
                    t("Aperçu résume présence, frais et évaluations visibles.", "Overview summarizes visible attendance, fees, and assessments."),
                    t("Parcours officiel affiche uniquement les résultats et décisions publiés.", "Official journey shows only published results and decisions."),
                    t("Une rubrique vide signifie qu’aucune donnée officielle n’a encore été publiée.", "An empty section means no official data has been published yet."),
                ],
            },
            {
                "title": t("Suivre la vie scolaire", "Review school life"),
                "route": "/parent",
                "steps": [
                    t("Ouvrez Vie scolaire pour la présence, les absences, retards et excusés finalisés.", "Open School life for finalized presence, absence, lateness, and excused counts."),
                    t("Consultez discipline, santé parent-visible, événements et correspondance.", "Review discipline, parent-safe health entries, events, and correspondence."),
                    t("Les données médicales confidentielles ne sont jamais exposées dans le portail.", "Confidential medical records are never exposed in the portal."),
                ],
            },
            {
                "title": t("Vérifier les frais et payer", "Review fees and pay"),
                "route": "/parent",
                "steps": [
                    t("Ouvrez Frais et paiements pour voir frais de classe, déjà payé, reste et prochaine tranche.", "Open Fees & payments to view class fees, paid amount, outstanding balance, and next instalment."),
                    t("Suivez les instructions du mode accepté et conservez toujours la référence de transaction.", "Follow the accepted-method instructions and always keep the transaction reference."),
                    t("Remettez la référence à la comptabilité; le paiement apparaît après enregistrement et comptabilisation.", "Give the reference to the bursary; the payment appears after it is recorded and posted."),
                    t("Vérifiez ensuite la ligne de reçu et le nouveau solde.", "Then verify the receipt row and updated balance."),
                ],
            },
            {
                "title": t("Consulter notes, fournitures et documents", "View grades, supplies, and documents"),
                "route": "/parent",
                "steps": [
                    t("Notes affiche uniquement les évaluations rendues visibles par l’établissement.", "Grades shows only assessments released by the school."),
                    t("Fournitures et manuels affiche les listes publiées pour la classe.", "Supplies & textbooks shows lists published for the class."),
                    t("Documents scolaires contient les fichiers explicitement partagés aux familles.", "School documents contains files explicitly shared with families."),
                ],
            },
            {
                "title": t("Envoyer un message", "Send a message"),
                "route": "/parent",
                "steps": [
                    t("Ouvrez Boîte à suggestions et choisissez Suggestion, Question, Plainte ou Remerciement.", "Open Suggestion box and choose Suggestion, Question, Complaint, or Thanks."),
                    t("Rédigez un message suffisamment précis puis cliquez Envoyer le message.", "Write a sufficiently specific message, then click Send message."),
                    t("Retrouvez le suivi dans Mes messages.", "Track it under My messages."),
                ],
            },
        ],
        "boundaries": [
            t("Le compte ne donne accès qu’aux enfants liés et aux options activées dans la relation familiale.", "The account accesses only linked children and guardian-enabled options."),
            t("Aucun accès aux écrans Personnel, Élèves, Académique, Finance interne, Réglages ou Permissions.", "No access to Staff, Students, Academic, internal Finance, Settings, or Permissions."),
            t("Les données non publiées ou confidentielles ne sont pas visibles.", "Unpublished or confidential data is not visible."),
            t("Une correction de données doit être demandée à l’établissement; le parent ne modifie pas le dossier élève.", "Data corrections must be requested from the school; a parent cannot edit the student record."),
        ],
        "verification": [
            t("Le nombre d’enfants correspond aux relations actives du compte.", "The child count matches active guardian relationships."),
            t("Frais et paiements affiche le même solde que la comptabilité.", "Fees & payments shows the same balance as the bursary."),
            t("Une URL vers un module du personnel redirige vers l’accueil sans données.", "A URL to a staff module redirects to a data-free home page."),
            t("La boîte à suggestions reste liée à l’enfant sélectionné.", "The suggestion box remains tied to the selected child."),
        ],
        "known_gaps": [
            t("L’en-tête affiche actuellement Tous les parcours pour un parent; il devrait afficher Mes enfants.", "The header currently says All parcours for a parent; it should say My children."),
            t("Le lien Applications ouvre une page vide pour le parent; il devrait revenir directement à l’espace parent.", "The Apps link opens an empty page for a parent; it should return directly to the parent space."),
        ],
    },
]


GUIDE_BY_SLUG = {guide["slug"]: guide for guide in ROLE_GUIDES}

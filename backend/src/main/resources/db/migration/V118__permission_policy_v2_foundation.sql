-- Permission Policy V2 foundation.
--
-- This migration is deliberately additive.  Existing module grants and
-- boolean action grants remain intact while the new policy tables are
-- populated with an equivalent, auditable representation.  Enforcement is
-- enabled by the application in later, reviewed phases.

CREATE TABLE IF NOT EXISTS permission_action (
    code                 VARCHAR(96) PRIMARY KEY,
    module               VARCHAR(32) NOT NULL,
    group_code           VARCHAR(32) NOT NULL,
    label_fr             VARCHAR(160) NOT NULL,
    label_en             VARCHAR(160) NOT NULL,
    description_fr       VARCHAR(500) NOT NULL DEFAULT '',
    description_en       VARCHAR(500) NOT NULL DEFAULT '',
    risk_level           VARCHAR(16) NOT NULL DEFAULT 'LOW'
        CHECK (risk_level IN ('LOW','MEDIUM','HIGH','CRITICAL')),
    scope_type           VARCHAR(32) NOT NULL DEFAULT 'NONE'
        CHECK (scope_type IN ('NONE','STUDENT','CLASS','CLASS_SUBJECT',
                              'TIMETABLE_OCCURRENCE','PARCOURS','SCHOOL',
                              'SELF','CHILD')),
    required_level       VARCHAR(8) NOT NULL DEFAULT 'read'
        CHECK (required_level IN ('none','read','write')),
    default_read_action  BOOLEAN NOT NULL DEFAULT false,
    active               BOOLEAN NOT NULL DEFAULT true,
    display_order        INT NOT NULL DEFAULT 0,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- The catalogue is the single checked-in vocabulary shared by the backend,
-- migrations and the Access Control workspace.  Labels are generated from the
-- stable code for the initial seed; the API may localize/override them later.
WITH actions(code, module, group_code, risk_level, scope_type, required_level,
             default_read_action, display_order) AS (VALUES
    ('STUDENT_DIRECTORY_VIEW','students','Students','LOW','STUDENT','read',true,10),
    ('STUDENT_PROFILE_VIEW','students','Students','LOW','STUDENT','read',true,11),
    ('STUDENT_PROFILE_CREATE','students','Students','HIGH','SCHOOL','write',false,12),
    ('STUDENT_PROFILE_EDIT','students','Students','HIGH','STUDENT','write',false,13),
    ('STUDENT_PROFILE_DEACTIVATE','students','Students','CRITICAL','STUDENT','write',false,14),
    ('STUDENT_PHOTO_VIEW','students','Students','LOW','STUDENT','read',true,15),
    ('STUDENT_PHOTO_MANAGE','students','Students','HIGH','STUDENT','write',false,16),
    ('STUDENT_IMPORT','students','Students','CRITICAL','SCHOOL','write',false,17),
    ('GUARDIAN_VIEW','students','Students','MEDIUM','STUDENT','read',true,18),
    ('GUARDIAN_LINK_MANAGE','students','Students','CRITICAL','STUDENT','write',false,19),
    ('GUARDIAN_ACCOUNT_MANAGE','students','Students','CRITICAL','STUDENT','write',false,20),
    ('GUARDIAN_DIRECTORY_SEARCH','students','Students','MEDIUM','SCHOOL','read',false,28),
    ('GUARDIAN_DIRECTORY_MANAGE','students','Students','CRITICAL','SCHOOL','write',false,29),
    ('ENROLLMENT_VIEW','students','Students','LOW','STUDENT','read',true,21),
    ('ENROLLMENT_CREATE','students','Students','HIGH','STUDENT','write',false,22),
    ('ENROLLMENT_TRANSFER','students','Students','CRITICAL','STUDENT','write',false,23),
    ('ENROLLMENT_WITHDRAW','students','Students','CRITICAL','STUDENT','write',false,24),
    ('STUDENT_DOCUMENT_VIEW','documents','Students','LOW','STUDENT','read',true,25),
    ('STUDENT_DOCUMENT_GENERATE','documents','Students','MEDIUM','STUDENT','write',false,26),
    ('STUDENT_DOCUMENT_REVOKE','documents','Students','CRITICAL','STUDENT','write',false,27),

    ('ACADEMIC_ROSTER_VIEW','academic','Academic','LOW','CLASS','read',true,100),
    ('ACADEMIC_ASSESSMENT_VIEW','academic','Academic','LOW','CLASS_SUBJECT','read',true,101),
    ('ACADEMIC_ASSESSMENT_MANAGE','academic','Academic','HIGH','CLASS_SUBJECT','write',false,102),
    ('ACADEMIC_SUBJECT_GRADE_VIEW','academic','Academic','LOW','CLASS_SUBJECT','read',true,103),
    ('ACADEMIC_SUBJECT_GRADE_EDIT','academic','Academic','HIGH','CLASS_SUBJECT','write',false,104),
    ('GRADE_SUBMIT','academic','Academic','HIGH','CLASS_SUBJECT','write',false,105),
    ('GRADE_EDIT_ANY_SUBJECT_IN_TITULAIRE_CLASS','academic','Academic','HIGH','CLASS','write',false,106),
    ('ACADEMIC_CLASS_RESULTS_VIEW','academic','Academic','MEDIUM','CLASS','read',true,107),
    ('ACADEMIC_REPORT_CARD_VIEW','academic','Academic','MEDIUM','CLASS','read',true,108),
    ('ACADEMIC_GRADE_PACKET_REVIEW','academic','Academic','HIGH','CLASS','write',false,109),
    ('ACADEMIC_REPORT_CARD_VALIDATE','academic','Academic','CRITICAL','CLASS','write',false,110),
    ('ACADEMIC_REPORT_CARD_PUBLISH','academic','Academic','CRITICAL','CLASS','write',false,111),
    ('ACADEMIC_COUNCIL_INPUT_VIEW','academic','Academic','MEDIUM','CLASS','read',true,112),
    ('ACADEMIC_COUNCIL_INPUT_EDIT','academic','Academic','HIGH','CLASS','write',false,113),
    ('ACADEMIC_ACCESS_DELEGATE','settings','Academic','CRITICAL','CLASS_SUBJECT','write',false,114),
    ('ACADEMIC_ACCESS_AUDIT_VIEW','settings','Academic','MEDIUM','SCHOOL','read',true,115),
    ('ACADEMIC_WINDOW_OVERRIDE','settings','Academic','CRITICAL','SCHOOL','write',false,116),
    ('BULLETIN_VALIDATE','academic','Academic','CRITICAL','CLASS','write',false,117),
    ('BULLETIN_PUBLISH','academic','Academic','CRITICAL','CLASS','write',false,118),
    ('PROMOTION_RECOMMEND','academic','Academic','HIGH','CLASS','write',false,119),
    ('PROMOTION_OVERRIDE','academic','Academic','CRITICAL','CLASS','write',false,120),
    ('PROMOTION_COMMIT','academic','Academic','CRITICAL','SCHOOL','write',false,121),
    ('PROGRESSION_VIEW','journey','Academic','LOW','STUDENT','read',true,122),
    ('PROGRESSION_CONFIGURE','journey','Academic','HIGH','SCHOOL','write',false,123),
    ('PROMOTION_REVIEW','journey','Academic','HIGH','CLASS','write',false,124),
    ('PROMOTION_CONFIGURE','journey','Academic','HIGH','SCHOOL','write',false,125),
    ('PROMOTION_CORRECT','journey','Academic','CRITICAL','STUDENT','write',false,126),

    ('ATTENDANCE_ROSTER_VIEW','presence','Attendance','LOW','CLASS','read',true,200),
    ('ATTENDANCE_MARK','presence','Attendance','HIGH','TIMETABLE_OCCURRENCE','write',false,201),
    ('ATTENDANCE_FINALIZE','presence','Attendance','HIGH','TIMETABLE_OCCURRENCE','write',false,202),
    ('ATTENDANCE_REOPEN','presence','Attendance','CRITICAL','SCHOOL','write',false,203),
    ('ATTENDANCE_ANALYTICS_VIEW','presence','Attendance','MEDIUM','CLASS','read',true,204),
    ('ATTENDANCE_POLICY_MANAGE','presence','Attendance','HIGH','SCHOOL','write',false,205),
    ('ATTENDANCE_RECONCILE','presence','Attendance','HIGH','SCHOOL','write',false,206),
    ('ATTENDANCE_POLICY_VIEW','presence','Attendance','LOW','SCHOOL','read',true,207),
    ('ATTENDANCE_DEVICE_VIEW','presence','Attendance','MEDIUM','SCHOOL','read',true,208),
    ('ATTENDANCE_NOTIFICATION_VIEW','presence','Attendance','MEDIUM','SCHOOL','read',true,209),

    ('TIMETABLE_MY_SCHEDULE_VIEW','timetable','Timetable','LOW','SELF','read',true,300),
    ('TIMETABLE_CLASS_SCHEDULE_VIEW','timetable','Timetable','MEDIUM','CLASS','read',true,301),
    ('TIMETABLE_MASTER_VIEW','timetable','Timetable','HIGH','SCHOOL','read',true,302),
    ('TIMETABLE_TEACHER_SCHEDULE_VIEW_ALL','timetable','Timetable','HIGH','SCHOOL','read',true,303),
    ('TIMETABLE_ROOM_VIEW','timetable','Timetable','MEDIUM','SCHOOL','read',true,304),
    ('TIMETABLE_RESOURCE_VIEW','timetable','Timetable','MEDIUM','SCHOOL','read',true,305),
    ('TIMETABLE_DRAFT','timetable','Timetable','HIGH','SCHOOL','write',false,306),
    ('TIMETABLE_PUBLISH','timetable','Timetable','CRITICAL','SCHOOL','write',false,307),
    ('TIMETABLE_REOPEN','timetable','Timetable','HIGH','SCHOOL','write',false,308),
    ('TIMETABLE_ARCHIVE','timetable','Timetable','HIGH','SCHOOL','write',false,309),
    ('TIMETABLE_SUBSTITUTION_VIEW','timetable','Timetable','MEDIUM','TIMETABLE_OCCURRENCE','read',true,310),
    ('TIMETABLE_SUBSTITUTION_MANAGE','timetable','Timetable','HIGH','TIMETABLE_OCCURRENCE','write',false,311),
    ('TIMETABLE_EXPORT','timetable','Timetable','HIGH','SCHOOL','read',false,312),
    ('TIMETABLE_OVERRIDE','timetable','Timetable','CRITICAL','TIMETABLE_OCCURRENCE','write',false,313),

    ('SCHOOL_PROFILE_VIEW','settings','Settings','LOW','SCHOOL','read',true,400),
    ('SCHOOL_PROFILE_MANAGE','settings','Settings','HIGH','SCHOOL','write',false,401),
    ('SESSION_VIEW','settings','Settings','LOW','SCHOOL','read',true,402),
    ('SESSION_MANAGE','settings','Settings','CRITICAL','SCHOOL','write',false,403),
    ('ACADEMIC_STRUCTURE_VIEW','settings','Settings','LOW','SCHOOL','read',true,404),
    ('CLASS_MANAGE','settings','Settings','HIGH','SCHOOL','write',false,405),
    ('SUBJECT_MANAGE','settings','Settings','HIGH','SCHOOL','write',false,406),
    ('CURRICULUM_MANAGE','settings','Settings','HIGH','CLASS_SUBJECT','write',false,407),
    ('TEACHING_ASSIGNMENT_MANAGE','settings','Settings','CRITICAL','CLASS_SUBJECT','write',false,408),
    ('CALENDAR_VIEW','settings','Settings','LOW','SCHOOL','read',true,409),
    ('CALENDAR_MANAGE','settings','Settings','HIGH','SCHOOL','write',false,410),
    ('DISCIPLINE_CATALOG_MANAGE','settings','Settings','HIGH','SCHOOL','write',false,411),
    ('MAIL_CONFIG_MANAGE','settings','Settings','CRITICAL','SCHOOL','write',false,412),
    ('ROLE_VIEW','settings','Settings','MEDIUM','SCHOOL','read',true,413),
    ('ROLE_MANAGE','settings','Settings','CRITICAL','SCHOOL','write',false,414),
    ('PERMISSION_VIEW','settings','Settings','HIGH','SCHOOL','read',true,415),
    ('PERMISSION_MANAGE','settings','Settings','CRITICAL','SCHOOL','write',false,416),
    ('AUDIT_VIEW','settings','Settings','HIGH','SCHOOL','read',true,417),
    ('DOCUMENT_DESIGN_PUBLISH','settings','Settings','CRITICAL','SCHOOL','write',false,418),
    ('PARENT_PROFILE_MANAGE','settings','Parent','HIGH','SCHOOL','write',false,419),

    ('FINANCE_OVERVIEW_VIEW','finance','Finance','LOW','SCHOOL','read',true,500),
    ('FEE_CONFIGURE','finance','Finance','HIGH','SCHOOL','write',false,501),
    ('FEE_TYPE_MANAGE','finance','Finance','HIGH','SCHOOL','write',false,502),
    ('FEE_PLAN_DRAFT','finance','Finance','HIGH','SCHOOL','write',false,503),
    ('FEE_PLAN_ACTIVATE','finance','Finance','CRITICAL','SCHOOL','write',false,504),
    ('CHARGE_PREVIEW','finance','Finance','LOW','SCHOOL','read',true,505),
    ('CHARGE_GENERATE','finance','Finance','HIGH','SCHOOL','write',false,506),
    ('CHARGE_ADJUST','finance','Finance','HIGH','STUDENT','write',false,507),
    ('FEE_WAIVE_REQUEST','finance','Finance','HIGH','STUDENT','write',false,508),
    ('FEE_WAIVE_APPROVE','finance','Finance','CRITICAL','STUDENT','write',false,509),
    ('PAYMENT_COLLECT','finance','Finance','HIGH','STUDENT','write',false,510),
    ('PAYMENT_VIEW','finance','Finance','LOW','STUDENT','read',true,511),
    ('PAYMENT_REVERSE','finance','Finance','CRITICAL','STUDENT','write',false,512),
    ('REFUND_REQUEST','finance','Finance','HIGH','STUDENT','write',false,513),
    ('REFUND_APPROVE','finance','Finance','CRITICAL','STUDENT','write',false,514),
    ('CASHIER_SESSION_OPEN','finance','Finance','HIGH','SCHOOL','write',false,515),
    ('CASHIER_SESSION_CLOSE','finance','Finance','HIGH','SCHOOL','write',false,516),
    ('CASHIER_SESSION_APPROVE','finance','Finance','CRITICAL','SCHOOL','write',false,517),
    ('PROVIDER_CALLBACK_REVIEW','finance','Finance','HIGH','SCHOOL','write',false,518),
    ('FINANCE_DOCUMENT_GENERATE','finance','Finance','MEDIUM','STUDENT','write',false,519),
    ('FINANCE_DOCUMENT_VOID','finance','Finance','CRITICAL','STUDENT','write',false,520),
    ('FINANCE_DOCUMENT_VIEW','finance','Finance','LOW','STUDENT','read',true,521),
    ('FINANCE_DOCUMENT_SUPERSEDE','finance','Finance','CRITICAL','STUDENT','write',false,522),
    ('FINANCE_DOCUMENT_BATCH','finance','Finance','HIGH','SCHOOL','write',false,523),
    ('ACCOUNT_MANAGE','finance','Finance','HIGH','SCHOOL','write',false,524),
    ('POSTING_RULE_MANAGE','finance','Finance','HIGH','SCHOOL','write',false,525),
    ('LEDGER_POST','finance','Finance','HIGH','SCHOOL','write',false,526),
    ('LEDGER_REVERSE','finance','Finance','CRITICAL','SCHOOL','write',false,527),
    ('LEDGER_CLOSE','finance','Finance','CRITICAL','SCHOOL','write',false,528),
    ('LEDGER_REOPEN','finance','Finance','CRITICAL','SCHOOL','write',false,529),
    ('PAYROLL_CALCULATE','hr','Finance','HIGH','SCHOOL','write',false,530),
    ('PAYROLL_VIEW','hr','Finance','MEDIUM','SCHOOL','read',true,531),
    ('PAYROLL_PERIOD_MANAGE','hr','Finance','HIGH','SCHOOL','write',false,532),
    ('PAYROLL_COMPONENT_MANAGE','hr','Finance','HIGH','SCHOOL','write',false,533),
    ('PAYROLL_ADJUST','hr','Finance','HIGH','SCHOOL','write',false,534),
    ('PAYROLL_REVIEW','hr','Finance','HIGH','SCHOOL','write',false,535),
    ('PAYROLL_APPROVE','hr','Finance','CRITICAL','SCHOOL','write',false,536),
    ('PAYROLL_PAY','hr','Finance','CRITICAL','SCHOOL','write',false,537),
    ('PAYROLL_VOID','hr','Finance','CRITICAL','SCHOOL','write',false,538),
    ('PAYSLIP_VIEW_ALL','hr','Finance','MEDIUM','SCHOOL','read',true,539),
    ('PAYSLIP_REGENERATE','hr','Finance','HIGH','SCHOOL','write',false,540),
    ('FINANCE_REPORT_VIEW','finance','Finance','MEDIUM','SCHOOL','read',true,541),
    ('FINANCE_EXPORT','finance','Finance','HIGH','SCHOOL','read',false,542),

    ('PARENT_CHILD_SUMMARY_VIEW','parent','Parent','LOW','CHILD','read',true,600),
    ('PARENT_ACADEMIC_VIEW','parent','Parent','MEDIUM','CHILD','read',true,601),
    ('PARENT_ATTENDANCE_VIEW','parent','Parent','MEDIUM','CHILD','read',true,602),
    ('PARENT_FINANCE_VIEW','parent','Parent','MEDIUM','CHILD','read',true,603),
    ('PARENT_DISCIPLINE_VIEW','parent','Parent','MEDIUM','CHILD','read',true,604),
    ('PARENT_HEALTH_VIEW','parent','Parent','HIGH','CHILD','read',true,605),
    ('PARENT_DOCUMENT_DOWNLOAD','parent','Parent','LOW','CHILD','read',true,606),
    ('PARENT_SUGGESTION_SUBMIT','parent','Parent','LOW','CHILD','write',false,607),

    -- Legacy module-only endpoints receive stable bridge actions during the
    -- conversion.  They are still subject to their module gate until mapped.
    ('DASHBOARD_VIEW','dashboard','Other','LOW','SCHOOL','read',true,700),
    ('ALERTS_VIEW','alerts','Other','LOW','SCHOOL','read',true,701),
    ('ALERTS_MANAGE','alerts','Other','MEDIUM','SCHOOL','write',false,702),
    ('DISCIPLINE_VIEW','discipline','Other','MEDIUM','CLASS','read',true,703),
    ('DISCIPLINE_MANAGE','discipline','Other','HIGH','STUDENT','write',false,704),
    ('COURSEBOOK_VIEW','coursebook','Other','LOW','CLASS','read',true,705),
    ('COURSEBOOK_MANAGE','coursebook','Other','MEDIUM','CLASS','write',false,706),
    ('MESSAGES_VIEW','messages','Other','LOW','SCHOOL','read',true,707),
    ('MESSAGES_MANAGE','messages','Other','MEDIUM','SCHOOL','write',false,708),
    ('EVENTS_VIEW','events','Other','LOW','SCHOOL','read',true,709),
    ('EVENTS_MANAGE','events','Other','MEDIUM','SCHOOL','write',false,710),
    ('HEALTH_VIEW','health','Other','HIGH','STUDENT','read',true,711),
    ('HEALTH_MANAGE','health','Other','CRITICAL','STUDENT','write',false,712),
    ('HEALTH_CONFIDENTIAL_VIEW','health','Other','CRITICAL','STUDENT','write',false,713),
    ('REPORTS_VIEW','reports','Other','MEDIUM','SCHOOL','read',true,714),
    ('HR_VIEW','hr','Other','MEDIUM','SCHOOL','read',true,715),
    ('HR_MANAGE','hr','Other','HIGH','SCHOOL','write',false,716),
    ('CLASSKIT_VIEW','classkit','Other','LOW','CLASS','read',true,717),
    ('CLASSKIT_MANAGE','classkit','Other','MEDIUM','CLASS','write',false,718),
    ('JOURNEY_VIEW','journey','Other','LOW','STUDENT','read',true,719),
    ('JOURNEY_MANAGE','journey','Other','HIGH','STUDENT','write',false,720),
    ('GUARDIAN_LINK','students','Students','CRITICAL','STUDENT','write',false,721),
    ('ENROLLMENT_MANAGE','students','Students','CRITICAL','STUDENT','write',false,722),
    ('DOCUMENT_VIEW','documents','Students','LOW','STUDENT','read',true,723),
    ('DOCUMENT_GENERATE','documents','Students','MEDIUM','STUDENT','write',false,724),
    ('DOCUMENT_REVOKE','documents','Students','CRITICAL','STUDENT','write',false,725)
)
INSERT INTO permission_action
    (code,module,group_code,label_fr,label_en,description_fr,description_en,
     risk_level,scope_type,required_level,default_read_action,display_order)
SELECT code, module, group_code,
       code, code, '', '',
       risk_level, scope_type, required_level, default_read_action, display_order
  FROM actions
ON CONFLICT (code) DO UPDATE SET
    module=EXCLUDED.module,
    group_code=EXCLUDED.group_code,
    risk_level=EXCLUDED.risk_level,
    scope_type=EXCLUDED.scope_type,
    required_level=EXCLUDED.required_level,
    default_read_action=EXCLUDED.default_read_action,
    display_order=EXCLUDED.display_order,
    updated_at=now();

-- Stable action codes remain internal.  The Access Control workspace consumes
-- these reviewed French/English labels and descriptions; it never presents a
-- machine-transformed code as user-facing copy.
UPDATE permission_action AS a
   SET label_fr=l.label_fr,
       label_en=l.label_en,
       description_fr=CASE a.group_code
           WHEN 'Students' THEN 'Accès aux données élèves dans le périmètre autorisé : ' || l.label_fr || '.'
           WHEN 'Academic' THEN 'Accès pédagogique soumis à la session, la classe et la matière : ' || l.label_fr || '.'
           WHEN 'Attendance' THEN 'Accès aux présences selon le modèle quotidien ou la séance publiée : ' || l.label_fr || '.'
           WHEN 'Timetable' THEN 'Accès aux emplois du temps publiés selon le rôle : ' || l.label_fr || '.'
           WHEN 'Settings' THEN 'Configuration contrôlée, justifiée et journalisée : ' || l.label_fr || '.'
           WHEN 'Finance' THEN 'Accès finance soumis à la séparation des tâches et aux données minimisées : ' || l.label_fr || '.'
           WHEN 'Parent' THEN 'Accès uniquement aux enfants liés et aux indicateurs autorisés : ' || l.label_fr || '.'
           ELSE 'Accès contrôlé et journalisé à cette fonctionnalité : ' || l.label_fr || '.'
       END,
       description_en=CASE a.group_code
           WHEN 'Students' THEN 'Access to student data within the permitted scope: ' || l.label_en || '.'
           WHEN 'Academic' THEN 'Academic access constrained by session, class and subject: ' || l.label_en || '.'
           WHEN 'Attendance' THEN 'Attendance access follows the daily model or a published occurrence: ' || l.label_en || '.'
           WHEN 'Timetable' THEN 'Published timetable access according to the assigned role: ' || l.label_en || '.'
           WHEN 'Settings' THEN 'Controlled configuration change with reason and audit trail: ' || l.label_en || '.'
           WHEN 'Finance' THEN 'Finance access with separation of duties and minimized data: ' || l.label_en || '.'
           WHEN 'Parent' THEN 'Access limited to linked children and enabled features: ' || l.label_en || '.'
           ELSE 'Controlled and audited access to this capability: ' || l.label_en || '.'
       END,
       updated_at=now()
  FROM (VALUES
    ('STUDENT_DIRECTORY_VIEW','Annuaire des élèves','Student directory'),
    ('STUDENT_PROFILE_VIEW','Dossier élève — consultation','Student profile — view'),
    ('STUDENT_PROFILE_CREATE','Créer un dossier élève','Create student profile'),
    ('STUDENT_PROFILE_EDIT','Modifier un dossier élève','Edit student profile'),
    ('STUDENT_PROFILE_DEACTIVATE','Désactiver un dossier élève','Deactivate student profile'),
    ('STUDENT_PHOTO_VIEW','Photo élève — consultation','Student photo — view'),
    ('STUDENT_PHOTO_MANAGE','Gérer la photo élève','Manage student photo'),
    ('STUDENT_IMPORT','Importer des élèves','Import students'),
    ('GUARDIAN_VIEW','Responsables — consultation','Guardians — view'),
    ('GUARDIAN_LINK_MANAGE','Gérer les liens familiaux','Manage family links'),
    ('GUARDIAN_ACCOUNT_MANAGE','Gérer les comptes responsables','Manage guardian accounts'),
    ('GUARDIAN_DIRECTORY_SEARCH','Rechercher un responsable','Search guardian directory'),
    ('GUARDIAN_DIRECTORY_MANAGE','Administrer le répertoire des responsables','Manage guardian directory'),
    ('ENROLLMENT_VIEW','Inscriptions — consultation','Enrollments — view'),
    ('ENROLLMENT_CREATE','Créer une inscription','Create enrollment'),
    ('ENROLLMENT_TRANSFER','Transférer une inscription','Transfer enrollment'),
    ('ENROLLMENT_WITHDRAW','Retirer une inscription','Withdraw enrollment'),
    ('STUDENT_DOCUMENT_VIEW','Documents élève — consultation','Student documents — view'),
    ('STUDENT_DOCUMENT_GENERATE','Générer un document élève','Generate student document'),
    ('STUDENT_DOCUMENT_REVOKE','Révoquer un document élève','Revoke student document'),
    ('ACADEMIC_ROSTER_VIEW','Liste pédagogique de classe','Academic class roster'),
    ('ACADEMIC_ASSESSMENT_VIEW','Évaluations — consultation','Assessments — view'),
    ('ACADEMIC_ASSESSMENT_MANAGE','Gérer les évaluations','Manage assessments'),
    ('ACADEMIC_SUBJECT_GRADE_VIEW','Notes par matière — consultation','Subject grades — view'),
    ('ACADEMIC_SUBJECT_GRADE_EDIT','Modifier les notes par matière','Edit subject grades'),
    ('GRADE_SUBMIT','Soumettre les notes','Submit grades'),
    ('GRADE_EDIT_ANY_SUBJECT_IN_TITULAIRE_CLASS','Modifier toute matière de la classe titulaire','Edit any subject in homeroom class'),
    ('ACADEMIC_CLASS_RESULTS_VIEW','Résultats de classe — consultation','Class results — view'),
    ('ACADEMIC_REPORT_CARD_VIEW','Bulletins — consultation','Report cards — view'),
    ('ACADEMIC_GRADE_PACKET_REVIEW','Réviser un paquet de notes','Review grade packet'),
    ('ACADEMIC_REPORT_CARD_VALIDATE','Valider les bulletins','Validate report cards'),
    ('ACADEMIC_REPORT_CARD_PUBLISH','Publier les bulletins','Publish report cards'),
    ('ACADEMIC_COUNCIL_INPUT_VIEW','Conseil de classe — consultation','Class council input — view'),
    ('ACADEMIC_COUNCIL_INPUT_EDIT','Modifier les contributions du conseil','Edit class council input'),
    ('ACADEMIC_ACCESS_DELEGATE','Déléguer un accès pédagogique','Delegate academic access'),
    ('ACADEMIC_ACCESS_AUDIT_VIEW','Audit des accès pédagogiques','Academic access audit'),
    ('ACADEMIC_WINDOW_OVERRIDE','Forcer une fenêtre pédagogique','Override academic window'),
    ('BULLETIN_VALIDATE','Valider un bulletin','Validate bulletin'),
    ('BULLETIN_PUBLISH','Publier un bulletin','Publish bulletin'),
    ('PROMOTION_RECOMMEND','Recommander une promotion','Recommend promotion'),
    ('PROMOTION_OVERRIDE','Outrepasser une promotion','Override promotion'),
    ('PROMOTION_COMMIT','Enregistrer une promotion','Commit promotion'),
    ('PROGRESSION_VIEW','Parcours scolaire — consultation','Student progression — view'),
    ('PROGRESSION_CONFIGURE','Configurer les parcours scolaires','Configure progression'),
    ('PROMOTION_REVIEW','Réviser les promotions','Review promotions'),
    ('PROMOTION_CONFIGURE','Configurer les règles de promotion','Configure promotion rules'),
    ('PROMOTION_CORRECT','Corriger une promotion','Correct promotion'),
    ('ATTENDANCE_ROSTER_VIEW','Liste d’appel — consultation','Attendance roster — view'),
    ('ATTENDANCE_MARK','Marquer une présence','Mark attendance'),
    ('ATTENDANCE_FINALIZE','Clôturer un appel','Finalize attendance'),
    ('ATTENDANCE_REOPEN','Rouvrir un appel','Reopen attendance'),
    ('ATTENDANCE_ANALYTICS_VIEW','Analyses de présence','Attendance analytics'),
    ('ATTENDANCE_POLICY_MANAGE','Gérer la politique de présence','Manage attendance policy'),
    ('ATTENDANCE_RECONCILE','Rapprocher les présences','Reconcile attendance'),
    ('ATTENDANCE_POLICY_VIEW','Politique de présence — consultation','Attendance policy — view'),
    ('ATTENDANCE_DEVICE_VIEW','Terminaux de présence — état','Attendance devices — status'),
    ('ATTENDANCE_NOTIFICATION_VIEW','Notifications de présence — consultation','Attendance notifications — view'),
    ('TIMETABLE_MY_SCHEDULE_VIEW','Mon emploi du temps publié','My published timetable'),
    ('TIMETABLE_CLASS_SCHEDULE_VIEW','Emploi du temps de classe','Class timetable'),
    ('TIMETABLE_MASTER_VIEW','Emploi du temps général','Master timetable'),
    ('TIMETABLE_TEACHER_SCHEDULE_VIEW_ALL','Emplois du temps des enseignants','All teacher timetables'),
    ('TIMETABLE_ROOM_VIEW','Salles — consultation','Rooms — view'),
    ('TIMETABLE_RESOURCE_VIEW','Ressources — consultation','Resources — view'),
    ('TIMETABLE_DRAFT','Modifier un projet d’emploi du temps','Edit timetable draft'),
    ('TIMETABLE_PUBLISH','Publier un emploi du temps','Publish timetable'),
    ('TIMETABLE_REOPEN','Rouvrir un emploi du temps','Reopen timetable'),
    ('TIMETABLE_ARCHIVE','Archiver un emploi du temps','Archive timetable'),
    ('TIMETABLE_SUBSTITUTION_VIEW','Remplacements — consultation','Substitutions — view'),
    ('TIMETABLE_SUBSTITUTION_MANAGE','Gérer les remplacements','Manage substitutions'),
    ('TIMETABLE_EXPORT','Exporter un emploi du temps','Export timetable'),
    ('TIMETABLE_OVERRIDE','Forcer une occurrence d’emploi du temps','Override timetable occurrence'),
    ('SCHOOL_PROFILE_VIEW','Établissement — consultation','School profile — view'),
    ('SCHOOL_PROFILE_MANAGE','Gérer le profil établissement','Manage school profile'),
    ('SESSION_VIEW','Sessions — consultation','Sessions — view'),
    ('SESSION_MANAGE','Gérer les sessions','Manage sessions'),
    ('ACADEMIC_STRUCTURE_VIEW','Structure scolaire — consultation','Academic structure — view'),
    ('CLASS_MANAGE','Gérer les classes','Manage classes'),
    ('SUBJECT_MANAGE','Gérer les matières','Manage subjects'),
    ('CURRICULUM_MANAGE','Gérer le curriculum','Manage curriculum'),
    ('TEACHING_ASSIGNMENT_MANAGE','Gérer les affectations pédagogiques','Manage teaching assignments'),
    ('CALENDAR_VIEW','Calendrier — consultation','Calendar — view'),
    ('CALENDAR_MANAGE','Gérer le calendrier','Manage calendar'),
    ('DISCIPLINE_CATALOG_MANAGE','Gérer le catalogue disciplinaire','Manage discipline catalogue'),
    ('MAIL_CONFIG_MANAGE','Gérer la messagerie','Manage mail configuration'),
    ('ROLE_VIEW','Rôles — consultation','Roles — view'),
    ('ROLE_MANAGE','Gérer les rôles','Manage roles'),
    ('PERMISSION_VIEW','Droits — consultation','Permissions — view'),
    ('PERMISSION_MANAGE','Gérer les droits','Manage permissions'),
    ('AUDIT_VIEW','Journal d’audit — consultation','Audit log — view'),
    ('DOCUMENT_DESIGN_PUBLISH','Publier un modèle de document','Publish document design'),
    ('PARENT_PROFILE_MANAGE','Gérer les profils responsables','Manage guardian profiles'),
    ('FINANCE_OVERVIEW_VIEW','Synthèse financière — consultation','Finance overview — view'),
    ('FEE_CONFIGURE','Configurer les frais','Configure fees'),
    ('FEE_TYPE_MANAGE','Gérer les types de frais','Manage fee types'),
    ('FEE_PLAN_DRAFT','Préparer un plan de frais','Draft fee plan'),
    ('FEE_PLAN_ACTIVATE','Activer un plan de frais','Activate fee plan'),
    ('CHARGE_PREVIEW','Prévisualiser les facturations','Preview charges'),
    ('CHARGE_GENERATE','Générer les facturations','Generate charges'),
    ('CHARGE_ADJUST','Ajuster une facturation','Adjust charge'),
    ('FEE_WAIVE_REQUEST','Demander une remise','Request fee waiver'),
    ('FEE_WAIVE_APPROVE','Approuver une remise','Approve fee waiver'),
    ('PAYMENT_COLLECT','Encaisser un paiement','Collect payment'),
    ('PAYMENT_VIEW','Paiements — consultation','Payments — view'),
    ('PAYMENT_REVERSE','Annuler un paiement','Reverse payment'),
    ('REFUND_REQUEST','Demander un remboursement','Request refund'),
    ('REFUND_APPROVE','Approuver un remboursement','Approve refund'),
    ('CASHIER_SESSION_OPEN','Ouvrir une caisse','Open cashier session'),
    ('CASHIER_SESSION_CLOSE','Clôturer une caisse','Close cashier session'),
    ('CASHIER_SESSION_APPROVE','Approuver une caisse','Approve cashier session'),
    ('PROVIDER_CALLBACK_REVIEW','Examiner un retour prestataire','Review provider callback'),
    ('FINANCE_DOCUMENT_GENERATE','Générer un document financier','Generate finance document'),
    ('FINANCE_DOCUMENT_VOID','Annuler un document financier','Void finance document'),
    ('FINANCE_DOCUMENT_VIEW','Documents financiers — consultation','Finance documents — view'),
    ('FINANCE_DOCUMENT_SUPERSEDE','Remplacer un document financier','Supersede finance document'),
    ('FINANCE_DOCUMENT_BATCH','Traiter un lot financier','Process finance batch'),
    ('ACCOUNT_MANAGE','Gérer le plan comptable','Manage accounts'),
    ('POSTING_RULE_MANAGE','Gérer les règles comptables','Manage posting rules'),
    ('LEDGER_POST','Comptabiliser une écriture','Post ledger entry'),
    ('LEDGER_REVERSE','Extourner une écriture','Reverse ledger entry'),
    ('LEDGER_CLOSE','Clôturer le grand livre','Close ledger'),
    ('LEDGER_REOPEN','Rouvrir le grand livre','Reopen ledger'),
    ('PAYROLL_CALCULATE','Calculer la paie','Calculate payroll'),
    ('PAYROLL_VIEW','Paie — consultation','Payroll — view'),
    ('PAYROLL_PERIOD_MANAGE','Gérer les périodes de paie','Manage payroll periods'),
    ('PAYROLL_COMPONENT_MANAGE','Gérer les composantes de paie','Manage payroll components'),
    ('PAYROLL_ADJUST','Ajuster la paie','Adjust payroll'),
    ('PAYROLL_REVIEW','Réviser la paie','Review payroll'),
    ('PAYROLL_APPROVE','Approuver la paie','Approve payroll'),
    ('PAYROLL_PAY','Exécuter le paiement de la paie','Pay payroll'),
    ('PAYROLL_VOID','Annuler la paie','Void payroll'),
    ('PAYSLIP_VIEW_ALL','Bulletins de paie — consultation','All payslips — view'),
    ('PAYSLIP_REGENERATE','Régénérer un bulletin de paie','Regenerate payslip'),
    ('FINANCE_REPORT_VIEW','Rapports financiers — consultation','Finance reports — view'),
    ('FINANCE_EXPORT','Exporter des données financières','Export finance data'),
    ('PARENT_CHILD_SUMMARY_VIEW','Résumé enfant — consultation','Child summary — view'),
    ('PARENT_ACADEMIC_VIEW','Résultats enfant — consultation','Child academics — view'),
    ('PARENT_ATTENDANCE_VIEW','Présences enfant — consultation','Child attendance — view'),
    ('PARENT_FINANCE_VIEW','Frais enfant — consultation','Child finance — view'),
    ('PARENT_DISCIPLINE_VIEW','Discipline enfant — consultation','Child discipline — view'),
    ('PARENT_HEALTH_VIEW','Santé enfant — consultation','Child health — view'),
    ('PARENT_DOCUMENT_DOWNLOAD','Télécharger un document enfant','Download child document'),
    ('PARENT_SUGGESTION_SUBMIT','Envoyer une suggestion','Submit suggestion'),
    ('DASHBOARD_VIEW','Tableau de bord — consultation','Dashboard — view'),
    ('ALERTS_VIEW','Alertes — consultation','Alerts — view'),
    ('ALERTS_MANAGE','Gérer les alertes','Manage alerts'),
    ('DISCIPLINE_VIEW','Discipline — consultation','Discipline — view'),
    ('DISCIPLINE_MANAGE','Gérer la discipline','Manage discipline'),
    ('COURSEBOOK_VIEW','Cahier de textes — consultation','Coursebook — view'),
    ('COURSEBOOK_MANAGE','Gérer le cahier de textes','Manage coursebook'),
    ('MESSAGES_VIEW','Messages — consultation','Messages — view'),
    ('MESSAGES_MANAGE','Gérer les messages','Manage messages'),
    ('EVENTS_VIEW','Événements — consultation','Events — view'),
    ('EVENTS_MANAGE','Gérer les événements','Manage events'),
    ('HEALTH_VIEW','Santé — consultation','Health — view'),
    ('HEALTH_MANAGE','Gérer les données santé','Manage health data'),
    ('HEALTH_CONFIDENTIAL_VIEW','Données santé confidentielles','Confidential health data'),
    ('REPORTS_VIEW','Rapports — consultation','Reports — view'),
    ('HR_VIEW','Ressources humaines — consultation','Human resources — view'),
    ('HR_MANAGE','Gérer les ressources humaines','Manage human resources'),
    ('CLASSKIT_VIEW','Ressources de classe — consultation','Class resources — view'),
    ('CLASSKIT_MANAGE','Gérer les ressources de classe','Manage class resources'),
    ('JOURNEY_VIEW','Parcours — consultation','Journey — view'),
    ('JOURNEY_MANAGE','Gérer les parcours','Manage journeys'),
    ('GUARDIAN_LINK','Créer un lien responsable-enfant','Create guardian link'),
    ('ENROLLMENT_MANAGE','Gérer les inscriptions','Manage enrollments'),
    ('DOCUMENT_VIEW','Documents — consultation','Documents — view'),
    ('DOCUMENT_GENERATE','Générer un document','Generate document'),
    ('DOCUMENT_REVOKE','Révoquer un document','Revoke document')
  ) AS l(code,label_fr,label_en)
 WHERE a.code=l.code;

-- Directory actions have reviewed copy rather than the generic group
-- description above; these strings are part of the checked-in catalogue.
UPDATE permission_action
   SET description_fr='Recherche minimisée nécessaire pour préparer un lien familial.',
       description_en='Minimized lookup needed to prepare a family link.',
       updated_at=now()
 WHERE code='GUARDIAN_DIRECTORY_SEARCH';

UPDATE permission_action
   SET description_fr='Fusion, invitation et cycle de vie des comptes responsables.',
       description_en='Merge, invitation and lifecycle operations for guardian accounts.',
       updated_at=now()
 WHERE code='GUARDIAN_DIRECTORY_MANAGE';

CREATE TABLE IF NOT EXISTS permission_role_action (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id       UUID NOT NULL REFERENCES school(id) ON DELETE CASCADE,
    role_code       VARCHAR(32) NOT NULL REFERENCES role(code),
    action_code     VARCHAR(96) NOT NULL REFERENCES permission_action(code),
    effect          VARCHAR(8) NOT NULL CHECK (effect IN ('ALLOW','DENY','INHERIT')),
    scope_mode      VARCHAR(40) NOT NULL DEFAULT 'NONE'
        CHECK (scope_mode IN ('NONE','SCHOOL_ALL','PARCOURS_ALLOWED','ASSIGNED_CLASSES',
                              'TITULAIRE_CLASSES','ASSIGNED_CLASS_SUBJECTS',
                              'TIMETABLE_OCCURRENCES_ASSIGNED','LINKED_CHILDREN','SELF',
                              'CLASS_SET','SUBJECT_SET','CLASS_SUBJECT_SET','PARCOURS_SET')),
    scope_payload   JSONB,
    effective_from  DATE,
    effective_to    DATE,
    is_permanent    BOOLEAN NOT NULL DEFAULT false,
    reason          VARCHAR(1000) NOT NULL DEFAULT '',
    version         BIGINT NOT NULL DEFAULT 0,
    created_by      UUID REFERENCES app_user(id),
    updated_by      UUID REFERENCES app_user(id),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (effective_to IS NULL OR effective_from IS NULL OR effective_to >= effective_from),
    CHECK (NOT is_permanent OR effective_to IS NULL),
    CHECK (effect <> 'INHERIT' OR (scope_mode = 'NONE' AND scope_payload IS NULL))
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_permission_role_action_effective
    ON permission_role_action
       (school_id, role_code, action_code, scope_mode,
        COALESCE(effective_from, '-infinity'::date),
        COALESCE(effective_to, 'infinity'::date),
        md5(COALESCE(scope_payload::text, '')));
CREATE INDEX IF NOT EXISTS idx_permission_role_action_lookup
    ON permission_role_action(school_id, role_code, action_code, effective_from, effective_to);

CREATE TABLE IF NOT EXISTS permission_user_action (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id       UUID NOT NULL REFERENCES school(id) ON DELETE CASCADE,
    user_id         UUID NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    action_code     VARCHAR(96) NOT NULL REFERENCES permission_action(code),
    effect          VARCHAR(8) NOT NULL CHECK (effect IN ('ALLOW','DENY','INHERIT')),
    scope_mode      VARCHAR(40) NOT NULL DEFAULT 'NONE'
        CHECK (scope_mode IN ('NONE','SCHOOL_ALL','PARCOURS_ALLOWED','ASSIGNED_CLASSES',
                              'TITULAIRE_CLASSES','ASSIGNED_CLASS_SUBJECTS',
                              'TIMETABLE_OCCURRENCES_ASSIGNED','LINKED_CHILDREN','SELF',
                              'CLASS_SET','SUBJECT_SET','CLASS_SUBJECT_SET','PARCOURS_SET')),
    scope_payload   JSONB,
    effective_from  DATE,
    effective_to    DATE,
    is_permanent    BOOLEAN NOT NULL DEFAULT false,
    reason          VARCHAR(1000) NOT NULL DEFAULT '',
    version         BIGINT NOT NULL DEFAULT 0,
    created_by      UUID REFERENCES app_user(id),
    updated_by      UUID REFERENCES app_user(id),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (effective_to IS NULL OR effective_from IS NULL OR effective_to >= effective_from),
    CHECK (NOT is_permanent OR effective_to IS NULL),
    CHECK (effect <> 'INHERIT' OR (scope_mode = 'NONE' AND scope_payload IS NULL))
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_permission_user_action_effective
    ON permission_user_action
       (school_id, user_id, action_code, scope_mode,
        COALESCE(effective_from, '-infinity'::date),
        COALESCE(effective_to, 'infinity'::date),
        md5(COALESCE(scope_payload::text, '')));
CREATE INDEX IF NOT EXISTS idx_permission_user_action_lookup
    ON permission_user_action(school_id, user_id, action_code, effective_from, effective_to);

CREATE TABLE IF NOT EXISTS app_user_role (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id       UUID NOT NULL REFERENCES school(id) ON DELETE CASCADE,
    user_id         UUID NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    role_code       VARCHAR(32) NOT NULL REFERENCES role(code),
    is_primary      BOOLEAN NOT NULL DEFAULT false,
    effective_from  DATE,
    effective_to    DATE,
    assigned_by     UUID REFERENCES app_user(id),
    reason          VARCHAR(1000) NOT NULL DEFAULT '',
    version         BIGINT NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (effective_to IS NULL OR effective_from IS NULL OR effective_to >= effective_from)
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_app_user_role
    ON app_user_role(school_id,user_id,role_code,
                     COALESCE(effective_from, '-infinity'::date),
                     COALESCE(effective_to, 'infinity'::date));
CREATE UNIQUE INDEX IF NOT EXISTS uq_app_user_primary_role
    ON app_user_role(school_id,user_id) WHERE is_primary;
CREATE INDEX IF NOT EXISTS idx_app_user_role_active
    ON app_user_role(school_id,user_id,effective_from,effective_to);

ALTER TABLE app_user ADD COLUMN IF NOT EXISTS parcours_scope_mode VARCHAR(24);
ALTER TABLE app_user DROP CONSTRAINT IF EXISTS app_user_parcours_scope_mode_check;
ALTER TABLE app_user ADD CONSTRAINT app_user_parcours_scope_mode_check
    CHECK (parcours_scope_mode IN ('GLOBAL','EXPLICIT','ASSIGNMENT_DERIVED','CHILD_DERIVED','NONE'));

CREATE TABLE IF NOT EXISTS school_permission_version (
    school_id   UUID PRIMARY KEY REFERENCES school(id) ON DELETE CASCADE,
    version     BIGINT NOT NULL DEFAULT 1,
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS permission_policy_rollout (
    school_id                    UUID PRIMARY KEY REFERENCES school(id) ON DELETE CASCADE,
    mode                         VARCHAR(32) NOT NULL
        CHECK (mode IN ('SAFE_DEFAULT','LEGACY_COMPATIBILITY','ADOPTED')),
    compatibility_profile_code   VARCHAR(32),
    enforcement_enabled          BOOLEAN NOT NULL DEFAULT false,
    reviewed_by                  UUID REFERENCES app_user(id),
    reviewed_at                  TIMESTAMPTZ,
    created_at                   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at                   TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS permission_compatibility_report (
    id                         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id                  UUID NOT NULL REFERENCES school(id) ON DELETE CASCADE,
    user_id                    UUID NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    legacy_role_code            VARCHAR(32) NOT NULL,
    legacy_effective_access     JSONB NOT NULL DEFAULT '{}'::jsonb,
    safer_preview               JSONB NOT NULL DEFAULT '{}'::jsonb,
    would_change                BOOLEAN NOT NULL DEFAULT false,
    review_status               VARCHAR(16) NOT NULL DEFAULT 'PENDING'
        CHECK (review_status IN ('PENDING','REVIEWED','ADOPTED','DISMISSED')),
    reviewed_by                 UUID REFERENCES app_user(id),
    reviewed_at                 TIMESTAMPTZ,
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (school_id,user_id)
);
CREATE INDEX IF NOT EXISTS idx_permission_compatibility_pending
    ON permission_compatibility_report(school_id, review_status, would_change);

CREATE TABLE IF NOT EXISTS permission_policy_audit (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id       UUID NOT NULL REFERENCES school(id) ON DELETE CASCADE,
    actor_user_id   UUID REFERENCES app_user(id),
    target_role_code VARCHAR(32),
    target_user_id  UUID REFERENCES app_user(id),
    mutation_type   VARCHAR(64) NOT NULL,
    reason          VARCHAR(1000) NOT NULL,
    before_state    JSONB NOT NULL DEFAULT '{}'::jsonb,
    after_state     JSONB NOT NULL DEFAULT '{}'::jsonb,
    correlation_id  VARCHAR(128),
    occurred_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_permission_policy_audit_school_time
    ON permission_policy_audit(school_id, occurred_at DESC);

CREATE TABLE IF NOT EXISTS permission_policy_shadow_decision (
    id              BIGSERIAL PRIMARY KEY,
    school_id       UUID NOT NULL REFERENCES school(id) ON DELETE CASCADE,
    user_id         UUID REFERENCES app_user(id) ON DELETE SET NULL,
    action_code     VARCHAR(96) NOT NULL,
    resource_context JSONB NOT NULL DEFAULT '{}'::jsonb,
    legacy_allowed  BOOLEAN NOT NULL,
    policy_allowed  BOOLEAN NOT NULL,
    denial_code     VARCHAR(96),
    policy_version  BIGINT NOT NULL,
    correlation_id  VARCHAR(128),
    occurred_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_permission_shadow_school_time
    ON permission_policy_shadow_decision(school_id, occurred_at DESC);

-- The legacy schema lets deployments create the built-in role catalogue from
-- application bootstrap.  Flyway must also work against an empty database,
-- however, because template base_role_code is a real foreign key.  Seed only
-- the stable role identities here; school-specific assignments remain the
-- responsibility of bootstrap/backfill below.
INSERT INTO role(code,label_fr,label_en,builtin)
VALUES
 ('principal','Direction','Principal',true),
 ('prefect','Préfet','Prefect',true),
 ('econome','Économe','Bursar',true),
 ('form_teacher','Titulaire','Form teacher',true),
 ('teacher','Enseignant','Teacher',true),
 ('parent','Parent','Parent',true),
 ('accountant','Comptable','Accountant',true)
ON CONFLICT (code) DO NOTHING;

CREATE TABLE IF NOT EXISTS permission_role_template (
    code            VARCHAR(64) PRIMARY KEY,
    base_role_code  VARCHAR(32) REFERENCES role(code),
    label_fr        VARCHAR(160) NOT NULL,
    label_en        VARCHAR(160) NOT NULL,
    description_fr  VARCHAR(500) NOT NULL DEFAULT '',
    description_en  VARCHAR(500) NOT NULL DEFAULT '',
    active          BOOLEAN NOT NULL DEFAULT true,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
INSERT INTO permission_role_template(code,base_role_code,label_fr,label_en,description_fr,description_en)
VALUES
 ('primary_teacher','teacher','Enseignant primaire / maternelle','Primary / Maternelle teacher','Accès aux classes et matières assignées, avec présence quotidienne titulaire.','Assigned classes and subjects, with daily attendance for titulaire classes.'),
 ('secondary_teacher','teacher','Enseignant secondaire','Secondary subject teacher','Accès strict aux couples classe-matière et occurrences publiées assignés.','Strict access to assigned class-subject pairs and published occurrences.'),
 ('form_teacher','form_teacher','Titulaire / professeur principal','Titulaire / form teacher','Lecture des résultats de la classe; édition élargie uniquement par délégation datée.','Class result visibility; wider editing only through a dated delegation.'),
 ('finance_collector','econome','Collecteur / caissier','Finance collector / cashier','Encaissement et recherche minimale de payeur, sans données pédagogiques.','Collections and minimal payer lookup, without academic data.'),
 ('accountant','accountant','Comptable','Accountant','Comptabilité et rapports selon séparation des tâches.','Accounting and reports with separation of duties.'),
 ('bursar','econome','Économe / responsable financier','Bursar / finance manager','Pilotage financier et corrections explicitement autorisées.','Finance management and explicitly authorized corrections.'),
 ('principal_oversight','principal','Direction — pilotage','Principal oversight','Pilotage et consultation; les opérations sensibles sont explicitement ajoutées.','Oversight and consultation; sensitive operations must be explicitly added.'),
 ('parent_portal','parent','Portail parent','Parent portal','Enfants liés et indicateurs de relation familiale effectifs uniquement.','Linked children and effective guardian relationship flags only.'),
 ('custom_blank',NULL,'Profil personnalisé','Custom blank','Profil vide à construire avec prévisualisation et justification.','Blank profile to configure with preview and reason.')
ON CONFLICT (code) DO UPDATE SET
    label_fr=EXCLUDED.label_fr,label_en=EXCLUDED.label_en,
    description_fr=EXCLUDED.description_fr,description_en=EXCLUDED.description_en,
    base_role_code=EXCLUDED.base_role_code,active=true;

CREATE TABLE IF NOT EXISTS permission_role_template_rule (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    template_code   VARCHAR(64) NOT NULL REFERENCES permission_role_template(code) ON DELETE CASCADE,
    action_code     VARCHAR(96) NOT NULL REFERENCES permission_action(code),
    effect          VARCHAR(8) NOT NULL CHECK (effect IN ('ALLOW','DENY','INHERIT')),
    scope_mode      VARCHAR(40) NOT NULL DEFAULT 'NONE'
        CHECK (scope_mode IN ('NONE','SCHOOL_ALL','PARCOURS_ALLOWED','ASSIGNED_CLASSES',
                              'TITULAIRE_CLASSES','ASSIGNED_CLASS_SUBJECTS',
                              'TIMETABLE_OCCURRENCES_ASSIGNED','LINKED_CHILDREN','SELF',
                              'CLASS_SET','SUBJECT_SET','CLASS_SUBJECT_SET','PARCOURS_SET')),
    scope_payload   JSONB,
    effective_from  DATE,
    effective_to    DATE,
    is_permanent    BOOLEAN NOT NULL DEFAULT false,
    reason          VARCHAR(1000) NOT NULL DEFAULT 'Role template rule',
    display_order   INT NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_permission_role_template_rule_effective
    ON permission_role_template_rule
       (template_code, action_code, scope_mode, effect,
        COALESCE(effective_from, '-infinity'::date),
        COALESCE(effective_to, 'infinity'::date),
        md5(COALESCE(scope_payload::text, '')));

-- Durable template rules make a template application deterministic and allow
-- the preview endpoint to calculate additions/removals without mutating rows.
INSERT INTO permission_role_template_rule
    (template_code,action_code,effect,scope_mode,is_permanent,reason,display_order)
VALUES
 ('primary_teacher','STUDENT_DIRECTORY_VIEW','ALLOW','ASSIGNED_CLASSES',true,'Primary teacher default',10),
 ('primary_teacher','STUDENT_PROFILE_VIEW','ALLOW','ASSIGNED_CLASSES',true,'Primary teacher default',11),
 ('primary_teacher','ACADEMIC_ROSTER_VIEW','ALLOW','ASSIGNED_CLASSES',true,'Primary teacher default',20),
 ('primary_teacher','ACADEMIC_SUBJECT_GRADE_VIEW','ALLOW','ASSIGNED_CLASS_SUBJECTS',true,'Primary teacher default',21),
 ('primary_teacher','ACADEMIC_SUBJECT_GRADE_EDIT','ALLOW','ASSIGNED_CLASS_SUBJECTS',true,'Primary teacher default',22),
 ('primary_teacher','GRADE_SUBMIT','ALLOW','ASSIGNED_CLASS_SUBJECTS',true,'Primary teacher default',23),
 ('primary_teacher','ATTENDANCE_ROSTER_VIEW','ALLOW','TITULAIRE_CLASSES',true,'Primary daily attendance',30),
 ('primary_teacher','ATTENDANCE_MARK','ALLOW','TITULAIRE_CLASSES',true,'Primary daily attendance',31),
  ('primary_teacher','ATTENDANCE_FINALIZE','ALLOW','TITULAIRE_CLASSES',true,'Primary daily attendance',32),
  ('primary_teacher','ATTENDANCE_ANALYTICS_VIEW','ALLOW','ASSIGNED_CLASSES',true,'Analytics for assigned primary classes',33),
 ('primary_teacher','TIMETABLE_MY_SCHEDULE_VIEW','ALLOW','SELF',true,'Own published timetable only',40),
 ('primary_teacher','SESSION_VIEW','ALLOW','SCHOOL_ALL',true,'Read current session dates',41),
 ('secondary_teacher','STUDENT_DIRECTORY_VIEW','ALLOW','ASSIGNED_CLASSES',true,'Secondary assigned classes',10),
 ('secondary_teacher','STUDENT_PROFILE_VIEW','ALLOW','ASSIGNED_CLASSES',true,'Secondary assigned classes',11),
 ('secondary_teacher','ACADEMIC_ROSTER_VIEW','ALLOW','ASSIGNED_CLASSES',true,'Secondary assigned classes',20),
 ('secondary_teacher','ACADEMIC_SUBJECT_GRADE_VIEW','ALLOW','ASSIGNED_CLASS_SUBJECTS',true,'Exact assigned subject',21),
 ('secondary_teacher','ACADEMIC_SUBJECT_GRADE_EDIT','ALLOW','ASSIGNED_CLASS_SUBJECTS',true,'Exact assigned subject',22),
 ('secondary_teacher','GRADE_SUBMIT','ALLOW','ASSIGNED_CLASS_SUBJECTS',true,'Exact assigned subject',23),
 ('secondary_teacher','ATTENDANCE_ROSTER_VIEW','ALLOW','TIMETABLE_OCCURRENCES_ASSIGNED',true,'Published responsible occurrence',30),
 ('secondary_teacher','ATTENDANCE_MARK','ALLOW','TIMETABLE_OCCURRENCES_ASSIGNED',true,'Published responsible occurrence',31),
  ('secondary_teacher','ATTENDANCE_FINALIZE','ALLOW','TIMETABLE_OCCURRENCES_ASSIGNED',true,'Published responsible occurrence',32),
  ('secondary_teacher','ATTENDANCE_ANALYTICS_VIEW','ALLOW','TIMETABLE_OCCURRENCES_ASSIGNED',true,'Analytics for responsible occurrences',33),
 ('secondary_teacher','TIMETABLE_MY_SCHEDULE_VIEW','ALLOW','SELF',true,'Own published timetable only',40),
 ('secondary_teacher','SESSION_VIEW','ALLOW','SCHOOL_ALL',true,'Read current session dates',41),
 ('form_teacher','STUDENT_DIRECTORY_VIEW','ALLOW','TITULAIRE_CLASSES',true,'Titulaire class scope',10),
 ('form_teacher','STUDENT_PROFILE_VIEW','ALLOW','TITULAIRE_CLASSES',true,'Titulaire class scope',11),
 ('form_teacher','ACADEMIC_CLASS_RESULTS_VIEW','ALLOW','TITULAIRE_CLASSES',true,'Class-wide result read',20),
 ('form_teacher','ACADEMIC_REPORT_CARD_VIEW','ALLOW','TITULAIRE_CLASSES',true,'Class-wide report-card read',21),
 ('form_teacher','ACADEMIC_SUBJECT_GRADE_VIEW','ALLOW','ASSIGNED_CLASS_SUBJECTS',true,'Assigned subject read',22),
 ('form_teacher','ACADEMIC_SUBJECT_GRADE_EDIT','ALLOW','ASSIGNED_CLASS_SUBJECTS',true,'Assigned subject edit only',23),
 ('form_teacher','GRADE_SUBMIT','ALLOW','ASSIGNED_CLASS_SUBJECTS',true,'Assigned subject submit only',24),
 ('form_teacher','ATTENDANCE_ROSTER_VIEW','ALLOW','TITULAIRE_CLASSES',true,'Dated titulaire daily attendance',30),
 ('form_teacher','ATTENDANCE_MARK','ALLOW','TITULAIRE_CLASSES',true,'Dated titulaire daily attendance',31),
  ('form_teacher','ATTENDANCE_FINALIZE','ALLOW','TITULAIRE_CLASSES',true,'Dated titulaire daily attendance',32),
  ('form_teacher','ATTENDANCE_ANALYTICS_VIEW','ALLOW','TITULAIRE_CLASSES',true,'Analytics for titulaire classes',33),
  ('form_teacher','TIMETABLE_MY_SCHEDULE_VIEW','ALLOW','SELF',true,'Own published timetable only',40),
  ('form_teacher','SESSION_VIEW','ALLOW','SCHOOL_ALL',true,'Read current session dates',41),
  ('primary_teacher','GUARDIAN_DIRECTORY_SEARCH','DENY','SCHOOL_ALL',true,'Teacher profiles do not expose guardian directory search',50),
  ('primary_teacher','GUARDIAN_DIRECTORY_MANAGE','DENY','SCHOOL_ALL',true,'Teacher profiles cannot administer guardian accounts',51),
  ('secondary_teacher','GUARDIAN_DIRECTORY_SEARCH','DENY','SCHOOL_ALL',true,'Teacher profiles do not expose guardian directory search',50),
  ('secondary_teacher','GUARDIAN_DIRECTORY_MANAGE','DENY','SCHOOL_ALL',true,'Teacher profiles cannot administer guardian accounts',51),
  ('form_teacher','GUARDIAN_DIRECTORY_SEARCH','DENY','SCHOOL_ALL',true,'Teacher profiles do not expose guardian directory search',50),
  ('form_teacher','GUARDIAN_DIRECTORY_MANAGE','DENY','SCHOOL_ALL',true,'Teacher profiles cannot administer guardian accounts',51),
 ('finance_collector','FINANCE_OVERVIEW_VIEW','ALLOW','SCHOOL_ALL',true,'Finance school-wide overview',10),
 ('finance_collector','PAYMENT_VIEW','ALLOW','SCHOOL_ALL',true,'Finance payer lookup',11),
 ('finance_collector','PAYMENT_COLLECT','ALLOW','SCHOOL_ALL',true,'Collections',12),
 ('finance_collector','STUDENT_DIRECTORY_VIEW','ALLOW','SCHOOL_ALL',true,'Minimal finance payer lookup',13),
 ('accountant','FINANCE_OVERVIEW_VIEW','ALLOW','SCHOOL_ALL',true,'Finance school-wide overview',10),
 ('accountant','FINANCE_REPORT_VIEW','ALLOW','SCHOOL_ALL',true,'Finance reporting',11),
 ('accountant','FINANCE_EXPORT','ALLOW','SCHOOL_ALL',true,'Finance export',12),
 ('bursar','FINANCE_OVERVIEW_VIEW','ALLOW','SCHOOL_ALL',true,'Finance school-wide overview',10),
 ('bursar','PAYMENT_VIEW','ALLOW','SCHOOL_ALL',true,'Finance payer lookup',11),
 ('bursar','PAYMENT_COLLECT','ALLOW','SCHOOL_ALL',true,'Collections',12),
 ('bursar','FINANCE_REPORT_VIEW','ALLOW','SCHOOL_ALL',true,'Finance reporting',13),
 ('principal_oversight','DASHBOARD_VIEW','ALLOW','SCHOOL_ALL',true,'Operational oversight',10),
 ('principal_oversight','REPORTS_VIEW','ALLOW','SCHOOL_ALL',true,'Operational oversight',11),
 ('principal_oversight','ACADEMIC_CLASS_RESULTS_VIEW','ALLOW','SCHOOL_ALL',true,'Class result oversight',12),
 ('principal_oversight','ACADEMIC_REPORT_CARD_VIEW','ALLOW','SCHOOL_ALL',true,'Report-card oversight',13),
  ('principal_oversight','ATTENDANCE_ANALYTICS_VIEW','ALLOW','SCHOOL_ALL',true,'Attendance analytics',14),
  ('principal_oversight','ATTENDANCE_POLICY_VIEW','ALLOW','SCHOOL_ALL',true,'Attendance policy visibility',14),
  ('principal_oversight','ATTENDANCE_DEVICE_VIEW','ALLOW','SCHOOL_ALL',true,'Attendance device visibility',15),
  ('principal_oversight','ATTENDANCE_NOTIFICATION_VIEW','ALLOW','SCHOOL_ALL',true,'Attendance notification visibility',16),
  ('principal_oversight','AUDIT_VIEW','ALLOW','SCHOOL_ALL',true,'Audit visibility',15),
  ('principal_oversight','SCHOOL_PROFILE_VIEW','ALLOW','SCHOOL_ALL',true,'School profile visibility',16),
  ('principal_oversight','ACADEMIC_STRUCTURE_VIEW','ALLOW','SCHOOL_ALL',true,'Structure visibility',17),
  ('principal_oversight','GUARDIAN_DIRECTORY_SEARCH','ALLOW','SCHOOL_ALL',true,'Principal directory lookup',28),
  ('principal_oversight','GUARDIAN_DIRECTORY_MANAGE','ALLOW','SCHOOL_ALL',true,'Principal guardian directory administration',29),
 ('parent_portal','PARENT_CHILD_SUMMARY_VIEW','ALLOW','LINKED_CHILDREN',true,'Linked children only',10),
 ('parent_portal','PARENT_ACADEMIC_VIEW','ALLOW','LINKED_CHILDREN',true,'Guardian academic flag',11),
 ('parent_portal','PARENT_ATTENDANCE_VIEW','ALLOW','LINKED_CHILDREN',true,'Guardian attendance flag',12),
 ('parent_portal','PARENT_FINANCE_VIEW','ALLOW','LINKED_CHILDREN',true,'Guardian finance flag',13),
 ('parent_portal','PARENT_DISCIPLINE_VIEW','ALLOW','LINKED_CHILDREN',true,'Guardian discipline flag',14),
 ('parent_portal','PARENT_HEALTH_VIEW','ALLOW','LINKED_CHILDREN',true,'Guardian health flag',15),
 ('parent_portal','PARENT_DOCUMENT_DOWNLOAD','ALLOW','LINKED_CHILDREN',true,'Linked children only',16),
 ('parent_portal','PARENT_SUGGESTION_SUBMIT','ALLOW','LINKED_CHILDREN',true,'Linked children only',17)
ON CONFLICT DO NOTHING;

-- Existing applications/users keep a composable primary role.  The current
-- role_code remains synchronized for JWT/UI compatibility during transition.
INSERT INTO app_user_role(school_id,user_id,role_code,is_primary,reason)
SELECT school_id,id,role_code,true,'Permission Policy V2 compatibility backfill'
  FROM app_user
ON CONFLICT DO NOTHING;

-- New accounts must never inherit the old "empty list means everything"
-- ambiguity.  Backfill modes according to the approved policy.
UPDATE app_user u SET parcours_scope_mode = CASE
    WHEN EXISTS (SELECT 1 FROM app_user_parcours p WHERE p.user_id=u.id) THEN 'EXPLICIT'
    WHEN lower(u.role_code) IN ('teacher','form_teacher') THEN 'ASSIGNMENT_DERIVED'
    WHEN lower(u.role_code) IN ('parent') THEN 'CHILD_DERIVED'
    WHEN lower(u.role_code) IN ('econome','accountant','bursar','cashier','finance_officer') THEN 'GLOBAL'
    WHEN lower(u.role_code) IN ('principal','prefect','administrator','admin') THEN 'GLOBAL'
    ELSE 'NONE'
END
WHERE u.parcours_scope_mode IS NULL;
ALTER TABLE app_user ALTER COLUMN parcours_scope_mode SET DEFAULT 'NONE';
ALTER TABLE app_user ALTER COLUMN parcours_scope_mode SET NOT NULL;

-- Backfill the new tri-state rules from the old explicit action grant first,
-- then use the exact old module fallback for actions that had no row.
INSERT INTO permission_role_action
    (school_id,role_code,action_code,effect,scope_mode,is_permanent,reason)
SELECT s.id,r.code,a.code,
       CASE
           WHEN legacy.allowed IS TRUE THEN 'ALLOW'
           WHEN legacy.allowed IS FALSE THEN 'DENY'
           WHEN lower(COALESCE(pg.level,'none')) = 'write' THEN 'ALLOW'
           WHEN a.required_level = 'read' AND lower(COALESCE(pg.level,'none')) IN ('read','write') THEN 'ALLOW'
           WHEN a.required_level = 'none' THEN 'ALLOW'
           ELSE 'DENY'
       END,
       CASE a.scope_type
           WHEN 'NONE' THEN 'NONE'
           WHEN 'SELF' THEN 'SELF'
           ELSE 'SCHOOL_ALL'
       END,
       true,'Permission Policy V2 compatibility backfill'
  FROM school s
  CROSS JOIN role r
  CROSS JOIN permission_action a
  LEFT JOIN permission_action_grant legacy
    ON legacy.school_id=s.id AND legacy.role_code=r.code AND legacy.action_code=a.code
 LEFT JOIN permission_grant pg
     ON pg.school_id=s.id AND pg.role_code=r.code AND pg.module=a.module
 WHERE a.code NOT IN ('GUARDIAN_DIRECTORY_SEARCH','GUARDIAN_DIRECTORY_MANAGE')
 ON CONFLICT DO NOTHING;

-- These school-scoped directory actions have explicit compatibility mappings
-- because their manage operation may have been represented by either the old
-- students or settings module grant.  Keep a concrete DENY for roles with no
-- legacy authority rather than relying on an implicit wildcard.
INSERT INTO permission_role_action
    (school_id,role_code,action_code,effect,scope_mode,is_permanent,reason)
SELECT s.id,r.code,'GUARDIAN_DIRECTORY_SEARCH',
       CASE
           WHEN legacy.allowed IS TRUE THEN 'ALLOW'
           WHEN legacy.allowed IS FALSE THEN 'DENY'
           WHEN lower(COALESCE(pg.level,'none')) IN ('read','write')
                OR r.code IN ('principal','administrator','admin','school_admin') THEN 'ALLOW'
           ELSE 'DENY'
       END,
       'SCHOOL_ALL',true,'Permission Policy V2 guardian directory search compatibility backfill'
  FROM school s
  CROSS JOIN role r
  LEFT JOIN permission_action_grant legacy
    ON legacy.school_id=s.id AND legacy.role_code=r.code
   AND legacy.action_code='GUARDIAN_DIRECTORY_SEARCH'
  LEFT JOIN permission_grant pg
    ON pg.school_id=s.id AND pg.role_code=r.code AND pg.module='students'
 ON CONFLICT DO NOTHING;

INSERT INTO permission_role_action
    (school_id,role_code,action_code,effect,scope_mode,is_permanent,reason)
SELECT s.id,r.code,'GUARDIAN_DIRECTORY_MANAGE',
       CASE
           WHEN legacy.allowed IS TRUE THEN 'ALLOW'
           WHEN legacy.allowed IS FALSE THEN 'DENY'
           WHEN lower(COALESCE(pg_students.level,'none'))='write'
                OR lower(COALESCE(pg_settings.level,'none'))='write'
                OR r.code IN ('principal','administrator','admin','school_admin') THEN 'ALLOW'
           ELSE 'DENY'
       END,
       'SCHOOL_ALL',true,'Permission Policy V2 guardian directory manage compatibility backfill'
  FROM school s
  CROSS JOIN role r
  LEFT JOIN permission_action_grant legacy
    ON legacy.school_id=s.id AND legacy.role_code=r.code
   AND legacy.action_code='GUARDIAN_DIRECTORY_MANAGE'
  LEFT JOIN permission_grant pg_students
    ON pg_students.school_id=s.id AND pg_students.role_code=r.code AND pg_students.module='students'
  LEFT JOIN permission_grant pg_settings
    ON pg_settings.school_id=s.id AND pg_settings.role_code=r.code AND pg_settings.module='settings'
 ON CONFLICT DO NOTHING;

-- Existing schools receive a visible compatibility profile.  It preserves the
-- current effective authority until an administrator reviews the safer profile;
-- no production access is silently revoked by this migration.
INSERT INTO role(code,label_fr,label_en,builtin)
VALUES ('principal_legacy_compat','Principal — compatibilité héritée','Principal — legacy compatibility',false)
ON CONFLICT (code) DO NOTHING;
INSERT INTO permission_role_action
    (school_id,role_code,action_code,effect,scope_mode,is_permanent,reason)
SELECT school_id,'principal_legacy_compat',action_code,effect,scope_mode,true,
       'Generated legacy principal compatibility profile; review before adoption'
  FROM permission_role_action p
 WHERE p.role_code='principal'
ON CONFLICT DO NOTHING;
INSERT INTO app_user_role(school_id,user_id,role_code,is_primary,reason)
SELECT u.school_id,u.id,'principal_legacy_compat',false,
       'Generated legacy principal compatibility profile; review before adoption'
  FROM app_user u
 WHERE lower(u.role_code)='principal'
ON CONFLICT DO NOTHING;
INSERT INTO permission_policy_rollout(school_id,mode,compatibility_profile_code,enforcement_enabled)
SELECT id,'LEGACY_COMPATIBILITY','principal_legacy_compat',false FROM school
ON CONFLICT (school_id) DO NOTHING;

-- Capture a before/after review record for every current account.  The
-- compatibility profile is the before state; the safe template is only a
-- preview until explicitly adopted.
INSERT INTO permission_compatibility_report
    (school_id,user_id,legacy_role_code,legacy_effective_access,safer_preview,would_change)
SELECT u.school_id,u.id,u.role_code,
       jsonb_build_object(
          'modules', COALESCE((SELECT jsonb_object_agg(pg.module,pg.level)
                                FROM permission_grant pg
                               WHERE pg.school_id=u.school_id AND pg.role_code=u.role_code), '{}'::jsonb),
          'actions', COALESCE((SELECT jsonb_object_agg(pa.action_code,pa.allowed)
                                FROM permission_action_grant pa
                               WHERE pa.school_id=u.school_id AND pa.role_code=u.role_code), '{}'::jsonb)),
       jsonb_build_object('template', CASE WHEN lower(u.role_code)='principal'
                                           THEN 'principal_oversight' ELSE 'unchanged-role-review' END,
                          'enforcement', false),
       lower(u.role_code)='principal'
  FROM app_user u
ON CONFLICT (school_id,user_id) DO NOTHING;

INSERT INTO school_permission_version(school_id,version)
SELECT id,1 FROM school ON CONFLICT (school_id) DO NOTHING;

-- Policy mutations advance a tenant-scoped version used by /me/capabilities
-- and short-lived authorization caches.
CREATE OR REPLACE FUNCTION bump_school_permission_version()
RETURNS trigger AS $$
DECLARE target_school UUID;
BEGIN
    IF TG_OP = 'DELETE' THEN
        target_school := OLD.school_id;
    ELSE
        target_school := NEW.school_id;
    END IF;
    INSERT INTO school_permission_version(school_id,version,updated_at)
    VALUES (target_school,1,now())
    ON CONFLICT (school_id) DO UPDATE
       SET version=school_permission_version.version+1,updated_at=now();
    IF TG_OP = 'DELETE' THEN
        RETURN OLD;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_permission_role_action_version ON permission_role_action;
CREATE TRIGGER trg_permission_role_action_version
AFTER INSERT OR UPDATE OR DELETE ON permission_role_action
FOR EACH ROW EXECUTE FUNCTION bump_school_permission_version();
DROP TRIGGER IF EXISTS trg_permission_user_action_version ON permission_user_action;
CREATE TRIGGER trg_permission_user_action_version
AFTER INSERT OR UPDATE OR DELETE ON permission_user_action
FOR EACH ROW EXECUTE FUNCTION bump_school_permission_version();
DROP TRIGGER IF EXISTS trg_app_user_role_version ON app_user_role;
CREATE TRIGGER trg_app_user_role_version
AFTER INSERT OR UPDATE OR DELETE ON app_user_role
FOR EACH ROW EXECUTE FUNCTION bump_school_permission_version();
DROP TRIGGER IF EXISTS trg_app_user_version ON app_user;
CREATE TRIGGER trg_app_user_version
AFTER UPDATE OF active,role_code,employee_id,parcours_scope_mode ON app_user
FOR EACH ROW EXECUTE FUNCTION bump_school_permission_version();
DROP TRIGGER IF EXISTS trg_academic_delegation_version ON academic_access_delegation;
CREATE TRIGGER trg_academic_delegation_version
AFTER INSERT OR UPDATE OR DELETE ON academic_access_delegation
FOR EACH ROW EXECUTE FUNCTION bump_school_permission_version();

CREATE OR REPLACE FUNCTION bump_user_school_permission_version()
RETURNS trigger AS $$
DECLARE target_user UUID; target_school UUID;
BEGIN
    IF TG_OP = 'DELETE' THEN
        target_user := OLD.user_id;
    ELSE
        target_user := NEW.user_id;
    END IF;
    SELECT school_id INTO target_school FROM app_user WHERE id=target_user;
    IF target_school IS NOT NULL THEN
        INSERT INTO school_permission_version(school_id,version,updated_at)
        VALUES (target_school,1,now())
        ON CONFLICT (school_id) DO UPDATE
           SET version=school_permission_version.version+1,updated_at=now();
    END IF;
    IF TG_OP = 'DELETE' THEN RETURN OLD; END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;
DROP TRIGGER IF EXISTS trg_app_user_parcours_version ON app_user_parcours;
CREATE TRIGGER trg_app_user_parcours_version
AFTER INSERT OR UPDATE OR DELETE ON app_user_parcours
FOR EACH ROW EXECUTE FUNCTION bump_user_school_permission_version();

-- Several scope resolvers read tables without a school_id on every row.  Keep
-- the version tied to the owning school so cached decisions cannot outlive an
-- assignment, employee link, guardian relation, timetable publication or
-- class-level change.
CREATE OR REPLACE FUNCTION bump_teacher_class_school_permission_version()
RETURNS trigger AS $$
DECLARE target_class UUID; target_school UUID;
BEGIN
    target_class := CASE WHEN TG_OP='DELETE' THEN OLD.class_id ELSE NEW.class_id END;
    SELECT school_id INTO target_school FROM school_class WHERE id=target_class;
    IF target_school IS NOT NULL THEN
        INSERT INTO school_permission_version(school_id,version,updated_at)
        VALUES (target_school,1,now())
        ON CONFLICT (school_id) DO UPDATE
           SET version=school_permission_version.version+1,updated_at=now();
    END IF;
    IF TG_OP='DELETE' THEN RETURN OLD; END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;
DROP TRIGGER IF EXISTS trg_teacher_class_scope_version ON teacher_class;
CREATE TRIGGER trg_teacher_class_scope_version
AFTER INSERT OR UPDATE OR DELETE ON teacher_class
FOR EACH ROW EXECUTE FUNCTION bump_teacher_class_school_permission_version();

DROP TRIGGER IF EXISTS trg_employee_scope_version ON employee;
CREATE TRIGGER trg_employee_scope_version
AFTER INSERT OR UPDATE OF school_id,active,form_class OR DELETE ON employee
FOR EACH ROW EXECUTE FUNCTION bump_school_permission_version();
DROP TRIGGER IF EXISTS trg_guardian_identity_scope_version ON guardian;
CREATE TRIGGER trg_guardian_identity_scope_version
AFTER INSERT OR UPDATE OF school_id,app_user_id,status OR DELETE ON guardian
FOR EACH ROW EXECUTE FUNCTION bump_school_permission_version();
DROP TRIGGER IF EXISTS trg_timetable_version_scope_version ON timetable_version;
CREATE TRIGGER trg_timetable_version_scope_version
AFTER INSERT OR UPDATE OF school_id,academic_session_id,status,effective_from,effective_to OR DELETE ON timetable_version
FOR EACH ROW EXECUTE FUNCTION bump_school_permission_version();
DROP TRIGGER IF EXISTS trg_school_class_scope_version ON school_class;
CREATE TRIGGER trg_school_class_scope_version
AFTER INSERT OR UPDATE OF school_id,level,subsystem,section_id OR DELETE ON school_class
FOR EACH ROW EXECUTE FUNCTION bump_school_permission_version();

-- Assignment, enrollment, timetable and guardian relationship writes change the
-- result of a scope resolver.  They therefore invalidate the same version as a
-- direct policy edit rather than relying on a stale authorization cache.
DROP TRIGGER IF EXISTS trg_academic_assignment_version ON academic_class_subject_teacher;
CREATE TRIGGER trg_academic_assignment_version
AFTER INSERT OR UPDATE OR DELETE ON academic_class_subject_teacher
FOR EACH ROW EXECUTE FUNCTION bump_school_permission_version();
DROP TRIGGER IF EXISTS trg_class_titulaire_version ON class_teacher_assignment;
CREATE TRIGGER trg_class_titulaire_version
AFTER INSERT OR UPDATE OR DELETE ON class_teacher_assignment
FOR EACH ROW EXECUTE FUNCTION bump_school_permission_version();
DROP TRIGGER IF EXISTS trg_timetable_slot_scope_version ON timetable_slot;
CREATE TRIGGER trg_timetable_slot_scope_version
AFTER INSERT OR UPDATE OR DELETE ON timetable_slot
FOR EACH ROW EXECUTE FUNCTION bump_school_permission_version();
DROP TRIGGER IF EXISTS trg_timetable_substitution_scope_version ON timetable_substitution;
CREATE TRIGGER trg_timetable_substitution_scope_version
AFTER INSERT OR UPDATE OR DELETE ON timetable_substitution
FOR EACH ROW EXECUTE FUNCTION bump_school_permission_version();
DROP TRIGGER IF EXISTS trg_enrollment_scope_version ON student_enrollment;
CREATE TRIGGER trg_enrollment_scope_version
AFTER INSERT OR UPDATE OR DELETE ON student_enrollment
FOR EACH ROW EXECUTE FUNCTION bump_school_permission_version();
DROP TRIGGER IF EXISTS trg_guardian_scope_version ON student_guardian;
CREATE TRIGGER trg_guardian_scope_version
AFTER INSERT OR UPDATE OR DELETE ON student_guardian
FOR EACH ROW EXECUTE FUNCTION bump_school_permission_version();

CREATE OR REPLACE FUNCTION validate_permission_rule_dates()
RETURNS trigger AS $$
DECLARE action_risk VARCHAR(16);
BEGIN
    SELECT risk_level INTO action_risk FROM permission_action WHERE code=NEW.action_code;
    IF action_risk IN ('HIGH','CRITICAL') AND NOT NEW.is_permanent
       AND NEW.effective_to IS NULL THEN
        RAISE EXCEPTION 'High-risk permission grants require an expiry or permanent flag';
    END IF;
    IF NEW.is_permanent AND length(trim(COALESCE(NEW.reason,''))) < 3 THEN
        RAISE EXCEPTION 'Permanent permission grants require a reason';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;
DROP TRIGGER IF EXISTS trg_permission_role_action_validation ON permission_role_action;
CREATE TRIGGER trg_permission_role_action_validation
BEFORE INSERT OR UPDATE ON permission_role_action
FOR EACH ROW EXECUTE FUNCTION validate_permission_rule_dates();
DROP TRIGGER IF EXISTS trg_permission_user_action_validation ON permission_user_action;
CREATE TRIGGER trg_permission_user_action_validation
BEFORE INSERT OR UPDATE ON permission_user_action
FOR EACH ROW EXECUTE FUNCTION validate_permission_rule_dates();

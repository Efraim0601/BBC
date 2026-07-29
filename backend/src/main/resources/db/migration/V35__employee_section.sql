-- ============================================================================
--  V35 — Section (cycle) d'un membre du personnel.
--
--  Un enseignant appartient à UNE section — maternelle, primaire ou secondaire
--  — et ne peut donc pas enseigner dans les trois à la fois. La colonne reprend
--  le vocabulaire des classes (school_class.level) pour que la comparaison
--  « section de l'enseignant = niveau de la classe » soit directe.
--
--  NULL = personnel non enseignant (économe, direction, administration), qui
--  n'est pas cloisonné par section.
-- ============================================================================
ALTER TABLE employee ADD COLUMN IF NOT EXISTS level VARCHAR(12);

ALTER TABLE employee DROP CONSTRAINT IF EXISTS employee_level_check;
ALTER TABLE employee ADD CONSTRAINT employee_level_check
    CHECK (level IS NULL OR level IN ('maternelle','primary','secondary'));

-- Reprise des données : quand toutes les classes déjà assignées à un employé
-- (teacher_class, ou sa classe de titulaire) relèvent d'un seul niveau, ce
-- niveau devient sa section. Les cas ambigus — un employé à cheval sur
-- plusieurs sections — restent à NULL et devront être tranchés à la main.
WITH assigned AS (
    SELECT tc.employee_id AS employee_id, c.level AS level
      FROM teacher_class tc
      JOIN school_class c ON c.id = tc.class_id
    UNION
    SELECT e.id, c.level
      FROM employee e
      JOIN school_class c ON c.name = e.form_class AND c.school_id = e.school_id
     WHERE e.form_class IS NOT NULL AND e.form_class <> ''
), single AS (
    SELECT employee_id, min(level) AS level
      FROM assigned
     GROUP BY employee_id
    HAVING count(DISTINCT level) = 1
)
UPDATE employee e SET level = s.level
  FROM single s
 WHERE s.employee_id = e.id AND e.level IS NULL;

-- Le cloisonnement interroge « quelles classes pour cet enseignant ? » à chaque
-- requête : l'index évite un balayage de teacher_class à chaque appel.
CREATE INDEX IF NOT EXISTS idx_teacher_class_employee ON teacher_class (employee_id);

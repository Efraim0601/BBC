-- BAY-10/BAY-11 remediation: recover durable promotion evidence and restore
-- planned semantics only for rows whose promotion provenance is unambiguous.
-- Applied migrations remain immutable.

-- Older committed batches may have completed the enrollment transaction but
-- missed the register insert. Rebuild the same evidence shape used by the
-- service. The unique key makes this safe on repeated deployment/retry.
WITH manifests AS (
    SELECT b.school_id, b.id AS batch_id,
           json_build_object(
               'batchId', b.id,
               'decisions', COALESCE(json_agg(
                   json_build_object(
                       'decisionId', d.id,
                       'studentId', d.student_id,
                       'finalDecision', d.final_decision,
                       'targetClassId', d.target_class_id,
                       'enrollmentId', d.committed_enrollment_id,
                       'evidence', d.evidence,
                       'annualSnapshot', COALESCE((SELECT json_build_object(
                           'id', v.id, 'version', v.version,
                           'snapshotHash', v.snapshot_hash,
                           'average', v.average, 'state', v.state
                       ) FROM bulletin_version v
                        WHERE v.id::text = NULLIF(d.evidence->>'annualBulletinId','')
                          AND v.school_id = d.school_id), '{}'::json),
                       'documents', COALESCE((SELECT json_agg(json_build_object(
                           'id', g.id, 'documentNumber', g.document_number,
                           'sha256', g.sha256, 'status', g.status, 'locale', g.locale
                       ) ORDER BY g.locale, g.generated_at)
                         FROM generated_document g
                        WHERE g.school_id = d.school_id
                          AND g.aggregate_type = 'BulletinVersion'
                          AND g.aggregate_id = d.evidence->>'annualBulletinId'
                          AND g.status <> 'REVOKED'), '[]'::json)
                   ) ORDER BY d.student_id
               ) FILTER (WHERE d.id IS NOT NULL), '[]'::json)
           )::jsonb AS manifest
      FROM promotion_batch b
      LEFT JOIN promotion_decision d ON d.school_id = b.school_id AND d.batch_id = b.id
     WHERE b.status = 'COMMITTED'
     GROUP BY b.school_id, b.id
)
INSERT INTO promotion_register(id, school_id, batch_id, manifest, sha256, created_by)
SELECT gen_random_uuid(), school_id, batch_id, manifest,
       encode(digest(manifest::text, 'sha256'), 'hex'), NULL
  FROM manifests
ON CONFLICT (school_id, batch_id) DO NOTHING;

-- Backfill the decision link only when the committed enrollment id is an
-- exact match. This does not infer provenance from names, dates, or classes.
UPDATE student_enrollment target
   SET promotion_decision_id = d.id
  FROM promotion_decision d
 WHERE target.school_id = d.school_id
   AND target.id = d.committed_enrollment_id
   AND target.source = 'PROMOTION'
   AND target.promotion_decision_id IS NULL;

-- Recover ACTIVE future targets only when their source enrollment is still
-- ACTIVE and the decision points exactly to the target. Manual/floating rows
-- and already activated rows are intentionally left unchanged.
WITH candidates AS (
    SELECT target.id, target.school_id, target.student_id,
           target.previous_enrollment_id, target.promotion_decision_id,
           d.batch_id, target.reason
      FROM student_enrollment target
      JOIN promotion_decision d
        ON d.school_id = target.school_id
       AND d.id = target.promotion_decision_id
      JOIN student_enrollment source
        ON source.school_id = target.school_id
       AND source.id = target.previous_enrollment_id
     WHERE target.source = 'PROMOTION'
       AND target.status = 'ACTIVE'
       AND source.status = 'ACTIVE'
       AND target.academic_session_id <> source.academic_session_id
       AND EXISTS (SELECT 1 FROM academic_session s
                    WHERE s.id = target.academic_session_id
                      AND s.start_date > current_date)
), changed AS (
    UPDATE student_enrollment target
       SET status = 'PLANNED',
           planned_on = COALESCE(target.planned_on,
                                 (SELECT start_date FROM academic_session s
                                   WHERE s.id = target.academic_session_id)),
           activation_reason = COALESCE(target.activation_reason,
                                        'Recovered promotion planning state'),
           version = target.version + 1,
           updated_at = now()
      FROM candidates c
     WHERE target.id = c.id
     RETURNING target.id, target.school_id, target.student_id,
               target.previous_enrollment_id, c.batch_id,
               COALESCE(target.activation_reason, c.reason) AS reason
)
INSERT INTO promotion_transition_event
    (school_id, student_id, source_enrollment_id, target_enrollment_id,
     promotion_batch_id, action, reason, actor_user_id)
SELECT c.school_id, c.student_id, c.previous_enrollment_id, c.id,
       c.batch_id, 'PLANNED', c.reason, NULL
  FROM changed c
 WHERE NOT EXISTS (
       SELECT 1 FROM promotion_transition_event e
        WHERE e.school_id = c.school_id
          AND e.target_enrollment_id = c.id
          AND e.action = 'PLANNED'
   );

-- Ensure the action catalogue is also materialized for existing schools and
-- roles. Explicit grants win at runtime; these rows make legacy databases
-- behave consistently with the fallback catalogue.
INSERT INTO permission_action_grant (school_id, role_code, action_code, allowed)
SELECT pg.school_id, pg.role_code, a.action_code,
       CASE WHEN a.required_level = 'read'
            THEN bool_or(pg.level IN ('read','write'))
            ELSE bool_or(pg.level = 'write') END
  FROM permission_grant pg
 CROSS JOIN (VALUES
     ('PROGRESSION_VIEW','read'),
     ('PROGRESSION_CONFIGURE','write'),
     ('PROMOTION_REVIEW','write')
 ) AS a(action_code, required_level)
 GROUP BY pg.school_id, pg.role_code, a.action_code, a.required_level
ON CONFLICT (school_id, role_code, action_code) DO UPDATE
    SET allowed = EXCLUDED.allowed;

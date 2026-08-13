-- V67 used PostgreSQL's 63-byte identifier limit; resolve the actual live
-- constraint name in a follow-up migration and attach the legacy thresholds
-- to the explicit published rule set.

ALTER TABLE promotion_rule
    DROP CONSTRAINT IF EXISTS promotion_rule_school_id_academic_session_id_subsystem_leve_key;

UPDATE promotion_rule r
   SET rule_set_id=rs.id, version=r.version+1, updated_at=now()
  FROM promotion_rule_set rs
 WHERE rs.school_id=r.school_id AND rs.academic_session_id=r.academic_session_id
   AND rs.status='PUBLISHED' AND r.active AND r.rule_set_id IS NULL;

-- BAY-11: a published rule set must remain immutable when a new draft is
-- edited.  New rules are version rows attached to the draft rule set.

ALTER TABLE promotion_rule
    DROP CONSTRAINT IF EXISTS promotion_rule_school_id_academic_session_id_subsystem_level_key;
CREATE UNIQUE INDEX IF NOT EXISTS uq_promotion_rule_set_scope
    ON promotion_rule(rule_set_id, COALESCE(subsystem,''), COALESCE(level,''))
    WHERE active AND rule_set_id IS NOT NULL;

ALTER TABLE promotion_decision
    DROP CONSTRAINT IF EXISTS promotion_decision_recommendation_check;
ALTER TABLE promotion_decision
    ADD CONSTRAINT promotion_decision_recommendation_check
    CHECK (recommendation IN ('PROMOTE','REPEAT','REVIEW','GRADUATE','HOLD'));

CREATE UNIQUE INDEX IF NOT EXISTS uq_published_rule_set_session
    ON promotion_rule_set(school_id,academic_session_id)
    WHERE status='PUBLISHED';

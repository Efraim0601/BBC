-- V150: inherited rules are placeholders, not permission grants.
-- The access-control editor submits the complete catalog so that a role
-- replacement is deterministic.  Only explicit ALLOW/DENY rows need the
-- high-risk expiry/permanent check; applying that check to INHERIT rows makes
-- the admin UI impossible to save when a HIGH/CRITICAL action is inherited.
CREATE OR REPLACE FUNCTION validate_permission_rule_dates()
RETURNS trigger AS $$
DECLARE action_risk VARCHAR(16);
BEGIN
    SELECT risk_level INTO action_risk
      FROM permission_action
     WHERE code=NEW.action_code;

    IF NEW.effect IN ('ALLOW','DENY')
       AND action_risk IN ('HIGH','CRITICAL')
       AND NOT NEW.is_permanent
       AND NEW.effective_to IS NULL THEN
        RAISE EXCEPTION 'High-risk permission grants require an expiry or permanent flag';
    END IF;

    IF NEW.is_permanent AND length(trim(COALESCE(NEW.reason,''))) < 3 THEN
        RAISE EXCEPTION 'Permanent permission grants require a reason';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- V133 adds these legacy compatibility grants for existing schools.  Fresh
-- schools do not exist when Flyway runs, so ProductionBootstrap seeds the
-- same three rows after creating the tenant; this migration covers upgrades.
INSERT INTO permission_action_grant (school_id, role_code, action_code, allowed)
SELECT s.id, 'principal', x.action_code, true
  FROM school s
 CROSS JOIN (VALUES
    ('ACADEMIC_GRADE_PACKET_REVIEW'),
    ('ACADEMIC_REPORT_CARD_VALIDATE'),
    ('ACADEMIC_REPORT_CARD_PUBLISH')
 ) x(action_code)
ON CONFLICT (school_id, role_code, action_code)
DO UPDATE SET allowed = EXCLUDED.allowed;

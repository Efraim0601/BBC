-- During the rollout compatibility window the central evaluator reads the
-- generated principal_legacy_compat profile. Keep the narrowly-scoped
-- Direction workflow actions present there as well as on principal.
INSERT INTO permission_role_action
    (school_id, role_code, action_code, effect, scope_mode, is_permanent, reason)
SELECT s.id, 'principal_legacy_compat', x.action_code, 'ALLOW', 'SCHOOL_ALL', true,
       'Direction academic workflow compatibility authority'
  FROM school s
 CROSS JOIN (VALUES
    ('ACADEMIC_GRADE_PACKET_REVIEW'),
    ('ACADEMIC_REPORT_CARD_VALIDATE'),
    ('ACADEMIC_REPORT_CARD_PUBLISH')
 ) x(action_code)
ON CONFLICT DO NOTHING;

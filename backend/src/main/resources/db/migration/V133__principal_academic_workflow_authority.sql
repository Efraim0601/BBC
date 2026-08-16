-- The V2 principal oversight profile already exposes read-only academic
-- oversight.  The review/validate/publish endpoints still have a legacy
-- action gate, so keep the two policy layers aligned for the intended
-- Direction workflow without granting subject-grade editing or setup writes.
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

INSERT INTO permission_role_action
    (school_id, role_code, action_code, effect, scope_mode, is_permanent, reason)
SELECT s.id, 'principal', x.action_code, 'ALLOW', 'SCHOOL_ALL', true,
       'Direction academic workflow: review, validate, and publish'
  FROM school s
 CROSS JOIN (VALUES
    ('ACADEMIC_GRADE_PACKET_REVIEW'),
    ('ACADEMIC_REPORT_CARD_VALIDATE'),
    ('ACADEMIC_REPORT_CARD_PUBLISH')
 ) x(action_code)
ON CONFLICT DO NOTHING;

INSERT INTO permission_role_template_rule
    (template_code, action_code, effect, scope_mode, is_permanent, reason, display_order)
VALUES
 ('principal_oversight','ACADEMIC_GRADE_PACKET_REVIEW','ALLOW','SCHOOL_ALL',true,
  'Direction academic workflow review',30),
 ('principal_oversight','ACADEMIC_REPORT_CARD_VALIDATE','ALLOW','SCHOOL_ALL',true,
  'Direction academic workflow validation',31),
 ('principal_oversight','ACADEMIC_REPORT_CARD_PUBLISH','ALLOW','SCHOOL_ALL',true,
  'Direction academic workflow publication',32)
ON CONFLICT DO NOTHING;

-- New schools are created after Flyway migrations have completed.  Backfill
-- the school-scoped attendance defaults and teacher session-read action for
-- both existing schools and schools created by a later bootstrap.

INSERT INTO attendance_policy
    (school_id, level, model, late_after_minutes,
     chronic_absence_percent, require_absence_reason)
SELECT s.id, x.level, x.model, 0, 20.00, false
FROM school s
CROSS JOIN (VALUES
    ('maternelle', 'DAILY'),
    ('primary',    'DAILY'),
    ('secondary',  'PERIOD')
) x(level, model)
ON CONFLICT (school_id, level) DO NOTHING;

INSERT INTO permission_action_grant(school_id, role_code, action_code, allowed)
SELECT s.id, r.role_code, a.action_code, true
FROM school s
CROSS JOIN (VALUES ('principal'), ('prefect')) r(role_code)
CROSS JOIN (VALUES
    ('ATTENDANCE_ROSTER_VIEW'), ('ATTENDANCE_MARK'),
    ('ATTENDANCE_FINALIZE'), ('ATTENDANCE_REOPEN'),
    ('ATTENDANCE_ANALYTICS_VIEW'), ('ATTENDANCE_POLICY_MANAGE'),
    ('ATTENDANCE_RECONCILE')
) a(action_code)
ON CONFLICT (school_id, role_code, action_code) DO NOTHING;

INSERT INTO permission_action_grant(school_id, role_code, action_code, allowed)
SELECT s.id, r.role_code, a.action_code, true
FROM school s
CROSS JOIN (VALUES ('teacher'), ('form_teacher')) r(role_code)
CROSS JOIN (VALUES
    ('ATTENDANCE_ROSTER_VIEW'), ('ATTENDANCE_MARK'),
    ('ATTENDANCE_FINALIZE'), ('ATTENDANCE_ANALYTICS_VIEW'),
    ('SESSION_VIEW')
) a(action_code)
ON CONFLICT (school_id, role_code, action_code) DO NOTHING;

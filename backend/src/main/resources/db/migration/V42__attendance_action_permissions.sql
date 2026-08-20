-- BAY-9: actionable attendance for scoped teachers without configuration access.
INSERT INTO permission_action_grant (school_id, role_code, action_code, allowed)
SELECT s.id, r.role_code, a.action_code, true
FROM school s
CROSS JOIN (VALUES ('principal'),('prefect')) r(role_code)
CROSS JOIN (VALUES
    ('ATTENDANCE_ROSTER_VIEW'),('ATTENDANCE_MARK'),('ATTENDANCE_FINALIZE'),
    ('ATTENDANCE_REOPEN'),('ATTENDANCE_ANALYTICS_VIEW'),
    ('ATTENDANCE_POLICY_MANAGE'),('ATTENDANCE_RECONCILE')
) a(action_code)
ON CONFLICT (school_id, role_code, action_code) DO UPDATE SET allowed=true;

INSERT INTO permission_action_grant (school_id, role_code, action_code, allowed)
SELECT s.id, r.role_code, a.action_code, true
FROM school s
CROSS JOIN (VALUES ('teacher'),('form_teacher')) r(role_code)
CROSS JOIN (VALUES
    ('ATTENDANCE_ROSTER_VIEW'),('ATTENDANCE_MARK'),('ATTENDANCE_FINALIZE'),
    ('ATTENDANCE_ANALYTICS_VIEW')
) a(action_code)
ON CONFLICT (school_id, role_code, action_code) DO UPDATE SET allowed=true;

-- The academic UI loads the session-scoped roster through /api/students/roster.
-- The endpoint still applies TeacherScopeService/AcademicAccessPolicyService,
-- so this module read grant does not broaden a teacher beyond assigned classes.
INSERT INTO permission_grant(school_id, role_code, module, level)
SELECT s.id, 'teacher', 'students', 'read'
FROM school s
ON CONFLICT (school_id, role_code, module)
DO UPDATE SET level = CASE
    WHEN permission_grant.level = 'write' THEN permission_grant.level
    ELSE 'read'
END;

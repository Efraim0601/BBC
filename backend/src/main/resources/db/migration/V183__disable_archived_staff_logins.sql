-- Staff archival previously left its app_user active. Preserve all staff/student,
-- payroll and academic records; only revoke login access for already inactive staff.
UPDATE app_user u
   SET active = false,
       credentials_version = credentials_version + 1
  FROM employee e
 WHERE u.employee_id = e.id
   AND u.school_id = e.school_id
   AND e.active = false
   AND u.active = true;

-- The BCrypt hash seeded in V2/V3 did NOT actually correspond to "password"
-- (a commonly copy-pasted but incorrect hash). Reset the demo accounts to a
-- verified BCrypt("password", cost=10) so the documented credentials work.
UPDATE app_user
   SET password_hash = '$2a$10$v0JND6DMVb87FMt3L.uZxem0ymfNyn5J/78P0Ra39qaVZGVfspwUe'
 WHERE username IN ('principal', 'econome', 'parent1');

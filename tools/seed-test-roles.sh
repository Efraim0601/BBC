#!/usr/bin/env bash
# ============================================================================
#  BBC SMS — créer des comptes de test (un par rôle) directement en base.
#
#  Usage (sur le serveur, depuis /opt/BBC) :
#    ./tools/seed-test-roles.sh
#    ./tools/seed-test-roles.sh --password 'MonMotDePasse'
#    COMPOSE_FILE=docker-compose.server.yml ./tools/seed-test-roles.sh
#
#  Idempotent : ré-exécuter met à jour mot de passe / rôle / fiche employé.
#  Mot de passe par défaut : Test2026!
# ============================================================================
set -euo pipefail
cd "$(dirname "$0")/.."

PASSWORD="Test2026!"
COMPOSE_FILE="${COMPOSE_FILE:-}"
DB_SERVICE=db
DB_USER=bbc
DB_NAME=bbc_sms

while [[ $# -gt 0 ]]; do
  case "$1" in
    --password|-p) PASSWORD="$2"; shift 2 ;;
    --compose|-f)  COMPOSE_FILE="$2"; shift 2 ;;
    -h|--help)
      sed -n '2,14p' "$0"
      exit 0
      ;;
    *) echo "Option inconnue : $1" >&2; exit 1 ;;
  esac
done

# ---- Detect compose stack (prod domain vs server self-signed) --------------
if [[ -z "${COMPOSE_FILE}" ]]; then
  if docker ps --format '{{.Names}}' | grep -q '^bbc-prod-db-1$'; then
    COMPOSE_FILE=docker-compose.letsencrypt.yml
  elif docker ps --format '{{.Names}}' | grep -q '^bbc-server-db-1$'; then
    COMPOSE_FILE=docker-compose.server.yml
  else
    echo "✗ Aucun conteneur DB BBC trouvé (bbc-prod-db-1 / bbc-server-db-1)." >&2
    echo "  Démarrez la stack ou passez --compose docker-compose.letsencrypt.yml" >&2
    exit 1
  fi
fi

COMPOSE=(docker compose -f "${COMPOSE_FILE}")
echo "→ Stack : ${COMPOSE_FILE}"

psql_q() {
  "${COMPOSE[@]}" exec -T "${DB_SERVICE}" \
    psql -U "${DB_USER}" -d "${DB_NAME}" -v ON_ERROR_STOP=1 "$@"
}

# ---- BCrypt hash (Spring accepts $2a$ / $2b$ / $2y$) -----------------------
hash_password() {
  local pw="$1"
  if command -v python3 >/dev/null 2>&1; then
    if python3 -c 'import bcrypt' 2>/dev/null; then
      python3 -c "import bcrypt,sys; print(bcrypt.hashpw(sys.argv[1].encode(), bcrypt.gensalt(rounds=10)).decode())" "$pw"
      return
    fi
  fi
  # Fallback: known BCrypt("Test2026!", cost=10) — only valid for that password.
  if [[ "$pw" == "Test2026!" ]]; then
    echo '$2b$10$jkb9tzGYJLjDeJ0Lkchq7OFaEhsT1rQ92jOSbl2upgCAMMm3o5Jei'
    return
  fi
  echo "✗ Impossible de hasher « ${pw} » (installez python3-bcrypt, ou utilisez --password Test2026!)." >&2
  exit 1
}

HASH="$(hash_password "${PASSWORD}")"
echo "→ Mot de passe commun : ${PASSWORD}"

# ---- Ensure school + roles exist, then upsert staff accounts ---------------
# Pass the BCrypt hash via a psql variable so bash does not expand its '$' chars.
psql_q -v hash="${HASH}" <<'SQL'
-- Target school (first tenant). Fail loudly if none.
DO $$
DECLARE
  sid UUID;
  n   INT;
BEGIN
  SELECT count(*) INTO n FROM school;
  IF n = 0 THEN
    RAISE EXCEPTION 'Aucune école en base — connectez-vous d''abord avec l''admin bootstrap.';
  END IF;
  SELECT id INTO sid FROM school ORDER BY code LIMIT 1;

  -- Roles (idempotent)
  INSERT INTO role (code, label_fr, label_en) VALUES
    ('principal',    'Principal',        'Principal'),
    ('prefect',      'Préfet d''études', 'Dean of studies'),
    ('econome',      'Économe',          'Bursar'),
    ('form_teacher', 'Prof. Principal',  'Form Teacher'),
    ('teacher',      'Enseignant',       'Teacher'),
    ('parent',       'Parent',           'Parent')
  ON CONFLICT (code) DO NOTHING;

  -- Minimal permission matrix if missing (same spirit as ProductionBootstrap)
  INSERT INTO permission_grant (school_id, role_code, module, level)
  SELECT sid, 'principal', m, 'write'
  FROM unnest(ARRAY[
    'dashboard','presence','students','hr','academic','finance','timetable',
    'events','discipline','reports','settings','journey','alerts','messages',
    'coursebook','health','documents','classkit','parent'
  ]) AS m
  ON CONFLICT (school_id, role_code, module) DO NOTHING;

  INSERT INTO permission_grant (school_id, role_code, module, level)
  SELECT sid, r, m, lvl FROM (VALUES
    ('prefect','presence','write'),('prefect','timetable','write'),('prefect','events','write'),
    ('prefect','discipline','write'),('prefect','journey','write'),('prefect','alerts','write'),
    ('prefect','messages','write'),('prefect','documents','write'),
    ('prefect','dashboard','read'),('prefect','students','read'),('prefect','academic','read'),
    ('prefect','reports','read'),('prefect','coursebook','read'),('prefect','health','read'),
    ('prefect','classkit','read'),
    ('econome','finance','write'),
    ('econome','dashboard','read'),('econome','students','read'),('econome','reports','read'),
    ('econome','alerts','read'),('econome','classkit','read'),
    ('form_teacher','academic','write'),('form_teacher','discipline','write'),
    ('form_teacher','coursebook','write'),('form_teacher','messages','write'),
    ('form_teacher','classkit','write'),
    ('form_teacher','dashboard','read'),('form_teacher','presence','read'),
    ('form_teacher','students','read'),('form_teacher','timetable','read'),
    ('form_teacher','events','read'),('form_teacher','journey','read'),
    ('form_teacher','alerts','read'),('form_teacher','health','read'),
    ('form_teacher','documents','read'),
    ('teacher','academic','write'),('teacher','coursebook','write'),
    ('teacher','dashboard','read'),('teacher','presence','read'),
    ('teacher','timetable','read'),('teacher','events','read'),('teacher','messages','read'),
    ('teacher','classkit','read'),
    ('parent','parent','read')
  ) AS t(r, m, lvl)
  ON CONFLICT (school_id, role_code, module) DO NOTHING;
END $$;

-- Staff test profiles: employee + login
WITH sch AS (SELECT id AS school_id, code AS school_code FROM school ORDER BY code LIMIT 1),
defs AS (
  SELECT * FROM (VALUES
    ('TEST-PRINCIPAL',    'demo.principal',    'principal',    'Demo Principal',        'DP', 'M'),
    ('TEST-PREFECT',      'demo.prefect',      'prefect',      'Demo Préfet',           'DF', 'M'),
    ('TEST-ECONOME',      'demo.econome',      'econome',      'Demo Économe',          'DE', 'F'),
    ('TEST-FORM',         'demo.form_teacher', 'form_teacher', 'Demo Prof. Principal',  'PP', 'F'),
    ('TEST-TEACHER',      'demo.teacher',      'teacher',      'Demo Enseignant',       'TE', 'M')
  ) AS v(emp_code, username, role_code, display_name, initials, sex)
),
upsert_emp AS (
  INSERT INTO employee (school_id, code, name, initials, sex, type, email, active)
  SELECT s.school_id, d.emp_code, d.display_name, d.initials, d.sex, 'Permanent',
         lower(d.username) || '@test.bbc.local', true
  FROM defs d CROSS JOIN sch s
  ON CONFLICT (school_id, code) DO UPDATE
    SET name = EXCLUDED.name,
        initials = EXCLUDED.initials,
        email = EXCLUDED.email,
        active = true
  RETURNING id, school_id, code
)
INSERT INTO app_user (school_id, username, password_hash, display_name, initials, role_code, employee_id, locale, active)
SELECT e.school_id, d.username, :'hash', d.display_name, d.initials, d.role_code, e.id, 'fr', true
FROM upsert_emp e
JOIN defs d ON d.emp_code = e.code
ON CONFLICT (school_id, username) DO UPDATE
  SET password_hash = EXCLUDED.password_hash,
      display_name  = EXCLUDED.display_name,
      initials      = EXCLUDED.initials,
      role_code     = EXCLUDED.role_code,
      employee_id   = EXCLUDED.employee_id,
      active        = true;

-- employee_role links
INSERT INTO employee_role (employee_id, role_code)
SELECT e.id, d.role_code
FROM employee e
JOIN (VALUES
  ('TEST-PRINCIPAL','principal'),
  ('TEST-PREFECT','prefect'),
  ('TEST-ECONOME','econome'),
  ('TEST-FORM','form_teacher'),
  ('TEST-TEACHER','teacher')
) AS d(emp_code, role_code) ON d.emp_code = e.code
JOIN school s ON s.id = e.school_id
WHERE e.code LIKE 'TEST-%'
ON CONFLICT DO NOTHING;

-- Parent test account (no employee)
WITH sch AS (SELECT id AS school_id FROM school ORDER BY code LIMIT 1)
INSERT INTO app_user (school_id, username, password_hash, display_name, initials, role_code, locale, active)
SELECT s.school_id, 'demo.parent', :'hash', 'Demo Parent', 'PA', 'parent', 'fr', true
FROM sch s
ON CONFLICT (school_id, username) DO UPDATE
  SET password_hash = EXCLUDED.password_hash,
      display_name  = EXCLUDED.display_name,
      role_code     = 'parent',
      employee_id   = NULL,
      active        = true;

-- Show results
SELECT u.username AS identifiant,
       r.label_fr AS role,
       u.display_name AS nom,
       s.code AS ecole,
       u.active
FROM app_user u
JOIN school s ON s.id = u.school_id
JOIN role r ON r.code = u.role_code
WHERE u.username LIKE 'demo.%'
ORDER BY CASE u.role_code
  WHEN 'principal' THEN 1
  WHEN 'prefect' THEN 2
  WHEN 'econome' THEN 3
  WHEN 'form_teacher' THEN 4
  WHEN 'teacher' THEN 5
  WHEN 'parent' THEN 6
  ELSE 9 END;
SQL

echo
echo "──────────────────────────────────────────────────────────────"
echo "  Comptes de test prêts  (mot de passe : ${PASSWORD})"
echo "──────────────────────────────────────────────────────────────"
printf "%-22s %-16s %s\n" "IDENTIFIANT" "RÔLE" "NOM"
printf "%-22s %-16s %s\n" "----------------------" "----------------" "--------------------"
printf "%-22s %-16s %s\n" "demo.principal"    "principal"     "Demo Principal"
printf "%-22s %-16s %s\n" "demo.prefect"      "prefect"       "Demo Préfet"
printf "%-22s %-16s %s\n" "demo.econome"      "econome"       "Demo Économe"
printf "%-22s %-16s %s\n" "demo.form_teacher" "form_teacher"  "Demo Prof. Principal"
printf "%-22s %-16s %s\n" "demo.teacher"      "teacher"       "Demo Enseignant"
printf "%-22s %-16s %s\n" "demo.parent"       "parent"        "Demo Parent"
echo "──────────────────────────────────────────────────────────────"
echo "  URL : https://bbcomplex.com  (ou votre domaine)"
echo "  Ré-exécutable sans doublon. Changez le mdp : --password 'xxx'"
echo "──────────────────────────────────────────────────────────────"

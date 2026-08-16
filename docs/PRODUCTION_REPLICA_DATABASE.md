# Local production-replica database

This is a sanitized copy of the current production-simulation database. It is
kept separate from the working/test database so test activity can continue
without changing the clean production starting point.

## Two switchable application stacks

| Stack | Browser | Backend | PostgreSQL | Volume | Purpose |
|---|---:|---:|---:|---|---|
| `bbc-prod-simulation` | `http://localhost:8110` | `8111` | `5542` | `bbc-prod-2026-db` | Current working/test data |
| `bbc-production-replica` | `http://localhost:8120` | `8121` | `5543` | `bbc-production-replica-db` | Clean production starting point |

The two stacks use the same code but different database and document volumes.
Use the browser URL to switch between them. They may run simultaneously.

The Compose-managed database container is named
`bbc-production-replica-db-1`; the volume name is deliberately stable and
external (`bbc-production-replica-db`) so rebuilding the application stack
does not create a fresh database.

Start the clean replica from the full-school worktree:

```powershell
docker compose -p bbc-production-replica `
  -f docker-compose.yml `
  -f docker-compose.production-replica.yml `
  up -d --build
```

Open `http://localhost:8120` and sign in with `admin / admin`.

Start the working/test stack with its existing command when needed:

```powershell
docker compose -p bbc-prod-simulation `
  -f docker-compose.yml `
  -f docker-compose.full-e2e.yml `
  -f tmp/docker-compose.prod-simulation.yml `
  up -d --build
```

Do not run an unqualified `docker compose up`: it can attach the browser to a
different local database.

## What the replica contains

The clone starts from the current `bbc-prod-simulation-db-1` database, not from
an empty schema and not from the older legacy replica. The following remains:

- school profile, sections/parcours, 46 classes, 42 subjects;
- both academic sessions, terms, reporting periods, dependencies, and windows;
- 532 class-subject curriculum/coefficient assignments;
- 3,192 generated assessment definitions/templates for the configured periods;
- attendance policies and calendar configuration;
- fee types, fee plans, installment template, chart of accounts, posting rules,
  accounting periods, and payment channels;
- timetable periods, rooms, class/time/subject slot shape, and timetable
  version configuration. Teacher identities are cleared and the timetable is
  left in draft because there are no staff to publish against;
- roles, action catalogue, role-level grants, templates, school permission
  version, and the administrator's 89 fine-grained permission overrides;
- 740 imported/source students (`BBC-1001` through `BBC-1740`), their current
  session enrollments, and their 740 guardian/contact links.

## What is intentionally empty

- `employee`, teacher assignments, staff applications, payroll and all staff
  login accounts;
- every login except the administrator;
- attendance sessions, records, marks, events, and attendance notifications;
- grades, grade packets, subject remarks, bulletin versions, and batch artifacts;
- finance charges, installments, invoices, payments, receipts, journal entries,
  reconciliation, refunds, expenses, and other financial transactions;
- promotion/journey decisions, operational alerts, generated documents, audit
  history, import logs, and other test activity;
- the five demonstration students and their demonstration parent accounts:
  `BBC-1741`–`BBC-1745`.

Parent/contact records for retained students remain as contact data, but no
parent portal login is retained. New parent accounts can be created through the
normal student-registration flow after the production administrator is ready.

## Recreate the replica safely

The sanitisation SQL is explicit and guarded by checks for the target database,
the active administrator, and all five demonstration matricules:

[`tools/sanitize-production-replica.sql`](../tools/sanitize-production-replica.sql)

The clone/sanitize sequence is:

```powershell
docker run --name bbc-production-replica-db --restart unless-stopped `
  -e POSTGRES_DB=bbc_sms -e POSTGRES_USER=bbc -e POSTGRES_PASSWORD=bbc `
  -p 5543:5432 -v bbc-production-replica-db:/var/lib/postgresql/data `
  --health-cmd "pg_isready -U bbc -d bbc_sms" `
  --health-interval 5s --health-timeout 3s --health-retries 20 `
  -d postgres:16-alpine

docker exec bbc-prod-simulation-db-1 pg_dump -U bbc -d bbc_sms -Fc `
  --no-owner --no-privileges |
  docker exec -i bbc-production-replica-db pg_restore -U bbc -d bbc_sms `
  --no-owner --no-privileges --clean --if-exists

Get-Content tools/sanitize-production-replica.sql -Raw |
  docker exec -i bbc-production-replica-db psql -U bbc -d bbc_sms `
  -v ON_ERROR_STOP=1
```

The current working database is never a write target during sanitisation. Do
not use `docker volume rm` or point the SQL file at
`bbc-prod-simulation-db-1`.

The one-time clone commands above use the temporary container name
`bbc-production-replica-db`. After the replica is brought up through Compose,
use `bbc-production-replica-db-1` in verification commands, or resolve the
name with `docker compose ... ps`.

## Verification checklist

```powershell
docker exec bbc-production-replica-db psql -U bbc -d bbc_sms -c `
  "select count(*) from student;"
docker exec bbc-production-replica-db psql -U bbc -d bbc_sms -c `
  "select count(*) from employee;"
docker exec bbc-production-replica-db psql -U bbc -d bbc_sms -c `
  "select count(*) from academic_grade;"
docker exec bbc-production-replica-db psql -U bbc -d bbc_sms -c `
  "select count(*) from attendance_mark;"
docker exec bbc-production-replica-db psql -U bbc -d bbc_sms -c `
  "select count(*) from payment;"
```

Expected values are 740 students, 0 employees, 0 grades, 0 attendance marks,
and 0 payments. The only row in `app_user` is `admin`; the access-control
tables remain populated.

Opening a primary roster in the UI creates a draft attendance session and blank
roster marks on demand. That is expected application behavior, but it is
operational data; clear it before handing the replica to production if the
screen was used for a smoke test:

```powershell
@'
TRUNCATE TABLE attendance_mark, attendance_notification,
  attendance_period_adjustment, attendance_record, attendance_session_event,
  attendance_session, alert RESTART IDENTITY;
'@ | docker exec -i bbc-production-replica-db-1 psql -U bbc -d bbc_sms
```

# Local test credentials — production simulation

> **Local-only test data.** These credentials are for the Docker production-simulation environment on this computer. Do not reuse them in a real production environment, publish them, or commit this file to a public repository.

## Connection

- Application: <http://localhost:8110/login>
- API: <http://localhost:8111>
- Establishment: **École publique de Biyem-Assi**
- Establishment code: **BBC-PROD-2026**
- Docker project: `bbc-prod-simulation`

The credentials below were checked against the active users in the current simulation database. Staff passwords are the latest values issued by the local Mailpit credential messages.

## Administrator and direction

| Username | Password | Role | Use this account for |
| --- | --- | --- | --- |
| `admin` | `admin` | Principal / global administrator | Full configuration, all parcours, users, permissions, sessions, classes, courses, finance and system administration. |
| `nsona.mireille` | `Fuban9Xv43` | Principal / direction | Direction-level access-control and school-management testing. |

`admin` is the global test administrator and is the account to use when a test requires the **Tous les parcours / All parcours** scope or a permission change for another role.

## Accountant

| Username | Password | Role | Use this account for |
| --- | --- | --- | --- |
| `fotso.bernard` | `qkCVVQ2aRR` | Accountant | Finance dashboard, fee structures, class/parcours fee assignment, installments, collections, payer lookup, receipts, invoices and accounting workflow. |

Use the latest password shown above. An older local credential message contained `39XTRZECg4`; treat that value as expired.

## Teachers

All of these accounts have the `teacher` role. Their academic, attendance and timetable visibility is assignment-derived: use them to verify that a teacher can access only the classes and class-course assignments granted to that teacher. The class titular has additional visibility according to the current access-control configuration.

| Username | Password | Role | Main test purpose |
| --- | --- | --- | --- |
| `aline.ndom` | `x7fRhJbp4u` | Teacher | Assigned-class academic entry, attendance and timetable scope. |
| `grace.forchu` | `Vr9jmJgywv` | Teacher | Assigned-class academic entry, attendance and timetable scope. |
| `jeanne.dongmo` | `JEXG83JM5q` | Teacher | Titular/teacher access checks where assigned. |
| `john.nji` | `fqEcnFkxCu` | Teacher | Assigned-class academic entry, attendance and timetable scope. |
| `mbah.junior` | `XKRkC4pWTD` | Teacher | Primary/homeroom titular workflow where assigned. |
| `ngounou.fabrice` | `VjzzJGByeb` | Teacher | Secondary subject-teacher workflow where assigned. |
| `paul.ngono` | `WgDU4Jymmb` | Teacher | Assigned-class academic entry, attendance and timetable scope. |
| `tagne.joel` | `BFse7wh98b` | Teacher | Assigned-class academic entry, attendance and timetable scope. |
| `teneku.donal` | `6sbZRMXEbD` | Teacher | Secondary subject-teacher workflow where assigned. |

Teacher accounts intentionally do **not** have administrator powers. When testing a teacher:

1. Log out of the administrator account first; do not rely on two tabs sharing the same browser session.
2. Sign in with the teacher account.
3. Test grade entry, attendance and the teacher timetable using only the classes and subjects displayed for that account.
4. Attempting to open another class, another subject, student transfer, finance, session configuration or timetable administration is an expected permission-boundary test.

## Parent accounts

| Username | Password | Role | Use this account for |
| --- | --- | --- | --- |
| `parent.demo@biyem-assi.cm` | **Not retained in plaintext** | Parent | Parent portal and linked-student visibility. |
| `parent.parcours@biyem-assi.cm` | **Not retained in plaintext** | Parent | Parent portal and linked-student/parcours visibility. |

These parent accounts exist and are active in the simulation database, but no parent credential email containing their plaintext passwords remains in the local Mailpit store. Do not guess their passwords. Reset or re-issue the parent credentials through the application’s parent-account/invitation flow before testing the parent portal.

## Role expectations while testing

- **Administrator:** may configure the school and grant permissions; normally has global parcours scope.
- **Principal/direction:** direction and academic oversight; exact access is controlled by the role’s configured permissions and scope.
- **Accountant:** financial operations for the school; should not need academic editing rights to collect a payment.
- **Teacher:** access is derived from teacher/class/course assignments. In secondary, the teacher should be limited to the subjects assigned to them in each class. In primary, the titular can have the class-wide workflow when configured.
- **Parent:** access is limited to the parent’s linked children and parent-facing information.

## Avoid stale demo credentials

The following values appear in older demo documentation or older local deployments and are **not** the credentials for the current `8110` production simulation:

- `principal / password`
- `econome / password`
- `parent1 / password`
- `fotso.bernard / 39XTRZECg4`

If a login behaves differently from this document, first confirm that the browser is actually on `http://localhost:8110` and that the current Docker project is `bbc-prod-simulation`, not an older full-E2E stack.

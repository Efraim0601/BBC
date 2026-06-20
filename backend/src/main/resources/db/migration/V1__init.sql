-- ============================================================================
--  BBC SMS — initial schema (multi-tenant by school_id)
--  Money is stored in integer FCFA (no floats). All tenant tables carry school_id.
-- ============================================================================

-- ---- Tenant -----------------------------------------------------------------
CREATE TABLE school (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code        VARCHAR(32)  NOT NULL UNIQUE,
    name        VARCHAR(160) NOT NULL,
    motto       VARCHAR(200),
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE academic_year (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id   UUID NOT NULL REFERENCES school(id),
    label       VARCHAR(16) NOT NULL,        -- e.g. 2025-2026
    start_year  INT NOT NULL,
    is_current  BOOLEAN NOT NULL DEFAULT false,
    UNIQUE (school_id, label)
);

-- ---- Identity & RBAC --------------------------------------------------------
CREATE TABLE role (
    code        VARCHAR(32) PRIMARY KEY,     -- principal, prefect, econome, form_teacher, teacher, parent
    label_fr    VARCHAR(64) NOT NULL,
    label_en    VARCHAR(64) NOT NULL,
    builtin     BOOLEAN NOT NULL DEFAULT true
);

-- permission matrix: role x module -> level (none|read|write)
CREATE TABLE permission_grant (
    id          BIGSERIAL PRIMARY KEY,
    school_id   UUID NOT NULL REFERENCES school(id),
    role_code   VARCHAR(32) NOT NULL REFERENCES role(code),
    module      VARCHAR(32) NOT NULL,        -- dashboard, presence, students, hr, academic, finance, ...
    level       VARCHAR(8)  NOT NULL CHECK (level IN ('none','read','write')),
    UNIQUE (school_id, role_code, module)
);

-- Employees: canonical staff registry (a teacher teaches N subjects AND N classes)
CREATE TABLE employee (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id       UUID NOT NULL REFERENCES school(id),
    code            VARCHAR(16) NOT NULL,    -- EMP-001
    name            VARCHAR(120) NOT NULL,
    initials        VARCHAR(4),
    hue             INT DEFAULT 210,
    sex             CHAR(1) CHECK (sex IN ('M','F')),
    type            VARCHAR(16) NOT NULL DEFAULT 'Permanent', -- Permanent | Vacataire
    email           VARCHAR(160),
    phone           VARCHAR(40),
    form_class      VARCHAR(32),
    hired_on        DATE,
    monthly_salary  BIGINT DEFAULT 0,        -- FCFA
    hourly_rate     INT DEFAULT 0,           -- FCFA
    monthly_hours   INT DEFAULT 0,
    active          BOOLEAN NOT NULL DEFAULT true,
    UNIQUE (school_id, code)
);
CREATE TABLE employee_role (
    employee_id UUID NOT NULL REFERENCES employee(id) ON DELETE CASCADE,
    role_code   VARCHAR(32) NOT NULL REFERENCES role(code),
    PRIMARY KEY (employee_id, role_code)
);

-- Login accounts. An account is either an employee or a parent.
CREATE TABLE app_user (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id       UUID NOT NULL REFERENCES school(id),
    username        VARCHAR(64) NOT NULL,
    password_hash   VARCHAR(100) NOT NULL,
    display_name    VARCHAR(120) NOT NULL,
    initials        VARCHAR(4),
    role_code       VARCHAR(32) NOT NULL REFERENCES role(code),
    employee_id     UUID REFERENCES employee(id),
    locale          VARCHAR(4) NOT NULL DEFAULT 'fr',
    active          BOOLEAN NOT NULL DEFAULT true,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (school_id, username)
);

-- ---- School structure -------------------------------------------------------
CREATE TABLE section (
    id          VARCHAR(16) PRIMARY KEY,     -- pri-fr, pri-en, sec-fr, sec-en
    school_id   UUID NOT NULL REFERENCES school(id),
    label       VARCHAR(40) NOT NULL,
    subsystem   CHAR(2) NOT NULL CHECK (subsystem IN ('FR','EN')),
    level       VARCHAR(12) NOT NULL CHECK (level IN ('primary','secondary'))
);

CREATE TABLE school_class (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id   UUID NOT NULL REFERENCES school(id),
    section_id  VARCHAR(16) NOT NULL REFERENCES section(id),
    name        VARCHAR(32) NOT NULL,        -- SIL, CP, 6ème, Form 1...
    subsystem   CHAR(2) NOT NULL,
    level       VARCHAR(12) NOT NULL,
    UNIQUE (school_id, name)
);

CREATE TABLE subject (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id   UUID NOT NULL REFERENCES school(id),
    code        VARCHAR(8) NOT NULL,         -- MATH, FR, EN...
    label       JSONB NOT NULL,              -- {"fr":"Mathématiques","en":"Mathematics"}
    coef        INT NOT NULL DEFAULT 1,
    UNIQUE (school_id, code)
);

CREATE TABLE teacher_subject (
    employee_id UUID NOT NULL REFERENCES employee(id) ON DELETE CASCADE,
    subject_id  UUID NOT NULL REFERENCES subject(id) ON DELETE CASCADE,
    PRIMARY KEY (employee_id, subject_id)
);
CREATE TABLE teacher_class (
    employee_id UUID NOT NULL REFERENCES employee(id) ON DELETE CASCADE,
    class_id    UUID NOT NULL REFERENCES school_class(id) ON DELETE CASCADE,
    PRIMARY KEY (employee_id, class_id)
);

-- ---- Students & parents -----------------------------------------------------
CREATE TABLE student (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id       UUID NOT NULL REFERENCES school(id),
    matricule       VARCHAR(16) NOT NULL,    -- BBC-1001
    first_name      VARCHAR(60) NOT NULL,
    last_name       VARCHAR(60) NOT NULL,
    sex             CHAR(1) CHECK (sex IN ('M','F')),
    dob             DATE,
    class_id        UUID REFERENCES school_class(id),
    class_name      VARCHAR(32),
    subsystem       CHAR(2),
    level           VARCHAR(12),
    parent_name     VARCHAR(120),
    parent_phone    VARCHAR(40),
    photo_hue       INT DEFAULT 210,
    active          BOOLEAN NOT NULL DEFAULT true,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (school_id, matricule)
);

CREATE TABLE parent_student (
    parent_user_id  UUID NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    student_id      UUID NOT NULL REFERENCES student(id) ON DELETE CASCADE,
    PRIMARY KEY (parent_user_id, student_id)
);

-- ---- Finance ----------------------------------------------------------------
CREATE TABLE fee_config (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id   UUID NOT NULL REFERENCES school(id),
    level       VARCHAR(12) NOT NULL,
    subsystem   CHAR(2),
    total       BIGINT NOT NULL,             -- FCFA
    tranches    JSONB NOT NULL,              -- [40000,30000,25000]
    items       JSONB,                       -- [{"name":"Scolarité","amount":75000}]
    UNIQUE (school_id, level, subsystem)
);

CREATE TABLE student_fee (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id       UUID NOT NULL REFERENCES school(id),
    student_id      UUID NOT NULL REFERENCES student(id) ON DELETE CASCADE,
    total           BIGINT NOT NULL,
    paid            BIGINT NOT NULL DEFAULT 0,
    balance         BIGINT NOT NULL,
    tranches_paid   INT NOT NULL DEFAULT 0,
    status          VARCHAR(8) NOT NULL DEFAULT 'unpaid' CHECK (status IN ('paid','partial','unpaid')),
    UNIQUE (school_id, student_id)
);

CREATE TABLE payment (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id       UUID NOT NULL REFERENCES school(id),
    receipt_no      VARCHAR(32) NOT NULL,
    student_id      UUID NOT NULL REFERENCES student(id),
    amount          BIGINT NOT NULL CHECK (amount > 0),
    method          VARCHAR(20) NOT NULL,    -- Espèces | Mobile Money | Virement
    tranche         INT,
    paid_on         DATE NOT NULL,
    created_by      UUID REFERENCES app_user(id),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (school_id, receipt_no)
);

CREATE TABLE expense (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id   UUID NOT NULL REFERENCES school(id),
    spent_on    DATE NOT NULL,
    category    VARCHAR(40) NOT NULL,
    label       VARCHAR(160) NOT NULL,
    amount      BIGINT NOT NULL CHECK (amount > 0)
);

-- ---- Attendance (real-time) -------------------------------------------------
CREATE TABLE device (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id   UUID NOT NULL REFERENCES school(id),
    label       VARCHAR(80) NOT NULL,
    api_key     VARCHAR(80) NOT NULL UNIQUE,
    active      BOOLEAN NOT NULL DEFAULT true
);

CREATE TABLE attendance_record (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id       UUID NOT NULL REFERENCES school(id),
    student_id      UUID NOT NULL REFERENCES student(id) ON DELETE CASCADE,
    att_date        DATE NOT NULL,
    status          VARCHAR(8) NOT NULL CHECK (status IN ('present','late','absent')),
    check_in_time   VARCHAR(5),              -- HH:mm
    late_minutes    INT NOT NULL DEFAULT 0,
    source          VARCHAR(12) NOT NULL DEFAULT 'manual', -- fingerprint | manual
    dedup_key       VARCHAR(80),             -- idempotency for device replays
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (school_id, student_id, att_date)
);
CREATE INDEX idx_attendance_date ON attendance_record (school_id, att_date);

-- ---- Academic ---------------------------------------------------------------
CREATE TABLE grade (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id   UUID NOT NULL REFERENCES school(id),
    student_id  UUID NOT NULL REFERENCES student(id) ON DELETE CASCADE,
    subject_code VARCHAR(8) NOT NULL,
    sequence    INT NOT NULL,                -- 1..6
    mark        NUMERIC(4,2) NOT NULL CHECK (mark >= 0 AND mark <= 20),
    UNIQUE (school_id, student_id, subject_code, sequence)
);

-- ---- Timetable --------------------------------------------------------------
CREATE TABLE timetable_slot (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id   UUID NOT NULL REFERENCES school(id),
    class_id    UUID NOT NULL REFERENCES school_class(id) ON DELETE CASCADE,
    day_idx     INT NOT NULL,                -- 0..5
    slot_idx    INT NOT NULL,                -- 0..8
    subject_code VARCHAR(8),
    teacher_id  UUID REFERENCES employee(id),
    room        VARCHAR(16),
    UNIQUE (school_id, class_id, day_idx, slot_idx)
);

-- ---- Events & notifications -------------------------------------------------
CREATE TABLE school_event (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id       UUID NOT NULL REFERENCES school(id),
    title           VARCHAR(160) NOT NULL,
    type            VARCHAR(20) NOT NULL,    -- meeting | exam | culture ...
    event_date      DATE NOT NULL,
    description     TEXT,
    audience        VARCHAR(12) NOT NULL DEFAULT 'all', -- all | classes
    target_classes  JSONB,
    notified        BOOLEAN NOT NULL DEFAULT false,
    notified_at     DATE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- ---- Discipline -------------------------------------------------------------
CREATE TABLE discipline_incident (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id   UUID NOT NULL REFERENCES school(id),
    student_id  UUID NOT NULL REFERENCES student(id) ON DELETE CASCADE,
    incident_date DATE NOT NULL,
    type        VARCHAR(20) NOT NULL,
    description TEXT,
    sanction    VARCHAR(120)
);

-- ---- Audit ------------------------------------------------------------------
CREATE TABLE audit_log (
    id          BIGSERIAL PRIMARY KEY,
    school_id   UUID,
    actor       VARCHAR(64),
    action      VARCHAR(80) NOT NULL,
    entity      VARCHAR(64),
    entity_id   VARCHAR(64),
    detail      JSONB,
    at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_student_class ON student (school_id, class_id);
CREATE INDEX idx_payment_date  ON payment (school_id, paid_on);
CREATE INDEX idx_grade_student ON grade (school_id, student_id);

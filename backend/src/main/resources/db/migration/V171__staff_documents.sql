-- ============================================================================
-- V171 — Private personnel documents
--
-- CVs, diplomas and other HR evidence are deliberately separate from the
-- shared school library.  The binary is stored in the private MinIO bucket;
-- this table keeps its metadata and the audit trail used by the HR API.
-- ============================================================================

CREATE TABLE staff_document (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id        UUID NOT NULL REFERENCES school(id),
    employee_id      UUID NOT NULL REFERENCES employee(id),
    document_type    VARCHAR(24) NOT NULL CHECK (document_type IN
                       ('cv','diploma','certificate','identity','contract','other')),
    label            VARCHAR(200) NOT NULL,
    object_key       VARCHAR(300) NOT NULL UNIQUE,
    file_name        VARCHAR(255) NOT NULL,
    content_type     VARCHAR(160) NOT NULL,
    byte_size        BIGINT NOT NULL,
    uploaded_by      UUID REFERENCES app_user(id),
    uploaded_by_name VARCHAR(120),
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_staff_document_employee
    ON staff_document (school_id, employee_id, created_at DESC);

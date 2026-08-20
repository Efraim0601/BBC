-- BAY-32: retry-safe guardian notification outbox created when attendance is finalized.
CREATE TABLE attendance_notification (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id UUID NOT NULL REFERENCES school(id) ON DELETE CASCADE,
    attendance_session_id UUID NOT NULL REFERENCES attendance_session(id) ON DELETE CASCADE,
    attendance_mark_id UUID NOT NULL REFERENCES attendance_mark(id) ON DELETE CASCADE,
    student_id UUID NOT NULL REFERENCES student(id) ON DELETE CASCADE,
    guardian_id UUID REFERENCES guardian(id) ON DELETE SET NULL,
    channel VARCHAR(16) NOT NULL CHECK (channel IN ('EMAIL','SMS','IN_APP')),
    recipient VARCHAR(180),
    subject VARCHAR(180) NOT NULL,
    message TEXT NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING','SENT','FAILED','CANCELLED')),
    attempt_count INT NOT NULL DEFAULT 0,
    last_error VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    sent_at TIMESTAMPTZ
);
CREATE INDEX idx_attendance_notification_delivery
    ON attendance_notification(school_id, status, created_at);
CREATE UNIQUE INDEX uq_attendance_notification_recipient
    ON attendance_notification(school_id, attendance_session_id, student_id,
        coalesce(guardian_id, '00000000-0000-0000-0000-000000000000'::uuid), channel);

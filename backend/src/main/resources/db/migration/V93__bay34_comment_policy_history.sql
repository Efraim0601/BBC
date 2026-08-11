-- BAY-34: controlled subject remarks and append-only history.

ALTER TABLE subject_result_comment
    ADD COLUMN IF NOT EXISTS author_user_id UUID REFERENCES app_user(id);
ALTER TABLE subject_result_comment
    DROP CONSTRAINT IF EXISTS subject_result_comment_appreciation_code_check;
ALTER TABLE subject_result_comment
    ADD CONSTRAINT subject_result_comment_appreciation_code_check CHECK (
        appreciation_code IS NULL OR appreciation_code IN
        ('ENCOURAGEMENT','CONGRATULATIONS','HONOR_ROLL','WORK_WARNING','CONDUCT_WARNING')
    );

CREATE TABLE IF NOT EXISTS subject_result_comment_history (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id UUID NOT NULL REFERENCES school(id) ON DELETE CASCADE,
    comment_id UUID NOT NULL REFERENCES subject_result_comment(id) ON DELETE CASCADE,
    comment TEXT,
    appreciation_code VARCHAR(40),
    workflow_status VARCHAR(16) NOT NULL,
    author_user_id UUID REFERENCES app_user(id),
    source_version BIGINT NOT NULL,
    changed_by UUID REFERENCES app_user(id),
    changed_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_subject_comment_history_lookup
    ON subject_result_comment_history(school_id, comment_id, changed_at, id);

CREATE OR REPLACE FUNCTION append_subject_result_comment_history()
RETURNS trigger AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        INSERT INTO subject_result_comment_history
            (school_id, comment_id, comment, appreciation_code, workflow_status,
             author_user_id, source_version)
        VALUES (OLD.school_id, OLD.id, OLD.comment, OLD.appreciation_code,
                OLD.workflow_status, OLD.author_user_id, OLD.version);
        RETURN OLD;
    END IF;
    INSERT INTO subject_result_comment_history
        (school_id, comment_id, comment, appreciation_code, workflow_status,
         author_user_id, source_version)
    VALUES (NEW.school_id, NEW.id, NEW.comment, NEW.appreciation_code,
            NEW.workflow_status, NEW.author_user_id, NEW.version);
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;
DROP TRIGGER IF EXISTS trg_subject_result_comment_history ON subject_result_comment;
CREATE TRIGGER trg_subject_result_comment_history
    AFTER INSERT OR UPDATE OR DELETE ON subject_result_comment
    FOR EACH ROW EXECUTE FUNCTION append_subject_result_comment_history();

CREATE OR REPLACE FUNCTION reject_subject_result_comment_history_mutation()
RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'Subject result comment history is immutable';
END;
$$ LANGUAGE plpgsql;
DROP TRIGGER IF EXISTS trg_subject_result_comment_history_immutable ON subject_result_comment_history;
CREATE TRIGGER trg_subject_result_comment_history_immutable
    BEFORE UPDATE OR DELETE ON subject_result_comment_history
    FOR EACH ROW EXECUTE FUNCTION reject_subject_result_comment_history_mutation();

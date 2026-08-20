-- BAY-11: make the live progression catalog explicit before the published
-- graph/rule authority is enabled.  Applied migrations remain immutable.

ALTER TABLE school_class
    ADD COLUMN IF NOT EXISTS progression_rank INTEGER;

-- The seed catalog omitted several levels that are required by the normal
-- FR/EN progression.  They are real catalog classes, not implicit skips.
INSERT INTO school_class (school_id, section_id, name, subsystem, level)
SELECT s.id, sec.id, v.name, v.subsystem, 'secondary'
  FROM school s
  JOIN (VALUES
      ('Form 2','EN'), ('Form 3','EN'), ('Form 4','EN'), ('Lower Sixth','EN'),
      ('6ème','FR'), ('5ème','FR'), ('4ème','FR'), ('3ème','FR'), ('2nde','FR')
  ) AS v(name, subsystem) ON true
  JOIN section sec ON sec.school_id=s.id AND sec.subsystem=v.subsystem AND sec.level='secondary'
 WHERE NOT EXISTS (
       SELECT 1 FROM school_class c
        WHERE c.school_id=s.id AND lower(c.name)=lower(v.name)
   );

-- Stable order is used by graph validation to reject accidental backward
-- edges and to require an explicit reason for a genuine skipped level.
UPDATE school_class c SET progression_rank = CASE c.name
    WHEN 'SIL' THEN 1 WHEN 'CP' THEN 2 WHEN 'CE1' THEN 3 WHEN 'CE2' THEN 4
    WHEN 'CM1' THEN 5 WHEN 'CM2' THEN 6 WHEN '6ème' THEN 7 WHEN '5ème' THEN 8
    WHEN '4ème' THEN 9 WHEN '3ème' THEN 10 WHEN '2nde' THEN 11
    WHEN '1ère' THEN 12 WHEN 'Terminale' THEN 13
    WHEN 'Class 1' THEN 1 WHEN 'Class 2' THEN 2 WHEN 'Class 3' THEN 3
    WHEN 'Form 1' THEN 4 WHEN 'Form 2' THEN 5 WHEN 'Form 3' THEN 6
    WHEN 'Form 4' THEN 7 WHEN 'Form 5' THEN 8 WHEN 'Lower Sixth' THEN 9
    WHEN 'Upper Sixth' THEN 10 ELSE c.progression_rank END
 WHERE c.progression_rank IS NULL OR c.name IN (
    'SIL','CP','CE1','CE2','CM1','CM2','6ème','5ème','4ème','3ème','2nde','1ère','Terminale',
    'Class 1','Class 2','Class 3','Form 1','Form 2','Form 3','Form 4','Form 5','Lower Sixth','Upper Sixth'
 );

-- Add the complete default chain to the current live graph.  This is scoped
-- to the latest draft for 2026-2027 -> 2027-2028 so historical graph rows are
-- not rewritten.
WITH graph AS (
    SELECT g.id, g.school_id, g.source_session_id, g.target_session_id
      FROM progression_graph_version g
      JOIN academic_session ss ON ss.id=g.source_session_id
      JOIN academic_session ts ON ts.id=g.target_session_id
     WHERE ss.code='2026-2027' AND ts.code='2027-2028' AND g.status='DRAFT'
       AND g.version_no=(
           SELECT max(g2.version_no) FROM progression_graph_version g2
            WHERE g2.school_id=g.school_id AND g2.source_session_id=g.source_session_id
              AND g2.target_session_id=g.target_session_id AND g2.status='DRAFT'
       )
), pairs(source_name,target_name) AS (
    VALUES
      ('CM2','6ème'),('6ème','5ème'),('5ème','4ème'),('4ème','3ème'),
      ('3ème','2nde'),('2nde','1ère'),
      ('Form 1','Form 2'),('Form 2','Form 3'),('Form 3','Form 4'),
      ('Form 4','Form 5'),('Form 5','Lower Sixth'),('Lower Sixth','Upper Sixth')
)
UPDATE class_progression_path p
   SET target_class_id=target.id, terminal=false, active=true,
       edge_type='DEFAULT', display_order=1, allow_skip=false,
       skip_reason=NULL, version=version+1, updated_at=now()
  FROM graph g
  JOIN school_class source ON source.school_id=g.school_id
  JOIN pairs x ON x.source_name=source.name
  JOIN school_class target ON target.school_id=g.school_id AND target.name=x.target_name
 WHERE p.graph_version_id=g.id AND p.source_class_id=source.id;

WITH graph AS (
    SELECT g.id, g.school_id, g.source_session_id, g.target_session_id
      FROM progression_graph_version g
      JOIN academic_session ss ON ss.id=g.source_session_id
      JOIN academic_session ts ON ts.id=g.target_session_id
     WHERE ss.code='2026-2027' AND ts.code='2027-2028' AND g.status='DRAFT'
       AND g.version_no=(
           SELECT max(g2.version_no) FROM progression_graph_version g2
            WHERE g2.school_id=g.school_id AND g2.source_session_id=g.source_session_id
              AND g2.target_session_id=g.target_session_id AND g2.status='DRAFT'
       )
), pairs(source_name,target_name) AS (
    VALUES
      ('CM2','6ème'),('6ème','5ème'),('5ème','4ème'),('4ème','3ème'),
      ('3ème','2nde'),('2nde','1ère'),
      ('Form 1','Form 2'),('Form 2','Form 3'),('Form 3','Form 4'),
      ('Form 4','Form 5'),('Form 5','Lower Sixth'),('Lower Sixth','Upper Sixth')
)
INSERT INTO class_progression_path
    (school_id,source_session_id,source_class_id,target_session_id,target_class_id,
     terminal,active,graph_version_id,edge_type,display_order,allow_skip)
SELECT g.school_id,g.source_session_id,source.id,g.target_session_id,target.id,
       false,true,g.id,'DEFAULT',1,false
  FROM graph g
  JOIN school_class source ON source.school_id=g.school_id
  JOIN pairs x ON x.source_name=source.name
  JOIN school_class target ON target.school_id=g.school_id AND target.name=x.target_name
 WHERE NOT EXISTS (
       SELECT 1 FROM class_progression_path p
        WHERE p.graph_version_id=g.id AND p.source_class_id=source.id
          AND p.target_class_id=target.id
   );

-- Primary and the remaining terminal rows in the original configuration are
-- retained; only the old unsafe CM2 terminal / Form jumps are corrected above.
CREATE UNIQUE INDEX IF NOT EXISTS uq_published_progression_graph_pair
    ON progression_graph_version(school_id,source_session_id,target_session_id)
    WHERE status='PUBLISHED';

-- A rule set is now a published, immutable authority.  The legacy 10/8 row
-- is attached to this explicitly published set rather than being an invisible
-- runtime default.
ALTER TABLE promotion_rule_set
    ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;
WITH target_sessions AS (
    SELECT s.id session_id, s.school_id
      FROM academic_session s WHERE s.code='2026-2027'
), inserted AS (
    INSERT INTO promotion_rule_set(school_id,academic_session_id,version_no,status,conditions,published_at,published_by)
    SELECT t.school_id,t.session_id,1,'PUBLISHED',
           '{"basis":"PUBLISHED_ANNUAL_OVERALL_AVERAGE","councilEvidence":"APPROVED","requiredSubjectAffects":"COMPLETENESS_ONLY"}'::jsonb,
           now(),u.id
      FROM target_sessions t
      LEFT JOIN LATERAL (
          SELECT id FROM app_user au WHERE au.school_id=t.school_id ORDER BY created_at LIMIT 1
      ) u ON true
     WHERE NOT EXISTS (
           SELECT 1 FROM promotion_rule_set r
            WHERE r.school_id=t.school_id AND r.academic_session_id=t.session_id
              AND r.status='PUBLISHED'
       )
    RETURNING id,school_id,academic_session_id
)
UPDATE promotion_rule r SET rule_set_id=rs.id, version=r.version+1, updated_at=now()
  FROM promotion_rule_set rs
 WHERE rs.school_id=r.school_id AND rs.academic_session_id=r.academic_session_id
   AND rs.status='PUBLISHED' AND r.active AND r.rule_set_id IS NULL;

-- Publish the completed live graph after all required catalog edges exist.
WITH graph AS (
    SELECT g.id,g.school_id
      FROM progression_graph_version g
      JOIN academic_session ss ON ss.id=g.source_session_id
      JOIN academic_session ts ON ts.id=g.target_session_id
     WHERE ss.code='2026-2027' AND ts.code='2027-2028' AND g.status='DRAFT'
       AND g.version_no=(
           SELECT max(g2.version_no) FROM progression_graph_version g2
            WHERE g2.school_id=g.school_id AND g2.source_session_id=g.source_session_id
              AND g2.target_session_id=g.target_session_id AND g2.status='DRAFT'
       )
)
UPDATE progression_graph_version g SET status='PUBLISHED',published_at=now(),
       published_by=(SELECT id FROM app_user u WHERE u.school_id=g.school_id ORDER BY created_at LIMIT 1),
       version=g.version+1
 WHERE g.id IN (SELECT id FROM graph);

-- Promotion reviews retain their exact frozen authority references and may be
-- cancelled without deleting the evidence trail.
ALTER TABLE promotion_batch
    ADD COLUMN IF NOT EXISTS graph_version_id UUID REFERENCES progression_graph_version(id),
    ADD COLUMN IF NOT EXISTS rule_set_id UUID REFERENCES promotion_rule_set(id),
    ADD COLUMN IF NOT EXISTS preview_fingerprint VARCHAR(128),
    ADD COLUMN IF NOT EXISTS cancelled_by UUID REFERENCES app_user(id),
    ADD COLUMN IF NOT EXISTS cancelled_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS cancel_reason VARCHAR(500);

CREATE TABLE IF NOT EXISTS promotion_decision_history (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id UUID NOT NULL REFERENCES school(id) ON DELETE CASCADE,
    decision_id UUID NOT NULL REFERENCES promotion_decision(id) ON DELETE CASCADE,
    from_decision VARCHAR(16),
    to_decision VARCHAR(16) NOT NULL,
    target_class_id UUID REFERENCES school_class(id),
    reason VARCHAR(500),
    actor_user_id UUID REFERENCES app_user(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_promotion_decision_history_decision
    ON promotion_decision_history(school_id,decision_id,created_at);

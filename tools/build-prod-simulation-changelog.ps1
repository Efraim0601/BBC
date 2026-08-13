[CmdletBinding()]
param(
    [string]$SourceContainer = 'bbcomplex-proddb',
    [string]$SourceDatabase = 'bbc_sms',
    [string]$SourceUser = 'bbc',
    [string]$OutputPath = (Join-Path $PSScriptRoot '..\tmp\prod_simulation_changelog.sql'),
    [string]$TargetContainer = 'bbcomplex-prodtest-db',
    [string]$TargetDatabase = 'bbc_sms',
    [string]$TargetUser = 'bbc',
    [switch]$Apply
)

$ErrorActionPreference = 'Stop'
# Windows PowerShell otherwise decodes UTF-8 stdout from Docker using the
# active console code page, turning accented characters into literal `?` bytes.
[Console]::OutputEncoding = [Text.UTF8Encoding]::new($false)
$OutputPath = [IO.Path]::GetFullPath($OutputPath)
$outputDirectory = Split-Path -Parent $OutputPath
New-Item -ItemType Directory -Force -Path $outputDirectory | Out-Null

$sql = [System.Collections.Generic.List[string]]::new()

function Add-SqlLine {
    param([AllowEmptyString()][string]$Line = '')
    $sql.Add($Line) | Out-Null
}

function Add-SqlBlock {
    param([string]$Block)
    foreach ($line in ($Block -split "`r?`n")) { Add-SqlLine $line }
}

function Sql-Literal {
    param([AllowNull()][object]$Value)
    if ($null -eq $Value) { return 'NULL' }
    if ($Value -is [bool]) { return $(if ($Value) { 'TRUE' } else { 'FALSE' }) }
    return "'" + ([string]$Value).Replace("'", "''") + "'"
}

function Invoke-SourcePsql {
    param([Parameter(Mandatory)][string]$Query)
    $result = @(& docker exec $SourceContainer psql -U $SourceUser -d $SourceDatabase -v ON_ERROR_STOP=1 -At -F "`t" -c $Query 2>&1)
    if ($LASTEXITCODE -ne 0) {
        throw "Source query failed: $($result -join [Environment]::NewLine)"
    }
    return @($result | ForEach-Object { [string]$_ })
}

function Add-SourceTableDump {
    param([Parameter(Mandatory)][string]$Table)
    $args = @(
        'exec', $SourceContainer, 'pg_dump', '-U', $SourceUser, '-d', $SourceDatabase,
        '--data-only', '--column-inserts', '--no-owner', '--no-privileges', "--table=public.$Table"
    )
    $result = @(& docker @args 2>&1)
    if ($LASTEXITCODE -ne 0) {
        throw "Source dump failed for $Table`: $($result -join [Environment]::NewLine)"
    }
    foreach ($line in $result) { Add-SqlLine ([string]$line) }
}

Add-SqlBlock @'
-- Generated local simulation changelog.
-- Source: the read-only production replica container.
-- Target: a separate PostgreSQL container, never the source replica.
--
-- This file deliberately does not copy production password hashes or the
-- production staff-portal token. Imported accounts use local test passwords:
--   admin/admin
--   every other imported account/password: password
--
-- It is generated into tmp/ because it contains student and staff PII and is
-- intentionally not committed to the repository.

SET statement_timeout = 0;
SET lock_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SET search_path = public;
BEGIN;
'@

$schoolRows = Invoke-SourcePsql @'
SELECT format(
  'INSERT INTO school (id,code,name,motto,city,country,address,phone,email,website,currency,authority,school_start_time,school_end_time,staff_portal_enabled,staff_portal_slug,staff_portal_token) VALUES (%L,%L,%L,%L,%L,%L,%L,%L,%L,%L,%L,%L,%L,%L,%L,%L,NULL) ON CONFLICT (id) DO NOTHING;',
  id,code,name,motto,city,country,address,phone,email,website,currency,authority,
  school_start_time,school_end_time,staff_portal_enabled,staff_portal_slug)
FROM school ORDER BY id;
'@
Add-SqlLine '-- School profile: token intentionally replaced with NULL.'
foreach ($row in $schoolRows) { Add-SqlLine $row }

$schoolLine = (@(Invoke-SourcePsql 'SELECT id::text || E''\t'' || school_start_time || E''\t'' || school_end_time FROM school ORDER BY id LIMIT 1'))[0]
if ([string]::IsNullOrWhiteSpace($schoolLine)) { throw 'The source database has no school row.' }
$schoolParts = $schoolLine -split "`t", 3
$schoolId = $schoolParts[0]
$schoolStart = $schoolParts[1]
$schoolEnd = $schoolParts[2]

Add-SqlLine ''
Add-SqlLine '-- Reference catalog and legacy business records copied from production.'
foreach ($table in @(
    'department', 'role', 'section', 'subject', 'employee', 'school_class',
    'employee_role', 'teacher_subject', 'teacher_class', 'app_user', 'student',
    'parent_student', 'staff_application', 'class_resource_item',
    'class_resource_publication', 'discipline_incident', 'mail_config',
    'payment_channel', 'permission_grant'
)) {
    if ($table -eq 'school_class' -or $table -eq 'teacher_class' -or $table -eq 'student' -or $table -eq 'parent_student' -or $table -eq 'class_resource_item' -or $table -eq 'class_resource_publication' -or $table -eq 'discipline_incident' -or $table -eq 'app_user') { continue }
    Add-SourceTableDump $table
}
Add-SqlLine 'SET search_path = public;'

Add-SqlLine ''
Add-SqlLine '-- Classes are mapped from legacy grade_order to current progression_rank.'
Add-SqlBlock (Invoke-SourcePsql @'
SELECT format(
  'INSERT INTO school_class (id,school_id,section_id,name,subsystem,level,progression_rank) VALUES (%L,%L,%L,%L,%L,%L,%s) ON CONFLICT (id) DO NOTHING;',
  id,school_id,section_id,name,subsystem,level,COALESCE(grade_order,0))
FROM school_class ORDER BY name,id;
'@)
Add-SourceTableDump 'teacher_class'
Add-SourceTableDump 'student'
Add-SourceTableDump 'class_resource_item'
Add-SourceTableDump 'class_resource_publication'
Add-SourceTableDump 'discipline_incident'
Add-SqlLine 'SET search_path = public;'

Add-SqlLine ''
Add-SqlLine '-- Restore the source UTF-8 text explicitly. Native Docker output on Windows can otherwise replace accents with question marks.'
Add-SqlBlock (Invoke-SourcePsql "SELECT format('UPDATE section SET label=%L WHERE id=%L;', label, id) FROM section ORDER BY id;")
Add-SqlBlock (Invoke-SourcePsql "SELECT format('UPDATE department SET name=%L WHERE id=%L;', name, id) FROM department ORDER BY id;")
Add-SqlBlock (Invoke-SourcePsql "SELECT format('UPDATE school_class SET name=%L WHERE id=%L;', name, id) FROM school_class ORDER BY id;")
Add-SqlBlock (Invoke-SourcePsql "SELECT format('UPDATE subject SET label=%L::jsonb WHERE id=%L;', label::text, id) FROM subject ORDER BY id;")
Add-SqlBlock (Invoke-SourcePsql "SELECT format('UPDATE student SET class_name=%L, birthplace=%L, father_name=%L, mother_name=%L, guardian_name=%L, guardian_relation=%L WHERE id=%L;', class_name, birthplace, father_name, mother_name, guardian_name, guardian_relation, id) FROM student ORDER BY id;")

Add-SqlLine ''
Add-SqlLine '-- User identities are retained for test navigation, but credentials are local simulation credentials.'
Add-SqlBlock (Invoke-SourcePsql @'
SELECT format(
  'INSERT INTO app_user (id,school_id,username,password_hash,display_name,initials,role_code,employee_id,locale,active,email,normalized_email,must_change_password) VALUES (%L,%L,%L,%L,%L,%L,%L,%L,%L,%L,NULL,NULL,FALSE) ON CONFLICT (id) DO NOTHING;',
  id,school_id,username,
  CASE WHEN username='admin'
       THEN '$2b$10$3HFw9TnZ7hYbkE6RJNnUC.Gj9BvnubBiuo0UvvY5SD28AcZJxzQ1m'
       ELSE '$2a$10$v0JND6DMVb87FMt3L.uZxem0ymfNyn5J/78P0Ra39qaVZGVfspwUe' END,
  display_name,initials,role_code,employee_id,locale,active)
FROM app_user ORDER BY username;
'@)
Add-SourceTableDump 'parent_student'
Add-SqlLine 'SET search_path = public;'

Add-SqlLine ''
Add-SqlLine '-- Preserve the legacy academic-year row as well as the current session projection.'
Add-SqlBlock (Invoke-SourcePsql @'
SELECT format(
  'INSERT INTO academic_year (id,school_id,label,start_year,is_current) VALUES (%L,%L,%L,%s,%s) ON CONFLICT (id) DO NOTHING;',
  id,school_id,label,start_year,CASE WHEN is_current THEN 'TRUE' ELSE 'FALSE' END)
FROM academic_year ORDER BY start_year,id;
'@)

$academicYearLine = (@(Invoke-SourcePsql "SELECT id::text || E'\t' || label || E'\t' || start_year::text || E'\t' || is_current::text FROM academic_year ORDER BY start_year,id LIMIT 1"))[0]
if ([string]::IsNullOrWhiteSpace($academicYearLine)) { throw 'The source database has no academic_year row.' }
$academicYearParts = $academicYearLine -split "`t", 4
$sessionId = $academicYearParts[0]
$sessionLabel = $academicYearParts[1]
$startYear = [int]$academicYearParts[2]
$sessionStart = "{0:D4}-09-01" -f $startYear
# The source session ended on July 31. The simulation keeps it open through
# August 31 so the current test date (2026-08-10) has a resolvable session.
$sessionEnd = "{0:D4}-08-31" -f ($startYear + 1)
$targetSessionId = ([guid]::NewGuid()).ToString()
$targetSessionLabel = "{0:D4}-{1:D4}" -f ($startYear + 1), ($startYear + 2)
$targetSessionStart = "{0:D4}-09-01" -f ($startYear + 1)
$targetSessionEnd = "{0:D4}-07-31" -f ($startYear + 2)
$adminUserId = ((@(Invoke-SourcePsql "SELECT id::text FROM app_user WHERE username='admin' LIMIT 1"))[0]).Trim()
if ([string]::IsNullOrWhiteSpace($adminUserId)) { throw 'The source database has no admin user.' }

Add-SqlLine ''
Add-SqlLine '-- Current session projection plus a future target session for promotion testing.'
Add-SqlLine ("INSERT INTO academic_session (id,school_id,code,label,start_date,end_date,status,is_current,grade_entry_opens_at,grade_entry_closes_at,bulletin_publish_opens_at,bulletin_publish_closes_at,timezone) VALUES ({0},{1},{2},{3},{4},{5},'OPEN',TRUE,{6},{7},{8},{9},'Africa/Douala') ON CONFLICT (id) DO NOTHING;" -f `
    (Sql-Literal $sessionId),(Sql-Literal $schoolId),(Sql-Literal $sessionLabel),(Sql-Literal $sessionLabel),(Sql-Literal $sessionStart),(Sql-Literal $sessionEnd),
    (Sql-Literal ($sessionStart + ' 07:00:00+01')),(Sql-Literal ($sessionEnd + ' 18:00:00+01')),(Sql-Literal ($sessionStart + ' 07:00:00+01')),(Sql-Literal ($sessionEnd + ' 18:00:00+01')))
Add-SqlLine ("INSERT INTO academic_session (id,school_id,code,label,start_date,end_date,status,is_current,timezone) VALUES ({0},{1},{2},{3},{4},{5},'DRAFT',FALSE,'Africa/Douala') ON CONFLICT (id) DO NOTHING;" -f `
    (Sql-Literal $targetSessionId),(Sql-Literal $schoolId),(Sql-Literal $targetSessionLabel),(Sql-Literal $targetSessionLabel),(Sql-Literal $targetSessionStart),(Sql-Literal $targetSessionEnd))

$termOneId = ([guid]::NewGuid()).ToString()
$termTwoId = ([guid]::NewGuid()).ToString()
$termThreeId = ([guid]::NewGuid()).ToString()
$terms = @(
    @{ Id=$termOneId; Code='T1'; Label='Trimestre 1'; No=1; Start='2025-09-01'; End='2025-12-19' },
    @{ Id=$termTwoId; Code='T2'; Label='Trimestre 2'; No=2; Start='2026-01-05'; End='2026-03-27' },
    @{ Id=$termThreeId; Code='T3'; Label='Trimestre 3'; No=3; Start='2026-04-06'; End='2026-07-31' }
)
Add-SqlLine ''
Add-SqlLine '-- Three-trimester structure used by the six-sequence reporting workflow.'
foreach ($term in $terms) {
    Add-SqlLine ("INSERT INTO academic_term (id,school_id,academic_session_id,code,label,sequence_no,start_date,end_date,timezone) VALUES ({0},{1},{2},{3},{4},{5},{6},{7},'Africa/Douala') ON CONFLICT (id) DO NOTHING;" -f `
        (Sql-Literal $term.Id),(Sql-Literal $schoolId),(Sql-Literal $sessionId),(Sql-Literal $term.Code),(Sql-Literal $term.Label),$term.No,(Sql-Literal $term.Start),(Sql-Literal $term.End))
}

function Format-Timestamp {
    param([datetime]$Date, [int]$Hour, [int]$Minute)
    return ($Date.ToString('yyyy-MM-dd') + (' {0:D2}:{1:D2}:00+01' -f $Hour,$Minute))
}

$periodSpecs = @(
    @{ Code='S1'; Label='Sequence 1'; Type='SEQUENCE'; Order=1; Start='2025-09-01'; End='2025-10-24'; Term=$termOneId },
    @{ Code='S2'; Label='Sequence 2'; Type='SEQUENCE'; Order=2; Start='2025-10-27'; End='2025-12-19'; Term=$termOneId },
    @{ Code='T1_RESULT'; Label='Resultat Trimestre 1'; Type='TERM_RESULT'; Order=3; Start='2025-09-01'; End='2025-12-19'; Term=$termOneId },
    @{ Code='S3'; Label='Sequence 3'; Type='SEQUENCE'; Order=4; Start='2026-01-05'; End='2026-02-13'; Term=$termTwoId },
    @{ Code='S4'; Label='Sequence 4'; Type='SEQUENCE'; Order=5; Start='2026-02-16'; End='2026-03-27'; Term=$termTwoId },
    @{ Code='T2_RESULT'; Label='Resultat Trimestre 2'; Type='TERM_RESULT'; Order=6; Start='2026-01-05'; End='2026-03-27'; Term=$termTwoId },
    @{ Code='S5'; Label='Sequence 5'; Type='SEQUENCE'; Order=7; Start='2026-04-06'; End='2026-05-29'; Term=$termThreeId },
    @{ Code='S6'; Label='Sequence 6'; Type='SEQUENCE'; Order=8; Start='2026-06-01'; End='2026-07-31'; Term=$termThreeId },
    @{ Code='T3_RESULT'; Label='Resultat Trimestre 3'; Type='TERM_RESULT'; Order=9; Start='2026-04-06'; End='2026-07-31'; Term=$termThreeId },
    @{ Code='ANNUAL'; Label='Resultat annuel'; Type='ANNUAL_RESULT'; Order=10; Start='2025-09-01'; End='2026-07-31'; Term=$null }
)
$periodIds = @{}
foreach ($period in $periodSpecs) { $periodIds[$period.Code] = ([guid]::NewGuid()).ToString() }

Add-SqlLine ''
Add-SqlLine '-- Reporting periods and explicit grade/review/validation/publication/correction windows.'
foreach ($period in $periodSpecs) {
    $start = [datetime]::ParseExact($period.Start,'yyyy-MM-dd',[Globalization.CultureInfo]::InvariantCulture)
    $end = [datetime]::ParseExact($period.End,'yyyy-MM-dd',[Globalization.CultureInfo]::InvariantCulture)
    $entryOpen = Format-Timestamp $start 7 0
    $entryClose = Format-Timestamp $end 18 0
    $reviewOpen = Format-Timestamp $end.AddDays(1) 7 0
    $reviewClose = Format-Timestamp $end.AddDays(3) 18 0
    $validationOpen = Format-Timestamp $end.AddDays(4) 7 0
    $validationClose = Format-Timestamp $end.AddDays(6) 18 0
    $publishOpen = Format-Timestamp $end.AddDays(7) 7 0
    $publishClose = Format-Timestamp $end.AddDays(14) 18 0
    $correctionOpen = Format-Timestamp $end.AddDays(15) 7 0
    $correctionClose = Format-Timestamp $end.AddDays(22) 18 0
    $termSql = Sql-Literal $period.Term
    Add-SqlLine ("INSERT INTO academic_reporting_period (id,school_id,academic_session_id,academic_term_id,code,label,period_type,display_order,start_date,end_date,grade_entry_opens_at,grade_entry_closes_at,review_opens_at,review_closes_at,validation_opens_at,validation_closes_at,bulletin_publish_opens_at,bulletin_publish_closes_at,correction_opens_at,correction_closes_at,calculation_policy,status,timezone) VALUES ({0},{1},{2},{3},{4},{5},{6},{7},{8},{9},{10},{11},{12},{13},{14},{15},{16},{17},{18},{19},'DEFAULT','OPEN','Africa/Douala') ON CONFLICT (id) DO NOTHING;" -f `
        (Sql-Literal $periodIds[$period.Code]),(Sql-Literal $schoolId),(Sql-Literal $sessionId),$termSql,(Sql-Literal $period.Code),(Sql-Literal $period.Label),(Sql-Literal $period.Type),$period.Order,
        (Sql-Literal $period.Start),(Sql-Literal $period.End),(Sql-Literal $entryOpen),(Sql-Literal $entryClose),(Sql-Literal $reviewOpen),(Sql-Literal $reviewClose),(Sql-Literal $validationOpen),(Sql-Literal $validationClose),(Sql-Literal $publishOpen),(Sql-Literal $publishClose),(Sql-Literal $correctionOpen),(Sql-Literal $correctionClose))
}

Add-SqlLine ''
Add-SqlLine '-- Result graph: each trimester result uses its two sequences; annual result uses the three trimester results.'
foreach ($edge in @(
    @('T1_RESULT','S1',0.5,1), @('T1_RESULT','S2',0.5,2),
    @('T2_RESULT','S3',0.5,1), @('T2_RESULT','S4',0.5,2),
    @('T3_RESULT','S5',0.5,1), @('T3_RESULT','S6',0.5,2),
    @('ANNUAL','T1_RESULT',0.3333333333,1), @('ANNUAL','T2_RESULT',0.3333333333,2), @('ANNUAL','T3_RESULT',0.3333333333,3)
)) {
    Add-SqlLine ("INSERT INTO academic_reporting_period_dependency (id,school_id,academic_session_id,parent_period_id,child_period_id,weight,optional,display_order) VALUES ({0},{1},{2},{3},{4},{5},FALSE,{6}) ON CONFLICT DO NOTHING;" -f `
        (Sql-Literal (([guid]::NewGuid()).ToString())),(Sql-Literal $schoolId),(Sql-Literal $sessionId),(Sql-Literal $periodIds[$edge[0]]),(Sql-Literal $periodIds[$edge[1]]),$edge[2],$edge[3])
}

Add-SqlLine ''
Add-SqlLine '-- Session-scoped attendance rules: nursery/primary daily, secondary subject-period.'
foreach ($policy in @(@('maternelle','DAILY'),@('primary','DAILY'),@('secondary','PERIOD'))) {
    Add-SqlLine ("INSERT INTO attendance_policy (id,school_id,level,model,late_after_minutes,chronic_absence_percent,require_absence_reason) VALUES ({0},{1},{2},{3},10,20.00,FALSE) ON CONFLICT (school_id,level) DO NOTHING;" -f `
        (Sql-Literal (([guid]::NewGuid()).ToString())),(Sql-Literal $schoolId),(Sql-Literal $policy[0]),(Sql-Literal $policy[1]))
}
Add-SqlLine ('INSERT INTO school_calendar_day (id,school_id,academic_session_id,day_of_week,teaching_day,start_time,end_time) SELECT gen_random_uuid(),s.id,a.id,d,d BETWEEN 1 AND 5,s.school_start_time::time,s.school_end_time::time FROM school s JOIN academic_session a ON a.id=' + (Sql-Literal $sessionId) + ' CROSS JOIN generate_series(1,7) d ON CONFLICT DO NOTHING;')

Add-SqlLine ''
Add-SqlLine '-- Active production students receive a current-session enrollment snapshot.'
Add-SqlLine ("INSERT INTO student_enrollment (id,school_id,student_id,academic_session_id,school_class_id,class_name_snapshot,level_snapshot,subsystem_snapshot,status,enrolled_on,source) SELECT gen_random_uuid(),st.school_id,st.id,{0},st.class_id,st.class_name,st.level,st.subsystem,'ACTIVE',GREATEST({1},st.created_at::date),'MIGRATION' FROM student st WHERE st.school_id={2} AND st.active ON CONFLICT (school_id,student_id,academic_session_id) WHERE status='ACTIVE' DO NOTHING;" -f (Sql-Literal $sessionId),(Sql-Literal $sessionStart),(Sql-Literal $schoolId))

Add-SqlLine ''
Add-SqlLine '-- Canonical class-teacher assignments are derived from the legacy teacher_class relation.'
Add-SqlLine ("WITH ranked AS (SELECT tc.employee_id,tc.class_id,row_number() OVER (PARTITION BY tc.class_id ORDER BY tc.employee_id) AS rn FROM teacher_class tc JOIN school_class c ON c.id=tc.class_id WHERE c.school_id={0}) INSERT INTO class_teacher_assignment (id,school_id,academic_session_id,class_id,employee_id,role,effective_from,status,source) SELECT gen_random_uuid(),{0},{1},class_id,employee_id,CASE WHEN rn=1 THEN 'HOMEROOM' ELSE 'ASSISTANT' END,{2},'ACTIVE','PRODUCTION_TEACHER_CLASS' FROM ranked ON CONFLICT DO NOTHING;" -f (Sql-Literal $schoolId),(Sql-Literal $sessionId),(Sql-Literal $sessionStart))

Add-SqlLine ''
Add-SqlLine '-- Timetable periods and class models needed by the current timetable UI.'
foreach ($p in @(@(0,'P1','07:30','08:25'),@(1,'P2','08:30','09:25'),@(2,'P3','09:30','10:25'),@(3,'P4','10:30','11:25'),@(4,'P5','11:30','12:25'),@(5,'P6','12:30','13:25'),@(6,'P7','13:30','14:25'),@(7,'P8','14:30','15:25'),@(8,'P9','15:30','16:25'))) {
    Add-SqlLine ("INSERT INTO timetable_period (id,school_id,slot_idx,label,start_time,end_time) VALUES ({0},{1},{2},{3},{4},{5}) ON CONFLICT (school_id,slot_idx) DO NOTHING;" -f (Sql-Literal (([guid]::NewGuid()).ToString())),(Sql-Literal $schoolId),$p[0],(Sql-Literal $p[1]),(Sql-Literal $p[2]),(Sql-Literal $p[3]))
}
Add-SqlLine ("INSERT INTO timetable_class_config (id,school_id,academic_session_id,class_id,model,homeroom_teacher_id,status,published_at,published_by) SELECT gen_random_uuid(),c.school_id,{0},c.id,CASE WHEN lower(c.level)='secondary' THEN 'DEPARTMENTAL' ELSE 'HOMEROOM' END,ht.employee_id,CASE WHEN lower(c.level)='secondary' OR ht.employee_id IS NOT NULL THEN 'PUBLISHED' ELSE 'DRAFT' END,CASE WHEN lower(c.level)='secondary' OR ht.employee_id IS NOT NULL THEN now() ELSE NULL END,CASE WHEN lower(c.level)='secondary' OR ht.employee_id IS NOT NULL THEN {1}::uuid ELSE NULL END FROM school_class c LEFT JOIN (SELECT class_id,employee_id FROM class_teacher_assignment WHERE school_id={2} AND academic_session_id={0} AND role='HOMEROOM' AND status='ACTIVE') ht ON ht.class_id=c.id WHERE c.school_id={2} ON CONFLICT (school_id,academic_session_id,class_id) DO NOTHING;" -f (Sql-Literal $sessionId),(Sql-Literal $adminUserId),(Sql-Literal $schoolId))

$timetableVersionId = ([guid]::NewGuid()).ToString()
Add-SqlLine ("INSERT INTO timetable_version (id,school_id,academic_session_id,version_no,status,effective_from,effective_to,timezone,published_at,published_by) VALUES ({0},{1},{2},1,'PUBLISHED',{3},{4},'Africa/Douala',now(),{5}) ON CONFLICT (school_id,academic_session_id,version_no) DO NOTHING;" -f `
    (Sql-Literal $timetableVersionId),(Sql-Literal $schoolId),(Sql-Literal $sessionId),(Sql-Literal $sessionStart),(Sql-Literal $sessionEnd),(Sql-Literal $adminUserId))
Add-SqlLine ''
Add-SqlLine '-- The two source timetable slots are retained and attached to the published simulation version.'
Add-SqlBlock (Invoke-SourcePsql ("SELECT format('INSERT INTO timetable_slot (id,school_id,class_id,academic_session_id,day_idx,slot_idx,subject_code,teacher_id,room,timetable_version_id,published_teacher_id) VALUES (%L,%L,%L,%L,%s,%s,%L,%L,%L,%L,%L) ON CONFLICT (id) DO NOTHING;',id,school_id,class_id,{0},day_idx,slot_idx,subject_code,teacher_id,room,{1},teacher_id) FROM timetable_slot ORDER BY id;" -f (Sql-Literal $sessionId),(Sql-Literal $timetableVersionId)))
Add-SqlLine ('INSERT INTO timetable_room (id,school_id,code,label) SELECT gen_random_uuid(),school_id,btrim(room),btrim(room) FROM timetable_slot WHERE school_id=' + (Sql-Literal $schoolId) + ' AND room IS NOT NULL AND btrim(room)<>'''' ON CONFLICT (school_id,code) DO NOTHING;')
Add-SqlLine ("UPDATE timetable_slot t SET assignment_id=a.id,assignment_version=a.version,published_teacher_id=t.teacher_id,published_assignment_id=a.id,published_assignment_version=a.version FROM class_teacher_assignment a WHERE a.school_id={0} AND a.academic_session_id={1} AND a.class_id=t.class_id AND a.employee_id=t.teacher_id AND a.role='HOMEROOM' AND a.status='ACTIVE' AND t.school_id={0} AND t.academic_session_id={1};" -f (Sql-Literal $schoolId),(Sql-Literal $sessionId))

Add-SqlLine ''
Add-SqlLine '-- The source had one legacy promotion rule but no class progression map. Its pass mark is preserved; the review threshold is the current UI default (pass mark minus two points).' 
$ruleSetId = ([guid]::NewGuid()).ToString()
$ruleJson = (Sql-Literal '{"source":"PRODUCTION_PROMOTION_RULE","councilMargin":1.00,"maxRepeats":2}') + '::jsonb'
Add-SqlLine ('INSERT INTO promotion_rule_set (id,school_id,academic_session_id,version_no,status,conditions,published_at,published_by) VALUES (' + (Sql-Literal $ruleSetId) + ',' + (Sql-Literal $schoolId) + ',' + (Sql-Literal $sessionId) + ",1,'PUBLISHED'," + $ruleJson + ',now(),' + (Sql-Literal $adminUserId) + ') ON CONFLICT (school_id,academic_session_id,version_no) DO NOTHING;')
Add-SqlBlock (Invoke-SourcePsql ("SELECT format('INSERT INTO promotion_rule (id,school_id,academic_session_id,subsystem,level,promote_min,review_min,require_final_average,active,rule_set_id) VALUES (%L,%L,%L,NULL,NULL,%s,%s,TRUE,TRUE,%L) ON CONFLICT DO NOTHING;',id,school_id,{0},pass_mark,GREATEST(0,pass_mark-(council_margin*2)),{1}) FROM promotion_rule ORDER BY id;" -f (Sql-Literal $sessionId),(Sql-Literal $ruleSetId)))

Add-SqlLine ''
Add-SqlLine '-- Map the one known legacy parent/student relationship to the current guardian model.'
Add-SqlLine ("INSERT INTO guardian (id,school_id,app_user_id,display_name,status) SELECT u.id,u.school_id,u.id,u.display_name,CASE WHEN u.active THEN 'ACTIVE' ELSE 'INACTIVE' END FROM app_user u WHERE u.role_code='parent' AND u.school_id={0} ON CONFLICT (id) DO NOTHING;" -f (Sql-Literal $schoolId))
Add-SqlLine ("INSERT INTO student_guardian (id,school_id,student_id,guardian_id,relationship_type,legal_guardian,pickup_authorized,finance_responsible,receives_finance,portal_access) SELECT gen_random_uuid(),s.school_id,ps.student_id,g.id,'PARENT',TRUE,TRUE,TRUE,TRUE,TRUE FROM parent_student ps JOIN student s ON s.id=ps.student_id JOIN guardian g ON g.app_user_id=ps.parent_user_id AND g.school_id=s.school_id WHERE s.school_id={0} ON CONFLICT (school_id,student_id,guardian_id) DO NOTHING;" -f (Sql-Literal $schoolId))

Add-SqlLine ''
Add-SqlLine '-- Fine-grained actions are rebuilt for the current action catalog from the imported module grants.'
Add-SqlBlock ((@'
INSERT INTO permission_action_grant (school_id,role_code,action_code,allowed)
SELECT pg.school_id, r.code, a.action_code,
       CASE WHEN a.required_level='read' THEN pg.level IN ('read','write') ELSE pg.level='write' END
  FROM role r
  JOIN permission_grant pg ON pg.role_code=r.code AND pg.school_id='SOURCE_SCHOOL_ID'
  JOIN (VALUES
    ('SESSION_VIEW','settings','read'),('SESSION_MANAGE','settings','write'),('ACADEMIC_WINDOW_OVERRIDE','settings','write'),
    ('ENROLLMENT_VIEW','students','read'),('ENROLLMENT_MANAGE','students','write'),('GUARDIAN_LINK','students','write'),
    ('CALENDAR_VIEW','settings','read'),('CALENDAR_MANAGE','settings','write'),('AUDIT_VIEW','settings','read'),
    ('DOCUMENT_VIEW','documents','read'),('DOCUMENT_DESIGN_PUBLISH','settings','write'),('DOCUMENT_GENERATE','documents','write'),('DOCUMENT_REVOKE','documents','write'),
    ('ATTENDANCE_ROSTER_VIEW','presence','read'),('ATTENDANCE_MARK','presence','write'),('ATTENDANCE_FINALIZE','presence','write'),('ATTENDANCE_REOPEN','presence','write'),('ATTENDANCE_ANALYTICS_VIEW','presence','read'),('ATTENDANCE_POLICY_MANAGE','presence','write'),('ATTENDANCE_RECONCILE','presence','write'),
    ('GRADE_SUBMIT','academic','write'),('BULLETIN_VALIDATE','academic','write'),('BULLETIN_PUBLISH','academic','write'),
    ('PROMOTION_RECOMMEND','academic','write'),('PROMOTION_OVERRIDE','academic','write'),('PROMOTION_COMMIT','academic','write'),
    ('PROGRESSION_VIEW','journey','read'),('PROGRESSION_CONFIGURE','journey','write'),('PROMOTION_REVIEW','journey','write'),('PROMOTION_CONFIGURE','journey','write'),('PROMOTION_CORRECT','journey','write'),
    ('FEE_CONFIGURE','finance','write'),('PAYMENT_COLLECT','finance','write'),('PAYMENT_REVERSE','finance','write'),('LEDGER_POST','finance','write'),('LEDGER_CLOSE','finance','write'),
    ('PAYROLL_APPROVE','hr','write'),('PAYROLL_PAY','hr','write'),('TIMETABLE_DRAFT','timetable','write'),('TIMETABLE_PUBLISH','timetable','write'),('TIMETABLE_OVERRIDE','timetable','write'),('HEALTH_CONFIDENTIAL_VIEW','health','write')
  ) AS a(action_code,module,required_level) ON pg.module=a.module
ON CONFLICT (school_id,role_code,action_code) DO UPDATE SET allowed=EXCLUDED.allowed;
UPDATE permission_grant SET id=id WHERE school_id='f6762abb-dd28-49bf-b26e-f696505c8942';
SELECT setval(pg_get_serial_sequence('permission_grant','id'),COALESCE((SELECT max(id) FROM permission_grant),0),true);
'@) -replace 'SOURCE_SCHOOL_ID', $schoolId)

Add-SqlLine ''
Add-SqlLine '-- Minimal branding and generic document templates make the document screens usable without copying production assets.'
Add-SqlLine ("INSERT INTO document_branding_version (id,school_id,locale,version,status,school_name,school_name_en,country,phone,principal_title,content_hash,created_by,published_by,published_at) SELECT {0},id,'fr',1,'PUBLISHED',name,name,country,phone,'Principal',repeat('0',64),{1}::uuid,{1}::uuid,now() FROM school WHERE id={2} ON CONFLICT (school_id,locale,version) DO NOTHING;" -f (Sql-Literal (([guid]::NewGuid()).ToString())),(Sql-Literal $adminUserId),(Sql-Literal $schoolId))
Add-SqlLine ("INSERT INTO document_template (id,school_id,type,locale,name,template_version,body_template,active,template_family,product,status,reference_family,published_at,published_by,config_json) VALUES ({0},{1},'ENROLLMENT_CERTIFICATE','fr','Certificat de scolarite',1,'Certifie que {{{{studentName}}}} ({{{{matricule}}}}) est inscrit(e) en {{{{className}}}} pour {{{{sessionLabel}}}}.',TRUE,'GENERIC','GENERIC','PUBLISHED','GENERIC',now(),{2},'{{}}'::jsonb) ON CONFLICT DO NOTHING;" -f (Sql-Literal (([guid]::NewGuid()).ToString())),(Sql-Literal $schoolId),(Sql-Literal $adminUserId))
Add-SqlLine ("INSERT INTO document_template (id,school_id,type,locale,name,template_version,body_template,active,template_family,product,status,reference_family,published_at,published_by,config_json) VALUES ({0},{1},'ENROLLMENT_CERTIFICATE','en','Enrollment certificate',1,'This certifies that {{{{studentName}}}} ({{{{matricule}}}}) is enrolled in {{{{className}}}} for {{{{sessionLabel}}}}.',TRUE,'GENERIC','GENERIC','PUBLISHED','GENERIC',now(),{2},'{{}}'::jsonb) ON CONFLICT DO NOTHING;" -f (Sql-Literal (([guid]::NewGuid()).ToString())),(Sql-Literal $schoolId),(Sql-Literal $adminUserId))

Add-SqlLine ''
Add-SqlLine '-- Keep this data load atomic.'
Add-SqlLine 'COMMIT;'

[IO.File]::WriteAllLines($OutputPath, $sql, [Text.UTF8Encoding]::new($false))
Write-Host "Wrote $OutputPath ($($sql.Count) lines)."

if ($Apply) {
    $payload = Get-Content -LiteralPath $OutputPath -Raw
    $result = $payload | & docker exec -i $TargetContainer psql -U $TargetUser -d $TargetDatabase -v ON_ERROR_STOP=1 2>&1
    if ($LASTEXITCODE -ne 0) { throw "Target apply failed: $($result -join [Environment]::NewLine)" }
    $result | ForEach-Object { Write-Host $_ }
    Write-Host "Applied to $TargetContainer/$TargetDatabase."
}

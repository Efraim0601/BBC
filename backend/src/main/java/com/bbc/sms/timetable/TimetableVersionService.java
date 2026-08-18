package com.bbc.sms.timetable;

import com.bbc.sms.foundation.audit.AuditService;
import com.bbc.sms.platform.common.ApiException;
import com.bbc.sms.platform.security.AppUserPrincipal;
import com.bbc.sms.platform.security.AuthorizationPolicyService;
import com.bbc.sms.platform.security.PolicyResourceContext;
import com.bbc.sms.platform.tenant.ParcoursContext;
import com.bbc.sms.platform.tenant.TenantContext;
import com.bbc.sms.timetable.dto.TimetableVersionDtos.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.font.PDType1Font;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.*;

/** Versioned timetable headers, resource records, substitutions, and deterministic exports. */
@Service
public class TimetableVersionService {
    private final JdbcTemplate jdbc;
    private final AuditService audit;
    private final TeachingAssignmentResolver assignments;
    private final AuthorizationPolicyService policy;

    public TimetableVersionService(JdbcTemplate jdbc, AuditService audit,
                                   TeachingAssignmentResolver assignments,
                                   AuthorizationPolicyService policy) {
        this.jdbc = jdbc;
        this.audit = audit;
        this.assignments = assignments;
        this.policy = policy;
    }

    @Transactional(readOnly = true)
    public List<TimetableVersionView> list(UUID sessionId) {
        policy.require("TIMETABLE_MASTER_VIEW", schoolContext());
        UUID school = TenantContext.get();
        requireSession(sessionId);
        return jdbc.query("""
            SELECT v.id,v.academic_session_id,v.version_no,v.status,v.effective_from,v.effective_to,
                   v.timezone,v.copied_from_version_id,v.version,
                   count(s.id),count(DISTINCT s.class_id)
              FROM timetable_version v LEFT JOIN timetable_slot s ON s.timetable_version_id=v.id
             WHERE v.school_id=? AND v.academic_session_id=?
             GROUP BY v.id ORDER BY v.version_no DESC
            """, (rs, n) -> new TimetableVersionView(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class),
                    rs.getInt(3), rs.getString(4), rs.getObject(5, LocalDate.class), rs.getObject(6, LocalDate.class),
                    rs.getString(7), rs.getObject(8, UUID.class), rs.getInt(10), rs.getInt(11), rs.getLong(9)),
                school, sessionId);
    }

    @Transactional(readOnly = true)
    public TimetableVersionView view(UUID id) {
        policy.require("TIMETABLE_MASTER_VIEW", schoolContext());
        return versionView(id);
    }

    private TimetableVersionView versionView(UUID id) {
        Map<String, Object> row = jdbc.queryForMap("""
            SELECT v.id,v.academic_session_id,v.version_no,v.status,v.effective_from,v.effective_to,
                   v.timezone,v.copied_from_version_id,v.version,count(s.id) AS slot_count,count(DISTINCT s.class_id) AS class_count
              FROM timetable_version v LEFT JOIN timetable_slot s ON s.timetable_version_id=v.id
             WHERE v.id=? AND v.school_id=? GROUP BY v.id
            """, id, TenantContext.get());
        return mapVersion(row);
    }

    @Transactional
    public TimetableVersionView create(TimetableVersionUpsert in) {
        policy.require("TIMETABLE_DRAFT", schoolContext());
        UUID school = TenantContext.get();
        requireSession(in.academicSessionId());
        validateDates(in.effectiveFrom(), in.effectiveTo());
        jdbc.queryForObject("SELECT pg_advisory_xact_lock(hashtext(?))", Object.class, school + ":timetable:" + in.academicSessionId());
        Integer nextValue = jdbc.queryForObject("SELECT COALESCE(max(version_no),0)+1 FROM timetable_version WHERE school_id=? AND academic_session_id=?", Integer.class, school, in.academicSessionId());
        int next = nextValue == null ? 1 : nextValue;
        UUID id = UUID.randomUUID();
        UUID source = in.copyFromVersionId();
        if (source != null) {
            Integer owned = jdbc.queryForObject("SELECT count(*) FROM timetable_version WHERE id=? AND school_id=?", Integer.class, source, school);
            if (owned == null || owned == 0) throw ApiException.notFound("Version source du planning");
        }
        jdbc.update("""
            INSERT INTO timetable_version(id,school_id,academic_session_id,version_no,status,effective_from,effective_to,timezone,copied_from_version_id)
            VALUES (?,?,?,?,'DRAFT',?,?,?,?)
            """, id, school, in.academicSessionId(), next, in.effectiveFrom(), in.effectiveTo(),
            normalizeTimezone(in.timezone()), source);
        if (source != null) {
            jdbc.update("""
                INSERT INTO timetable_slot
                    (id,school_id,class_id,academic_session_id,day_idx,slot_idx,subject_code,teacher_id,room,
                     assignment_id,assignment_version,timetable_version_id)
                SELECT gen_random_uuid(),school_id,class_id,?,day_idx,slot_idx,subject_code,
                       teacher_id,room,assignment_id,assignment_version,?
                  FROM timetable_slot WHERE school_id=? AND timetable_version_id=?
                """, in.academicSessionId(), id, school, source);
        }
        // Return the mutation result without requiring the unrelated master
        // timetable-read action a second time.  A manager may be allowed to
        // create/publish a version while the master grid remains separately
        // governed.
        TimetableVersionView result = versionView(id);
        audit.record("TIMETABLE_VERSION_CREATED", "TimetableVersion", id.toString(), null, result, in.reason());
        return result;
    }

    @Transactional
    public TimetableVersionView publish(UUID id, TimetableVersionActionRequest in) {
        policy.require("TIMETABLE_PUBLISH", schoolContext());
        UUID school = TenantContext.get();
        UUID sessionId = jdbc.query("SELECT academic_session_id FROM timetable_version WHERE id=? AND school_id=?",
                rs -> rs.next() ? rs.getObject(1, UUID.class) : null, id, school);
        if (sessionId == null) throw ApiException.notFound("Version du planning");
        jdbc.queryForObject("SELECT pg_advisory_xact_lock(hashtext(?))", Object.class,
                school + ":timetable:" + sessionId);
        Map<String, Object> current = jdbc.queryForMap("SELECT * FROM timetable_version WHERE id=? AND school_id=? FOR UPDATE", id, school);
        assertActionVersion(in.version(), ((Number) current.get("version")).longValue(), "publier");
        if (!"DRAFT".equals(current.get("status"))) throw ApiException.conflict("Seul un brouillon de planning peut être publié");
        LocalDate effectiveDate = localDate(current.get("effective_from"));
        List<Map<String, Object>> assignmentConflicts = new ArrayList<>();
        Map<UUID, UUID> canonicalHomerooms = new LinkedHashMap<>();
        List<Map<String, Object>> slots = jdbc.queryForList(
                "SELECT id,class_id,subject_code,day_idx,slot_idx FROM timetable_slot WHERE school_id=? AND timetable_version_id=? ORDER BY class_id,day_idx,slot_idx",
                school, id);
        for (Map<String, Object> slot : slots) {
            String subjectCode = (String) slot.get("subject_code");
            TeachingAssignmentResolver.Resolution resolved = assignments.resolve(sessionId,
                    (UUID) slot.get("class_id"), subjectCode, effectiveDate);
            if (!resolved.available()) {
                assignmentConflicts.add(new LinkedHashMap<>(Map.of(
                        "resourceType", "ASSIGNMENT", "classId", slot.get("class_id"),
                        "subjectCode", Objects.toString(subjectCode, ""), "dayIdx", slot.get("day_idx"),
                        "slotIdx", slot.get("slot_idx"), "code", resolved.code(),
                        "repair", "Repair the canonical teacher assignment in Academic Setup.")));
                continue;
            }
            jdbc.update("""
                UPDATE timetable_slot
                   SET teacher_id=?, assignment_id=?, assignment_version=?
                 WHERE id=? AND school_id=? AND timetable_version_id=?
                """, resolved.teacherId(), resolved.assignmentId(), resolved.assignmentVersion(),
                    slot.get("id"), school, id);
            if ("HOMEROOM".equals(resolved.source())) {
                canonicalHomerooms.put((UUID) slot.get("class_id"), resolved.teacherId());
            }
        }
        if (!assignmentConflicts.isEmpty()) throw ApiException.conflict("TIMETABLE_ASSIGNMENT_BLOCKED",
                "Le planning contient des cours sans affectation canonique résolue.", assignmentConflicts);
        canonicalHomerooms.forEach((classId, teacherId) -> jdbc.update("""
            UPDATE timetable_class_config
               SET homeroom_teacher_id=?, version=version+1, updated_at=now()
             WHERE school_id=? AND academic_session_id=? AND class_id=? AND status='DRAFT'
            """, teacherId, school, sessionId, classId));
        List<Map<String, Object>> resourceConflicts = resourceBlockers(sessionId, id);
        if (!resourceConflicts.isEmpty()) throw ApiException.conflict("TIMETABLE_RESOURCES_BLOCKED",
                "Le planning contient des ressources indisponibles ou des salles trop petites.", resourceConflicts);
        Integer teacherConflicts = jdbc.queryForObject("""
            SELECT count(*) FROM (
                SELECT day_idx,slot_idx,teacher_id FROM timetable_slot
                 WHERE school_id=? AND timetable_version_id=? AND teacher_id IS NOT NULL
                 GROUP BY day_idx,slot_idx,teacher_id HAVING count(*)>1
            ) x
            """, Integer.class, school, id);
        Integer roomConflicts = jdbc.queryForObject("""
            SELECT count(*) FROM (
                SELECT day_idx,slot_idx,lower(btrim(room)) room FROM timetable_slot
                 WHERE school_id=? AND timetable_version_id=? AND room IS NOT NULL AND btrim(room)<>''
                 GROUP BY day_idx,slot_idx,lower(btrim(room)) HAVING count(*)>1
            ) x
            """, Integer.class, school, id);
        List<String> conflictBlockers = new ArrayList<>();
        if (teacherConflicts != null && teacherConflicts > 0) conflictBlockers.add("TEACHER_DOUBLE_BOOKED");
        if (roomConflicts != null && roomConflicts > 0) conflictBlockers.add("ROOM_DOUBLE_BOOKED");
        if (!conflictBlockers.isEmpty()) throw ApiException.blockers("TIMETABLE_CONFLICTS", "Le planning contient des conflits non résolus.", conflictBlockers);
        jdbc.update("UPDATE timetable_version SET status='ARCHIVED',archive_reason=?,version=version+1,updated_at=now() WHERE school_id=? AND academic_session_id=? AND status='PUBLISHED'", "Replaced by version " + current.get("version_no"), school, sessionId);
        int changed = jdbc.update("UPDATE timetable_version SET status='PUBLISHED',published_at=now(),published_by=?,version=version+1,updated_at=now() WHERE id=? AND school_id=? AND status='DRAFT'", currentUser(), id, school);
        if (changed == 0) throw ApiException.conflict("La version du planning a changé. Rechargez avant de publier.");
        jdbc.update("UPDATE timetable_class_config c SET status='PUBLISHED',published_at=now(),published_by=?,version=version+1,updated_at=now() "
                        + "WHERE c.school_id=? AND c.academic_session_id=? AND EXISTS "
                        + "(SELECT 1 FROM timetable_slot s WHERE s.school_id=c.school_id AND s.timetable_version_id=? AND s.class_id=c.class_id)",
                currentUser(), school, sessionId, id);
        jdbc.update("""
            UPDATE timetable_slot SET published_teacher_id=teacher_id,
                   published_assignment_id=assignment_id,
                   published_assignment_version=assignment_version
             WHERE school_id=? AND timetable_version_id=?
            """, school, id);
        TimetableVersionView result = versionView(id);
        audit.record("TIMETABLE_VERSION_PUBLISHED", "TimetableVersion", id.toString(), current, result, in.reason());
        return result;
    }

    /**
     * Publish one class without forcing every other draft class in the session
     * to be complete.  The published version remains a full immutable snapshot
     * of every class that was already published plus the class being published.
     * A successor draft is copied before the snapshot is frozen so unfinished
     * classes (and future reopen operations) keep their editable work.
     */
    @Transactional
    public TimetableVersionView publishClass(UUID sessionId, UUID classId, long classConfigVersion, String reason) {
        if (reason == null || reason.isBlank()) throw ApiException.badRequest("Le motif de publication est obligatoire.");
        UUID school = TenantContext.get();
        requireSession(sessionId);
        jdbc.queryForObject("SELECT pg_advisory_xact_lock(hashtext(?))", Object.class,
                school + ":timetable:" + sessionId);

        Map<String, Object> config = jdbc.query("""
                SELECT c.id,c.status,c.version,sc.name
                  FROM timetable_class_config c
                  JOIN school_class sc ON sc.id=c.class_id AND sc.school_id=c.school_id
                 WHERE c.school_id=? AND c.academic_session_id=? AND c.class_id=?
                 FOR UPDATE
                """, rs -> {
            if (!rs.next()) return null;
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("id", rs.getObject("id", UUID.class));
            value.put("status", rs.getString("status"));
            value.put("version", rs.getLong("version"));
            value.put("class_name", rs.getString("name"));
            return value;
        }, school, sessionId, classId);
        if (config == null) throw ApiException.notFound("Configuration du planning de la classe");
        long currentConfigVersion = ((Number) config.get("version")).longValue();
        if (currentConfigVersion != classConfigVersion) {
            throw ApiException.staleVersion("Le planning de cette classe a changé. Rechargez avant de publier.",
                    currentConfigVersion, classConfigVersion);
        }
        if (!"DRAFT".equals(config.get("status"))) {
            throw ApiException.conflict("Seul le planning brouillon de cette classe peut être publié.");
        }

        UUID draftId = currentDraftVersion(sessionId);
        if (draftId == null) throw ApiException.badRequest("Aucune version brouillon ne contient ce planning.");
        Map<String, Object> current = jdbc.queryForMap(
                "SELECT * FROM timetable_version WHERE id=? AND school_id=? AND academic_session_id=? FOR UPDATE",
                draftId, school, sessionId);
        LocalDate effectiveDate = localDate(current.get("effective_from"));
        List<Map<String, Object>> slots = jdbc.queryForList("""
                SELECT id,class_id,subject_code,day_idx,slot_idx
                  FROM timetable_slot
                 WHERE school_id=? AND timetable_version_id=? AND class_id=?
                 ORDER BY day_idx,slot_idx
                """, school, draftId, classId);
        if (slots.isEmpty()) throw ApiException.badRequest("Ajoutez au moins un cours avant de publier l'emploi du temps de cette classe.");

        List<Map<String, Object>> assignmentConflicts = new ArrayList<>();
        UUID canonicalHomeroom = null;
        for (Map<String, Object> slot : slots) {
            String subjectCode = (String) slot.get("subject_code");
            TeachingAssignmentResolver.Resolution resolved = assignments.resolve(sessionId, classId,
                    subjectCode, effectiveDate);
            if (!resolved.available()) {
                Map<String, Object> conflict = new LinkedHashMap<>();
                conflict.put("resourceType", "ASSIGNMENT");
                conflict.put("classId", classId);
                conflict.put("class", config.get("class_name"));
                conflict.put("subjectCode", Objects.toString(subjectCode, ""));
                conflict.put("dayIdx", slot.get("day_idx"));
                conflict.put("slotIdx", slot.get("slot_idx"));
                conflict.put("code", resolved.code());
                conflict.put("repair", "Repair the canonical teacher assignment in Academic Setup.");
                assignmentConflicts.add(conflict);
                continue;
            }
            jdbc.update("""
                    UPDATE timetable_slot
                       SET teacher_id=?, assignment_id=?, assignment_version=?
                     WHERE id=? AND school_id=? AND timetable_version_id=?
                    """, resolved.teacherId(), resolved.assignmentId(), resolved.assignmentVersion(),
                    slot.get("id"), school, draftId);
            if ("HOMEROOM".equals(resolved.source())) canonicalHomeroom = resolved.teacherId();
        }
        if (!assignmentConflicts.isEmpty()) {
            throw ApiException.conflict("TIMETABLE_ASSIGNMENT_BLOCKED",
                    "Cette classe contient des cours sans enseignant résolu.", assignmentConflicts);
        }

        // Preserve the complete editable workspace before removing unfinished
        // classes from the immutable snapshot that is about to be published.
        UUID successorDraftId = cloneDraftSuccessor(current, draftId, sessionId);
        jdbc.update("""
                DELETE FROM timetable_slot s
                 USING timetable_class_config c
                 WHERE s.school_id=? AND s.timetable_version_id=?
                   AND c.school_id=s.school_id AND c.academic_session_id=? AND c.class_id=s.class_id
                   AND c.status='DRAFT' AND c.class_id<>?
                """, school, draftId, sessionId, classId);

        if (canonicalHomeroom != null) {
            jdbc.update("""
                    UPDATE timetable_class_config
                       SET homeroom_teacher_id=?,updated_at=now()
                     WHERE school_id=? AND academic_session_id=? AND class_id=?
                    """, canonicalHomeroom, school, sessionId, classId);
        }
        List<Map<String, Object>> resourceConflicts = resourceBlockers(sessionId, draftId, classId);
        if (!resourceConflicts.isEmpty()) {
            throw ApiException.conflict("TIMETABLE_RESOURCES_BLOCKED",
                    "Cette classe contient des ressources indisponibles ou des salles trop petites.", resourceConflicts);
        }
        assertNoVersionConflicts(school, draftId);

        jdbc.update("""
                UPDATE timetable_version
                   SET status='ARCHIVED',archive_reason=?,version=version+1,updated_at=now()
                 WHERE school_id=? AND academic_session_id=? AND status='PUBLISHED'
                """, "Replaced by version " + current.get("version_no"), school, sessionId);
        int published = jdbc.update("""
                UPDATE timetable_version
                   SET status='PUBLISHED',published_at=now(),published_by=?,version=version+1,updated_at=now()
                 WHERE id=? AND school_id=? AND status='DRAFT'
                """, currentUser(), draftId, school);
        if (published == 0) throw ApiException.conflict("La version du planning a changé. Rechargez avant de publier.");
        int classPublished = jdbc.update("""
                UPDATE timetable_class_config
                   SET status='PUBLISHED',published_at=now(),published_by=?,version=version+1,updated_at=now()
                 WHERE school_id=? AND academic_session_id=? AND class_id=? AND status='DRAFT' AND version=?
                """, currentUser(), school, sessionId, classId, classConfigVersion);
        if (classPublished == 0) throw ApiException.conflict("Le planning de cette classe a changé. Rechargez avant de publier.");
        jdbc.update("""
                UPDATE timetable_slot
                   SET published_teacher_id=teacher_id,
                       published_assignment_id=assignment_id,
                       published_assignment_version=assignment_version
                 WHERE school_id=? AND timetable_version_id=?
                """, school, draftId);
        TimetableVersionView result = versionView(draftId);
        audit.record("TIMETABLE_CLASS_PUBLISHED", "SchoolClass", classId.toString(), config,
                Map.of("publishedVersionId", draftId, "successorDraftVersionId", successorDraftId), reason.trim());
        return result;
    }

    /** Reopen one published class into the already-preserved successor draft. */
    @Transactional
    public void reopenClass(UUID sessionId, UUID classId, long classConfigVersion, String reason) {
        if (reason == null || reason.isBlank()) throw ApiException.badRequest("Le motif de réouverture est obligatoire.");
        UUID school = TenantContext.get();
        requireSession(sessionId);
        jdbc.queryForObject("SELECT pg_advisory_xact_lock(hashtext(?))", Object.class,
                school + ":timetable:" + sessionId);
        Map<String, Object> config = jdbc.query("""
                SELECT id,status,version FROM timetable_class_config
                 WHERE school_id=? AND academic_session_id=? AND class_id=? FOR UPDATE
                """, rs -> {
            if (!rs.next()) return null;
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("id", rs.getObject(1, UUID.class));
            value.put("status", rs.getString(2));
            value.put("version", rs.getLong(3));
            return value;
        }, school, sessionId, classId);
        if (config == null) throw ApiException.notFound("Configuration du planning de la classe");
        long currentConfigVersion = ((Number) config.get("version")).longValue();
        if (currentConfigVersion != classConfigVersion) {
            throw ApiException.staleVersion("Le planning de cette classe a changé. Rechargez avant de le rouvrir.",
                    currentConfigVersion, classConfigVersion);
        }
        if (!"PUBLISHED".equals(config.get("status"))) {
            throw ApiException.conflict("Seul un planning publié peut être rouvert.");
        }
        UUID draftId = ensureDraftVersion(sessionId);
        UUID publishedId = jdbc.query("""
                SELECT v.id FROM timetable_version v
                 WHERE v.school_id=? AND v.academic_session_id=? AND v.status='PUBLISHED'
                   AND EXISTS (SELECT 1 FROM timetable_slot s
                                WHERE s.school_id=v.school_id AND s.timetable_version_id=v.id
                                  AND s.class_id=?)
                 ORDER BY v.version_no DESC LIMIT 1
                """, rs -> rs.next() ? rs.getObject(1, UUID.class) : null, school, sessionId, classId);
        if (publishedId == null) throw ApiException.conflict("Aucune version publiée ne contient cette classe.");
        jdbc.update("""
                INSERT INTO timetable_slot
                    (id,school_id,class_id,academic_session_id,day_idx,slot_idx,subject_code,teacher_id,room,
                     assignment_id,assignment_version,timetable_version_id)
                SELECT gen_random_uuid(),s.school_id,s.class_id,s.academic_session_id,s.day_idx,s.slot_idx,
                       s.subject_code,coalesce(s.published_teacher_id,s.teacher_id),s.room,
                       coalesce(s.published_assignment_id,s.assignment_id),
                       coalesce(s.published_assignment_version,s.assignment_version),?
                  FROM timetable_slot s
                 WHERE s.school_id=? AND s.timetable_version_id=? AND s.class_id=?
                ON CONFLICT (school_id,timetable_version_id,class_id,day_idx,slot_idx) DO NOTHING
                """, draftId, school, publishedId, classId);
        int changed = jdbc.update("""
                UPDATE timetable_class_config
                   SET status='DRAFT',published_at=NULL,published_by=NULL,version=version+1,updated_at=now()
                 WHERE school_id=? AND academic_session_id=? AND class_id=? AND status='PUBLISHED' AND version=?
                """, school, sessionId, classId, classConfigVersion);
        if (changed == 0) throw ApiException.conflict("Le planning de cette classe a changé. Rechargez avant de le rouvrir.");
        audit.record("TIMETABLE_CLASS_REOPENED", "SchoolClass", classId.toString(), config,
                Map.of("draftVersionId", draftId, "publishedVersionId", publishedId), reason.trim());
    }

    @Transactional
    public TimetableVersionView archive(UUID id, TimetableVersionActionRequest in) {
        policy.require("TIMETABLE_ARCHIVE", schoolContext());
        int changed = jdbc.update("UPDATE timetable_version SET status='ARCHIVED',archive_reason=?,version=version+1,updated_at=now() WHERE id=? AND school_id=? AND status<>'ARCHIVED'", in.reason().trim(), id, TenantContext.get());
        if (changed == 0) throw ApiException.notFound("Version du planning");
        TimetableVersionView result = versionView(id);
        audit.record("TIMETABLE_VERSION_ARCHIVED", "TimetableVersion", id.toString(), null, result, in.reason());
        return result;
    }

    @Transactional
    public TimetableVersionView reopenAsNew(UUID id, TimetableVersionActionRequest in) {
        policy.require("TIMETABLE_REOPEN", schoolContext());
        TimetableVersionView old = versionView(id);
        assertActionVersion(in.version(), old.version(), "rouvrir");
        TimetableVersionView created = create(new TimetableVersionUpsert(old.academicSessionId(), old.effectiveFrom(), old.effectiveTo(), old.timezone(), id, in.reason()));
        jdbc.update("UPDATE timetable_class_config SET status='DRAFT',version=version+1,updated_at=now() WHERE school_id=? AND academic_session_id=?",
                TenantContext.get(), old.academicSessionId());
        return created;
    }

    private static void assertActionVersion(Long requested, long current, String action) {
        long stale = requested == null ? -1L : requested;
        if (requested == null || requested != current) {
            throw ApiException.staleVersion(
                    "La version du planning a changé depuis son chargement. Rechargez avant de " + action + ".",
                    current, stale);
        }
    }

    @Transactional(readOnly = true)
    public TimetableVersionDiff diff(UUID fromId, UUID toId) {
        policy.require("TIMETABLE_MASTER_VIEW", schoolContext());
        ensureOwned(fromId); ensureOwned(toId);
        int added = count("SELECT count(*) FROM timetable_slot b WHERE b.timetable_version_id=? AND NOT EXISTS (SELECT 1 FROM timetable_slot a WHERE a.timetable_version_id=? AND a.class_id=b.class_id AND a.day_idx=b.day_idx AND a.slot_idx=b.slot_idx)", toId, fromId);
        int removed = count("SELECT count(*) FROM timetable_slot a WHERE a.timetable_version_id=? AND NOT EXISTS (SELECT 1 FROM timetable_slot b WHERE b.timetable_version_id=? AND b.class_id=a.class_id AND b.day_idx=a.day_idx AND b.slot_idx=a.slot_idx)", fromId, toId);
        int changed = count("SELECT count(*) FROM timetable_slot a JOIN timetable_slot b ON b.timetable_version_id=? AND a.class_id=b.class_id AND a.day_idx=b.day_idx AND a.slot_idx=b.slot_idx WHERE a.timetable_version_id=? AND (a.subject_code,btrim(a.room),a.teacher_id) IS DISTINCT FROM (b.subject_code,btrim(b.room),b.teacher_id)", toId, fromId);
        List<String> changes = jdbc.query("""
            SELECT coalesce(a.class_id,b.class_id)::text||':'||coalesce(a.day_idx,b.day_idx)::text||':'||coalesce(a.slot_idx,b.slot_idx)::text
              FROM timetable_slot a FULL OUTER JOIN timetable_slot b
                ON b.timetable_version_id=? AND a.class_id=b.class_id AND a.day_idx=b.day_idx AND a.slot_idx=b.slot_idx
             WHERE (a.timetable_version_id=? OR b.timetable_version_id=?)
               AND (a.id IS NULL OR b.id IS NULL OR (a.subject_code,a.teacher_id,a.room) IS DISTINCT FROM (b.subject_code,b.teacher_id,b.room))
             ORDER BY 1 LIMIT 200
            """, (rs, n) -> rs.getString(1), toId, fromId, toId);
        return new TimetableVersionDiff(fromId, toId, added, removed, changed, changes);
    }

    @Transactional(readOnly = true)
    public List<TimetableDriftView> drift(UUID versionId) {
        policy.require("TIMETABLE_MASTER_VIEW", schoolContext());
        UUID school = TenantContext.get();
        Map<String, Object> version = jdbc.queryForMap(
                "SELECT academic_session_id,effective_from,status FROM timetable_version WHERE id=? AND school_id=?",
                versionId, school);
        if (!"PUBLISHED".equals(version.get("status"))) return List.of();
        UUID sessionId = (UUID) version.get("academic_session_id");
        LocalDate effectiveDate = localDate(version.get("effective_from"));
        return jdbc.query("""
                SELECT s.id,s.class_id,c.name,s.subject_code,s.day_idx,s.slot_idx,
                       coalesce(s.published_teacher_id,s.teacher_id) AS published_teacher_id,
                       pt.name AS published_teacher_name,
                       coalesce(s.published_assignment_id,s.assignment_id) AS published_assignment_id,
                       coalesce(s.published_assignment_version,s.assignment_version,0) AS published_assignment_version
                  FROM timetable_slot s
                  JOIN school_class c ON c.id=s.class_id
                  LEFT JOIN employee pt ON pt.id=coalesce(s.published_teacher_id,s.teacher_id)
                 WHERE s.school_id=? AND s.timetable_version_id=?
                 ORDER BY c.name,s.day_idx,s.slot_idx
                """, (rs, n) -> {
            UUID slotId = rs.getObject("id", UUID.class);
            UUID classId = rs.getObject("class_id", UUID.class);
            String subjectCode = rs.getString("subject_code");
            UUID publishedTeacherId = rs.getObject("published_teacher_id", UUID.class);
            UUID publishedAssignmentId = rs.getObject("published_assignment_id", UUID.class);
            long publishedAssignmentVersion = rs.getLong("published_assignment_version");
            TeachingAssignmentResolver.Resolution current = assignments.resolve(sessionId, classId, subjectCode, effectiveDate);
            boolean same = current.available()
                    && Objects.equals(publishedTeacherId, current.teacherId())
                    && Objects.equals(publishedAssignmentId, current.assignmentId())
                    && publishedAssignmentVersion == current.assignmentVersion();
            if (same) return null;
            String message = !current.available()
                    ? "The canonical dated assignment is no longer resolved. Repair Academic Setup before creating the next timetable version."
                    : "The canonical dated assignment changed after publication. Create a new timetable version to adopt it.";
            return new TimetableDriftView(slotId, classId, rs.getString("name"), subjectCode,
                    rs.getInt("day_idx"), rs.getInt("slot_idx"), publishedTeacherId,
                    rs.getString("published_teacher_name"), current.teacherId(), current.teacherName(),
                    publishedAssignmentId, publishedAssignmentVersion, current.assignmentId(),
                    current.assignmentVersion(), current.available() ? "CHANGED" : current.status(), message);
        }, school, versionId).stream().filter(Objects::nonNull).toList();
    }

    @Transactional(readOnly = true)
    public List<TimetableProjectionSlotView> master(UUID versionId, LocalDate occurrenceDate) {
        policy.require("TIMETABLE_MASTER_VIEW", schoolContext());
        UUID school = TenantContext.get();
        Map<String, Object> version = jdbc.queryForMap(
                "SELECT academic_session_id,effective_from,effective_to,status FROM timetable_version WHERE id=? AND school_id=?",
                versionId, school);
        LocalDate effectiveDate = localDate(version.get("effective_from"));
        LocalDate date = occurrenceDate == null ? effectiveDate : occurrenceDate;
        LocalDate endDate = localDate(version.get("effective_to"));
        if (date == null || (endDate != null && date.isAfter(endDate))) {
            throw ApiException.field(org.springframework.http.HttpStatus.BAD_REQUEST, "TIMETABLE_DATE_OUT_OF_RANGE",
                    "La date demandée ne se trouve pas dans la période effective du planning.", "occurrenceDate",
                    "Choose a date inside the timetable effective range.");
        }
        UUID sessionId = (UUID) version.get("academic_session_id");
        return jdbc.query("""
                SELECT s.id,s.class_id,c.name,s.subject_code,s.day_idx,s.slot_idx,
                       CASE WHEN sub.action='CANCEL' THEN NULL
                            ELSE coalesce(sub.replacement_teacher_id,s.published_teacher_id,s.teacher_id) END AS teacher_id,
                       CASE WHEN sub.action='CANCEL' THEN NULL
                            ELSE coalesce(rt.name,pt.name) END AS teacher_name,
                       s.room,? AS occurrence_date,sub.action,rt.name AS substitution_teacher_name
                  FROM timetable_slot s
                  JOIN school_class c ON c.id=s.class_id
                  LEFT JOIN employee pt ON pt.id=coalesce(s.published_teacher_id,s.teacher_id)
                  LEFT JOIN timetable_substitution sub
                    ON sub.school_id=s.school_id AND sub.academic_session_id=?
                   AND sub.timetable_version_id=s.timetable_version_id
                   AND sub.class_id=s.class_id AND sub.day_idx=s.day_idx AND sub.slot_idx=s.slot_idx
                   AND sub.occurrence_date=? AND sub.status='APPROVED'
                  LEFT JOIN employee rt ON rt.id=sub.replacement_teacher_id
                 WHERE s.school_id=? AND s.timetable_version_id=?
                 ORDER BY c.name,s.day_idx,s.slot_idx
                """, (rs, n) -> new TimetableProjectionSlotView(
                        rs.getObject("id", UUID.class), rs.getObject("class_id", UUID.class), rs.getString("name"),
                        rs.getString("subject_code"), rs.getObject("teacher_id", UUID.class), rs.getString("teacher_name"),
                        rs.getString("room"), rs.getInt("day_idx"), rs.getInt("slot_idx"),
                        rs.getObject("occurrence_date", LocalDate.class), rs.getString("action"),
                        rs.getString("substitution_teacher_name")),
                date, sessionId, date, school, versionId);
    }

    @Transactional(readOnly = true)
    public List<RoomView> rooms() {
        policy.require("TIMETABLE_ROOM_VIEW", schoolContext());
        return jdbc.query("SELECT id,code,label,capacity,resource_type,active,version FROM timetable_room WHERE school_id=? ORDER BY code", (rs,n) -> new RoomView(rs.getObject(1,UUID.class),rs.getString(2),rs.getString(3),rs.getObject(4,Integer.class),rs.getString(5),rs.getBoolean(6),rs.getLong(7)), TenantContext.get());
    }

    @Transactional
    public RoomView saveRoom(UUID id, RoomUpsert in) {
        policy.require("TIMETABLE_DRAFT", schoolContext());
        UUID school = TenantContext.get(); String code=in.code().trim().toUpperCase(Locale.ROOT);
        UUID room = id == null ? UUID.randomUUID() : id;
        if (id == null) jdbc.update("INSERT INTO timetable_room(id,school_id,code,label,capacity,resource_type,active) VALUES (?,?,?,?,?,?,?)",room,school,code,in.label().trim(),in.capacity(),blank(in.resourceType(),"ROOM"),in.active());
        else {
            int changed=jdbc.update("UPDATE timetable_room SET code=?,label=?,capacity=?,resource_type=?,active=?,version=version+1 WHERE id=? AND school_id=? AND (CAST(? AS bigint) IS NULL OR version=CAST(? AS bigint))",code,in.label().trim(),in.capacity(),blank(in.resourceType(),"ROOM"),in.active(),id,school,in.version(),in.version());
            if(changed==0) throw ApiException.conflict("La ressource a changé. Rechargez avant de l'enregistrer.");
        }
        return jdbc.queryForObject("SELECT id,code,label,capacity,resource_type,active,version FROM timetable_room WHERE id=? AND school_id=?",(rs,n)->new RoomView(rs.getObject(1,UUID.class),rs.getString(2),rs.getString(3),rs.getObject(4,Integer.class),rs.getString(5),rs.getBoolean(6),rs.getLong(7)),room,school);
    }

    @Transactional(readOnly = true)
    public List<RoomAvailabilityView> roomAvailability(UUID roomId) {
        policy.require("TIMETABLE_ROOM_VIEW", schoolContext());
        ensureRoom(roomId);
        return jdbc.query("SELECT id,room_id,day_idx,slot_idx,available,reason FROM timetable_room_availability WHERE school_id=? AND room_id=? ORDER BY day_idx,slot_idx",
                (rs,n) -> new RoomAvailabilityView(rs.getObject("id",UUID.class), rs.getObject("room_id",UUID.class),
                        rs.getInt("day_idx"), rs.getInt("slot_idx"), rs.getBoolean("available"), rs.getString("reason")),
                TenantContext.get(), roomId);
    }

    @Transactional
    public RoomAvailabilityView saveRoomAvailability(UUID roomId, RoomAvailabilityUpsert in) {
        policy.require("TIMETABLE_DRAFT", schoolContext());
        ensureRoom(roomId);
        if (in.dayIdx() < 0 || in.dayIdx() > 6 || in.slotIdx() < 0 || in.slotIdx() > 15) {
            throw ApiException.field(org.springframework.http.HttpStatus.BAD_REQUEST, "ROOM_SLOT_INVALID",
                    "Le jour ou la période de disponibilité est invalide.", "slotIdx", "Choose a valid school day and bell period.");
        }
        UUID id = jdbc.query("SELECT id FROM timetable_room_availability WHERE school_id=? AND room_id=? AND day_idx=? AND slot_idx=?",
                rs -> rs.next() ? rs.getObject(1,UUID.class) : null, TenantContext.get(), roomId, in.dayIdx(), in.slotIdx());
        if (id == null) {
            id = UUID.randomUUID();
            jdbc.update("INSERT INTO timetable_room_availability(id,school_id,room_id,day_idx,slot_idx,available,reason) VALUES (?,?,?,?,?,?,?)",
                    id, TenantContext.get(), roomId, in.dayIdx(), in.slotIdx(), in.available(), cleanReason(in.reason()));
        } else {
            jdbc.update("UPDATE timetable_room_availability SET available=?,reason=? WHERE id=? AND school_id=?",
                    in.available(), cleanReason(in.reason()), id, TenantContext.get());
        }
        return jdbc.queryForObject("SELECT id,room_id,day_idx,slot_idx,available,reason FROM timetable_room_availability WHERE id=? AND school_id=?",
                (rs,n) -> new RoomAvailabilityView(rs.getObject("id",UUID.class),rs.getObject("room_id",UUID.class),rs.getInt("day_idx"),rs.getInt("slot_idx"),rs.getBoolean("available"),rs.getString("reason")), id, TenantContext.get());
    }

    @Transactional(readOnly = true)
    public List<TeacherAvailabilityView> teacherAvailability(UUID teacherId) {
        policy.require("TIMETABLE_RESOURCE_VIEW", schoolContext());
        ensureTeacher(teacherId);
        return jdbc.query("SELECT id,employee_id,day_idx,slot_idx,available,reason FROM timetable_teacher_availability WHERE school_id=? AND employee_id=? ORDER BY day_idx,slot_idx",
                (rs, n) -> new TeacherAvailabilityView(rs.getObject("id", UUID.class),
                        rs.getObject("employee_id", UUID.class), rs.getInt("day_idx"), rs.getInt("slot_idx"),
                        rs.getBoolean("available"), rs.getString("reason")), TenantContext.get(), teacherId);
    }

    @Transactional
    public TeacherAvailabilityView saveTeacherAvailability(UUID teacherId, TeacherAvailabilityUpsert in) {
        policy.require("TIMETABLE_DRAFT", schoolContext());
        ensureTeacher(teacherId);
        validateAvailabilitySlot(in.dayIdx(), in.slotIdx());
        UUID school = TenantContext.get();
        UUID id = jdbc.query("SELECT id FROM timetable_teacher_availability WHERE school_id=? AND employee_id=? AND day_idx=? AND slot_idx=?",
                rs -> rs.next() ? rs.getObject(1, UUID.class) : null, school, teacherId, in.dayIdx(), in.slotIdx());
        if (id == null) {
            id = UUID.randomUUID();
            jdbc.update("INSERT INTO timetable_teacher_availability(id,school_id,employee_id,day_idx,slot_idx,available,reason) VALUES (?,?,?,?,?,?,?)",
                    id, school, teacherId, in.dayIdx(), in.slotIdx(), in.available(), cleanReason(in.reason()));
        } else {
            jdbc.update("UPDATE timetable_teacher_availability SET available=?,reason=? WHERE id=? AND school_id=?",
                    in.available(), cleanReason(in.reason()), id, school);
        }
        UUID savedId = id;
        return jdbc.queryForObject("SELECT id,employee_id,day_idx,slot_idx,available,reason FROM timetable_teacher_availability WHERE id=? AND school_id=?",
                (rs, n) -> new TeacherAvailabilityView(rs.getObject("id", UUID.class),
                        rs.getObject("employee_id", UUID.class), rs.getInt("day_idx"), rs.getInt("slot_idx"),
                        rs.getBoolean("available"), rs.getString("reason")), savedId, school);
    }

    @Transactional(readOnly = true)
    public List<TeacherWorkloadView> teacherWorkload(UUID teacherId) {
        policy.require("TIMETABLE_RESOURCE_VIEW", schoolContext());
        ensureTeacher(teacherId);
        return jdbc.query("SELECT id,employee_id,max_slots_per_day,max_slots_per_week,effective_from,effective_to,reason,version FROM timetable_teacher_workload_policy WHERE school_id=? AND employee_id=? ORDER BY effective_from",
                (rs, n) -> new TeacherWorkloadView(rs.getObject("id", UUID.class), rs.getObject("employee_id", UUID.class),
                        rs.getObject("max_slots_per_day", Integer.class), rs.getObject("max_slots_per_week", Integer.class),
                        localDate(rs.getObject("effective_from")), localDate(rs.getObject("effective_to")),
                        rs.getString("reason"), rs.getLong("version")), TenantContext.get(), teacherId);
    }

    @Transactional
    public TeacherWorkloadView saveTeacherWorkload(UUID teacherId, TeacherWorkloadUpsert in) {
        policy.require("TIMETABLE_DRAFT", schoolContext());
        ensureTeacher(teacherId);
        validateEffectiveRange(in.effectiveFrom(), in.effectiveTo(), "WORKLOAD_DATE_INVALID");
        if (in.maxSlotsPerDay() == null && in.maxSlotsPerWeek() == null)
            throw ApiException.field(org.springframework.http.HttpStatus.BAD_REQUEST, "WORKLOAD_LIMIT_REQUIRED",
                    "Configurez au moins une limite de charge.", "maxSlotsPerWeek", "Provide a daily or weekly workload limit.");
        if ((in.maxSlotsPerDay() != null && in.maxSlotsPerDay() <= 0) || (in.maxSlotsPerWeek() != null && in.maxSlotsPerWeek() <= 0))
            throw ApiException.field(org.springframework.http.HttpStatus.BAD_REQUEST, "WORKLOAD_LIMIT_INVALID",
                    "Les limites de charge doivent être positives.", "maxSlotsPerWeek", "Workload limits must be positive.");
        UUID school = TenantContext.get();
        UUID id = jdbc.query("SELECT id FROM timetable_teacher_workload_policy WHERE school_id=? AND employee_id=? AND effective_from=?",
                rs -> rs.next() ? rs.getObject(1, UUID.class) : null, school, teacherId, in.effectiveFrom());
        if (id == null) {
            id = UUID.randomUUID();
            jdbc.update("INSERT INTO timetable_teacher_workload_policy(id,school_id,employee_id,max_slots_per_day,max_slots_per_week,effective_from,effective_to,reason) VALUES (?,?,?,?,?,?,?,?)",
                    id, school, teacherId, in.maxSlotsPerDay(), in.maxSlotsPerWeek(), in.effectiveFrom(), in.effectiveTo(), cleanReason(in.reason()));
        } else {
            int changed = jdbc.update("UPDATE timetable_teacher_workload_policy SET max_slots_per_day=?,max_slots_per_week=?,effective_to=?,reason=?,version=version+1,updated_at=now() WHERE id=? AND school_id=? AND (CAST(? AS bigint) IS NULL OR version=CAST(? AS bigint))",
                    in.maxSlotsPerDay(), in.maxSlotsPerWeek(), in.effectiveTo(), cleanReason(in.reason()), id, school, in.version(), in.version());
            if (changed == 0) throw ApiException.conflict("La politique de charge a changé. Rechargez avant de l'enregistrer.");
        }
        UUID saved = id;
        return jdbc.queryForObject("SELECT id,employee_id,max_slots_per_day,max_slots_per_week,effective_from,effective_to,reason,version FROM timetable_teacher_workload_policy WHERE id=? AND school_id=?",
                (rs, n) -> new TeacherWorkloadView(rs.getObject(1, UUID.class), teacherId, rs.getObject(3, Integer.class), rs.getObject(4, Integer.class), localDate(rs.getObject(5)), localDate(rs.getObject(6)), rs.getString(7), rs.getLong(8)), saved, school);
    }

    @Transactional(readOnly = true)
    public List<TeacherQualificationView> teacherQualifications(UUID teacherId) {
        policy.require("TIMETABLE_RESOURCE_VIEW", schoolContext());
        ensureTeacher(teacherId);
        return jdbc.query("SELECT id,employee_id,qualification_code,valid_from,valid_to,evidence_reference,version FROM timetable_teacher_qualification WHERE school_id=? AND employee_id=? ORDER BY valid_from,qualification_code",
                (rs, n) -> new TeacherQualificationView(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class), rs.getString(3),
                        localDate(rs.getObject(4)), localDate(rs.getObject(5)), rs.getString(6), rs.getLong(7)), TenantContext.get(), teacherId);
    }

    @Transactional
    public TeacherQualificationView saveTeacherQualification(UUID teacherId, TeacherQualificationUpsert in) {
        policy.require("TIMETABLE_DRAFT", schoolContext());
        ensureTeacher(teacherId);
        validateEffectiveRange(in.validFrom(), in.validTo(), "QUALIFICATION_DATE_INVALID");
        UUID school = TenantContext.get();
        String code = in.qualificationCode().trim().toUpperCase(Locale.ROOT);
        UUID id = jdbc.query("SELECT id FROM timetable_teacher_qualification WHERE school_id=? AND employee_id=? AND qualification_code=? AND valid_from=?",
                rs -> rs.next() ? rs.getObject(1, UUID.class) : null, school, teacherId, code, in.validFrom());
        if (id == null) {
            id = UUID.randomUUID();
            jdbc.update("INSERT INTO timetable_teacher_qualification(id,school_id,employee_id,qualification_code,valid_from,valid_to,evidence_reference) VALUES (?,?,?,?,?,?,?)",
                    id, school, teacherId, code, in.validFrom(), in.validTo(), cleanReason(in.evidenceReference()));
        } else {
            int changed = jdbc.update("UPDATE timetable_teacher_qualification SET valid_to=?,evidence_reference=?,version=version+1,updated_at=now() WHERE id=? AND school_id=? AND (CAST(? AS bigint) IS NULL OR version=CAST(? AS bigint))",
                    in.validTo(), cleanReason(in.evidenceReference()), id, school, in.version(), in.version());
            if (changed == 0) throw ApiException.conflict("La qualification a changé. Rechargez avant de l'enregistrer.");
        }
        UUID saved = id;
        return jdbc.queryForObject("SELECT id,employee_id,qualification_code,valid_from,valid_to,evidence_reference,version FROM timetable_teacher_qualification WHERE id=? AND school_id=?",
                (rs, n) -> new TeacherQualificationView(rs.getObject(1, UUID.class), teacherId, rs.getString(3), localDate(rs.getObject(4)), localDate(rs.getObject(5)), rs.getString(6), rs.getLong(7)), saved, school);
    }

    @Transactional(readOnly = true)
    public List<SubjectQualificationRequirementView> subjectQualificationRequirements(UUID sessionId) {
        policy.require("TIMETABLE_RESOURCE_VIEW", schoolContext());
        requireSession(sessionId);
        return jdbc.query("SELECT id,academic_session_id,subject_code,qualification_code,effective_from,effective_to,reason,version FROM timetable_subject_qualification_requirement WHERE school_id=? AND academic_session_id=? ORDER BY subject_code,effective_from",
                (rs, n) -> new SubjectQualificationRequirementView(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class), rs.getString(3), rs.getString(4), localDate(rs.getObject(5)), localDate(rs.getObject(6)), rs.getString(7), rs.getLong(8)), TenantContext.get(), sessionId);
    }

    @Transactional
    public SubjectQualificationRequirementView saveSubjectQualificationRequirement(SubjectQualificationRequirementUpsert in) {
        policy.require("TIMETABLE_DRAFT", schoolContext());
        requireSession(in.academicSessionId());
        validateEffectiveRange(in.effectiveFrom(), in.effectiveTo(), "QUALIFICATION_REQUIREMENT_DATE_INVALID");
        UUID school = TenantContext.get();
        String subject = clean(in.subjectCode());
        String qualification = in.qualificationCode().trim().toUpperCase(Locale.ROOT);
        Integer known = jdbc.queryForObject("SELECT count(*) FROM subject WHERE school_id=? AND upper(code)=upper(?)", Integer.class, school, subject);
        if (known == null || known == 0) throw ApiException.field(org.springframework.http.HttpStatus.BAD_REQUEST, "SUBJECT_NOT_FOUND", "La matière n'existe pas.", "subjectCode", "Choose an existing subject.");
        UUID id = jdbc.query("SELECT id FROM timetable_subject_qualification_requirement WHERE school_id=? AND academic_session_id=? AND subject_code=? AND qualification_code=? AND effective_from=?",
                rs -> rs.next() ? rs.getObject(1, UUID.class) : null, school, in.academicSessionId(), subject, qualification, in.effectiveFrom());
        if (id == null) {
            id = UUID.randomUUID();
            jdbc.update("INSERT INTO timetable_subject_qualification_requirement(id,school_id,academic_session_id,subject_code,qualification_code,effective_from,effective_to,reason) VALUES (?,?,?,?,?,?,?,?)",
                    id, school, in.academicSessionId(), subject, qualification, in.effectiveFrom(), in.effectiveTo(), cleanReason(in.reason()));
        } else {
            int changed = jdbc.update("UPDATE timetable_subject_qualification_requirement SET effective_to=?,reason=?,version=version+1,updated_at=now() WHERE id=? AND school_id=? AND (CAST(? AS bigint) IS NULL OR version=CAST(? AS bigint))",
                    in.effectiveTo(), cleanReason(in.reason()), id, school, in.version(), in.version());
            if (changed == 0) throw ApiException.conflict("La condition de qualification a changé. Rechargez avant de l'enregistrer.");
        }
        UUID saved = id;
        return jdbc.queryForObject("SELECT id,academic_session_id,subject_code,qualification_code,effective_from,effective_to,reason,version FROM timetable_subject_qualification_requirement WHERE id=? AND school_id=?",
                (rs, n) -> new SubjectQualificationRequirementView(rs.getObject(1, UUID.class), in.academicSessionId(), rs.getString(3), rs.getString(4), localDate(rs.getObject(5)), localDate(rs.getObject(6)), rs.getString(7), rs.getLong(8)), saved, school);
    }

    @Transactional(readOnly = true)
    public List<SubstitutionView> substitutions(UUID sessionId, LocalDate date) {
        // Visibility is evaluated per published occurrence below; a module
        // read grant must never expose every substitution to a teacher.
        requireSession(sessionId);
        String suffix=date==null?"":" AND x.occurrence_date=?";
        List<Object> args=new ArrayList<>(List.of(TenantContext.get(),sessionId)); if(date!=null) args.add(date);
        List<SubstitutionScopeRow> scopeRows = jdbc.query("""
            SELECT x.id,x.academic_session_id,x.timetable_version_id,x.occurrence_date,
                   x.class_id,x.subject_code,x.day_idx,x.slot_idx
              FROM timetable_substitution x
             WHERE x.school_id=? AND x.academic_session_id=?"""+suffix+
                " ORDER BY x.occurrence_date,x.day_idx,x.slot_idx",
                (rs,n) -> new SubstitutionScopeRow(rs.getObject("id",UUID.class),
                        rs.getObject("academic_session_id",UUID.class),
                        rs.getObject("timetable_version_id",UUID.class),
                        rs.getObject("occurrence_date",LocalDate.class),
                        rs.getObject("class_id",UUID.class), rs.getString("subject_code"),
                        rs.getInt("day_idx"), rs.getInt("slot_idx")), args.toArray());
        List<UUID> permitted = scopeRows.stream().filter(this::substitutionVisible)
                .map(SubstitutionScopeRow::id).toList();
        if (permitted.isEmpty()) return List.of();
        String placeholders = String.join(",", permitted.stream().map(id -> "?").toList());
        List<Object> fullArgs = new ArrayList<>(List.of(TenantContext.get()));
        fullArgs.addAll(permitted);
        return jdbc.query("""
            SELECT x.*,c.name AS class_name,ot.name AS original_name,rt.name AS replacement_name
              FROM timetable_substitution x JOIN school_class c ON c.id=x.class_id
              LEFT JOIN employee ot ON ot.id=x.original_teacher_id
              LEFT JOIN employee rt ON rt.id=x.replacement_teacher_id
             WHERE x.school_id=? AND x.id IN ("""+placeholders+
                ") ORDER BY x.occurrence_date,x.day_idx,x.slot_idx",
                (rs,n)->new SubstitutionView(rs.getObject("id",UUID.class),
                        rs.getObject("academic_session_id",UUID.class),
                        rs.getObject("timetable_version_id",UUID.class),
                        rs.getObject("occurrence_date",LocalDate.class),
                        rs.getObject("class_id",UUID.class),rs.getString("class_name"),
                        rs.getString("subject_code"),rs.getInt("day_idx"),rs.getInt("slot_idx"),
                        rs.getObject("original_teacher_id",UUID.class),rs.getString("original_name"),
                        rs.getObject("replacement_teacher_id",UUID.class),rs.getString("replacement_name"),
                        rs.getString("action"),rs.getString("reason"),rs.getString("status"),
                        rs.getLong("version")), fullArgs.toArray());
    }

    @Transactional
    public SubstitutionView createSubstitution(SubstitutionUpsert in) {
        requireSession(in.academicSessionId());
        policy.require("TIMETABLE_SUBSTITUTION_MANAGE", substitutionContext(in));
        if (in.dayIdx() < 0 || in.dayIdx() > 6 || in.slotIdx() < 0 || in.slotIdx() > 15) {
            throw ApiException.field(org.springframework.http.HttpStatus.BAD_REQUEST, "SUBSTITUTION_SLOT_INVALID",
                    "Le jour ou la période de substitution est invalide.", "slotIdx", "Choose a valid school day and bell period.");
        }
        if (in.timetableVersionId() != null) validateVersionForSession(in.timetableVersionId(), in.academicSessionId());
        String action=in.action().trim().toUpperCase(Locale.ROOT);
        if(!Set.of("SUBSTITUTE","CANCEL").contains(action)) throw ApiException.badRequest("Action de remplacement invalide");
        if("SUBSTITUTE".equals(action)) {
            if (in.replacementTeacherId()==null) throw ApiException.field(org.springframework.http.HttpStatus.BAD_REQUEST,
                    "REPLACEMENT_TEACHER_REQUIRED", "Le remplaçant est obligatoire", "replacementTeacherId", "Sélectionnez un enseignant remplaçant.");
            if (in.originalTeacherId() != null && in.originalTeacherId().equals(in.replacementTeacherId())) {
                throw ApiException.field(org.springframework.http.HttpStatus.BAD_REQUEST, "REPLACEMENT_MUST_DIFFER",
                        "Le remplaçant doit être différent de l'enseignant absent.", "replacementTeacherId", "Choose a different replacement teacher.");
            }
        }
        UUID original=in.originalTeacherId();
        if(original==null) original=jdbc.query("SELECT COALESCE(t.published_teacher_id,t.teacher_id) FROM timetable_slot t WHERE t.school_id=? AND t.academic_session_id=? AND t.class_id=? AND t.day_idx=? AND t.slot_idx=? ORDER BY t.timetable_version_id DESC LIMIT 1",rs->rs.next()?rs.getObject(1,UUID.class):null,TenantContext.get(),in.academicSessionId(),in.classId(),in.dayIdx(),in.slotIdx());
        UUID id=UUID.randomUUID();
        try { jdbc.update("INSERT INTO timetable_substitution(id,school_id,academic_session_id,timetable_version_id,occurrence_date,class_id,subject_code,day_idx,slot_idx,original_teacher_id,replacement_teacher_id,action,reason,created_by) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)",id,TenantContext.get(),in.academicSessionId(),in.timetableVersionId(),in.occurrenceDate(),in.classId(),clean(in.subjectCode()),in.dayIdx(),in.slotIdx(),original,in.replacementTeacherId(),action,in.reason().trim(),currentUser()); }
        catch(Exception ex){ throw ApiException.conflict("Une substitution existe déjà pour cette classe et cette période."); }
        return substitution(id);
    }

    @Transactional
    public SubstitutionView approveSubstitution(UUID id, SubstitutionActionRequest in) {
        policy.require("TIMETABLE_SUBSTITUTION_MANAGE", substitutionContext(substitution(id)));
        int changed=jdbc.update("UPDATE timetable_substitution SET status='APPROVED',approved_by=?,approved_at=now(),reason=?,version=version+1,updated_at=now() WHERE id=? AND school_id=? AND status='DRAFT' AND (CAST(? AS bigint) IS NULL OR version=CAST(? AS bigint))",currentUser(),in.reason().trim(),id,TenantContext.get(),in.version(),in.version());
        if(changed==0) throw ApiException.conflict("La substitution a changé ou n'est plus en brouillon.");
        return substitution(id);
    }

    @Transactional
    public SubstitutionView cancelSubstitution(UUID id, SubstitutionActionRequest in) {
        policy.require("TIMETABLE_SUBSTITUTION_MANAGE", substitutionContext(substitution(id)));
        int changed=jdbc.update("UPDATE timetable_substitution SET status='CANCELLED',reason=?,version=version+1,updated_at=now() WHERE id=? AND school_id=? AND status<>'CANCELLED' AND (CAST(? AS bigint) IS NULL OR version=CAST(? AS bigint))",in.reason().trim(),id,TenantContext.get(),in.version(),in.version());
        if(changed==0) throw ApiException.conflict("La substitution a changé ou est déjà annulée.");
        return substitution(id);
    }

    @Transactional(readOnly = true)
    public String exportCsv(UUID versionId) {
        policy.require("TIMETABLE_EXPORT", versionContext(versionId));
        ensureOwned(versionId);
        List<TimetableExportRow> rows = exportRows(versionId);
        return "# timetable-version="+versionId+"\nclass,day,period,subject,teacherId,room\n"+
                rows.stream().map(r -> String.join(",", csv(r.className()), String.valueOf(r.dayIdx()),
                        String.valueOf(r.slotIdx()), csv(r.subjectCode()), csv(r.teacherId()), csv(r.room())))
                        .collect(java.util.stream.Collectors.joining("\n")) + "\n";
    }

    @Transactional(readOnly = true)
    public byte[] exportXlsx(UUID versionId) {
        policy.require("TIMETABLE_EXPORT", versionContext(versionId));
        ensureOwned(versionId);
        List<TimetableExportRow> rows = exportRows(versionId);
        String sheet = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
                "<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><sheetData>" +
                xlsxRow(1, List.of("Class", "Day", "Period", "Subject", "Teacher ID", "Room")) +
                java.util.stream.IntStream.range(0, rows.size()).mapToObj(i -> {
                    TimetableExportRow r = rows.get(i);
                    return xlsxRow(i + 2, List.of(r.className(), String.valueOf(r.dayIdx()),
                            String.valueOf(r.slotIdx()), r.subjectCode(), r.teacherId(), r.room()));
                })
                        .collect(java.util.stream.Collectors.joining()) +
                "</sheetData></worksheet>";
        String workbook = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
                "<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\"><sheets><sheet name=\"Timetable\" sheetId=\"1\" r:id=\"rId1\"/></sheets></workbook>";
        String contentTypes = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
                "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\"><Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/><Default Extension=\"xml\" ContentType=\"application/xml\"/><Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/><Override PartName=\"/xl/worksheets/sheet1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/></Types>";
        String rootRels = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
                "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/></Relationships>";
        String workbookRels = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
                "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet1.xml\"/></Relationships>";
        try (ByteArrayOutputStream out = new ByteArrayOutputStream(); java.util.zip.ZipOutputStream zip = new java.util.zip.ZipOutputStream(out, StandardCharsets.UTF_8)) {
            zipEntry(zip, "[Content_Types].xml", contentTypes);
            zipEntry(zip, "_rels/.rels", rootRels);
            zipEntry(zip, "xl/workbook.xml", workbook);
            zipEntry(zip, "xl/_rels/workbook.xml.rels", workbookRels);
            zipEntry(zip, "xl/worksheets/sheet1.xml", sheet);
            zip.finish();
            return out.toByteArray();
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to create timetable XLSX", ex);
        }
    }

    @Transactional(readOnly = true)
    public byte[] exportPdf(UUID versionId) {
        policy.require("TIMETABLE_EXPORT", versionContext(versionId));
        ensureOwned(versionId);
        TimetableVersionView version = versionView(versionId);
        List<TimetableExportRow> rows = exportRows(versionId);
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            document.getDocumentInformation().setTitle("BBC SMS Timetable V" + version.versionNo());
            document.getDocumentInformation().setAuthor("BBC SMS");
            PDFont normal = loadFont(document, "/usr/share/fonts/dejavu/DejaVuSans.ttf");
            PDFont bold = loadFont(document, "/usr/share/fonts/dejavu/DejaVuSans-Bold.ttf");
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            float y = 790;
            PDPageContentStream stream = new PDPageContentStream(document, page);
            stream.setFont(bold, 14);
            stream.beginText(); stream.newLineAtOffset(42, y); stream.showText("BBC SMS TIMETABLE V" + version.versionNo()); stream.endText(); y -= 22;
            stream.setFont(normal, 8);
            stream.beginText(); stream.newLineAtOffset(42, y); stream.showText("Session " + version.academicSessionId() + " | " + version.timezone() + " | " + version.effectiveFrom() + " -> " + (version.effectiveTo() == null ? "open" : version.effectiveTo())); stream.endText(); y -= 22;
            stream.setFont(bold, 8);
            stream.beginText(); stream.newLineAtOffset(42, y); stream.showText("Class | Day | Period | Subject | Teacher ID | Room"); stream.endText(); y -= 14;
            stream.setFont(normal, 7);
            for (TimetableExportRow row : rows) {
                if (y < 40) { stream.close(); page = new PDPage(PDRectangle.A4); document.addPage(page); stream = new PDPageContentStream(document, page); stream.setFont(normal, 7); y = 790; }
                String line = row.className() + " | " + row.dayIdx() + " | " + row.slotIdx() + " | " + row.subjectCode() + " | " + row.teacherId() + " | " + row.room();
                stream.beginText(); stream.newLineAtOffset(42, y); stream.showText(clipPdf(line, 150)); stream.endText(); y -= 11;
            }
            stream.close();
            document.save(out);
            return out.toByteArray();
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to create timetable PDF", ex);
        }
    }

    @Transactional(readOnly = true)
    public String exportIcal(UUID versionId) {
        policy.require("TIMETABLE_EXPORT", versionContext(versionId));
        Map<String,Object> version = jdbc.queryForObject("SELECT effective_from,effective_to,timezone FROM timetable_version WHERE id=? AND school_id=?",
                (rs,n) -> {
                    Map<String,Object> row = new HashMap<>();
                    row.put("effective_from", rs.getObject("effective_from", LocalDate.class));
                    row.put("effective_to", rs.getObject("effective_to", LocalDate.class));
                    row.put("timezone", rs.getString("timezone"));
                    return row;
                }, versionId, TenantContext.get());
        LocalDate from = localDate(version.get("effective_from"));
        LocalDate to = localDate(version.get("effective_to"));
        LocalDate weekStart = from.minusDays(from.getDayOfWeek().getValue() - 1L);
        String timezone = String.valueOf(version.get("timezone"));
        List<String> events = jdbc.query("""
            SELECT c.id::text AS class_id,c.name,s.subject_code,s.day_idx,s.slot_idx,p.start_time,p.end_time,
                   coalesce(s.published_teacher_id,s.teacher_id)::text teacher_id
              FROM timetable_slot s JOIN school_class c ON c.id=s.class_id
              JOIN timetable_period p ON p.school_id=s.school_id AND p.slot_idx=s.slot_idx
             WHERE s.school_id=? AND s.timetable_version_id=?
             ORDER BY c.name,s.day_idx,s.slot_idx
            """, (rs,n) -> {
                LocalDate day = weekStart.plusDays(rs.getInt("day_idx"));
                String start = day.toString().replace("-", "") + "T" + rs.getTime("start_time").toLocalTime().toString().replace(":", "");
                String end = day.toString().replace("-", "") + "T" + rs.getTime("end_time").toLocalTime().toString().replace(":", "");
                 String summary = icalEscape(rs.getString("subject_code") + " - " + rs.getString("name"));
                 String rule = to == null ? "RRULE:FREQ=WEEKLY" : "RRULE:FREQ=WEEKLY;UNTIL=" + to.plusDays(1).toString().replace("-", "") + "T000000Z";
                 return "BEGIN:VEVENT\nUID=" + versionId + "-" + rs.getString("class_id") + "-" + rs.getInt("day_idx") + "-" + rs.getInt("slot_idx") + "@bbc-sms\nDTSTART;TZID=" + timezone + ":" + start + "\nDTEND;TZID=" + timezone + ":" + end + "\n" + rule + "\nSUMMARY:" + summary + "\nEND:VEVENT";
            }, TenantContext.get(), versionId);
        return "BEGIN:VCALENDAR\nVERSION:2.0\nPRODID:-//BBC SMS//Timetable//EN\nX-WR-TIMEZONE:" + timezone + "\n" + String.join("\n", events) + "\nEND:VCALENDAR\n";
    }

    public UUID currentDraftVersion(UUID sessionId) {
        return jdbc.query("SELECT id FROM timetable_version WHERE school_id=? AND academic_session_id=? AND status='DRAFT' ORDER BY version_no DESC LIMIT 1",rs->rs.next()?rs.getObject(1,UUID.class):null,TenantContext.get(),sessionId);
    }

    private UUID cloneDraftSuccessor(Map<String, Object> sourceVersion, UUID sourceId, UUID sessionId) {
        UUID school = TenantContext.get();
        Integer nextValue = jdbc.queryForObject("""
                SELECT coalesce(max(version_no),0)+1
                  FROM timetable_version
                 WHERE school_id=? AND academic_session_id=?
                """, Integer.class, school, sessionId);
        int next = nextValue == null ? 1 : nextValue;
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO timetable_version
                    (id,school_id,academic_session_id,version_no,status,effective_from,effective_to,
                     timezone,copied_from_version_id)
                VALUES (?,?,?,?,'DRAFT',?,?,?,?)
                """, id, school, sessionId, next, localDate(sourceVersion.get("effective_from")),
                localDate(sourceVersion.get("effective_to")), normalizeTimezone((String) sourceVersion.get("timezone")),
                sourceId);
        jdbc.update("""
                INSERT INTO timetable_slot
                    (id,school_id,class_id,academic_session_id,day_idx,slot_idx,subject_code,teacher_id,room,
                     assignment_id,assignment_version,timetable_version_id)
                SELECT gen_random_uuid(),school_id,class_id,academic_session_id,day_idx,slot_idx,subject_code,
                       teacher_id,room,assignment_id,assignment_version,?
                  FROM timetable_slot
                 WHERE school_id=? AND timetable_version_id=?
                """, id, school, sourceId);
        return id;
    }

    private void assertNoVersionConflicts(UUID school, UUID versionId) {
        Integer teacherConflicts = jdbc.queryForObject("""
                SELECT count(*) FROM (
                    SELECT day_idx,slot_idx,teacher_id FROM timetable_slot
                     WHERE school_id=? AND timetable_version_id=? AND teacher_id IS NOT NULL
                     GROUP BY day_idx,slot_idx,teacher_id HAVING count(*)>1
                ) x
                """, Integer.class, school, versionId);
        Integer roomConflicts = jdbc.queryForObject("""
                SELECT count(*) FROM (
                    SELECT day_idx,slot_idx,lower(btrim(room)) room FROM timetable_slot
                     WHERE school_id=? AND timetable_version_id=? AND room IS NOT NULL AND btrim(room)<>''
                     GROUP BY day_idx,slot_idx,lower(btrim(room)) HAVING count(*)>1
                ) x
                """, Integer.class, school, versionId);
        List<String> blockers = new ArrayList<>();
        if (teacherConflicts != null && teacherConflicts > 0) blockers.add("TEACHER_DOUBLE_BOOKED");
        if (roomConflicts != null && roomConflicts > 0) blockers.add("ROOM_DOUBLE_BOOKED");
        if (!blockers.isEmpty()) {
            throw ApiException.blockers("TIMETABLE_CONFLICTS",
                    "Le planning de cette classe entre en conflit avec un planning déjà publié.", blockers);
        }
    }

    /** Create one new draft from the effective published version when a legacy class editor first writes. */
    @Transactional
    public UUID ensureDraftVersion(UUID sessionId) {
        UUID existing = currentDraftVersion(sessionId);
        if (existing != null) return existing;
        Map<String,Object> source = jdbc.query("SELECT id,effective_from,effective_to,timezone FROM timetable_version WHERE school_id=? AND academic_session_id=? AND status='PUBLISHED' ORDER BY version_no DESC LIMIT 1", rs -> {
            if (!rs.next()) return null;
            Map<String,Object> row = new HashMap<>();
            row.put("id", rs.getObject("id", UUID.class));
            row.put("effective_from", rs.getObject("effective_from", LocalDate.class));
            row.put("effective_to", rs.getObject("effective_to", LocalDate.class));
            row.put("timezone", rs.getString("timezone"));
            return row;
        }, TenantContext.get(), sessionId);
        if (source != null) return create(new TimetableVersionUpsert(sessionId, localDate(source.get("effective_from")), localDate(source.get("effective_to")), (String)source.get("timezone"), (UUID)source.get("id"), "Legacy class editor opened a new draft version.")).id();
        Map<String,Object> session = jdbc.queryForMap("SELECT start_date,end_date,timezone FROM academic_session WHERE id=? AND school_id=?", sessionId, TenantContext.get());
        return create(new TimetableVersionUpsert(sessionId, localDate(session.get("start_date")), localDate(session.get("end_date")), (String)session.get("timezone"), null, "Initial timetable draft version.")).id();
    }

    /** Class-level compatibility editor uses a published snapshot for locked classes and the newest draft for editable classes. */
    public UUID versionForClass(UUID sessionId, UUID classId) {
        String status = jdbc.query("SELECT status FROM timetable_class_config WHERE school_id=? AND academic_session_id=? AND class_id=?", rs -> rs.next() ? rs.getString(1) : "DRAFT", TenantContext.get(), sessionId, classId);
        String desired = "PUBLISHED".equals(status) ? "PUBLISHED" : "DRAFT";
        return jdbc.query("SELECT id FROM timetable_version WHERE school_id=? AND academic_session_id=? AND status=? ORDER BY version_no DESC LIMIT 1", rs -> rs.next() ? rs.getObject(1, UUID.class) : null, TenantContext.get(), sessionId, desired);
    }

    /** Validate resources before a draft slot is written. */
    @Transactional(readOnly = true)
    public void assertSlotResourcesAvailable(UUID sessionId, UUID versionId, UUID classId, UUID teacherId,
                                              int dayIdx, int slotIdx, String room) {
        UUID school = TenantContext.get();
        Integer teacherBlocked = jdbc.queryForObject("SELECT count(*) FROM timetable_teacher_availability WHERE school_id=? AND employee_id=? AND day_idx=? AND slot_idx=? AND available=false",
                Integer.class, school, teacherId, dayIdx, slotIdx);
        if (teacherBlocked != null && teacherBlocked > 0) {
            throw ApiException.conflict("TIMETABLE_TEACHER_UNAVAILABLE",
                    "L'enseignant canonique n'est pas disponible sur cette période.",
                    List.of(Map.of("resourceType", "TEACHER", "teacherId", teacherId,
                            "dayIdx", dayIdx, "slotIdx", slotIdx,
                            "repair", "Choose another period or update teacher availability.")));
        }
        if (room == null || room.isBlank()) return;
        Map<String, Object> roomRow = jdbc.query("""
                SELECT r.id,r.active,r.capacity,coalesce(count(e.student_id),0) AS student_count,
                       exists(SELECT 1 FROM timetable_room_availability a
                               WHERE a.room_id=r.id AND a.day_idx=? AND a.slot_idx=? AND a.available=false) AS unavailable
                  FROM timetable_room r
                  LEFT JOIN student_enrollment e ON e.school_id=r.school_id AND e.academic_session_id=?
                   AND e.school_class_id=? AND e.status='ACTIVE'
                 WHERE r.school_id=? AND lower(btrim(r.code))=lower(btrim(?))
                 GROUP BY r.id,r.active,r.capacity
                """, rs -> {
                    if (!rs.next()) return null;
                    Map<String, Object> value = new LinkedHashMap<>();
                    value.put("id", rs.getObject("id", UUID.class)); value.put("active", rs.getBoolean("active"));
                    value.put("capacity", rs.getObject("capacity", Integer.class)); value.put("studentCount", rs.getLong("student_count"));
                    value.put("unavailable", rs.getBoolean("unavailable"));
                    return value;
                },
                dayIdx, slotIdx, sessionId, classId, school, room);
        if (roomRow == null) return;
        if (!Boolean.TRUE.equals(roomRow.get("active")) || Boolean.TRUE.equals(roomRow.get("unavailable"))) {
            throw ApiException.conflict("TIMETABLE_ROOM_UNAVAILABLE",
                    "La salle sélectionnée n'est pas disponible sur cette période.",
                    List.of(Map.of("resourceType", "ROOM", "room", room.trim(), "dayIdx", dayIdx,
                            "slotIdx", slotIdx, "repair", "Choose another room or update room availability.")));
        }
        Integer capacity = (Integer) roomRow.get("capacity");
        long students = ((Number) roomRow.get("studentCount")).longValue();
        if (capacity != null && students > capacity) {
            throw ApiException.conflict("TIMETABLE_ROOM_CAPACITY",
                    "La capacité de la salle est inférieure à l'effectif de la classe.",
                    List.of(Map.of("resourceType", "ROOM", "room", room.trim(), "capacity", capacity,
                            "studentCount", students, "repair", "Choose a larger room.")));
        }
    }

    private List<Map<String, Object>> resourceBlockers(UUID sessionId, UUID versionId) {
        return resourceBlockers(sessionId, versionId, null);
    }

    private List<Map<String, Object>> resourceBlockers(UUID sessionId, UUID versionId, UUID classId) {
        UUID school = TenantContext.get();
        List<Map<String, Object>> result = new ArrayList<>();
        jdbc.query("""
                SELECT s.id,c.id AS class_id,c.name,s.subject_code,s.day_idx,s.slot_idx,btrim(s.room) AS room,
                       r.active,a.id AS unavailable_id,r.capacity,count(DISTINCT e.student_id) AS student_count
                  FROM timetable_slot s
                  JOIN school_class c ON c.id=s.class_id
                  LEFT JOIN timetable_room r ON r.school_id=s.school_id AND lower(btrim(r.code))=lower(btrim(s.room))
                  LEFT JOIN timetable_room_availability a ON a.room_id=r.id AND a.day_idx=s.day_idx AND a.slot_idx=s.slot_idx AND a.available=false
                  LEFT JOIN student_enrollment e ON e.school_id=s.school_id AND e.academic_session_id=?
                   AND e.school_class_id=s.class_id AND e.status='ACTIVE'
                 WHERE s.school_id=? AND s.timetable_version_id=?
                   AND (CAST(? AS uuid) IS NULL OR s.class_id=?)
                   AND s.room IS NOT NULL AND btrim(s.room)<>''
                 GROUP BY s.id,c.id,c.name,s.subject_code,s.day_idx,s.slot_idx,s.room,r.id,r.active,a.id,r.capacity
                """, rs -> {
            UUID roomId = rs.getObject("id", UUID.class);
            boolean roomKnown = rs.getObject("active") != null;
            boolean active = roomKnown && rs.getBoolean("active");
            boolean unavailable = rs.getObject("unavailable_id") != null;
            Integer capacity = rs.getObject("capacity", Integer.class);
            long students = rs.getLong("student_count");
            if (roomKnown && active && !unavailable && (capacity == null || students <= capacity)) return;
            Map<String, Object> blocker = new LinkedHashMap<>();
            blocker.put("resourceType", "ROOM"); blocker.put("slotId", rs.getObject("id", UUID.class));
            blocker.put("classId", rs.getObject("class_id", UUID.class)); blocker.put("class", rs.getString("name"));
            blocker.put("room", rs.getString("room")); blocker.put("dayIdx", rs.getInt("day_idx")); blocker.put("slotIdx", rs.getInt("slot_idx"));
            blocker.put("capacity", capacity); blocker.put("studentCount", students);
            blocker.put("code", !roomKnown ? "ROOM_NOT_REGISTERED" : !active ? "ROOM_INACTIVE" : unavailable ? "ROOM_UNAVAILABLE" : "ROOM_CAPACITY_EXCEEDED");
            blocker.put("repair", !roomKnown ? "Register this room/resource before publishing." : capacity != null && students > capacity ? "Choose a larger room." : "Update room availability or choose another room.");
            result.add(blocker);
        }, sessionId, school, versionId, classId, classId);
        jdbc.query("""
                SELECT s.id,c.id AS class_id,c.name,s.subject_code,s.day_idx,s.slot_idx,s.teacher_id,e.name AS teacher_name
                  FROM timetable_slot s
                  JOIN school_class c ON c.id=s.class_id
                  JOIN timetable_teacher_availability a ON a.school_id=s.school_id AND a.employee_id=s.teacher_id
                   AND a.day_idx=s.day_idx AND a.slot_idx=s.slot_idx AND a.available=false
                  LEFT JOIN employee e ON e.id=s.teacher_id
                 WHERE s.school_id=? AND s.timetable_version_id=?
                   AND (CAST(? AS uuid) IS NULL OR s.class_id=?)
                """, rs -> {
            Map<String, Object> blocker = new LinkedHashMap<>();
            blocker.put("resourceType", "TEACHER"); blocker.put("slotId", rs.getObject("id", UUID.class));
            blocker.put("classId", rs.getObject("class_id", UUID.class)); blocker.put("class", rs.getString("name"));
            blocker.put("subjectCode", rs.getString("subject_code")); blocker.put("teacherId", rs.getObject("teacher_id", UUID.class));
            blocker.put("teacher", rs.getString("teacher_name")); blocker.put("dayIdx", rs.getInt("day_idx")); blocker.put("slotIdx", rs.getInt("slot_idx"));
            blocker.put("code", "TEACHER_UNAVAILABLE"); blocker.put("repair", "Choose another period or update teacher availability.");
            result.add(blocker);
        }, school, versionId, classId, classId);
        jdbc.query("""
                SELECT s.teacher_id,e.name,s.day_idx,w.max_slots_per_day,count(*) AS slot_count
                  FROM timetable_slot s
                  JOIN timetable_version v ON v.id=s.timetable_version_id AND v.school_id=s.school_id
                  JOIN employee e ON e.id=s.teacher_id
                  JOIN LATERAL (SELECT x.max_slots_per_day
                                  FROM timetable_teacher_workload_policy x
                                 WHERE x.school_id=s.school_id AND x.employee_id=s.teacher_id
                                   AND x.effective_from<=v.effective_from
                                   AND (x.effective_to IS NULL OR x.effective_to>=v.effective_from)
                                 ORDER BY x.effective_from DESC LIMIT 1) w ON w.max_slots_per_day IS NOT NULL
                 WHERE s.school_id=? AND s.timetable_version_id=?
                 GROUP BY s.teacher_id,e.name,s.day_idx,w.max_slots_per_day
                HAVING count(*)>w.max_slots_per_day
                """, rs -> {
            Map<String, Object> blocker = new LinkedHashMap<>();
            blocker.put("resourceType", "TEACHER_WORKLOAD"); blocker.put("teacherId", rs.getObject(1, UUID.class));
            blocker.put("teacher", rs.getString(2)); blocker.put("dayIdx", rs.getInt(3));
            blocker.put("limit", rs.getInt(4)); blocker.put("slotCount", rs.getLong(5));
            blocker.put("code", "TEACHER_DAILY_WORKLOAD_EXCEEDED"); blocker.put("repair", "Adjust the workload policy or move a timetable slot.");
            result.add(blocker);
        }, school, versionId);
        jdbc.query("""
                SELECT s.teacher_id,e.name,w.max_slots_per_week,count(*) AS slot_count
                  FROM timetable_slot s
                  JOIN timetable_version v ON v.id=s.timetable_version_id AND v.school_id=s.school_id
                  JOIN employee e ON e.id=s.teacher_id
                  JOIN LATERAL (SELECT x.max_slots_per_week
                                  FROM timetable_teacher_workload_policy x
                                 WHERE x.school_id=s.school_id AND x.employee_id=s.teacher_id
                                   AND x.effective_from<=v.effective_from
                                   AND (x.effective_to IS NULL OR x.effective_to>=v.effective_from)
                                 ORDER BY x.effective_from DESC LIMIT 1) w ON w.max_slots_per_week IS NOT NULL
                 WHERE s.school_id=? AND s.timetable_version_id=?
                 GROUP BY s.teacher_id,e.name,w.max_slots_per_week
                HAVING count(*)>w.max_slots_per_week
                """, rs -> {
            Map<String, Object> blocker = new LinkedHashMap<>();
            blocker.put("resourceType", "TEACHER_WORKLOAD"); blocker.put("teacherId", rs.getObject(1, UUID.class));
            blocker.put("teacher", rs.getString(2)); blocker.put("limit", rs.getInt(3)); blocker.put("slotCount", rs.getLong(4));
            blocker.put("code", "TEACHER_WEEKLY_WORKLOAD_EXCEEDED"); blocker.put("repair", "Adjust the workload policy or move a timetable slot.");
            result.add(blocker);
        }, school, versionId);
        jdbc.query("""
                SELECT s.id,c.id,c.name,s.subject_code,s.teacher_id,e.name,r.qualification_code
                  FROM timetable_slot s
                  JOIN timetable_version v ON v.id=s.timetable_version_id AND v.school_id=s.school_id
                  JOIN school_class c ON c.id=s.class_id
                  JOIN employee e ON e.id=s.teacher_id
                  JOIN timetable_subject_qualification_requirement r
                    ON r.school_id=s.school_id AND r.academic_session_id=v.academic_session_id
                   AND upper(r.subject_code)=upper(s.subject_code)
                   AND r.effective_from<=v.effective_from
                   AND (r.effective_to IS NULL OR r.effective_to>=v.effective_from)
                  LEFT JOIN LATERAL (SELECT q.id FROM timetable_teacher_qualification q
                                      WHERE q.school_id=s.school_id AND q.employee_id=s.teacher_id
                                        AND upper(q.qualification_code)=upper(r.qualification_code)
                                        AND q.valid_from<=v.effective_from
                                        AND (q.valid_to IS NULL OR q.valid_to>=v.effective_from)
                                      LIMIT 1) qualified ON true
                 WHERE s.school_id=? AND s.timetable_version_id=?
                   AND (CAST(? AS uuid) IS NULL OR s.class_id=?) AND qualified.id IS NULL
                """, rs -> {
            Map<String, Object> blocker = new LinkedHashMap<>();
            blocker.put("resourceType", "TEACHER_QUALIFICATION"); blocker.put("slotId", rs.getObject(1, UUID.class));
            blocker.put("classId", rs.getObject(2, UUID.class)); blocker.put("class", rs.getString(3));
            blocker.put("subjectCode", rs.getString(4)); blocker.put("teacherId", rs.getObject(5, UUID.class));
            blocker.put("teacher", rs.getString(6)); blocker.put("qualification", rs.getString(7));
            blocker.put("code", "TEACHER_QUALIFICATION_MISSING"); blocker.put("repair", "Add the qualification to the teacher or assign a qualified responsible teacher.");
            result.add(blocker);
        }, school, versionId, classId, classId);
        return result;
    }

    private List<TimetableExportRow> exportRows(UUID versionId) {
        return jdbc.query("""
                SELECT c.name,s.day_idx,s.slot_idx,s.subject_code,coalesce(s.published_teacher_id,s.teacher_id)::text,coalesce(s.room,'')
                  FROM timetable_slot s JOIN school_class c ON c.id=s.class_id
                 WHERE s.school_id=? AND s.timetable_version_id=? ORDER BY c.name,s.day_idx,s.slot_idx
                """, (rs, n) -> new TimetableExportRow(rs.getString(1), rs.getInt(2), rs.getInt(3),
                        rs.getString(4), rs.getString(5), rs.getString(6)), TenantContext.get(), versionId);
    }

    private static String xlsxRow(int row, List<String> values) {
        StringBuilder out = new StringBuilder("<row r=\"").append(row).append("\">");
        for (int i = 0; i < values.size(); i++) {
            String ref = columnName(i + 1) + row;
            out.append("<c r=\"").append(ref).append("\" t=\"inlineStr\"><is><t>")
                    .append(xml(values.get(i))).append("</t></is></c>");
        }
        return out.append("</row>").toString();
    }

    private static String columnName(int value) { StringBuilder result = new StringBuilder(); while (value > 0) { int rem = (value - 1) % 26; result.insert(0, (char) ('A' + rem)); value = (value - 1) / 26; } return result.toString(); }
    private static String xml(String value) { return value == null ? "" : value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&apos;"); }
    private static void zipEntry(java.util.zip.ZipOutputStream zip, String name, String value) throws Exception { java.util.zip.ZipEntry entry = new java.util.zip.ZipEntry(name); entry.setTime(0); zip.putNextEntry(entry); zip.write(value.getBytes(StandardCharsets.UTF_8)); zip.closeEntry(); }
    private static PDFont loadFont(PDDocument document, String path) {
        try {
            File file = new File(path);
            if (file.isFile()) return PDType0Font.load(document, file);
        } catch (Exception ignored) { }
        return path.toLowerCase(Locale.ROOT).contains("bold") ? PDType1Font.HELVETICA_BOLD : PDType1Font.HELVETICA;
    }
    private static String clipPdf(String value, int max) {
        String text = value == null ? "" : value;
        return text.length() <= max ? text : text.substring(0, Math.max(0, max - 1)) + "…";
    }
    private record TimetableExportRow(String className, int dayIdx, int slotIdx, String subjectCode, String teacherId, String room) {}

    /** Minimal row used for policy filtering before substitution DTOs are materialized. */
    private record SubstitutionScopeRow(UUID id, UUID academicSessionId, UUID timetableVersionId,
                                        LocalDate occurrenceDate, UUID classId, String subjectCode,
                                        int dayIdx, int slotIdx) {}

    private void validateAvailabilitySlot(int dayIdx, int slotIdx) {
        if (dayIdx < 0 || dayIdx > 6 || slotIdx < 0 || slotIdx > 15)
            throw ApiException.field(org.springframework.http.HttpStatus.BAD_REQUEST, "AVAILABILITY_SLOT_INVALID",
                    "Le jour ou la période de disponibilité est invalide.", "slotIdx", "Choose a valid school day and bell period.");
    }
    private void validateEffectiveRange(LocalDate from, LocalDate to, String code) {
        if (from == null || (to != null && to.isBefore(from)))
            throw ApiException.field(org.springframework.http.HttpStatus.BAD_REQUEST, code,
                    "La période d'effet est invalide.", "effectiveFrom", "The effective date range is invalid.");
    }
    private void ensureTeacher(UUID teacherId) {
        Integer count = jdbc.queryForObject("SELECT count(*) FROM employee WHERE id=? AND school_id=?", Integer.class, teacherId, TenantContext.get());
        if (count == null || count == 0) throw ApiException.notFound("Enseignant");
    }

    private SubstitutionView substitution(UUID id) { return jdbc.queryForObject("SELECT x.*,c.name,ot.name AS original_name,rt.name AS replacement_name FROM timetable_substitution x JOIN school_class c ON c.id=x.class_id LEFT JOIN employee ot ON ot.id=x.original_teacher_id LEFT JOIN employee rt ON rt.id=x.replacement_teacher_id WHERE x.id=? AND x.school_id=?",(rs,n)->new SubstitutionView(rs.getObject("id",UUID.class),rs.getObject("academic_session_id",UUID.class),rs.getObject("timetable_version_id",UUID.class),rs.getObject("occurrence_date",LocalDate.class),rs.getObject("class_id",UUID.class),rs.getString("name"),rs.getString("subject_code"),rs.getInt("day_idx"),rs.getInt("slot_idx"),rs.getObject("original_teacher_id",UUID.class),rs.getString("original_name"),rs.getObject("replacement_teacher_id",UUID.class),rs.getString("replacement_name"),rs.getString("action"),rs.getString("reason"),rs.getString("status"),rs.getLong("version")),id,TenantContext.get()); }

    private boolean substitutionVisible(SubstitutionView value) {
        return policy.decide("TIMETABLE_SUBSTITUTION_VIEW", substitutionContext(value)).allowed();
    }

    private boolean substitutionVisible(SubstitutionScopeRow value) {
        return policy.decide("TIMETABLE_SUBSTITUTION_VIEW", substitutionContext(value)).allowed();
    }

    private PolicyResourceContext substitutionContext(SubstitutionUpsert value) {
        UUID occurrenceId = publishedOccurrence(value.academicSessionId(), value.timetableVersionId(),
                value.occurrenceDate(), value.classId(), value.subjectCode(), value.dayIdx(), value.slotIdx());
        return new PolicyResourceContext(TenantContext.get(), value.academicSessionId(), value.occurrenceDate(),
                ParcoursContext.get(), value.classId(), clean(value.subjectCode()), null, occurrenceId, null,
                null, "P" + (value.slotIdx() + 1), null);
    }

    private PolicyResourceContext substitutionContext(SubstitutionView value) {
        UUID occurrenceId = publishedOccurrence(value.academicSessionId(), value.timetableVersionId(),
                value.occurrenceDate(), value.classId(), value.subjectCode(), value.dayIdx(), value.slotIdx());
        return new PolicyResourceContext(TenantContext.get(), value.academicSessionId(), value.occurrenceDate(),
                ParcoursContext.get(), value.classId(), clean(value.subjectCode()), null, occurrenceId, null,
                null, "P" + (value.slotIdx() + 1), null);
    }

    private PolicyResourceContext substitutionContext(SubstitutionScopeRow value) {
        UUID occurrenceId = publishedOccurrence(value.academicSessionId(), value.timetableVersionId(),
                value.occurrenceDate(), value.classId(), value.subjectCode(), value.dayIdx(), value.slotIdx());
        return new PolicyResourceContext(TenantContext.get(), value.academicSessionId(), value.occurrenceDate(),
                ParcoursContext.get(), value.classId(), clean(value.subjectCode()), null, occurrenceId, null,
                null, "P" + (value.slotIdx() + 1), null);
    }

    /** Resolve the exact published occurrence represented by a substitution. */
    private UUID publishedOccurrence(UUID academicSessionId, UUID timetableVersionId, LocalDate date,
                                     UUID classId, String subjectCode, int dayIdx, int slotIdx) {
        return jdbc.query("""
                SELECT s.id
                  FROM timetable_slot s
                  JOIN timetable_version v ON v.id=s.timetable_version_id
                   AND v.school_id=s.school_id AND v.academic_session_id=s.academic_session_id
                   AND v.status='PUBLISHED' AND v.effective_from<=?
                   AND (v.effective_to IS NULL OR v.effective_to>=?)
                 WHERE s.school_id=? AND s.academic_session_id=? AND s.class_id=?
                   AND s.day_idx=? AND s.slot_idx=?
                   AND (? IS NULL OR s.timetable_version_id=?)
                   AND (? IS NULL OR upper(coalesce(s.subject_code,''))=upper(?))
                 ORDER BY v.version_no DESC, s.id
                 LIMIT 1
                """, rs -> rs.next() ? rs.getObject(1, UUID.class) : null,
                date, date, TenantContext.get(), academicSessionId, classId, dayIdx, slotIdx,
                timetableVersionId, timetableVersionId, subjectCode, subjectCode);
    }

    private PolicyResourceContext schoolContext() {
        return new PolicyResourceContext(TenantContext.get(), null, LocalDate.now(), ParcoursContext.get(),
                null, null, null, null, null, null, null, null);
    }

    /** Header-only server context for exports; slot rows are not read before authorization. */
    private PolicyResourceContext versionContext(UUID versionId) {
        Map<String, Object> row = jdbc.queryForMap(
                "SELECT academic_session_id,effective_from FROM timetable_version WHERE id=? AND school_id=?",
                versionId, TenantContext.get());
        LocalDate effectiveDate = localDate(row.get("effective_from"));
        return new PolicyResourceContext(TenantContext.get(),
                (UUID) row.get("academic_session_id"),
                effectiveDate == null ? LocalDate.now() : effectiveDate,
                ParcoursContext.get(), null, null, null, null, null, null, null, null);
    }
    private void requireSession(UUID id){Integer n=jdbc.queryForObject("SELECT count(*) FROM academic_session WHERE id=? AND school_id=?",Integer.class,id,TenantContext.get());if(n==null||n==0)throw ApiException.notFound("Session académique");}
    private void ensureOwned(UUID id){Integer n=jdbc.queryForObject("SELECT count(*) FROM timetable_version WHERE id=? AND school_id=?",Integer.class,id,TenantContext.get());if(n==null||n==0)throw ApiException.notFound("Version du planning");}
    private void ensureRoom(UUID id){Integer n=jdbc.queryForObject("SELECT count(*) FROM timetable_room WHERE id=? AND school_id=?",Integer.class,id,TenantContext.get());if(n==null||n==0)throw ApiException.notFound("Salle");}
    private void validateVersionForSession(UUID versionId, UUID sessionId) {
        Integer n = jdbc.queryForObject("SELECT count(*) FROM timetable_version WHERE id=? AND school_id=? AND academic_session_id=?",
                Integer.class, versionId, TenantContext.get(), sessionId);
        if (n == null || n == 0) throw ApiException.badRequest("La version du planning n'appartient pas à cette session.");
    }
    private int count(String sql,Object... args){Integer n=jdbc.queryForObject(sql,Integer.class,args);return n==null?0:n;}
    private TimetableVersionView mapVersion(Map<String,Object> r){return new TimetableVersionView((UUID)r.get("id"),(UUID)r.get("academic_session_id"),(Integer)r.get("version_no"),(String)r.get("status"),localDate(r.get("effective_from")),localDate(r.get("effective_to")),(String)r.get("timezone"),(UUID)r.get("copied_from_version_id"),((Number)r.get("slot_count")).intValue(),((Number)r.get("class_count")).intValue(),((Number)r.get("version")).longValue());}
    private static LocalDate localDate(Object value){
        if (value == null) return null;
        if (value instanceof LocalDate date) return date;
        if (value instanceof java.sql.Date date) return date.toLocalDate();
        if (value instanceof java.util.Date date) return date.toInstant().atZone(java.time.ZoneOffset.UTC).toLocalDate();
        return LocalDate.parse(value.toString());
    }
    private static void validateDates(LocalDate from,LocalDate to){if(to!=null&&to.isBefore(from))throw ApiException.badRequest("La période d'effet du planning est invalide");}
    private static String normalizeTimezone(String value){return value==null||value.isBlank()?"Africa/Douala":value.trim();}
    private static String blank(String v,String d){return v==null||v.isBlank()?d:v.trim();}
    private static String clean(String v){return v==null||v.isBlank()?null:v.trim().toUpperCase(Locale.ROOT);}
    private static String cleanReason(String v){return v==null||v.isBlank()?null:v.trim();}
    private static String csv(String v){if(v==null)return "";return "\""+v.replace("\"","\"\"")+"\"";}
    private static String icalEscape(String v){return v==null?"":v.replace("\\","\\\\").replace(";","\\;").replace(",","\\,").replace("\n","\\n");}
    private UUID currentUser(){var a=SecurityContextHolder.getContext().getAuthentication();return a!=null&&a.getPrincipal() instanceof AppUserPrincipal p?p.userId():null;}
}

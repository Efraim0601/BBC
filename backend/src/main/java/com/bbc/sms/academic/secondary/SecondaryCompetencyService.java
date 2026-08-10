package com.bbc.sms.academic.secondary;

import com.bbc.sms.platform.common.ApiException;
import com.bbc.sms.platform.tenant.TenantContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;

import static com.bbc.sms.academic.secondary.SecondaryCompetencyDtos.*;

/** Versioned, session-scoped competency evidence for secondary report cards. */
@Service
public class SecondaryCompetencyService {
    private final JdbcTemplate jdbc;

    public SecondaryCompetencyService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Transactional(readOnly = true)
    public List<ModelView> list(UUID reportingPeriodId, UUID classId, UUID subjectId, String locale) {
        String normalized = locale == null || locale.isBlank() ? "fr" : locale.trim().toLowerCase(Locale.ROOT);
        return jdbc.query("""
                SELECT id,academic_session_id,reporting_period_id,class_id,subject_id,locale,
                       name,version,status,source
                  FROM secondary_competency_model
                 WHERE school_id=? AND reporting_period_id=? AND class_id=? AND subject_id=?
                   AND locale=?
                 ORDER BY version DESC
                """, (rs, n) -> model(rs.getObject("id", UUID.class),
                        rs.getObject("academic_session_id", UUID.class),
                        rs.getObject("reporting_period_id", UUID.class),
                        rs.getObject("class_id", UUID.class), rs.getObject("subject_id", UUID.class),
                        rs.getString("locale"), rs.getString("name"), rs.getInt("version"),
                        rs.getString("status"), rs.getString("source")),
                TenantContext.get(), reportingPeriodId, classId, subjectId, normalized);
    }

    @Transactional(readOnly = true)
    public ModelView get(UUID id) {
        ModelView found = jdbc.query("""
                SELECT id,academic_session_id,reporting_period_id,class_id,subject_id,locale,
                       name,version,status,source
                  FROM secondary_competency_model WHERE school_id=? AND id=?
                """, rs -> rs.next() ? model(rs.getObject("id", UUID.class),
                        rs.getObject("academic_session_id", UUID.class),
                        rs.getObject("reporting_period_id", UUID.class),
                        rs.getObject("class_id", UUID.class), rs.getObject("subject_id", UUID.class),
                        rs.getString("locale"), rs.getString("name"), rs.getInt("version"),
                        rs.getString("status"), rs.getString("source")) : null,
                TenantContext.get(), id);
        if (found == null) throw ApiException.notFound("Compétence secondaire");
        return found;
    }

    @Transactional
    public ModelView create(ModelRequest request) {
        UUID school = TenantContext.get();
        String locale = normalizeLocale(request.locale());
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO secondary_competency_model
                    (id,school_id,academic_session_id,reporting_period_id,class_id,subject_id,locale,name,version,status,source)
                VALUES (?,?,?,?,?,?,?, ?,1,'DRAFT','MANUAL')
                """, id, school, request.academicSessionId(), request.reportingPeriodId(),
                request.classId(), request.subjectId(), locale, request.name().trim());
        insertCompetencies(school, id, request.competencies());
        return get(id);
    }

    @Transactional
    public ModelView copy(UUID modelId, String reason) {
        ModelView previous = get(modelId);
        if (reason == null || reason.isBlank()) throw ApiException.badRequest("Le motif de nouvelle version est obligatoire");
        UUID school = TenantContext.get();
        int next = jdbc.queryForObject("SELECT coalesce(max(version),0)+1 FROM secondary_competency_model WHERE school_id=? AND academic_session_id=? AND reporting_period_id=? AND class_id=? AND subject_id=? AND locale=?",
                Integer.class, school, previous.academicSessionId(), previous.reportingPeriodId(), previous.classId(), previous.subjectId(), previous.locale());
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO secondary_competency_model (id,school_id,academic_session_id,reporting_period_id,class_id,subject_id,locale,name,version,status,source) VALUES (?,?,?,?,?,?,?, ?,?,'DRAFT','MANUAL')",
                id, school, previous.academicSessionId(), previous.reportingPeriodId(), previous.classId(), previous.subjectId(), previous.locale(), previous.name(), next);
        insertCompetencies(school, id, previous.competencies().stream().map(x -> new CompetencyInput(x.code(), x.description(), x.maxScore(), x.displayOrder())).toList());
        return get(id);
    }

    @Transactional
    public ModelView publish(UUID id) {
        ModelView model = get(id);
        jdbc.update("UPDATE secondary_competency_model SET status='RETIRED' WHERE school_id=? AND academic_session_id=? AND reporting_period_id=? AND class_id=? AND subject_id=? AND locale=? AND status='PUBLISHED'",
                TenantContext.get(), model.academicSessionId(), model.reportingPeriodId(), model.classId(), model.subjectId(), model.locale());
        jdbc.update("UPDATE secondary_competency_model SET status='PUBLISHED',published_at=now() WHERE school_id=? AND id=?", TenantContext.get(), id);
        return get(id);
    }

    @Transactional
    public MarkView saveMark(MarkRequest request) {
        UUID school = TenantContext.get();
        CompetencyRow competency = jdbc.query("SELECT max_score FROM secondary_competency WHERE school_id=? AND id=? AND model_id=?",
                rs -> rs.next() ? new CompetencyRow(rs.getBigDecimal(1)) : null, school, request.competencyId(), request.modelId());
        if (competency == null) throw ApiException.notFound("Compétence secondaire");
        String status = normalizeStatus(request.valueStatus(), request.mark());
        if (request.mark() != null && (request.mark().compareTo(BigDecimal.ZERO) < 0 || request.mark().compareTo(competency.maxScore()) > 0))
            throw ApiException.badRequest("La note dépasse le barème de la compétence");
        MarkView current = findMark(request.modelId(), request.competencyId(), request.reportingPeriodId(), request.studentId());
        if (current != null && request.version() != null && request.version() != current.version()) throw ApiException.conflict("La note a changé entre-temps");
        if (current == null) {
            UUID id = UUID.randomUUID();
            jdbc.update("INSERT INTO secondary_competency_mark (id,school_id,model_id,competency_id,reporting_period_id,student_id,enrollment_id,teacher_id,mark,value_status) VALUES (?,?,?,?,?,?,?,?,?,?)",
                    id, school, request.modelId(), request.competencyId(), request.reportingPeriodId(), request.studentId(), request.enrollmentId(), request.teacherId(), request.mark(), status);
        } else {
            jdbc.update("UPDATE secondary_competency_mark SET mark=?,value_status=?,enrollment_id=?,teacher_id=?,version=version+1,updated_at=now() WHERE school_id=? AND id=?",
                    request.mark(), status, request.enrollmentId(), request.teacherId(), school, current.id());
        }
        return findMark(request.modelId(), request.competencyId(), request.reportingPeriodId(), request.studentId());
    }

    @Transactional
    public List<MarkView> importMarks(ImportRequest request) {
        ModelView model = get(request.modelId());
        Map<String, UUID> competencies = model.competencies().stream().collect(java.util.stream.Collectors.toMap(x -> x.code().toUpperCase(Locale.ROOT), CompetencyView::id));
        List<MarkView> result = new ArrayList<>();
        for (ImportRow row : request.rows()) {
            UUID competencyId = competencies.get(row.competencyCode().trim().toUpperCase(Locale.ROOT));
            if (competencyId == null) throw ApiException.badRequest("Compétence inconnue : " + row.competencyCode());
            result.add(saveMark(new MarkRequest(request.modelId(), competencyId, request.reportingPeriodId(), row.studentId(), row.enrollmentId(), row.teacherId(), row.mark(), row.valueStatus(), null)));
        }
        jdbc.update("UPDATE secondary_competency_model SET source='IMPORT' WHERE school_id=? AND id=?", TenantContext.get(), request.modelId());
        return result;
    }

    @Transactional(readOnly = true)
    public List<MarkView> marks(UUID modelId, UUID reportingPeriodId, UUID studentId) {
        // PostgreSQL cannot infer the type of a nullable JDBC placeholder in the optional
        // student filter.  Cast the first placeholder explicitly so the all-students view
        // (the normal Settings workflow) is a valid prepared statement as well.
        return jdbc.query("SELECT id,model_id,competency_id,reporting_period_id,student_id,enrollment_id,teacher_id,mark,value_status,version FROM secondary_competency_mark WHERE school_id=? AND model_id=? AND reporting_period_id=? AND (CAST(? AS uuid) IS NULL OR student_id=?) ORDER BY student_id,competency_id",
                (rs,n) -> mark(rs), TenantContext.get(), modelId, reportingPeriodId, studentId, studentId);
    }

    private ModelView model(UUID id, UUID session, UUID period, UUID clazz, UUID subject, String locale, String name, int version, String status, String source) {
        List<CompetencyView> competencies = jdbc.query("SELECT id,model_id,code,description,max_score,display_order,active FROM secondary_competency WHERE school_id=? AND model_id=? ORDER BY display_order,code",
                (rs,n) -> new CompetencyView(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class), rs.getString(3), rs.getString(4), rs.getBigDecimal(5), rs.getInt(6), rs.getBoolean(7)), TenantContext.get(), id);
        return new ModelView(id, session, period, clazz, subject, locale, name, version, status, source, competencies);
    }

    private void insertCompetencies(UUID school, UUID model, List<CompetencyInput> inputs) {
        Set<String> codes = new HashSet<>();
        int order = 1;
        for (CompetencyInput input : inputs) {
            String code = input.code().trim();
            if (!codes.add(code.toUpperCase(Locale.ROOT))) throw ApiException.badRequest("Codes de compétence dupliqués");
            jdbc.update("INSERT INTO secondary_competency (school_id,model_id,code,description,max_score,display_order) VALUES (?,?,?,?,?,?)",
                    school, model, code, input.description().trim(), input.maxScore(), input.displayOrder() > 0 ? input.displayOrder() : order++);
        }
        if (inputs.isEmpty()) throw ApiException.badRequest("Au moins une compétence est requise");
    }

    private MarkView findMark(UUID model, UUID competency, UUID period, UUID student) {
        return jdbc.query("SELECT id,model_id,competency_id,reporting_period_id,student_id,enrollment_id,teacher_id,mark,value_status,version FROM secondary_competency_mark WHERE school_id=? AND model_id=? AND competency_id=? AND reporting_period_id=? AND student_id=?",
                rs -> rs.next() ? mark(rs) : null, TenantContext.get(), model, competency, period, student);
    }
    private static MarkView mark(java.sql.ResultSet rs) throws java.sql.SQLException { return new MarkView(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class), rs.getObject(3, UUID.class), rs.getObject(4, UUID.class), rs.getObject(5, UUID.class), rs.getObject(6, UUID.class), rs.getObject(7, UUID.class), rs.getBigDecimal(8), rs.getString(9), rs.getLong(10)); }
    private record CompetencyRow(BigDecimal maxScore) {}
    private static String normalizeLocale(String raw) { String x = raw.trim().toLowerCase(Locale.ROOT); if (!x.matches("fr|en|fr-[a-z]{2}|en-[a-z]{2}")) throw ApiException.badRequest("Locale de compétence invalide"); return x; }
    private static String normalizeStatus(String value, BigDecimal mark) { String x = value == null || value.isBlank() ? (mark == null ? "MISSING" : "SCORED") : value.trim().toUpperCase(Locale.ROOT); if (!Set.of("SCORED","ABSENT","EXEMPT","MISSING").contains(x)) throw ApiException.badRequest("Statut de note invalide"); return x; }
}

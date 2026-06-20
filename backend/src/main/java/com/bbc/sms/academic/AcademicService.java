package com.bbc.sms.academic;

import com.bbc.sms.academic.dto.AcademicDtos.*;
import com.bbc.sms.platform.common.ApiException;
import com.bbc.sms.platform.tenant.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
public class AcademicService {

    private static final BigDecimal MIN_MARK = BigDecimal.ZERO;
    private static final BigDecimal MAX_MARK = new BigDecimal("20");

    private final GradeRepository repo;

    public AcademicService(GradeRepository repo) { this.repo = repo; }

    @Transactional(readOnly = true)
    public List<GradeView> listForStudent(UUID studentId) {
        UUID schoolId = TenantContext.get();
        return repo.findBySchoolIdAndStudentId(schoolId, studentId).stream()
                .map(this::toView)
                .toList();
    }

    @Transactional
    public GradeView upsert(GradeUpsert in) {
        UUID schoolId = TenantContext.get();
        if (in.mark().compareTo(MIN_MARK) < 0 || in.mark().compareTo(MAX_MARK) > 0) {
            throw ApiException.badRequest("La note doit être comprise entre 0 et 20");
        }
        Grade g = repo.findBySchoolIdAndStudentIdAndSubjectCodeAndSequence(
                        schoolId, in.studentId(), in.subjectCode(), in.sequence())
                .orElseGet(() -> {
                    Grade fresh = new Grade();
                    fresh.setSchoolId(schoolId);
                    fresh.setStudentId(in.studentId());
                    fresh.setSubjectCode(in.subjectCode());
                    fresh.setSequence(in.sequence());
                    return fresh;
                });
        g.setMark(in.mark());
        return toView(repo.save(g));
    }

    private GradeView toView(Grade g) {
        return new GradeView(g.getId(), g.getStudentId(), g.getSubjectCode(), g.getSequence(), g.getMark());
    }
}

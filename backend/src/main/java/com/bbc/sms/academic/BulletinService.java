package com.bbc.sms.academic;

import com.bbc.sms.academic.dto.BulletinDtos.*;
import com.bbc.sms.platform.common.ApiException;
import com.bbc.sms.platform.tenant.TenantContext;
import com.bbc.sms.student.Student;
import com.bbc.sms.student.StudentRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class BulletinService {

    private final SubjectRepository subjectRepo;
    private final GradeRepository gradeRepo;
    private final StudentRepository studentRepo;
    private final BulletinValidationRepository validationRepo;
    private final JdbcTemplate jdbc;

    public BulletinService(SubjectRepository subjectRepo,
                           GradeRepository gradeRepo,
                           StudentRepository studentRepo,
                           BulletinValidationRepository validationRepo,
                           JdbcTemplate jdbc) {
        this.subjectRepo = subjectRepo;
        this.gradeRepo = gradeRepo;
        this.studentRepo = studentRepo;
        this.validationRepo = validationRepo;
        this.jdbc = jdbc;
    }

    /**
     * Weighted average for a student over a sequence: Σ(mark*coef) / Σ(coef),
     * counting only subjects for which the student has a recorded grade.
     * Returns BigDecimal.ZERO when the student has no grades for the sequence.
     */
    private BigDecimal average(UUID schoolId, List<Subject> subjects, UUID studentId, int sequence) {
        BigDecimal weightedSum = BigDecimal.ZERO;
        int coefSum = 0;
        for (Subject s : subjects) {
            var grade = gradeRepo.findBySchoolIdAndStudentIdAndSubjectCodeAndSequence(
                    schoolId, studentId, s.getCode(), sequence);
            if (grade.isEmpty()) continue;
            weightedSum = weightedSum.add(grade.get().getMark().multiply(BigDecimal.valueOf(s.getCoef())));
            coefSum += s.getCoef();
        }
        if (coefSum == 0) return BigDecimal.ZERO;
        return weightedSum.divide(BigDecimal.valueOf(coefSum), 2, RoundingMode.HALF_UP);
    }

    /** True when the student has at least one grade for the sequence. */
    private boolean hasAnyGrade(UUID schoolId, List<Subject> subjects, UUID studentId, int sequence) {
        for (Subject s : subjects) {
            if (gradeRepo.findBySchoolIdAndStudentIdAndSubjectCodeAndSequence(
                    schoolId, studentId, s.getCode(), sequence).isPresent()) {
                return true;
            }
        }
        return false;
    }

    private String studentName(Student s) {
        return s.getLastName().toUpperCase() + " " + s.getFirstName();
    }

    private String subjectLabel(Subject s) {
        Map<String, String> label = s.getLabel();
        if (label == null || label.isEmpty()) return s.getCode();
        String fr = label.get("fr");
        if (fr != null && !fr.isBlank()) return fr;
        String en = label.get("en");
        if (en != null && !en.isBlank()) return en;
        return label.values().iterator().next();
    }

    @Transactional(readOnly = true)
    public BulletinView bulletin(UUID studentId, int sequence) {
        UUID schoolId = TenantContext.get();

        Student student = studentRepo.findByIdAndSchoolId(studentId, schoolId)
                .orElseThrow(() -> ApiException.notFound("Élève"));

        List<Subject> subjects = subjectRepo.findBySchoolIdOrderByCode(schoolId);

        // 1. Lines (only subjects where a grade exists) + this student's average.
        List<BulletinLine> lines = new ArrayList<>();
        for (Subject s : subjects) {
            var grade = gradeRepo.findBySchoolIdAndStudentIdAndSubjectCodeAndSequence(
                    schoolId, studentId, s.getCode(), sequence);
            if (grade.isEmpty()) continue;
            BigDecimal mark = grade.get().getMark();
            BigDecimal weighted = mark.multiply(BigDecimal.valueOf(s.getCoef()))
                    .setScale(2, RoundingMode.HALF_UP);
            lines.add(new BulletinLine(s.getCode(), subjectLabel(s), s.getCoef(),
                    mark.setScale(2, RoundingMode.HALF_UP), weighted));
        }
        BigDecimal average = average(schoolId, subjects, studentId, sequence);

        // 2. Rank + class average across active classmates that have grades.
        List<Student> classmates = student.getClassName() == null
                ? List.of()
                : studentRepo.findBySchoolIdAndClassNameAndActiveTrueOrderByLastNameAsc(schoolId, student.getClassName());

        List<BigDecimal> classAverages = new ArrayList<>();
        for (Student mate : classmates) {
            if (!hasAnyGrade(schoolId, subjects, mate.getId(), sequence)) continue;
            classAverages.add(average(schoolId, subjects, mate.getId(), sequence));
        }
        classAverages.sort(Comparator.reverseOrder());
        int classSize = classAverages.size();

        int rank = 0;
        if (hasAnyGrade(schoolId, subjects, studentId, sequence)) {
            // 1-based position of this student's average within the desc-sorted list.
            for (int i = 0; i < classAverages.size(); i++) {
                if (classAverages.get(i).compareTo(average) == 0) {
                    rank = i + 1;
                    break;
                }
            }
        }

        BigDecimal classAverage = mean(classAverages);

        // 3. Validation state.
        var validation = validationRepo.findBySchoolIdAndStudentIdAndSequence(schoolId, studentId, sequence);
        boolean validated = validation.map(BulletinValidation::isValidated).orElse(false);
        String generalAppreciation = validation.map(BulletinValidation::getGeneralAppreciation).orElse(null);

        // 4. Financial block — finance owns student_fee, read it via JDBC.
        boolean financiallyBlocked = isFinanciallyBlocked(schoolId, studentId);

        return new BulletinView(
                student.getId(),
                studentName(student),
                student.getClassName(),
                sequence,
                lines,
                average,
                rank,
                classSize,
                classAverage,
                validated,
                generalAppreciation,
                financiallyBlocked);
    }

    @Transactional(readOnly = true)
    public PvView pv(String className, int sequence) {
        UUID schoolId = TenantContext.get();
        List<Subject> subjects = subjectRepo.findBySchoolIdOrderByCode(schoolId);

        List<Student> students =
                studentRepo.findBySchoolIdAndClassNameAndActiveTrueOrderByLastNameAsc(schoolId, className);

        record Scored(Student student, BigDecimal average) {}
        List<Scored> scored = new ArrayList<>();
        for (Student s : students) {
            if (!hasAnyGrade(schoolId, subjects, s.getId(), sequence)) continue;
            scored.add(new Scored(s, average(schoolId, subjects, s.getId(), sequence)));
        }
        scored.sort(Comparator.comparing(Scored::average).reversed());

        List<PvRow> rows = new ArrayList<>();
        for (int i = 0; i < scored.size(); i++) {
            Scored sc = scored.get(i);
            rows.add(new PvRow(sc.student().getId(), studentName(sc.student()), sc.average(), i + 1));
        }

        BigDecimal classAverage = mean(scored.stream().map(Scored::average).toList());

        return new PvView(className, sequence, rows, classAverage);
    }

    @Transactional
    public BulletinView validate(UUID studentId, ValidateRequest req) {
        UUID schoolId = TenantContext.get();

        BulletinValidation v = validationRepo
                .findBySchoolIdAndStudentIdAndSequence(schoolId, studentId, req.sequence())
                .orElseGet(() -> {
                    BulletinValidation fresh = new BulletinValidation();
                    fresh.setSchoolId(schoolId);
                    fresh.setStudentId(studentId);
                    fresh.setSequence(req.sequence());
                    return fresh;
                });
        v.setValidated(true);
        v.setGeneralAppreciation(req.generalAppreciation());
        v.setValidatedAt(OffsetDateTime.now());
        // validatedBy intentionally left null (current user wiring out of scope).
        validationRepo.save(v);

        return bulletin(studentId, req.sequence());
    }

    private boolean isFinanciallyBlocked(UUID schoolId, UUID studentId) {
        List<Long> balances = jdbc.query(
                "SELECT balance FROM student_fee WHERE school_id=? AND student_id=?",
                (rs, rowNum) -> rs.getObject("balance") == null ? null : rs.getLong("balance"),
                schoolId, studentId);
        if (balances.isEmpty()) return false;
        Long balance = balances.get(0);
        return balance != null && balance > 0;
    }

    private BigDecimal mean(List<BigDecimal> values) {
        if (values.isEmpty()) return BigDecimal.ZERO;
        BigDecimal sum = BigDecimal.ZERO;
        for (BigDecimal v : values) sum = sum.add(v);
        return sum.divide(BigDecimal.valueOf(values.size()), 2, RoundingMode.HALF_UP);
    }
}

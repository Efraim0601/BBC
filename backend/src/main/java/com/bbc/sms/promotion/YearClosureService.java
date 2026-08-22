package com.bbc.sms.promotion;

import com.bbc.sms.finance.FeeConfig;
import com.bbc.sms.finance.FeeService;
import com.bbc.sms.finance.StudentFee;
import com.bbc.sms.finance.StudentFeeRepository;
import com.bbc.sms.platform.common.ApiException;
import com.bbc.sms.platform.security.AppUserPrincipal;
import com.bbc.sms.platform.tenant.TenantContext;
import com.bbc.sms.promotion.dto.PromotionDtos.*;
import com.bbc.sms.student.Student;
import com.bbc.sms.student.StudentRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Clôture de l'année scolaire.
 *
 * <p>Le passage de classe déplace les élèves ; la clôture, elle, remet
 * l'établissement à zéro pour la rentrée. Trois tables seulement débordent d'une
 * année sur l'autre — {@code grade}, {@code bulletin_validation} et
 * {@code student_fee} n'ont aucune dimension temporelle. La clôture les archive
 * sous le libellé de l'année écoulée, vide les tables vives, régénère les
 * scolarités depuis la grille des frais, puis bascule l'année courante.
 *
 * <p>Deux garde-fous. L'opération <b>refuse de s'exécuter tant que des élèves
 * n'ont pas de décision de fin d'année</b> — archiver leurs notes avant de les
 * avoir fait passer reviendrait à jeter la base même du calcul. Et une année
 * déjà clôturée ne peut pas l'être une seconde fois : la nouvelle année serait
 * archivée par-dessus l'ancienne.
 */
@Service
public class YearClosureService {

    private final StudentRepository students;
    private final StudentFeeRepository studentFees;
    private final FeeService fees;
    private final ProgressionService progression;
    private final JdbcTemplate jdbc;

    public YearClosureService(StudentRepository students, StudentFeeRepository studentFees,
                              FeeService fees, ProgressionService progression, JdbcTemplate jdbc) {
        this.students = students;
        this.studentFees = studentFees;
        this.fees = fees;
        this.progression = progression;
        this.jdbc = jdbc;
    }

    // =====================================================================
    //  État des lieux
    // =====================================================================

    @Transactional(readOnly = true)
    public ClosurePreview preview(String academicYear) {
        UUID schoolId = TenantContext.get();
        String year = academicYear == null || academicYear.isBlank()
                ? progression.currentAcademicYear() : academicYear.trim();
        String nextYear = ProgressionService.nextAcademicYear(year);

        Instant closedAt = jdbc.query(
                "SELECT closed_at FROM year_closure WHERE school_id = ? AND academic_year = ?",
                rs -> rs.next() ? rs.getTimestamp(1).toInstant() : null, schoolId, year);

        int active = (int) students.countBySchoolIdAndActiveTrue(schoolId);
        int decided = count("""
                SELECT COUNT(*) FROM student s
                 WHERE s.school_id = ? AND s.active = true
                   AND EXISTS (SELECT 1 FROM year_promotion_decision d
                                WHERE d.school_id = s.school_id AND d.student_id = s.id
                                  AND d.academic_year = ?)
                """, schoolId, year);

        // Les classes où il reste des élèves sans décision : c'est là qu'il faut
        // retourner avant de clôturer.
        List<PendingClass> pending = jdbc.query("""
                SELECT COALESCE(s.class_name, '—') AS class_name, COUNT(*) AS n
                  FROM student s
                 WHERE s.school_id = ? AND s.active = true
                   AND NOT EXISTS (SELECT 1 FROM year_promotion_decision d
                                    WHERE d.school_id = s.school_id AND d.student_id = s.id
                                      AND d.academic_year = ?)
                 GROUP BY 1 ORDER BY 1
                """, (rs, n) -> new PendingClass(rs.getString("class_name"), rs.getInt("n")),
                schoolId, year);

        int grades = count("SELECT COUNT(*) FROM grade WHERE school_id = ?", schoolId);
        int validations = count("SELECT COUNT(*) FROM bulletin_validation WHERE school_id = ?", schoolId);
        int feeRows = count("SELECT COUNT(*) FROM student_fee WHERE school_id = ?", schoolId);
        int feesToCreate = (int) students.findBySchoolIdAndActiveTrueOrderByLastNameAsc(schoolId).stream()
                .filter(s -> fees.resolveGrid(schoolId, s).isPresent())
                .count();

        List<String> warnings = new ArrayList<>();
        if (closedAt != null) {
            warnings.add("L'année " + year + " a déjà été clôturée. Une seconde clôture est refusée : "
                       + "elle archiverait la nouvelle année à la place de l'ancienne.");
        }
        if (!pending.isEmpty()) {
            warnings.add((active - decided) + " élève(s) actif(s) n'ont pas de décision de fin d'année pour "
                       + year + " — passez d'abord leur classe, sinon leurs notes seront archivées "
                       + "sans avoir servi au calcul.");
        }
        if (feesToCreate < active) {
            warnings.add((active - feesToCreate) + " élève(s) n'ont aucune grille de frais applicable : "
                       + "leur scolarité ne sera pas régénérée. Complétez la grille avant de clôturer.");
        }
        if (grades == 0 && validations == 0 && feeRows == 0) {
            warnings.add("Aucune donnée d'année en cours à archiver — la clôture ne fera que basculer l'année.");
        }

        return new ClosurePreview(year, nextYear, closedAt, active, decided, active - decided,
                pending, grades, validations, feeRows, feesToCreate, warnings);
    }

    // =====================================================================
    //  Clôture
    // =====================================================================

    /**
     * Exécute la clôture. Tout tient dans une transaction : archivage, purge,
     * régénération des scolarités et bascule d'année réussissent ensemble ou
     * échouent ensemble — un établissement ne peut pas se retrouver avec des
     * notes archivées mais l'année toujours ouverte.
     */
    @Transactional
    public ClosureResult close(ClosureRequest in) {
        UUID schoolId = TenantContext.get();
        String year = in.academicYear().trim();
        String nextYear = in.nextAcademicYear().trim();
        if (year.equals(nextYear)) {
            throw ApiException.badRequest("La nouvelle année doit différer de celle que l'on clôture");
        }

        Integer already = jdbc.query(
                "SELECT 1 FROM year_closure WHERE school_id = ? AND academic_year = ?",
                rs -> rs.next() ? 1 : null, schoolId, year);
        if (already != null) {
            throw ApiException.conflict("L'année " + year + " a déjà été clôturée");
        }

        ClosurePreview state = preview(year);
        if (state.studentsPending() > 0 && !in.ignorePending()) {
            String classes = state.pendingClasses().stream()
                    .map(p -> p.className() + " (" + p.students() + ")")
                    .reduce((a, b) -> a + ", " + b).orElse("");
            throw ApiException.conflict(
                    state.studentsPending() + " élève(s) sans décision de fin d'année : " + classes
                  + ". Terminez le passage de classe, ou cochez explicitement « clôturer malgré tout ».");
        }

        List<String> warnings = new ArrayList<>();
        int gradesArchived = 0, validationsArchived = 0, feesArchived = 0, feesCreated = 0;

        if (in.archiveGrades()) {
            gradesArchived = archiveGrades(schoolId, year);
            validationsArchived = archiveValidations(schoolId, year);
        } else {
            warnings.add("Notes non archivées : les séquences de " + year
                       + " se mélangeront à celles de " + nextYear + ".");
        }

        if (in.resetFees()) {
            feesArchived = archiveFees(schoolId, year);
            feesCreated = regenerateFees(schoolId);
            if (feesCreated == 0 && feesArchived > 0) {
                warnings.add("Aucune scolarité régénérée : vérifiez la grille des frais des nouvelles classes.");
            }
        } else {
            warnings.add("Scolarités conservées : les soldes de " + year + " restent dus en " + nextYear + ".");
        }

        if (in.makeCurrent()) {
            progression.ensureAcademicYear(nextYear);
            jdbc.update("UPDATE academic_year SET is_current = false WHERE school_id = ?", schoolId);
            jdbc.update("UPDATE academic_year SET is_current = true WHERE school_id = ? AND label = ?",
                    schoolId, nextYear);
        }

        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO year_closure (id, school_id, academic_year, next_academic_year,
                        grades_archived, validations_archived, fees_archived, fees_created,
                        students_active, students_pending, made_current, closed_by)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?)
                """, id, schoolId, year, nextYear, gradesArchived, validationsArchived,
                feesArchived, feesCreated, state.activeStudents(), state.studentsPending(),
                in.makeCurrent(), currentUserId());

        return new ClosureResult(id, year, nextYear, gradesArchived, validationsArchived,
                feesArchived, feesCreated, in.makeCurrent(), warnings);
    }

    @Transactional(readOnly = true)
    public List<ClosureView> history() {
        return jdbc.query("""
                SELECT c.*, u.display_name AS closed_by_name
                  FROM year_closure c LEFT JOIN app_user u ON u.id = c.closed_by
                 WHERE c.school_id = ? ORDER BY c.closed_at DESC LIMIT 20
                """, (rs, n) -> new ClosureView(
                        UUID.fromString(rs.getString("id")),
                        rs.getString("academic_year"), rs.getString("next_academic_year"),
                        rs.getInt("grades_archived"), rs.getInt("validations_archived"),
                        rs.getInt("fees_archived"), rs.getInt("fees_created"),
                        rs.getInt("students_active"), rs.getInt("students_pending"),
                        rs.getBoolean("made_current"), rs.getString("closed_by_name"),
                        rs.getTimestamp("closed_at").toInstant()),
                TenantContext.get());
    }

    // =====================================================================
    //  Archivage
    // =====================================================================

    /**
     * La classe portée par l'archive est celle que l'élève a réellement fréquentée
     * cette année-là : le parcours en fait foi, la fiche élève ne servant que de
     * repli — après un passage, elle indique déjà la classe de l'année suivante.
     */
    private int archiveGrades(UUID schoolId, String year) {
        int copied = jdbc.update("""
                INSERT INTO grade_archive (school_id, academic_year, student_id, class_name,
                                           subject_code, sequence, mark)
                SELECT g.school_id, ?, g.student_id, COALESCE(j.class_name, s.class_name),
                       g.subject_code, g.sequence, g.mark
                  FROM grade g
                  JOIN student s ON s.id = g.student_id
                  LEFT JOIN journey_entry j ON j.school_id = g.school_id
                                           AND j.student_id = g.student_id
                                           AND j.academic_year = ?
                 WHERE g.school_id = ?
                ON CONFLICT (school_id, academic_year, student_id, subject_code, sequence) DO NOTHING
                """, year, year, schoolId);
        jdbc.update("DELETE FROM grade WHERE school_id = ?", schoolId);
        return copied;
    }

    private int archiveValidations(UUID schoolId, String year) {
        int copied = jdbc.update("""
                INSERT INTO bulletin_validation_archive (school_id, academic_year, student_id, sequence,
                                                         validated, general_appreciation, validated_by, validated_at)
                SELECT school_id, ?, student_id, sequence, validated, general_appreciation,
                       validated_by, validated_at
                  FROM bulletin_validation WHERE school_id = ?
                ON CONFLICT (school_id, academic_year, student_id, sequence) DO NOTHING
                """, year, schoolId);
        jdbc.update("DELETE FROM bulletin_validation WHERE school_id = ?", schoolId);
        return copied;
    }

    private int archiveFees(UUID schoolId, String year) {
        int copied = jdbc.update("""
                INSERT INTO student_fee_archive (school_id, academic_year, student_id, class_name,
                                                 total, paid, balance, tranches_paid, status)
                SELECT f.school_id, ?, f.student_id, COALESCE(j.class_name, s.class_name),
                       f.total, f.paid, f.balance, f.tranches_paid, f.status
                  FROM student_fee f
                  JOIN student s ON s.id = f.student_id
                  LEFT JOIN journey_entry j ON j.school_id = f.school_id
                                           AND j.student_id = f.student_id
                                           AND j.academic_year = ?
                 WHERE f.school_id = ?
                ON CONFLICT (school_id, academic_year, student_id) DO NOTHING
                """, year, year, schoolId);
        jdbc.update("DELETE FROM student_fee WHERE school_id = ?", schoolId);
        return copied;
    }

    /**
     * Rouvre une scolarité vierge pour chaque élève actif, au tarif de sa NOUVELLE
     * classe — le passage ayant déjà eu lieu, la grille résolue est la bonne. Un
     * élève sans grille applicable n'a pas de ligne : elle sera créée au premier
     * encaissement, comme pour une inscription en cours d'année.
     */
    private int regenerateFees(UUID schoolId) {
        int created = 0;
        for (Student s : students.findBySchoolIdAndActiveTrueOrderByLastNameAsc(schoolId)) {
            FeeConfig grid = fees.resolveGrid(schoolId, s).orElse(null);
            if (grid == null || grid.getTotal() <= 0) continue;
            StudentFee fee = new StudentFee();
            fee.setSchoolId(schoolId);
            fee.setStudentId(s.getId());
            fee.setTotal(grid.getTotal());
            fee.setPaid(0);
            fee.setBalance(grid.getTotal());
            fee.setTranchesPaid(0);
            fee.setStatus("unpaid");
            studentFees.save(fee);
            created++;
        }
        return created;
    }

    // =====================================================================
    //  Utilitaires
    // =====================================================================

    private int count(String sql, Object... args) {
        Integer n = jdbc.queryForObject(sql, Integer.class, args);
        return n == null ? 0 : n;
    }

    private UUID currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getPrincipal() instanceof AppUserPrincipal p ? p.userId() : null;
    }
}

package com.bbc.sms.reports;

import com.bbc.sms.platform.common.ApiException;
import com.bbc.sms.platform.tenant.ParcoursContext;
import com.bbc.sms.platform.tenant.TenantContext;
import com.bbc.sms.reports.dto.ReportDtos.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Aggregations for the reports module. No JPA entities here — every figure is computed
 * with JdbcTemplate and scoped to the current tenant via {@link TenantContext#get()}.
 *
 * <p>Second cloisonnement : le verrou de section. Un administrateur de cycle ne
 * lit que ses propres chiffres, sans quoi ces agrégats — élèves, recouvrement,
 * assiduité — lui livreraient tout l'établissement d'un seul écran. Le filtre
 * est neutre pour les autres comptes, dont la vue reste école-entière.
 */
@Service
public class ReportService {

    private static final String UNKNOWN = "—";

    /**
     * Clause neutre quand aucun verrou n'est posé : le paramètre vaut alors null
     * et la condition s'efface. Le transtypage explicite est exigé par
     * PostgreSQL, qui refuse de deviner le type d'un paramètre nul.
     */
    private static final String LOCK = " AND (CAST(? AS VARCHAR) IS NULL OR %s = CAST(? AS VARCHAR))";

    private final JdbcTemplate jdbc;

    public ReportService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    /** Section imposée à la requête, ou null quand le compte voit toute l'école. */
    private static String lock() { return ParcoursContext.sectionLock(); }

    @Transactional(readOnly = true)
    public FinanceReport finance() {
        UUID schoolId = TenantContext.get();

        String lock = lock();
        long totalRevenue = jdbc.queryForObject(
                "SELECT COALESCE(SUM(p.amount),0) FROM payment p"
                + " LEFT JOIN student s ON s.id = p.student_id WHERE p.school_id = ?"
                + LOCK.formatted("s.level"),
                Long.class, schoolId, lock, lock);
        // Les dépenses n'appartiennent à aucun cycle : un admin de section en
        // voit zéro plutôt qu'une part arbitraire (cf. FinanceService).
        long totalExpense = lock != null ? 0L : jdbc.queryForObject(
                "SELECT COALESCE(SUM(amount),0) FROM expense WHERE school_id = ?",
                Long.class, schoolId);

        long[] fees = jdbc.query(
                "SELECT COALESCE(SUM(f.paid),0), COALESCE(SUM(f.total),0) FROM student_fee f"
                + " LEFT JOIN student s ON s.id = f.student_id WHERE f.school_id = ?"
                + LOCK.formatted("s.level"),
                rs -> rs.next() ? new long[]{rs.getLong(1), rs.getLong(2)} : new long[]{0, 0},
                schoolId, lock, lock);
        long paid = fees[0];
        long total = fees[1];
        double recoveryRate = total == 0 ? 0d : Math.round(((double) paid / total) * 1000d) / 10d;

        long balance = totalRevenue - totalExpense;
        return new FinanceReport(totalRevenue, totalExpense, balance, recoveryRate);
    }

    @Transactional(readOnly = true)
    public List<AttendanceRow> attendanceMonthly(String month) {
        UUID schoolId = TenantContext.get();

        YearMonth ym;
        if (month == null || month.isBlank()) {
            ym = YearMonth.now();
        } else {
            try {
                ym = YearMonth.parse(month);
            } catch (DateTimeParseException e) {
                throw ApiException.badRequest("Mois invalide (attendu YYYY-MM)");
            }
        }
        LocalDate from = ym.atDay(1);
        LocalDate to = ym.atEndOfMonth();

        Map<UUID, Agg> byStudent = new LinkedHashMap<>();
        jdbc.query(
                "SELECT a.student_id, s.last_name, s.first_name, s.class_name, a.status " +
                        "FROM attendance_record a JOIN student s ON s.id = a.student_id " +
                        "WHERE a.school_id = ? AND a.att_date BETWEEN ? AND ?" + LOCK.formatted("s.level"),
                rs -> {
                    // Read all ResultSet columns here (the RowCallbackHandler declares
                    // throws SQLException); the computeIfAbsent lambda below is a plain
                    // Function and may not throw, so capture values into locals first.
                    UUID studentId = rs.getObject("student_id", UUID.class);
                    String lastName = rs.getString("last_name");
                    String firstName = rs.getString("first_name");
                    String className = rs.getString("class_name");
                    String status = rs.getString("status");
                    Agg agg = byStudent.computeIfAbsent(studentId,
                            k -> new Agg(studentId, lastName, firstName, className));
                    switch (status) {
                        case "present" -> agg.present++;
                        case "late" -> agg.late++;
                        case "absent" -> agg.absent++;
                        default -> { /* ignore unknown statuses */ }
                    }
                },
                schoolId, java.sql.Date.valueOf(from), java.sql.Date.valueOf(to), lock(), lock());

        List<AttendanceRow> rows = new ArrayList<>(byStudent.size());
        for (Agg a : byStudent.values()) {
            int counted = a.present + a.late + a.absent;
            int rate = counted == 0 ? 0 : Math.round(((float) (a.present + a.late) / counted) * 100f);
            String name = (a.lastName == null ? "" : a.lastName.toUpperCase()) + " "
                    + (a.firstName == null ? "" : a.firstName);
            rows.add(new AttendanceRow(a.studentId, name.trim(), a.className,
                    a.present, a.late, a.absent, rate));
        }
        rows.sort(Comparator.comparingInt(AttendanceRow::rate).reversed()
                .thenComparing(AttendanceRow::studentName));
        return rows;
    }

    @Transactional(readOnly = true)
    public Demographics demographics() {
        UUID schoolId = TenantContext.get();

        Map<String, Long> byLevel = new LinkedHashMap<>();
        Map<String, Long> bySubsystem = new LinkedHashMap<>();
        Map<String, Long> bySex = new LinkedHashMap<>();
        long[] total = {0};

        jdbc.query(
                "SELECT level, subsystem, sex FROM student WHERE school_id = ? AND active = true"
                        + LOCK.formatted("level"),
                rs -> {
                    total[0]++;
                    bump(byLevel, rs.getString("level"));
                    bump(bySubsystem, rs.getString("subsystem"));
                    bump(bySex, rs.getString("sex"));
                },
                schoolId, lock(), lock());

        return new Demographics(total[0], byLevel, bySubsystem, bySex);
    }

    private static void bump(Map<String, Long> map, String key) {
        String k = (key == null || key.isBlank()) ? UNKNOWN : key;
        map.merge(k, 1L, Long::sum);
    }

    /** Mutable per-student accumulator used while scanning attendance rows. */
    private static final class Agg {
        final UUID studentId;
        final String lastName;
        final String firstName;
        final String className;
        int present;
        int late;
        int absent;

        Agg(UUID studentId, String lastName, String firstName, String className) {
            this.studentId = studentId;
            this.lastName = lastName;
            this.firstName = firstName;
            this.className = className;
        }
    }
}

package com.bbc.sms.finance;

import com.bbc.sms.finance.dto.FeeDtos.*;
import com.bbc.sms.platform.common.ApiException;
import com.bbc.sms.platform.security.AuthorizationPolicyService;
import com.bbc.sms.platform.security.PolicyResourceContext;
import com.bbc.sms.platform.tenant.TenantContext;
import com.bbc.sms.student.Student;
import com.bbc.sms.student.StudentRepository;
import com.bbc.sms.timetable.SchoolClass;
import com.bbc.sms.timetable.SchoolClassRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Frais de scolarité : grilles (par niveau, surchargeables par classe), situation des
 * élèves et catalogue des moyens de paiement.
 *
 * <p>La grille applicable à un élève est résolue dans cet ordre : surcharge de sa
 * classe, sinon grille de son niveau (avec le sous-système correspondant). C'est cette
 * grille qui découpe la scolarité en tranches, donc qui rend le paiement progressif
 * lisible pour l'économat comme pour le parent.
 */
@Service
public class FeeService {

    private final FeeConfigRepository feeConfigs;
    private final StudentFeeRepository studentFees;
    private final StudentRepository students;
    private final PaymentRepository payments;
    private final PaymentChannelRepository channels;
    private final SchoolClassRepository classes;
    private final AuthorizationPolicyService policy;
    private final JdbcTemplate jdbc;

    private record FinanceStudent(UUID id, String matricule, String firstName,
                                  String lastName, String className) {}

    public FeeService(FeeConfigRepository feeConfigs, StudentFeeRepository studentFees,
                      StudentRepository students, PaymentRepository payments,
                      PaymentChannelRepository channels, SchoolClassRepository classes,
                      AuthorizationPolicyService policy, JdbcTemplate jdbc) {
        this.feeConfigs = feeConfigs;
        this.studentFees = studentFees;
        this.students = students;
        this.payments = payments;
        this.channels = channels;
        this.classes = classes;
        this.policy = policy;
        this.jdbc = jdbc;
    }

    // ------------------------------------------------------------------- grilles

    @Transactional(readOnly = true)
    public List<FeeConfigView> listConfig() {
        requireSchool("FINANCE_OVERVIEW_VIEW");
        UUID schoolId = TenantContext.get();
        Map<UUID, String> classNames = classNames(schoolId);
        return feeConfigs.findBySchoolId(schoolId).stream()
                .sorted(Comparator.comparing(FeeConfig::getLevel)
                        .thenComparing(c -> c.getClassId() == null ? "" : classNames.getOrDefault(c.getClassId(), "")))
                .map(c -> toView(c, classNames))
                .toList();
    }

    @Transactional
    public FeeConfigView upsertConfig(FeeConfigUpdate in) {
        requireSchool("FEE_CONFIGURE");
        UUID schoolId = TenantContext.get();
        List<TrancheView> tranches = in.tranches() == null ? List.of() : in.tranches();

        if (!tranches.isEmpty()) {
            long sum = tranches.stream().mapToLong(TrancheView::amount).sum();
            if (sum != in.total()) {
                throw ApiException.badRequest("La somme des tranches (" + sum
                        + ") doit être égale au total (" + in.total() + ")");
            }
        }

        FeeConfig cfg = findExisting(schoolId, in).orElseGet(() -> {
            FeeConfig fresh = new FeeConfig();
            fresh.setSchoolId(schoolId);
            return fresh;
        });

        cfg.setLevel(in.level());
        cfg.setSubsystem(in.subsystem());
        cfg.setClassId(in.classId());
        cfg.setTotal(in.total());
        cfg.setTranches(toJson(tranches));
        cfg.setItems(in.items());
        FeeConfig saved = feeConfigs.save(cfg);

        // Le montant attendu de chaque élève concerné suit la grille : sans ce
        // recalcul, les soldes et la liste des débiteurs resteraient sur l'ancienne.
        refreshExpectedTotals(schoolId);

        return toView(saved, classNames(schoolId));
    }

    @Transactional
    public void deleteConfig(UUID id) {
        requireSchool("FEE_CONFIGURE");
        UUID schoolId = TenantContext.get();
        FeeConfig cfg = feeConfigs.findById(id)
                .filter(c -> c.getSchoolId().equals(schoolId))
                .orElseThrow(() -> ApiException.notFound("Grille de frais"));
        feeConfigs.delete(cfg);
        refreshExpectedTotals(schoolId);
    }

    /** Grille applicable à un élève : surcharge de classe, sinon grille du niveau. */
    @Transactional(readOnly = true)
    public Optional<FeeConfig> resolveGrid(UUID schoolId, Student student) {
        if (student == null) return Optional.empty();
        List<FeeConfig> all = feeConfigs.findBySchoolId(schoolId);

        // L'élève porte parfois seulement le nom de sa classe (données antérieures aux
        // sélecteurs de classe) : on retrouve alors l'identifiant par son libellé.
        UUID classId = student.getClassId() != null ? student.getClassId()
                : (student.getClassName() == null ? null
                   : classes.findBySchoolIdAndName(schoolId, student.getClassName())
                            .map(SchoolClass::getId).orElse(null));

        if (classId != null) {
            UUID finalClassId = classId;
            Optional<FeeConfig> override = all.stream()
                    .filter(c -> finalClassId.equals(c.getClassId()))
                    .findFirst();
            if (override.isPresent()) return override;
        }
        if (student.getLevel() == null) return Optional.empty();

        List<FeeConfig> levelGrids = all.stream()
                .filter(c -> c.getClassId() == null && student.getLevel().equals(c.getLevel()))
                .toList();
        // Une grille propre au sous-système prime sur la grille « les deux ».
        return levelGrids.stream()
                .filter(c -> Objects.equals(c.getSubsystem(), student.getSubsystem()))
                .findFirst()
                .or(() -> levelGrids.stream().filter(c -> c.getSubsystem() == null).findFirst());
    }

    // ----------------------------------------------------------------- situation

    @Transactional(readOnly = true)
    public List<SituationView> situation() {
        requireSchool("FINANCE_OVERVIEW_VIEW");
        return buildSituation(false);
    }

    @Transactional(readOnly = true)
    public List<SituationView> debtors() {
        requireSchool("FINANCE_OVERVIEW_VIEW");
        return buildSituation(true);
    }

    private List<SituationView> buildSituation(boolean onlyDebtors) {
        UUID schoolId = TenantContext.get();
        List<StudentFee> feeRows = studentFees.findBySchoolId(schoolId);
        Map<UUID, FinanceStudent> studentsById = financeStudents(schoolId,
                feeRows.stream().map(StudentFee::getStudentId).collect(Collectors.toSet()));

        return feeRows.stream()
                .filter(sf -> !onlyDebtors || sf.getBalance() > 0)
                .map(sf -> toSituation(sf, studentsById.get(sf.getStudentId())))
                .sorted(Comparator.comparingLong(SituationView::balance).reversed())
                .toList();
    }

    /**
     * Situation détaillée d'un élève : grille de sa classe découpée en tranches, part
     * couverte par les versements reçus et historique des reçus. Sert à l'économat
     * comme au portail parent.
     */
    @Transactional(readOnly = true)
    public StudentFeeStatementView statement(UUID schoolId, UUID studentId) {
        policy.require("PAYMENT_VIEW", new PolicyResourceContext(schoolId, null, LocalDate.now(),
                null, null, null, studentId, null, null, null, null, null));
        return statementInternal(schoolId, studentId);
    }

    /** Parent portal entry point; ParentService has already enforced child + feature scope. */
    @Transactional(readOnly = true)
    public StudentFeeStatementView statementForParent(UUID schoolId, UUID studentId) {
        return statementInternal(schoolId, studentId);
    }

    private StudentFeeStatementView statementInternal(UUID schoolId, UUID studentId) {
        Student s = students.findByIdAndSchoolId(studentId, schoolId)
                .orElseThrow(() -> ApiException.notFound("Élève"));

        FeeConfig grid = resolveGrid(schoolId, s).orElse(null);
        long total = grid == null ? 0 : grid.getTotal();
        long paid = payments.findBySchoolIdAndStudentIdOrderByPaidOnAsc(schoolId, studentId).stream()
                .mapToLong(Payment::getAmount).sum();
        long balance = Math.max(0, total - paid);
        int progress = total > 0 ? (int) Math.min(100, Math.round(paid * 100.0 / total)) : (paid > 0 ? 100 : 0);
        String status = total > 0 && balance <= 0 ? "paid" : (paid > 0 ? "partial" : "unpaid");

        Map<String, PaymentChannel> byCode = channels.findBySchoolIdOrderBySortOrderAscLabelFrAsc(schoolId).stream()
                .collect(Collectors.toMap(PaymentChannel::getCode, c -> c, (a, b) -> a));

        List<PaymentLineView> lines = payments.findBySchoolIdAndStudentIdOrderByPaidOnAsc(schoolId, studentId)
                .stream()
                .sorted(Comparator.comparing(Payment::getPaidOn).reversed())
                .map(p -> {
                    PaymentChannel ch = byCode.get(p.getMethod());
                    return new PaymentLineView(p.getReceiptNo(), p.getPaidOn(), p.getAmount(), p.getMethod(),
                            ch == null ? p.getMethod() : ch.getLabelFr(),
                            ch == null ? p.getMethod() : ch.getLabelEn(),
                            p.getReference(), p.getTranche());
                })
                .toList();

        return new StudentFeeStatementView(
                s.getId(), s.getFirstName() + " " + s.getLastName(), s.getMatricule(), s.getClassName(),
                grid == null ? null : (grid.getClassId() != null ? "class" : "level"),
                total, paid, balance, progress, status,
                trancheStatuses(grid, paid), lines);
    }

    /**
     * Ventile le total versé sur les tranches, dans l'ordre : la première est soldée
     * avant que la suivante commence — c'est ainsi que l'école lit un paiement partiel.
     */
    private List<TrancheStatusView> trancheStatuses(FeeConfig grid, long paid) {
        List<TrancheView> tranches = fromJson(grid == null ? null : grid.getTranches());
        if (tranches.isEmpty()) return List.of();

        List<TrancheStatusView> out = new ArrayList<>(tranches.size());
        long remainingCredit = paid;
        LocalDate today = LocalDate.now();

        for (int i = 0; i < tranches.size(); i++) {
            TrancheView t = tranches.get(i);
            long covered = Math.max(0, Math.min(remainingCredit, t.amount()));
            remainingCredit -= covered;
            long remaining = Math.max(0, t.amount() - covered);
            String status = remaining == 0 ? "paid" : (covered > 0 ? "partial" : "pending");
            boolean overdue = remaining > 0 && t.dueOn() != null && t.dueOn().isBefore(today);
            out.add(new TrancheStatusView(i + 1, label(t, i), t.amount(), t.dueOn(),
                    covered, remaining, status, overdue));
        }
        return out;
    }

    private String label(TrancheView t, int index) {
        return t.label() == null || t.label().isBlank() ? "T" + (index + 1) : t.label();
    }

    // ---------------------------------------------------------- moyens de paiement

    @Transactional(readOnly = true)
    public List<PaymentChannelView> listChannels() {
        requireSchool("FINANCE_OVERVIEW_VIEW");
        return channels.findBySchoolIdOrderBySortOrderAscLabelFrAsc(TenantContext.get()).stream()
                .map(this::toView).toList();
    }

    /** Canaux proposés aux parents : activés et marqués visibles. */
    @Transactional(readOnly = true)
    public List<PaymentChannelView> parentChannels(UUID schoolId) {
        return channels.findBySchoolIdAndEnabledTrueOrderBySortOrderAscLabelFrAsc(schoolId).stream()
                .filter(PaymentChannel::isVisibleToParents)
                .map(this::toView).toList();
    }

    @Transactional
    public PaymentChannelView updateChannel(String code, PaymentChannelUpdate in) {
        requireSchool("FEE_CONFIGURE");
        UUID schoolId = TenantContext.get();
        PaymentChannel c = channels.findBySchoolIdAndCode(schoolId, code)
                .orElseThrow(() -> ApiException.notFound("Moyen de paiement " + code));

        if (in.labelFr() != null && !in.labelFr().isBlank()) c.setLabelFr(in.labelFr().trim());
        if (in.labelEn() != null && !in.labelEn().isBlank()) c.setLabelEn(in.labelEn().trim());
        if (in.accountRef() != null) c.setAccountRef(blankToNull(in.accountRef()));
        if (in.accountName() != null) c.setAccountName(blankToNull(in.accountName()));
        if (in.instructionsFr() != null) c.setInstructionsFr(blankToNull(in.instructionsFr()));
        if (in.instructionsEn() != null) c.setInstructionsEn(blankToNull(in.instructionsEn()));
        if (in.requiresReference() != null) c.setRequiresReference(in.requiresReference());
        if (in.enabled() != null) c.setEnabled(in.enabled());
        if (in.visibleToParents() != null) c.setVisibleToParents(in.visibleToParents());
        if (in.sortOrder() != null) c.setSortOrder(in.sortOrder());

        return toView(channels.save(c));
    }

    /** Canal actif portant ce code, ou échec explicite : un encaissement doit être traçable. */
    @Transactional(readOnly = true)
    public PaymentChannel requireEnabledChannel(UUID schoolId, String code) {
        PaymentChannel c = channels.findBySchoolIdAndCode(schoolId, code)
                .orElseThrow(() -> ApiException.badRequest("Moyen de paiement inconnu : " + code));
        if (!c.isEnabled()) {
            throw ApiException.badRequest("Le moyen de paiement « " + c.getLabelFr() + " » est désactivé.");
        }
        return c;
    }

    // -------------------------------------------------------------------- interne

    /** Réaligne le montant attendu de chaque élève sur la grille qui lui est applicable. */
    private void refreshExpectedTotals(UUID schoolId) {
        Map<UUID, Student> byId = students.findBySchoolIdAndActiveTrueOrderByLastNameAsc(schoolId).stream()
                .collect(Collectors.toMap(Student::getId, Function.identity()));

        for (StudentFee fee : studentFees.findBySchoolId(schoolId)) {
            Student s = byId.get(fee.getStudentId());
            if (s == null) continue;
            long total = resolveGrid(schoolId, s).map(FeeConfig::getTotal).orElse(0L);
            if (total == 0) continue;                      // aucune grille : on ne touche à rien
            fee.setTotal(Math.max(total, fee.getPaid()));  // jamais de solde négatif
            fee.setBalance(Math.max(0, fee.getTotal() - fee.getPaid()));
            fee.setStatus(fee.getBalance() <= 0 ? "paid" : (fee.getPaid() > 0 ? "partial" : "unpaid"));
            studentFees.save(fee);
        }
    }

    private Optional<FeeConfig> findExisting(UUID schoolId, FeeConfigUpdate in) {
        List<FeeConfig> all = feeConfigs.findBySchoolId(schoolId);
        if (in.classId() != null) {
            return all.stream().filter(c -> in.classId().equals(c.getClassId())).findFirst();
        }
        return all.stream()
                .filter(c -> c.getClassId() == null
                        && in.level().equals(c.getLevel())
                        && Objects.equals(in.subsystem(), c.getSubsystem()))
                .findFirst();
    }

    private Map<UUID, String> classNames(UUID schoolId) {
        Map<UUID, String> out = new HashMap<>();
        for (SchoolClass c : classes.findBySchoolIdOrderByName(schoolId)) out.put(c.getId(), c.getName());
        return out;
    }

    private SituationView toSituation(StudentFee sf, FinanceStudent student) {
        String name = student == null ? null : (student.firstName() + " " + student.lastName());
        String className = student == null ? null : student.className();
        int progressPct = sf.getTotal() > 0
                ? (int) Math.round(sf.getPaid() * 100.0 / sf.getTotal())
                : 100;
        return new SituationView(sf.getStudentId(), name, className, sf.getTotal(), sf.getPaid(),
                sf.getBalance(), sf.getTranchesPaid(), sf.getStatus(), progressPct);
    }

    private FeeConfigView toView(FeeConfig c, Map<UUID, String> classNames) {
        return new FeeConfigView(c.getId(), c.getLevel(), c.getSubsystem(), c.getClassId(),
                c.getClassId() == null ? null : classNames.get(c.getClassId()),
                c.getTotal(), fromJson(c.getTranches()), c.getItems());
    }

    private PaymentChannelView toView(PaymentChannel c) {
        return new PaymentChannelView(c.getId(), c.getCode(), c.getLabelFr(), c.getLabelEn(),
                c.getAccountRef(), c.getAccountName(), c.getInstructionsFr(), c.getInstructionsEn(),
                c.isRequiresReference(), c.isEnabled(), c.isVisibleToParents(), c.getSortOrder());
    }

    private Map<UUID, FinanceStudent> financeStudents(UUID schoolId, java.util.Set<UUID> ids) {
        if (ids.isEmpty()) return Map.of();
        String placeholders = String.join(",", java.util.Collections.nCopies(ids.size(), "?"));
        List<Object> args = new ArrayList<>();
        args.add(schoolId);
        args.add(schoolId);
        args.addAll(ids);
        return jdbc.query("""
                SELECT s.id,s.matricule,s.first_name,s.last_name,
                       COALESCE(c.name,s.class_name)
                  FROM student s
                  LEFT JOIN LATERAL (
                       SELECT e.school_class_id
                         FROM student_enrollment e
                         JOIN academic_session a ON a.id=e.academic_session_id
                                               AND a.school_id=e.school_id
                        WHERE e.school_id=? AND e.student_id=s.id AND e.status='ACTIVE'
                          AND a.is_current=true AND e.enrolled_on<=CURRENT_DATE
                          AND (e.exited_on IS NULL OR e.exited_on>=CURRENT_DATE)
                        ORDER BY e.enrolled_on DESC,e.created_at DESC LIMIT 1
                  ) current_enrollment ON true
                  LEFT JOIN school_class c ON c.id=current_enrollment.school_class_id
                                           AND c.school_id=s.school_id
                 WHERE s.school_id=? AND s.active=true AND s.id IN (%s)
                """.formatted(placeholders), (rs, n) -> new FinanceStudent(
                        rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3),
                        rs.getString(4), rs.getString(5)), args.toArray())
                .stream().collect(Collectors.toMap(FinanceStudent::id, x -> x));
    }

    private void requireSchool(String action) {
        policy.require(action, PolicyResourceContext.empty().forSchool(TenantContext.get()));
    }

    /** JSONB → tranches typées. Tolère l'ancien format (un simple tableau de montants). */
    static List<TrancheView> fromJson(List<Map<String, Object>> raw) {
        if (raw == null || raw.isEmpty()) return List.of();
        List<TrancheView> out = new ArrayList<>(raw.size());
        int i = 0;
        for (Object item : raw) {
            i++;
            if (item instanceof Number n) {                       // ancien format
                out.add(new TrancheView("T" + i, n.longValue(), null));
                continue;
            }
            if (!(item instanceof Map<?, ?> m)) continue;
            Object amount = m.get("amount");
            Object label = m.get("label");
            Object dueOn = m.get("dueOn");
            out.add(new TrancheView(
                    label == null ? "T" + i : String.valueOf(label),
                    amount instanceof Number n ? n.longValue() : 0L,
                    parseDate(dueOn)));
        }
        return out;
    }

    static List<Map<String, Object>> toJson(List<TrancheView> tranches) {
        List<Map<String, Object>> out = new ArrayList<>();
        int i = 0;
        for (TrancheView t : tranches) {
            i++;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("label", t.label() == null || t.label().isBlank() ? "T" + i : t.label().trim());
            m.put("amount", t.amount());
            m.put("dueOn", t.dueOn() == null ? null : t.dueOn().toString());
            out.add(m);
        }
        return out;
    }

    private static LocalDate parseDate(Object value) {
        if (value == null) return null;
        try {
            return LocalDate.parse(String.valueOf(value));
        } catch (Exception e) {
            return null;
        }
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}

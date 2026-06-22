package com.bbc.sms.staff;

import com.bbc.sms.hr.Department;
import com.bbc.sms.hr.DepartmentRepository;
import com.bbc.sms.platform.common.ApiException;
import com.bbc.sms.platform.mail.MailService;
import com.bbc.sms.platform.tenant.TenantContext;
import com.bbc.sms.staff.dto.StaffDtos.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class StaffService {

    private final EmployeeRepository repo;
    private final DepartmentRepository departments;
    private final MailService mail;

    public StaffService(EmployeeRepository repo, DepartmentRepository departments, MailService mail) {
        this.repo = repo;
        this.departments = departments;
        this.mail = mail;
    }

    @Transactional(readOnly = true)
    public List<EmployeeView> list() {
        UUID schoolId = TenantContext.get();
        Map<UUID, String> deptNames = new HashMap<>();
        for (Department d : departments.findBySchoolIdOrderByName(schoolId)) deptNames.put(d.getId(), d.getName());
        return repo.findBySchoolIdAndActiveTrueOrderByNameAsc(schoolId).stream()
                .map(e -> toView(e, deptNames)).toList();
    }

    @Transactional(readOnly = true)
    public EmployeeView get(UUID id) {
        return toView(find(id));
    }

    @Transactional
    public EmployeeView create(EmployeeUpsert in) {
        UUID schoolId = TenantContext.get();
        Employee e = new Employee();
        e.setSchoolId(schoolId);
        e.setCode(nextCode(schoolId));
        apply(e, in);
        e.setInitials(initials(in.name()));
        Employee saved = repo.save(e);
        // Fire-and-forget e-mail notification (no-op unless SMTP is configured).
        mail.notifyUserCreated(schoolId, saved.getName(), saved.getEmail());
        return toView(saved);
    }

    @Transactional
    public EmployeeView update(UUID id, EmployeeUpsert in) {
        Employee e = find(id);
        apply(e, in);
        e.setInitials(initials(in.name()));
        return toView(repo.save(e));
    }

    @Transactional
    public void delete(UUID id) {
        Employee e = find(id);
        e.setActive(false);   // soft delete — keeps payroll/academic history intact
        repo.save(e);
    }

    private Employee find(UUID id) {
        return repo.findByIdAndSchoolId(id, TenantContext.get())
                .orElseThrow(() -> ApiException.notFound("Employé"));
    }

    private void apply(Employee e, EmployeeUpsert in) {
        e.setName(in.name());
        e.setSex(blankToNull(in.sex()));   // "" would violate CHECK (sex IN ('M','F'))
        if (in.type() != null && !in.type().isBlank()) e.setType(in.type());
        e.setEmail(blankToNull(in.email()));
        e.setPhone(blankToNull(in.phone()));
        e.setFormClass(blankToNull(in.formClass()));
        if (in.departmentId() != null
                && departments.findByIdAndSchoolId(in.departmentId(), TenantContext.get()).isEmpty()) {
            throw ApiException.badRequest("Département inconnu");
        }
        e.setDepartmentId(in.departmentId());
        e.setMonthlySalary(in.monthlySalary());
        e.setHourlyRate(in.hourlyRate());
        e.setRoles(in.roles() == null ? new HashSet<>() : new HashSet<>(in.roles()));
    }

    /** Turn empty/blank input into null so optional, CHECK-constrained columns stay valid. */
    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }

    private String nextCode(UUID schoolId) {
        long n = repo.countBySchoolId(schoolId) + 1;
        String code;
        do {
            code = "EMP-" + String.format("%03d", n++);
        } while (repo.existsBySchoolIdAndCode(schoolId, code));
        return code;
    }

    private String initials(String name) {
        if (name == null || name.isBlank()) return null;
        StringBuilder sb = new StringBuilder();
        for (String word : name.trim().split("\\s+")) {
            if (!word.isEmpty() && sb.length() < 2) {
                sb.append(Character.toUpperCase(word.charAt(0)));
            }
        }
        return sb.length() == 0 ? null : sb.toString();
    }

    private EmployeeView toView(Employee e) {
        String deptName = e.getDepartmentId() == null ? null
                : departments.findByIdAndSchoolId(e.getDepartmentId(), e.getSchoolId())
                        .map(Department::getName).orElse(null);
        return toView(e, deptName);
    }

    private EmployeeView toView(Employee e, Map<UUID, String> deptNames) {
        return toView(e, e.getDepartmentId() == null ? null : deptNames.get(e.getDepartmentId()));
    }

    private EmployeeView toView(Employee e, String deptName) {
        // Copy the lazy @ElementCollection into a plain set while the session is
        // still open, otherwise JSON serialization fails with LazyInitializationException.
        Set<String> roles = e.getRoles() == null ? Set.of() : new HashSet<>(e.getRoles());
        return new EmployeeView(e.getId(), e.getCode(), e.getName(), e.getInitials(),
                e.getSex(), e.getType(), e.getEmail(), e.getPhone(), e.getFormClass(),
                e.getDepartmentId(), deptName,
                e.getMonthlySalary(), e.getHourlyRate(), roles, e.isActive());
    }
}

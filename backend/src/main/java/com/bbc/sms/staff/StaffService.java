package com.bbc.sms.staff;

import com.bbc.sms.platform.common.ApiException;
import com.bbc.sms.platform.tenant.TenantContext;
import com.bbc.sms.staff.dto.StaffDtos.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.UUID;

@Service
public class StaffService {

    private final EmployeeRepository repo;

    public StaffService(EmployeeRepository repo) { this.repo = repo; }

    @Transactional(readOnly = true)
    public List<EmployeeView> list() {
        UUID schoolId = TenantContext.get();
        return repo.findBySchoolIdAndActiveTrueOrderByNameAsc(schoolId).stream()
                .map(this::toView).toList();
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
        return toView(repo.save(e));
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
        e.setSex(in.sex());
        if (in.type() != null && !in.type().isBlank()) e.setType(in.type());
        e.setEmail(in.email());
        e.setPhone(in.phone());
        e.setFormClass(in.formClass());
        e.setMonthlySalary(in.monthlySalary());
        e.setHourlyRate(in.hourlyRate());
        e.setRoles(in.roles() == null ? new HashSet<>() : new HashSet<>(in.roles()));
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
        return new EmployeeView(e.getId(), e.getCode(), e.getName(), e.getInitials(),
                e.getSex(), e.getType(), e.getEmail(), e.getPhone(), e.getFormClass(),
                e.getMonthlySalary(), e.getHourlyRate(), e.getRoles(), e.isActive());
    }
}

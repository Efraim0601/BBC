package com.bbc.sms.hr;

import com.bbc.sms.hr.dto.HrDtos.*;
import com.bbc.sms.platform.common.ApiException;
import com.bbc.sms.platform.security.AuthorizationPolicyService;
import com.bbc.sms.platform.security.PolicyResourceContext;
import com.bbc.sms.platform.tenant.TenantContext;
import com.bbc.sms.staff.Employee;
import com.bbc.sms.staff.EmployeeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Departments + leave management — the HR / Operations review gaps. */
@Service
public class HrService {

    private final DepartmentRepository departments;
    private final LeaveRequestRepository leaves;
    private final EmployeeRepository employees;
    private final AuthorizationPolicyService policy;

    public HrService(DepartmentRepository departments, LeaveRequestRepository leaves, EmployeeRepository employees,
                     AuthorizationPolicyService policy) {
        this.departments = departments;
        this.leaves = leaves;
        this.employees = employees;
        this.policy = policy;
    }

    // ---- Departments --------------------------------------------------------

    @Transactional(readOnly = true)
    public List<DepartmentView> listDepartments() {
        requireSchool("HR_VIEW");
        UUID schoolId = TenantContext.get();
        Map<UUID, String> names = employeeNames(schoolId);
        return departments.findBySchoolIdOrderByName(schoolId).stream()
                .map(d -> toView(d, names))
                .toList();
    }

    @Transactional
    public DepartmentView createDepartment(DepartmentUpsert in) {
        requireSchool("HR_MANAGE");
        UUID schoolId = TenantContext.get();
        String name = in.name().trim();
        if (departments.existsBySchoolIdAndName(schoolId, name)) {
            throw ApiException.conflict("Un département « " + name + " » existe déjà");
        }
        Department d = new Department();
        d.setSchoolId(schoolId);
        d.setName(name);
        d.setHeadEmployeeId(validateHead(schoolId, in.headEmployeeId()));
        return toView(departments.save(d), employeeNames(schoolId));
    }

    @Transactional
    public DepartmentView updateDepartment(UUID id, DepartmentUpsert in) {
        requireSchool("HR_MANAGE");
        UUID schoolId = TenantContext.get();
        Department d = departments.findByIdAndSchoolId(id, schoolId)
                .orElseThrow(() -> ApiException.notFound("Département"));
        String name = in.name().trim();
        if (!name.equalsIgnoreCase(d.getName()) && departments.existsBySchoolIdAndName(schoolId, name)) {
            throw ApiException.conflict("Un département « " + name + " » existe déjà");
        }
        d.setName(name);
        d.setHeadEmployeeId(validateHead(schoolId, in.headEmployeeId()));
        return toView(departments.save(d), employeeNames(schoolId));
    }

    @Transactional
    public void deleteDepartment(UUID id) {
        requireSchool("HR_MANAGE");
        UUID schoolId = TenantContext.get();
        Department d = departments.findByIdAndSchoolId(id, schoolId)
                .orElseThrow(() -> ApiException.notFound("Département"));
        if (employees.countBySchoolIdAndDepartmentIdAndActiveTrue(schoolId, id) > 0) {
            throw ApiException.conflict("Des employés sont rattachés à ce département — réaffectez-les d'abord");
        }
        departments.delete(d);
    }

    // ---- Leave --------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<LeaveView> listLeaves() {
        requireSchool("HR_VIEW");
        UUID schoolId = TenantContext.get();
        Map<UUID, String> names = employeeNames(schoolId);
        return leaves.findBySchoolIdOrderByCreatedAtDesc(schoolId).stream()
                .map(l -> toView(l, names))
                .toList();
    }

    @Transactional
    public LeaveView createLeave(LeaveCreate in) {
        requireSchool("HR_MANAGE");
        UUID schoolId = TenantContext.get();
        employees.findByIdAndSchoolId(in.employeeId(), schoolId)
                .orElseThrow(() -> ApiException.badRequest("Employé inconnu"));
        if (in.endDate().isBefore(in.startDate())) {
            throw ApiException.badRequest("La date de fin précède la date de début");
        }
        LeaveRequest l = new LeaveRequest();
        l.setSchoolId(schoolId);
        l.setEmployeeId(in.employeeId());
        l.setType(in.type());
        l.setStartDate(in.startDate());
        l.setEndDate(in.endDate());
        l.setDays((int) (ChronoUnit.DAYS.between(in.startDate(), in.endDate()) + 1));
        l.setReason(in.reason());
        l.setStatus("pending");
        return toView(leaves.save(l), employeeNames(schoolId));
    }

    @Transactional
    public LeaveView decideLeave(UUID id, LeaveDecision in) {
        requireSchool("HR_MANAGE");
        UUID schoolId = TenantContext.get();
        LeaveRequest l = leaves.findByIdAndSchoolId(id, schoolId)
                .orElseThrow(() -> ApiException.notFound("Demande de congé"));
        String status = in.status();
        if (!"approved".equals(status) && !"rejected".equals(status)) {
            throw ApiException.badRequest("Statut invalide");
        }
        l.setStatus(status);
        l.setDecidedAt(OffsetDateTime.now());
        return toView(leaves.save(l), employeeNames(schoolId));
    }

    // ---- helpers ------------------------------------------------------------

    private UUID validateHead(UUID schoolId, UUID headId) {
        if (headId == null) return null;
        employees.findByIdAndSchoolId(headId, schoolId)
                .orElseThrow(() -> ApiException.badRequest("Responsable inconnu"));
        return headId;
    }

    private Map<UUID, String> employeeNames(UUID schoolId) {
        Map<UUID, String> names = new HashMap<>();
        for (Employee e : employees.findBySchoolId(schoolId)) names.put(e.getId(), e.getName());
        return names;
    }

    private DepartmentView toView(Department d, Map<UUID, String> names) {
        long count = employees.countBySchoolIdAndDepartmentIdAndActiveTrue(d.getSchoolId(), d.getId());
        return new DepartmentView(d.getId(), d.getName(), d.getHeadEmployeeId(),
                d.getHeadEmployeeId() == null ? null : names.get(d.getHeadEmployeeId()), count);
    }

    private LeaveView toView(LeaveRequest l, Map<UUID, String> names) {
        return new LeaveView(l.getId(), l.getEmployeeId(), names.get(l.getEmployeeId()),
                l.getType(), l.getStartDate(), l.getEndDate(), l.getDays(), l.getReason(),
                l.getStatus(), l.getDecidedAt());
    }

    private void requireSchool(String action) {
        policy.require(action, new PolicyResourceContext(TenantContext.get(), null, java.time.LocalDate.now(),
                null, null, null, null, null, null, null, null, null));
    }
}

package com.bbc.sms.staff;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EmployeeRepository extends JpaRepository<Employee, UUID> {
    List<Employee> findBySchoolIdAndActiveTrueOrderByNameAsc(UUID schoolId);
    Optional<Employee> findByIdAndSchoolId(UUID id, UUID schoolId);
    long countBySchoolId(UUID schoolId);
    boolean existsBySchoolIdAndCode(UUID schoolId, String code);
}

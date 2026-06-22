package com.bbc.sms.hr;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DepartmentRepository extends JpaRepository<Department, UUID> {
    List<Department> findBySchoolIdOrderByName(UUID schoolId);
    Optional<Department> findByIdAndSchoolId(UUID id, UUID schoolId);
    boolean existsBySchoolIdAndName(UUID schoolId, String name);
}

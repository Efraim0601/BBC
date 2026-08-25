package com.bbc.sms.staff;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StaffDocumentRepository extends JpaRepository<StaffDocument, UUID> {

    List<StaffDocument> findBySchoolIdAndEmployeeIdOrderByCreatedAtDesc(UUID schoolId, UUID employeeId);

    Optional<StaffDocument> findByIdAndSchoolIdAndEmployeeId(UUID id, UUID schoolId, UUID employeeId);
}

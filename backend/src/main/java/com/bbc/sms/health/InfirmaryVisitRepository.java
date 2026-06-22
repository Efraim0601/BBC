package com.bbc.sms.health;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InfirmaryVisitRepository extends JpaRepository<InfirmaryVisit, UUID> {

    List<InfirmaryVisit> findBySchoolIdAndStudentIdOrderByVisitDateDesc(UUID schoolId, UUID studentId);

    Optional<InfirmaryVisit> findByIdAndSchoolId(UUID id, UUID schoolId);
}

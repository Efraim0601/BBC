package com.bbc.sms.foundation.session;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AcademicTermRepository extends JpaRepository<AcademicTerm, UUID> {
    List<AcademicTerm> findBySchoolIdAndAcademicSessionIdOrderBySequenceNo(UUID schoolId, UUID sessionId);
    Optional<AcademicTerm> findByIdAndSchoolId(UUID id, UUID schoolId);
}

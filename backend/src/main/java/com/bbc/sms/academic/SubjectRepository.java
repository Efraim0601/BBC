package com.bbc.sms.academic;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SubjectRepository extends JpaRepository<Subject, UUID> {
    List<Subject> findBySchoolIdOrderByCode(UUID schoolId);
    Optional<Subject> findByIdAndSchoolId(UUID id, UUID schoolId);
    Optional<Subject> findBySchoolIdAndCode(UUID schoolId, String code);
    Optional<Subject> findBySchoolIdAndCodeAndSubsystem(UUID schoolId, String code, String subsystem);
    List<Subject> findAllBySchoolIdAndCode(UUID schoolId, String code);
    boolean existsBySchoolIdAndCode(UUID schoolId, String code);
}

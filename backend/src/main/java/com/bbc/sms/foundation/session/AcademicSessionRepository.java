package com.bbc.sms.foundation.session;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AcademicSessionRepository extends JpaRepository<AcademicSession, UUID> {
    List<AcademicSession> findBySchoolIdOrderByStartDateDesc(UUID schoolId);
    Optional<AcademicSession> findByIdAndSchoolId(UUID id, UUID schoolId);
    Optional<AcademicSession> findBySchoolIdAndCurrentTrue(UUID schoolId);
    boolean existsBySchoolIdAndCodeIgnoreCase(UUID schoolId, String code);

    @Modifying(clearAutomatically = true)
    @Query("update AcademicSession s set s.current=false where s.schoolId=:schoolId and s.id<>:keepId and s.current=true")
    int clearCurrent(UUID schoolId, UUID keepId);
}

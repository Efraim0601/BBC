package com.bbc.sms.finance.charges;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ChargeGenerationJobRepository extends JpaRepository<ChargeGenerationJob, UUID> {
    Optional<ChargeGenerationJob> findByIdAndSchoolId(UUID id, UUID schoolId);
    Optional<ChargeGenerationJob> findBySchoolIdAndIdempotencyKey(UUID schoolId, String idempotencyKey);
    List<ChargeGenerationJob> findBySchoolIdOrderByCreatedAtDesc(UUID schoolId);
}

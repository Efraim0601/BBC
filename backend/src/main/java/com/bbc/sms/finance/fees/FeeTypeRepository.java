package com.bbc.sms.finance.fees;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FeeTypeRepository extends JpaRepository<FeeType, UUID> {
    List<FeeType> findBySchoolIdOrderByCodeAsc(UUID schoolId);
    Optional<FeeType> findByIdAndSchoolId(UUID id, UUID schoolId);
    Optional<FeeType> findBySchoolIdAndCode(UUID schoolId, String code);
    boolean existsBySchoolIdAndCode(UUID schoolId, String code);
}

package com.bbc.sms.finance.accounting;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ChartOfAccountRepository extends JpaRepository<ChartOfAccount, UUID> {
    List<ChartOfAccount> findBySchoolIdOrderByCodeAsc(UUID schoolId);
    List<ChartOfAccount> findBySchoolIdAndActiveTrueOrderByCodeAsc(UUID schoolId);
    Optional<ChartOfAccount> findByIdAndSchoolId(UUID id, UUID schoolId);
    Optional<ChartOfAccount> findBySchoolIdAndCode(UUID schoolId, String code);
    boolean existsBySchoolIdAndCode(UUID schoolId, String code);
}

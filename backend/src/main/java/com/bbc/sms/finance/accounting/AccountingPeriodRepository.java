package com.bbc.sms.finance.accounting;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AccountingPeriodRepository extends JpaRepository<AccountingPeriod, UUID> {
    List<AccountingPeriod> findBySchoolIdOrderByStartDateDesc(UUID schoolId);
    Optional<AccountingPeriod> findByIdAndSchoolId(UUID id, UUID schoolId);
    Optional<AccountingPeriod> findFirstBySchoolIdAndStatusAndStartDateLessThanEqualAndEndDateGreaterThanEqualOrderByStartDateDesc(
            UUID schoolId, String status, LocalDate date1, LocalDate date2);
    Optional<AccountingPeriod> findFirstBySchoolIdAndAcademicSessionIdAndStatusAndStartDateLessThanEqualAndEndDateGreaterThanEqualOrderByStartDateDesc(
            UUID schoolId, UUID academicSessionId, String status, LocalDate date1, LocalDate date2);
    boolean existsBySchoolIdAndCode(UUID schoolId, String code);
}

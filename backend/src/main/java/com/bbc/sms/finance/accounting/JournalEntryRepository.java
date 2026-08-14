package com.bbc.sms.finance.accounting;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface JournalEntryRepository extends JpaRepository<JournalEntry, UUID> {
    List<JournalEntry> findBySchoolIdOrderByEntryDateDescNumberDesc(UUID schoolId);
    List<JournalEntry> findBySchoolIdAndEntryDateBetweenOrderByEntryDateAscNumberAsc(
            UUID schoolId, LocalDate from, LocalDate to);
    Optional<JournalEntry> findByIdAndSchoolId(UUID id, UUID schoolId);
    Optional<JournalEntry> findBySchoolIdAndSourceEventKey(UUID schoolId, String sourceEventKey);
    Optional<JournalEntry> findBySchoolIdAndReversalOfId(UUID schoolId, UUID reversalOfId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select j from JournalEntry j where j.id = :id and j.schoolId = :schoolId")
    Optional<JournalEntry> findForUpdate(@Param("id") UUID id, @Param("schoolId") UUID schoolId);
}

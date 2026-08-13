package com.bbc.sms.finance.accounting;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface JournalLineRepository extends JpaRepository<JournalLine, UUID> {
    List<JournalLine> findBySchoolIdAndJournalEntryIdOrderByLineNumberAsc(UUID schoolId, UUID journalEntryId);
    void deleteBySchoolIdAndJournalEntryId(UUID schoolId, UUID journalEntryId);
}

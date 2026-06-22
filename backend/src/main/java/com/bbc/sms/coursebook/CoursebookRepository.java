package com.bbc.sms.coursebook;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CoursebookRepository extends JpaRepository<CoursebookEntry, UUID> {

    List<CoursebookEntry> findBySchoolIdAndClassNameOrderByEntryDateDesc(UUID schoolId, String className);

    Optional<CoursebookEntry> findByIdAndSchoolId(UUID id, UUID schoolId);
}

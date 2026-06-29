package com.bbc.sms.classkit;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ClassResourceItemRepository extends JpaRepository<ClassResourceItem, UUID> {
    List<ClassResourceItem> findBySchoolIdAndClassIdAndKindOrderByPositionAscLabelAsc(
            UUID schoolId, UUID classId, String kind);
    Optional<ClassResourceItem> findByIdAndSchoolId(UUID id, UUID schoolId);
}

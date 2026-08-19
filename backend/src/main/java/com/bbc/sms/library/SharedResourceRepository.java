package com.bbc.sms.library;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SharedResourceRepository extends JpaRepository<SharedResource, UUID> {

    Optional<SharedResource> findByIdAndSchoolId(UUID id, UUID schoolId);

    List<SharedResource> findBySchoolIdOrderByCreatedAtDesc(UUID schoolId);
}

package com.bbc.sms.identity;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface SchoolRepository extends JpaRepository<School, UUID> {
    Optional<School> findByCode(String code);
    Optional<School> findByStaffPortalSlug(String staffPortalSlug);
}

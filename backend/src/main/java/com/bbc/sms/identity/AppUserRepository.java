package com.bbc.sms.identity;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AppUserRepository extends JpaRepository<AppUser, UUID> {
    List<AppUser> findByUsernameAndActiveTrue(String username);
    Optional<AppUser> findBySchoolIdAndUsernameAndActiveTrue(UUID schoolId, String username);
}

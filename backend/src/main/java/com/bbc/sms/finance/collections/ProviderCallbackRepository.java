package com.bbc.sms.finance.collections;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ProviderCallbackRepository extends JpaRepository<ProviderCallback, UUID> {
    Optional<ProviderCallback> findBySchoolIdAndProviderCodeAndEventId(
            UUID schoolId, String providerCode, String eventId);
}

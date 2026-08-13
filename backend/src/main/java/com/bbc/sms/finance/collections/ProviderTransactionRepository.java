package com.bbc.sms.finance.collections;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ProviderTransactionRepository extends JpaRepository<ProviderTransaction, UUID> {
    Optional<ProviderTransaction> findBySchoolIdAndProviderCodeAndExternalReference(
            UUID schoolId, String providerCode, String externalReference);
}

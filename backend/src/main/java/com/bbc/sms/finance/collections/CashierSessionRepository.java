package com.bbc.sms.finance.collections;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CashierSessionRepository extends JpaRepository<CashierSession, UUID> {
    Optional<CashierSession> findByIdAndSchoolId(UUID id, UUID schoolId);
    Optional<CashierSession> findBySchoolIdAndCashierUserIdAndStatus(UUID schoolId, UUID cashierUserId, String status);
    List<CashierSession> findBySchoolIdOrderByOpenedAtDesc(UUID schoolId);
}

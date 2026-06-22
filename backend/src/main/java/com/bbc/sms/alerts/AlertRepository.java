package com.bbc.sms.alerts;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface AlertRepository extends JpaRepository<Alert, UUID> {

    Optional<Alert> findByIdAndSchoolId(UUID id, UUID schoolId);
}

package com.bbc.sms.attendance;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DeviceRepository extends JpaRepository<Device, UUID> {
    Optional<Device> findByIdAndApiKeyAndActiveTrue(UUID id, String apiKey);

    List<Device> findBySchoolIdOrderByLabel(UUID schoolId);
}

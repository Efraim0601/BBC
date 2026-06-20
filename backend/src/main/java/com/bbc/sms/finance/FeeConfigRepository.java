package com.bbc.sms.finance;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface FeeConfigRepository extends JpaRepository<FeeConfig, UUID> {
    List<FeeConfig> findBySchoolId(UUID schoolId);
    List<FeeConfig> findBySchoolIdAndLevel(UUID schoolId, String level);
}

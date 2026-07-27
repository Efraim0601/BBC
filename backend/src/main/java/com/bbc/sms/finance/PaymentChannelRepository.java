package com.bbc.sms.finance;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentChannelRepository extends JpaRepository<PaymentChannel, UUID> {

    List<PaymentChannel> findBySchoolIdOrderBySortOrderAscLabelFrAsc(UUID schoolId);

    List<PaymentChannel> findBySchoolIdAndEnabledTrueOrderBySortOrderAscLabelFrAsc(UUID schoolId);

    Optional<PaymentChannel> findBySchoolIdAndCode(UUID schoolId, String code);
}

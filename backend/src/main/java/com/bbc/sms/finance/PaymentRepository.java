package com.bbc.sms.finance;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {
    List<Payment> findBySchoolIdOrderByPaidOnDesc(UUID schoolId);
    List<Payment> findBySchoolIdAndPaidOnBetween(UUID schoolId, LocalDate from, LocalDate to);
    long countBySchoolId(UUID schoolId);
}

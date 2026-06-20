package com.bbc.sms.finance;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ExpenseRepository extends JpaRepository<Expense, UUID> {
    List<Expense> findBySchoolIdOrderBySpentOnDesc(UUID schoolId);
}

package com.bbc.sms.academic;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SubjectRepository extends JpaRepository<Subject, UUID> {
    List<Subject> findBySchoolIdOrderByCode(UUID schoolId);
}

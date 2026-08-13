package com.bbc.sms.student;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.Collection;

public interface StudentRepository extends JpaRepository<Student, UUID> {
    List<Student> findBySchoolIdAndActiveTrueOrderByLastNameAsc(UUID schoolId);
    List<Student> findBySchoolIdAndClassNameAndActiveTrueOrderByLastNameAsc(UUID schoolId, String className);
    List<Student> findBySchoolIdAndIdInAndActiveTrue(UUID schoolId, Collection<UUID> ids);
    Optional<Student> findByIdAndSchoolId(UUID id, UUID schoolId);
    Optional<Student> findBySchoolIdAndMatriculeAndActiveTrue(UUID schoolId, String matricule);
    boolean existsBySchoolIdAndMatricule(UUID schoolId, String matricule);
    boolean existsBySchoolIdAndNiuAndActiveTrue(UUID schoolId, String niu);
    long countBySchoolIdAndActiveTrue(UUID schoolId);
    long countBySchoolIdAndClassIdAndActiveTrue(UUID schoolId, UUID classId);
}

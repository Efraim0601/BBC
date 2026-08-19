package com.bbc.sms.student;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StudentRepository extends JpaRepository<Student, UUID> {

    /**
     * Les seules colonnes dont la recherche de doublons a besoin. Les noms se
     * comparent normalisés (accents, casse, tirets) — ce que le SQL ne sait pas
     * faire ici — donc le tri se fait en mémoire ; n'en charger qu'une projection
     * garde ce balayage léger même sur un établissement entier.
     */
    interface DuplicateRow {
        UUID getId();
        String getMatricule();
        String getFirstName();
        String getLastName();
        LocalDate getDob();
        String getNiu();
        UUID getClassId();
        String getClassName();
        String getLevel();
        String getSubsystem();
    }

    List<DuplicateRow> findProjectedBySchoolIdAndActiveTrue(UUID schoolId);

    List<Student> findBySchoolIdAndActiveTrueOrderByLastNameAsc(UUID schoolId);
    List<Student> findBySchoolIdAndClassNameAndActiveTrueOrderByLastNameAsc(UUID schoolId, String className);
    List<Student> findBySchoolIdAndClassIdAndActiveTrue(UUID schoolId, UUID classId);

    /**
     * The three bulk reads an import needs up front. Loading them once turns the
     * per-row lookups into hash probes: a register of any size then costs a handful
     * of queries plus its inserts, instead of two or three round-trips per line.
     */
    @Query("select s.matricule from Student s where s.schoolId = ?1")
    List<String> findMatriculesBySchoolId(UUID schoolId);

    @Query("select s.niu from Student s where s.schoolId = ?1 and s.active = true and s.niu is not null")
    List<String> findActiveNiusBySchoolId(UUID schoolId);
    Optional<Student> findByIdAndSchoolId(UUID id, UUID schoolId);
    Optional<Student> findBySchoolIdAndMatriculeAndActiveTrue(UUID schoolId, String matricule);
    boolean existsBySchoolIdAndMatricule(UUID schoolId, String matricule);
    long countBySchoolIdAndActiveTrue(UUID schoolId);
    long countBySchoolIdAndClassIdAndActiveTrue(UUID schoolId, UUID classId);
    long countBySchoolIdAndClassNameAndActiveTrue(UUID schoolId, String className);
}

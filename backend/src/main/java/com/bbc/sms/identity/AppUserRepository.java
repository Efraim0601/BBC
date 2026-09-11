package com.bbc.sms.identity;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

public interface AppUserRepository extends JpaRepository<AppUser, UUID> {
    String ACTIVE_LOGIN = "select u from AppUser u where u.id = :id and u.active = true "
            + "and (u.employeeId is null or exists (select e.id from Employee e "
            + "where e.id = u.employeeId and e.schoolId = u.schoolId and e.active = true))";

    @Query(ACTIVE_LOGIN)
    Optional<AppUser> findActiveLoginById(@Param("id") UUID id);

    // Serialize password attempts so concurrent failures cannot evade the lockout counter.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(ACTIVE_LOGIN)
    Optional<AppUser> lockActiveLoginById(@Param("id") UUID id);

    List<AppUser> findByUsernameAndActiveTrue(String username);
    Optional<AppUser> findBySchoolIdAndUsernameAndActiveTrue(UUID schoolId, String username);
    List<AppUser> findByUsernameInAndActiveTrue(Collection<String> usernames);
    List<AppUser> findBySchoolIdAndUsernameInAndActiveTrue(UUID schoolId, Collection<String> usernames);
    boolean existsBySchoolIdAndUsernameIn(UUID schoolId, Collection<String> usernames);
    Optional<AppUser> findByEmployeeId(UUID employeeId);
    List<AppUser> findBySchoolIdAndEmployeeIdNotNull(UUID schoolId);
    boolean existsBySchoolIdAndUsername(UUID schoolId, String username);
    boolean existsBySchoolIdAndUsernameIgnoreCase(UUID schoolId, String username);
    Optional<AppUser> findBySchoolIdAndNormalizedEmail(UUID schoolId, String normalizedEmail);
    List<AppUser> findByNormalizedEmail(String normalizedEmail);
}

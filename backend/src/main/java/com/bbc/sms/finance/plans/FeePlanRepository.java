package com.bbc.sms.finance.plans;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FeePlanRepository extends JpaRepository<FeePlan, UUID> {
    Optional<FeePlan> findByIdAndSchoolId(UUID id, UUID schoolId);
    List<FeePlan> findBySchoolIdAndAcademicSessionIdOrderByLevelAscSubsystemAscSchoolClassIdAscPlanVersionNoDesc(
            UUID schoolId, UUID academicSessionId);
    List<FeePlan> findBySchoolIdAndLifecycleOrderByAcademicSessionIdAscLevelAscSubsystemAsc(
            UUID schoolId, String lifecycle);
    @Query("select p from FeePlan p where p.schoolId = :schoolId and p.academicSessionId = :sessionId "
            + "and p.scopeType = :scopeType and p.level = :level and p.subsystem = :subsystem "
            + "and ((:classId is null and p.schoolClassId is null) or p.schoolClassId = :classId) "
            + "and p.lifecycle = :lifecycle")
    List<FeePlan> findForScope(UUID schoolId, UUID sessionId, String scopeType, String level,
                               String subsystem, UUID classId, String lifecycle);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from FeePlan p where p.schoolId = :schoolId and p.academicSessionId = :sessionId "
            + "and p.scopeType = :scopeType and p.level = :level and p.subsystem = :subsystem "
            + "and ((:classId is null and p.schoolClassId is null) or p.schoolClassId = :classId) "
            + "and p.lifecycle = 'ACTIVE'")
    Optional<FeePlan> findActiveForUpdate(UUID schoolId, UUID sessionId, String scopeType, String level,
                                           String subsystem, UUID classId);
    @Query("select p from FeePlan p where p.schoolId = :schoolId and p.academicSessionId = :sessionId "
            + "and p.scopeType = :scopeType and p.level = :level and p.subsystem = :subsystem "
            + "and ((:classId is null and p.schoolClassId is null) or p.schoolClassId = :classId) "
            + "order by p.planVersionNo desc")
    List<FeePlan> findVersions(UUID schoolId, UUID sessionId, String scopeType, String level,
                               String subsystem, UUID classId);
    @Query("select coalesce(max(p.planVersionNo), 0) from FeePlan p where p.schoolId = :schoolId "
            + "and p.academicSessionId = :sessionId and p.scopeType = :scopeType and p.level = :level "
            + "and p.subsystem = :subsystem and ((:classId is null and p.schoolClassId is null) "
            + "or p.schoolClassId = :classId)")
    int nextVersion(UUID schoolId, UUID sessionId, String scopeType, String level,
                    String subsystem, UUID classId);
}

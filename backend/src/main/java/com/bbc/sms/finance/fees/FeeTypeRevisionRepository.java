package com.bbc.sms.finance.fees;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FeeTypeRevisionRepository extends JpaRepository<FeeTypeRevision, UUID> {
    List<FeeTypeRevision> findBySchoolIdAndFeeTypeIdOrderByRevisionNoDesc(UUID schoolId, UUID feeTypeId);
    Optional<FeeTypeRevision> findByIdAndSchoolId(UUID id, UUID schoolId);
    Optional<FeeTypeRevision> findBySchoolIdAndFeeTypeIdAndRevisionNo(UUID schoolId, UUID feeTypeId, int revisionNo);
    Optional<FeeTypeRevision> findTopBySchoolIdAndFeeTypeIdAndRevisionStatusOrderByRevisionNoDesc(
            UUID schoolId, UUID feeTypeId, String revisionStatus);
    int countBySchoolIdAndFeeTypeId(UUID schoolId, UUID feeTypeId);
}

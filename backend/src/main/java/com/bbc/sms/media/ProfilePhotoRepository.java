package com.bbc.sms.media;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ProfilePhotoRepository extends JpaRepository<ProfilePhoto, ProfilePhoto.Key> {
    Optional<ProfilePhoto> findByOwnerTypeAndOwnerIdAndSchoolId(String ownerType, UUID ownerId, UUID schoolId);
    void deleteByOwnerTypeAndOwnerIdAndSchoolId(String ownerType, UUID ownerId, UUID schoolId);
    boolean existsByOwnerTypeAndOwnerIdAndSchoolId(String ownerType, UUID ownerId, UUID schoolId);
}

package com.bbc.sms.academic;

import com.bbc.sms.academic.dto.AcademicDtos.AuthoritativeSnapshotView;

import java.util.UUID;

/**
 * Read-only boundary used by the snapshot-only renderer.  Keeping the
 * renderer dependent on this contract lets the publication coordinator call
 * it in the same transaction without creating a service dependency cycle.
 */
public interface AuthoritativeBulletinSnapshotReader {
    AuthoritativeSnapshotView authoritativeById(UUID id);
}

package com.bbc.sms.academic;

import com.bbc.sms.academic.dto.AcademicDtos.*;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AuthoritativeSnapshotContractTest {
    @Test
    void contractCarriesFrozenIdentityPhotoCurriculumAndPreciseDisplayValues() {
        UUID studentId = UUID.randomUUID();
        UUID photoId = UUID.randomUUID();
        UUID curriculumId = UUID.randomUUID();
        UUID schoolId = UUID.randomUUID();
        SnapshotStudentView student = new SnapshotStudentView(studentId, "DOE Jane", "Jane", "DOE", "BBC-42",
                null, "Maroua", "F", true);
        SnapshotPhotoView photo = new SnapshotPhotoView(photoId, "image/jpeg", "abc", 512, 512, "PHOTO", Instant.now());
        SnapshotCurriculumView curriculum = new SnapshotCurriculumView(curriculumId, 3, "PUBLISHED", "curriculum-hash", List.of());
        SnapshotResultView result = new SnapshotResultView(new BigDecimal("13.333333333333333333"),
                new BigDecimal("13.33"), 1, 2, List.of(), List.of(), null, List.of(), List.of());
        AuthoritativeSnapshotView contract = new AuthoritativeSnapshotView(1, schoolId, UUID.randomUUID(), UUID.randomUUID(),
                "T1_RESULT", "Term 1", "TERM", student,
                new SnapshotEnrollmentView(UUID.randomUUID(), UUID.randomUUID(), "4eme A", "secondary", "FR", 2),
                null, new SnapshotStaffView(null, List.of()), photo,
                new SnapshotSchoolView(schoolId, "BBC", "BBC", "MINESEC", null, "Maroua", "Cameroun", null, null, null, null),
                curriculum, result, null, null, null, null, "AcademicCalculationEngine/v2-live-dependencies", "DEFAULT",
                null, null, List.of(new SnapshotSourceVersionView("CURRICULUM_VERSION", curriculumId, 3L, "curriculum-hash", "curriculum")),
                "canonical-hash");

        assertThat(contract.student().matricule()).isEqualTo("BBC-42");
        assertThat(contract.student().repeater()).isTrue();
        assertThat(contract.profilePhoto().assetVersionId()).isEqualTo(photoId);
        assertThat(contract.profilePhoto().fallbackDecision()).isEqualTo("PHOTO");
        assertThat(contract.curriculum().versionId()).isEqualTo(curriculumId);
        assertThat(contract.result().preciseAverage()).isEqualByComparingTo("13.333333333333333333");
        assertThat(contract.result().displayAverage()).isEqualByComparingTo("13.33");
        assertThat(contract.sourceVersions()).singleElement().satisfies(source -> assertThat(source.sourceHash()).isEqualTo("curriculum-hash"));
    }
}

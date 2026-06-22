package com.bbc.sms.documents.dto;

import jakarta.validation.constraints.NotBlank;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public class DocumentDtos {

    /** A single document on a student's file (metadata only). */
    public record DocumentView(
            UUID id,
            UUID studentId,
            String kind,
            String title,
            String note,
            String fileRef,
            Instant createdAt) {}

    /** Create a document metadata record. */
    public record DocumentUpsert(
            @NotBlank String kind,
            @NotBlank String title,
            String note,
            String fileRef) {}

    /** A conseil de classe orientation decision. */
    public record OrientationView(
            UUID id,
            UUID studentId,
            String academicYear,
            String stage,
            String recommendation,
            String decision,
            LocalDate councilDate,
            Instant createdAt) {}

    /** Create an orientation decision. */
    public record OrientationUpsert(
            @NotBlank String academicYear,
            @NotBlank String stage,
            String recommendation,
            String decision,
            LocalDate councilDate) {}

    /**
     * Full document & orientation dossier for one student: identity header plus
     * the document register and the list of orientation decisions.
     */
    public record StudentDossier(
            UUID studentId,
            String studentName,
            String matricule,
            String className,
            List<DocumentView> documents,
            List<OrientationView> orientations) {}
}

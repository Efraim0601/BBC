package com.bbc.sms.student.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public class StudentDtos {

    /** Common safe projection contract for list/roster callers. */
    public interface DirectoryView {
        UUID id();
        String className();
    }

    public record StudentView(
            UUID id,
            String matricule,
            String niu,
            String firstName,
            String lastName,
            String name,
            String sex,
            LocalDate dob,
            String birthplace,
            boolean repeats,
            UUID classId,
            String className,
            String subsystem,
            String level,
            String parentName,
            String parentPhone,
            String fatherName,
            String fatherPhone,
            String fatherEmail,
            String motherName,
            String motherPhone,
            String motherEmail,
            String guardianName,
            String guardianPhone,
            String guardianEmail,
            String guardianRelation,
            int photoHue) implements DirectoryView {}

    /** Educationally necessary teacher projection; never includes guardian contacts or credentials. */
    public record StudentTeacherView(
            UUID id,
            String matricule,
            String niu,
            String firstName,
            String lastName,
            String name,
            String sex,
            LocalDate dob,
            boolean repeats,
            UUID classId,
            String className,
            String subsystem,
            String level,
            int photoHue) implements DirectoryView {}

    public record StudentUpsert(
            String firstName,
            @NotBlank String lastName,
            String niu,
            String sex,
            LocalDate dob,
            String birthplace,
            boolean repeats,
            UUID classId,
            String className,
            String subsystem,
            String level,
            String parentName,
            String parentPhone,
            String fatherName,
            String fatherPhone,
            String fatherEmail,
            String motherName,
            String motherPhone,
            String motherEmail,
            String guardianName,
            String guardianPhone,
            String guardianEmail,
            String guardianRelation,
            /**
             * Enregistrer malgré un homonyme déjà au fichier. Faux par défaut : la
             * fiche est refusée (409) tant que l'utilisateur n'a pas confirmé, ce qui
             * rend le contrôle actif pour tout appelant, écran ou non.
             */
            boolean allowDuplicate) {}

    /**
     * Une fiche déjà au fichier qui ressemble à celle en cours de saisie. Les
     * drapeaux disent POURQUOI elle ressort — même NIU, même nom, même date de
     * naissance, même classe — pour que l'écran puisse nuancer son message au lieu
     * d'afficher un avertissement uniforme.
     */
    public record DuplicateMatch(
            UUID id,
            String matricule,
            String name,
            UUID classId,
            String className,
            String level,
            String subsystem,
            LocalDate dob,
            String niu,
            boolean sameClass,
            boolean sameNiu,
            boolean sameName,
            boolean sameDob) {}

    /**
     * Ce que la recherche de doublons a trouvé. {@code blocking} distingue les cas
     * où l'enregistrement sera refusé sans confirmation (NIU déjà attribué) de la
     * simple mise en garde sur un homonyme.
     */
    public record DuplicateCheckResult(
            boolean exists,
            boolean sameClass,
            boolean blocking,
            String message,
            List<DuplicateMatch> matches) {}

    public record ParentAccountView(
            UUID userId,
            String displayName,
            String username,
            boolean active,
            int childCount) {}

    public record ParentLinkRequest(
            @NotBlank String displayName,
            @NotBlank String username,
            String password) {}

    /** One imported row — same fields as StudentUpsert, minus the class (set for the whole batch). */
    public record StudentImportRow(
            String name,
            String firstName,
            String lastName,
            String niu,
            String sex,
            LocalDate dob,
            String birthplace,
            boolean repeats,
            String parentName,
            String parentPhone,
            String fatherName,
            String fatherPhone,
            String fatherEmail,
            String motherName,
            String motherPhone,
            String motherEmail,
            String guardianName,
            String guardianPhone,
            String guardianEmail,
            String guardianRelation) {}

    public record NewClassSpec(
            @NotBlank String name,
            @NotBlank String subsystem,
            @NotBlank String level) {}

    public record StudentImportRequest(
            UUID classId,
            NewClassSpec newClass,
            @NotEmpty List<StudentImportRow> rows) {}

    public record StudentImportError(int row, String name, String message) {}

    /**
     * Une ligne importée qui a bien créé une fiche, mais dont l'élève porte le nom
     * d'un élève déjà inscrit dans une AUTRE classe. Le doute n'est pas tranchable
     * automatiquement — un transfert de classe et un homonyme se ressemblent — donc
     * la fiche est créée et le cas signalé, à charge de l'école de fusionner.
     */
    public record StudentImportWarning(int row, String name, String message) {}

    /** Les élèves cochés dans la liste, à retirer d'un seul geste. */
    public record BulkDeleteRequest(@NotEmpty List<UUID> ids) {}

    public record BulkDeleteError(UUID id, String message) {}

    /**
     * Ce qu'une suppression groupée a réellement fait. Les échecs sont rendus
     * fiche par fiche — une seule hors périmètre ne doit pas laisser croire que
     * rien n'a été supprimé.
     */
    public record BulkDeleteResult(int deleted, int failed, List<BulkDeleteError> errors) {}

    /**
     * What an import actually did. `updated` counts pupils already on file whose
     * empty fields the register filled in, `unchanged` those it had nothing to add
     * to — telling them apart is what lets a second run be read as "nothing left to
     * complete" rather than as a no-op failure.
     *
     * <p>{@code warnings} liste les fiches créées malgré un homonyme ailleurs dans
     * l'établissement : rien n'a échoué, mais l'école doit y jeter un œil.
     */
    public record StudentImportResult(
            int created,
            int updated,
            int unchanged,
            int fieldsFilled,
            int failed,
            List<StudentImportError> errors,
            List<StudentImportWarning> warnings) {}
}

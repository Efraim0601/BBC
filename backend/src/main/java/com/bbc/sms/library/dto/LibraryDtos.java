package com.bbc.sms.library.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

public class LibraryDtos {

    /**
     * Une ressource telle qu'un écran la présente.
     *
     * <p>{@code canEdit} évite à l'interface de rejouer la règle de cloisonnement :
     * un admin de section voit les documents école-entière sans pouvoir y toucher,
     * et c'est le serveur — seul juge — qui le dit.
     */
    public record ResourceView(
            UUID id,
            String title,
            String description,
            String category,
            String audience,
            String section,
            String fileName,
            String contentType,
            long byteSize,
            boolean published,
            Instant publishedAt,
            String uploadedByName,
            Instant createdAt,
            boolean canEdit) {}

    /**
     * Métadonnées d'un dépôt ou d'une modification. Le fichier voyage à côté,
     * en multipart ; ce corps ne décrit que la fiche.
     *
     * <p>{@code section} nulle vaut « toute l'école » — le serveur refuse ce
     * choix à un administrateur de cycle.
     */
    public record ResourceUpsert(
            @NotBlank @Size(max = 200) String title,
            @Size(max = 4000) String description,
            @NotBlank String category,
            @NotBlank String audience,
            String section,
            boolean published) {}
}

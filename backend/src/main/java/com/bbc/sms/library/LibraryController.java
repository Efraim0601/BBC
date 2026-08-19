package com.bbc.sms.library;

import com.bbc.sms.library.LibraryService.Download;
import com.bbc.sms.library.dto.LibraryDtos.ResourceUpsert;
import com.bbc.sms.library.dto.LibraryDtos.ResourceView;
import jakarta.validation.Valid;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Bibliothèque de ressources — dépôt par la direction, consultation par le
 * personnel. Les parents lisent les mêmes documents par le portail parent
 * ({@code /api/parent/resources}), jamais par ici : la matrice des rôles ne
 * leur ouvre que le module {@code parent}.
 */
@RestController
@RequestMapping("/api/library")
public class LibraryController {

    /** Types que le navigateur sait afficher — les autres se téléchargent. */
    private static final Set<String> INLINE = Set.of(
            "application/pdf", "image/jpeg", "image/png", "image/webp", "image/gif", "text/plain");

    private final LibraryService service;

    public LibraryController(LibraryService service) { this.service = service; }

    @GetMapping
    @PreAuthorize("@perm.can('library','read') and @perm.staffOnly()")
    public List<ResourceView> list() {
        return service.list();
    }

    /**
     * Dépôt : le fichier et sa fiche dans la même requête multipart. La fiche
     * arrive en JSON sous la partie {@code meta} — un formulaire à plat ne saurait
     * pas exprimer « périmètre non renseigné = toute l'école ».
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@perm.can('library','write') and @perm.staffOnly()")
    public ResourceView create(@RequestPart("file") MultipartFile file,
                               @RequestPart("meta") @Valid ResourceUpsert meta) {
        return service.create(file, meta);
    }

    @PutMapping("/{id}")
    @PreAuthorize("@perm.can('library','write') and @perm.staffOnly()")
    public ResourceView update(@PathVariable UUID id, @Valid @RequestBody ResourceUpsert in) {
        return service.update(id, in);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("@perm.can('library','write') and @perm.staffOnly()")
    public void delete(@PathVariable UUID id) {
        service.delete(id);
    }

    @GetMapping("/{id}/file")
    @PreAuthorize("@perm.can('library','read') and @perm.staffOnly()")
    public ResponseEntity<InputStreamResource> file(@PathVariable UUID id) {
        return serve(service.download(id));
    }

    /**
     * Renvoie les octets, en laissant le navigateur afficher ce qu'il sait
     * afficher. Rien n'est mis en cache : une ressource dépubliée ne doit pas
     * survivre dans le cache du poste.
     */
    public static ResponseEntity<InputStreamResource> serve(Download d) {
        ContentDisposition disposition = (INLINE.contains(d.contentType())
                ? ContentDisposition.inline()
                : ContentDisposition.attachment())
                .filename(d.fileName(), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(d.contentType()))
                .contentLength(d.byteSize())
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .body(new InputStreamResource(d.stream()));
    }
}

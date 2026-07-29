package com.bbc.sms.student;

import com.bbc.sms.media.PhotoService;
import com.bbc.sms.media.PhotoUpload;
import com.bbc.sms.media.ProfilePhoto;
import com.bbc.sms.student.dto.StudentDtos.*;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/students")
public class StudentController {

    private final StudentService service;
    private final ParentLinkService parentLinks;
    private final PhotoService photos;

    public StudentController(StudentService service, ParentLinkService parentLinks, PhotoService photos) {
        this.service = service;
        this.parentLinks = parentLinks;
        this.photos = photos;
    }

    // ---- Photo de profil ----------------------------------------------------

    /** Les octets de la photo. 404 quand l'élève n'en a pas — l'interface retombe sur les initiales. */
    @GetMapping("/{id}/photo")
    @PreAuthorize("@perm.can('students','read') and @perm.staffOnly()")
    public ResponseEntity<byte[]> photo(@PathVariable UUID id) {
        service.get(id);   // portée enseignant : 403 si l'élève n'est pas dans ses classes
        ProfilePhoto p = photos.find(PhotoService.STUDENT, id);
        if (p == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(p.getContentType()))
                .cacheControl(CacheControl.noCache())
                .eTag(String.valueOf(p.getUpdatedAt().toInstant().toEpochMilli()))
                .body(p.getBytes());
    }

    /** Selfie ou fichier importé, déjà recadré et compressé par le navigateur. */
    @PutMapping("/{id}/photo")
    @PreAuthorize("@perm.can('students','write') and @perm.staffOnly()")
    public void savePhoto(@PathVariable UUID id, @RequestBody PhotoUpload in) {
        service.get(id);
        photos.save(PhotoService.STUDENT, id, in.dataUrl());
    }

    @DeleteMapping("/{id}/photo")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("@perm.can('students','write') and @perm.staffOnly()")
    public void deletePhoto(@PathVariable UUID id) {
        service.get(id);
        photos.delete(PhotoService.STUDENT, id);
    }

    @GetMapping
    @PreAuthorize("@parcours.allows() and @perm.can('students','read') and @perm.staffOnly()")
    public List<StudentView> list(@RequestParam(required = false) String className) {
        return service.list(className);
    }

    @GetMapping("/{id}")
    @PreAuthorize("@perm.can('students','read') and @perm.staffOnly()")
    public StudentView get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@perm.can('students','write') and @perm.staffOnly()")
    public StudentView create(@Valid @RequestBody StudentUpsert in) {
        return service.create(in);
    }

    @PutMapping("/{id}")
    @PreAuthorize("@perm.can('students','write') and @perm.staffOnly()")
    public StudentView update(@PathVariable UUID id, @Valid @RequestBody StudentUpsert in) {
        return service.update(id, in);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("@perm.can('students','write') and @perm.staffOnly()")
    public void delete(@PathVariable UUID id) {
        service.delete(id);
    }

    /** Bulk-import students into one class; returns a per-row created/failed report. */
    @PostMapping("/import")
    @PreAuthorize("@perm.can('students','write') and @perm.staffOnly()")
    public StudentImportResult importStudents(@Valid @RequestBody StudentImportRequest in) {
        return service.importForClass(in);
    }

    // ---- Parent accounts (review issue #2) ---------------------------------
    @GetMapping("/{id}/parents")
    @PreAuthorize("@perm.can('students','read') and @perm.staffOnly()")
    public List<ParentAccountView> parents(@PathVariable UUID id) {
        return parentLinks.list(id);
    }

    @PostMapping("/{id}/parents")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@perm.can('students','write') and @perm.staffOnly()")
    public ParentAccountView linkParent(@PathVariable UUID id, @Valid @RequestBody ParentLinkRequest in) {
        return parentLinks.link(id, in);
    }

    @DeleteMapping("/{id}/parents/{parentUserId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("@perm.can('students','write') and @perm.staffOnly()")
    public void unlinkParent(@PathVariable UUID id, @PathVariable UUID parentUserId) {
        parentLinks.unlink(id, parentUserId);
    }
}

package com.bbc.sms.staff;

import com.bbc.sms.media.PhotoService;
import com.bbc.sms.media.PhotoUpload;
import com.bbc.sms.media.ProfilePhoto;
import com.bbc.sms.staff.dto.StaffDtos.*;
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
@RequestMapping("/api/staff")
public class StaffController {

    private final StaffService service;
    private final PhotoService photos;

    public StaffController(StaffService service, PhotoService photos) {
        this.service = service;
        this.photos = photos;
    }

    // ---- Classes d'un enseignant --------------------------------------------

    @GetMapping("/{id}/classes")
    @PreAuthorize("@perm.canAction('HR_VIEW')")
    public List<TeacherClassView> classes(@PathVariable UUID id) {
        return service.classesOf(id);
    }

    /** Remplace la totalité des classes de l'enseignant ; une liste vide le détache de toutes. */
    @PutMapping("/{id}/classes")
    @PreAuthorize("@perm.canAction('TEACHING_CLASS_ASSIGNMENT_MANAGE')")
    public List<TeacherClassView> setClasses(@PathVariable UUID id, @RequestBody SetTeacherClasses in) {
        return service.setClasses(id, in.classIds());
    }

    // ---- Photo de profil ----------------------------------------------------

    @GetMapping("/{id}/photo")
    @PreAuthorize("@perm.canAction('HR_VIEW')")
    public ResponseEntity<byte[]> photo(@PathVariable UUID id) {
        service.get(id);   // vérifie l'appartenance à l'établissement
        ProfilePhoto p = photos.find(PhotoService.EMPLOYEE, id);
        if (p == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(p.getContentType()))
                .cacheControl(CacheControl.noCache())
                .eTag(String.valueOf(p.getUpdatedAt().toInstant().toEpochMilli()))
                .body(p.getBytes());
    }

    @PutMapping("/{id}/photo")
    @PreAuthorize("@perm.canAction('HR_MANAGE')")
    public void savePhoto(@PathVariable UUID id, @RequestBody PhotoUpload in) {
        service.get(id);
        photos.save(PhotoService.EMPLOYEE, id, in.dataUrl());
    }

    @DeleteMapping("/{id}/photo")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("@perm.canAction('HR_MANAGE')")
    public void deletePhoto(@PathVariable UUID id) {
        service.get(id);
        photos.delete(PhotoService.EMPLOYEE, id);
    }

    @GetMapping
    @PreAuthorize("@perm.canAction('HR_VIEW')")
    public List<EmployeeView> list() {
        return service.list();
    }

    @GetMapping("/{id}")
    @PreAuthorize("@perm.canAction('HR_VIEW')")
    public EmployeeView get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@perm.canAction('HR_MANAGE')")
    public EmployeeView create(@Valid @RequestBody EmployeeUpsert in) {
        return service.create(in);
    }

    @PostMapping("/import")
    @PreAuthorize("@perm.canAction('HR_MANAGE')")
    public StaffImportResult importStaff(@Valid @RequestBody StaffImportRequest in) {
        return service.importStaff(in);
    }

    @PutMapping("/{id}")
    @PreAuthorize("@perm.canAction('HR_MANAGE')")
    public EmployeeView update(@PathVariable UUID id, @Valid @RequestBody EmployeeUpsert in) {
        return service.update(id, in);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("@perm.canAction('HR_MANAGE')")
    public void delete(@PathVariable UUID id) {
        service.delete(id);
    }

    /**
     * Create the employee's login account if missing, otherwise regenerate its
     * password; the new credentials are e-mailed to the employee. Doubles as the
     * admin "reset credentials" action.
     */
    @PostMapping("/{id}/reset-credentials")
    @PreAuthorize("@perm.canAction('HR_MANAGE')")
    public AccountResult resetCredentials(@PathVariable UUID id) {
        return service.resetCredentials(id);
    }
}

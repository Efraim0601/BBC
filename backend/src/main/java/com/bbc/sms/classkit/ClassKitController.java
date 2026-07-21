package com.bbc.sms.classkit;

import com.bbc.sms.classkit.dto.ClassKitDtos.*;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Staff API for class resources (fournitures &amp; livres). {@code kind} is the path
 * segment {@code supplies} or {@code books}. Reads/writes are guarded by the active
 * parcours scope so each parcours manages its own classes.
 */
@RestController
@RequestMapping("/api/classkit/{kind}")
public class ClassKitController {

    private static final String READ = "@parcours.allows() and @perm.can('classkit','read')";
    private static final String WRITE = "@parcours.allows() and @perm.can('classkit','write')";

    private final ClassKitService service;

    public ClassKitController(ClassKitService service) { this.service = service; }

    @GetMapping("/classes/{classId}")
    @PreAuthorize(READ)
    public ClassResourceView ofClass(@PathVariable String kind, @PathVariable UUID classId) {
        return service.ofClass(classId, kind);
    }

    @PostMapping("/classes/{classId}/items")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(WRITE)
    public ItemView addItem(@PathVariable String kind, @PathVariable UUID classId,
                            @Valid @RequestBody ItemUpsert in) {
        return service.addItem(classId, kind, in);
    }

    @PutMapping("/items/{itemId}")
    @PreAuthorize(WRITE)
    public ItemView updateItem(@PathVariable String kind, @PathVariable UUID itemId,
                               @Valid @RequestBody ItemUpsert in) {
        return service.updateItem(itemId, in);
    }

    @DeleteMapping("/items/{itemId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize(WRITE)
    public void deleteItem(@PathVariable String kind, @PathVariable UUID itemId) {
        service.deleteItem(itemId);
    }

    @PostMapping("/classes/{classId}/publish")
    @PreAuthorize(WRITE)
    public ClassResourceView publish(@PathVariable String kind, @PathVariable UUID classId,
                                     @RequestBody PublishRequest req) {
        return service.publish(classId, kind, req.published());
    }
}

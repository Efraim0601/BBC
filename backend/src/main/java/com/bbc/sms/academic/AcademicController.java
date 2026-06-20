package com.bbc.sms.academic;

import com.bbc.sms.academic.dto.AcademicDtos.*;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/academic")
public class AcademicController {

    private final AcademicService service;

    public AcademicController(AcademicService service) { this.service = service; }

    @GetMapping("/students/{studentId}/grades")
    @PreAuthorize("@perm.can('academic','read')")
    public List<GradeView> listForStudent(@PathVariable UUID studentId) {
        return service.listForStudent(studentId);
    }

    @PostMapping("/grades")
    @PreAuthorize("@perm.can('academic','write')")
    public GradeView upsert(@Valid @RequestBody GradeUpsert in) {
        return service.upsert(in);
    }
}

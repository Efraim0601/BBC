package com.bbc.sms.academic.competence;

import com.bbc.sms.academic.competence.PrimaryCompetenceService.ApplyResult;
import com.bbc.sms.academic.competence.PrimaryCompetenceService.ClassStatus;
import com.bbc.sms.academic.competence.PrimaryCompetenceService.GridSaveRequest;
import com.bbc.sms.academic.competence.PrimaryCompetenceService.GridView;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Primary competence grids and their application to classes. Reads are open to staff; the
 * service checks the curriculum-catalogue permission for every change.
 */
@RestController
@RequestMapping("/api/academic/primary-competence")
public class PrimaryCompetenceController {
    private static final String READ = "@perm.staffOnly()";
    private static final String WRITE = "@parcours.allows() and @perm.staffOnly()";

    private final PrimaryCompetenceService service;

    public PrimaryCompetenceController(PrimaryCompetenceService service) { this.service = service; }

    @GetMapping("/grids")
    @PreAuthorize(READ)
    public List<GridView> grids() { return service.grids(); }

    @GetMapping("/grids/{subsystem}/{level}")
    @PreAuthorize(READ)
    public GridView grid(@PathVariable String subsystem, @PathVariable int level) { return service.grid(subsystem, level); }

    @PutMapping("/grids/{subsystem}/{level}")
    @PreAuthorize(WRITE)
    public GridView save(@PathVariable String subsystem, @PathVariable int level, @RequestBody GridSaveRequest request) {
        return service.save(subsystem, level, request);
    }

    @GetMapping("/classes")
    @PreAuthorize(READ)
    public List<ClassStatus> classes(@RequestParam UUID academicSessionId) { return service.classes(academicSessionId); }

    @PostMapping("/classes/{classId}/apply")
    @PreAuthorize(WRITE)
    public ApplyResult apply(@PathVariable UUID classId, @RequestParam UUID academicSessionId,
                             @RequestParam(required = false) Integer level) {
        return service.apply(academicSessionId, classId, level);
    }
}

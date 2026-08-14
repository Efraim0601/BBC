package com.bbc.sms.finance.fees;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

import static com.bbc.sms.finance.fees.FeeTypeDtos.*;

@RestController
@RequestMapping("/api/finance/v2/fee-types")
public class FeeTypeController {
    private final FeeTypeService service;

    public FeeTypeController(FeeTypeService service) { this.service = service; }

    @GetMapping
    @PreAuthorize("@perm.canAction('FINANCE_OVERVIEW_VIEW')")
    public List<FeeTypeView> list(@RequestParam(required = false) String query,
                                  @RequestParam(required = false) String lifecycle,
                                  @RequestParam(required = false) String category) {
        return service.list(query, lifecycle, category);
    }

    @GetMapping("/{id}")
    @PreAuthorize("@perm.canAction('FINANCE_OVERVIEW_VIEW')")
    public FeeTypeView detail(@PathVariable UUID id) { return service.detail(id); }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@perm.canAction('FEE_TYPE_MANAGE')")
    public FeeTypeView create(@Valid @RequestBody FeeTypeCreateRequest request) {
        return service.create(request);
    }

    @PutMapping("/{id}/draft")
    @PreAuthorize("@perm.canAction('FEE_TYPE_MANAGE')")
    public FeeTypeView updateDraft(@PathVariable UUID id, @Valid @RequestBody FeeTypeDraftUpdate request) {
        return service.updateDraft(id, request);
    }

    @PostMapping("/{id}/revisions")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@perm.canAction('FEE_TYPE_MANAGE')")
    public FeeTypeView createRevision(@PathVariable UUID id,
                                      @Valid @RequestBody FeeTypeRevisionCreateRequest request) {
        return service.createRevision(id, request);
    }

    @PostMapping("/{id}/activate")
    @PreAuthorize("@perm.canAction('FEE_TYPE_MANAGE')")
    public FeeTypeView activate(@PathVariable UUID id, @Valid @RequestBody FeeTypeActionRequest request) {
        return service.activate(id, request);
    }

    @PostMapping("/{id}/deactivate")
    @PreAuthorize("@perm.canAction('FEE_TYPE_MANAGE')")
    public FeeTypeView deactivate(@PathVariable UUID id, @Valid @RequestBody FeeTypeActionRequest request) {
        return service.deactivate(id, request);
    }

    @GetMapping("/{id}/usage")
    @PreAuthorize("@perm.canAction('FINANCE_OVERVIEW_VIEW')")
    public FeeTypeUsageView usage(@PathVariable UUID id) { return service.usage(id); }

    @GetMapping("/{id}/compare")
    @PreAuthorize("@perm.canAction('FINANCE_OVERVIEW_VIEW')")
    public FeeTypeComparison compare(@PathVariable UUID id,
                                     @RequestParam int leftRevision,
                                     @RequestParam int rightRevision) {
        return service.compare(id, leftRevision, rightRevision);
    }

    @GetMapping("/legacy/fee-config/preview")
    @PreAuthorize("@perm.canAction('FINANCE_OVERVIEW_VIEW')")
    public LegacyPreviewView legacyPreview() { return service.legacyPreview(); }

    @PostMapping("/legacy/fee-config/migrate")
    @PreAuthorize("@perm.canAction('FEE_TYPE_MANAGE')")
    public LegacyMigrationResult migrateLegacy(@Valid @RequestBody LegacyMappingRequest request) {
        return service.migrateLegacy(request);
    }
}

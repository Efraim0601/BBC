package com.bbc.sms.parentportal;

import com.bbc.sms.classkit.dto.ClassKitDtos.ClassResourceView;
import com.bbc.sms.finance.dto.FeeDtos.PaymentChannelView;
import com.bbc.sms.finance.dto.FeeDtos.StudentFeeStatementView;
import com.bbc.sms.library.LibraryController;
import com.bbc.sms.library.LibraryService;
import com.bbc.sms.library.dto.LibraryDtos.ResourceView;
import com.bbc.sms.parentportal.dto.ParentDtos.*;
import com.bbc.sms.platform.security.AppUserPrincipal;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/parent")
public class ParentController {

    private final ParentService service;
    private final LibraryService library;

    public ParentController(ParentService service, LibraryService library) {
        this.service = service;
        this.library = library;
    }

    @GetMapping("/children")
    @PreAuthorize("@perm.isParent()")
    public List<ChildView> children(@AuthenticationPrincipal AppUserPrincipal principal) {
        return service.children(principal);
    }

    @GetMapping("/children/{studentId}/grades")
    @PreAuthorize("@perm.isParent()")
    public List<GradeView> grades(@AuthenticationPrincipal AppUserPrincipal principal,
                                  @PathVariable UUID studentId) {
        return service.grades(principal, studentId);
    }

    @GetMapping("/children/{studentId}/resources/{kind}")
    @PreAuthorize("@perm.isParent()")
    public ClassResourceView resources(@AuthenticationPrincipal AppUserPrincipal principal,
                                       @PathVariable UUID studentId, @PathVariable String kind) {
        return service.resources(principal, studentId, kind);
    }

    @GetMapping("/children/{studentId}/fees")
    @PreAuthorize("@perm.isParent()")
    public StudentFeeStatementView fees(@AuthenticationPrincipal AppUserPrincipal principal,
                                        @PathVariable UUID studentId) {
        return service.feeStatement(principal, studentId);
    }

    /** Comment régler : canaux acceptés, coordonnées et instructions. */
    @GetMapping("/payment-channels")
    @PreAuthorize("@perm.isParent()")
    public List<PaymentChannelView> paymentChannels(@AuthenticationPrincipal AppUserPrincipal principal) {
        return service.paymentChannels(principal);
    }

    @PostMapping("/suggestions")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@perm.isParent()")
    public SuggestionView createSuggestion(@AuthenticationPrincipal AppUserPrincipal principal,
                                           @Valid @RequestBody SuggestionRequest req) {
        return service.createSuggestion(principal, req);
    }

    @GetMapping("/suggestions")
    @PreAuthorize("@perm.isParent()")
    public List<SuggestionView> mySuggestions(@AuthenticationPrincipal AppUserPrincipal principal) {
        return service.mySuggestions(principal);
    }

    // ---- Ressources mises a disposition par la direction ---------------------

    /**
     * Les documents publies qui s'adressent aux familles, bornes aux cycles ou
     * ce parent a un enfant. Le service applique la meme regle que pour le
     * personnel ; seul le destinataire change.
     */
    @GetMapping("/resources")
    @PreAuthorize("@perm.isParent()")
    public List<ResourceView> resources(@AuthenticationPrincipal AppUserPrincipal principal) {
        return library.listForParent(principal.userId());
    }

    /** Le fichier, apres le meme controle que la liste — l'identifiant ne suffit pas. */
    @GetMapping("/resources/{id}/file")
    @PreAuthorize("@perm.isParent()")
    public ResponseEntity<InputStreamResource> resourceFile(
            @AuthenticationPrincipal AppUserPrincipal principal, @PathVariable UUID id) {
        return LibraryController.serve(library.downloadForParent(principal.userId(), id));
    }
}

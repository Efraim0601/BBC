package com.bbc.sms.parentportal;

import com.bbc.sms.classkit.dto.ClassKitDtos.ClassResourceView;
import com.bbc.sms.finance.dto.FeeDtos.PaymentChannelView;
import com.bbc.sms.finance.dto.FeeDtos.StudentFeeStatementView;
import com.bbc.sms.finance.documents.FinanceDocumentDtos.ParentInvoiceView;
import com.bbc.sms.finance.documents.FinanceDocumentDtos.ParentReceiptView;
import com.bbc.sms.documents.OfficialDocumentService;
import com.bbc.sms.library.LibraryController;
import com.bbc.sms.library.LibraryService;
import com.bbc.sms.library.dto.LibraryDtos.ResourceView;
import com.bbc.sms.parentportal.dto.ParentDtos.*;
import com.bbc.sms.platform.common.ApiException;
import com.bbc.sms.platform.security.AppUserPrincipal;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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
    private final OfficialDocumentService officialDocuments;
    private final LibraryService library;

    public ParentController(ParentService service, OfficialDocumentService officialDocuments,
                            LibraryService library) {
        this.service = service;
        this.officialDocuments = officialDocuments;
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
        throw ApiException.forbidden("Les notes brutes ne sont pas visibles dans le portail parent. Consultez un bulletin publié.");
    }

    @GetMapping("/children/{studentId}/programme-classes")
    @PreAuthorize("@perm.isParent()")
    public List<ProgrammeClassView> programmeClasses(@AuthenticationPrincipal AppUserPrincipal principal,
                                                     @PathVariable UUID studentId) {
        return service.programmeClasses(principal, studentId);
    }

    @GetMapping("/children/{studentId}/bulletins")
    @PreAuthorize("@perm.isParent()")
    public com.bbc.sms.academic.dto.AcademicDtos.BulletinSnapshotView publishedBulletin(
            @AuthenticationPrincipal AppUserPrincipal principal,
            @PathVariable UUID studentId, @RequestParam UUID reportingPeriodId,
            @RequestParam(required = false) UUID classId) {
        return service.publishedBulletin(principal, studentId, reportingPeriodId, classId);
    }

    @GetMapping("/children/{studentId}/bulletins/latest")
    @PreAuthorize("@perm.isParent()")
    public com.bbc.sms.academic.dto.AcademicDtos.BulletinSnapshotView latestPublishedBulletin(
            @AuthenticationPrincipal AppUserPrincipal principal, @PathVariable UUID studentId,
            @RequestParam(required = false) UUID classId) {
        return service.latestPublishedBulletin(principal, studentId, classId);
    }

    @GetMapping("/children/{studentId}/journey")
    @PreAuthorize("@perm.isParent()")
    public List<ParentJourneyEventView> journey(@AuthenticationPrincipal AppUserPrincipal principal,
                                                @PathVariable UUID studentId) {
        return service.journey(principal, studentId);
    }

    @GetMapping("/children/{studentId}/attendance")
    @PreAuthorize("@perm.isParent()")
    public ParentAttendanceView attendance(@AuthenticationPrincipal AppUserPrincipal principal,
                                           @PathVariable UUID studentId) {
        return service.attendance(principal, studentId);
    }

    @GetMapping("/children/{studentId}/discipline")
    @PreAuthorize("@perm.isParent()")
    public List<ParentDisciplineView> discipline(@AuthenticationPrincipal AppUserPrincipal principal,
                                                  @PathVariable UUID studentId) {
        return service.discipline(principal, studentId);
    }

    @GetMapping("/children/{studentId}/health")
    @PreAuthorize("@perm.isParent()")
    public ParentHealthView health(@AuthenticationPrincipal AppUserPrincipal principal,
                                   @PathVariable UUID studentId) {
        return service.health(principal, studentId);
    }

    @GetMapping("/children/{studentId}/events")
    @PreAuthorize("@perm.isParent()")
    public List<ParentEventView> events(@AuthenticationPrincipal AppUserPrincipal principal,
                                        @PathVariable UUID studentId) {
        return service.events(principal, studentId);
    }

    @GetMapping("/children/{studentId}/messages")
    @PreAuthorize("@perm.isParent()")
    public List<ParentNoticeView> messages(@AuthenticationPrincipal AppUserPrincipal principal,
                                           @PathVariable UUID studentId) {
        return service.messages(principal, studentId);
    }

    @PostMapping("/children/{studentId}/messages/{messageId}/ack")
    @PreAuthorize("@perm.isParent()")
    public ParentNoticeView acknowledgeMessage(@AuthenticationPrincipal AppUserPrincipal principal,
                                               @PathVariable UUID studentId,
                                               @PathVariable UUID messageId,
                                               @Valid @RequestBody ParentAckRequest request) {
        return service.acknowledgeMessage(principal, studentId, messageId, request);
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

    @GetMapping("/children/{studentId}/invoices")
    @PreAuthorize("@perm.isParent()")
    public List<ParentInvoiceView> invoices(@AuthenticationPrincipal AppUserPrincipal principal,
                                            @PathVariable UUID studentId) {
        return service.financeInvoices(principal, studentId);
    }

    @GetMapping("/children/{studentId}/receipts")
    @PreAuthorize("@perm.isParent()")
    public List<ParentReceiptView> receipts(@AuthenticationPrincipal AppUserPrincipal principal,
                                            @PathVariable UUID studentId) {
        return service.financeReceipts(principal, studentId);
    }

    @GetMapping("/children/{studentId}/finance-documents/{type}/{documentId}/download")
    @PreAuthorize("@perm.isParent()")
    public ResponseEntity<byte[]> financeDocument(@AuthenticationPrincipal AppUserPrincipal principal,
                                                  @PathVariable UUID studentId, @PathVariable String type,
                                                  @PathVariable UUID documentId) {
        UUID generatedId = service.financeDocumentId(principal, type, documentId, studentId);
        var document = officialDocuments.byId(generatedId);
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF).cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline()
                        .filename((document.documentNumber() == null ? "finance-document" : document.documentNumber()) + ".pdf")
                        .build().toString())
                .body(officialDocuments.content(generatedId));
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

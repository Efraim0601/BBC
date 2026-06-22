package com.bbc.sms.documents;

import com.bbc.sms.documents.dto.DocumentDtos.*;
import com.bbc.sms.platform.common.ApiException;
import com.bbc.sms.platform.security.AppUserPrincipal;
import com.bbc.sms.platform.tenant.TenantContext;
import com.bbc.sms.student.Student;
import com.bbc.sms.student.StudentRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class DocumentService {

    private final StudentDocumentRepository documents;
    private final OrientationDecisionRepository orientations;
    private final StudentRepository students;

    public DocumentService(StudentDocumentRepository documents,
                           OrientationDecisionRepository orientations,
                           StudentRepository students) {
        this.documents = documents;
        this.orientations = orientations;
        this.students = students;
    }

    @Transactional(readOnly = true)
    public StudentDossier forStudent(UUID studentId) {
        UUID schoolId = TenantContext.get();
        Student student = students.findByIdAndSchoolId(studentId, schoolId)
                .orElseThrow(() -> ApiException.notFound("Élève"));

        List<DocumentView> docs = documents
                .findBySchoolIdAndStudentIdOrderByCreatedAtDesc(schoolId, studentId)
                .stream().map(this::toView).toList();

        List<OrientationView> oris = orientations
                .findBySchoolIdAndStudentIdOrderByAcademicYearDesc(schoolId, studentId)
                .stream().map(this::toView).toList();

        String name = student.getLastName().toUpperCase() + " " + student.getFirstName();
        return new StudentDossier(student.getId(), name, student.getMatricule(),
                student.getClassName(), docs, oris);
    }

    @Transactional
    public DocumentView addDocument(UUID studentId, DocumentUpsert in) {
        UUID schoolId = TenantContext.get();
        students.findByIdAndSchoolId(studentId, schoolId)
                .orElseThrow(() -> ApiException.notFound("Élève"));

        StudentDocument d = new StudentDocument();
        d.setSchoolId(schoolId);
        d.setStudentId(studentId);
        d.setKind(in.kind().trim());
        d.setTitle(in.title().trim());
        d.setNote(in.note());
        d.setFileRef(in.fileRef() == null || in.fileRef().isBlank() ? null : in.fileRef().trim());
        d.setUploadedBy(currentUserId());
        return toView(documents.save(d));
    }

    @Transactional
    public void deleteDocument(UUID id) {
        StudentDocument d = documents.findByIdAndSchoolId(id, TenantContext.get())
                .orElseThrow(() -> ApiException.notFound("Document"));
        documents.delete(d);
    }

    @Transactional
    public OrientationView addOrientation(UUID studentId, OrientationUpsert in) {
        UUID schoolId = TenantContext.get();
        students.findByIdAndSchoolId(studentId, schoolId)
                .orElseThrow(() -> ApiException.notFound("Élève"));

        OrientationDecision o = new OrientationDecision();
        o.setSchoolId(schoolId);
        o.setStudentId(studentId);
        o.setAcademicYear(in.academicYear().trim());
        o.setStage(in.stage().trim());
        o.setRecommendation(in.recommendation());
        o.setDecision(in.decision());
        o.setCouncilDate(in.councilDate());
        o.setCreatedBy(currentUserId());
        return toView(orientations.save(o));
    }

    @Transactional
    public void deleteOrientation(UUID id) {
        OrientationDecision o = orientations.findByIdAndSchoolId(id, TenantContext.get())
                .orElseThrow(() -> ApiException.notFound("Décision d’orientation"));
        orientations.delete(o);
    }

    private UUID currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getPrincipal() instanceof AppUserPrincipal p ? p.userId() : null;
    }

    private DocumentView toView(StudentDocument d) {
        return new DocumentView(d.getId(), d.getStudentId(), d.getKind(), d.getTitle(),
                d.getNote(), d.getFileRef(), d.getCreatedAt());
    }

    private OrientationView toView(OrientationDecision o) {
        return new OrientationView(o.getId(), o.getStudentId(), o.getAcademicYear(), o.getStage(),
                o.getRecommendation(), o.getDecision(), o.getCouncilDate(), o.getCreatedAt());
    }
}

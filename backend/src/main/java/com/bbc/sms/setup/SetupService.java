package com.bbc.sms.setup;

import com.bbc.sms.academic.Subject;
import com.bbc.sms.academic.SubjectRepository;
import com.bbc.sms.platform.common.ApiException;
import com.bbc.sms.platform.tenant.TenantContext;
import com.bbc.sms.setup.dto.SetupDtos.*;
import com.bbc.sms.student.StudentRepository;
import com.bbc.sms.timetable.SchoolClass;
import com.bbc.sms.timetable.SchoolClassRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Academic Setup — admins build the relational backbone (sections, classes, subjects)
 * here, BEFORE enrolling students. The student form then binds a real {@code class_id}
 * instead of free text, which is the whole point of review issues #1 and #3.
 */
@Service
public class SetupService {

    private final SectionRepository sections;
    private final SchoolClassRepository classes;
    private final SubjectRepository subjects;
    private final StudentRepository students;

    public SetupService(SectionRepository sections, SchoolClassRepository classes,
                        SubjectRepository subjects, StudentRepository students) {
        this.sections = sections;
        this.classes = classes;
        this.subjects = subjects;
        this.students = students;
    }

    // ---- Sections -----------------------------------------------------------

    @Transactional(readOnly = true)
    public List<SectionView> listSections() {
        UUID schoolId = TenantContext.get();
        return sections.findBySchoolIdOrderByLabel(schoolId).stream().map(this::toView).toList();
    }

    @Transactional
    public SectionView createSection(SectionUpsert in) {
        UUID schoolId = TenantContext.get();
        Section s = new Section();
        s.setId(uniqueSectionId(schoolId, in.subsystem(), in.level()));
        s.setSchoolId(schoolId);
        s.setLabel(in.label().trim());
        s.setSubsystem(in.subsystem());
        s.setLevel(in.level());
        return toView(sections.save(s));
    }

    @Transactional
    public SectionView updateSection(String id, SectionUpsert in) {
        UUID schoolId = TenantContext.get();
        Section s = sections.findByIdAndSchoolId(id, schoolId)
                .orElseThrow(() -> ApiException.notFound("Section"));
        s.setLabel(in.label().trim());
        s.setSubsystem(in.subsystem());
        s.setLevel(in.level());
        return toView(sections.save(s));
    }

    @Transactional
    public void deleteSection(String id) {
        UUID schoolId = TenantContext.get();
        Section s = sections.findByIdAndSchoolId(id, schoolId)
                .orElseThrow(() -> ApiException.notFound("Section"));
        if (classes.existsBySchoolIdAndSectionId(schoolId, id)) {
            throw ApiException.conflict("Cette section contient des classes — supprimez-les d'abord");
        }
        sections.delete(s);
    }

    // ---- Classes ------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<ClassView> listClasses() {
        UUID schoolId = TenantContext.get();
        Map<String, Section> byId = sections.findBySchoolIdOrderByLabel(schoolId).stream()
                .collect(java.util.stream.Collectors.toMap(Section::getId, x -> x));
        return classes.findBySchoolIdOrderByName(schoolId).stream()
                .map(c -> toView(c, byId.get(c.getSectionId())))
                .toList();
    }

    @Transactional
    public ClassView createClass(ClassUpsert in) {
        UUID schoolId = TenantContext.get();
        Section section = sections.findByIdAndSchoolId(in.sectionId(), schoolId)
                .orElseThrow(() -> ApiException.notFound("Section"));
        String name = in.name().trim();
        if (classes.existsBySchoolIdAndName(schoolId, name)) {
            throw ApiException.conflict("Une classe « " + name + " » existe déjà");
        }
        SchoolClass c = new SchoolClass();
        c.setSchoolId(schoolId);
        c.setSectionId(section.getId());
        c.setName(name);
        c.setSubsystem(section.getSubsystem());   // class inherits its section's subsystem/level
        c.setLevel(section.getLevel());
        return toView(classes.save(c), section);
    }

    @Transactional
    public ClassView updateClass(UUID id, ClassUpsert in) {
        UUID schoolId = TenantContext.get();
        SchoolClass c = classes.findByIdAndSchoolId(id, schoolId)
                .orElseThrow(() -> ApiException.notFound("Classe"));
        Section section = sections.findByIdAndSchoolId(in.sectionId(), schoolId)
                .orElseThrow(() -> ApiException.notFound("Section"));
        String name = in.name().trim();
        if (!name.equalsIgnoreCase(c.getName()) && classes.existsBySchoolIdAndName(schoolId, name)) {
            throw ApiException.conflict("Une classe « " + name + " » existe déjà");
        }
        c.setName(name);
        c.setSectionId(section.getId());
        c.setSubsystem(section.getSubsystem());
        c.setLevel(section.getLevel());
        return toView(classes.save(c), section);
    }

    @Transactional
    public void deleteClass(UUID id) {
        UUID schoolId = TenantContext.get();
        SchoolClass c = classes.findByIdAndSchoolId(id, schoolId)
                .orElseThrow(() -> ApiException.notFound("Classe"));
        if (students.countBySchoolIdAndClassIdAndActiveTrue(schoolId, id) > 0) {
            throw ApiException.conflict("Des élèves sont inscrits dans cette classe — réaffectez-les d'abord");
        }
        classes.delete(c);
    }

    // ---- Subjects -----------------------------------------------------------

    @Transactional(readOnly = true)
    public List<SubjectView> listSubjects() {
        UUID schoolId = TenantContext.get();
        return subjects.findBySchoolIdOrderByCode(schoolId).stream().map(this::toView).toList();
    }

    @Transactional
    public SubjectView createSubject(SubjectUpsert in) {
        UUID schoolId = TenantContext.get();
        String code = in.code().trim().toUpperCase();
        if (subjects.existsBySchoolIdAndCode(schoolId, code)) {
            throw ApiException.conflict("Une matière « " + code + " » existe déjà");
        }
        Subject s = new Subject();
        s.setSchoolId(schoolId);
        s.setCode(code);
        s.setLabel(in.label());
        s.setCoef(Math.max(1, in.coef()));
        return toView(subjects.save(s));
    }

    @Transactional
    public SubjectView updateSubject(UUID id, SubjectUpsert in) {
        UUID schoolId = TenantContext.get();
        Subject s = subjects.findByIdAndSchoolId(id, schoolId)
                .orElseThrow(() -> ApiException.notFound("Matière"));
        s.setLabel(in.label());
        s.setCoef(Math.max(1, in.coef()));
        return toView(subjects.save(s));
    }

    @Transactional
    public void deleteSubject(UUID id) {
        UUID schoolId = TenantContext.get();
        Subject s = subjects.findByIdAndSchoolId(id, schoolId)
                .orElseThrow(() -> ApiException.notFound("Matière"));
        subjects.delete(s);
    }

    // ---- mapping ------------------------------------------------------------

    private SectionView toView(Section s) {
        long classCount = classes.findBySchoolIdAndSectionId(s.getSchoolId(), s.getId()).size();
        return new SectionView(s.getId(), s.getLabel(), s.getSubsystem(), s.getLevel(), classCount);
    }

    private ClassView toView(SchoolClass c, Section section) {
        long count = students.countBySchoolIdAndClassIdAndActiveTrue(c.getSchoolId(), c.getId());
        return new ClassView(c.getId(), c.getName(), c.getSectionId(),
                section == null ? null : section.getLabel(),
                c.getSubsystem(), c.getLevel(), count);
    }

    private SubjectView toView(Subject s) {
        return new SubjectView(s.getId(), s.getCode(), s.getLabel(), s.getCoef());
    }

    /** Deterministic short id from subsystem+level (pri-fr, sec-en…), suffixed if taken. */
    private String uniqueSectionId(UUID schoolId, String subsystem, String level) {
        String base = (level.startsWith("pri") ? "pri" : "sec") + "-" + subsystem.toLowerCase();
        base = Normalizer.normalize(base, Normalizer.Form.NFD).replaceAll("[^a-z0-9-]", "");
        String id = base;
        int n = 2;
        while (sections.existsByIdAndSchoolId(id, schoolId)) {
            id = base + "-" + n++;
        }
        return id;
    }
}

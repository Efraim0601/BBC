package com.bbc.sms.classkit;

import com.bbc.sms.classkit.dto.ClassKitDtos.*;
import com.bbc.sms.platform.common.ApiException;
import com.bbc.sms.platform.security.AuthorizationPolicyService;
import com.bbc.sms.platform.security.PolicyResourceContext;
import com.bbc.sms.platform.security.TeacherScopeService;
import com.bbc.sms.platform.tenant.TenantContext;
import com.bbc.sms.timetable.SchoolClass;
import com.bbc.sms.timetable.SchoolClassRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Class resources: supply lists (fournitures) and payable book lists (livres).
 * Staff edit and PUBLISH a list per class; parents read only published lists.
 * Book prices are informational — never posted to the finance ledger.
 */
@Service
public class ClassKitService {

    private static final Set<String> KINDS = Set.of("supplies", "books");

    private final ClassResourceItemRepository items;
    private final SchoolClassRepository classes;
    private final AccessScopeService accessScope;
    private final JdbcTemplate jdbc;
    private final AuthorizationPolicyService policy;

    public ClassKitService(ClassResourceItemRepository items,
                           SchoolClassRepository classes,
                           TeacherScopeService teacherScope,
                           JdbcTemplate jdbc,
                           AuthorizationPolicyService policy) {
        this.items = items;
        this.classes = classes;
        this.accessScope = accessScope;
        this.jdbc = jdbc;
        this.policy = policy;
    }

    private static String requireKind(String kind) {
        if (!KINDS.contains(kind)) throw ApiException.badRequest("Type de ressource inconnu");
        return kind;
    }

    private SchoolClass requireClass(UUID schoolId, UUID classId) {
        SchoolClass cls = classes.findByIdAndSchoolId(classId, schoolId)
                .orElseThrow(() -> ApiException.notFound("Classe"));
        // Un enseignant n'ouvre le kit que d'une classe qui lui est assignée.
        accessScope.assertClass(cls.getId());
        return cls;
    }

    /** Staff view: the class list for a kind plus its publish state (always returned). */
    @Transactional(readOnly = true)
    public ClassResourceView ofClass(UUID classId, String kind) {
        UUID schoolId = TenantContext.get();
        requireKind(kind);
        SchoolClass cls = requireClass(schoolId, classId);
        requirePolicy("CLASSKIT_VIEW", cls.getId());
        return view(schoolId, cls, kind);
    }

    /** Parent view: returns the published list, or an empty unpublished view otherwise. */
    @Transactional(readOnly = true)
    public ClassResourceView publishedForClass(UUID classId, String kind) {
        UUID schoolId = TenantContext.get();
        requireKind(kind);
        SchoolClass cls = requireClass(schoolId, classId);
        if (!isPublished(schoolId, classId, kind)) {
            return new ClassResourceView(classId, cls.getName(), kind, false, null, List.of());
        }
        return view(schoolId, cls, kind);
    }

    @Transactional
    public ItemView addItem(UUID classId, String kind, ItemUpsert in) {
        UUID schoolId = TenantContext.get();
        requireKind(kind);
        requireClass(schoolId, classId);
        requirePolicy("CLASSKIT_MANAGE", classId);
        ClassResourceItem it = new ClassResourceItem();
        it.setSchoolId(schoolId);
        it.setClassId(classId);
        it.setKind(kind);
        apply(it, in);
        Integer maxPos = jdbc.queryForObject(
                "SELECT COALESCE(MAX(position), -1) + 1 FROM class_resource_item "
              + "WHERE school_id = ? AND class_id = ? AND kind = ?",
                Integer.class, schoolId, classId, kind);
        it.setPosition(maxPos == null ? 0 : maxPos);
        return toView(items.save(it));
    }

    @Transactional
    public ItemView updateItem(UUID itemId, ItemUpsert in) {
        UUID schoolId = TenantContext.get();
        ClassResourceItem it = items.findByIdAndSchoolId(itemId, schoolId)
                .orElseThrow(() -> ApiException.notFound("Élément"));
        teacherScope.assertClass(it.getClassId());
        requirePolicy("CLASSKIT_MANAGE", it.getClassId());
        apply(it, in);
        return toView(items.save(it));
    }

    @Transactional
    public void deleteItem(UUID itemId) {
        UUID schoolId = TenantContext.get();
        ClassResourceItem it = items.findByIdAndSchoolId(itemId, schoolId)
                .orElseThrow(() -> ApiException.notFound("Élément"));
        teacherScope.assertClass(it.getClassId());
        requirePolicy("CLASSKIT_MANAGE", it.getClassId());
        items.delete(it);
    }

    @Transactional
    public ClassResourceView publish(UUID classId, String kind, boolean published) {
        UUID schoolId = TenantContext.get();
        requireKind(kind);
        SchoolClass cls = requireClass(schoolId, classId);
        requirePolicy("CLASSKIT_MANAGE", cls.getId());
        jdbc.update(
                "INSERT INTO class_resource_publication (school_id, class_id, kind, published, published_at) "
              + "VALUES (?,?,?,?,?) "
              + "ON CONFLICT (school_id, class_id, kind) DO UPDATE SET published = EXCLUDED.published, "
              + "published_at = EXCLUDED.published_at",
                schoolId, classId, kind, published, published ? OffsetDateTime.now() : null);
        return view(schoolId, cls, kind);
    }

    // ---- helpers ------------------------------------------------------------

    private void apply(ClassResourceItem it, ItemUpsert in) {
        it.setLabel(in.label().trim());
        // Keep the shape clean per kind: supplies carry quantity, books carry price.
        if ("books".equals(it.getKind())) {
            it.setPrice(in.price());
            it.setQuantity(null);
            it.setSubjectCode(blankToNull(in.subjectCode()));
            it.setAuthor(blankToNull(in.author()));
            // Default a book to mandatory unless explicitly marked optional.
            it.setMandatory(in.mandatory() == null ? Boolean.TRUE : in.mandatory());
        } else {
            it.setQuantity(in.quantity());
            it.setPrice(null);
            it.setSubjectCode(null);
            it.setAuthor(null);
            it.setMandatory(null);
        }
        it.setNote(in.note() == null || in.note().isBlank() ? null : in.note().trim());
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private boolean isPublished(UUID schoolId, UUID classId, String kind) {
        Boolean p = jdbc.query(
                "SELECT published FROM class_resource_publication "
              + "WHERE school_id = ? AND class_id = ? AND kind = ?",
                rs -> rs.next() ? rs.getBoolean("published") : Boolean.FALSE,
                schoolId, classId, kind);
        return Boolean.TRUE.equals(p);
    }

    private ClassResourceView view(UUID schoolId, SchoolClass cls, String kind) {
        boolean published = isPublished(schoolId, cls.getId(), kind);
        OffsetDateTime publishedAt = jdbc.query(
                "SELECT published_at FROM class_resource_publication "
              + "WHERE school_id = ? AND class_id = ? AND kind = ?",
                rs -> rs.next() && rs.getTimestamp("published_at") != null
                        ? rs.getObject("published_at", OffsetDateTime.class) : null,
                schoolId, cls.getId(), kind);
        List<ItemView> views = items
                .findBySchoolIdAndClassIdAndKindOrderByPositionAscLabelAsc(schoolId, cls.getId(), kind)
                .stream().map(this::toView).toList();
        return new ClassResourceView(cls.getId(), cls.getName(), kind, published, publishedAt, views);
    }

    private ItemView toView(ClassResourceItem it) {
        return new ItemView(it.getId(), it.getLabel(), it.getQuantity(), it.getPrice(), it.getNote(),
                it.getSubjectCode(), it.getAuthor(), it.getMandatory());
    }

    private void requirePolicy(String action, UUID classId) {
        policy.require(action, new PolicyResourceContext(TenantContext.get(), null, java.time.LocalDate.now(),
                null, classId, null, null, null, null, null, null, null));
    }
}

package com.bbc.sms.settings;

import com.bbc.sms.identity.School;
import com.bbc.sms.identity.SchoolRepository;
import com.bbc.sms.platform.common.ApiException;
import com.bbc.sms.platform.tenant.TenantContext;
import com.bbc.sms.settings.dto.SettingsDtos.SchoolProfileUpdate;
import com.bbc.sms.settings.dto.SettingsDtos.SchoolProfileView;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * The school's own identity — name, city, contacts, currency, supervising authority.
 *
 * Bulletins, receipts, the parent portal and Settings → Général all used to hardcode
 * these strings in the frontend; they now read them from here.
 */
@Service
public class SchoolProfileService {

    private final SchoolRepository schools;
    private final JdbcTemplate jdbc;

    public SchoolProfileService(SchoolRepository schools, JdbcTemplate jdbc) {
        this.schools = schools;
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public SchoolProfileView get() {
        return toView(current());
    }

    @Transactional
    public SchoolProfileView update(SchoolProfileUpdate in) {
        School s = current();
        s.setName(in.name().trim());
        s.setMotto(trimToNull(in.motto()));
        s.setCity(trimToNull(in.city()));
        s.setCountry(trimToNull(in.country()));
        s.setAddress(trimToNull(in.address()));
        s.setPhone(trimToNull(in.phone()));
        s.setEmail(trimToNull(in.email()));
        s.setWebsite(trimToNull(in.website()));
        // Currency labels every amount in the app; an empty one would render "12 000 ".
        s.setCurrency(in.currency() == null || in.currency().isBlank() ? "FCFA" : in.currency().trim());
        s.setAuthority(trimToNull(in.authority()));
        return toView(schools.save(s));
    }

    private School current() {
        UUID schoolId = TenantContext.get();
        return schools.findById(schoolId)
                .orElseThrow(() -> ApiException.badRequest("Établissement introuvable"));
    }

    /** Label of the year flagged current, or null when the school has none yet. */
    private String academicYear(UUID schoolId) {
        List<String> rows = jdbc.query(
                "SELECT label FROM academic_year WHERE school_id = ? AND is_current = true LIMIT 1",
                (rs, i) -> rs.getString("label"),
                schoolId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private SchoolProfileView toView(School s) {
        return new SchoolProfileView(
                s.getCode(), s.getName(), s.getMotto(), s.getCity(), s.getCountry(), s.getAddress(),
                s.getPhone(), s.getEmail(), s.getWebsite(),
                s.getCurrency() == null ? "FCFA" : s.getCurrency(),
                s.getAuthority(), academicYear(s.getId()));
    }

    private static String trimToNull(String v) {
        if (v == null) return null;
        String t = v.trim();
        return t.isEmpty() ? null : t;
    }
}

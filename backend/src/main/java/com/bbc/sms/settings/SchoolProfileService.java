package com.bbc.sms.settings;

import com.bbc.sms.identity.School;
import com.bbc.sms.identity.SchoolRepository;
import com.bbc.sms.platform.common.ApiException;
import com.bbc.sms.platform.tenant.TenantContext;
import com.bbc.sms.settings.dto.SettingsDtos.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.UUID;

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
        s.setCurrency(in.currency() == null || in.currency().isBlank() ? "FCFA" : in.currency().trim());
        s.setAuthority(trimToNull(in.authority()));
        if (in.schoolStartTime() != null && !in.schoolStartTime().isBlank()) {
            s.setSchoolStartTime(normalizeTime(in.schoolStartTime()));
        }
        if (in.schoolEndTime() != null && !in.schoolEndTime().isBlank()) {
            s.setSchoolEndTime(normalizeTime(in.schoolEndTime()));
        }
        return toView(schools.save(s));
    }

    /** Opening time used by attendance late detection. */
    @Transactional(readOnly = true)
    public LocalTime schoolStart() {
        String raw = current().getSchoolStartTime();
        try {
            return LocalTime.parse(raw == null || raw.isBlank() ? "07:30" : raw.trim());
        } catch (DateTimeParseException e) {
            return LocalTime.of(7, 30);
        }
    }

    @Transactional(readOnly = true)
    public boolean isHoliday(LocalDate date) {
        Integer n = jdbc.queryForObject(
                "SELECT count(*) FROM school_holiday WHERE school_id = ? AND holiday_date = ?",
                Integer.class, TenantContext.get(), date);
        return n != null && n > 0;
    }

    @Transactional(readOnly = true)
    public List<HolidayView> listHolidays() {
        return jdbc.query(
                "SELECT id, holiday_date, label FROM school_holiday WHERE school_id = ? ORDER BY holiday_date",
                (rs, i) -> new HolidayView(
                        UUID.fromString(rs.getString("id")),
                        rs.getDate("holiday_date").toLocalDate(),
                        rs.getString("label")),
                TenantContext.get());
    }

    @Transactional
    public HolidayView addHoliday(HolidayUpsert in) {
        UUID schoolId = TenantContext.get();
        UUID id = UUID.randomUUID();
        try {
            jdbc.update(
                    "INSERT INTO school_holiday (id, school_id, holiday_date, label) VALUES (?,?,?,?)",
                    id, schoolId, in.date(), in.label().trim());
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            throw ApiException.conflict("Un jour férié existe déjà à cette date");
        }
        return new HolidayView(id, in.date(), in.label().trim());
    }

    @Transactional
    public void deleteHoliday(UUID id) {
        int n = jdbc.update("DELETE FROM school_holiday WHERE id = ? AND school_id = ?",
                id, TenantContext.get());
        if (n == 0) throw ApiException.notFound("Jour férié");
    }

    private School current() {
        UUID schoolId = TenantContext.get();
        return schools.findById(schoolId)
                .orElseThrow(() -> ApiException.badRequest("Établissement introuvable"));
    }

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
                s.getAuthority(), academicYear(s.getId()),
                s.getSchoolStartTime() == null ? "07:30" : s.getSchoolStartTime(),
                s.getSchoolEndTime() == null ? "17:00" : s.getSchoolEndTime());
    }

    private static String normalizeTime(String raw) {
        try {
            return LocalTime.parse(raw.trim()).toString().substring(0, 5);
        } catch (DateTimeParseException e) {
            throw ApiException.badRequest("Heure invalide (attendu HH:mm)");
        }
    }

    private static String trimToNull(String v) {
        if (v == null) return null;
        String t = v.trim();
        return t.isEmpty() ? null : t;
    }
}

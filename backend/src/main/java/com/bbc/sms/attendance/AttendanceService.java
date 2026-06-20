package com.bbc.sms.attendance;

import com.bbc.sms.attendance.dto.AttendanceDtos.*;
import com.bbc.sms.platform.common.ApiException;
import com.bbc.sms.platform.realtime.RealtimeService;
import com.bbc.sms.platform.tenant.TenantContext;
import com.bbc.sms.student.Student;
import com.bbc.sms.student.StudentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;

@Service
public class AttendanceService {

    private static final LocalTime SCHOOL_START = LocalTime.of(7, 30);

    private final AttendanceRepository repo;
    private final DeviceRepository devices;
    private final StudentRepository students;
    private final RealtimeService realtime;

    public AttendanceService(AttendanceRepository repo, DeviceRepository devices,
                             StudentRepository students, RealtimeService realtime) {
        this.repo = repo;
        this.devices = devices;
        this.students = students;
        this.realtime = realtime;
    }

    @Transactional(readOnly = true)
    public DailyBoard board(LocalDate date) {
        UUID schoolId = TenantContext.get();
        Map<UUID, Student> byId = new HashMap<>();
        students.findBySchoolIdAndActiveTrueOrderByLastNameAsc(schoolId)
                .forEach(s -> byId.put(s.getId(), s));
        List<AttendanceView> views = repo.findBySchoolIdAndDate(schoolId, date).stream()
                .map(r -> toView(r, byId.get(r.getStudentId())))
                .sorted(Comparator.comparing(AttendanceView::studentName))
                .toList();
        int present = (int) views.stream().filter(v -> "present".equals(v.status())).count();
        int late = (int) views.stream().filter(v -> "late".equals(v.status())).count();
        int absent = (int) views.stream().filter(v -> "absent".equals(v.status())).count();
        return new DailyBoard(date, present, late, absent, views);
    }

    @Transactional
    public AttendanceView mark(MarkRequest req) {
        UUID schoolId = TenantContext.get();
        Student student = students.findByIdAndSchoolId(req.studentId(), schoolId)
                .orElseThrow(() -> ApiException.notFound("Élève"));
        AttendanceRecord rec = repo.findBySchoolIdAndStudentIdAndDate(schoolId, req.studentId(), req.date())
                .orElseGet(AttendanceRecord::new);
        rec.setSchoolId(schoolId);
        rec.setStudentId(req.studentId());
        rec.setDate(req.date());
        rec.setStatus(req.status());
        rec.setCheckInTime(req.checkInTime());
        rec.setLateMinutes(req.lateMinutes());
        rec.setSource("manual");
        AttendanceView view = toView(repo.save(rec), student);
        realtime.broadcast(schoolId, "attendance", view);
        return view;
    }

    /** Called by the device endpoint (already authenticated by API key). */
    @Transactional
    public AttendanceView deviceCheckin(UUID deviceId, String apiKey, DeviceCheckin in) {
        Device device = devices.findByIdAndApiKeyAndActiveTrue(deviceId, apiKey)
                .orElseThrow(() -> new ApiException(org.springframework.http.HttpStatus.UNAUTHORIZED, "Périphérique non autorisé"));
        UUID schoolId = device.getSchoolId();

        if (in.dedupKey() != null && repo.existsByDedupKey(in.dedupKey())) {
            // Idempotent replay after a reconnection — ignore silently.
            Student s = students.findBySchoolIdAndMatriculeAndActiveTrue(schoolId, in.matricule()).orElse(null);
            return s == null ? null
                    : repo.findBySchoolIdAndStudentIdAndDate(schoolId, s.getId(), LocalDate.now())
                          .map(r -> toView(r, s)).orElse(null);
        }

        Student student = students.findBySchoolIdAndMatriculeAndActiveTrue(schoolId, in.matricule())
                .orElseThrow(() -> ApiException.notFound("Élève " + in.matricule()));

        LocalTime t = in.time() != null && !in.time().isBlank()
                ? LocalTime.parse(in.time()) : LocalTime.now();
        int lateMin = (int) Math.max(0, java.time.Duration.between(SCHOOL_START, t).toMinutes());
        String status = lateMin > 0 ? "late" : "present";

        LocalDate today = LocalDate.now();
        AttendanceRecord rec = repo.findBySchoolIdAndStudentIdAndDate(schoolId, student.getId(), today)
                .orElseGet(AttendanceRecord::new);
        rec.setSchoolId(schoolId);
        rec.setStudentId(student.getId());
        rec.setDate(today);
        rec.setStatus(status);
        rec.setCheckInTime(String.format("%02d:%02d", t.getHour(), t.getMinute()));
        rec.setLateMinutes(lateMin);
        rec.setSource("fingerprint");
        rec.setDedupKey(in.dedupKey());

        AttendanceView view = toView(repo.save(rec), student);
        realtime.broadcast(schoolId, "attendance", view);   // <- live update to the board
        return view;
    }

    private AttendanceView toView(AttendanceRecord r, Student s) {
        String name = s == null ? "—" : s.getLastName().toUpperCase() + " " + s.getFirstName();
        String matricule = s == null ? "" : s.getMatricule();
        String className = s == null ? "" : s.getClassName();
        return new AttendanceView(r.getStudentId(), matricule, name, className,
                r.getDate(), r.getStatus(), r.getCheckInTime(), r.getLateMinutes(), r.getSource());
    }
}

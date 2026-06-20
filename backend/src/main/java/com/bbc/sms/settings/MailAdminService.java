package com.bbc.sms.settings;

import com.bbc.sms.platform.mail.MailConfig;
import com.bbc.sms.platform.mail.MailConfigRepository;
import com.bbc.sms.platform.tenant.TenantContext;
import com.bbc.sms.settings.dto.SettingsDtos.MailConfigUpdate;
import com.bbc.sms.settings.dto.SettingsDtos.MailConfigView;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Read/write the tenant's SMTP configuration for the admin Settings UI. */
@Service
public class MailAdminService {

    private final MailConfigRepository repo;

    public MailAdminService(MailConfigRepository repo) {
        this.repo = repo;
    }

    @Transactional(readOnly = true)
    public MailConfigView get() {
        MailConfig c = repo.findById(TenantContext.get()).orElse(null);
        if (c == null) {
            return new MailConfigView(false, null, 587, null, false, null, null, true, true);
        }
        boolean hasPwd = c.getPassword() != null && !c.getPassword().isBlank();
        return new MailConfigView(c.isEnabled(), c.getHost(), c.getPort(), c.getUsername(),
                hasPwd, c.getFromAddress(), c.getFromName(), c.isUseTls(), c.isNotifyOnUserCreate());
    }

    @Transactional
    public MailConfigView update(MailConfigUpdate in) {
        UUID schoolId = TenantContext.get();
        MailConfig c = repo.findById(schoolId).orElseGet(() -> {
            MailConfig n = new MailConfig();
            n.setSchoolId(schoolId);
            return n;
        });
        c.setEnabled(in.enabled());
        c.setHost(blankToNull(in.host()));
        if (in.port() != null) c.setPort(in.port());
        c.setUsername(blankToNull(in.username()));
        // A blank password means "keep the stored one" (the view never echoes it).
        if (in.password() != null && !in.password().isBlank()) c.setPassword(in.password());
        c.setFromAddress(blankToNull(in.fromAddress()));
        c.setFromName(blankToNull(in.fromName()));
        if (in.useTls() != null) c.setUseTls(in.useTls());
        if (in.notifyOnUserCreate() != null) c.setNotifyOnUserCreate(in.notifyOnUserCreate());
        c.setUpdatedAt(OffsetDateTime.now());
        repo.save(c);
        return get();
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }
}

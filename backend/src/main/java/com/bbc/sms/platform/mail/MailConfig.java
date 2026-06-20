package com.bbc.sms.platform.mail;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Per-tenant SMTP configuration (one row per school). */
@Entity
@Table(name = "mail_config")
@Getter
@Setter
public class MailConfig {

    @Id
    @Column(name = "school_id")
    private UUID schoolId;

    private boolean enabled = false;
    private String host;
    private int port = 587;
    private String username;
    private String password;

    @Column(name = "from_address")
    private String fromAddress;

    @Column(name = "from_name")
    private String fromName;

    @Column(name = "use_tls")
    private boolean useTls = true;

    @Column(name = "notify_on_user_create")
    private boolean notifyOnUserCreate = true;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt = OffsetDateTime.now();
}

package com.bbc.sms.messaging;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * A correspondence notice ("carnet de correspondance") addressed to a student's
 * parents by school staff. When {@code requiresAck} is set, parents are expected
 * to sign/acknowledge it — captured here as {@code acknowledgedAt}/{@code acknowledgedBy}.
 */
@Entity
@Table(name = "correspondence")
@Getter
@Setter
public class Correspondence {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "school_id", nullable = false)
    private UUID schoolId;

    @Column(name = "student_id", nullable = false)
    private UUID studentId;

    @Column(nullable = false)
    private String category;

    @Column(nullable = false)
    private String subject;

    @Column(nullable = false, columnDefinition = "text")
    private String body;

    @Column(name = "requires_ack", nullable = false)
    private boolean requiresAck = true;

    @Column(name = "acknowledged_at")
    private Instant acknowledgedAt;

    @Column(name = "acknowledged_by")
    private String acknowledgedBy;

    @Column(name = "sender_name")
    private String senderName;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();
}

package com.bbc.sms.attendance;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "device")
@Getter
@Setter
public class Device {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "school_id", nullable = false)
    private UUID schoolId;

    @Column(nullable = false)
    private String label;

    @Column(name = "api_key", nullable = false, unique = true)
    private String apiKey;

    @Column(nullable = false)
    private boolean active = true;

    /** Stamped on every check-in. Null means the reader has never reported in. */
    @Column(name = "last_seen_at")
    private OffsetDateTime lastSeenAt;

    private String location;

    private String model;
}

package com.bbc.sms.events;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "school_event")
@Getter
@Setter
public class SchoolEvent {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "school_id", nullable = false)
    private UUID schoolId;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private String type;              // meeting | exam | culture | annonce

    @Column(name = "event_date", nullable = false)
    private LocalDate eventDate;

    @Column(columnDefinition = "text")
    private String description;

    @Column(nullable = false)
    private String audience = "all";  // all | classes

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "target_classes", columnDefinition = "jsonb")
    private List<String> targetClasses = new ArrayList<>();

    @Column(nullable = false)
    private boolean notified = false;

    @Column(name = "notified_at")
    private LocalDate notifiedAt;

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;
}

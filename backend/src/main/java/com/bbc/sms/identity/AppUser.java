package com.bbc.sms.identity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "app_user")
@Getter
@Setter
public class AppUser {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "school_id", nullable = false)
    private UUID schoolId;

    @Column(nullable = false)
    private String username;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    private String initials;

    @Column(name = "role_code", nullable = false)
    private String roleCode;

    @Column(name = "employee_id")
    private UUID employeeId;

    @Column(nullable = false)
    private String locale = "fr";

    @Column(nullable = false)
    private boolean active = true;
}

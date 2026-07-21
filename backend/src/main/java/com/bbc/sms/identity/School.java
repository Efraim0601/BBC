package com.bbc.sms.identity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "school")
@Getter
@Setter
public class School {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false, unique = true)
    private String code;

    @Column(nullable = false)
    private String name;

    private String motto;

    private String city;
    private String country;
    private String address;
    private String phone;
    private String email;
    private String website;

    /** Money label shown across the app — receipts, KPIs, payroll. */
    private String currency;

    /** Supervising authority printed on bulletins, e.g. "République du Cameroun · MINESEC". */
    private String authority;
}

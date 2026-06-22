package com.bbc.sms.staff;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "employee")
@Getter
@Setter
public class Employee {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "school_id", nullable = false)
    private UUID schoolId;

    @Column(nullable = false)
    private String code;

    @Column(nullable = false)
    private String name;

    private String initials;

    private int hue = 210;

    private String sex;

    @Column(nullable = false)
    private String type = "Permanent";

    private String email;

    private String phone;

    @Column(name = "form_class")
    private String formClass;

    @Column(name = "department_id")
    private UUID departmentId;

    @Column(name = "hired_on")
    private LocalDate hiredOn;

    @Column(name = "monthly_salary")
    private long monthlySalary;

    @Column(name = "hourly_rate")
    private int hourlyRate;

    @Column(name = "monthly_hours")
    private int monthlyHours;

    @Column(nullable = false)
    private boolean active = true;

    @ElementCollection
    @CollectionTable(name = "employee_role", joinColumns = @JoinColumn(name = "employee_id"))
    @Column(name = "role_code")
    private Set<String> roles = new HashSet<>();
}

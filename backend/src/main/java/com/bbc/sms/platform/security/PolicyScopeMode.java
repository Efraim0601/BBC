package com.bbc.sms.platform.security;

/** Data-scope vocabulary shared by role rules, user overrides and decisions. */
public enum PolicyScopeMode {
    NONE,
    SCHOOL_ALL,
    PARCOURS_ALLOWED,
    ASSIGNED_CLASSES,
    TITULAIRE_CLASSES,
    ASSIGNED_CLASS_SUBJECTS,
    TIMETABLE_OCCURRENCES_ASSIGNED,
    LINKED_CHILDREN,
    SELF,
    CLASS_SET,
    SUBJECT_SET,
    CLASS_SUBJECT_SET,
    PARCOURS_SET
}

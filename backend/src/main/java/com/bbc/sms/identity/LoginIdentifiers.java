package com.bbc.sms.identity;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

/** Shared normalization for credential issuance, sign-in and password recovery. */
public final class LoginIdentifiers {
    private LoginIdentifiers() {}

    public static String email(String value) {
        String email = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        return email.length() <= 254 && email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$") ? email : null;
    }

    /** BBC is in Cameroon: local nine-digit numbers use the displayed +237 default. */
    public static String phone(String value) {
        if (value == null || !value.trim().matches("^[+0-9][0-9\\s().-]{5,24}$")) return null;
        String phone = value.trim().replaceAll("[\\s().-]", "");
        if (phone.matches("[26][0-9]{8}")) phone = "+237" + phone;
        else if (phone.matches("237[26][0-9]{8}")) phone = "+" + phone;
        else if (phone.startsWith("00")) phone = "+" + phone.substring(2);
        return phone.matches("\\+[1-9][0-9]{7,14}") ? phone : null;
    }

    /** Keep legacy usernames, but resolve equivalent phone formats to one account. */
    public static List<String> candidates(String value) {
        var candidates = new LinkedHashSet<String>();
        String trimmed = value == null ? "" : value.trim();
        if (trimmed.isEmpty()) return List.of();
        candidates.add(trimmed);
        String email = email(trimmed);
        if (email != null) candidates.add(email);
        String phone = phone(trimmed);
        if (phone != null) {
            candidates.add(phone);
            candidates.add(phone.substring(1));
            candidates.add("00" + phone.substring(1));
            if (phone.matches("\\+237[26][0-9]{8}")) candidates.add(phone.substring(4));
        }
        return List.copyOf(candidates);
    }
}

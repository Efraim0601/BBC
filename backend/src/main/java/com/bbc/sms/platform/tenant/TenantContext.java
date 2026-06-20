package com.bbc.sms.platform.tenant;

import java.util.UUID;

/**
 * Holds the current request's tenant (school) id, derived from the JWT.
 * Every service reads it to scope queries — defence in depth against cross-tenant leaks.
 */
public final class TenantContext {

    private static final ThreadLocal<UUID> CURRENT = new ThreadLocal<>();

    private TenantContext() {}

    public static void set(UUID schoolId) { CURRENT.set(schoolId); }

    public static UUID get() {
        UUID id = CURRENT.get();
        if (id == null) {
            throw new IllegalStateException("No tenant bound to the current request");
        }
        return id;
    }

    public static boolean isSet() { return CURRENT.get() != null; }

    public static void clear() { CURRENT.remove(); }
}

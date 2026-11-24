package com.secondserve.service;

import com.secondserve.exception.ForbiddenException;
import com.secondserve.model.Principal;
import com.secondserve.model.Role;

import java.util.Set;

/**
 * Role-based access control in one framework-free place, used by BOTH the standalone server and the Spring interceptor.
 * Default is deny-by-role: any /api route not listed below is ADMIN only.
 * Record-level ownership (a donor only sees its own batches, an NGO only its own deliveries) is enforced in the services.
 */
public final class AccessPolicy {
    private AccessPolicy() {}

    private static final Set<Role> ANY = Set.of(Role.ADMIN, Role.DONOR, Role.NGO);
    private static final Set<Role> ADMIN = Set.of(Role.ADMIN);
    private static final Set<Role> DONOR = Set.of(Role.DONOR);
    private static final Set<Role> NGO = Set.of(Role.NGO);
    private static final Set<Role> DONOR_NGO = Set.of(Role.DONOR, Role.NGO);

    public static boolean isPublic(String method, String path) {
        return (method.equals("POST") && (path.equals("/api/auth/login") || path.equals("/api/auth/register"))) || method.equals("OPTIONS");
    }

    public static Set<Role> allowedRoles(String method, String path) {
        if (path.equals("/api/auth/me") || path.equals("/api/auth/logout") || path.equals("/api/auth/password")) return ANY;
        if (path.startsWith("/api/notifications")) return ANY;
        if (path.startsWith("/api/my/donations")) return DONOR;
        if (path.equals("/api/my/ngo")) return NGO;
        if (path.equals("/api/my/deliveries")) return DONOR_NGO;
        if (path.equals("/api/dashboard/donor")) return DONOR;
        if (path.equals("/api/dashboard/ngo")) return NGO;
        if (path.matches("^/api/deliveries/[^/]+/handover$")) return DONOR;
        if (path.matches("^/api/deliveries/[^/]+/confirm$")) return NGO;
        if (path.matches("^/api/deliveries/[^/]+/report$")) return Set.of(Role.NGO, Role.DONOR, Role.ADMIN);
        return ADMIN;
    }

    /** Throws ForbiddenException when the principal's role may not call the route. */
    public static void check(String method, String path, Principal p) {
        if (!allowedRoles(method, path).contains(p.role()))
            throw new ForbiddenException("Your " + p.role().name().toLowerCase() + " account is not allowed to use this feature.");
    }
}

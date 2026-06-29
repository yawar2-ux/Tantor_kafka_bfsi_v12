package io.translab.tantor.server.governance.service;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public final class PrincipalUtil {
    private PrincipalUtil() {}

    public static String currentUser() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        return (a == null || a.getName() == null) ? "system" : a.getName();
    }

    public static String currentRole() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        if (a == null) return null;
        return a.getAuthorities().stream()
                .map(Object::toString)
                .filter(s -> s.startsWith("ROLE_"))
                .map(s -> s.substring(5))
                .findFirst().orElse(null);
    }
}

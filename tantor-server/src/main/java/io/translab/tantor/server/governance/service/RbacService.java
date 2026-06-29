package io.translab.tantor.server.governance.service;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/** Resolves the effective permission set for a role from role_permissions. */
@Service
@RequiredArgsConstructor
public class RbacService {

    private final JdbcTemplate jdbcTemplate;

    public List<String> permissionsForRole(Integer roleId) {
        if (roleId == null) return List.of();
        return jdbcTemplate.queryForList(
                "SELECT p.name FROM permissions p " +
                "JOIN role_permissions rp ON rp.permission_id = p.id " +
                "WHERE rp.role_id = ?", String.class, roleId);
    }

    public List<String> permissionsForUser(UUID userId) {
        return jdbcTemplate.queryForList(
                "SELECT p.name FROM permissions p " +
                "JOIN role_permissions rp ON rp.permission_id = p.id " +
                "JOIN users u ON u.role_id = rp.role_id " +
                "WHERE u.id = ?", String.class, userId);
    }
}

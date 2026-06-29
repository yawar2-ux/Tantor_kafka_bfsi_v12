package io.translab.tantor.server.web;

import io.translab.tantor.server.domain.User;
import io.translab.tantor.server.governance.service.AuditService;
import io.translab.tantor.server.governance.service.RbacService;
import io.translab.tantor.server.repository.UserRepository;
import io.translab.tantor.server.security.JwtUtils;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final JwtUtils jwtUtils;
    private final UserRepository userRepository;
    private final RbacService rbacService;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;

    @PostMapping("/login")
    public ResponseEntity<?> authenticateUser(@RequestBody LoginRequest loginRequest) {
        Optional<User> userOpt = userRepository.findByUsername(loginRequest.getUsername());
        if (userOpt.isEmpty() || !userOpt.get().isActive()
                || !passwordEncoder.matches(loginRequest.getPassword(), userOpt.get().getPasswordHash())) {
            auditService.record(loginRequest.getUsername(), "LOGIN", "AUTH",
                    loginRequest.getUsername(), "FAILED");
            return ResponseEntity.status(401).body(new ErrorResponse("Invalid credentials"));
        }
        User user = userOpt.get();
        String roleName = user.getRole() != null ? user.getRole().getName() : "VIEWER";
        List<String> perms = rbacService.permissionsForUser(user.getId());
        String jwt = jwtUtils.generateToken(user.getUsername(), roleName, perms);
        auditService.record(user.getUsername(), roleName, "LOGIN", "AUTH",
                user.getUsername(), null, null, null, "SUCCESS", null);
        return ResponseEntity.ok(new JwtResponse(jwt, user.getUsername(), roleName, perms));
    }

    @Data
    static class LoginRequest {
        private String username;
        private String password;
    }

    @Data
    static class JwtResponse {
        private final String token;
        private final String username;
        private final String role;
        private final List<String> permissions;
    }

    @Data
    static class ErrorResponse {
        private final String error;
    }
}

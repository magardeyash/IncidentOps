package com.incidentops.controller;

import com.incidentops.config.JwtUtils;
import com.incidentops.dto.AuthRequest;
import com.incidentops.dto.AuthResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "JWT Token Generation Endpoint")
public class AuthController {

    private final JwtUtils jwtUtils;

    @Value("${jwt.expiration-ms:86400000}")
    private long expirationMs;

    /** Configurable via ADMIN_USERNAME env var; defaults to "admin" for local dev. */
    @Value("${admin.username:admin}")
    private String adminUsername;

    /** Configurable via ADMIN_PASSWORD env var; defaults to "password123" for local dev. */
    @Value("${admin.password:password123}")
    private String adminPassword;

    @PostMapping("/login")
    @Operation(summary = "Authenticate user and issue JWT Bearer token")
    public ResponseEntity<?> login(@Valid @RequestBody AuthRequest request) {
        if (!adminUsername.equals(request.getUsername()) || !adminPassword.equals(request.getPassword())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Unauthorized", "message", "Invalid username or password"));
        }

        String token = jwtUtils.generateToken(request.getUsername());

        return ResponseEntity.ok(AuthResponse.builder()
                .token(token)
                .tokenType("Bearer")
                .expiresInMs(expirationMs)
                .build());
    }
}

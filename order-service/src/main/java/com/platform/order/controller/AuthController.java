package com.platform.order.controller;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final String jwtSecret;

    public AuthController(@org.springframework.beans.factory.annotation.Value("${app.jwt.secret:order-platform-secret-key-2024}") String jwtSecret) {
        this.jwtSecret = jwtSecret;
    }

    @PostMapping("/token")
    public Mono<ResponseEntity<TokenResponse>> token(@Valid @RequestBody TokenRequest request) {
        if ("admin".equals(request.getUsername()) && "admin123".equals(request.getPassword())) {
            String token = buildToken(request.getUsername(), List.of("ADMIN"));
            return Mono.just(ResponseEntity.ok(new TokenResponse(token)));
        }
        if ("user".equals(request.getUsername()) && "user123".equals(request.getPassword())) {
            String token = buildToken(request.getUsername(), List.of("USER"));
            return Mono.just(ResponseEntity.ok(new TokenResponse(token)));
        }
        return Mono.just(ResponseEntity.status(401).build());
    }

    private String buildToken(String username, List<String> roles) {
        Instant now = Instant.now();
        return JWT.create()
                .withSubject(username)
                .withClaim("roles", roles)
                .withIssuedAt(now)
                .withExpiresAt(now.plusSeconds(3600))
                .sign(Algorithm.HMAC256(jwtSecret));
    }

    public static class TokenRequest {
        @NotBlank
        private String username;
        @NotBlank
        private String password;

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }
    }

    public static class TokenResponse {
        private String token;

        public TokenResponse(String token) {
            this.token = token;
        }

        public String getToken() {
            return token;
        }
    }
}

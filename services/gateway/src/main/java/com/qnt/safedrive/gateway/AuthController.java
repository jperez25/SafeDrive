package com.qnt.safedrive.gateway;

import com.qnt.safedrive.commons.CorrelationId;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);
    // Compared when the user is unknown, so a wrong email and a wrong password take the same time.
    private static final String DUMMY_HASH = "$2y$10$xqxn2svzqRCsKbMlrnhVY.XFxiv4xnQnZmjeddQ6ErroljSrK6jkW";

    record LoginRequest(String email, String password) {}

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final JwtService jwt;

    AuthController(UserRepository users, PasswordEncoder encoder, JwtService jwt) {
        this.users = users;
        this.encoder = encoder;
        this.jwt = jwt;
    }

    /** The driver ID comes from the X-Driver-Id header and goes into the token as is (PoC, no ownership check). */
    @PostMapping("/v1/auth/token")
    ResponseEntity<?> token(@RequestBody(required = false) LoginRequest body,
                            @RequestHeader(value = "X-Driver-Id", required = false) String driverId) {
        if (body == null || blank(body.email()) || blank(body.password()) || blank(driverId)) {
            return error(400, "bad_request", "email, password and the X-Driver-Id header are required");
        }
        var user = users.findByEmail(body.email());
        boolean ok = encoder.matches(body.password(), user.map(UserRepository.User::passwordHash).orElse(DUMMY_HASH));
        if (user.isEmpty() || !ok || !user.get().active()) {
            log.info("login rejected");
            return error(401, "unauthorized", "invalid credentials");
        }
        String token = jwt.issue(user.get().userId().toString(), driverId, user.get().scopes());
        log.info("token issued for user {}", user.get().userId());
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("accessToken", token);
        out.put("tokenType", "Bearer");
        out.put("expiresIn", jwt.ttlSeconds());
        out.put("scope", String.join(" ", user.get().scopes()));
        return ResponseEntity.ok(out);
    }

    @GetMapping("/.well-known/jwks.json")
    Map<String, Object> jwks() {
        return jwt.jwks();
    }

    static ResponseEntity<Map<String, Object>> error(int status, String code, String message) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("status", code);
        out.put("message", message);
        out.put("correlationId", String.valueOf(CorrelationId.current()));
        return ResponseEntity.status(status).body(out);
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }
}

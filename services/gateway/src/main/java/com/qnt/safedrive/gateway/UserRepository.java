package com.qnt.safedrive.gateway;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class UserRepository {

    record User(UUID userId, String passwordHash, List<String> scopes, boolean active) {}

    private final JdbcClient jdbc;

    UserRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    Optional<User> findByEmail(String email) {
        return jdbc.sql("SELECT user_id, password_hash, scopes, status FROM users WHERE email = :email")
                .param("email", email)
                .query((rs, n) -> {
                    String scopes = rs.getString("scopes");
                    return new User(
                            rs.getObject("user_id", UUID.class),
                            rs.getString("password_hash"),
                            scopes.isBlank() ? List.of() : List.of(scopes.split(" ")),
                            "active".equals(rs.getString("status")));
                })
                .optional();
    }
}

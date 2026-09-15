package com.ase.billing.domain;

import com.ase.billing.domain.enums.UserRole;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/**
 * An application login. Mapped to the `users` table created in V1__schema.sql.
 *
 * Without this entity (and {@link com.ase.billing.repo.UserRepository} and
 * {@link com.ase.billing.service.DbUserDetailsService}) Spring Security has no
 * way to reach the table: it falls back to its auto-configured in-memory user
 * and every real login fails with "Bad credentials".
 */
@Entity
@Table(name = "users")
@Getter
@Setter
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 60)
    private String username;

    /** BCrypt. Never stored or compared in plain text. */
    @Column(name = "password_hash", nullable = false, length = 120)
    private String passwordHash;

    @Column(name = "display_name", nullable = false, length = 120)
    private String displayName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private UserRole role = UserRole.USER;

    @Column(nullable = false)
    private boolean active = true;
}

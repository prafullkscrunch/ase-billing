package com.ase.billing;

import com.ase.billing.domain.User;
import com.ase.billing.domain.enums.UserRole;
import com.ase.billing.repo.UserRepository;
import com.ase.billing.service.DbUserDetailsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Proves that a user stored in the database can actually log in.
 *
 * This is the test that was missing when the admin account could not sign in:
 * the application compiled, booted and served the login page, but nothing
 * connected Spring Security to the `users` table, and the seeded password hash
 * did not decode to the documented password. Neither fault is visible without
 * exercising authentication end to end.
 */
@SpringBootTest
@ActiveProfiles("test")
class AuthenticationTest {

    @Autowired private UserRepository users;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private DbUserDetailsService userDetailsService;
    @Autowired private DaoAuthenticationProvider authenticationProvider;

    @BeforeEach
    void seedAdmin() {
        users.deleteAll();
        User admin = new User();
        admin.setUsername("admin");
        admin.setPasswordHash(passwordEncoder.encode("changeme"));
        admin.setDisplayName("Administrator");
        admin.setRole(UserRole.ADMIN);
        admin.setActive(true);
        users.save(admin);
    }

    @Test
    @DisplayName("the seeded admin authenticates and carries ROLE_ADMIN")
    void adminCanAuthenticate() {
        Authentication result = authenticationProvider.authenticate(
                new UsernamePasswordAuthenticationToken("admin", "changeme"));

        assertThat(result.isAuthenticated()).isTrue();
        assertThat(result.getAuthorities())
                .extracting(Object::toString)
                .containsExactly("ROLE_ADMIN");
    }

    @Test
    @DisplayName("a wrong password is rejected")
    void wrongPasswordRejected() {
        assertThatThrownBy(() -> authenticationProvider.authenticate(
                new UsernamePasswordAuthenticationToken("admin", "not-the-password")))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    @DisplayName("the username lookup is case insensitive")
    void usernameIsCaseInsensitive() {
        assertThat(userDetailsService.loadUserByUsername("ADMIN").getUsername()).isEqualTo("admin");
    }

    @Test
    @DisplayName("an unknown user is not found")
    void unknownUserRejected() {
        assertThatThrownBy(() -> userDetailsService.loadUserByUsername("nobody"))
                .isInstanceOf(UsernameNotFoundException.class);
    }

    @Test
    @DisplayName("the hash stored by V3 decodes to 'changeme'")
    void v3HashMatchesDocumentedPassword() {
        String v3Hash = "$2a$10$tr0SEoN/zPz59xa68PwwPeXMygy5yQ//qcxj1298aYBPh52/uYnLC";
        assertThat(passwordEncoder.matches("changeme", v3Hash)).isTrue();

        // The hash V2 shipped with, for the record. It matches nothing.
        String v2Hash = "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy";
        assertThat(passwordEncoder.matches("changeme", v2Hash)).isFalse();
    }
}

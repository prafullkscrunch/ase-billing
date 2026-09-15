package com.ase.billing.service;

import com.ase.billing.domain.User;
import com.ase.billing.repo.UserRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Loads logins from the `users` table.
 *
 * Declaring this bean is what makes Spring Security use the database. Without a
 * UserDetailsService in the context, Spring Boot auto-configures a single
 * in-memory user named "user" with a random password printed at startup, and any
 * other credentials are rejected as "Bad credentials".
 */
@Service
public class DbUserDetailsService implements UserDetailsService {

    private final UserRepository users;

    public DbUserDetailsService(UserRepository users) {
        this.users = users;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = users.findByUsernameIgnoreCase(username)
                .orElseThrow(() -> new UsernameNotFoundException("No such user: " + username));

        return org.springframework.security.core.userdetails.User
                .withUsername(user.getUsername())
                .password(user.getPasswordHash())
                // Spring expects ROLE_ADMIN / ROLE_USER; roles() adds the prefix.
                .roles(user.getRole().name())
                .disabled(!user.isActive())
                .build();
    }
}

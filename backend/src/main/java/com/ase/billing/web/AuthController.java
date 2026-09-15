package com.ase.billing.web;

import com.ase.billing.repo.UserRepository;
import com.ase.billing.web.dto.Dtos.CurrentUser;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserRepository users;

    public AuthController(UserRepository users) {
        this.users = users;
    }

    /** The SPA calls this on load to decide whether to show the login screen. */
    @GetMapping("/me")
    public ResponseEntity<CurrentUser> me(Authentication auth) {
        if (auth == null || !auth.isAuthenticated()) {
            return ResponseEntity.status(401).build();
        }
        return users.findByUsernameIgnoreCase(auth.getName())
                .map(u -> ResponseEntity.ok(
                        new CurrentUser(u.getUsername(), u.getDisplayName(), u.getRole().name())))
                .orElseGet(() -> ResponseEntity.status(401).build());
    }
}

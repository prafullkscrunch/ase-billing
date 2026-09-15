package com.ase.billing.config;

import com.ase.billing.service.DbUserDetailsService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;

import java.io.IOException;

/**
 * Single-office deployment: session cookie, ADMIN and USER roles.
 *
 * The front end is a single-page app, so every auth outcome is a status code and
 * a JSON body. A 302 to an HTML login page would arrive at fetch() as an opaque
 * success and the UI would render a login form as if it were data.
 */
@Configuration
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public DaoAuthenticationProvider authenticationProvider(DbUserDetailsService userDetailsService,
                                                            PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        // Report an unknown username as "Bad credentials" rather than confirming
        // which accounts exist. Flip to false while debugging a login problem.
        provider.setHideUserNotFoundExceptions(true);
        return provider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http,
                                           DaoAuthenticationProvider authenticationProvider) throws Exception {
        http
            .authenticationProvider(authenticationProvider)
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/auth/login", "/actuator/health").permitAll()
                // Static assets and the SPA shell are public; the API behind them is not.
                .requestMatchers("/", "/index.html", "/assets/**", "/favicon.ico").permitAll()
                .requestMatchers("/api/settings/**").hasRole("ADMIN")
                .requestMatchers("/api/**").authenticated()
                .anyRequest().permitAll())
            .formLogin(form -> form
                .loginProcessingUrl("/api/auth/login")
                .successHandler((req, res, authn) -> res.setStatus(HttpStatus.NO_CONTENT.value()))
                .failureHandler((req, res, ex) -> writeJson(res, HttpStatus.UNAUTHORIZED,
                        "That username and password did not match.")))
            .logout(out -> out
                .logoutUrl("/api/auth/logout")
                .logoutSuccessHandler((req, res, authn) -> res.setStatus(HttpStatus.NO_CONTENT.value())))
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED))
                .accessDeniedHandler((req, res, denied) -> writeJson(res, HttpStatus.FORBIDDEN,
                        "Your account does not have access to that.")))
            // Same-origin SPA sharing the session cookie. Turn CSRF back on before
            // this is reachable from anywhere but the office machine.
            .csrf(csrf -> csrf.disable());
        return http.build();
    }

    private static void writeJson(HttpServletResponse res, HttpStatus status, String message)
            throws IOException {
        res.setStatus(status.value());
        res.setContentType(MediaType.APPLICATION_JSON_VALUE);
        res.getWriter().write("{\"message\":\"%s\",\"problems\":[]}".formatted(message));
    }
}

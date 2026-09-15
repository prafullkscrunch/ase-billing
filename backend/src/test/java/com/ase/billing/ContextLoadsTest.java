package com.ase.billing;

import com.ase.billing.repo.InvoiceRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Smoke test. Boots the whole application context against H2 so that Hibernate
 * entity mapping, Spring proxying and repository wiring are all exercised.
 *
 * This is the test that fails first when the JDK moves ahead of the framework:
 * Spring's CGLIB proxies and Hibernate's bytecode enhancement are the parts that
 * break on a JDK the framework has not been certified against.
 */
@SpringBootTest
@ActiveProfiles("test")
class ContextLoadsTest {

    @Autowired
    private InvoiceRepository invoices;

    @Test
    void contextLoadsAndRepositoriesAreWired() {
        assertThat(invoices).isNotNull();
        assertThat(invoices.count()).isZero();
    }
}

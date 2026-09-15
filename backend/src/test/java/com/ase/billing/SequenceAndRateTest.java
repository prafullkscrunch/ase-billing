package com.ase.billing;

import com.ase.billing.domain.Customer;
import com.ase.billing.domain.ServiceCategory;
import com.ase.billing.domain.ServiceItem;
import com.ase.billing.domain.ServiceRate;
import com.ase.billing.domain.enums.CalculationType;
import com.ase.billing.domain.enums.GstTreatment;
import com.ase.billing.domain.enums.PdfLayout;
import com.ase.billing.exception.ValidationException;
import com.ase.billing.repo.*;
import com.ase.billing.service.RateUpdateService;
import com.ase.billing.service.SequenceAdminService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The two behaviours ASE asked for after seeing the first bills:
 * skipping the invoice number forward, and a rate typed on a bill sticking.
 */
@SpringBootTest
@ActiveProfiles("test")
class SequenceAndRateTest {

    private static final LocalDate BILL_DATE = LocalDate.of(2026, 9, 13);

    @Autowired private SequenceAdminService sequences;
    @Autowired private RateUpdateService rateUpdates;
    @Autowired private CustomerRepository customers;
    @Autowired private ServiceCategoryRepository categories;
    @Autowired private ServiceItemRepository services;
    @Autowired private ServiceRateRepository rates;
    @Autowired private InvoiceRepository invoices;
    @Autowired private InvoiceSequenceRepository sequenceRows;

    private Customer customer;
    private ServiceItem vgm;

    @BeforeEach
    void seed() {
        // Order matters: invoice_sequences and invoices both reference customers.
        invoices.deleteAll();
        sequenceRows.deleteAll();
        rates.deleteAll();
        services.deleteAll();
        categories.deleteAll();
        customers.deleteAll();

        customer = new Customer();
        customer.setCode("HC");
        customer.setName("HANGAL COFFEE EXPORTING PVT LTD");
        customer.setGstTreatment(GstTreatment.INTRA);
        customer = customers.save(customer);

        ServiceCategory cnf = new ServiceCategory();
        cnf.setCode("CNF");
        cnf.setDescription("Clearing & Forwarding");
        cnf.setPdfLayout(PdfLayout.ITEMISED);
        cnf = categories.save(cnf);

        vgm = new ServiceItem();
        vgm.setCategory(cnf);
        vgm.setName("VGM expenses");
        vgm.setCalculationType(CalculationType.PER_TEU);
        vgm = services.save(vgm);

        ServiceRate r = new ServiceRate();
        r.setService(vgm);
        r.setRate(new BigDecimal("750.00"));
        r.setEffectiveFrom(LocalDate.of(2026, 4, 1));
        rates.save(r);
    }

    @Test
    @DisplayName("the next number can be skipped forward")
    void jumpForward() {
        assertThat(sequences.peek(customer.getId(), "CNF", BILL_DATE).nextNumber()).isEqualTo(1);

        var after = sequences.jumpTo(customer.getId(), BILL_DATE, 518, "CNF");

        assertThat(after.nextNumber()).isEqualTo(518);
        assertThat(after.previewNumber()).isEqualTo("ASE/HC/CNF/518/2026-27");
        assertThat(after.financialYear()).isEqualTo("2026-27");
    }

    @Test
    @DisplayName("the next number cannot be moved backwards")
    void cannotGoBackwards() {
        sequences.jumpTo(customer.getId(), BILL_DATE, 518, "CNF");

        assertThatThrownBy(() -> sequences.jumpTo(customer.getId(), BILL_DATE, 100, "CNF"))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("moved forward but not back");
    }

    @Test
    @DisplayName("a rate typed on a bill becomes the new default")
    void rateChangeSticks() {
        assertThat(currentRate()).isEqualByComparingTo("750.00");

        boolean moved = rateUpdates.updateRate(vgm, customer, new BigDecimal("500.00"),
                BILL_DATE, null, "admin");

        assertThat(moved).isTrue();
        assertThat(currentRate()).isEqualByComparingTo("500.00");
    }

    @Test
    @DisplayName("the old rate is closed, not deleted, so earlier bills still resolve to it")
    void oldRateIsClosedNotDeleted() {
        rateUpdates.updateRate(vgm, customer, new BigDecimal("500.00"), BILL_DATE, null, "admin");

        // A bill dated before the change still resolves the rate it was issued under.
        BigDecimal before = rates.resolve(vgm.getId(), customer.getId(), LocalDate.of(2026, 8, 8))
                .get(0).getRate();
        assertThat(before).isEqualByComparingTo("750.00");

        assertThat(rates.findAll()).hasSize(2);
    }

    @Test
    @DisplayName("setting a rate to what it already is changes nothing")
    void noOpRateChange() {
        boolean moved = rateUpdates.updateRate(vgm, customer, new BigDecimal("750.00"),
                BILL_DATE, null, "admin");

        assertThat(moved).isFalse();
        assertThat(rates.findAll()).hasSize(1);
    }

    private BigDecimal currentRate() {
        return rates.resolve(vgm.getId(), customer.getId(), BILL_DATE).get(0).getRate();
    }
}

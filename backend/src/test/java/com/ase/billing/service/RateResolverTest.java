package com.ase.billing.service;

import com.ase.billing.domain.CochinRateOverride;
import com.ase.billing.domain.Customer;
import com.ase.billing.domain.ServiceItem;
import com.ase.billing.domain.ServiceRate;
import com.ase.billing.repo.CochinRateOverrideRepository;
import com.ase.billing.repo.ServiceRateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The Cochin flag (confirmed rare: max ~10/year) only ever changes the rate
 * for a service that actually has a {@link CochinRateOverride} row — every
 * other CNF service must resolve exactly as it always has, flag or no flag.
 */
class RateResolverTest {

    private ServiceRateRepository rates;
    private CochinRateOverrideRepository cochinOverrides;
    private RateResolver resolver;
    private Customer customer;
    private LocalDate today;

    @BeforeEach
    void setUp() {
        rates = mock(ServiceRateRepository.class);
        cochinOverrides = mock(CochinRateOverrideRepository.class);
        resolver = new RateResolver(rates, cochinOverrides);
        customer = new Customer();
        customer.setId(1L);
        today = LocalDate.of(2026, 9, 1);
    }

    private ServiceItem serviceWithId(long id) {
        ServiceItem s = new ServiceItem();
        s.setId(id);
        return s;
    }

    private ServiceRate rateOf(String amount) {
        ServiceRate r = new ServiceRate();
        r.setRate(new BigDecimal(amount));
        return r;
    }

    @Test
    void withoutCochinFlag_alwaysReturnsTheGlobalRate_evenIfAnOverrideExists() {
        ServiceItem emptyContainerSurvey = serviceWithId(101L);
        when(rates.resolve(101L, 1L, today)).thenReturn(List.of(rateOf("500.00")));

        CochinRateOverride override = new CochinRateOverride();
        override.setRate(new BigDecimal("800.00"));
        when(cochinOverrides.findByServiceId(101L)).thenReturn(Optional.of(override));

        BigDecimal result = resolver.rateOrZero(emptyContainerSurvey, customer, today, false);

        assertThat(result).isEqualByComparingTo("500.00");
    }

    @Test
    void withCochinFlag_usesTheOverrideRateWhenOneExists() {
        ServiceItem emptyContainerSurvey = serviceWithId(101L);
        when(rates.resolve(101L, 1L, today)).thenReturn(List.of(rateOf("500.00")));

        CochinRateOverride override = new CochinRateOverride();
        override.setRate(new BigDecimal("800.00"));
        when(cochinOverrides.findByServiceId(101L)).thenReturn(Optional.of(override));

        BigDecimal result = resolver.rateOrZero(emptyContainerSurvey, customer, today, true);

        assertThat(result).isEqualByComparingTo("800.00");
    }

    @Test
    void withCochinFlag_fallsBackToTheGlobalRateWhenNoOverrideExists() {
        // e.g. Fumigation charges — doesn't differ for Cochin, so it has no
        // row in cochin_rate_overrides at all.
        ServiceItem fumigation = serviceWithId(202L);
        when(rates.resolve(202L, 1L, today)).thenReturn(List.of(rateOf("2000.00")));
        when(cochinOverrides.findByServiceId(202L)).thenReturn(Optional.empty());

        BigDecimal result = resolver.rateOrZero(fumigation, customer, today, true);

        assertThat(result).isEqualByComparingTo("2000.00");
    }
}

package com.ase.billing.service;

import com.ase.billing.domain.ServiceItem;
import com.ase.billing.domain.ServiceRate;
import com.ase.billing.domain.enums.CalculationType;
import com.ase.billing.exception.NotFoundException;
import com.ase.billing.exception.ValidationException;
import com.ase.billing.repo.ServiceItemRepository;
import com.ase.billing.repo.ServiceRateRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * The "Quotation" screen: a printable, editable rate card for Hangal Coffee
 * Exporting — the sole customer these rates are ever billed against, so this
 * manages the general house rate directly rather than any customer-specific
 * arrangement.
 *
 * Shows and edits two figures per charge, not the raw base/rate columns:
 *
 *   rate for 1 container   = base_amount + rate     (what a single-container
 *                                                     shipment actually pays)
 *   additional per container = rate                 (what each container
 *                                                     after the first adds)
 *
 * ASE thinks in "first container costs X, each one after costs Y more" — the
 * base_amount/rate split that makes the billing formula work is an
 * implementation detail nobody editing a rate card should have to do the
 * arithmetic for. A flat charge (Pre-shipment documentation, EDI, Post
 * shipment) doesn't scale with containers at all, so its "additional" figure
 * is the same as its "1 container" figure and base_amount stays zero.
 *
 * Saving here goes through the exact same close-old/open-new rate history
 * ({@link RateUpdateService#updateGlobalRate}) that every other rate change in
 * this app uses, so a bill created after a change here picks it up
 * automatically, and every bill already issued keeps what it was billed with.
 */
@Service
public class QuotationService {

    private final ServiceItemRepository services;
    private final ServiceRateRepository rates;
    private final RateUpdateService rateUpdates;

    public QuotationService(ServiceItemRepository services, ServiceRateRepository rates,
                            RateUpdateService rateUpdates) {
        this.services = services;
        this.rates = rates;
        this.rateUpdates = rateUpdates;
    }

    public record Line(Long serviceId, String categoryCode, String categoryName, String name,
                       String calculationType, boolean scalesWithContainers,
                       BigDecimal rateForOneContainer, BigDecimal additionalPerContainer,
                       LocalDate effectiveFrom) {}

    @Transactional(readOnly = true)
    public List<Line> forCategory(String categoryCode) {
        LocalDate today = LocalDate.now();
        return services.findByCategoryCodeIgnoreCaseAndActiveTrueOrderBySortOrderAsc(categoryCode)
                .stream()
                .map(s -> toLine(s, today))
                .toList();
    }

    private Line toLine(ServiceItem s, LocalDate today) {
        List<ServiceRate> resolved = rates.resolveGlobal(s.getId(), today);
        BigDecimal rate = resolved.stream().findFirst().map(ServiceRate::getRate).orElse(BigDecimal.ZERO);
        LocalDate since = resolved.stream().findFirst().map(ServiceRate::getEffectiveFrom).orElse(null);

        boolean scales = scalesWithContainers(s.getCalculationType());
        BigDecimal oneContainer = s.getBaseAmount().add(rate);

        return new Line(s.getId(), s.getCategory().getCode(), s.getCategory().getDescription(), s.getName(),
                s.getCalculationType().name(), scales, oneContainer,
                scales ? rate : oneContainer, since);
    }

    /**
     * @param rateForOneContainer  what one container's worth of this charge costs
     * @param additionalPerContainer  what each container after the first adds;
     *                                ignored for a flat charge (it doesn't scale)
     */
    @Transactional
    public Line update(Long serviceId, BigDecimal rateForOneContainer,
                       BigDecimal additionalPerContainer, String username) {
        ServiceItem service = services.findById(serviceId)
                .orElseThrow(() -> new NotFoundException("Service", serviceId));

        boolean scales = scalesWithContainers(service.getCalculationType());
        BigDecimal additional = scales
                ? (additionalPerContainer == null ? BigDecimal.ZERO : additionalPerContainer)
                : rateForOneContainer;
        BigDecimal base = rateForOneContainer.subtract(additional);

        if (base.signum() < 0) {
            throw new ValidationException(
                    "The additional-per-container rate can't be more than the 1-container rate for "
                            + service.getName() + ".");
        }

        service.setBaseAmount(scales ? base : BigDecimal.ZERO);
        services.save(service);
        rateUpdates.updateGlobalRate(service, additional, LocalDate.now(), username);

        return toLine(service, LocalDate.now());
    }

    private static boolean scalesWithContainers(CalculationType type) {
        return type == CalculationType.PER_TEU || type == CalculationType.PER_TEU_PER_DAY;
    }
}

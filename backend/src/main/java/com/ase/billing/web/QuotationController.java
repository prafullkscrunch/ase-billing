package com.ase.billing.web;

import com.ase.billing.domain.TransportRoute;
import com.ase.billing.repo.ServiceCategoryRepository;
import com.ase.billing.repo.TransportRouteRepository;
import com.ase.billing.service.QuotationService;
import com.ase.billing.web.dto.Dtos.QuotationLine;
import com.ase.billing.web.dto.Dtos.QuotationUpdateRequest;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Stream;

/**
 * The Quotation screen: a printable, editable rate card for Hangal Coffee
 * Exporting, grouped by category. Reading is open to anyone who can reach the
 * app; writing moves the same general house rate every bill already reads
 * from, so every change here is logged the same way a rate change from an
 * invoice line is.
 *
 * <p>The Transportation tab includes both the per-TEU services in the T
 * category (e.g. "Additional transportation via Hassan") AND the flat
 * per-route rates from {@code transport_routes} (e.g. "Mangalore-
 * Kushalnagar") — these live in a separate table with no rate history, so
 * they weren't showing up here at all before; a route line is
 * distinguished by carrying a {@code routeId} instead of a
 * {@code serviceId} and is saved through the existing route-rate endpoint,
 * not {@link QuotationService#update}.
 */
@RestController
@RequestMapping("/api/quotation")
public class QuotationController {

    private static final Logger log = LoggerFactory.getLogger(QuotationController.class);

    private final QuotationService quotation;
    private final ServiceCategoryRepository categories;
    private final TransportRouteRepository routes;

    public QuotationController(QuotationService quotation, ServiceCategoryRepository categories,
                               TransportRouteRepository routes) {
        this.quotation = quotation;
        this.categories = categories;
        this.routes = routes;
    }

    /** Every category's rate card in one call, in the order categories are configured, with transport routes folded into the T category. */
    @GetMapping
    public List<QuotationLine> all() {
        return categories.findByActiveTrueOrderBySortOrderAsc().stream()
                .flatMap(c -> {
                    Stream<QuotationLine> serviceLines = quotation.forCategory(c.getCode()).stream()
                            .map(QuotationController::toView);
                    if (!"T".equalsIgnoreCase(c.getCode())) return serviceLines;
                    Stream<QuotationLine> routeLines = routes.findByActiveTrueOrderByNameAsc().stream()
                            .map(r -> toRouteView(r, c.getDescription()));
                    return Stream.concat(routeLines, serviceLines);
                })
                .toList();
    }

    @PutMapping("/{serviceId}")
    public QuotationLine update(@PathVariable Long serviceId,
                                @Valid @RequestBody QuotationUpdateRequest req,
                                Authentication auth) {
        String username = auth == null ? "system" : auth.getName();
        log.info("Quotation update requested by {}: service {} -> 1-container {}, additional {}.",
                username, serviceId, req.rateForOneContainer(), req.additionalPerContainer());
        return toView(quotation.update(serviceId, req.rateForOneContainer(),
                req.additionalPerContainer(), username));
    }

    private static QuotationLine toView(QuotationService.Line l) {
        return new QuotationLine(l.serviceId(), null, l.categoryCode(), l.categoryName(), l.name(),
                com.ase.billing.domain.enums.CalculationType.valueOf(l.calculationType()),
                l.scalesWithContainers(), l.rateForOneContainer(), l.additionalPerContainer(),
                l.effectiveFrom());
    }

    private static QuotationLine toRouteView(TransportRoute r, String categoryName) {
        return new QuotationLine(null, r.getId(), "T", categoryName, r.getName(),
                com.ase.billing.domain.enums.CalculationType.PER_TEU, true,
                r.getRatePerContainer(), r.getRatePerContainer(), null);
    }
}


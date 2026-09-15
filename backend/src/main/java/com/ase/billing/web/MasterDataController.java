package com.ase.billing.web;

import com.ase.billing.domain.Consignee;
import com.ase.billing.domain.ServiceItem;
import com.ase.billing.exception.NotFoundException;
import com.ase.billing.repo.*;
import com.ase.billing.service.RateResolver;
import com.ase.billing.web.dto.Dtos.*;
import jakarta.validation.Valid;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/**
 * Read-only master data for the dropdowns on the invoice screens.
 *
 * The class is transactional because open-in-view is disabled: reading
 * ServiceItem.category outside a session throws LazyInitializationException, and
 * the screen then shows a 500 where a dropdown should be.
 */
@RestController
@Transactional(readOnly = true)
@RequestMapping("/api")
public class MasterDataController {

    private final CustomerRepository customers;
    private final ServiceCategoryRepository categories;
    private final ServiceItemRepository services;
    private final TransportRouteRepository routes;
    private final ConsigneeRepository consignees;
    private final RateResolver rateResolver;
    private final InvoiceMapper mapper;

    public MasterDataController(CustomerRepository customers, ServiceCategoryRepository categories,
                                ServiceItemRepository services, TransportRouteRepository routes,
                                ConsigneeRepository consignees,
                                RateResolver rateResolver, InvoiceMapper mapper) {
        this.customers = customers;
        this.categories = categories;
        this.services = services;
        this.routes = routes;
        this.consignees = consignees;
        this.rateResolver = rateResolver;
        this.mapper = mapper;
    }

    @GetMapping("/customers")
    public List<CustomerView> customers() {
        return customers.findByActiveTrueOrderByNameAsc().stream().map(mapper::toView).toList();
    }

    @GetMapping("/categories")
    public List<CategoryView> categories() {
        return categories.findByActiveTrueOrderBySortOrderAsc().stream().map(mapper::toView).toList();
    }

    /**
     * Services for a category, each carrying the rate that applies today for this
     * customer. The rate is a suggestion: the invoice stores whatever is billed.
     */
    @GetMapping("/services")
    public List<ServiceView> services(@RequestParam(required = false) String category,
                                      @RequestParam(required = false) Long customerId) {
        List<ServiceItem> found = (category == null || category.isBlank())
                ? services.findByActiveTrueOrderBySortOrderAsc()
                : services.findByCategoryCodeIgnoreCaseAndActiveTrueOrderBySortOrderAsc(category);

        var customer = customerId == null ? null : customers.findById(customerId).orElse(null);
        LocalDate today = LocalDate.now();

        return found.stream().map(s -> new ServiceView(
                s.getId(),
                s.getCategory().getCode(),
                s.getName(),
                s.getPrintTemplate(),
                s.getCalculationType(),
                s.getDefaultUnit(),
                customer == null ? null : rateResolver.rateOrZero(s, customer, today),
                s.isStandard(),
                s.getDefaultQuantity(),
                s.getBaseAmount()
        )).toList();
    }

    /**
     * The standard charge sheet for a category, in print order.
     *
     * Every CNF bill ASE issues carries the same fourteen charges. Returning them
     * as a ready-made list means a new bill starts complete and the operator
     * deletes what does not apply, rather than rebuilding the sheet each time.
     */
    @GetMapping("/services/standard")
    public List<ServiceView> standard(@RequestParam String category,
                                      @RequestParam Long customerId) {
        return services(category, customerId).stream().filter(ServiceView::standard).toList();
    }

    /**
     * Buyers for the sample bill's PSC statement. A fixed list, so the same name
     * is spelled the same way every month.
     */
    @GetMapping("/consignees")
    public List<ConsigneeView> consignees() {
        return consignees.findByActiveTrueOrderByNameAsc().stream()
                .map(c -> new ConsigneeView(c.getId(), c.getName(), c.getCountry()))
                .toList();
    }

    /**
     * Adds a buyer the list does not have yet.
     *
     * A fixed list would block a new buyer at exactly the moment the operator is
     * mid-bill, so a name can be added from the statement itself and is then
     * there for every later month. An existing name is returned rather than
     * duplicated, so two spellings of the same buyer cannot both end up stored.
     */
    @PostMapping("/consignees")
    @Transactional
    public ConsigneeView addConsignee(@Valid @RequestBody ConsigneeRequest req) {
        String name = req.name().trim();
        Consignee saved = consignees.findByNameIgnoreCase(name).orElseGet(() -> {
            Consignee c = new Consignee();
            c.setName(name);
            c.setCountry(req.country());
            return consignees.save(c);
        });
        return new ConsigneeView(saved.getId(), saved.getName(), saved.getCountry());
    }

    @GetMapping("/transport-routes")
    public List<RouteView> routes() {
        return routes.findByActiveTrueOrderByNameAsc().stream().map(mapper::toView).toList();
    }

    /**
     * Changes a route's standing price. Transport rates move — NMPA to Baikampady
     * was billed at 12,000 and is now 6,000 — so the price is editable rather than
     * fixed at whatever was seeded.
     *
     * Invoices already issued are untouched: each line stored the rate it was
     * billed at when it was created.
     */
    @PutMapping("/transport-routes/{id}/rate")
    @Transactional
    public RouteView updateRouteRate(@PathVariable Long id,
                                     @Valid @RequestBody RouteRateRequest req) {
        var route = routes.findById(id)
                .orElseThrow(() -> new NotFoundException("Route", id));
        route.setRatePerContainer(req.ratePerContainer());
        return mapper.toView(routes.save(route));
    }
}

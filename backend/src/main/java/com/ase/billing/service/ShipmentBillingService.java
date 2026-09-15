package com.ase.billing.service;

import com.ase.billing.domain.*;
import com.ase.billing.domain.enums.CalculationType;
import com.ase.billing.domain.enums.InvoiceStatus;
import com.ase.billing.exception.NotFoundException;
import com.ase.billing.exception.ValidationException;
import com.ase.billing.repo.*;
import com.ase.billing.web.dto.Dtos.BillPairRequest;
import com.ase.billing.web.dto.Dtos.ItemRequest;
import com.ase.billing.web.dto.Dtos.TransportLegRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * The primary entry path: one shipment produces a consecutive CNF + T invoice pair.
 * Every shipment in both supplied PDFs is billed this way (CNF/439 + T/440,
 * CNF/441 + T/442, and so on), which is why the running number is shared across
 * categories rather than kept per category.
 *
 * Entering the shipment header once and generating both bills is what makes this
 * faster than the manual process. Duplicating an old invoice is the fallback.
 *
 * Most shipments need both bills, but not every one does — a shipment that is
 * only being cleared (no transport arranged yet) or only being transported
 * (CNF already billed separately) needs just one. {@link BillPairRequest#billMode()}
 * selects which of the pair actually gets created; the other is simply skipped,
 * not created-then-discarded.
 */
@Service
public class ShipmentBillingService {

    private static final Logger log = LoggerFactory.getLogger(ShipmentBillingService.class);

    private final ShipmentRepository shipments;
    private final InvoiceRepository invoices;
    private final CustomerRepository customers;
    private final ServiceCategoryRepository categories;
    private final ServiceItemRepository services;
    private final TransportRouteRepository routes;
    private final CompanySettingsRepository company;
    private final InvoiceNumberService numbers;
    private final InvoiceCalculationService calculator;
    private final RateUpdateService rateUpdates;

    public ShipmentBillingService(ShipmentRepository shipments, InvoiceRepository invoices,
                                  CustomerRepository customers, ServiceCategoryRepository categories,
                                  ServiceItemRepository services, TransportRouteRepository routes,
                                  CompanySettingsRepository company, InvoiceNumberService numbers,
                                  InvoiceCalculationService calculator, RateUpdateService rateUpdates) {
        this.shipments = shipments;
        this.invoices = invoices;
        this.customers = customers;
        this.categories = categories;
        this.routes = routes;
        this.services = services;
        this.company = company;
        this.numbers = numbers;
        this.calculator = calculator;
        this.rateUpdates = rateUpdates;
    }

    /**
     * Creates the CNF invoice, the T invoice, or both, with consecutive running
     * numbers when both are made.
     */
    @Transactional
    public List<Invoice> createPair(BillPairRequest req, String username) {
        Customer customer = customers.findById(req.customerId())
                .orElseThrow(() -> new NotFoundException("Customer", req.customerId()));

        BillMode mode = BillMode.of(req.billMode());

        Shipment shipment = new Shipment();
        shipment.setCustomer(customer);
        shipment.setHcInvoiceNumber(req.shipment().hcInvoiceNumber());
        shipment.setIcoMarkFull(req.shipment().icoMarkFull());
        shipment.setContainerCount(req.shipment().containerCount());
        shipment.setContainerSize(req.shipment().containerSize() == null
                ? "20" : req.shipment().containerSize());
        shipment.setDestinationCountry(req.shipment().destinationCountry());
        shipment.setNotes(req.shipment().notes());
        shipment.deriveFields();
        Shipment saved = shipments.save(shipment);

        if (mode.includesCnf() && (req.cnfItems() == null || req.cnfItems().isEmpty())) {
            throw new ValidationException("The CNF bill needs at least one charge.");
        }
        if (mode.includesTransport() && (req.legs() == null || req.legs().isEmpty())) {
            throw new ValidationException("The transport bill needs at least one route.");
        }

        if (mode.includesTransport()) {
            int totalLegContainers = req.legs().stream().mapToInt(TransportLegRequest::containers).sum();
            if (totalLegContainers > saved.getContainerCount()) {
                throw new ValidationException(
                        "The transport legs cover %d containers but the shipment has %d."
                                .formatted(totalLegContainers, saved.getContainerCount()));
            }
        }

        // An explicit starting number reissues one freed by a deletion, or fills a
        // gap. The CNF bill takes it and the T bill (when made) takes the one
        // straight after, keeping the pair consecutive. Only made when both
        // bills are actually created — a single-bill run just needs one number,
        // which is simpler to pass as the starting number regardless.
        Integer cnfNumber = req.startingRunningNumber();
        Integer transportNumber = (cnfNumber != null && mode.includesCnf())
                ? cnfNumber + 1
                : cnfNumber;

        List<Invoice> created = new ArrayList<>();
        if (mode.includesCnf()) {
            created.add(createCnf(saved, customer, req, username, cnfNumber));
        }
        if (mode.includesTransport()) {
            created.add(createTransport(saved, customer, req, username, transportNumber));
        }
        log.info("Bill a shipment ({}) by {}: customer {}, HC {}, {} container(s), created {}.",
                mode, username, customer.getId(), saved.getHcInvoiceNumber(),
                saved.getContainerCount(),
                created.stream().map(Invoice::getInvoiceNumber).toList());
        return created;
    }

    private Invoice createCnf(Shipment shipment, Customer customer,
                              BillPairRequest req, String username, Integer explicitNumber) {
        Invoice inv = newInvoice(shipment, customer, categoryByCode("CNF"),
                                 req.invoiceDate(), username, explicitNumber);

        for (ItemRequest line : req.cnfItems()) {
            InvoiceItem item = new InvoiceItem();
            item.setPrintedDescription(line.printedDescription());
            item.setCalculationType(line.calculationType());
            item.setQuantity(line.quantity());
            item.setUnit(line.unit());
            item.setRate(line.rate());
            item.setBaseAmount(line.baseAmount() == null ? BigDecimal.ZERO : line.baseAmount());
            item.setDays(line.days());
            item.setNotes(line.notes());
            item.setTaxable(line.taxable() == null || line.taxable());
            item.setAmount(line.amount() == null ? BigDecimal.ZERO : line.amount());
            item.setTeu(shipment.getTeu());
            item.setUpdateMasterRate(Boolean.TRUE.equals(line.updateMasterRate()));

            if (line.serviceId() != null) {
                item.setService(services.findById(line.serviceId())
                        .orElseThrow(() -> new NotFoundException("Service", line.serviceId())));
            }
            if (line.routeId() != null) {
                item.setRoute(routes.findById(line.routeId())
                        .orElseThrow(() -> new NotFoundException("Route", line.routeId())));
            }
            if (line.subLines() != null) {
                line.subLines().stream().filter(s -> s != null && !s.isBlank())
                    .forEach(item::addSubLine);
            }
            inv.addItem(item);
        }

        calculator.recalculate(inv, customer.getGstTreatment());
        Invoice saved = invoices.save(inv);
        // "Keep for next time" on a CNF charge: the same mechanism the standalone
        // invoice editor uses, applied here too — this path used to skip it
        // entirely, so a rate typed on a shipment bill never reached the master.
        int rateChanges = rateUpdates.applyFrom(saved, username);
        if (rateChanges > 0) {
            log.info("CNF {}: {} service rate(s) carried to master by {}.",
                    saved.getInvoiceNumber(), rateChanges, username);
        }
        return hydrate(saved);
    }

    private Invoice createTransport(Shipment shipment, Customer customer,
                                    BillPairRequest req, String username, Integer explicitNumber) {
        Invoice inv = newInvoice(shipment, customer, categoryByCode("T"),
                                 req.invoiceDate(), username, explicitNumber);

        boolean multiLeg = req.legs().size() > 1;

        for (TransportLegRequest leg : req.legs()) {
            TransportRoute route = routes.findById(leg.routeId())
                    .orElseThrow(() -> new NotFoundException("Route", leg.routeId()));

            InvoiceItem item = new InvoiceItem();
            item.setRoute(route);
            item.setCalculationType(CalculationType.SIMPLE);
            item.setQuantity(BigDecimal.valueOf(leg.containers()));
            item.setUnit("container");
            // The rate as billed, copied now. A later change to the route master
            // must not alter this invoice. A rate typed on the leg wins over the
            // master, and is written back to the master when asked.
            BigDecimal legRate = leg.rate() != null ? leg.rate() : route.getRatePerContainer();
            item.setRate(legRate);
            if (Boolean.TRUE.equals(leg.updateMasterRate())
                    && legRate.compareTo(route.getRatePerContainer()) != 0) {
                log.info("Transport leg on {}: route {} rate carried to master by {} ({} -> {}).",
                        shipment.getHcInvoiceNumber(), route.getId(), username,
                        route.getRatePerContainer(), legRate);
                route.setRatePerContainer(legRate);
                routes.save(route);
            }
            item.setPrintedDescription(route.getPrintTemplate());
            item.setTeu(shipment.getTeu());

            // Bill T/476 annotates each leg with its own container split.
            if (multiLeg) {
                item.addSubLine(leg.containers() + "x" + shipment.getContainerSize());
            }
            inv.addItem(item);
        }

        BigDecimal hassan = req.hassanRatePerTeu();
        if (hassan != null && hassan.signum() > 0) {
            // Only some of a shipment's containers may go via Hassan — 4 of 7, say —
            // so the surcharge is priced on that count, not on the whole shipment.
            int viaHassan = req.hassanContainers() == null
                    ? shipment.getContainerCount()
                    : req.hassanContainers();
            if (viaHassan > shipment.getContainerCount()) {
                throw new ValidationException(
                        "%d containers are marked as going via Hassan, but the shipment only has %d."
                                .formatted(viaHassan, shipment.getContainerCount()));
            }

            InvoiceItem surcharge = new InvoiceItem();
            surcharge.setCalculationType(CalculationType.PER_TEU);
            surcharge.setTeu(Shipment.teuFor(viaHassan, shipment.getContainerSize()));
            surcharge.setRate(hassan);
            surcharge.setUnit("TEU");
            surcharge.setPrintedDescription("Addnl. Charges for Transportation via Hassan");
            surcharge.addSubLine(viaHassan + "x" + shipment.getContainerSize());
            inv.addItem(surcharge);
        }

        calculator.recalculate(inv, customer.getGstTreatment());
        return hydrate(invoices.save(inv));
    }

    /**
     * Lets the TEU move while the shipment's bill(s) are still drafts — a
     * container count typed wrong at entry, corrected before anything is
     * issued. Refused once any attached invoice has been finalised or
     * cancelled: an issued tax document's figures are frozen, and this would
     * silently change them underneath it.
     *
     * Every PER_TEU / PER_TEU_PER_DAY line on every attached draft is re-priced
     * against the new TEU and the invoice totals recomputed, so the change
     * actually reaches the bill instead of only updating the shipment header.
     */
    @Transactional
    public List<Invoice> updateContainers(Long shipmentId, Integer containerCount,
                                          String containerSize, String username) {
        Shipment shipment = shipments.findById(shipmentId)
                .orElseThrow(() -> new NotFoundException("Shipment", shipmentId));

        List<Invoice> attached = invoices.findByShipmentIdOrderByRunningNumberAsc(shipmentId);
        Invoice notDraft = attached.stream()
                .filter(i -> i.getStatus() != InvoiceStatus.DRAFT)
                .findFirst().orElse(null);
        if (notDraft != null) {
            throw new ValidationException(
                    "The container count can't change: %s against this shipment is already %s."
                            .formatted(notDraft.getInvoiceNumber(), notDraft.getStatus().name().toLowerCase()));
        }

        BigDecimal before = shipment.getTeu();
        shipment.setContainerCount(containerCount);
        if (containerSize != null) {
            shipment.setContainerSize(containerSize);
        }
        shipment.deriveFields();
        Shipment saved = shipments.save(shipment);

        List<Invoice> updated = new ArrayList<>();
        for (Invoice inv : attached) {
            boolean touched = false;
            for (InvoiceItem item : inv.getItems()) {
                if (item.getCalculationType() == CalculationType.PER_TEU
                        || item.getCalculationType() == CalculationType.PER_TEU_PER_DAY) {
                    item.setTeu(saved.getTeu());
                    touched = true;
                }
            }
            if (touched) {
                calculator.recalculate(inv, inv.getCustomer().getGstTreatment());
                invoices.save(inv);
            }
            updated.add(inv);
        }
        log.info("Shipment {} TEU changed {} -> {} by {}; re-priced {} draft invoice(s): {}.",
                shipmentId, before, saved.getTeu(), username, updated.size(),
                updated.stream().map(Invoice::getInvoiceNumber).toList());
        return updated.stream().map(this::hydrate).toList();
    }

    /**
     * Initialises the lazy sides before the invoice leaves the transaction.
     * open-in-view is off, so without this the pair commits and the response
     * then fails with LazyInitializationException.
     */
    private Invoice hydrate(Invoice inv) {
        inv.getCustomer().getName();
        inv.getCategory().getCode();
        inv.getItems().forEach(i -> {
            i.getSubLines().size();
            if (i.getRoute() != null) i.getRoute().getId();
            if (i.getService() != null) i.getService().getId();
        });
        if (inv.getShipment() != null) inv.getShipment().getHcInvoiceNumber();
        return inv;
    }

    private Invoice newInvoice(Shipment shipment, Customer customer, ServiceCategory category,
                               LocalDate date, String username, Integer explicitNumber) {
        Invoice inv = new Invoice();
        inv.setCustomer(customer);
        inv.setCategory(category);
        inv.setShipment(shipment);
        inv.setInvoiceDate(date);
        inv.setStatus(InvoiceStatus.DRAFT);
        inv.setCreatedBy(username);
        inv.setUpdatedBy(username);
        inv.setHsnCode(company.findAll().stream().findFirst()
                .map(CompanySettings::getDefaultHsnCode).orElse("996713"));

        if (explicitNumber != null) {
            String fy = FinancialYear.of(date);
            if (invoices.existsByCustomerIdAndFinancialYearAndRunningNumber(
                    customer.getId(), fy, explicitNumber)) {
                throw new ValidationException(
                        "Number %d is already used for %s in %s."
                                .formatted(explicitNumber, customer.getCode(), fy));
            }
            inv.setInvoiceNumber("ASE/%s/%s/%d/%s".formatted(
                    customer.getCode(), category.getCode(), explicitNumber, fy));
            inv.setRunningNumber(explicitNumber);
            inv.setFinancialYear(fy);
            numbers.reserveHistorical(customer, fy, explicitNumber);
        } else {
            InvoiceNumberService.Allocated allocated = numbers.allocate(customer, category, date);
            inv.setInvoiceNumber(allocated.invoiceNumber());
            inv.setRunningNumber(allocated.runningNumber());
            inv.setFinancialYear(allocated.financialYear());
        }
        return inv;
    }

    private ServiceCategory categoryByCode(String code) {
        return categories.findByCodeIgnoreCase(code)
                .orElseThrow(() -> new NotFoundException("Category " + code + " is not configured."));
    }

    /** Which half (or both) of the pair to actually create. */
    private enum BillMode {
        BOTH, CNF_ONLY, T_ONLY;

        boolean includesCnf() { return this != T_ONLY; }
        boolean includesTransport() { return this != CNF_ONLY; }

        static BillMode of(String raw) {
            if (raw == null || raw.isBlank()) return BOTH;
            return switch (raw.trim().toUpperCase()) {
                case "CNF", "CNF_ONLY" -> CNF_ONLY;
                case "T", "T_ONLY", "TRANSPORT", "TRANSPORT_ONLY" -> T_ONLY;
                case "BOTH" -> BOTH;
                default -> throw new ValidationException(
                        "\"%s\" is not a bill mode. Use BOTH, CNF or T.".formatted(raw));
            };
        }
    }
}

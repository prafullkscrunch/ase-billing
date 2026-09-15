package com.ase.billing.service;

import com.ase.billing.domain.*;
import com.ase.billing.domain.enums.InvoiceStatus;
import com.ase.billing.exception.NotFoundException;
import com.ase.billing.exception.ValidationException;
import com.ase.billing.repo.*;
import com.ase.billing.web.dto.Dtos.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Every write path for an invoice.
 *
 * Two rules hold throughout and are the reason this layer exists:
 *   1. Totals are recomputed here on every save. A figure sent by the client is
 *      never stored.
 *   2. Only a DRAFT can be changed. A finalised invoice is an issued tax document.
 */
@Service
public class InvoiceService {

    private static final Logger log = LoggerFactory.getLogger(InvoiceService.class);

    private final InvoiceRepository invoices;
    private final CustomerRepository customers;
    private final ServiceCategoryRepository categories;
    private final ServiceItemRepository services;
    private final TransportRouteRepository routes;
    private final ShipmentRepository shipments;
    private final CompanySettingsRepository company;
    private final InvoiceNumberService numbers;
    private final InvoiceCalculationService calculator;
    private final InvoiceValidator validator;
    private final RateUpdateService rateUpdates;

    public InvoiceService(InvoiceRepository invoices, CustomerRepository customers,
                          ServiceCategoryRepository categories, ServiceItemRepository services,
                          TransportRouteRepository routes, ShipmentRepository shipments,
                          CompanySettingsRepository company, InvoiceNumberService numbers,
                          InvoiceCalculationService calculator, InvoiceValidator validator,
                          RateUpdateService rateUpdates) {
        this.invoices = invoices;
        this.customers = customers;
        this.categories = categories;
        this.services = services;
        this.routes = routes;
        this.shipments = shipments;
        this.company = company;
        this.numbers = numbers;
        this.calculator = calculator;
        this.validator = validator;
        this.rateUpdates = rateUpdates;
    }

    // ---------- reads ---------------------------------------------------------

    @Transactional(readOnly = true)
    public Invoice load(Long id) {
        Invoice inv = invoices.findById(id)
                .orElseThrow(() -> new NotFoundException("Invoice", id));
        return hydrate(inv);
    }

    /**
     * Initialises every lazy association before the invoice leaves the transaction.
     *
     * open-in-view is off, so an Invoice returned from a service method is detached
     * by the time the mapper reads it. Every write path below ends with this call:
     * without it the database change commits and then the response throws
     * LazyInitializationException, which looks to the user like the save failed
     * when in fact it succeeded.
     */
    private Invoice hydrate(Invoice inv) {
        inv.getCustomer().getName();
        inv.getCategory().getCode();
        inv.getItems().forEach(i -> {
            i.getSubLines().size();
            if (i.getService() != null) i.getService().getId();
            if (i.getRoute() != null) i.getRoute().getId();
        });
        if (inv.getAnnexure() != null) inv.getAnnexure().getRows().size();
        if (inv.getShipment() != null) inv.getShipment().getHcInvoiceNumber();
        return inv;
    }

    @Transactional(readOnly = true)
    public List<Invoice> search(Long customerId, LocalDate from, LocalDate to,
                                String categoryCode, InvoiceStatus status, String text) {
        return invoices.search(customerId, from, to, categoryCode, status,
                text == null || text.isBlank() ? null : text.trim().toLowerCase());
    }

    // ---------- create and update --------------------------------------------

    @Transactional
    public Invoice createDraft(InvoiceRequest req, String username) {
        Customer customer = customers.findById(req.customerId())
                .orElseThrow(() -> new NotFoundException("Customer", req.customerId()));
        ServiceCategory category = categoryByCode(req.categoryCode());

        Invoice inv = new Invoice();
        inv.setCustomer(customer);
        inv.setCategory(category);
        inv.setInvoiceDate(req.invoiceDate());
        inv.setCreatedBy(username);
        inv.setUpdatedBy(username);

        // An explicit number reissues one that was freed by a deletion, or fills a
        // gap left in the middle of the run. Without it the sequence decides.
        if (req.runningNumber() != null) {
            String fy = FinancialYear.of(req.invoiceDate());
            if (invoices.existsByCustomerIdAndFinancialYearAndRunningNumber(
                    customer.getId(), fy, req.runningNumber())) {
                throw new ValidationException(
                        "Number %d is already used for %s in %s."
                                .formatted(req.runningNumber(), customer.getCode(), fy));
            }
            inv.setInvoiceNumber("ASE/%s/%s/%d/%s".formatted(
                    customer.getCode(), category.getCode(), req.runningNumber(), fy));
            inv.setRunningNumber(req.runningNumber());
            inv.setFinancialYear(fy);
            numbers.reserveHistorical(customer, fy, req.runningNumber());
        } else {
            InvoiceNumberService.Allocated allocated =
                    numbers.allocate(customer, category, req.invoiceDate());
            inv.setInvoiceNumber(allocated.invoiceNumber());
            inv.setRunningNumber(allocated.runningNumber());
            inv.setFinancialYear(allocated.financialYear());
        }

        applyRequest(inv, req);
        Invoice saved = invoices.save(inv);
        int rateChanges = rateUpdates.applyFrom(saved, username);
        log.info("Created draft {} for customer {} by {} ({} rate(s) carried to master).",
                saved.getInvoiceNumber(), customer.getId(), username, rateChanges);
        return hydrate(saved);
    }

    @Transactional
    public Invoice updateDraft(Long id, InvoiceRequest req, String username) {
        Invoice inv = invoices.findById(id)
                .orElseThrow(() -> new NotFoundException("Invoice", id));
        requireDraft(inv);

        // The number, date-derived financial year and customer are settled at creation.
        // Changing the date after allocation would let the financial year drift away
        // from the number that was already issued.
        if (!inv.getInvoiceDate().equals(req.invoiceDate())) {
            String newFy = FinancialYear.of(req.invoiceDate());
            if (!newFy.equals(inv.getFinancialYear())) {
                throw new ValidationException(
                        "Moving this invoice to %s would change its financial year to %s, but the "
                        .formatted(req.invoiceDate(), newFy)
                        + "number %s was issued under %s. Cancel it and create a new one instead."
                          .formatted(inv.getInvoiceNumber(), inv.getFinancialYear()));
            }
            inv.setInvoiceDate(req.invoiceDate());
        }

        inv.setUpdatedBy(username);
        applyRequest(inv, req);
        Invoice saved = invoices.save(inv);
        int rateChanges = rateUpdates.applyFrom(saved, username);
        log.info("Updated draft {} by {} ({} rate(s) carried to master).",
                saved.getInvoiceNumber(), username, rateChanges);
        return hydrate(saved);
    }

    /** Shared between create and update: shipment, header fields, items, annexure, totals. */
    private void applyRequest(Invoice inv, InvoiceRequest req) {
        inv.setHsnCode(resolveHsn(req.hsnCode()));
        inv.setHeaderNote(req.headerNote());
        inv.setPostTaxAdjustmentLabel(req.postTaxAdjustmentLabel());
        inv.setPostTaxAdjustmentAmount(
                req.postTaxAdjustmentAmount() == null ? BigDecimal.ZERO : req.postTaxAdjustmentAmount());

        inv.setShipment(resolveShipment(req, inv.getCustomer()));

        inv.getItems().clear();
        for (ItemRequest ir : req.items()) {
            inv.addItem(buildItem(ir, inv));
        }

        applyAnnexure(inv, req.annexure());
        applyAnnexureQuantity(inv);

        calculator.recalculate(inv, inv.getCustomer().getGstTreatment());
    }

    private InvoiceItem buildItem(ItemRequest ir, Invoice inv) {
        InvoiceItem item = new InvoiceItem();
        item.setPrintedDescription(ir.printedDescription());
        item.setCalculationType(ir.calculationType());
        item.setQuantity(ir.quantity());
        item.setUnit(ir.unit());
        item.setRate(ir.rate());
        item.setBaseAmount(ir.baseAmount() == null ? BigDecimal.ZERO : ir.baseAmount());
        item.setDays(ir.days());
        item.setNotes(ir.notes());
        item.setTaxable(ir.taxable() == null || ir.taxable());
        item.setAmount(ir.amount() == null ? BigDecimal.ZERO : ir.amount());
        item.setUpdateMasterRate(Boolean.TRUE.equals(ir.updateMasterRate()));

        // A per-TEU line takes the shipment's TEU; the client does not get to set it.
        switch (ir.calculationType()) {
            case PER_TEU, PER_TEU_PER_DAY -> {
                if (inv.getShipment() == null) {
                    throw new ValidationException(
                            "\"%s\" is charged per TEU, so the invoice needs shipment details."
                                    .formatted(ir.printedDescription()));
                }
                item.setTeu(inv.getShipment().getTeu());
            }
            default -> item.setTeu(ir.teu());
        }

        if (ir.serviceId() != null) {
            item.setService(services.findById(ir.serviceId())
                    .orElseThrow(() -> new NotFoundException("Service", ir.serviceId())));
        }
        if (ir.routeId() != null) {
            item.setRoute(routes.findById(ir.routeId())
                    .orElseThrow(() -> new NotFoundException("Route", ir.routeId())));
        }
        if (ir.subLines() != null) {
            ir.subLines().stream()
              .filter(s -> s != null && !s.isBlank())
              .forEach(item::addSubLine);
        }
        return item;
    }

    private Shipment resolveShipment(InvoiceRequest req, Customer customer) {
        if (req.shipment() != null) {
            Shipment s = new Shipment();
            s.setCustomer(customer);
            s.setHcInvoiceNumber(req.shipment().hcInvoiceNumber());
            s.setIcoMarkFull(req.shipment().icoMarkFull());
            s.setContainerCount(req.shipment().containerCount());
            s.setContainerSize(req.shipment().containerSize() == null
                    ? "20" : req.shipment().containerSize());
            s.setDestinationCountry(req.shipment().destinationCountry());
            s.setNotes(req.shipment().notes());
            s.deriveFields();
            return shipments.save(s);
        }
        if (req.shipmentId() != null) {
            return shipments.findById(req.shipmentId())
                    .orElseThrow(() -> new NotFoundException("Shipment", req.shipmentId()));
        }
        return null;
    }

    /**
     * The PSC statement's row count is the number of sets billed, so every
     * per-set line takes its quantity from it. Seventeen consignee rows bill as
     * seventeen sets; the statement and the bill cannot drift apart.
     */
    private void applyAnnexureQuantity(Invoice inv) {
        InvoiceAnnexure a = inv.getAnnexure();
        if (a == null || !a.isDrivesQuantity() || a.getRows().isEmpty()) return;

        BigDecimal sets = BigDecimal.valueOf(a.getRows().size());
        for (InvoiceItem item : inv.getItems()) {
            if (item.getCalculationType() == com.ase.billing.domain.enums.CalculationType.PER_SET) {
                item.setQuantity(sets);
            }
        }
    }

    private void applyAnnexure(Invoice inv, AnnexureRequest req) {
        if (req == null) {
            inv.setAnnexure(null);
            return;
        }
        InvoiceAnnexure a = inv.getAnnexure() == null ? new InvoiceAnnexure() : inv.getAnnexure();
        a.setInvoice(inv);
        a.setTitle(req.title());
        if (req.col1Header() != null) a.setCol1Header(req.col1Header());
        if (req.col2Header() != null) a.setCol2Header(req.col2Header());
        a.setFooterText(req.footerText());
        a.setDrivesQuantity(req.drivesQuantity() == null || req.drivesQuantity());
        a.getRows().clear();
        int n = 1;
        if (req.rows() != null) {
            for (AnnexureRowView r : req.rows()) {
                InvoiceAnnexureRow row = new InvoiceAnnexureRow();
                row.setAnnexure(a);
                row.setSequenceNo(n++);
                row.setCol1(r.col1());
                row.setCol2(r.col2());
                a.getRows().add(row);
            }
        }
        inv.setAnnexure(a);
    }

    // ---------- state transitions --------------------------------------------

    @Transactional
    public Invoice finalize(Long id, String username) {
        return hydrate(finalizeOne(id, username));
    }

    /** The finalise itself, without hydration, so bulk can call it in a loop. */
    private Invoice finalizeOne(Long id, String username) {
        Invoice inv = invoices.findById(id)
                .orElseThrow(() -> new NotFoundException("Invoice", id));
        requireDraft(inv);

        // Recompute before checking, so the validator tests stored values that
        // were produced by the engine rather than whatever was last written.
        calculator.recalculate(inv, inv.getCustomer().getGstTreatment());
        validator.validateForFinalize(inv, inv.getCustomer().getGstTreatment());

        inv.setStatus(InvoiceStatus.FINALIZED);
        inv.setFinalizedAt(LocalDateTime.now());
        inv.setFinalizedBy(username);
        inv.setUpdatedBy(username);
        Invoice saved = invoices.save(inv);
        log.info("Finalised {} by {}. Taxable {}, grand total {}.",
                saved.getInvoiceNumber(), username, saved.getTaxableAmount(), saved.getGrandTotal());
        return saved;
    }

    /**
     * Finalises a set in one go, so a batch reviewed on paper can be approved
     * together. One invoice failing its checks does not stop the others: the
     * caller gets told exactly which ones were refused and why.
     */
    @Transactional
    public BulkResult finalizeAll(List<Long> ids, String username) {
        List<String> approved = new java.util.ArrayList<>();
        List<BulkFailure> refused = new java.util.ArrayList<>();

        for (Long id : ids) {
            try {
                Invoice done = finalizeOne(id, username);
                approved.add(done.getInvoiceNumber());
            } catch (ValidationException e) {
                log.warn("Bulk finalise refused invoice {}: {}", id, e.getMessage());
                refused.add(new BulkFailure(id, numberOf(id), e.getMessage(), e.getProblems()));
            } catch (NotFoundException e) {
                log.warn("Bulk finalise could not find invoice {}: {}", id, e.getMessage());
                refused.add(new BulkFailure(id, null, e.getMessage(), List.of()));
            }
        }
        log.info("Bulk finalise by {}: {} approved, {} refused.", username, approved.size(), refused.size());
        return new BulkResult(approved, refused);
    }

    public record BulkFailure(Long id, String invoiceNumber, String message, List<String> problems) {}
    public record BulkResult(List<String> approved, List<BulkFailure> refused) {}

    private String numberOf(Long id) {
        return invoices.findById(id).map(Invoice::getInvoiceNumber).orElse(null);
    }

    @Transactional
    public Invoice cancel(Long id, String username) {
        Invoice inv = invoices.findById(id)
                .orElseThrow(() -> new NotFoundException("Invoice", id));
        if (inv.getStatus() == InvoiceStatus.CANCELLED) {
            return hydrate(inv);
        }
        inv.setStatus(InvoiceStatus.CANCELLED);
        inv.setUpdatedBy(username);
        // The number is burnt. Invoice numbers are never recycled.
        return hydrate(invoices.save(inv));
    }

    /**
     * Copies a bill into a new draft with a fresh number and today's date.
     * Most of ASE's bills are near-repeats of an earlier one.
     */
    @Transactional
    public Invoice duplicate(Long id, LocalDate newDate, String username) {
        Invoice source = load(id);
        LocalDate date = newDate == null ? LocalDate.now() : newDate;

        Invoice copy = new Invoice();
        copy.setCustomer(source.getCustomer());
        copy.setCategory(source.getCategory());
        copy.setInvoiceDate(date);
        copy.setShipment(source.getShipment());
        copy.setHsnCode(source.getHsnCode());
        copy.setHeaderNote(source.getHeaderNote());
        copy.setPostTaxAdjustmentLabel(source.getPostTaxAdjustmentLabel());
        copy.setPostTaxAdjustmentAmount(source.getPostTaxAdjustmentAmount());
        copy.setCreatedBy(username);
        copy.setUpdatedBy(username);

        InvoiceNumberService.Allocated allocated =
                numbers.allocate(source.getCustomer(), source.getCategory(), date);
        copy.setInvoiceNumber(allocated.invoiceNumber());
        copy.setRunningNumber(allocated.runningNumber());
        copy.setFinancialYear(allocated.financialYear());

        for (InvoiceItem src : source.getItems()) {
            InvoiceItem item = new InvoiceItem();
            item.setService(src.getService());
            item.setRoute(src.getRoute());
            item.setPrintedDescription(src.getPrintedDescription());
            item.setQuantity(src.getQuantity());
            item.setUnit(src.getUnit());
            item.setRate(src.getRate());        // the rate as billed, not today's master rate
            item.setBaseAmount(src.getBaseAmount());
            item.setDays(src.getDays());
            item.setTeu(src.getTeu());
            item.setCalculationType(src.getCalculationType());
            item.setAmount(src.getAmount());
            item.setTaxable(src.isTaxable());
            item.setNotes(src.getNotes());
            src.getSubLines().forEach(sl -> item.addSubLine(sl.getText()));
            copy.addItem(item);
        }

        calculator.recalculate(copy, copy.getCustomer().getGstTreatment());
        return hydrate(invoices.save(copy));
    }

    // ---------- helpers -------------------------------------------------------

    private void requireDraft(Invoice inv) {
        if (inv.getStatus() != InvoiceStatus.DRAFT) {
            throw new ValidationException(
                    "Invoice %s is %s and can no longer be changed."
                            .formatted(inv.getInvoiceNumber(), inv.getStatus().name().toLowerCase()));
        }
    }

    private ServiceCategory categoryByCode(String code) {
        return categories.findByCodeIgnoreCase(code)
                .orElseThrow(() -> new NotFoundException("Category " + code + " is not configured."));
    }

    private String resolveHsn(String requested) {
        if (requested != null && !requested.isBlank()) return requested;
        return company.findAll().stream().findFirst()
                .map(CompanySettings::getDefaultHsnCode)
                .orElse("996713");
    }
}

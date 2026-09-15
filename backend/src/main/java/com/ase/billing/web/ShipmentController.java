package com.ase.billing.web;

import com.ase.billing.repo.ShipmentRepository;
import com.ase.billing.service.ShipmentBillingService;
import com.ase.billing.web.dto.Dtos.*;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/shipments")
public class ShipmentController {

    private static final Logger log = LoggerFactory.getLogger(ShipmentController.class);

    private final ShipmentBillingService billing;
    private final ShipmentRepository shipments;
    private final InvoiceMapper mapper;

    public ShipmentController(ShipmentBillingService billing, ShipmentRepository shipments,
                              InvoiceMapper mapper) {
        this.billing = billing;
        this.shipments = shipments;
        this.mapper = mapper;
    }

    /**
     * The customer's most recent shipment — lets the "Bill a shipment" screen
     * suggest the next HC invoice / ICO mark number (typically "+1" on
     * whatever was last used) instead of the operator retyping it. A
     * suggestion only: the fields it fills stay freely editable, since the
     * pattern breaks whenever the customer's own numbering resets.
     */
    @GetMapping("/last")
    public LastShipmentView last(@RequestParam Long customerId) {
        return shipments.findTopByCustomerIdOrderByIdDesc(customerId)
                .map(s -> new LastShipmentView(s.getHcInvoiceNumber(), s.getIcoMarkFull()))
                .orElse(new LastShipmentView(null, null));
    }

    /**
     * The main entry path. One shipment in, a CNF and/or T draft out, depending
     * on billMode. Every shipment in the historical bills is billed this way.
     */
    @PostMapping("/bill-pair")
    public List<InvoiceView> billPair(@Valid @RequestBody BillPairRequest req, Authentication auth) {
        String username = username(auth);
        log.info("Bill a shipment requested by {}: customer {}, mode {}, starting number {}.",
                username, req.customerId(), req.billMode(), req.startingRunningNumber());
        return billing.createPair(req, username)
                .stream().map(mapper::toView).toList();
    }

    /**
     * Corrects a shipment's container count/size while its bill(s) are still
     * drafts, and re-prices every PER_TEU line on them. Refused once any
     * attached invoice has been finalised or cancelled.
     */
    @PutMapping("/{id}/containers")
    public List<InvoiceView> updateContainers(@PathVariable Long id,
                                              @Valid @RequestBody ShipmentContainerRequest req,
                                              Authentication auth) {
        String username = username(auth);
        log.info("Shipment {} TEU change requested by {}: {}x{}.",
                id, username, req.containerCount(), req.containerSize());
        return billing.updateContainers(id, req.containerCount(), req.containerSize(), username)
                .stream().map(mapper::toView).toList();
    }

    private String username(Authentication auth) {
        return auth == null ? "system" : auth.getName();
    }
}

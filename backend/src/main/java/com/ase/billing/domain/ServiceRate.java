package com.ase.billing.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Rate history. A rate change closes the current row (effectiveTo) and inserts a
 * new one; rows are never updated in place. The invoice copies the rate it used
 * into invoice_items.rate, so changing a rate later cannot alter an old bill.
 */
@Entity
@Table(name = "service_rates")
@Getter
@Setter
public class ServiceRate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "service_id")
    private ServiceItem service;

    /** Null means the global rate. Set means a rate for this customer only. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id")
    private Customer customer;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal rate;

    @Column(name = "gst_rate", nullable = false, precision = 5, scale = 2)
    private BigDecimal gstRate = new BigDecimal("18.00");

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    /** MASTER when set in Settings, INVOICE when typed on a bill and kept. */
    @Column(nullable = false, length = 24)
    private String source = "MASTER";

    @Column(name = "set_from_invoice")
    private Long setFromInvoice;

    @Column(name = "set_by", length = 60)
    private String setBy;
}

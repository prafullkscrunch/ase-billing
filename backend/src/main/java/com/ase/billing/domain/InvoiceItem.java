package com.ase.billing.domain;

import com.ase.billing.domain.enums.CalculationType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "invoice_items")
@Getter
@Setter
public class InvoiceItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "invoice_id")
    private Invoice invoice;

    @Column(name = "sequence_no", nullable = false)
    private Integer sequenceNo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "service_id")
    private ServiceItem service;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "route_id")
    private TransportRoute route;

    /**
     * The line exactly as it prints. Prefilled from the service print_template with
     * {qty}/{rate}/{days}/{containers} substituted, then editable by the user.
     * The PDF prints this string; it does not rebuild it from quantity and rate.
     */
    @Column(name = "printed_description", nullable = false, length = 400)
    private String printedDescription;

    @Column(precision = 12, scale = 3)
    private BigDecimal quantity;

    @Column(length = 24)
    private String unit;

    /** The rate as billed, copied from the rate master at creation and never updated after. */
    @Column(precision = 14, scale = 2)
    private BigDecimal rate;

    /**
     * The fixed component as billed, copied from the service master at creation.
     * amount = base + (quantity or teu) x rate. Zero for the ordinary case where
     * the line is pure quantity x rate.
     */
    @Column(name = "base_amount", precision = 14, scale = 2, nullable = false)
    private BigDecimal baseAmount = BigDecimal.ZERO;

    private Integer days;

    @Column(precision = 6, scale = 2)
    private BigDecimal teu;

    @Enumerated(EnumType.STRING)
    @Column(name = "calculation_type", nullable = false, length = 24)
    private CalculationType calculationType = CalculationType.MANUAL;

    @Column(precision = 14, scale = 2, nullable = false)
    private BigDecimal amount = BigDecimal.ZERO;

    @Column(nullable = false)
    private boolean taxable = true;

    @Column(length = 255)
    private String notes;

    /**
     * Set by the request when the operator changed this line's rate and wants it
     * to apply to later bills too. Transient: the intent belongs to one save, and
     * what it produces is a new row in service_rates, not a column here.
     */
    @Transient
    private boolean updateMasterRate = false;

    /** Trailer and container numbers, container-split annotations. Printed, never priced. */
    @OneToMany(mappedBy = "item", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sequenceNo ASC")
    private List<InvoiceItemSubline> subLines = new ArrayList<>();

    public void addSubLine(String text) {
        InvoiceItemSubline sl = new InvoiceItemSubline();
        sl.setItem(this);
        sl.setSequenceNo(subLines.size() + 1);
        sl.setText(text);
        subLines.add(sl);
    }
}

package com.ase.billing.domain;

import com.ase.billing.domain.enums.CalculationType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/**
 * A canonical charge. Named ServiceItem rather than Service so it does not collide
 * with org.springframework.stereotype.Service on import.
 *
 * The master exists because the paper bills spell the same charge several ways
 * ('Certificate of Origin' / 'Certificate of origin' / 'Certificate origin').
 * Users pick from here; the printed line comes from printTemplate.
 */
@Entity
@Table(name = "services")
@Getter
@Setter
public class ServiceItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id")
    private ServiceCategory category;

    @Column(nullable = false, length = 160)
    private String name;

    /** e.g. 'VGM expenses {qty}@RS{rate}/TEU'. Placeholders: {qty} {rate} {days} {containers} */
    @Column(name = "print_template", length = 240)
    private String printTemplate;

    @Enumerated(EnumType.STRING)
    @Column(name = "calculation_type", nullable = false, length = 24)
    private CalculationType calculationType = CalculationType.MANUAL;

    @Column(name = "default_unit", length = 24)
    private String defaultUnit;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder = 0;

    /**
     * Part of the standard CNF charge sheet. Every CNF bill in ASE's Word
     * templates carries the same fourteen charges, so a new CNF bill starts with
     * them already filled in; any line can still be removed before finalising.
     */
    @Column(name = "is_standard", nullable = false)
    private boolean standard = false;

    @Column(name = "default_quantity", nullable = false, precision = 12, scale = 3)
    private java.math.BigDecimal defaultQuantity = java.math.BigDecimal.ONE;

    /**
     * A fixed amount added once, on top of quantity x rate. Some CNF charges
     * are not purely proportional to container count: ICO/Permit runs
     * 1000/1500/2000/.../4000 for 1/2/3/.../7 containers, which is 500 + 500 x n,
     * not 500 x n. Zero for every service where quantity x rate is the whole
     * story (the default, and the common case).
     */
    @Column(name = "base_amount", nullable = false, precision = 12, scale = 2)
    private java.math.BigDecimal baseAmount = java.math.BigDecimal.ZERO;

    @Column(nullable = false)
    private boolean active = true;
}

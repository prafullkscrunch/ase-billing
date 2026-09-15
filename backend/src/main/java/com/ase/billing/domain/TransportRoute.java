package com.ase.billing.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Transportation is priced per container, not per trip. Verified across every T
 * bill in both PDFs: Kushalnagar 27,000 x {1,2,3,4,6}; Somwarpet 29,750 x {1,2,3}.
 */
@Entity
@Table(name = "transport_routes")
@Getter
@Setter
public class TransportRoute {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 120)
    private String name;

    @Column(nullable = false, length = 80) private String origin;
    @Column(nullable = false, length = 80) private String destination;

    @Column(name = "print_template", nullable = false, length = 240)
    private String printTemplate;

    @Column(name = "rate_per_container", nullable = false, precision = 14, scale = 2)
    private BigDecimal ratePerContainer;

    @Column(nullable = false)
    private boolean active = true;
}

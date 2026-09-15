package com.ase.billing.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/**
 * A buyer named on the PSC statement attached to a sample (S) bill.
 *
 * A master list rather than free text, because the same handful of names recur
 * every month and typing them by hand is how "Group Sopex" ends up spelled three
 * ways across three statements.
 */
@Entity
@Table(name = "consignees")
@Getter
@Setter
public class Consignee {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 160)
    private String name;

    @Column(length = 80)
    private String country;

    @Column(nullable = false)
    private boolean active = true;
}

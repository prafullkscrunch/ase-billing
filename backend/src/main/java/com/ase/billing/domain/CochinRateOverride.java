package com.ase.billing.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * A Cochin-specific rate for one of the standard CNF charges, in place of
 * the general/global rate — Cochin-bound shipments are rare enough
 * (confirmed: max ~10/year) that this doesn't need the full close-old/
 * open-new history {@link com.ase.billing.service.RateUpdateService} gives
 * the Mangalore/global rate. A Cochin bill always uses today's row here;
 * there is no "what was the Cochin rate on this past date" question to
 * answer the way there is for the global rate.
 *
 * <p>Only services that genuinely differ for Cochin have a row here — a
 * service with no row here is billed at its normal global rate regardless
 * of the Cochin flag (this is why "Tally Wages", which has no Mangalore
 * equivalent at all, doesn't need one: it's simply excluded from a
 * Mangalore bill's standard sheet via {@code cochin_only}, and uses its own
 * ordinary global rate on a Cochin bill).
 */
@Entity
@Table(name = "cochin_rate_overrides")
@Getter
@Setter
public class CochinRateOverride {

    @Id
    @Column(name = "service_id")
    private Long serviceId;

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "service_id")
    private ServiceItem service;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal rate;

    @Column(name = "base_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal baseAmount = BigDecimal.ZERO;
}

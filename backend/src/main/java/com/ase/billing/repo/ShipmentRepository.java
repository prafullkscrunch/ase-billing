package com.ase.billing.repo;

import com.ase.billing.domain.Shipment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ShipmentRepository extends JpaRepository<Shipment, Long> {

    /** The most recently created shipment for a customer, so the operator's
     *  next HC invoice / ICO mark number can be suggested from it. */
    Optional<Shipment> findTopByCustomerIdOrderByIdDesc(Long customerId);

    /**
     * The earliest (i.e. "original") shipment billed against exactly this ICO
     * mark string — used by the "taken twice" CNF redo flow to find the
     * first-issued certificates for the same mark, so it can check what that
     * original CNF bill actually included (specifically, whether it had a
     * Certificate of origin line).
     */
    Optional<Shipment> findFirstByIcoMarkFullOrderByIdAsc(String icoMarkFull);

    /**
     * Fallback when the redo's mark isn't typed identically to the original —
     * e.g. redoing just mark "281" from an original billed as the range
     * "281-282". A plain substring match is deliberately loose here: this
     * only ever informs a suggestion the operator reviews before saving,
     * never a figure billed automatically without review.
     */
    Optional<Shipment> findFirstByIcoMarkFullContainingOrderByIdAsc(String icoMarkFragment);
}

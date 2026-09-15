package com.ase.billing.repo;

import com.ase.billing.domain.Shipment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ShipmentRepository extends JpaRepository<Shipment, Long> {

    /** The most recently created shipment for a customer, so the operator's
     *  next HC invoice / ICO mark number can be suggested from it. */
    Optional<Shipment> findTopByCustomerIdOrderByIdDesc(Long customerId);
}

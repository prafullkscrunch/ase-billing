package com.ase.billing.repo;

import com.ase.billing.domain.Shipment;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ShipmentRepository extends JpaRepository<Shipment, Long> {
}

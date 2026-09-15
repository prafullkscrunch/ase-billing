package com.ase.billing.repo;

import com.ase.billing.domain.TransportRoute;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TransportRouteRepository extends JpaRepository<TransportRoute, Long> {

    List<TransportRoute> findByActiveTrueOrderByNameAsc();
}

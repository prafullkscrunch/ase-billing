package com.ase.billing.repo;

import com.ase.billing.domain.CochinRateOverride;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CochinRateOverrideRepository extends JpaRepository<CochinRateOverride, Long> {

    Optional<CochinRateOverride> findByServiceId(Long serviceId);
}

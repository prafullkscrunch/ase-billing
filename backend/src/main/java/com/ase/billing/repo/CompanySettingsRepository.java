package com.ase.billing.repo;

import com.ase.billing.domain.CompanySettings;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CompanySettingsRepository extends JpaRepository<CompanySettings, Long> {
}

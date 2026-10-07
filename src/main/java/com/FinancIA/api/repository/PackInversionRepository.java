package com.FinancIA.api.repository;

import com.FinancIA.api.domain.PackInversion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PackInversionRepository extends JpaRepository<PackInversion, Long> {
}

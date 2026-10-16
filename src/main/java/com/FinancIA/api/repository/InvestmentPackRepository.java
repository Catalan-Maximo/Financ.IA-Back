package com.FinancIA.api.repository;

import com.FinancIA.api.domain.InvestmentPack;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InvestmentPackRepository extends JpaRepository<InvestmentPack, Long> {
}

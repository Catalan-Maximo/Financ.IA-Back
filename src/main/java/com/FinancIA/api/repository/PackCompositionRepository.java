package com.FinancIA.api.repository;

import com.FinancIA.api.domain.PackComposition;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PackCompositionRepository extends JpaRepository<PackComposition, Long> {

    List<PackComposition> findByPackId(Long packId);
}

package com.FinancIA.api.repository;

import com.FinancIA.api.domain.PackComposicion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PackComposicionRepository extends JpaRepository<PackComposicion, Long> {

    List<PackComposicion> findByPackId(Long packId);
}

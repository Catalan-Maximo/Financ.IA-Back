package com.FinancIA.api.repository;

import com.FinancIA.api.domain.Position;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PositionRepository extends JpaRepository<Position, Long> {

    List<Position> findByEmailOrderByDateDesc(String email);

    /** Todas las posiciones de un activo (para el stop-loss del job). */
    List<Position> findByAsset(String asset);
}

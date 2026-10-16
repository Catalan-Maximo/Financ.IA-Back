package com.FinancIA.api.repository;

import com.FinancIA.api.domain.CryptoState;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface CryptoStateRepository extends JpaRepository<CryptoState, Long> {

    /** Último snapshot de un par. */
    Optional<CryptoState> findTop1BySymbolOrderByDateDesc(String symbol);

    /** Los dos últimos snapshots (hoy y el anterior) para detectar cambios de veredicto. */
    List<CryptoState> findTop2BySymbolOrderByDateDesc(String symbol);

    /** Evita guardar dos veces el mismo par el mismo día. */
    boolean existsBySymbolAndDate(String symbol, LocalDate date);
}

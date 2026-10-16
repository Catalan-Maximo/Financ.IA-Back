package com.FinancIA.api.repository;

import com.FinancIA.api.domain.MarketRate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface MarketRateRepository extends JpaRepository<MarketRate, Long> {

    /** Último valor publicado de un tipo de activo. */
    Optional<MarketRate> findTop1ByAssetTypeOrderByDateDesc(String assetType);

    /** Todos los registros de un tipo de activo, del más nuevo al más viejo. */
    List<MarketRate> findByAssetTypeOrderByDateDesc(String assetType);

    /** Historial desde una fecha, ordenado de más antiguo a más nuevo. */
    List<MarketRate> findByAssetTypeAndDateGreaterThanEqualOrderByDateAsc(String assetType, LocalDate since);

    /** Evita guardar dos veces el mismo dato del mismo día. */
    boolean existsByAssetTypeAndDate(String assetType, LocalDate date);

    /** Evita guardar dos veces la misma billetera el mismo día. */
    boolean existsByAssetTypeAndEntityAndDate(String assetType, String entity, LocalDate date);

    /** Últimos 30 registros de una entidad (para el gráfico de historial). */
    List<MarketRate> findTop30ByEntityOrderByDateAsc(String entity);
}

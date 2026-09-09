package com.FinancIA.api.repository;

import com.FinancIA.api.domain.CriptoEstado;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface CriptoEstadoRepository extends JpaRepository<CriptoEstado, Long> {

    /** Último snapshot de un par. */
    Optional<CriptoEstado> findTop1BySimboloOrderByFechaDesc(String simbolo);

    /** Los dos últimos snapshots (hoy y el anterior) para detectar cambios de veredicto. */
    List<CriptoEstado> findTop2BySimboloOrderByFechaDesc(String simbolo);

    /** Evita guardar dos veces el mismo par el mismo día. */
    boolean existsBySimboloAndFecha(String simbolo, LocalDate fecha);
}

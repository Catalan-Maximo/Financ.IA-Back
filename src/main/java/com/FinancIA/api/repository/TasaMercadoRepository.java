package com.FinancIA.api.repository;

import com.FinancIA.api.domain.TasaMercado;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface TasaMercadoRepository extends JpaRepository<TasaMercado, Long> {

    /** Último valor publicado de un tipo de activo. */
    Optional<TasaMercado> findTop1ByTipoActivoOrderByFechaDesc(String tipoActivo);

    /** Todos los registros de un tipo de activo, del más nuevo al más viejo. */
    List<TasaMercado> findByTipoActivoOrderByFechaDesc(String tipoActivo);

    /** Historial desde una fecha, ordenado de más antiguo a más nuevo. */
    List<TasaMercado> findByTipoActivoAndFechaGreaterThanEqualOrderByFechaAsc(String tipoActivo, LocalDate desde);

    /** Evita guardar dos veces el mismo dato del mismo día. */
    boolean existsByTipoActivoAndFecha(String tipoActivo, LocalDate fecha);

    /** Evita guardar dos veces la misma billetera el mismo día. */
    boolean existsByTipoActivoAndEntidadAndFecha(String tipoActivo, String entidad, LocalDate fecha);
}

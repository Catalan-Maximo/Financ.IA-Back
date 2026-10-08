package com.FinancIA.api.repository;

import com.FinancIA.api.domain.Gasto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface GastoRepository extends JpaRepository<Gasto, Long> {

    List<Gasto> findByEmailAndFechaGreaterThanEqualOrderByFechaDesc(String email, LocalDate desde);
}

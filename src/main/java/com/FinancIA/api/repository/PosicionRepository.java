package com.FinancIA.api.repository;

import com.FinancIA.api.domain.Posicion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PosicionRepository extends JpaRepository<Posicion, Long> {

    List<Posicion> findByEmailOrderByFechaDesc(String email);
}

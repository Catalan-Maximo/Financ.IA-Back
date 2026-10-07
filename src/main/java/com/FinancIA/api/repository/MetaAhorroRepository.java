package com.FinancIA.api.repository;

import com.FinancIA.api.domain.MetaAhorro;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MetaAhorroRepository extends JpaRepository<MetaAhorro, Long> {

    List<MetaAhorro> findByEmailOrderByFechaLimiteAsc(String email);
}

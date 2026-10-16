package com.FinancIA.api.repository;

import com.FinancIA.api.domain.Expense;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface ExpenseRepository extends JpaRepository<Expense, Long> {

    List<Expense> findByEmailAndDateGreaterThanEqualOrderByDateDesc(String email, LocalDate since);
}

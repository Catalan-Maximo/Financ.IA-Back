package com.FinancIA.api.repository;

import com.FinancIA.api.domain.SavingsGoal;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SavingsGoalRepository extends JpaRepository<SavingsGoal, Long> {

    List<SavingsGoal> findByEmailOrderByDeadlineAsc(String email);
}

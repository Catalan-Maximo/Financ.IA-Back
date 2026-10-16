package com.FinancIA.api.controller;

import com.FinancIA.api.domain.Expense;
import com.FinancIA.api.domain.SavingsGoal;
import com.FinancIA.api.domain.Position;
import com.FinancIA.api.domain.User;
import com.FinancIA.api.repository.ExpenseRepository;
import com.FinancIA.api.repository.SavingsGoalRepository;
import com.FinancIA.api.repository.PositionRepository;
import com.FinancIA.api.repository.UserRepository;
import com.FinancIA.api.service.MarketService;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Portafolio y metas del usuario autenticado.
 *
 * GET    /api/v1/midinero/posiciones       — posiciones con P&L real
 * POST   /api/v1/midinero/posiciones       — agregar posición
 * DELETE /api/v1/midinero/posiciones/{id}  — eliminar posición
 * GET    /api/v1/midinero/metas            — metas de ahorro
 * POST   /api/v1/midinero/metas            — crear meta
 * POST   /api/v1/midinero/metas/{id}/aporte — sumar un aporte
 * DELETE /api/v1/midinero/metas/{id}       — eliminar meta
 */
@RestController
@RequestMapping("/api/v1/my-money")
public class MyMoneyController {

    private final PositionRepository positionRepository;
    private final SavingsGoalRepository goalRepository;
    private final ExpenseRepository expenseRepository;
    private final UserRepository userRepository;
    private final MarketService marketService;

    public MyMoneyController(PositionRepository positionRepository,
                              SavingsGoalRepository goalRepository,
                              ExpenseRepository expenseRepository,
                              UserRepository userRepository,
                              MarketService marketService) {
        this.positionRepository = positionRepository;
        this.goalRepository = goalRepository;
        this.expenseRepository = expenseRepository;
        this.userRepository = userRepository;
        this.marketService = marketService;
    }

    private String email() {
        return SecurityContextHolder.getContext().getAuthentication().getName();
    }

    // ─── Posiciones ─────────────────────────────────────────────

    @GetMapping("/positions")
    public List<Map<String, Object>> positions() {
        return positionRepository.findByEmailOrderByDateDesc(email()).stream()
                .map(this::withPnl)
                .toList();
    }

    @PostMapping("/positions")
    public Map<String, Object> addPosition(@RequestBody Position posicion) {
        posicion.setId(null);
        posicion.setEmail(email());
        if (posicion.getDate() == null) posicion.setDate(LocalDate.now());
        positionRepository.save(posicion);
        return withPnl(posicion);
    }

    @DeleteMapping("/posiciones/{id}")
    public Map<String, String> deletePosition(@PathVariable Long id) {
        positionRepository.deleteById(id);
        return Map.of("status", "ok");
    }

    private Map<String, Object> withPnl(Position p) {
        Double currentPrice = marketService.currentPrice(p.getAsset());
        if (currentPrice == null) currentPrice = p.getPurchasePrice(); // renta fija: sin fluctuación

        double currentValue = currentPrice * p.getQuantity();
        double purchaseValue = p.getPurchasePrice() * p.getQuantity();
        double gain = currentValue - purchaseValue;
        double gainPct = purchaseValue > 0 ? gain / purchaseValue * 100 : 0;

        // Stop-loss: alerta si cayó 10% o más desde el precio de compra
        boolean alert = gainPct <= -10;

        return Map.of(
                "id", p.getId(),
                "asset", p.getAsset(),
                "quantity", p.getQuantity(),
                "purchasePrice", p.getPurchasePrice(),
                "currentPrice", currentPrice,
                "currentValue", round2(currentValue),
                "gain", round2(gain),
                "gainPct", round2(gainPct),
                "date", p.getDate().toString(),
                "alert", alert
        );
    }

    // ─── Metas ──────────────────────────────────────────────────

    @GetMapping("/goals")
    public List<SavingsGoal> goals() {
        return goalRepository.findByEmailOrderByDeadlineAsc(email());
    }

    @PostMapping("/goals")
    public SavingsGoal createGoal(@RequestBody SavingsGoal meta) {
        meta.setId(null);
        meta.setEmail(email());
        if (meta.getCurrentAmount() == 0) meta.setCurrentAmount(0);
        return goalRepository.save(meta);
    }

    @PostMapping("/goals/{id}/contribution")
    public SavingsGoal contribute(@PathVariable Long id, @RequestBody Map<String, Double> body) {
        SavingsGoal meta = goalRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Meta no encontrada"));
        meta.setCurrentAmount(meta.getCurrentAmount() + body.getOrDefault("amount", 0.0));
        return goalRepository.save(meta);
    }

    @DeleteMapping("/metas/{id}")
    public Map<String, String> deleteGoal(@PathVariable Long id) {
        goalRepository.deleteById(id);
        return Map.of("status", "ok");
    }

    // ─── Gastos del mes ─────────────────────────────────────────

    @GetMapping("/expenses")
    public List<Expense> expenses() {
        LocalDate since = LocalDate.now().withDayOfMonth(1);
        return expenseRepository.findByEmailAndDateGreaterThanEqualOrderByDateDesc(email(), since);
    }

    @PostMapping("/expenses")
    public Expense addExpense(@RequestBody Expense expense) {
        expense.setId(null);
        expense.setEmail(email());
        if (expense.getDate() == null) expense.setDate(LocalDate.now());
        return expenseRepository.save(expense);
    }

    @DeleteMapping("/gastos/{id}")
    public Map<String, String> deleteExpense(@PathVariable Long id) {
        expenseRepository.deleteById(id);
        return Map.of("status", "ok");
    }

    /** Resumen del mes: total gastado, por categoría, sobra y metas. */
    @GetMapping("/expenses/summary")
    public Map<String, Object> expensesSummary() {
        String user = email();
        LocalDate since = LocalDate.now().withDayOfMonth(1);
        List<Expense> expenses = expenseRepository.findByEmailAndDateGreaterThanEqualOrderByDateDesc(user, since);

        double total = expenses.stream().mapToDouble(Expense::getAmount).sum();

        Map<String, Double> byCategory = new LinkedHashMap<>();
        for (Expense g : expenses) {
            byCategory.merge(g.getCategory(), g.getAmount(), Double::sum);
        }

        User u = userRepository.findByEmailIgnoreCase(user).orElse(null);
        Double income = u != null ? u.getMonthlyIncome() : null;
        Double leftover = income != null ? income - total : null;

        // Cuánto necesito por mes para cada meta
        List<Map<String, Object>> monthlyGoals = goalRepository
                .findByEmailOrderByDeadlineAsc(user).stream()
                .map(m -> {
                    long meses = Math.max(1, ChronoUnit.MONTHS.between(LocalDate.now(), m.getDeadline()));
                    double perMonth = Math.max(0, (m.getTargetAmount() - m.getCurrentAmount()) / meses);
                    return Map.<String, Object>of(
                            "name", m.getName(),
                            "remaining", round2(m.getTargetAmount() - m.getCurrentAmount()),
                            "perMonth", round2(perMonth)
                    );
                })
                .toList();

        // HashMap en vez de Map.of: "sobra" puede no existir si no hay ingreso declarado
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("total", round2(total));
        summary.put("income", income != null ? income : 0.0);
        summary.put("hasIncome", income != null);
        if (leftover != null) summary.put("leftover", round2(leftover));
        summary.put("byCategory", byCategory);
        summary.put("monthlyGoals", monthlyGoals);
        return summary;
    }

    /** Declara el ingreso mensual del usuario. */
    @PostMapping("/income")
    public Map<String, Object> saveIncome(@RequestBody Map<String, Double> body) {
        String user = email();
        User u = userRepository.findByEmailIgnoreCase(user)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));
        u.setMonthlyIncome(body.getOrDefault("amount", 0.0));
        userRepository.save(u);
        return Map.of("income", u.getMonthlyIncome());
    }

    private double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}

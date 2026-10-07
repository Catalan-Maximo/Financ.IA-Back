package com.FinancIA.api.controller;

import com.FinancIA.api.domain.MetaAhorro;
import com.FinancIA.api.domain.Posicion;
import com.FinancIA.api.repository.MetaAhorroRepository;
import com.FinancIA.api.repository.PosicionRepository;
import com.FinancIA.api.service.MercadoService;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
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
@RequestMapping("/api/v1/midinero")
public class MiDineroController {

    private final PosicionRepository posicionRepository;
    private final MetaAhorroRepository metaRepository;
    private final MercadoService mercadoService;

    public MiDineroController(PosicionRepository posicionRepository,
                              MetaAhorroRepository metaRepository,
                              MercadoService mercadoService) {
        this.posicionRepository = posicionRepository;
        this.metaRepository = metaRepository;
        this.mercadoService = mercadoService;
    }

    private String email() {
        return SecurityContextHolder.getContext().getAuthentication().getName();
    }

    // ─── Posiciones ─────────────────────────────────────────────

    @GetMapping("/posiciones")
    public List<Map<String, Object>> posiciones() {
        return posicionRepository.findByEmailOrderByFechaDesc(email()).stream()
                .map(this::conPnl)
                .toList();
    }

    @PostMapping("/posiciones")
    public Map<String, Object> agregarPosicion(@RequestBody Posicion posicion) {
        posicion.setId(null);
        posicion.setEmail(email());
        if (posicion.getFecha() == null) posicion.setFecha(LocalDate.now());
        posicionRepository.save(posicion);
        return conPnl(posicion);
    }

    @DeleteMapping("/posiciones/{id}")
    public Map<String, String> eliminarPosicion(@PathVariable Long id) {
        posicionRepository.deleteById(id);
        return Map.of("estado", "ok");
    }

    private Map<String, Object> conPnl(Posicion p) {
        Double precioActual = mercadoService.precioActual(p.getActivo());
        if (precioActual == null) precioActual = p.getPrecioCompra(); // renta fija: sin fluctuación

        double valorActual = precioActual * p.getCantidad();
        double valorCompra = p.getPrecioCompra() * p.getCantidad();
        double ganancia = valorActual - valorCompra;
        double gananciaPct = valorCompra > 0 ? ganancia / valorCompra * 100 : 0;

        return Map.of(
                "id", p.getId(),
                "activo", p.getActivo(),
                "cantidad", p.getCantidad(),
                "precioCompra", p.getPrecioCompra(),
                "precioActual", precioActual,
                "valorActual", redondear(valorActual),
                "ganancia", redondear(ganancia),
                "gananciaPct", redondear(gananciaPct),
                "fecha", p.getFecha().toString()
        );
    }

    // ─── Metas ──────────────────────────────────────────────────

    @GetMapping("/metas")
    public List<MetaAhorro> metas() {
        return metaRepository.findByEmailOrderByFechaLimiteAsc(email());
    }

    @PostMapping("/metas")
    public MetaAhorro crearMeta(@RequestBody MetaAhorro meta) {
        meta.setId(null);
        meta.setEmail(email());
        if (meta.getMontoActual() == 0) meta.setMontoActual(0);
        return metaRepository.save(meta);
    }

    @PostMapping("/metas/{id}/aporte")
    public MetaAhorro aportar(@PathVariable Long id, @RequestBody Map<String, Double> body) {
        MetaAhorro meta = metaRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Meta no encontrada"));
        meta.setMontoActual(meta.getMontoActual() + body.getOrDefault("monto", 0.0));
        return metaRepository.save(meta);
    }

    @DeleteMapping("/metas/{id}")
    public Map<String, String> eliminarMeta(@PathVariable Long id) {
        metaRepository.deleteById(id);
        return Map.of("estado", "ok");
    }

    private double redondear(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}

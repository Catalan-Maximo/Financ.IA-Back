package com.FinancIA.api.controller;

import com.FinancIA.api.domain.Gasto;
import com.FinancIA.api.domain.MetaAhorro;
import com.FinancIA.api.domain.Posicion;
import com.FinancIA.api.domain.Usuario;
import com.FinancIA.api.repository.GastoRepository;
import com.FinancIA.api.repository.MetaAhorroRepository;
import com.FinancIA.api.repository.PosicionRepository;
import com.FinancIA.api.repository.UsuarioRepository;
import com.FinancIA.api.service.MercadoService;
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
@RequestMapping("/api/v1/midinero")
public class MiDineroController {

    private final PosicionRepository posicionRepository;
    private final MetaAhorroRepository metaRepository;
    private final GastoRepository gastoRepository;
    private final UsuarioRepository usuarioRepository;
    private final MercadoService mercadoService;

    public MiDineroController(PosicionRepository posicionRepository,
                              MetaAhorroRepository metaRepository,
                              GastoRepository gastoRepository,
                              UsuarioRepository usuarioRepository,
                              MercadoService mercadoService) {
        this.posicionRepository = posicionRepository;
        this.metaRepository = metaRepository;
        this.gastoRepository = gastoRepository;
        this.usuarioRepository = usuarioRepository;
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

        // Stop-loss: alerta si cayó 10% o más desde el precio de compra
        boolean alerta = gananciaPct <= -10;

        return Map.of(
                "id", p.getId(),
                "activo", p.getActivo(),
                "cantidad", p.getCantidad(),
                "precioCompra", p.getPrecioCompra(),
                "precioActual", precioActual,
                "valorActual", redondear(valorActual),
                "ganancia", redondear(ganancia),
                "gananciaPct", redondear(gananciaPct),
                "fecha", p.getFecha().toString(),
                "alerta", alerta
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

    // ─── Gastos del mes ─────────────────────────────────────────

    @GetMapping("/gastos")
    public List<Gasto> gastos() {
        LocalDate desde = LocalDate.now().withDayOfMonth(1);
        return gastoRepository.findByEmailAndFechaGreaterThanEqualOrderByFechaDesc(email(), desde);
    }

    @PostMapping("/gastos")
    public Gasto agregarGasto(@RequestBody Gasto gasto) {
        gasto.setId(null);
        gasto.setEmail(email());
        if (gasto.getFecha() == null) gasto.setFecha(LocalDate.now());
        return gastoRepository.save(gasto);
    }

    @DeleteMapping("/gastos/{id}")
    public Map<String, String> eliminarGasto(@PathVariable Long id) {
        gastoRepository.deleteById(id);
        return Map.of("estado", "ok");
    }

    /** Resumen del mes: total gastado, por categoría, sobra y metas. */
    @GetMapping("/gastos/resumen")
    public Map<String, Object> resumenGastos() {
        String usuario = email();
        LocalDate desde = LocalDate.now().withDayOfMonth(1);
        List<Gasto> gastos = gastoRepository.findByEmailAndFechaGreaterThanEqualOrderByFechaDesc(usuario, desde);

        double total = gastos.stream().mapToDouble(Gasto::getMonto).sum();

        Map<String, Double> porCategoria = new LinkedHashMap<>();
        for (Gasto g : gastos) {
            porCategoria.merge(g.getCategoria(), g.getMonto(), Double::sum);
        }

        Usuario u = usuarioRepository.findByEmailIgnoreCase(usuario).orElse(null);
        Double ingreso = u != null ? u.getIngresoMensual() : null;
        Double sobra = ingreso != null ? ingreso - total : null;

        // Cuánto necesito por mes para cada meta
        List<Map<String, Object>> metasMensuales = metaRepository
                .findByEmailOrderByFechaLimiteAsc(usuario).stream()
                .map(m -> {
                    long meses = Math.max(1, ChronoUnit.MONTHS.between(LocalDate.now(), m.getFechaLimite()));
                    double porMes = Math.max(0, (m.getMontoObjetivo() - m.getMontoActual()) / meses);
                    return Map.<String, Object>of(
                            "nombre", m.getNombre(),
                            "faltante", redondear(m.getMontoObjetivo() - m.getMontoActual()),
                            "porMes", redondear(porMes)
                    );
                })
                .toList();

        // HashMap en vez de Map.of: "sobra" puede no existir si no hay ingreso declarado
        Map<String, Object> resumen = new LinkedHashMap<>();
        resumen.put("total", redondear(total));
        resumen.put("ingreso", ingreso != null ? ingreso : 0.0);
        resumen.put("tieneIngreso", ingreso != null);
        if (sobra != null) resumen.put("sobra", redondear(sobra));
        resumen.put("porCategoria", porCategoria);
        resumen.put("metasMensuales", metasMensuales);
        return resumen;
    }

    /** Declara el ingreso mensual del usuario. */
    @PostMapping("/ingreso")
    public Map<String, Object> guardarIngreso(@RequestBody Map<String, Double> body) {
        String usuario = email();
        Usuario u = usuarioRepository.findByEmailIgnoreCase(usuario)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));
        u.setIngresoMensual(body.getOrDefault("monto", 0.0));
        usuarioRepository.save(u);
        return Map.of("ingreso", u.getIngresoMensual());
    }

    private double redondear(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}

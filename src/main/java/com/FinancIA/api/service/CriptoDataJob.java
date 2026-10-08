package com.FinancIA.api.service;

import com.FinancIA.api.domain.CriptoEstado;
import com.FinancIA.api.domain.Posicion;
import com.FinancIA.api.domain.Usuario;
import com.FinancIA.api.repository.CriptoEstadoRepository;
import com.FinancIA.api.repository.PosicionRepository;
import com.FinancIA.api.repository.UsuarioRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

/**
 * Job programado que analiza BTC y ETH con datos de Binance.
 *
 * - Corre todos los días a las 8:15 (después del job del BCRA a las 8:00).
 * - También corre una vez al levantar la app.
 * - Try/catch por par: si Binance no responde, la app sigue con el
 *   último snapshot guardado en BD.
 * - Si el veredicto cambió respecto del día anterior, envía push
 *   a los usuarios que habilitaron notificaciones.
 * - Stop-loss: avisa (una vez por día por posición) si una posición
 *   cripto del portafolio cayó 10% o más desde su precio de compra.
 */
@Service
@Slf4j
public class CriptoDataJob {

    private static final double STOP_LOSS_PCT = -10.0;

    private final BinanceClient binanceClient;
    private final CriptoChecklistService checklist;
    private final CriptoEstadoRepository repository;
    private final UsuarioRepository usuarioRepository;
    private final PosicionRepository posicionRepository;
    private final ExpoPushClient expoPushClient;

    public CriptoDataJob(BinanceClient binanceClient,
                         CriptoChecklistService checklist,
                         CriptoEstadoRepository repository,
                         UsuarioRepository usuarioRepository,
                         PosicionRepository posicionRepository,
                         ExpoPushClient expoPushClient) {
        this.binanceClient = binanceClient;
        this.checklist = checklist;
        this.repository = repository;
        this.usuarioRepository = usuarioRepository;
        this.posicionRepository = posicionRepository;
        this.expoPushClient = expoPushClient;
    }

    @Scheduled(cron = "0 15 8 * * *")
    public void actualizarCriptoDiario() {
        actualizar();
    }

    @EventListener(ApplicationReadyEvent.class)
    public void cargarAlArrancar() {
        actualizar();
    }

    private void actualizar() {
        for (String par : BinanceClient.PARES) {
            try {
                analizarYGuardar(par);
            } catch (Exception e) {
                log.warn("No se pudo analizar {}: {}", par, e.getMessage());
            }
        }
    }

    private void analizarYGuardar(String simbolo) {
        LocalDate hoy = LocalDate.now();
        if (repository.existsBySimboloAndFecha(simbolo, hoy)) {
            log.info("{} ya está actualizado al {}", simbolo, hoy);
            return;
        }

        List<BinanceClient.Candle> velas = binanceClient.velasDiarias(simbolo, 210);
        List<Double> closes = velas.stream().map(BinanceClient.Candle::close).toList();
        CriptoChecklistService.CriptoAnalisis analisis = checklist.analizar(closes);

        CriptoEstado estado = new CriptoEstado();
        estado.setSimbolo(simbolo);
        estado.setFecha(hoy);
        estado.setPrecio(redondear(analisis.precio()));
        estado.setSma50(redondearNullable(analisis.sma50()));
        estado.setSma200(redondearNullable(analisis.sma200()));
        estado.setRsi14(redondearNullable(analisis.rsi14()));
        estado.setSoporte20(redondear(analisis.soporte20()));
        estado.setResistencia20(redondear(analisis.resistencia20()));
        estado.setCambio3d(redondearNullable(analisis.cambio3d()));
        estado.setChecksPasados(analisis.checksPasados());
        estado.setVeredicto(analisis.veredicto());
        estado.setFuente("Binance");
        repository.save(estado);

        log.info("Guardado {}: precio {}, RSI {}, veredicto {} ({}/4)",
                simbolo, estado.getPrecio(), estado.getRsi14(),
                estado.getVeredicto(), estado.getChecksPasados());

        notificarCambioVeredicto(simbolo, estado.getVeredicto());
        notificarStopLoss(simbolo, estado.getPrecio());
    }

    /** Si el veredicto cambió vs el día anterior, manda push a todos los usuarios. */
    private void notificarCambioVeredicto(String simbolo, String veredictoNuevo) {
        List<CriptoEstado> ultimos = repository.findTop2BySimboloOrderByFechaDesc(simbolo);
        if (ultimos.size() < 2) return; // primer día: no hay comparación

        String anterior = ultimos.get(1).getVeredicto();
        if (anterior.equals(veredictoNuevo)) return;

        String mensaje = simbolo.replace("USDT", "") + " cambió de " + anterior + " a " + veredictoNuevo
                + (veredictoNuevo.equals("BUY") ? " — señal de compra."
                : veredictoNuevo.equals("AVOID") ? " — señal de venta/cuidado."
                : " — señal de precaución.");

        for (Usuario usuario : usuarioRepository.findAllByPushTokenNotNull()) {
            try {
                expoPushClient.enviar(usuario.getPushToken(), "FinancIA — Señal cripto", mensaje);
                log.info("Push enviado a {}: {}", usuario.getEmail(), mensaje);
            } catch (Exception e) {
                log.warn("No se pudo enviar push a {}: {}", usuario.getEmail(), e.getMessage());
            }
        }
    }

    /**
     * Stop-loss del portafolio: si una posición cripto cayó 10% o más
     * desde su precio de compra, avisa por push (una vez por día por posición).
     */
    private void notificarStopLoss(String simbolo, double precioActual) {
        String nombre = simbolo.equals("BTCUSDT") ? "Bitcoin" : "Ethereum";
        LocalDate hoy = LocalDate.now();

        for (Posicion p : posicionRepository.findByActivo(nombre)) {
            if (p.getPrecioCompra() <= 0) continue;
            if (hoy.equals(p.getUltimaAlerta())) continue;

            double pct = (precioActual - p.getPrecioCompra()) / p.getPrecioCompra() * 100;
            if (pct > STOP_LOSS_PCT) continue;

            p.setUltimaAlerta(hoy);
            posicionRepository.save(p);

            usuarioRepository.findByEmailIgnoreCase(p.getEmail())
                    .filter(u -> u.getPushToken() != null && !u.getPushToken().isBlank())
                    .ifPresent(u -> {
                        try {
                            expoPushClient.enviar(u.getPushToken(), "FinancIA — Stop-loss",
                                    String.format("Tu posición en %s cayó %+.1f%% desde tu compra. Considerá vender.",
                                            nombre, pct));
                            log.info("Stop-loss enviado a {} por {}", u.getEmail(), nombre);
                        } catch (Exception e) {
                            log.warn("No se pudo enviar stop-loss a {}: {}", u.getEmail(), e.getMessage());
                        }
                    });
        }
    }

    private double redondear(double valor) {
        return Math.round(valor * 100.0) / 100.0;
    }

    private Double redondearNullable(Double valor) {
        return valor == null ? null : redondear(valor);
    }
}

package com.FinancIA.api.service;

import com.FinancIA.api.domain.TasaMercado;
import com.FinancIA.api.repository.TasaMercadoRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

/**
 * Job programado que actualiza las tasas de mercado desde el BCRA.
 *
 * - Corre todos los días a las 8:00 (hora local del servidor).
 * - También corre una vez al levantar la aplicación, para que la
 *   base de datos no arranque vacía.
 */
@Service
@Slf4j
public class MercadoDataJob {

    public static final String INFLACION_MENSUAL = "Inflación Mensual";
    public static final String BILLETERA_VIRTUAL = "Billetera Virtual";

    private final BcraClient bcraClient;
    private final BilleterasClient billeterasClient;
    private final TasaMercadoRepository repository;

    public MercadoDataJob(BcraClient bcraClient, BilleterasClient billeterasClient,
                          TasaMercadoRepository repository) {
        this.bcraClient = bcraClient;
        this.billeterasClient = billeterasClient;
        this.repository = repository;
    }

    /** Actualización diaria programada. */
    @Scheduled(cron = "0 0 8 * * *")
    public void actualizarTasasDiarias() {
        actualizarTasas();
    }

    /** Carga inicial al levantar la app. */
    @EventListener(ApplicationReadyEvent.class)
    public void cargarAlArrancar() {
        actualizarTasas();
    }

    private void actualizarTasas() {
        actualizarDesdeBcra();
        actualizarBilleteras();
    }

    private void actualizarDesdeBcra() {
        try {
            guardarSerie(BcraClient.SERIE_BADLAR, ActivoService.PLAZO_FIJO,
                    "BCRA BADLAR (plazo fijo bancos privados)");
            guardarSerie(BcraClient.SERIE_DOLAR_MINORISTA, ActivoService.DOLAR,
                    "BCRA tipo de cambio minorista");
            guardarSerie(BcraClient.SERIE_INFLACION_MENSUAL, INFLACION_MENSUAL,
                    "BCRA IPC");
        } catch (Exception e) {
            // Si el BCRA no responde, la app sigue funcionando con lo último guardado en BD
            log.warn("No se pudieron actualizar las tasas del BCRA: {}", e.getMessage());
        }
    }

    private void actualizarBilleteras() {
        try {
            for (BilleterasClient.BilleteraTasa billetera : billeterasClient.obtenerTasas()) {
                guardarBilletera(billetera.entidad(), billetera.tna());
            }
        } catch (Exception e) {
            // Si el sitio cambió el HTML o está caído, seguimos con el último valor guardado
            log.warn("No se pudieron actualizar las tasas de billeteras: {}", e.getMessage());
        }
    }

    private void guardarBilletera(String entidad, double tna) {
        LocalDate hoy = LocalDate.now();
        if (repository.existsByTipoActivoAndEntidadAndFecha(BILLETERA_VIRTUAL, entidad, hoy)) {
            log.info("Billetera {} ya está actualizada al {}", entidad, hoy);
            return;
        }

        TasaMercado tasa = new TasaMercado();
        tasa.setEntidad(entidad);
        tasa.setTipoActivo(BILLETERA_VIRTUAL);
        tasa.setValor(tna);
        tasa.setFuente(BilleterasClient.URL_FUENTE);
        tasa.setFecha(hoy);
        repository.save(tasa);

        log.info("Guardada billetera {}: {}% (fecha {})", entidad, tna, hoy);
    }

    private void guardarSerie(int idSerie, String tipoActivo, String fuente) {
        BcraClient.SerieValor ultimo = bcraClient.ultimoValor(idSerie);

        if (repository.existsByTipoActivoAndFecha(tipoActivo, ultimo.fecha())) {
            log.info("{} ya está actualizado al {}", tipoActivo, ultimo.fecha());
            return;
        }

        TasaMercado tasa = new TasaMercado();
        tasa.setEntidad("BCRA");
        tasa.setTipoActivo(tipoActivo);
        tasa.setValor(ultimo.valor());
        tasa.setFuente(fuente);
        tasa.setFecha(ultimo.fecha());
        repository.save(tasa);

        log.info("Guardada tasa {}: {} (fecha {})", tipoActivo, ultimo.valor(), ultimo.fecha());
    }
}

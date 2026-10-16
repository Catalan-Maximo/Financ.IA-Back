package com.FinancIA.api.service;

import com.FinancIA.api.domain.MarketRate;
import com.FinancIA.api.repository.MarketRateRepository;
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
public class MarketDataJob {

    public static final String MONTHLY_INFLATION = "Inflación Mensual";
    public static final String VIRTUAL_WALLET = "Billetera Virtual";

    private final BcraClient bcraClient;
    private final WalletsClient billeterasClient;
    private final MarketRateRepository repository;

    public MarketDataJob(BcraClient bcraClient, WalletsClient billeterasClient,
                          MarketRateRepository repository) {
        this.bcraClient = bcraClient;
        this.billeterasClient = billeterasClient;
        this.repository = repository;
    }

    /** Actualización diaria programada. */
    @Scheduled(cron = "0 0 8 * * *")
    public void updateDailyRates() {
        updateRates();
    }

    /** Carga inicial al levantar la app. */
    @EventListener(ApplicationReadyEvent.class)
    public void loadOnStartup() {
        updateRates();
    }

    private void updateRates() {
        updateFromBcra();
        updateWallets();
    }

    private void updateFromBcra() {
        try {
            saveSeries(BcraClient.SERIES_BADLAR, AssetService.FIXED_TERM_DEPOSIT,
                    "BCRA BADLAR (plazo fijo bancos privados)");
            saveSeries(BcraClient.SERIES_RETAIL_DOLLAR, AssetService.DOLLAR,
                    "BCRA tipo de cambio minorista");
            saveSeries(BcraClient.SERIES_MONTHLY_INFLATION, MONTHLY_INFLATION,
                    "BCRA IPC");
        } catch (Exception e) {
            // Si el BCRA no responde, la app sigue funcionando con lo último guardado en BD
            log.warn("No se pudieron actualizar las tasas del BCRA: {}", e.getMessage());
        }
    }

    private void updateWallets() {
        try {
            for (WalletsClient.WalletRate billetera : billeterasClient.getRates()) {
                saveWallet(billetera.entity(), billetera.tna());
            }
        } catch (Exception e) {
            // Si el sitio cambió el HTML o está caído, seguimos con el último valor guardado
            log.warn("No se pudieron actualizar las tasas de billeteras: {}", e.getMessage());
        }
    }

    private void saveWallet(String entity, double tna) {
        LocalDate today = LocalDate.now();
        if (repository.existsByAssetTypeAndEntityAndDate(VIRTUAL_WALLET, entity, today)) {
            log.info("Billetera {} ya está actualizada al {}", entity, today);
            return;
        }

        MarketRate rate = new MarketRate();
        rate.setEntity(entity);
        rate.setAssetType(VIRTUAL_WALLET);
        rate.setValue(tna);
        rate.setSource(WalletsClient.SOURCE_URL);
        rate.setDate(today);
        repository.save(rate);

        log.info("Guardada billetera {}: {}% (fecha {})", entity, tna, today);
    }

    private void saveSeries(int idSerie, String assetType, String source) {
        BcraClient.SeriesValue latest = bcraClient.latestValue(idSerie);

        if (repository.existsByAssetTypeAndDate(assetType, latest.date())) {
            log.info("{} ya está actualizado al {}", assetType, latest.date());
            return;
        }

        MarketRate rate = new MarketRate();
        rate.setEntity("BCRA");
        rate.setAssetType(assetType);
        rate.setValue(latest.value());
        rate.setSource(source);
        rate.setDate(latest.date());
        repository.save(rate);

        log.info("Guardada tasa {}: {} (fecha {})", assetType, latest.value(), latest.date());
    }
}

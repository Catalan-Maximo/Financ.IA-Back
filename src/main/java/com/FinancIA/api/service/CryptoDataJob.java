package com.FinancIA.api.service;

import com.FinancIA.api.domain.CryptoState;
import com.FinancIA.api.domain.Position;
import com.FinancIA.api.domain.User;
import com.FinancIA.api.repository.CryptoStateRepository;
import com.FinancIA.api.repository.PositionRepository;
import com.FinancIA.api.repository.UserRepository;
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
public class CryptoDataJob {

    private static final double STOP_LOSS_PCT = -10.0;

    private final BinanceClient binanceClient;
    private final CryptoChecklistService checklist;
    private final CryptoStateRepository repository;
    private final UserRepository userRepository;
    private final PositionRepository positionRepository;
    private final ExpoPushClient expoPushClient;

    public CryptoDataJob(BinanceClient binanceClient,
                         CryptoChecklistService checklist,
                         CryptoStateRepository repository,
                         UserRepository userRepository,
                         PositionRepository positionRepository,
                         ExpoPushClient expoPushClient) {
        this.binanceClient = binanceClient;
        this.checklist = checklist;
        this.repository = repository;
        this.userRepository = userRepository;
        this.positionRepository = positionRepository;
        this.expoPushClient = expoPushClient;
    }

    @Scheduled(cron = "0 15 8 * * *")
    public void updateCryptoDaily() {
        update();
    }

    @EventListener(ApplicationReadyEvent.class)
    public void loadOnStartup() {
        update();
    }

    private void update() {
        for (String par : BinanceClient.PAIRS) {
            try {
                analyzeAndSave(par);
            } catch (Exception e) {
                log.warn("No se pudo analizar {}: {}", par, e.getMessage());
            }
        }
    }

    private void analyzeAndSave(String symbol) {
        LocalDate today = LocalDate.now();
        if (repository.existsBySymbolAndDate(symbol, today)) {
            log.info("{} ya está actualizado al {}", symbol, today);
            return;
        }

        List<BinanceClient.Candle> velas = binanceClient.dailyCandles(symbol, 210);
        List<Double> closes = velas.stream().map(BinanceClient.Candle::close).toList();
        CryptoChecklistService.CryptoAnalysis analisis = checklist.analyze(closes);

        CryptoState state = new CryptoState();
        state.setSymbol(symbol);
        state.setDate(today);
        state.setPrice(round2(analisis.price()));
        state.setSma50(redondearNullable(analisis.sma50()));
        state.setSma200(redondearNullable(analisis.sma200()));
        state.setRsi14(redondearNullable(analisis.rsi14()));
        state.setSupport20(round2(analisis.support20()));
        state.setResistance20(round2(analisis.resistance20()));
        state.setChange3d(redondearNullable(analisis.change3d()));
        state.setChecksPassed(analisis.checksPassed());
        state.setVerdict(analisis.verdict());
        state.setSource("Binance");
        repository.save(state);

        log.info("Guardado {}: precio {}, RSI {}, veredicto {} ({}/4)",
                symbol, state.getPrice(), state.getRsi14(),
                state.getVerdict(), state.getChecksPassed());

        notifyVerdictChange(symbol, state.getVerdict());
        notifyStopLoss(symbol, state.getPrice());
    }

    /** Si el veredicto cambió vs el día anterior, manda push a todos los usuarios. */
    private void notifyVerdictChange(String symbol, String veredictoNuevo) {
        List<CryptoState> latest = repository.findTop2BySymbolOrderByDateDesc(symbol);
        if (latest.size() < 2) return; // primer día: no hay comparación

        String previous = latest.get(1).getVerdict();
        if (previous.equals(veredictoNuevo)) return;

        String message = symbol.replace("USDT", "") + " cambió de " + previous + " a " + veredictoNuevo
                + (veredictoNuevo.equals("BUY") ? " — señal de compra."
                : veredictoNuevo.equals("AVOID") ? " — señal de venta/cuidado."
                : " — señal de precaución.");

        for (User user : userRepository.findAllByPushTokenNotNull()) {
            try {
                expoPushClient.send(user.getPushToken(), "FinancIA — Señal cripto", message);
                log.info("Push enviado a {}: {}", user.getEmail(), message);
            } catch (Exception e) {
                log.warn("No se pudo enviar push a {}: {}", user.getEmail(), e.getMessage());
            }
        }
    }

    /**
     * Stop-loss del portafolio: si una posición cripto cayó 10% o más
     * desde su precio de compra, avisa por push (una vez por día por posición).
     */
    private void notifyStopLoss(String symbol, double currentPrice) {
        String name = symbol.equals("BTCUSDT") ? "Bitcoin" : "Ethereum";
        LocalDate today = LocalDate.now();

        for (Position p : positionRepository.findByAsset(name)) {
            if (p.getPurchasePrice() <= 0) continue;
            if (today.equals(p.getLastAlertAt())) continue;

            double pct = (currentPrice - p.getPurchasePrice()) / p.getPurchasePrice() * 100;
            if (pct > STOP_LOSS_PCT) continue;

            p.setLastAlertAt(today);
            positionRepository.save(p);

            userRepository.findByEmailIgnoreCase(p.getEmail())
                    .filter(u -> u.getPushToken() != null && !u.getPushToken().isBlank())
                    .ifPresent(u -> {
                        try {
                            expoPushClient.send(u.getPushToken(), "FinancIA — Stop-loss",
                                    String.format("Tu posición en %s cayó %+.1f%% desde tu compra. Considerá vender.",
                                            name, pct));
                            log.info("Stop-loss enviado a {} por {}", u.getEmail(), name);
                        } catch (Exception e) {
                            log.warn("No se pudo enviar stop-loss a {}: {}", u.getEmail(), e.getMessage());
                        }
                    });
        }
    }

    private double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private Double redondearNullable(Double value) {
        return value == null ? null : round2(value);
    }
}

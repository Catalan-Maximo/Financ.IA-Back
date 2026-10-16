package com.FinancIA.api.service;

import com.FinancIA.api.domain.PackComposition;
import com.FinancIA.api.domain.InvestmentPack;
import com.FinancIA.api.repository.PackCompositionRepository;
import com.FinancIA.api.repository.InvestmentPackRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Inserta los packs iniciales la primera vez que corre la app
 * (si la tabla está vacía). La composición queda en la BD y
 * se puede editar sin tocar código.
 */
@Service
@Slf4j
public class PackSeeder {

    private final InvestmentPackRepository packRepository;
    private final PackCompositionRepository compositionRepository;

    public PackSeeder(InvestmentPackRepository packRepository,
                      PackCompositionRepository compositionRepository) {
        this.packRepository = packRepository;
        this.compositionRepository = compositionRepository;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void seed() {
        if (packRepository.count() > 0) return;

        createPack("Argentina Potenciada", "Moderado", List.of("YPF", "GGAL", "SPY"), List.of(40, 35, 25));
        createPack("Tecnología Global", "Agresivo", List.of("AAPL", "MSFT", "SPY"), List.of(40, 35, 25));
        createPack("Global Estable", "Bajo", List.of("SPY", "AAPL", "MSFT"), List.of(60, 20, 20));
        log.info("Packs iniciales creados");
    }

    private void createPack(String name, String risk, List<String> tickers, List<Integer> weights) {
        InvestmentPack pack = new InvestmentPack();
        pack.setName(name);
        pack.setRisk(risk);
        packRepository.save(pack);

        for (int i = 0; i < tickers.size(); i++) {
            PackComposition item = new PackComposition();
            item.setPackId(pack.getId());
            item.setTicker(tickers.get(i));
            item.setWeightPercentage(weights.get(i));
            compositionRepository.save(item);
        }
    }
}

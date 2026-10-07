package com.FinancIA.api.service;

import com.FinancIA.api.domain.PackComposicion;
import com.FinancIA.api.domain.PackInversion;
import com.FinancIA.api.repository.PackComposicionRepository;
import com.FinancIA.api.repository.PackInversionRepository;
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

    private final PackInversionRepository packRepository;
    private final PackComposicionRepository composicionRepository;

    public PackSeeder(PackInversionRepository packRepository,
                      PackComposicionRepository composicionRepository) {
        this.packRepository = packRepository;
        this.composicionRepository = composicionRepository;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void sembrar() {
        if (packRepository.count() > 0) return;

        crearPack("Argentina Potenciada", "Moderado", List.of("YPF", "GGAL", "SPY"), List.of(40, 35, 25));
        crearPack("Tecnología Global", "Agresivo", List.of("AAPL", "MSFT", "SPY"), List.of(40, 35, 25));
        crearPack("Global Estable", "Bajo", List.of("SPY", "AAPL", "MSFT"), List.of(60, 20, 20));
        log.info("Packs iniciales creados");
    }

    private void crearPack(String nombre, String riesgo, List<String> tickers, List<Integer> pesos) {
        PackInversion pack = new PackInversion();
        pack.setNombre(nombre);
        pack.setRiesgo(riesgo);
        packRepository.save(pack);

        for (int i = 0; i < tickers.size(); i++) {
            PackComposicion item = new PackComposicion();
            item.setPackId(pack.getId());
            item.setTicker(tickers.get(i));
            item.setPorcentajePeso(pesos.get(i));
            composicionRepository.save(item);
        }
    }
}

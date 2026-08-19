package com.FinancIA.api.service;

import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Cliente de tasas de billeteras virtuales.
 *
 * No existe una API oficial para las tasas de Mercado Pago, Personal Pay,
 * Naranja X, Ualá, etc. Este cliente hace scraping de
 * https://billeterasvirtuales.com.ar, un sitio que agrega esas tasas
 * y las actualiza a diario.
 *
 * Estructura del HTML objetivo: cada billetera está en un &lt;article&gt;
 * con el nombre en el atributo title de su logo y la TNA en un
 * &lt;div class="text-lg font-bold"&gt; con coma decimal (ej. "17,89%").
 *
 * Si el sitio cambia su HTML o está caído, el {@link MercadoDataJob}
 * captura el error y la app sigue funcionando con el último valor
 * guardado en la base de datos.
 */
@Service
@Slf4j
public class BilleterasClient {

    public static final String URL_FUENTE = "https://billeterasvirtuales.com.ar/";

    /**
     * Billeteras que no nos interesan (entidades poco conocidas o
     * que no queremos mostrar en la app). Se filtran al scrapear.
     */
    public static final Set<String> EXCLUIDAS = Set.of(
            "Banco Bica", "Fiwind", "Carrefour Banco", "YPF",
            "Montemar Pay", "Claro Pay", "Taca Taca", "Let'sBit"
    );

    /** Una tasa de billetera parseada del sitio. */
    public record BilleteraTasa(String entidad, double tna) {}

    /**
     * Devuelve todas las tasas de billeteras publicadas en el sitio.
     */
    public List<BilleteraTasa> obtenerTasas() throws IOException {
        Document doc = Jsoup.connect(URL_FUENTE)
                .userAgent("Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36")
                .timeout(15_000)
                .get();

        List<BilleteraTasa> tasas = new ArrayList<>();
        for (Element article : doc.select("article")) {
            Element logo = article.selectFirst("img[title]");
            Element tasaEl = article.selectFirst("div.text-lg.font-bold");
            if (logo == null || tasaEl == null) continue;

            String entidad = logo.attr("title").trim();
            if (EXCLUIDAS.contains(entidad)) continue;

            String texto = tasaEl.text().replace("%", "").trim().replace(",", ".");
            try {
                double tna = Double.parseDouble(texto);
                tasas.add(new BilleteraTasa(entidad, tna));
            } catch (NumberFormatException e) {
                log.warn("No se pudo parsear la tasa de {}: '{}'", entidad, texto);
            }
        }
        log.info("Billeteras scrapeadas: {} tasas obtenidas", tasas.size());
        return tasas;
    }
}

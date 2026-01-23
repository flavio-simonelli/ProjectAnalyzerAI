package it.flaviosimonelli.isw2.correlation;

import it.flaviosimonelli.isw2.model.MetricCorrelation;
import org.apache.commons.math3.stat.correlation.PearsonsCorrelation;
import org.apache.commons.math3.stat.correlation.SpearmansCorrelation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

public class CorrelationCalculatorService {
    private static final Logger logger = LoggerFactory.getLogger(CorrelationCalculatorService.class);

    /**
     * Prende una mappa di colonne grezze (Stringhe), identifica la colonna target "Buggy",
     * converte le altre colonne in numeri e calcola le correlazioni.
     */
    public List<MetricCorrelation> calculateCorrelations(Map<String, List<String>> rawData) {
        List<MetricCorrelation> results = new ArrayList<>();

        // 1. Validazione e Estrazione Target
        if (!rawData.containsKey("Buggy")) {
            logger.error("Impossibile calcolare correlazioni: Colonna 'Buggy' mancante.");
            return results;
        }

        double[] buggyArray = parseTargetColumn(rawData.get("Buggy"));

        // 2. Setup Calcolatori Matematici
        PearsonsCorrelation pCalc = new PearsonsCorrelation();
        SpearmansCorrelation sCalc = new SpearmansCorrelation();

        // 3. Iterazione su tutte le colonne
        for (Map.Entry<String, List<String>> entry : rawData.entrySet()) {
            String metricName = entry.getKey();

            // Saltiamo colonne di metadati o la target stessa
            if (isMetadata(metricName)) continue;

            try {
                // Parsing colonna metrica
                double[] metricValues = parseMetricColumn(entry.getValue());

                // Sanity Check: Lunghezza array
                if (metricValues.length != buggyArray.length) {
                    logger.warn("Metrica {} scartata: lunghezza dati incoerente.", metricName);
                    continue;
                }

                // Calcolo Pura Matematica
                double pearson = pCalc.correlation(metricValues, buggyArray);
                double spearman = sCalc.correlation(metricValues, buggyArray);

                // Pulizia NaN -> 0.0
                if (Double.isNaN(pearson)) pearson = 0.0;
                if (Double.isNaN(spearman)) spearman = 0.0;

                results.add(new MetricCorrelation(metricName, pearson, spearman));

            } catch (NumberFormatException e) {
                // Log debug: è normale che colonne come "Class" o "File" falliscano il parsing se non filtrate prima
                logger.debug("Colonna {} non numerica, ignorata.", metricName);
            }
        }

        // 4. Ordinamento per Rilevanza (Spearman Assoluto decrescente)
        results.sort(Comparator.comparingDouble((MetricCorrelation m) -> Math.abs(m.getSpearman()))
                .reversed());

        return results;
    }

    // --- Helpers Privati (Business Logic interna) ---

    private boolean isMetadata(String header) {
        return header.equals("Buggy") ||
                header.equals("Version") ||
                header.equals("File") ||
                header.equals("Class") ||
                header.equals("Signature");
    }

    private double[] parseTargetColumn(List<String> values) {
        return values.stream()
                .mapToDouble(v -> v.equalsIgnoreCase("Yes") ? 1.0 : 0.0)
                .toArray();
    }

    private double[] parseMetricColumn(List<String> values) throws NumberFormatException {
        return values.stream()
                .mapToDouble(Double::parseDouble)
                .toArray();
    }
}

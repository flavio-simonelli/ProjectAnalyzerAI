package it.flaviosimonelli.isw2.correlation;

import it.flaviosimonelli.isw2.model.MetricCorrelation;
import org.apache.commons.math3.stat.correlation.PearsonsCorrelation;
import org.apache.commons.math3.stat.correlation.SpearmansCorrelation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

import it.flaviosimonelli.isw2.config.ProjectConstants;

public class CorrelationCalculatorService {
    private static final Logger logger = LoggerFactory.getLogger(CorrelationCalculatorService.class);

    /**
     * Prende una mappa di colonne grezze (Stringhe), identifica la colonna target @ProjectConstants.TARGET_CLASS,
     * converte le altre colonne in numeri e calcola le correlazioni.
     */
    public List<MetricCorrelation> calculateCorrelations(Map<String, List<String>> rawData) {
        // 1. Validazione ed Estrazione Target
        if (!rawData.containsKey(ProjectConstants.TARGET_CLASS)) {
            logger.error("Impossibile calcolare correlazioni: Colonna '{}' mancante.", ProjectConstants.TARGET_CLASS);
            return List.of();
        }

        double[] targetValues = parseTargetColumn(rawData.get(ProjectConstants.TARGET_CLASS));

        // 2. Elaborazione tramite Stream (Pipeline dichiarativa)
        return rawData.entrySet().stream()
                .filter(entry -> !isMetadata(entry.getKey())) // Filtraggio metadati
                .map(entry -> processMetricColumn(entry.getKey(), entry.getValue(), targetValues))
                .filter(Objects::nonNull) // Scarta i risultati falliti (NaN o lunghezze errate)
                .sorted(Comparator.comparingDouble((MetricCorrelation m) -> Math.abs(m.getSpearman())).reversed())
                .toList();
    }

    /**
     * Calcola la correlazione per una singola colonna.
     * Ritorna null se la colonna non è numerica o ha dimensioni errate.
     */
    private MetricCorrelation processMetricColumn(String name, List<String> rawValues, double[] targetValues) {
        try {
            double[] metricValues = parseMetricColumn(rawValues);

            if (metricValues.length != targetValues.length) {
                logger.warn("Metrica '{}' scartata: lunghezza dati incoerente.", name);
                return null;
            }

            double pearson = new PearsonsCorrelation().correlation(metricValues, targetValues);
            double spearman = new SpearmansCorrelation().correlation(metricValues, targetValues);

            // Sanificazione dei valori NaN (es. varianza zero)
            return new MetricCorrelation(
                    name,
                    Double.isNaN(pearson) ? 0.0 : pearson,
                    Double.isNaN(spearman) ? 0.0 : spearman
            );

        } catch (NumberFormatException e) {
            logger.debug("Salto colonna non numerica '{}': {}", name, e.getMessage());
            return null;
        }
    }

    // --- Helpers Privati (Business Logic interna) ---

    private boolean isMetadata(String header) {
        return header.equals(ProjectConstants.TARGET_CLASS) ||
                header.equals(ProjectConstants.VERSION_ATTRIBUTE) ||
                header.equals(ProjectConstants.RELEASE_INDEX_ATTRIBUTE) ||
                header.equals(ProjectConstants.DATA_ATTRIBUTE) ||
                header.equals("File") ||
                header.equals("Class") ||
                header.equals("Signature");
    }

    private double[] parseTargetColumn(List<String> values) {
        return values.stream()
                .mapToDouble(v -> v.equalsIgnoreCase(ProjectConstants.BUGGY_LABEL) ? 1.0 : 0.0)
                .toArray();
    }

    private double[] parseMetricColumn(List<String> values) throws NumberFormatException {
        return values.stream()
                .mapToDouble(Double::parseDouble)
                .toArray();
    }
}

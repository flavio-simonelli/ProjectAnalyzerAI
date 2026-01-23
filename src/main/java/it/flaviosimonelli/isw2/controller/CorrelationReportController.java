package it.flaviosimonelli.isw2.controller;

import it.flaviosimonelli.isw2.correlation.CorrelationCalculatorService;
import it.flaviosimonelli.isw2.model.MetricCorrelation;
import it.flaviosimonelli.isw2.util.CsvUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

public class CorrelationReportController {
    private static final Logger logger = LoggerFactory.getLogger(CorrelationReportController.class);

    private final CorrelationCalculatorService calculatorService;

    public CorrelationReportController() {
        this.calculatorService = new CorrelationCalculatorService();
    }

    /**
     * Entry point principale: orchestra la generazione del report di correlazione.
     * @param inputDatasetPath Path del CSV contenente il dataset completo (con colonna Buggy).
     * @param outputReportPath Path dove salvare il report delle correlazioni.
     */
    public void createCorrelationReport(String inputDatasetPath, String outputReportPath) {
        logger.info("=== START CORRELATION REPORT ===");
        logger.info("Input: {}", inputDatasetPath);

        // 1. LETTURA (Delegata a Utility I/O)
        // Leggiamo tutto come stringhe per disaccoppiare la lettura dal parsing
        Map<String, List<String>> rawDataset = CsvUtils.readCsvByColumn(inputDatasetPath);

        if (rawDataset.isEmpty()) {
            logger.error("Dataset vuoto o illeggibile. Report abortito.");
            return;
        }

        // 2. ELABORAZIONE (Delegata a Service di Dominio)
        // Il service trasforma i dati grezzi in oggetti di business (MetricCorrelation)
        List<MetricCorrelation> correlations = calculatorService.calculateCorrelations(rawDataset);

        if (correlations.isEmpty()) {
            logger.warn("Nessuna correlazione calcolata. Controllare se la colonna 'Buggy' è presente.");
            return;
        }

        // 3. TRASFORMAZIONE PER OUTPUT (Presentation Layer)
        // Convertiamo i DTO in righe di stringhe per il CSV
        List<String> headers = Arrays.asList("Metric", "Pearson", "Spearman");

        List<List<String>> rows = correlations.stream()
                .map(c -> Arrays.asList(
                        c.getMetricName(),
                        String.format(Locale.US, "%.4f", c.getPearson()),
                        String.format(Locale.US, "%.4f", c.getSpearman())
                ))
                .collect(Collectors.toList());

        // 4. SCRITTURA (Delegata a Utility I/O)
        CsvUtils.writeCsv(outputReportPath, headers, rows);

        // Log riassuntivo per l'utente (Top 3)
        printSummary(correlations);

        logger.info("Report salvato in: {}", outputReportPath);
        logger.info("=== END CORRELATION REPORT ===");
    }

    private void printSummary(List<MetricCorrelation> correlations) {
        logger.info("--- TOP 3 METRICS (Spearman) ---");

        correlations.stream().limit(3).forEach(c ->
                logger.info("{} -> Spear: {}, Pears: {}",
                        c.getMetricName(),
                        String.format(Locale.US, "%.3f", c.getSpearman()), // Formattazione manuale qui
                        String.format(Locale.US, "%.3f", c.getPearson())   // e qui
                )
        );
    }
}

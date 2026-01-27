package it.flaviosimonelli.isw2.ml.reporting;

import it.flaviosimonelli.isw2.ml.evaluation.EvaluationResult;
import it.flaviosimonelli.isw2.util.CsvUtils;
import org.apache.commons.csv.CSVPrinter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;

public class CsvResultExporter {

    private static final Logger logger = LoggerFactory.getLogger(CsvResultExporter.class);

    // Intestazioni del report finale
    private static final String[] REPORT_HEADERS = {
            "Project", "Run", "Classifier", "Sampling", "CostSensitive",
            "ReleaseIndex",
            "Precision", "Recall", "F-Measure", "AUC", "Kappa", "NPofB20"
    };

    /**
     * Scrive i risultati su CSV usando CsvUtils in modalità append.
     */
    public void appendResults(String outputPath, String project, int run, String classifierName,
                              String samplingName, List<EvaluationResult> results) {

        try (CSVPrinter printer = CsvUtils.createPrinter(outputPath, true, REPORT_HEADERS)) {

            for (EvaluationResult res : results) {
                printer.printRecord(
                        project,
                        run,
                        classifierName,
                        samplingName,
                        "No", // Placeholder per Cost Sensitive Learning se lo userai
                        res.getReleaseIndex(),
                        format(res.getPrecision()),
                        format(res.getRecall()),
                        format(res.getFMeasure()),
                        format(res.getAuc()),
                        format(res.getKappa()),
                        format(res.getNpofb20())
                );
            }
            // Il flush è gestito dal try-with-resources di CSVPrinter
            logger.info("Salvati {} risultati per {} ({})", results.size(), classifierName, samplingName);

        } catch (IOException e) {
            logger.error("Errore scrittura report ML: {}", outputPath, e);
        }
    }

    private String format(double val) {
        if (Double.isNaN(val)) return "NaN";
        return String.format(java.util.Locale.US, "%.4f", val);
    }
}
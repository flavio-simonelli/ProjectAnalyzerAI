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

    private static final String[] REPORT_HEADERS = {
            "Project",
            "Run",
            "Classifier",
            "Sampling",
            "FeatureSelection",
            "ReleaseIndex",
            "Precision",
            "Recall",
            "F-Measure",
            "AUC",
            "Kappa",
            "NPofB20",
            "SelectedFeatures"
    };

    /**
     * Scrive i risultati su CSV usando CsvUtils in modalità append.
     */
    public void appendResults(String outputPath, String project, int run, String classifierName,
                              String samplingName, String fsName, List<EvaluationResult> results) {

        try (CSVPrinter printer = CsvUtils.createPrinter(outputPath, true, REPORT_HEADERS)) {

            for (EvaluationResult res : results) {
                printer.printRecord(
                        project,
                        run,
                        classifierName,
                        samplingName,
                        fsName,
                        res.getReleaseIndex(),
                        format(res.getPrecision()),
                        format(res.getRecall()),
                        format(res.getFMeasure()),
                        format(res.getAuc()),
                        format(res.getKappa()),
                        format(res.getNpofb20()),
                        res.getSelectedFeatures()
                );
            }
            logger.info("Salvati {} risultati per {} ({}, {})",
                    results.size(), classifierName, samplingName, fsName);

        } catch (IOException e) {
            logger.error("Errore scrittura report ML: {}", outputPath, e);
        }
    }

    private String format(double val) {
        if (Double.isNaN(val)) return "NaN";
        return String.format(java.util.Locale.US, "%.4f", val);
    }
}
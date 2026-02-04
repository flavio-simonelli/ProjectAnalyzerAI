package it.flaviosimonelli.isw2.controller;

import it.flaviosimonelli.isw2.config.ProjectConstants;
import it.flaviosimonelli.isw2.exception.WhatIfAnalysisException;
import it.flaviosimonelli.isw2.ml.prediction.PredictionService;
import it.flaviosimonelli.isw2.ml.prediction.PredictionService.PredictionResult;
import it.flaviosimonelli.isw2.util.AppConfig;
import it.flaviosimonelli.isw2.util.CsvUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class WhatIfController {

    private static final Logger logger = LoggerFactory.getLogger(WhatIfController.class);
    private final String projectKey;
    private final PredictionService predictionService; // Init del servizio

    public WhatIfController(String projectKey) {
        this.projectKey = projectKey;
        this.predictionService = new PredictionService();
    }

    public void runAnalysis() {
        // 1. Configurazione (Path identici a prima)
        String outputBase = AppConfig.getProperty("output.base.path", "./results");
        String modelsDir = outputBase + "/models";
        String variantsDir = outputBase + "/dataset/variants";
        String datasetDir = outputBase + "/dataset";

        // Recupera nome modello
        String clfName = AppConfig.getProperty("final.model.classifier", "RandomForest");
        String modelNameBase = String.format("%s_%s_%s_%s", projectKey, clfName,
                AppConfig.getProperty("final.model.sampling", "NoSampling"),
                AppConfig.getProperty("final.model.feature_selection", "NoSelection"));

        String modelPath = Paths.get(modelsDir, modelNameBase + ".model").toString();

        logger.info("Avvio Analisi Impatto usando modello: {}", modelNameBase);

        try {
            // 2. Definizione Path Dataset
            String pathA = Paths.get(datasetDir, projectKey + ProjectConstants.DATASET_SUFFIX).toString();
            String pathBPlus = Paths.get(variantsDir, projectKey + "_B_PLUS.csv").toString();
            String pathBFixed = Paths.get(variantsDir, projectKey + "_B_FIXED.csv").toString();
            String pathCClean = Paths.get(variantsDir, projectKey + "_C_CLEAN.csv").toString();

            // 3. Analisi Delegata al PredictionService
            // Il controller ora fa solo "aggregazione" dei risultati
            Stats statsA = aggregateStats(predictionService.predictDataset(modelPath, pathA));
            Stats statsBPlus = aggregateStats(predictionService.predictDataset(modelPath, pathBPlus));
            Stats statsB = aggregateStats(predictionService.predictDataset(modelPath, pathBFixed));
            Stats statsC = aggregateStats(predictionService.predictDataset(modelPath, pathCClean));

            // 4. Salvataggio Report
            saveImpactReportCsv(statsA, statsBPlus, statsB, statsC, outputBase);

        } catch (Exception e) {
            throw new WhatIfAnalysisException("Errore in What-If " + projectKey, e);
        }
    }

    /**
     * Trasforma la lista di risultati row-by-row in conteggi totali.
     */
    private Stats aggregateStats(List<PredictionResult> results) {
        int actualBuggy = 0;
        int predictedBuggy = 0;

        for (PredictionResult res : results) {
            // Conta Reali
            if ("True".equalsIgnoreCase(res.actualClass()) || "Yes".equalsIgnoreCase(res.actualClass())) {
                actualBuggy++;
            }
            // Conta Predetti
            if ("True".equalsIgnoreCase(res.predictedClass()) || "Yes".equalsIgnoreCase(res.predictedClass())) {
                predictedBuggy++;
            }
        }
        return new Stats(actualBuggy, predictedBuggy);
    }

    // --- Helper salvataggio e classe interna Stats rimangono uguali ---

    private void saveImpactReportCsv(Stats a, Stats bPlus, Stats b, Stats c, String outputBase) {
        // ... (Codice identico a prima: crea righe e usa CsvUtils.writeCsv) ...
        String reportDir = outputBase + "/reports";
        new File(reportDir).mkdirs();
        String filePath = Paths.get(reportDir, projectKey + "_Impact_Analysis.csv").toString();

        List<String> headers = Arrays.asList("Dataset", "Actual_Bugs", "Predicted_Bugs (E)", "Bug_Reduction_Percentage");
        List<List<String>> rows = new ArrayList<>();

        rows.add(Arrays.asList("Dataset A", String.valueOf(a.actual), String.valueOf(a.predicted), ""));
        rows.add(Arrays.asList("Dataset B+", String.valueOf(bPlus.actual), String.valueOf(bPlus.predicted), ""));

        double dropPercent = (bPlus.actual > 0) ? (double) (bPlus.actual - b.predicted) / bPlus.actual * 100.0 : 0.0;
        rows.add(Arrays.asList("Dataset B (Fixed)", "-", String.valueOf(b.predicted), String.format("%.2f%%", dropPercent).replace(',', '.')));

        rows.add(Arrays.asList("Dataset C", String.valueOf(c.actual), String.valueOf(c.predicted), ""));

        CsvUtils.writeCsv(filePath, headers, rows);
    }

    private static class Stats {
        int actual;
        int predicted;
        public Stats(int a, int p) { actual = a; predicted = p; }
    }
}
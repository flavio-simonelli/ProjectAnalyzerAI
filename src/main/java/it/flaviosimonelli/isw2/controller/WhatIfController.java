package it.flaviosimonelli.isw2.controller;

import it.flaviosimonelli.isw2.config.ProjectConstants;
import it.flaviosimonelli.isw2.exception.WhatIfAnalysisException;
import it.flaviosimonelli.isw2.ml.data.WekaDataLoader;
import it.flaviosimonelli.isw2.util.AppConfig;
import it.flaviosimonelli.isw2.util.CsvUtils; // <--- Importiamo la tua utility
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import weka.classifiers.Classifier;
import weka.core.Attribute;
import weka.core.Instance;
import weka.core.Instances;
import weka.core.SerializationHelper;

import java.io.File;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class WhatIfController {

    private static final Logger logger = LoggerFactory.getLogger(WhatIfController.class);
    private final String projectKey;

    public WhatIfController(String projectKey) {
        this.projectKey = projectKey;
    }

    public void runAnalysis() {
        // 1. Recupera configurazione
        String clfName = AppConfig.getProperty("final.model.classifier", "RandomForest");
        String smpName = AppConfig.getProperty("final.model.sampling", "NoSampling");
        String fsName = AppConfig.getProperty("final.model.feature_selection", "NoSelection");

        String outputBase = AppConfig.getProperty("output.base.path", "./results");
        String modelsDir = outputBase + "/models";
        String variantsDir = outputBase + "/dataset/variants";
        String datasetDir = outputBase + "/dataset";

        String modelNameBase = String.format("%s_%s_%s_%s", projectKey, clfName, smpName, fsName);
        String modelPath = Paths.get(modelsDir, modelNameBase + ".model").toString();
        String headerPath = Paths.get(modelsDir, modelNameBase + "_Header.arff").toString();

        logger.info("Avvio Analisi Impatto (CSV Report) usando: {}", modelNameBase);

        try {
            // 2. Carica Modello
            Classifier model = (Classifier) SerializationHelper.read(modelPath);
            Instances header = (Instances) SerializationHelper.read(headerPath);
            header.setClassIndex(header.numAttributes() - 1);

            // 3. Path Dataset
            String pathA = Paths.get(datasetDir, projectKey + ProjectConstants.DATASET_SUFFIX).toString();
            String pathBPlus = Paths.get(variantsDir, projectKey + "_B_PLUS.csv").toString();
            String pathBFixed = Paths.get(variantsDir, projectKey + "_B_FIXED.csv").toString();
            String pathCClean = Paths.get(variantsDir, projectKey + "_C_CLEAN.csv").toString();

            // 4. Analisi (Classificazione)
            Stats statsA = analyzeDataset("Dataset A", pathA, model, header);
            Stats statsBPlus = analyzeDataset("Dataset B+", pathBPlus, model, header);
            Stats statsB = analyzeDataset("Dataset B (Fixed)", pathBFixed, model, header);
            Stats statsC = analyzeDataset("Dataset C", pathCClean, model, header);

            // 5. SALVATAGGIO CSV (Invece della stampa console)
            saveImpactReportCsv(statsA, statsBPlus, statsB, statsC, outputBase);

        } catch (Exception e) {
            throw new WhatIfAnalysisException("Errore critico nell'analisi What-If per " + projectKey, e);
        }
    }

    private void saveImpactReportCsv(Stats a, Stats bPlus, Stats b, Stats c, String outputBase) {
        String reportDir = outputBase + "/reports";
        new File(reportDir).mkdirs();
        String filePath = Paths.get(reportDir, projectKey + "_Impact_Analysis.csv").toString();

        // Intestazioni colonne
        List<String> headers = Arrays.asList(
                "Dataset",
                "Actual_Bugs",
                "Predicted_Bugs (E)",
                "Bug_Reduction_Percentage"
        );

        List<List<String>> rows = new ArrayList<>();

        // Riga Dataset A
        rows.add(Arrays.asList("Dataset A", String.valueOf(a.actual), String.valueOf(a.predicted), ""));

        // Riga Dataset B+
        rows.add(Arrays.asList("Dataset B+", String.valueOf(bPlus.actual), String.valueOf(bPlus.predicted), ""));

        // Riga Dataset B (Fixed) - Calcoliamo il calo % qui
        double dropPercent = 0.0;
        if (bPlus.actual > 0) {
            // Formula: (Actual(B+) - Predicted(B)) / Actual(B+)
            dropPercent = (double) (bPlus.actual - b.predicted) / bPlus.actual * 100.0;
        }

        // Nota: Dataset B è artificiale, quindi Actual è "N/A" o 0. Mettiamo "-" per chiarezza.
        rows.add(Arrays.asList(
                "Dataset B (Fixed)",
                "-",
                String.valueOf(b.predicted),
                String.format("%.2f%%", dropPercent).replace(',', '.') // Formato 12.50%
        ));

        // Riga Dataset C
        rows.add(Arrays.asList("Dataset C", String.valueOf(c.actual), String.valueOf(c.predicted), ""));

        // Scrittura su disco
        CsvUtils.writeCsv(filePath, headers, rows);
    }

    // --- I METODI DI ANALISI RIMANGONO UGUALI ---

    private Stats analyzeDataset(String label, String path, Classifier model, Instances header) throws Exception {
        logger.info("Inizio elaborazione: {}", label);

        try {
            WekaDataLoader loader = new WekaDataLoader();
            Instances rawData = loader.loadData(path, ProjectConstants.TARGET_CLASS, null);

            int actualBuggy = 0;
            int predictedBuggy = 0;
            int classIdxRaw = rawData.classIndex();

            for (Instance rawInst : rawData) {
                // Conteggio reali
                if (ProjectConstants.BUGGY_LABEL.equalsIgnoreCase(rawInst.stringValue(classIdxRaw))) {
                    actualBuggy++;
                }

                // Predizione con allineamento
                Instance alignedInst = alignDataToHeader(rawInst, header);
                double predVal = model.classifyInstance(alignedInst);
                String predLabel = header.classAttribute().value((int) predVal);

                if (ProjectConstants.BUGGY_LABEL.equalsIgnoreCase(predLabel)) {
                    predictedBuggy++;
                }
            }
            return new Stats(actualBuggy, predictedBuggy);

        } catch (Exception e) {
            throw new WhatIfAnalysisException("Fallimento durante l'analisi di " + label, e);
        }
    }

    private Instance alignDataToHeader(Instance rawInst, Instances header) {
        Instance aligned = new weka.core.DenseInstance(header.numAttributes());
        aligned.setDataset(header);
        for (int i = 0; i < header.numAttributes(); i++) {
            Attribute targetAttr = header.attribute(i);
            Attribute sourceAttr = rawInst.dataset().attribute(targetAttr.name());
            if (sourceAttr != null) {
                if (targetAttr.isNumeric()) aligned.setValue(i, rawInst.value(sourceAttr));
                else if (targetAttr.isNominal()) aligned.setValue(i, rawInst.stringValue(sourceAttr));
            } else {
                aligned.setMissing(i);
            }
        }
        return aligned;
    }

    private static class Stats {
        int actual;
        int predicted;
        public Stats(int a, int p) { actual = a; predicted = p; }
    }
}
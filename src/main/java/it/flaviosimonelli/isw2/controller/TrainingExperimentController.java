package it.flaviosimonelli.isw2.controller;

import it.flaviosimonelli.isw2.ml.data.WekaDataLoader;
import it.flaviosimonelli.isw2.ml.evaluation.EvaluationResult;
import it.flaviosimonelli.isw2.ml.exceptions.DatasetLoadingException;
import it.flaviosimonelli.isw2.ml.feature_selection.BestFirstSelectionStrategy;
import it.flaviosimonelli.isw2.ml.feature_selection.FeatureSelectionStrategy;
import it.flaviosimonelli.isw2.ml.feature_selection.InfoGainSelectionStrategy;
import it.flaviosimonelli.isw2.ml.feature_selection.NoSelectionStrategy;
import it.flaviosimonelli.isw2.ml.reporting.CsvResultExporter;
import it.flaviosimonelli.isw2.ml.sampling.SamplingStrategy;
import it.flaviosimonelli.isw2.ml.sampling.SmoteSamplingStrategy;
import it.flaviosimonelli.isw2.ml.validation.WalkForwardValidator;
import it.flaviosimonelli.isw2.util.AppConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import weka.classifiers.Classifier;
import weka.core.Instances;

import it.flaviosimonelli.isw2.config.ProjectConstants;

import java.io.File;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class TrainingExperimentController {

    private static final Logger logger = LoggerFactory.getLogger(TrainingExperimentController.class);

    private final String datasetPath;
    private final String projectKey;

    private static final String[] METADATA_COLS = {
            ProjectConstants.RELEASE_INDEX_ATTRIBUTE,
            ProjectConstants.DATA_ATTRIBUTE,
            ProjectConstants.VERSION_ATTRIBUTE,
            "File", "Class", "Signature"
    };

    public TrainingExperimentController(String datasetPath, String projectKey) {
        this.datasetPath = datasetPath;
        this.projectKey = projectKey;
    }

    public void runExperiment() {
        logger.info("Avvio esperimento Bug Prediction per {}", projectKey);

        WekaDataLoader loader = new WekaDataLoader();
        CsvResultExporter exporter = new CsvResultExporter();
        WalkForwardValidator validator = new WalkForwardValidator();

        // 1. Setup Cartelle Output
        String outputDir = AppConfig.getProperty("output.base.path", "./results") + "/ml";
        new File(outputDir).mkdirs();
        String reportPath = Paths.get(outputDir, projectKey + "_validation_results.csv").toString();

        // 2. Lettura Configurazioni da AppConfig
        int numRuns = Integer.parseInt(AppConfig.getProperty("ml.num_runs", "10"));

        // Legge liste separate da virgola (es. "RandomForest,IBk")
        List<String> activeClassifiers = getListFromConfig("ml.classifiers", "RandomForest,NaiveBayes,IBk");
        List<String> activeSamplers = getListFromConfig("ml.samplers", "NoSampling,SMOTE");
        List<String> activeFeatureSelectors = getListFromConfig("ml.feature_selection", "NoSelection,BestFirst");

        logger.info("CONFIGURAZIONE ESPERIMENTO:");
        logger.info("Runs: {}", numRuns);
        logger.info("Classifiers: {}", activeClassifiers);
        logger.info("Samplers: {}", activeSamplers);
        logger.info("Feature Sel.: {}", activeFeatureSelectors);

        try {
            // 3. Load Dataset
            Instances dataset = loader.loadData(datasetPath, ProjectConstants.TARGET_CLASS, null);

            // --- TRIPLO LOOP: Classifier -> Sampler -> FeatureSelection ---
            // Nota: L'ordine dei loop non è critico per il risultato, ma per il log sì.
            // Eseguiamo 10 run per ogni configurazione.

            for (int run = 1; run <= numRuns; run++) {
                logger.info(">>> INIZIO RUN {}/{}", run, numRuns);

                for (String clfName : activeClassifiers) {
                    for (String smpName : activeSamplers) {
                        for (String fsName : activeFeatureSelectors) {

                            try {
                                // A. Istanzia le strategie (Con Seed variabile dove serve)
                                Classifier classifier = getClassifierInstance(clfName, run);
                                SamplingStrategy sampler = getSamplingStrategy(smpName, run);
                                FeatureSelectionStrategy fsStrategy = getFeatureSelectionStrategy(fsName);

                                logger.info("Training: [Run {}] {} + {} + {}", run, clfName, smpName, fsName);

                                // B. Esegui Validazione Walk-Forward
                                // Passiamo TUTTE le strategie al validatore
                                List<EvaluationResult> results = validator.validate(
                                        dataset,
                                        classifier,
                                        METADATA_COLS, // Colonne da scartare (ID, Version...)
                                        sampler,       // SMOTE o null
                                        fsStrategy     // BestFirst o NoSelection
                                );

                                // C. Salva Risultati su CSV
                                exporter.appendResults(
                                        reportPath,
                                        projectKey,
                                        run,
                                        clfName,
                                        smpName,
                                        fsName, // Passiamo anche il nome della FS strategy
                                        results
                                );

                            } catch (Exception ex) {
                                logger.error("Errore nella configurazione [{} - {} - {}]: {}",
                                        clfName, smpName, fsName, ex.getMessage());
                            }
                        }
                    }
                }
            }

            logger.info("Esperimento completato. Report salvato in: {}", reportPath);

        } catch (DatasetLoadingException e) {
            logger.error("Impossibile caricare il dataset: {}", e.getMessage());
        } catch (Exception e) {
            logger.error("Errore critico di sistema", e);
        }
    }

    // --- Helper Methods ---

    /**
     * Legge una proprietà CSV e la converte in Lista di stringhe.
     */
    private List<String> getListFromConfig(String key, String defaultValue) {
        String raw = AppConfig.getProperty(key, defaultValue);
        if (raw == null || raw.trim().isEmpty()) return new ArrayList<>();
        return Arrays.asList(raw.split("\\s*,\\s*")); // Split su virgola rimuovendo spazi
    }

    private Classifier getClassifierInstance(String name, int seed) {
        switch (name) {
            case "RandomForest":
                weka.classifiers.trees.RandomForest rf = new weka.classifiers.trees.RandomForest();
                rf.setNumIterations(100);
                rf.setSeed(seed);
                return rf;
            case "NaiveBayes":
                return new weka.classifiers.bayes.NaiveBayes();
            case "IBk":
                return new weka.classifiers.lazy.IBk();
            default:
                throw new IllegalArgumentException("Classificatore non supportato: " + name);
        }
    }

    private SamplingStrategy getSamplingStrategy(String name, int seed) {
        if ("SMOTE".equals(name)) {
            SmoteSamplingStrategy smote = new SmoteSamplingStrategy();
            smote.setRandomSeed(seed);
            return smote;
        } else if ("NoSampling".equals(name)) {
            return null;
        }
        throw new IllegalArgumentException("Sampling Strategy non supportata: " + name);
    }

    private FeatureSelectionStrategy getFeatureSelectionStrategy(String name) {
        return switch (name) {
            case "BestFirst" -> new BestFirstSelectionStrategy();
            case "InfoGain" -> new InfoGainSelectionStrategy();
            case "NoSelection" -> new NoSelectionStrategy();
            default -> throw new IllegalArgumentException("Feature Selection Strategy non supportata: " + name);
        };
    }
}
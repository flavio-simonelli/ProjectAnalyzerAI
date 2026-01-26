package it.flaviosimonelli.isw2.controller;

import it.flaviosimonelli.isw2.ml.data.WekaDataLoader;
import it.flaviosimonelli.isw2.ml.evaluation.EvaluationResult;
import it.flaviosimonelli.isw2.ml.exceptions.DatasetLoadingException;
import it.flaviosimonelli.isw2.ml.exceptions.ModelEvaluationException;
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
import java.util.Arrays;
import java.util.List;

public class TrainingExperimentController {

    private static final Logger logger = LoggerFactory.getLogger(TrainingExperimentController.class);

    private final String datasetPath;
    private final String projectKey;

    private static final String[] METADATA_COLS = {
            ProjectConstants.RELEASE_INDEX_ATTRIBUTE, // Serve al validator, ma non al modello
            ProjectConstants.DATA_ATTRIBUTE,          // Serve ai grafici, ma non al modello
            ProjectConstants.VERSION_ATTRIBUTE,       // Stringa versione
            "File", "Class", "Signature"              // Identificatori stringa
    };

    public TrainingExperimentController(String datasetPath, String projectKey) {
        this.datasetPath = datasetPath;
        this.projectKey = projectKey;
    }

    public void runExperiment() {
        logger.info("Avvio esperimento Bug Prediction...");

        WekaDataLoader loader = new WekaDataLoader();
        CsvResultExporter exporter = new CsvResultExporter();
        WalkForwardValidator validator = new WalkForwardValidator();

        String outputDir = AppConfig.getProperty("output.base.path", "./results") + "/ml";
        new File(outputDir).mkdirs();
        String reportPath = Paths.get(outputDir, projectKey + "_validation_results.csv").toString();

        String runsStr = AppConfig.getProperty("ml.num_runs", "10");
        int numRuns;
        try {
            numRuns = Integer.parseInt(runsStr);
        } catch (NumberFormatException e) {
            logger.warn("Valore 'ml.num_runs' non valido nel properties: {}. Uso default: 10", runsStr);
            numRuns = 10;
        }

        logger.info("Configurazione caricata: Esecuzione di {} Run totali.", numRuns);

        try {
            // 1. Load Dataset Completo
            Instances dataset = loader.loadData(datasetPath, ProjectConstants.TARGET_CLASS, null);

            // Definiamo le configurazioni (nomi e oggetti base)
            // Nota: Li ricreeremo o configureremo dentro il loop per pulizia
            List<String> classifierNames = Arrays.asList("RandomForest", "NaiveBayes", "IBk");
            List<String> samplerNames = Arrays.asList("NoSampling", "SMOTE");

            // --- INIZIO LOOP 10 RUNS ---
            for (int run = 1; run <= numRuns; run++) {
                logger.info(">>> RUN {}/{}", run, numRuns);

                for (String clfName : classifierNames) {
                    for (String smpName : samplerNames) {

                        // 1. Setup Classificatore con SEED VARIABILE
                        Classifier classifier = getClassifierInstance(clfName, run);

                        // 2. Setup Sampler con SEED VARIABILE
                        SamplingStrategy strategy = getSamplingStrategy(smpName, run);

                        logger.info("Evaluating Run {}: {} + {}", run, clfName, smpName);

                        // 3. Validazione
                        List<EvaluationResult> results = validator.validate(
                                dataset,
                                classifier,
                                METADATA_COLS,
                                strategy
                        );

                        // 4. Export (passiamo 'run')
                        exporter.appendResults(
                                reportPath,
                                projectKey,
                                run,
                                clfName,
                                smpName,
                                results
                        );
                    }
                }
            }

            logger.info("Esperimento completato. File: {}", reportPath);

        } catch (DatasetLoadingException e) {
            logger.error("ERRORE DATI: {}", e.getMessage());
        } catch (ModelEvaluationException e) {
            logger.error("Errore validazione modello: {}", e.getMessage());
        } catch (Exception e) {
            logger.error("ERRORE IMPREVISTO DI SISTEMA", e);
        }
    }

    // --- Helper Classes ---

    private Classifier getClassifierInstance(String name, int seed) {
        switch (name) {
            case "RandomForest":
                weka.classifiers.trees.RandomForest rf = new weka.classifiers.trees.RandomForest();
                rf.setNumIterations(100);
                rf.setSeed(seed); // FONDAMENTALE: cambia la foresta ogni volta
                return rf;
            case "NaiveBayes":
                return new weka.classifiers.bayes.NaiveBayes(); // Deterministico, il seed non serve
            case "IBk":
                return new weka.classifiers.lazy.IBk(); // Deterministico (KNN standard)
            default:
                throw new IllegalArgumentException("Classificatore sconosciuto: " + name);
        }
    }

    private SamplingStrategy getSamplingStrategy(String name, int seed) {
        if ("SMOTE".equals(name)) {
            SmoteSamplingStrategy smote = new SmoteSamplingStrategy();
            smote.setRandomSeed(seed);
            return smote;
        }
        return null; // NoSampling
    }

}

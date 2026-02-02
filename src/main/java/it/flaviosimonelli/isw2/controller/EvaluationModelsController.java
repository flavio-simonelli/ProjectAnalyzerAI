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
import java.util.List;

public class EvaluationModelsController {

    private static final Logger logger = LoggerFactory.getLogger(EvaluationModelsController.class);

    private final String datasetPath;
    private final String projectKey;

    private static final List<String> METADATA_COLS = List.of(
            ProjectConstants.RELEASE_INDEX_ATTRIBUTE,
            ProjectConstants.DATA_ATTRIBUTE,
            ProjectConstants.VERSION_ATTRIBUTE,
            "File", "Class", "Signature"
    );

    public EvaluationModelsController(String datasetPath, String projectKey) {
        this.datasetPath = datasetPath;
        this.projectKey = projectKey;
    }

    /**
     * Raggruppa gli strumenti di esecuzione per ridurre il numero di parametri (S107).
     */
    private record ExperimentContext(
            WalkForwardValidator validator,
            CsvResultExporter exporter,
            String reportPath
    ) {}

    public void runExperiment() {
        logger.info("Avvio esperimento Bug Prediction per {}", projectKey);

        WekaDataLoader loader = new WekaDataLoader();
        CsvResultExporter exporter = new CsvResultExporter();
        WalkForwardValidator validator = new WalkForwardValidator();

        String reportPath = prepareOutputDirectory();

        // Lettura configurazioni
        int numRuns = Integer.parseInt(AppConfig.getProperty("ml.num_runs", "10"));
        List<String> activeClassifiers = AppConfig.getList("ml.classifiers", "RandomForest,NaiveBayes,IBk");
        List<String> activeSamplers = AppConfig.getList("ml.samplers", "NoSampling,SMOTE");
        List<String> activeFeatureSelectors = AppConfig.getList("ml.feature_selection", "NoSelection,BestFirst");

        ExperimentContext ctx = new ExperimentContext(validator, exporter, reportPath);

        try {
            Instances dataset = loader.loadData(datasetPath, ProjectConstants.TARGET_CLASS, null);

            for (int run = 1; run <= numRuns; run++) {
                logger.info(">>> INIZIO RUN {}/{}", run, numRuns);

                // Iteriamo sulle combinazioni di parametri
                for (String clfName : activeClassifiers) {
                    for (String smpName : activeSamplers) {
                        for (String fsName : activeFeatureSelectors) {
                            // Estrazione della logica in un metodo separato per evitare try-catch annidati (S1141)
                            executeSingleConfiguration(run, dataset, clfName, smpName, fsName, ctx);
                        }
                    }
                }
            }
            logger.info("Esperimento completato. Report salvato in: {}", reportPath);

        } catch (DatasetLoadingException e) {
            logger.error("Impossibile caricare il dataset: {}", e.getMessage());
        } catch (Exception e) {
            logger.error("Errore critico durante l'esecuzione dell'esperimento", e);
        }
    }

    /**
     * Esegue una singola configurazione del modello.
     * Metodo estratto per ridurre la complessità e risolvere java:S1141.
     */
    private void executeSingleConfiguration(int run, Instances dataset, String clf, String smp, String fs,
                                            ExperimentContext ctx) {
        try {
            Classifier classifier = getClassifierInstance(clf, run);
            SamplingStrategy sampler = getSamplingStrategy(smp, run);
            FeatureSelectionStrategy fsStrategy = getFeatureSelectionStrategy(fs);

            logger.info("Valutazione: [Run {}] {} + {} + {}", run, clf, smp, fs);

            List<EvaluationResult> results = ctx.validator().validate(
                    dataset,
                    classifier,
                    METADATA_COLS,
                    sampler,
                    fsStrategy
            );

            ctx.exporter().appendResults(ctx.reportPath(), projectKey, run, clf, smp, fs, results);

        } catch (Exception ex) {
            logger.error("Fallimento configurazione [{}|{}|{}]: {}", clf, smp, fs, ex.getMessage());
        }
    }

    private String prepareOutputDirectory() {
        String outputDir = AppConfig.getProperty("output.base.path", "./results") + "/ml";
        File dir = new File(outputDir);
        if (!dir.exists() && !dir.mkdirs()) {
            logger.warn("Impossibile creare la cartella di output: {}", outputDir);
        }
        return Paths.get(outputDir, projectKey + "_validation_results.csv").toString();
    }

    // --- Helper Methods ---

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
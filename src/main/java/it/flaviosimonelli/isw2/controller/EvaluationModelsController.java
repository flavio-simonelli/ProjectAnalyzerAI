package it.flaviosimonelli.isw2.controller;

import it.flaviosimonelli.isw2.ml.data.WekaDataLoader;
import it.flaviosimonelli.isw2.ml.evaluation.EvaluationResult;
import it.flaviosimonelli.isw2.ml.exceptions.DatasetLoadingException;
import it.flaviosimonelli.isw2.ml.feature_selection.*;
import it.flaviosimonelli.isw2.ml.model.ClassifierFactory;
import it.flaviosimonelli.isw2.ml.reporting.CsvResultExporter;
import it.flaviosimonelli.isw2.ml.sampling.SamplingFactory;
import it.flaviosimonelli.isw2.ml.sampling.SamplingStrategy;
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
     * Raggruppa gli strumenti di esecuzione per ridurre il numero di parametri
     */
    private record ExperimentContext(
            WalkForwardValidator validator,
            CsvResultExporter exporter,
            String reportPath
    ) {}

    public void runExperiment() {
        logger.info("Avvio esperimento Bug Prediction per {}", projectKey);

        WekaDataLoader loader = new WekaDataLoader();
        String reportPath = prepareOutputDirectory();
        ExperimentContext ctx = new ExperimentContext(new WalkForwardValidator(), new CsvResultExporter(), reportPath);

        // 1. Caricamento Parametri e Dataset
        int numRuns = Integer.parseInt(AppConfig.getProperty("evaluation.num_runs", "10"));
        List<String> activeClassifiers = AppConfig.getList("evaluation.classifiers", "RandomForest,NaiveBayes,IBk");
        List<String> activeSamplers = AppConfig.getList("evaluation.samplers", "NoSampling,SMOTE,Undersampling");

        // Parametri per la Feature Selection flessibile
        String fsMode = AppConfig.getProperty("evaluation.feature_selection.mode", "PER_FOLD"); // GLOBAL o PER_FOLD
        List<String> fsStrategies = AppConfig.getList("evaluation.feature_selection", "NoSelection,BestFirst");

        try {
            Instances originalDataset = loader.loadData(datasetPath, ProjectConstants.TARGET_CLASS, null);

            // 2. Loop delle RUN
            for (int run = 1; run <= numRuns; run++) {
                logger.info(">>> INIZIO RUN {}/{} <<<", run, numRuns);

                for (String fsStrategyName : fsStrategies) {
                    // Gestione della Feature Selection Globale: trasformiamo il dataset prima dei cicli interni
                    Instances workingDataset = applyGlobalFSIfRequired(originalDataset, fsStrategyName, fsMode);

                    // Se abbiamo fatto Global FS, nel fold non dobbiamo fare nulla
                    String effectiveFsName = "GLOBAL".equalsIgnoreCase(fsMode) ? "NoSelection" : fsStrategyName;

                    for (String clfName : activeClassifiers) {
                        for (String smpName : activeSamplers) {
                            executeSingleConfiguration(run, workingDataset, clfName, smpName, effectiveFsName, ctx);
                        }
                    }
                }
            }
            logger.info("Esperimento completato. Report in: {}", reportPath);

        } catch (DatasetLoadingException e) {
            logger.error("Errore caricamento dataset: {}", e.getMessage());
        } catch (Exception e) {
            logger.error("Errore critico durante l'esperimento", e);
        }
    }

    /**
     * Applica la selezione globale se configurata, altrimenti restituisce il dataset originale.
     */
    private Instances applyGlobalFSIfRequired(Instances data, String strategy, String mode) throws Exception {
        if ("GLOBAL".equalsIgnoreCase(mode) && !"NoSelection".equalsIgnoreCase(strategy)) {
            logger.info("Applicazione Global Feature Selection: {}", strategy);
            return new GlobalFeatureSelectionProcessor().apply(data, strategy, METADATA_COLS);
        }
        return data;
    }

    /**
     * Esegue una singola configurazione del modello.
     */
    private void executeSingleConfiguration(int run, Instances dataset, String clf, String smp, String fs,
                                            ExperimentContext ctx) {
        try {
            Classifier classifier = ClassifierFactory.getClassifier(clf, run);
            SamplingStrategy sampler = SamplingFactory.getStrategy(smp, run);
            FeatureSelectionStrategy fsStrategy = FeatureSelectionFactory.getStrategy(fs);

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
}
package it.flaviosimonelli.isw2.controller;

import it.flaviosimonelli.isw2.config.ProjectConstants;
import it.flaviosimonelli.isw2.ml.data.WekaDataLoader;
import it.flaviosimonelli.isw2.ml.feature_selection.*;
import it.flaviosimonelli.isw2.ml.model.ClassifierFactory;
import it.flaviosimonelli.isw2.ml.sampling.SamplingFactory;
import it.flaviosimonelli.isw2.ml.sampling.SamplingStrategy;
import it.flaviosimonelli.isw2.util.AppConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import weka.classifiers.Classifier;
import weka.core.Instances;
import weka.core.SerializationHelper;

import java.io.File;
import java.nio.file.Paths;
import java.util.List;

public class FinalModelTrainingController {

    private static final Logger logger = LoggerFactory.getLogger(FinalModelTrainingController.class);

    private final String datasetPath;
    private final String projectKey;

    private static final List<String> METADATA_COLS = List.of(
            ProjectConstants.RELEASE_INDEX_ATTRIBUTE,
            ProjectConstants.DATA_ATTRIBUTE,
            ProjectConstants.VERSION_ATTRIBUTE,
            "File", "Class", "Signature", "Project"
    );

    public FinalModelTrainingController(String datasetPath, String projectKey) {
        this.datasetPath = datasetPath;
        this.projectKey = projectKey;
    }

    public void trainAndSaveModel() {
        logger.info(">>> AVVIO TRAINING MODELLO FINALE per {} <<<", projectKey);

        // 1. Lettura configurazione
        String clfName = AppConfig.getProperty("final.model.classifier", "RandomForest");
        String smpName = AppConfig.getProperty("final.model.sampling", "NoSampling");
        String fsName = AppConfig.getProperty("final.model.feature_selection", "NoSelection");
        int finalSeed = Integer.parseInt(AppConfig.getProperty("final.model.seed", "42"));

        logger.info("Configurazione Scelta: Classificatore={}, Sampling={}, FeatureSel={}, Seed={}", clfName, smpName, fsName, finalSeed);

        WekaDataLoader loader = new WekaDataLoader();
        String modelsDir = prepareOutputDirectory();

        try {
            // 2. Caricamento e Selezione Globale delle Feature
            Instances data = loader.loadData(datasetPath, ProjectConstants.TARGET_CLASS, null);

            // Usiamo il nuovo Processor per gestire la selezione globale e la rimozione metadati in un colpo solo
            GlobalFeatureSelectionProcessor fsProcessor = new GlobalFeatureSelectionProcessor();
            Instances trainingData = fsProcessor.apply(data, fsName, METADATA_COLS);

            logger.info("Dataset preparato. Istanze: {}, Attributi: {}", trainingData.numInstances(), trainingData.numAttributes());

            // 3. Applicazione Sampling (Undersampling o SMOTE tramite Factory)
            SamplingStrategy sampler = SamplingFactory.getStrategy(smpName, finalSeed);
            if (sampler != null) {
                logger.info("Applicazione Sampling: {}...", smpName);
                trainingData = sampler.apply(trainingData);
            }

            // 4. Training
            Classifier model = ClassifierFactory.getClassifier(clfName, finalSeed);
            logger.info("Avvio addestramento {}...", clfName);
            model.buildClassifier(trainingData);

            // 5. Salvataggio
            saveModelArtifacts(trainingData, model, modelsDir, clfName, smpName, fsName);

        } catch (Exception e) {
            logger.error("Errore critico durante il training finale", e);
        }
    }

    private void saveModelArtifacts(Instances data, Classifier model, String dir, String clf, String smp, String fs) throws Exception {
        String fileNameBase = String.format("%s_%s_%s_%s", projectKey, clf, smp, fs);

        // Salvataggio Modello Binario
        String modelPath = Paths.get(dir, fileNameBase + ".model").toString();
        SerializationHelper.write(modelPath, model);

        // Salvataggio Header (fondamentale per le predizioni future su OpenJPA)
        String headerPath = Paths.get(dir, fileNameBase + "_Header.arff").toString();
        Instances headerOnly = new Instances(data, 0);
        SerializationHelper.write(headerPath, headerOnly);

        logger.info("MODELLO E HEADER SALVATI IN: {}", dir);
    }

    private String prepareOutputDirectory() {
        String path = AppConfig.getProperty("output.base.path", "./results") + "/models";
        new File(path).mkdirs();
        return path;
    }
}
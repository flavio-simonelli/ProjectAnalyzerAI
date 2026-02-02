package it.flaviosimonelli.isw2.controller;

import it.flaviosimonelli.isw2.config.ProjectConstants;
import it.flaviosimonelli.isw2.ml.data.WekaDataLoader;
import it.flaviosimonelli.isw2.ml.feature_selection.BestFirstSelectionStrategy;
import it.flaviosimonelli.isw2.ml.feature_selection.FeatureSelectionStrategy;
import it.flaviosimonelli.isw2.ml.feature_selection.InfoGainSelectionStrategy;
import it.flaviosimonelli.isw2.ml.feature_selection.NoSelectionStrategy;
import it.flaviosimonelli.isw2.ml.sampling.SamplingStrategy;
import it.flaviosimonelli.isw2.ml.sampling.SmoteSamplingStrategy;
import it.flaviosimonelli.isw2.util.AppConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import weka.classifiers.Classifier;
import weka.classifiers.bayes.NaiveBayes;
import weka.classifiers.lazy.IBk;
import weka.classifiers.trees.RandomForest;
import weka.core.Instances;
import weka.core.SerializationHelper;
import weka.filters.Filter;
import weka.filters.unsupervised.attribute.Remove;

import java.io.File;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class FinalModelTrainingController {

    private static final Logger logger = LoggerFactory.getLogger(FinalModelTrainingController.class);

    private final String datasetPath;
    private final String projectKey;

    private static final String[] METADATA_COLS = {
            ProjectConstants.RELEASE_INDEX_ATTRIBUTE,
            ProjectConstants.DATA_ATTRIBUTE,
            ProjectConstants.VERSION_ATTRIBUTE,
            "File", "Class", "Signature", "Project"
    };

    public FinalModelTrainingController(String datasetPath, String projectKey) {
        this.datasetPath = datasetPath;
        this.projectKey = projectKey;
    }

    public void trainAndSaveModel() {
        logger.info(">>> AVVIO TRAINING MODELLO FINALE per {} <<<", projectKey);

        // 1. LETTURA CONFIGURAZIONE
        String clfName = AppConfig.getProperty("final.model.classifier", "RandomForest");
        String smpName = AppConfig.getProperty("final.model.sampling", "NoSampling");
        String fsName = AppConfig.getProperty("final.model.feature_selection", "NoSelection");

        logger.info("Configurazione Scelta: Classificatore={}, Sampling={}, FeatureSel={}", clfName, smpName, fsName);

        WekaDataLoader loader = new WekaDataLoader();
        String modelsDir = AppConfig.getProperty("output.base.path", "./results") + "/models";
        new File(modelsDir).mkdirs();

        try {
            // 2. CARICAMENTO E PULIZIA BASE
            Instances data = loader.loadData(datasetPath, ProjectConstants.TARGET_CLASS, null);
            Instances trainingData = removeMetadataColumns(data);
            logger.info("Dati caricati e puliti. Istanze: {}, Attributi: {}", trainingData.numInstances(), trainingData.numAttributes());

            // 3. APPLICAZIONE FEATURE SELECTION
            // Nota: Le strategie accettano (train, test). Qui passiamo (data, data) e prendiamo solo il primo risultato.
            FeatureSelectionStrategy fsStrategy = getFeatureSelectionStrategy(fsName);
            if (!(fsStrategy instanceof NoSelectionStrategy)) {
                logger.info("Applicazione Feature Selection: {}...", fsName);
                Instances[] fsResult = fsStrategy.apply(trainingData, trainingData);
                trainingData = fsResult[0]; // Prendiamo il dataset filtrato
                logger.info("Feature Selection completata. Attributi rimanenti: {}", trainingData.numAttributes());
            }

            // 4. APPLICAZIONE SAMPLING (SMOTE)
            SamplingStrategy sampler = getSamplingStrategy(smpName);
            if (sampler != null) {
                logger.info("Applicazione Sampling: {}...", smpName);
                int oldSize = trainingData.numInstances();
                trainingData = sampler.apply(trainingData);
                logger.info("Sampling completato. Istanze: {} -> {}", oldSize, trainingData.numInstances());
            }

            // 5. CONFIGURAZIONE CLASSIFICATORE
            Classifier model = getClassifierInstance(clfName);

            // 6. TRAINING
            logger.info("Avvio addestramento {}...", clfName);
            model.buildClassifier(trainingData);
            logger.info("Training completato.");

            // 7. SALVATAGGIO
            // Costruiamo un nome file dinamico per non sovrascrivere modelli diversi
            String fileNameBase = String.format("%s_%s_%s_%s", projectKey, clfName, smpName, fsName);

            String modelPath = Paths.get(modelsDir, fileNameBase + ".model").toString();
            SerializationHelper.write(modelPath, model);

            String headerPath = Paths.get(modelsDir, fileNameBase + "_Header.arff").toString();
            Instances headerOnly = new Instances(trainingData, 0);
            SerializationHelper.write(headerPath, headerOnly);

            logger.info("=================================================");
            logger.info("MODELLO SALVATO CON SUCCESSO!");
            logger.info("File: {}", modelPath);
            logger.info("Header: {}", headerPath);
            logger.info("=================================================");

        } catch (Exception e) {
            logger.error("Errore critico durante il training finale", e);
        }
    }

    // --- FACTORY METHODS (Uguali a quelli dell'esperimento) ---

    private Classifier getClassifierInstance(String name) {
        switch (name) {
            case "RandomForest":
                RandomForest rf = new RandomForest();
                rf.setNumIterations(100);
                rf.setSeed(42);
                return rf;
            case "NaiveBayes":
                return new NaiveBayes();
            case "IBk":
                return new IBk(); // Default K=1
            default:
                throw new IllegalArgumentException("Classificatore non supportato: " + name);
        }
    }

    private SamplingStrategy getSamplingStrategy(String name) {
        if ("SMOTE".equals(name)) {
            SmoteSamplingStrategy smote = new SmoteSamplingStrategy();
            smote.setRandomSeed(42); // Seed fisso per riproducibilità finale
            return smote;
        } else if ("NoSampling".equals(name)) {
            return null;
        }
        throw new IllegalArgumentException("Sampling Strategy non supportata: " + name);
    }

    private FeatureSelectionStrategy getFeatureSelectionStrategy(String name) {
        return switch (name) {
            case "BestFirst" -> new BestFirstSelectionStrategy();
            case "InfoGain" -> new InfoGainSelectionStrategy(); // Assicurati che sia la versione fixata (Top 10)
            case "NoSelection" -> new NoSelectionStrategy();
            default -> throw new IllegalArgumentException("Feature Selection Strategy non supportata: " + name);
        };
    }

    private Instances removeMetadataColumns(Instances data) throws Exception {
        List<Integer> indicesToRemove = new ArrayList<>();
        for (String colName : METADATA_COLS) {
            weka.core.Attribute attr = data.attribute(colName);
            if (attr != null) indicesToRemove.add(attr.index());
        }
        if (indicesToRemove.isEmpty()) return data;

        int[] indicesArray = indicesToRemove.stream().mapToInt(i -> i).toArray();
        Remove removeFilter = new Remove();
        removeFilter.setAttributeIndicesArray(indicesArray);
        removeFilter.setInputFormat(data);
        return Filter.useFilter(data, removeFilter);
    }
}
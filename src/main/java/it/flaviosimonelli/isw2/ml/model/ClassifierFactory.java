package it.flaviosimonelli.isw2.ml.model;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import weka.classifiers.Classifier;
import weka.classifiers.bayes.NaiveBayes;
import weka.classifiers.lazy.IBk;
import weka.classifiers.trees.RandomForest;

public class ClassifierFactory {
    private static final Logger logger = LoggerFactory.getLogger(ClassifierFactory.class);

    private ClassifierFactory() {
        // Classe di utility
    }

    /**
     * Restituisce un'istanza del classificatore basata sul nome.
     * @param name Nome del classificatore (es. RandomForest, NaiveBayes, IBk)
     * @param seed Il seme per la generazione casuale (usato da RandomForest)
     * @return Un'istanza di weka.classifiers.Classifier
     */
    public static Classifier getClassifier(String name, int seed) {
        logger.debug("Istanza Classificatore richiesta: {}", name);

        return switch (name) {
            case "RandomForest" -> {
                RandomForest rf = new RandomForest();
                rf.setNumIterations(100); // Parametro standard per la tesi
                rf.setSeed(seed);
                yield rf;
            }
            case "NaiveBayes" -> new NaiveBayes();
            case "IBk" -> {
                IBk ibk = new IBk();
                yield ibk;
            }
            default -> {
                logger.error("Classificatore '{}' non supportato. Lancio eccezione.", name);
                throw new IllegalArgumentException("Classificatore non supportato: " + name);
            }
        };
    }
}
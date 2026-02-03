package it.flaviosimonelli.isw2.ml.model;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import weka.classifiers.Classifier;
import weka.classifiers.bayes.NaiveBayes;
import weka.classifiers.lazy.IBk;
import weka.classifiers.meta.FilteredClassifier;
import weka.classifiers.trees.RandomForest;
import weka.filters.unsupervised.attribute.Normalize;

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

        Classifier baseClassifier = switch (name) {
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

        return createFiltered(baseClassifier);
    }

    /**
     * Incapsula un classificatore in un filtro di normalizzazione.
     */
    private static Classifier createFiltered(Classifier classifier) {
        FilteredClassifier filtered = new FilteredClassifier();
        filtered.setFilter(new Normalize()); // Normalizzazione 0-1
        filtered.setClassifier(classifier);
        return filtered;
    }
}
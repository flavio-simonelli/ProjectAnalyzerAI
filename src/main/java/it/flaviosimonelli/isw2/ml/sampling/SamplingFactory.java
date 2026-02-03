package it.flaviosimonelli.isw2.ml.sampling;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SamplingFactory {
    private static final Logger logger = LoggerFactory.getLogger(SamplingFactory.class);

    private SamplingFactory() {
        // Costruttore privato per classe utility
    }

    public static SamplingStrategy getStrategy(String name, int seed) {
        logger.debug("Istanza Sampling Strategy richiesta: {}", name);

        return switch (name) {
            case "SMOTE" -> {
                SmoteSamplingStrategy smote = new SmoteSamplingStrategy();
                smote.setRandomSeed(seed);
                yield smote;
            }
            case "Undersampling" -> {
                UndersamplingStrategy us = new UndersamplingStrategy();
                us.setSeed(seed);
                yield us;
            }
            case "NoSampling", "None" -> null;
            default -> {
                logger.warn("Sampling Strategy '{}' non riconosciuta, procedo senza campionamento.", name);
                yield null;
            }
        };
    }
}
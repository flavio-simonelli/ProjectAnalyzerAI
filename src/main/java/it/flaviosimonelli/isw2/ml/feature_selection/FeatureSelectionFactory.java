package it.flaviosimonelli.isw2.ml.feature_selection;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FeatureSelectionFactory {
    private static final Logger logger = LoggerFactory.getLogger(FeatureSelectionFactory.class);

    private FeatureSelectionFactory() {
        // Costruttore privato per classe utility
    }

    public static FeatureSelectionStrategy getStrategy(String name) {
        logger.debug("Istanza Feature Selection richiesta: {}", name);

        return switch (name) {
            case "BestFirst" -> new BestFirstSelectionStrategy();
            case "InfoGain" -> new InfoGainSelectionStrategy();
            case "NoSelection", "None" -> new NoSelectionStrategy();
            default -> {
                logger.warn("Feature Selection '{}' non riconosciuta, procedo con NoSelection.", name);
                yield new NoSelectionStrategy();
            }
        };
    }
}
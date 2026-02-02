package it.flaviosimonelli.isw2.ml.exceptions;

/**
 * Eccezione specifica per fallimenti durante la selezione delle feature (Ranking o Search).
 */
public class FeatureSelectionException extends RuntimeException {

    public FeatureSelectionException(String message) {
        super(message);
    }

    public FeatureSelectionException(String message, Throwable cause) {
        super(message, cause);
    }
}
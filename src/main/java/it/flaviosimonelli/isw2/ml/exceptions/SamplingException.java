package it.flaviosimonelli.isw2.ml.exceptions;

/**
 * Eccezione specifica per fallimenti durante le fasi di oversampling o undersampling.
 */
public class SamplingException extends RuntimeException {

    public SamplingException(String message) {
        super(message);
    }

    public SamplingException(String message, Throwable cause) {
        super(message, cause);
    }
}
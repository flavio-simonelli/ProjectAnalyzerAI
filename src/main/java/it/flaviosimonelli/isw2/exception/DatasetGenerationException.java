package it.flaviosimonelli.isw2.exception;

/**
 * Eccezione specifica per errori durante la generazione del dataset.
 */
public class DatasetGenerationException extends RuntimeException {
    public DatasetGenerationException(String message, Throwable cause) {
        super(message, cause);
    }
}
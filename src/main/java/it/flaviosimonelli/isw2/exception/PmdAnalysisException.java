package it.flaviosimonelli.isw2.exception;

/**
 * Eccezione specifica per fallimenti durante l'inizializzazione o l'analisi PMD.
 */
public class PmdAnalysisException extends RuntimeException {
    public PmdAnalysisException(String message) {
        super(message);
    }

    public PmdAnalysisException(String message, Throwable cause) {
        super(message, cause);
    }
}
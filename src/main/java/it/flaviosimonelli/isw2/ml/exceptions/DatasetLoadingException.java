package it.flaviosimonelli.isw2.ml.exceptions;

public class DatasetLoadingException extends MLException {
    public DatasetLoadingException(String message) {
        super(message);
    }

    // Fondamentale per la "Exception Chaining":
    // Manteniamo l'errore originale di Weka come "causa" per il debug.
    public DatasetLoadingException(String message, Throwable cause) {
        super(message, cause);
    }
}
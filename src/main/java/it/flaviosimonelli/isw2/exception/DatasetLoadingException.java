package it.flaviosimonelli.isw2.exception;

/**
 * Eccezione personalizzata per errori durante il caricamento dei dataset Weka/ARFF/CSV.
 */
public class DatasetLoadingException extends RuntimeException {

    // Costruttore per messaggi semplici
    public DatasetLoadingException(String message) {
        super(message);
    }

    // Costruttore per incapsulare un'altra eccezione (causa)
    // Fondamentale per il "Chained Exception" pattern
    public DatasetLoadingException(String message, Throwable cause) {
        super(message, cause);
    }
}
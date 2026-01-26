package it.flaviosimonelli.isw2.ml.exceptions;

public class MLException extends RuntimeException {
    public MLException(String message) {
        super(message);
    }

    public MLException(String message, Throwable cause) {
        super(message, cause);
    }
}
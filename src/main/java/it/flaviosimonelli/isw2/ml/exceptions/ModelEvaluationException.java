package it.flaviosimonelli.isw2.ml.exceptions;

public class ModelEvaluationException extends MLException {

    public ModelEvaluationException(String message) {
        super(message);
    }

    public ModelEvaluationException(String message, Throwable cause) {
        super(message, cause);
    }
}
package it.flaviosimonelli.isw2.git.exceptions;

public class GitClientException extends RuntimeException {
    public GitClientException(String message) {
        super(message);
    }
    public GitClientException(String message, Throwable cause) {
        super(message, cause);
    }
}
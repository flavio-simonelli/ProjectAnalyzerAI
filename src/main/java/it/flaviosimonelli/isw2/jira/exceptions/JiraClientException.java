package it.flaviosimonelli.isw2.jira.exceptions;

public class JiraClientException extends RuntimeException {

    // Costruttore base con messaggio
    public JiraClientException(String message) {
        super(message);
    }

    // Chained Exception: Messaggio + causa originale
    public JiraClientException(String message, Throwable cause) {
        super(message, cause);
    }
}

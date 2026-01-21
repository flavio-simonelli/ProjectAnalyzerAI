package it.flaviosimonelli.isw2.jira.client;

import it.flaviosimonelli.isw2.jira.exceptions.JiraClientException;
import org.json.JSONArray;

public interface IJiraClient {
    /**
     * Recupera l'elenco grezzo delle versioni (releases) in formato JSON.
     */
    JSONArray getProjectVersions(String projectKey) throws JiraClientException;

    /**
     * Recupera tutti i ticket (issues) che rispettano i criteri di ricerca (Bug, Fixed, Closed/Resolved).
     */
    JSONArray getProjectIssues(String projectKey) throws JiraClientException;
}
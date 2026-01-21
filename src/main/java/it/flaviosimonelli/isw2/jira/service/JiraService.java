package it.flaviosimonelli.isw2.jira.service;

import it.flaviosimonelli.isw2.jira.bean.JiraRelease;
import it.flaviosimonelli.isw2.jira.client.IJiraClient;
import it.flaviosimonelli.isw2.jira.bean.JiraTicket;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class JiraService {
    private static final Logger logger = LoggerFactory.getLogger(JiraService.class);
    private IJiraClient jiraClient;

    public JiraService(IJiraClient jiraClient) {
        this.jiraClient = jiraClient;
    }

    /**
     * Recupera tutte le
     * @param projectKey la chiave Jira del progetto
     * @return la lista delle versioni elencate da Jira
     */
    public List<JiraRelease> getReleases(String projectKey) {
        JSONArray jsonReleases = jiraClient.getProjectVersions(projectKey);
        List<JiraRelease> releases = new ArrayList<>();

        for (int i = 0; i < jsonReleases.length(); i++) {
            JSONObject jsonItem = jsonReleases.getJSONObject(i);

            // Estrazione Nome (se manca, saltiamo la versione)
            String name = jsonItem.optString("name");
            if (name == null || name.isEmpty()) {
                logger.warn("Release scartata (manca name)");
                continue; // Salta alla prossima iterazione
            }
            // Estrazione ID (se manca, saltiamo la versione)
            String id = jsonItem.optString("id");
            if (id == null || id.isEmpty()) {
                logger.warn("Release scartata (manca id): Name={}", name);
                continue; // Salta alla prossima iterazione
            };
            // Estrazione Data (se manca, saltiamo la versione)
            LocalDate releaseDate = parseDateSimple(jsonItem.optString("releaseDate"));
            if (releaseDate == null) {
                // Logghiamo il warning con i dettagli per capire quale versione stiamo perdendo
                logger.warn("Release scartata (manca releaseDate): ID={}, Name={}", id, name);
                continue; // Salta alla prossima iterazione
            }
            // Creazione Oggetto
            JiraRelease release = new JiraRelease(
                    id,
                    name,
                    releaseDate
            );
            releases.add(release);
        }

        // Sorting per data di rilascio
        releases.sort(Comparator.comparing(JiraRelease::getReleaseDate, Comparator.nullsLast(Comparator.naturalOrder())));

        return releases;
    }

    /**
     * Recupera i ticket da Jira e li converte in oggetti del dominio JiraTicket.
     * @param projectKey la chiave JIRA del progetto
     * @return la lista dei fixed issue ticket
     */
    public List<JiraTicket> getTickets(String projectKey) {
        // Otteniamo il JSON grezzo dal client
        JSONArray jsonTickets = jiraClient.getProjectIssues(projectKey);
        List<JiraTicket> tickets = new ArrayList<>();

        // Iteriamo sui risultati
        for (int i = 0; i < jsonTickets.length(); i++) {
            JSONObject jsonItem = jsonTickets.getJSONObject(i);
            JSONObject fields = jsonItem.getJSONObject("fields");

            // Estrazione dei campi fondamentali
            String key = jsonItem.getString("key");
            // Estrazione Date (Created & Resolution)
            LocalDate createdDate = parseDateSimple(fields.optString("created"));
            LocalDate resolutionDate = parseDateSimple(fields.optString("resolutiondate"));
            // Estrazione Versioni (Fix & Affected)
            List<String> fixVersions = extractVersionNames(fields, "fixVersions");
            List<String> affectedVersions = extractVersionNames(fields, "versions");
            // Creazione dell'oggetto JiraTicket aggiornato
            JiraTicket ticket = new JiraTicket(
                    key,
                    createdDate,
                    resolutionDate,
                    fixVersions,
                    affectedVersions
            );
            tickets.add(ticket);
        }
        return tickets;
    }

    // --- Metodi Helper (Private) ---

    /**
     * Estrae i nomi delle versioni da un JSONArray dentro i campi di Jira.
     */
    private List<String> extractVersionNames(JSONObject fields, String arrayName) {
        List<String> list = new ArrayList<>();
        if (fields.has(arrayName) && !fields.isNull(arrayName)) {
            JSONArray arr = fields.getJSONArray(arrayName);
            for (int j = 0; j < arr.length(); j++) {
                JSONObject versionObj = arr.getJSONObject(j);
                list.add(versionObj.optString("name"));
            }
        }
        return list;
    }

    /**
     * Parsa una data Jira in modo sicuro.
     * Gestisce formati ISO ignorando l'orario e il timezone.
     *
     * @param dateTimeStr la stringa data (es. "2017-08-16T01:42:23.000+0000")
     * @return LocalDate o null se la stringa è invalida o vuota
     */
    private LocalDate parseDateSimple(String dateTimeStr) {
        // Controllo null o vuoto
        if (dateTimeStr == null || dateTimeStr.isEmpty()) {
            return null;
        }
        // Controllo di sicurezza: la stringa deve essere abbastanza lunga per contenere una data
        if (dateTimeStr.length() < 10) {
            logger.warn("Formato data non valido (troppo corto): '{}'", dateTimeStr);
            return null;
        }
        try {
            // Prendo i primi 10 caratteri (yyyy-MM-dd)
            return LocalDate.parse(dateTimeStr.substring(0, 10));
        } catch (DateTimeParseException e) {
            // Logghiamo l'errore ma non interrompiamo il programma.
            // Usiamo il livello WARN perché è un'anomalia nei dati, non un bug del codice.
            logger.warn("Impossibile parsare la data '{}': {}", dateTimeStr, e.getMessage());
            return null;
        }
    }
}

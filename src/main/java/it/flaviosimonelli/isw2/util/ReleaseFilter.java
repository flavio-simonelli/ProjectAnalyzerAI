package it.flaviosimonelli.isw2.util;

import it.flaviosimonelli.isw2.model.GitRelease;
import it.flaviosimonelli.isw2.model.JiraRelease;
import it.flaviosimonelli.isw2.model.Release;

import java.util.ArrayList;
import java.util.List;

public class ReleaseFilter {
    /**
     * Combina le release Jira e Git, dando priorità al tag Git più corto
     * per la stessa versione base (per filtrare suffissi come '-docker').
     *
     * @param gitReleasesList Lista di tutti i tag Git estratti.
     * @param jiraReleasesList Lista di tutte le release Jira estratte.
     * @return Lista di oggetti Release combinati.
     */
    public List<Release> combineReleases(List<GitRelease> gitReleasesList, List<JiraRelease> jiraReleasesList) {
        List<Release> releases = new ArrayList<>();
        // Itera su ogni versione Jira che deve essere abbinata a un tag Git
        for (JiraRelease jiraRelease : jiraReleasesList) {

            String jiraVersion = jiraRelease.getName(); // Esempio: "4.14.4"
            GitRelease bestMatch = selectGitRelease(gitReleasesList, jiraVersion);

            // Unione se è stato trovato un match pulito
            if (bestMatch != null) {
                // Trovata la corrispondenza più corta/pulita: crea l'oggetto finale 'Release'
                Release combined = new Release(jiraVersion , bestMatch.getCommitId());
                releases.add(combined);
            } else {
                // Logga se una versione Jira non ha trovato un tag Git corrispondente
                System.out.println("⚠️ WARN: Versione Jira " + jiraVersion + " non ha trovato un tag Git corrispondente.");
            }
        }

        System.out.println("✅ Combinate " + releases.size() + " release finali.");
        System.out.println( gitReleasesList.size() + " tag Git disponibili per il matching.");
        System.out.println( jiraReleasesList.size() + " release Jira da abbinare.");
        return releases;
    }

    private static GitRelease selectGitRelease(List<GitRelease> gitReleasesList, String jiraVersion) {
        GitRelease bestMatch = null;

        // Cerca il tag Git più corto che contiene la versione Jira
        for (GitRelease gitTag : gitReleasesList) {
            String fullTagId = gitTag.getId(); // Esempio: "release-4.14.4-docker"

            // Criterio di corrispondenza: il tag Git deve contenere la versione Jira
            if (fullTagId.contains(jiraVersion)) {

                // Criterio di Selezione: Seleziona il tag più corto
                if (bestMatch == null || fullTagId.length() < bestMatch.getId().length()) {
                    bestMatch = gitTag;
                }
            }
        }
        return bestMatch;
    }
}

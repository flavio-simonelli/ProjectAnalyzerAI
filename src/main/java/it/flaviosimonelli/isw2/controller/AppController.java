package it.flaviosimonelli.isw2.controller;

import it.flaviosimonelli.isw2.git.GitExtractor;
import it.flaviosimonelli.isw2.jira.JiraExtractor;
import it.flaviosimonelli.isw2.model.GitRelease;
import it.flaviosimonelli.isw2.model.JiraRelease;
import it.flaviosimonelli.isw2.model.JiraTicket;
import it.flaviosimonelli.isw2.util.CsvReader;
import it.flaviosimonelli.isw2.util.CsvWriter;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

public class AppController {

    public void extractFromJira(String projectKey) {
        try {
            JiraExtractor jiraExtractor = new JiraExtractor();
            List<JiraRelease> releases = jiraExtractor.extractJiraReleases(projectKey);
            List<JiraTicket>  tickets = jiraExtractor.extractJiraTickets(projectKey);
            CsvWriter.write(releases, "data/jira_releases_" + projectKey.toLowerCase() + ".csv");
            CsvWriter.write(tickets, "data/jira_tickets_" + projectKey.toLowerCase() + ".csv");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Estrae i tag GitHub corrispondenti alle release Jira del progetto specificato.
     *
     * @param basePath   percorso base per clonare/leggere i repository
     * @param owner      proprietario del repository GitHub (es. "apache")
     * @param repo       nome del repository (es. "commons-lang")
     * @param projectKey chiave del progetto Jira (es. "LANG")
     */
    public void extractFromGit(Path basePath, String owner, String repo, String projectKey, String prefix) {
        try {
            // lettura delle versioni di jira salvate
            List<JiraRelease> jiraReleases = null;
            try {
                jiraReleases = CsvReader.read(JiraRelease.class, "data/jira_releases_" + projectKey.toLowerCase() + ".csv");
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            List<String> jiraVersionNames = jiraReleases.stream()
                    .map(JiraRelease::getName)
                    .collect(Collectors.toList());

            // estrazione dei tag corrispondenti
            GitExtractor gitExtractor = new GitExtractor();
            List<GitRelease> gitReleases = gitExtractor.extractGitReleaseTag(
                    basePath,
                    owner,
                    repo,
                    jiraVersionNames,
                    prefix
            );

            // Esporta in CSV
            CsvWriter.write(gitReleases, "data/git_releases_" + projectKey.toLowerCase() + ".csv");

            System.out.printf("✅ Completata estrazione Git per %s/%s (%d tag trovati)%n",
                    owner, repo, gitReleases.size());

        } catch (IOException e) {
            System.err.println("❌ Errore durante l’estrazione da GitHub: " + e.getMessage());
            e.printStackTrace();
        } catch (Exception e) {
            System.err.println("❌ Errore imprevisto: " + e.getMessage());
            e.printStackTrace();
        }
    }
}

package it.flaviosimonelli.isw2.controller;

import it.flaviosimonelli.isw2.git.GitExtractor;
import it.flaviosimonelli.isw2.jira.JiraExtractor;
import it.flaviosimonelli.isw2.model.GitRelease;
import it.flaviosimonelli.isw2.model.JiraRelease;
import it.flaviosimonelli.isw2.model.JiraTicket;
import it.flaviosimonelli.isw2.model.Release;
import it.flaviosimonelli.isw2.ui.ConsoleMenu;
import it.flaviosimonelli.isw2.util.Config;
import it.flaviosimonelli.isw2.util.CsvReader;
import it.flaviosimonelli.isw2.util.CsvWriter;
import it.flaviosimonelli.isw2.util.ReleaseFilter;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

public class AppController {
    private static final Logger logger = LoggerFactory.getLogger(AppController.class);


    public void extractReleasesFromJira(Config config) {
        JiraExtractor jiraExtractor = new JiraExtractor();
        List<JiraRelease> releases = null;
        try {
            releases = jiraExtractor.extractJiraReleases(config.getJira().getProjectKey());
        } catch (IOException | URISyntaxException e) {
            logger.error("Errore durante l'estrazione delle release da Jira", e);

            System.err.println("\n--- ERRORE ESTRAZIONE JIRA ---");
            System.err.println(e.getMessage());

            System.exit(1);
        }
        try {
            CsvWriter.write(releases, config.getOutput().getDirectory() + "/jira_releases_" + config.getJira().getProjectKey().toLowerCase() + ".csv");
        } catch (IOException | IllegalAccessException e) {
            logger.error("Errore durante la scrittura delle release Jira su CSV", e);

            System.err.println("\n--- ERRORE SCRITTURA CSV ---");
            System.err.println(e.getMessage());

            System.exit(1);
        }
    }

    public void extractTicketsFromJira(Config config) {
        JiraExtractor jiraExtractor = new JiraExtractor();
        List<JiraTicket> tickets = null;
        try {
            tickets = jiraExtractor.extractJiraTickets(config.getJira().getProjectKey());
        } catch (IOException | URISyntaxException e) {
            logger.error("Errore durante l'estrazione dei ticket da Jira", e);

            System.err.println("\n--- ERRORE ESTRAZIONE JIRA ---");
            System.err.println(e.getMessage());

            System.exit(1);
        }
        try {
            CsvWriter.write(tickets, config.getOutput().getDirectory() + "/jira_tickets_" + config.getJira().getProjectKey().toLowerCase() + ".csv");
        } catch (IOException | IllegalAccessException e) {
            logger.error("Errore durante la scrittura dei ticket Jira su CSV", e);

            System.err.println("\n--- ERRORE SCRITTURA CSV ---");
            System.err.println(e.getMessage());

            System.exit(1);
        }
    }

    public void extractTagsFromGit(Config config) {
        GitExtractor gitExtractor = new GitExtractor();
        List<GitRelease> releases = null;
        try {
            releases = gitExtractor.extractGitReleases(
                    Path.of(config.getGithub().getLocalClonePath()),
                    config.getGithub().getOwner(),
                    config.getGithub().getRepository()
            );
        } catch (IOException | GitAPIException e) {
            logger.error("Errore durante l'estrazione delle release da Git", e);

            System.err.println("\n--- ERRORE ESTRAZIONE GIT ---");
            System.err.println(e.getMessage());

            System.exit(1);
        }
        try {
            CsvWriter.write(releases, config.getOutput().getDirectory() + "/git_releases_" + config.getGithub().getRepository().toLowerCase() + ".csv");
        } catch (IOException | IllegalAccessException e) {
            logger.error("Errore durante la scrittura delle release Git su CSV", e);

            System.err.println("\n--- ERRORE SCRITTURA CSV ---");
            System.err.println(e.getMessage());

            System.exit(1);
        }
    }

    public void filterReleases(Config config) {
        List<JiraRelease> jiraReleases = null;
        List<GitRelease> gitReleases = null;
        try {
            jiraReleases = CsvReader.read(JiraRelease.class, config.getOutput().getDirectory() + "/jira_releases_" + config.getJira().getProjectKey().toLowerCase() + ".csv");
            gitReleases = CsvReader.read(GitRelease.class,  config.getOutput().getDirectory() + "/git_releases_" + config.getGithub().getRepository().toLowerCase() + ".csv");
        } catch (IOException e) {
            logger.error("Errore durante la lettura dei CSV per il filtraggio delle release", e);

            System.err.println("\n--- ERRORE LETTURA CSV ---");
            System.err.println(e.getMessage());

            System.exit(1);
        }

        var releaseFilter = new ReleaseFilter();
        List<Release> combinedReleases = releaseFilter.combineReleases(gitReleases, jiraReleases);

        try {
            CsvWriter.write(combinedReleases, config.getOutput().getDirectory() + "/releases_" + config.getJira().getProjectKey().toLowerCase() + ".csv");
        } catch (IOException | IllegalAccessException e) {
            logger.error("Errore durante la scrittura delle release combinate su CSV", e);

            System.err.println("\n--- ERRORE SCRITTURA CSV ---");
            System.err.println(e.getMessage());

            System.exit(1);
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
    public void extractFromGitByTags(Path basePath, String owner, String repo, String projectKey, String prefix) {
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
            List<GitRelease> gitReleases = gitExtractor.extractGitReleaseByTag(
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

    public void extractFromGitByDates(Path repoPath, String owner, String repo, String projectKey) {
        try {
            // lettura delle versioni di jira salvate
            List<JiraRelease> jiraReleases = null;
            try {
                jiraReleases = CsvReader.read(JiraRelease.class, "data/jira_releases_" + projectKey.toLowerCase() + ".csv");
            } catch (IOException e) {
                throw new RuntimeException(e);
            }

            // estrazione dei tag corrispondenti
            GitExtractor gitExtractor = new GitExtractor();
            List<GitRelease> gitReleases = gitExtractor.extractGitReleasesByDate(
                    repoPath,
                    owner,
                    repo,
                    jiraReleases
            );

            // Esporta in CSV
            CsvWriter.write(gitReleases, "data/git_releases_" + projectKey.toLowerCase() + ".csv");

            System.out.printf("✅ Completata estrazione Git per %s/%s (%d release trovate)%n",
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

package it.flaviosimonelli.isw2;

import it.flaviosimonelli.isw2.controller.DatasetGeneratorController;
import it.flaviosimonelli.isw2.git.client.IGitClient;
import it.flaviosimonelli.isw2.git.client.JGitClient;
import it.flaviosimonelli.isw2.git.service.GitService;
import it.flaviosimonelli.isw2.jira.client.IJiraClient;
import it.flaviosimonelli.isw2.jira.service.JiraService;
import it.flaviosimonelli.isw2.jira.client.RestJiraClient;
import it.flaviosimonelli.isw2.util.AppConfig;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

public class Main {
    private static final Logger logger = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) {
        logger.info("Avvio applicazione ISW2 Mining...");

        try {
            // 1. Caricamento Configurazioni
            String projectKey = AppConfig.get("jira.projectKey");
            String gitRepoPath = AppConfig.get("git.repoPath");
            String csvOutputPath = AppConfig.get("output.csvPath");

            // Verifica veloce esistenza repo
            if (!new File(gitRepoPath).exists()) {
                logger.error("La cartella Git specificata non esiste: {}", gitRepoPath);
                return;
            }

            // 2. Setup Layer Basso (Clients)
            IJiraClient jiraClient = new RestJiraClient();
            IGitClient gitClient = new JGitClient(gitRepoPath);

            // 3. Setup Layer Logico (Services)
            JiraService jiraService = new JiraService(jiraClient);
            GitService gitService = new GitService(gitClient);

            // 4. Setup Controller
            DatasetGeneratorController generator = new DatasetGeneratorController(jiraService, gitService);

            // 5. Esecuzione
            logger.info("Inizio analisi per il progetto: {}", projectKey);
            logger.info("Lettura da Git: {}", gitRepoPath);

            generator.createDataset(projectKey, csvOutputPath);

            logger.info("COMPLETATO! File salvato in: {}", csvOutputPath);

        } catch (Exception e) {
            logger.error("Errore irreversibile durante l'esecuzione", e);
        }
    }
}
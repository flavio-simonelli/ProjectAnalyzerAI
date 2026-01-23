package it.flaviosimonelli.isw2;

import it.flaviosimonelli.isw2.controller.CorrelationReportController;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class Main {
    private static final Logger logger = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) {
        logger.info("Avvio applicazione ISW2 Mining...");

        try {
            // =================================================================
            // 1. CARICAMENTO CONFIGURAZIONI & SETUP PATH
            // =================================================================
            String projectKey = AppConfig.get("jira.projectKey");
            String gitRepoPath = AppConfig.get("git.repoPath");

            // Verifica Repo Git
            if (!new File(gitRepoPath).exists()) {
                logger.error("La cartella Git specificata non esiste: {}", gitRepoPath);
                return;
            }

            // Gestione Output Directory (Nuova logica dinamica)
            String basePathString = AppConfig.getProperty("output.base.path", "./results");
            Path datasetDir = Paths.get(basePathString, "dataset");
            Path correlationDir = Paths.get(basePathString, "correlation");

            // Creazione cartelle se non esistono
            Files.createDirectories(datasetDir);
            Files.createDirectories(correlationDir);

            // Definizione nomi file finali
            String datasetFilePath = datasetDir.resolve(projectKey + "_dataset.csv").toString();
            String correlationFilePath = correlationDir.resolve(projectKey + "_correlation.csv").toString();

            logger.info("Output configurato in: {}", Paths.get(basePathString).toAbsolutePath().normalize());


            // =================================================================
            // 2. SETUP DIPENDENZE (Dependency Injection Manuale)
            // =================================================================
            IJiraClient jiraClient = new RestJiraClient();
            IGitClient gitClient = new JGitClient(gitRepoPath);

            JiraService jiraService = new JiraService(jiraClient);
            GitService gitService = new GitService(gitClient);


            // =================================================================
            // 3. ESECUZIONE PIPELINE
            // =================================================================

            // --- STEP 1: Generazione Dataset ---
            logger.info(">>> STEP 1: Generazione Dataset per {}", projectKey);
            DatasetGeneratorController datasetController = new DatasetGeneratorController(jiraService, gitService);
            datasetController.createDataset(projectKey, datasetFilePath);
            logger.info("Dataset salvato: {}", datasetFilePath);


            // --- STEP 2: Analisi Correlazione ---
            logger.info(">>> STEP 2: Analisi Correlazione");
            CorrelationReportController correlationController = new CorrelationReportController();
            correlationController.createCorrelationReport(datasetFilePath, correlationFilePath);
            logger.info("Report Correlazione salvato: {}", correlationFilePath);


            logger.info("=== ESECUZIONE COMPLETATA CON SUCCESSO ===");

        } catch (Exception e) {
            logger.error("Errore irreversibile durante l'esecuzione", e);
        }
    }
}
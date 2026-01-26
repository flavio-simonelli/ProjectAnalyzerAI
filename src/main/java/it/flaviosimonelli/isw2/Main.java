package it.flaviosimonelli.isw2;

import it.flaviosimonelli.isw2.controller.CorrelationReportController;
import it.flaviosimonelli.isw2.controller.DatasetGeneratorController;
import it.flaviosimonelli.isw2.controller.TrainingExperimentController;
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

import static it.flaviosimonelli.isw2.config.ProjectConstants.CORRELATION_SUFFIX;
import static it.flaviosimonelli.isw2.config.ProjectConstants.DATASET_SUFFIX;

import it.flaviosimonelli.isw2.config.ExecutionMode;

public class Main {
    private static final Logger logger = LoggerFactory.getLogger(Main.class);

    public static void main() {
        logger.info("Avvio applicazione ISW2 Mining...");

        try {
            // =================================================================
            // 1. SETUP AMBIENTE E PATH
            // =================================================================
            String projectKey = AppConfig.get("jira.projectKey");
            String basePathString = AppConfig.getProperty("output.base.path", "./results");

            // Definizione Cartelle
            Path baseDir = Paths.get(basePathString);
            Path datasetDir = baseDir.resolve("dataset");
            Path correlationDir = baseDir.resolve("correlation");
            Path mlDir = baseDir.resolve("ml");

            // Creazione Cartelle (idempotente: se esistono non fa nulla)
            Files.createDirectories(datasetDir);
            Files.createDirectories(correlationDir);
            Files.createDirectories(mlDir);

            // Definizione File Path Completi
            String datasetFile = datasetDir.resolve(projectKey + DATASET_SUFFIX).toString();
            String correlationFile = correlationDir.resolve(projectKey + CORRELATION_SUFFIX).toString();

            // =================================================================
            // 2. LOGICA DI ESECUZIONE
            // =================================================================
            String modeStr = AppConfig.getProperty("execution.mode", "FULL");
            ExecutionMode mode = ExecutionMode.fromString(modeStr);

            logger.info("Project: {}", projectKey);
            logger.info("Execution Mode: {}", mode);
            logger.info("Base Output Dir: {}", baseDir.toAbsolutePath());

            switch (mode) {
                case FULL:
                    // Flusso completo a cascata
                    runDatasetGeneration(projectKey, datasetFile);
                    runCorrelationAnalysis(datasetFile, correlationFile);
                    runMachineLearning(datasetFile, projectKey);
                    break;

                case DATASET_ONLY:
                    runDatasetGeneration(projectKey, datasetFile);
                    break;

                case CORRELATION_ONLY:
                    if (ensureFileExists(datasetFile)) {
                        runCorrelationAnalysis(datasetFile, correlationFile);
                    }
                    break;

                case ML_ONLY:
                    if (ensureFileExists(datasetFile)) {
                        runMachineLearning(datasetFile, projectKey);
                    }
                    break;
            }

            logger.info("=== PROCESSO TERMINATO CON SUCCESSO ===");

        } catch (Exception e) {
            logger.error("ERRORE FATALE durante l'esecuzione", e);
            System.exit(1);
        }
    }

    private static void runDatasetGeneration(String projectKey, String outputCsvPath) {
        logger.info(">>> STEP 1: Generazione Dataset");

        // Inizializzazione Lazy dei servizi (risparmia risorse se non usati)
        String gitRepoPath = AppConfig.get("git.repoPath");
        // non effettuiamo più il controllo sul repo se è esistente o meno
        // Verifica Repo Git
//        if (!new File(gitRepoPath).exists()) {
//            logger.error("La cartella Git specificata non esiste: {}", gitRepoPath);
//            return;
//        }
        IJiraClient jiraClient = new RestJiraClient();
        IGitClient gitClient = new JGitClient(gitRepoPath);
        JiraService jiraService = new JiraService(jiraClient);
        GitService gitService = new GitService(gitClient);

        DatasetGeneratorController controller = new DatasetGeneratorController(jiraService, gitService);
        controller.createDataset(projectKey, outputCsvPath);
    }

    private static void runCorrelationAnalysis(String inputCsvPath, String outputReportPath) {
        logger.info(">>> STEP 2: Analisi Correlazione");
        CorrelationReportController controller = new CorrelationReportController();
        controller.createCorrelationReport(inputCsvPath, outputReportPath);
    }

    private static void runMachineLearning(String inputCsvPath, String projectKey) {
        logger.info(">>> STEP 3: Machine Learning (Weka)");
        TrainingExperimentController mlController = new TrainingExperimentController(inputCsvPath, projectKey);
        mlController.runExperiment();
    }

    // Utility per verificare i pre-requisiti
    private static boolean ensureFileExists(String path) {
        if (!new File(path).exists()) {
            logger.error("File mancante richiesto per questo step: {}", path);
            logger.error("Esegui prima uno step precedente (es. FULL o DATASET_ONLY).");
            return false;
        }
        return true;
    }

}
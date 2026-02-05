package it.flaviosimonelli.isw2;

import it.flaviosimonelli.isw2.controller.*;
import it.flaviosimonelli.isw2.git.client.IGitClient;
import it.flaviosimonelli.isw2.git.client.JGitClient;
import it.flaviosimonelli.isw2.git.service.GitService;
import it.flaviosimonelli.isw2.jira.client.IJiraClient;
import it.flaviosimonelli.isw2.jira.service.JiraService;
import it.flaviosimonelli.isw2.jira.client.RestJiraClient;
import it.flaviosimonelli.isw2.metrics.StaticAnalysisService;
import it.flaviosimonelli.isw2.ml.prediction.PredictionService;
import it.flaviosimonelli.isw2.ml.reporting.GraphGenerationService;
import it.flaviosimonelli.isw2.util.AppConfig;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static it.flaviosimonelli.isw2.config.ProjectConstants.CORRELATION_SUFFIX;
import static it.flaviosimonelli.isw2.config.ProjectConstants.DATASET_SUFFIX;

import it.flaviosimonelli.isw2.config.ExecutionMode;

public class Main {
    private static final Logger logger = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) {
        logger.info("Avvio applicazione ISW2 Prediction...");

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

            String mlResultFile = mlDir.resolve(projectKey + "_validation_results.csv").toString();
            String graphsDir = mlDir.resolve("graphs").toString();

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
                        runGraphGeneration(mlResultFile, graphsDir);
                    }
                    break;
                case GRAPH_ONLY:
                    if (ensureFileExists(mlResultFile)) {
                        runGraphGeneration(mlResultFile, graphsDir);
                    } else {
                        logger.error("Impossibile generare grafici: manca il file risultati ML ({})", mlResultFile);
                    }
                    break;

                case TRAIN_FINAL:
                    if (ensureFileExists(datasetFile)) {
                        runFinalTraining(datasetFile, projectKey);
                    }
                    break;

                case CREATE_VARIANTS:
                    if (ensureFileExists(datasetFile)) {
                        runDatasetVariantsCreation(datasetFile, projectKey);
                    }
                    break;

                case WHATIF_ANALYSIS:
                    runImpactAnalysis(projectKey);
                    break;

                case REFACTORING_EXPERIMENT: // Assicurati che l'enum abbia questo valore
                    runRefactoringExperiment(projectKey);
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

        String gitRepoPath = AppConfig.get("git.repoPath");
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
        EvaluationModelsController mlController = new EvaluationModelsController(inputCsvPath, projectKey);
        mlController.runExperiment();
    }

    private static void runGraphGeneration(String inputCsvPath, String outputDir) {
        logger.info(">>> STEP 4: Generazione Grafici (Python)");
        GraphGenerationService graphService = new GraphGenerationService();
        graphService.generateGraphs(inputCsvPath, outputDir);
    }

    private static void runFinalTraining(String inputCsvPath, String projectKey) {
        logger.info(">>> STEP: Training Modello Finale (Produzione)");
        FinalModelTrainingController finalController = new FinalModelTrainingController(inputCsvPath, projectKey);
        finalController.trainAndSaveModel();
    }

    private static void runDatasetVariantsCreation(String inputCsvPath, String projectKey) {
        logger.info(">>> STEP: Creazione Varianti Dataset (B+, B, C)");
        DatasetVariantController variantController = new DatasetVariantController(inputCsvPath, projectKey);
        variantController.createVariants();
    }

    private static void runImpactAnalysis(String projectKey) {
        logger.info(">>> STEP: Analisi Impatto Refactoring (Tabella Risultati)");
        WhatIfController controller = new WhatIfController(projectKey);
        controller.runAnalysis();
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

    private static void runRefactoringExperiment(String projectKey) {
        logger.info(">>> STEP: Refactoring Experiment (Analisi Post-Modifica)");

        // 1. Setup Servizi
        String gitRepoPath = AppConfig.get("git.repoPath");
        GitService gitService = new GitService(new JGitClient(gitRepoPath));
        StaticAnalysisService staticService = new StaticAnalysisService(gitService);
        RefactoringController refactoringCtrl = new RefactoringController(staticService);

        // 2. Recupero percorsi
        String basePath = AppConfig.getProperty("output.base.path", "./results");
        String datasetPath = basePath + "/dataset/" + projectKey + DATASET_SUFFIX;

        String originalFile = AppConfig.getRefactoringOriginalFile();
        String refactoredFile = AppConfig.getRefactoringRefactoredFile();
        String targetSig = AppConfig.getRefactoringTargetSignature();
        String outputCsv = AppConfig.getRefactoringOutputFullCsv();

        // 3. Creazione cartella dedicata per l'esperimento
        try {
            Path outputDir = Paths.get(AppConfig.getRefactoringOutputDir());
            Files.createDirectories(outputDir);

            // 4. Generazione CSV Tecnico (Simulazione)
            logger.info("Target Method: {}", targetSig);
            refactoringCtrl.generateRefactoringCsv(
                    datasetPath,
                    outputCsv,
                    originalFile,
                    refactoredFile,
                    targetSig
            );
            logger.info("Esperimento completato. CSV tecnico generato in: {}", outputCsv);

            // =========================================================
            // 5. ESECUZIONE PREDIZIONE (RISCHIO BUG)
            // =========================================================

            // Costruzione nome modello dinamico
            String clfName = AppConfig.getProperty("final.model.classifier", "RandomForest");
            String smpName = AppConfig.getProperty("final.model.sampling", "NoSampling");
            String fsName = AppConfig.getProperty("final.model.feature_selection", "NoSelection");

            String modelNameBase = String.format("%s_%s_%s_%s", projectKey, clfName, smpName, fsName);
            String modelPath = basePath + "/models/" + modelNameBase + ".model";

            if (new File(modelPath).exists()) {
                logger.info("Modello trovato: {}", modelNameBase);

                // A. Calcolo Predizioni
                PredictionService predictor = new PredictionService();
                List<PredictionService.PredictionResult> results = predictor.predictDataset(modelPath, outputCsv);

                // B. Stampa a Video (Console)
                System.out.println("\n======================================================================================");
                System.out.printf("%-80s | %-10s | %s%n", "METODO", "RISCHIO", "PROB. BUG");
                System.out.println("--------------------------------------------------------------------------------------");
                for (PredictionService.PredictionResult res : results) {
                    String risk = (res.bugProbability() > 0.5) ? "ALTO ⚠️" : "BASSO ✅";
                    String displaySig = res.signature();
                    if (displaySig.length() > 75) displaySig = "..." + displaySig.substring(displaySig.length() - 72);
                    System.out.printf("%-80s | %-10s | %6.2f%%%n", displaySig, risk, res.bugProbability() * 100);
                }
                System.out.println("======================================================================================\n");

                // C. SALVATAGGIO REPORT SU DISCO (Ecco la chiamata mancante!)
                refactoringCtrl.saveRefactoringReport(projectKey, results, basePath);

            } else {
                logger.warn("IMPOSSIBILE CALCOLARE IL RISCHIO: Modello non trovato in {}", modelPath);
            }

        } catch (Exception e) {
            logger.error("Errore durante l'esecuzione del Refactoring Experiment", e);
        }
    }

}
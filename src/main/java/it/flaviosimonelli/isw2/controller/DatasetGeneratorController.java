package it.flaviosimonelli.isw2.controller;

import it.flaviosimonelli.isw2.git.bean.GitCommit;
import it.flaviosimonelli.isw2.git.service.GitService;
import it.flaviosimonelli.isw2.jira.bean.JiraRelease;
import it.flaviosimonelli.isw2.jira.service.JiraService;
import it.flaviosimonelli.isw2.metrics.MetricsCalculator;
import it.flaviosimonelli.isw2.model.Method;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;

public class DatasetGeneratorController {

    private static final Logger logger = LoggerFactory.getLogger(DatasetGeneratorController.class);

    private final JiraService jiraService;
    private final GitService gitService;
    private final MetricsCalculator metricsCalculator;

    public DatasetGeneratorController(JiraService jiraService, GitService gitService) {
        this.jiraService = jiraService;
        this.gitService = gitService;
        // Il calculator istanzia internamente la catena di metriche (LOC, Complexity, etc.)
        this.metricsCalculator = new MetricsCalculator();
    }

    public void createDataset(String projectKey, String outputCsvPath) {
        logger.info("Inizio generazione dataset per {}", projectKey);
        // 1. Recupero informazioni da Jira
        List<JiraRelease> releases = jiraService.getReleases(projectKey);

        try (PrintWriter writer = new PrintWriter(new FileWriter(outputCsvPath))) {
            // 1. Scriviamo l'header del file csv in base alle metriche descritte nel metricsCalculator
            writer.println(metricsCalculator.getCsvHeader());
            // 2. Loop sulle Release
            for (JiraRelease release : releases) {
                logger.info("Analisi release: {}", release.getName());
                // 3. Otteniamo lo snapshot (Ultimo commit della release)
                GitCommit snapshot = gitService.getLastCommitOnOrBeforeDate(release.getReleaseDate());
                if (snapshot == null) {
                    logger.warn("Nessun commit trovato per la release {}. Skipping.", release.getName());
                    continue;
                }
                // 4. Otteniamo TUTTI i file Java presenti in quel momento storico
                List<String> javaFiles = gitService.getAllJavaFiles(snapshot);
                logger.debug("Trovati {} file Java nella release {}", javaFiles.size(), release.getName());
                // 5. Loop sui File (Classi)
                for (String filePath : javaFiles) {
                    // Scarica il contenuto del file al momento della release
                    String sourceCode = gitService.getRawFileContent(snapshot, filePath);
                    if (sourceCode == null || sourceCode.isEmpty()) continue;
                    // 6. Estrazione Metriche (Statiche) tramite JavaParser
                    // Restituisce una lista di Method (contenitore dinamico map-based)
                    List<Method> methods = metricsCalculator.extractMetrics(sourceCode, filePath);
                    // 7. Loop sui Metodi e Scrittura
                    for (Method method : methods) {
                        // TODO: Implementare logica "Buggy" (Labeling SZZ)
                        // Questo sarà il prossimo step. Per ora placeholder "No".
                        String isBuggy = "No";

                        // SCRITTURA RIGA CSV
                        // method.toCSV() restituisce la stringa formattata: "NomeClasse,"Signature",Metric1,Metric2..."
                        // Noi aggiungiamo il contesto (Versione) all'inizio e il label (Buggy) alla fine.
                        writer.println(
                                release.getName() + "," +
                                        method.toCSV() + "," +
                                        isBuggy
                        );
                    }
                }
            }
            logger.info("Dataset creato con successo: {}", outputCsvPath);

        } catch (IOException e) {
            logger.error("Errore critico durante la scrittura del CSV", e);
        }
    }
}
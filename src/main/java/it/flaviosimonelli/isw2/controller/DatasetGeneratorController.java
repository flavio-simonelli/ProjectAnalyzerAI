package it.flaviosimonelli.isw2.controller;

import it.flaviosimonelli.isw2.git.bean.GitCommit;
import it.flaviosimonelli.isw2.git.service.GitService;
import it.flaviosimonelli.isw2.jira.bean.JiraRelease;
import it.flaviosimonelli.isw2.jira.service.JiraService;
import it.flaviosimonelli.isw2.metrics.StaticAnalysisService;
import it.flaviosimonelli.isw2.metrics.process.ProcessMetricAnalyzer;
import it.flaviosimonelli.isw2.model.MethodIdentity;
import it.flaviosimonelli.isw2.model.MethodProcessMetrics;
import it.flaviosimonelli.isw2.model.MethodStaticMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DatasetGeneratorController {

    private static final Logger logger = LoggerFactory.getLogger(DatasetGeneratorController.class);

    private final JiraService jiraService;
    private final GitService gitService;
    private final StaticAnalysisService staticService;
    private final ProcessMetricAnalyzer processAnalyzer;

    public DatasetGeneratorController(JiraService jiraService, GitService gitService) {
        this.jiraService = jiraService;
        this.gitService = gitService;
        this.staticService = new StaticAnalysisService(gitService);
        this.processAnalyzer = new ProcessMetricAnalyzer(gitService);
    }

    public void createDataset(String projectKey, String outputCsvPath) {
        logger.info("Inizio generazione dataset per {}", projectKey);

        // 1. Recupero informazioni release da Jira
        List<JiraRelease> releases = jiraService.getReleases(projectKey);
        // DEBUG 1: Controllo Release
        logger.debug("Trovate {} release", releases.size());

        // REGISTRO GLOBALE (Accumulatore per Total Revisions, Total Churn, ecc.)
        // Sopravvive attraverso le iterazioni del loop releases
        Map<MethodIdentity, MethodProcessMetrics> globalProcessMap = new HashMap<>();

        try (PrintWriter writer = new PrintWriter(new FileWriter(outputCsvPath))) {

            // 1. HEADER CSV
            String fixedHeader = "Version,File,Class,Signature";
            String staticHeader = staticService.getCsvHeader();
            String processIntervalHeader = processAnalyzer.getCsvHeader();
            String processGlobalHeader = processAnalyzer.getGlobalCsvHeader();
            String labelHeader = "Buggy";

            writer.println(fixedHeader + "," +
                    staticHeader + "," +
                    processIntervalHeader + "," +
                    processGlobalHeader + "," +
                    labelHeader);

            // Variabile per tracciare l'inizio della finestra temporale
            JiraRelease prevRelease = null;

            // 2. LOOP SULLE RELEASE
            for (JiraRelease release : releases) {
                logger.info("Processing Release: {} ({})", release.getName(), release.getReleaseDate());

                // --- FASE A: ANALISI STORICA (Process Metrics) ---
                // Calcoliamo cosa è successo TRA la release precedente e questa
                LocalDate startDate = (prevRelease != null) ? prevRelease.getReleaseDate() : null;
                LocalDate endDate = release.getReleaseDate();

                // DEBUG 2: Controllo Date
                logger.debug("Finestra temporale {} -> {}", startDate, endDate);

                // Recuperiamo i commit dell'intervallo
                List<GitCommit> historyCommits = gitService.getCommitsBetweenDates(startDate, endDate);
                // DEBUG 3: Controllo Commit
                logger.debug("Trovati {} commit storici per la release {}", historyCommits.size(), release.getName());

                // 1. Calcolo Metriche Locali (Intervallo)
                Map<MethodIdentity, MethodProcessMetrics> intervalProcessMap = processAnalyzer.extractProcessMetrics(historyCommits);

                // 2. Aggiornamento Metriche Globali (Merge)
                processAnalyzer.mergeToGlobal(globalProcessMap, intervalProcessMap);


                // --- FASE B: Recupero Snapshot (Static Metrics) ---
                // OTTIMIZZAZIONE: Se ho dei commit, l'ultimo è lo snapshot. Altrimenti interrogo Git.
                GitCommit snapshot;
                if (!historyCommits.isEmpty()) {
                    snapshot = historyCommits.getFirst();
                } else {
                    snapshot = gitService.getLastCommitOnOrBeforeDate(endDate);
                }

                if (snapshot == null) {
                    logger.warn("Skipping release {}: nessun commit valido trovato.", release.getName());
                    continue;
                }

                // 3. Analisi Statica dell'intero progetto
                // Questa chiamata singola sostituisce tutto il loop manuale sui file.
                // Ritorna una mappa completa di TUTTI i metodi esistenti nel progetto in quel momento.
                Map<MethodIdentity, MethodStaticMetrics> projectStaticMap = staticService.analyzeRelease(snapshot);

                if (projectStaticMap.isEmpty()) {
                    logger.error("ERRORE GRAVE: L'analisi statica non ha prodotto risultati. Controllare StaticAnalysisService o il parsing.");
                }

                // --- FASE C: UNIONE (JOIN) E SCRITTURA ---
                // Iteriamo sulla mappa STATICA perché il dataset deve contenere le righe dei metodi ESISTENTI nello snapshot.
                for (Map.Entry<MethodIdentity, MethodStaticMetrics> entry : projectStaticMap.entrySet()) {

                    MethodIdentity id = entry.getKey();
                    MethodStaticMetrics staticData = entry.getValue();

                    // LOOKUP EFFICIENTE O(1)
                    // Grazie a MethodIdentity, peschiamo i dati storici corrispondenti
                    MethodProcessMetrics intervalData = intervalProcessMap.get(id); // Può essere null
                    MethodProcessMetrics globalData = globalProcessMap.get(id);     // Può essere null

                    // TODO: Integrazione SZZ
                    String isBuggy = "No";

                    // Costruzione Riga
                    String sb = release.getName() + "," +
                            entry.getKey().getClassName() + ".java" + "," + // File name approssimato o passato nei metadata
                            id.getClassName() + "," +
                            "\"" + id.getFullSignature() + "\"," +

                            // Valori Statici (Delegati al service per formattazione e ordine)
                            staticService.getCsvValues(staticData) + "," +

                            // Valori Processo Intervallo (L'analyzer gestisce i null ritornando default values)
                            processAnalyzer.getCsvValues(intervalData) + "," +

                            // Valori Processo Globali (L'analyzer gestisce i null)
                            processAnalyzer.getCsvValues(globalData) + "," +

                            // Label
                            isBuggy;

                    writer.println(sb);
                }

                // DEBUG 6: Flush per sicurezza
                writer.flush();

                // Setup per la prossima iterazione
                prevRelease = release;

            }

            // DEBUG 6: Flush per sicurezza
            writer.flush();

            logger.info("Dataset generato con successo: {}", outputCsvPath);

        } catch (IOException e) {
            logger.error("Errore critico durante la scrittura del dataset", e);
            throw new RuntimeException("Creazione dataset fallita", e);
        }
    }

}
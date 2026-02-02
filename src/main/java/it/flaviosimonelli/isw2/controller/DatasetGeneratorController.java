package it.flaviosimonelli.isw2.controller;

import it.flaviosimonelli.isw2.git.bean.GitCommit;
import it.flaviosimonelli.isw2.git.service.GitService;
import it.flaviosimonelli.isw2.jira.bean.JiraRelease;
import it.flaviosimonelli.isw2.jira.bean.JiraTicket;
import it.flaviosimonelli.isw2.jira.service.JiraService;
import it.flaviosimonelli.isw2.metrics.StaticAnalysisService;
import it.flaviosimonelli.isw2.metrics.process.ProcessMetricAnalyzer;
import it.flaviosimonelli.isw2.model.MethodIdentity;
import it.flaviosimonelli.isw2.model.MethodProcessMetrics;
import it.flaviosimonelli.isw2.model.MethodStaticMetrics;
import it.flaviosimonelli.isw2.snoring.SnoringControlService;
import it.flaviosimonelli.isw2.szz.SZZService;
import it.flaviosimonelli.isw2.config.ProjectConstants;
import it.flaviosimonelli.isw2.util.CsvUtils;
import org.apache.commons.csv.CSVPrinter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.LocalDate;
import java.util.*;

public class DatasetGeneratorController {

    private static final Logger logger = LoggerFactory.getLogger(DatasetGeneratorController.class);

    private final JiraService jiraService;
    private final GitService gitService;
    private final StaticAnalysisService staticService;
    private final ProcessMetricAnalyzer processAnalyzer;

    private static final String SEP = "===============================================================";

    public DatasetGeneratorController(JiraService jiraService, GitService gitService) {
        this.jiraService = jiraService;
        this.gitService = gitService;
        this.staticService = new StaticAnalysisService(gitService);
        this.processAnalyzer = new ProcessMetricAnalyzer(gitService);
    }

    public void createDataset(String projectKey, String outputCsvPath) {
        logger.info("Inizio generazione dataset per {}", projectKey);

        // Recupero informazioni release da Jira
        List<JiraRelease> releases = jiraService.getReleases(projectKey);
        releases.sort(Comparator.comparing(JiraRelease::getReleaseDate));
        logger.info("Trovate {} release in Jira", releases.size());
        // Recupero informazioni ticket da Jira
        List<JiraTicket> tickets = jiraService.getTickets(projectKey);
        logger.info("Trovati {} ticket fixed in Jira", tickets.size());

        // INIZIALIZZAZIONE LOGICA SNORIN
        SnoringControlService snoringService = new SnoringControlService(releases);

        // ESECUZIONE SZZ (Labeling)
        // Calcoliamo ORA quali metodi sono buggati in quali versioni.
        // Lo facciamo fuori dal loop principale per performance (lo calcoliamo una volta sola).
        SZZService szzService = new SZZService(gitService, releases);
        Map<String, Set<MethodIdentity>> buggyRegistry = szzService.getBuggyMethodsPerRelease(tickets);


        // REGISTRO GLOBALE (Accumulatore per Total Revisions, Total Churn, ecc.)
        // Sopravvive attraverso le iterazioni del loop releases
        Map<MethodIdentity, MethodProcessMetrics> globalProcessMap = new HashMap<>();

        // 2. PREPARAZIONE HEADER
        // Costruiamo una lista ordinata di intestazioni
        List<String> headers = new ArrayList<>();
        headers.add(ProjectConstants.VERSION_ATTRIBUTE);
        headers.add(ProjectConstants.RELEASE_INDEX_ATTRIBUTE);
        headers.add(ProjectConstants.DATA_ATTRIBUTE);
        headers.add("File");
        headers.add("Class");
        headers.add("Signature");
        headers.addAll(staticService.getHeaderList());       // Es: ["LOC", "NAuth", ...]
        headers.addAll(processAnalyzer.getHeaderList());     // Es: ["Churn", "Revision", ...]
        headers.addAll(processAnalyzer.getGlobalHeaderList());
        headers.add(ProjectConstants.TARGET_CLASS);

        long totalRowsWritten = 0;
        long totalBuggyRowsWritten = 0;

        try (CSVPrinter printer = CsvUtils.createPrinter(outputCsvPath, false, headers.toArray(new String[0]))) {

            // Variabile per tracciare l'inizio della finestra temporale
            JiraRelease prevRelease = null;

            // 2. LOOP SULLE RELEASE
            for (int i = 0; i < releases.size(); i++) {
                // controllo se dobbiamo fermarci per via dello snoring
                if (snoringService.shouldStopProcessingReleases(i)) {
                    logger.info("Snoring Cutoff triggered. Stopping loop.");
                    break;
                }

                JiraRelease release = releases.get(i);
                boolean isSnoring = snoringService.isSnoringZone(i);

                logger.info("Processing Release {}/{} : {} {}",
                        (i+1), releases.size(), release.getName(),
                        (isSnoring ? "[SNORING ZONE]" : "[SAFE ZONE]"));

                // --- FASE A: ANALISI STORICA (Process Metrics) ---
                // Calcoliamo cosa è successo TRA la release precedente e questa
                LocalDate startDate = (prevRelease != null) ? prevRelease.getReleaseDate() : null;
                LocalDate endDate = release.getReleaseDate();
                int releaseIndex = i + 1;
                String releaseDateStr = release.getReleaseDate().toString();

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
                GitCommit snapshot = (!historyCommits.isEmpty()) ? historyCommits.getFirst() : gitService.getLastCommitOnOrBeforeDate(endDate);

                if (snapshot == null) {
                    logger.warn("Skipping release {}: nessun commit valido trovato.", release.getName());
                    prevRelease = release; // QUESTO SIAMO SICURI CHE CI VA?
                    continue;
                }

                // 3. Analisi Statica dell'intero progetto
                // Questa chiamata singola sostituisce tutto il loop manuale sui file.
                // Ritorna una mappa completa di TUTTI i metodi esistenti nel progetto in quel momento.
                Map<MethodIdentity, MethodStaticMetrics> projectStaticMap = staticService.analyzeRelease(snapshot);

                if (projectStaticMap.isEmpty()) {
                    logger.error("ERRORE GRAVE: L'analisi statica non ha prodotto risultati. Controllare StaticAnalysisService o il parsing.");
                }

                // Recuperiamo il Set dei metodi buggati per QUESTA release specifica
                Set<MethodIdentity> buggyInThisRelease = buggyRegistry.get(release.getName());

                // Debug per release
                int buggyInReleaseCount = 0;

                // --- FASE C: UNIONE (JOIN) E SCRITTURA ---
                // Iteriamo sulla mappa STATICA perché il dataset deve contenere le righe dei metodi ESISTENTI nello snapshot.
                for (Map.Entry<MethodIdentity, MethodStaticMetrics> entry : projectStaticMap.entrySet()) {

                    MethodIdentity id = entry.getKey();

                    boolean isBuggyBoolean = (buggyInThisRelease != null && buggyInThisRelease.contains(id));

                    // Controllo snoring (filtro righe)
                    if (!snoringService.shouldKeepRow(i, isBuggyBoolean)) {
                        continue; // Il service ha detto di scartarla
                    }

                    totalRowsWritten++;
                    if (isBuggyBoolean) {
                        totalBuggyRowsWritten++;
                        buggyInReleaseCount++;
                    }

                    // Costruzione Riga (Lista di Oggetti)
                    // CSVPrinter gestirà automaticamente la conversione in stringa e l'escape
                    List<Object> csvRow = new ArrayList<>();

                    // 1. Identificatori
                    csvRow.add(release.getName());
                    csvRow.add(releaseIndex);
                    csvRow.add(releaseDateStr);
                    csvRow.add(id.getClassName() + ".java");
                    csvRow.add(id.getClassName());
                    csvRow.add(id.getFullSignature()); // Commons CSV gestirà le virgole interne alla firma!

                    // 2. Metriche
                    // Metriche Statiche
                    csvRow.addAll(staticService.getValuesAsList(entry.getValue()));
                    // Valori Locali (Intervallo corrente)
                    csvRow.addAll(processAnalyzer.getLocalValues(intervalProcessMap.get(id)));
                    // Valori Globali (Storico accumulato)
                    csvRow.addAll(processAnalyzer.getGlobalValues(globalProcessMap.get(id)));

                    // 3. Label
                    csvRow.add(isBuggyBoolean ? ProjectConstants.BUGGY_LABEL : ProjectConstants.CLEAN_LABEL);

                    // Scrittura fisica
                    printer.printRecord(csvRow);
                }

                printer.flush();
                // Setup per la prossima iterazione
                prevRelease = release;
            }

            snoringService.printFinalReport(outputCsvPath);
            logDatasetReport(outputCsvPath, totalRowsWritten, totalBuggyRowsWritten);

        } catch (IOException e) {
            logger.error("Errore critico durante la scrittura del dataset", e);
            throw new RuntimeException("Creazione dataset fallita", e);
        }
    }


    /**
     * Funzione Helper per la stampa del report finale.
     */
    private void logDatasetReport(String path, long total, long buggy) {
        double perc = (total > 0) ? (double) buggy / total * 100.0 : 0.0;

        String report = """
            %s
                        DATASET GENERATION REPORT
            %s
            File Output: %s
            Totale Righe Scritte: %d
            Totale Righe BUGGY (Class 'Yes'): %d
            Percentuale Bugginess nel Dataset: %.2f%%
            %s
            """.formatted(SEP, SEP, path, total, buggy, perc, SEP);

        logger.info("\n{}", report);
    }

}
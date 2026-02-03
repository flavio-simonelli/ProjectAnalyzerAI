package it.flaviosimonelli.isw2.controller;

import it.flaviosimonelli.isw2.exception.DatasetGenerationException;
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

        // 1. Inizializzazione dati Jira e SZZ
        List<JiraRelease> releases = getSortedReleases(projectKey);
        List<JiraTicket> tickets = jiraService.getTickets(projectKey);
        Map<String, Set<MethodIdentity>> buggyRegistry = new SZZService(gitService, releases).getBuggyMethodsPerRelease(tickets);
        SnoringControlService snoringService = new SnoringControlService(releases);

        // 2. Preparazione accumulatore e headers
        Map<MethodIdentity, MethodProcessMetrics> globalProcessMap = new HashMap<>();
        List<String> headers = buildHeaders();

        // 3. Loop di processamento release
        processReleases(releases, buggyRegistry, snoringService, globalProcessMap, outputCsvPath, headers);
    }

    private void processReleases(List<JiraRelease> releases, Map<String, Set<MethodIdentity>> buggyRegistry,
                                 SnoringControlService snoring, Map<MethodIdentity, MethodProcessMetrics> globalProcessMap,
                                 String outputPath, List<String> headers) {

        long[] stats = {0, 0}; // [totalRows, buggyRows]

        try (CSVPrinter printer = CsvUtils.createPrinter(outputPath, false, headers.toArray(new String[0]))) {
            JiraRelease prevRelease = null;

            for (int i = 0; i < releases.size() && !snoring.shouldStopProcessingReleases(i); i++) {
                JiraRelease current = releases.get(i);

                // 1. Analisi storica: consumiamo i commit di questa release
                Map<MethodIdentity, MethodProcessMetrics> intervalMap = performProcessAnalysis(prevRelease, current, globalProcessMap);

                // 2. Analisi statica: scattiamo la foto al codice
                Map<MethodIdentity, MethodStaticMetrics> staticMap = performStaticAnalysis(current);

                // 3. Scrittura: solo se abbiamo dati per popolare il CSV
                if (!staticMap.isEmpty()) {
                    WriteContext ctx = new WriteContext(printer, i, current, intervalMap, globalProcessMap, buggyRegistry, snoring, stats);
                    writeReleaseRows(ctx, staticMap);
                    printer.flush();
                }

                // 4. Update: passiamo alla prossima finestra temporale.
                // Fondamentale per non processare due volte gli stessi commit!
                prevRelease = current;
            }

            snoring.printFinalReport(outputPath);
            logDatasetReport(outputPath, stats[0], stats[1]);

        } catch (IOException e) {
            throw new DatasetGenerationException("Creazione dataset fallita", e);
        }
    }

    private Map<MethodIdentity, MethodProcessMetrics> performProcessAnalysis(JiraRelease prev, JiraRelease current,
                                                                             Map<MethodIdentity, MethodProcessMetrics> global) {
        LocalDate start = (prev != null) ? prev.getReleaseDate() : null;
        List<GitCommit> commits = gitService.getCommitsBetweenDates(start, current.getReleaseDate());

        Map<MethodIdentity, MethodProcessMetrics> intervalMap = processAnalyzer.extractProcessMetrics(commits);
        processAnalyzer.mergeToGlobal(global, intervalMap);
        return intervalMap;
    }

    private Map<MethodIdentity, MethodStaticMetrics> performStaticAnalysis(JiraRelease release) {
        GitCommit snapshot = gitService.getLastCommitOnOrBeforeDate(release.getReleaseDate());
        if (snapshot == null) {
            logger.warn("Nessun snapshot per la release {}", release.getName());
            return Collections.emptyMap();
        }
        return staticService.analyzeRelease(snapshot);
    }

    /**
     * Scrive le righe sul CSV. Parametri ridotti drasticamente.
     */
    private void writeReleaseRows(WriteContext ctx, Map<MethodIdentity, MethodStaticMetrics> staticMap) throws IOException {

        Set<MethodIdentity> buggyInThisRelease = ctx.buggyRegistry()
                .getOrDefault(ctx.release().getName(), Collections.emptySet());

        for (var entry : staticMap.entrySet()) {
            MethodIdentity id = entry.getKey();
            boolean isBuggy = buggyInThisRelease.contains(id);

            // Verifichiamo se la riga va mantenuta (Snoring Control)
            if (ctx.snoring().shouldKeepRow(ctx.releaseIdx(), isBuggy)) {
                writeSingleRow(ctx, id, entry.getValue(), isBuggy);
            }
        }
    }

    /**
     * Helper per la costruzione fisica della riga.
     */
    private void writeSingleRow(WriteContext ctx, MethodIdentity id, MethodStaticMetrics staticMetrics, boolean isBuggy) throws IOException {
        List<Object> row = new ArrayList<>();

        // 1. Metadati
        row.add(ctx.release().getName());
        row.add(ctx.releaseIdx() + 1);
        row.add(ctx.release().getReleaseDate().toString());
        row.add(id.className() + ".java");
        row.add(id.className());
        row.add(id.fullSignature());

        // 2. Metriche
        row.addAll(staticService.getValuesAsList(staticMetrics));
        row.addAll(processAnalyzer.getLocalValues(ctx.intervalMap().get(id)));
        row.addAll(processAnalyzer.getGlobalValues(ctx.globalHistory().get(id)));

        // 3. Label
        row.add(isBuggy ? ProjectConstants.BUGGY_LABEL : ProjectConstants.CLEAN_LABEL);

        ctx.printer().printRecord(row);

        // Aggiornamento statistiche
        ctx.stats()[0]++;
        if (isBuggy) ctx.stats()[1]++;
    }

    private List<String> buildHeaders() {
        List<String> h = new ArrayList<>(List.of(
                ProjectConstants.VERSION_ATTRIBUTE, ProjectConstants.RELEASE_INDEX_ATTRIBUTE,
                ProjectConstants.DATA_ATTRIBUTE, "File", "Class", "Signature"
        ));
        h.addAll(staticService.getHeaderList());
        h.addAll(processAnalyzer.getHeaderList());
        h.addAll(processAnalyzer.getGlobalHeaderList());
        h.add(ProjectConstants.TARGET_CLASS);
        return h;
    }

    private List<JiraRelease> getSortedReleases(String projectKey) {
        List<JiraRelease> releases = jiraService.getReleases(projectKey);
        releases.sort(Comparator.comparing(JiraRelease::getReleaseDate));
        return releases;
    }

    /**
     * Raggruppa i dati necessari alla scrittura
     */
    private record WriteContext(
            CSVPrinter printer,
            int releaseIdx,
            JiraRelease release,
            Map<MethodIdentity, MethodProcessMetrics> intervalMap,
            Map<MethodIdentity, MethodProcessMetrics> globalHistory,
            Map<String, Set<MethodIdentity>> buggyRegistry,
            SnoringControlService snoring,
            long[] stats
    ) {}

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
package it.flaviosimonelli.isw2.controller;

import it.flaviosimonelli.isw2.metrics.StaticAnalysisService;
import it.flaviosimonelli.isw2.ml.prediction.PredictionService.PredictionResult;
import it.flaviosimonelli.isw2.model.MethodStaticMetrics;
import it.flaviosimonelli.isw2.util.CsvUtils;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

/**
 * Controller dedicato all'esperimento di Refactoring Simulato (What-If Analysis).
 * Gestisce il confronto tra file originale e rifattorizzato, calcola i delta delle metriche
 * (Added/Deleted) e prepara i dati per la predizione del rischio.
 */
public class RefactoringController {

    private static final Logger logger = LoggerFactory.getLogger(RefactoringController.class);
    private final StaticAnalysisService staticService;

    public RefactoringController(StaticAnalysisService staticService) {
        this.staticService = staticService;
    }

    // =========================================================================
    // STEP 1: GENERAZIONE CSV PER WEKA
    // =========================================================================

    public void generateRefactoringCsv(String originalDatasetPath, String outputCsvPath,
                                       String originalFile, String refactoredFile,
                                       String targetMethodSignature) {
        logger.info(">>> Avvio Simulazione Refactoring (What-If Analysis)...");

        try {
            List<String> headers = CsvUtils.getHeaders(originalDatasetPath);
            CSVRecord historicalRecord = findRecordBySignature(originalDatasetPath, targetMethodSignature);

            if (historicalRecord == null) {
                logger.error("ERRORE CRITICO: Metodo '{}' non trovato nel dataset storico.", targetMethodSignature);
                return;
            }

            Map<String, MethodStaticMetrics> oldFileMetrics = staticService.analyzeLocalFile(new File(originalFile));
            Map<String, MethodStaticMetrics> newFileMetrics = staticService.analyzeLocalFile(new File(refactoredFile));

            try (CSVPrinter printer = CsvUtils.createPrinter(outputCsvPath, false, headers.toArray(new String[0]))) {

                for (Map.Entry<String, MethodStaticMetrics> entry : newFileMetrics.entrySet()) {
                    String newSig = entry.getKey();
                    MethodStaticMetrics newStats = entry.getValue();
                    Map<String, String> rowData;

                    if (oldFileMetrics.containsKey(newSig)) {
                        // CASO A: Il metodo esisteva già (es. main modificato)
                        double oldLoc = getVal(oldFileMetrics.get(newSig), "LOC");
                        double newLoc = getVal(newStats, "LOC");

                        // Calcolo Delta Reale (con segno)
                        double diff = newLoc - oldLoc;

                        // Se non è cambiato nulla, saltiamo (Filtro Anti-Rumore)
                        if (diff == 0.0) {
                            continue;
                        }

                        // Distinguiamo tra Added e Deleted
                        double addedCurrent = (diff > 0) ? diff : 0;
                        double deletedCurrent = (diff < 0) ? Math.abs(diff) : 0;

                        rowData = buildModifiedRow(headers, historicalRecord, newStats, addedCurrent, deletedCurrent);
                        logger.info("Target Modificato: {} | Added: {} | Deleted: {}", newSig, addedCurrent, deletedCurrent);

                    } else {
                        // CASO B: Nuovo metodo estratto
                        rowData = buildNewRow(headers, historicalRecord, newStats);
                        logger.info("Nuovo Metodo Estratto: {}", newSig);
                    }

                    rowData.put("Signature", newSig);
                    printer.printRecord(mapToRecord(headers, rowData));
                }
            }
            logger.info("CSV tecnico per Weka generato con successo: {}", outputCsvPath);

        } catch (Exception e) {
            logger.error("Errore durante la generazione del CSV Refactoring", e);
        }
    }

    // =========================================================================
    // STEP 2: SALVATAGGIO REPORT FINALE
    // =========================================================================

    public void saveRefactoringReport(String projectKey, List<PredictionResult> results, String outputBase) {
        String reportDir = outputBase + "/reports";
        new File(reportDir).mkdirs();
        String filePath = Paths.get(reportDir, projectKey + "_Refactoring_Experiment_Report.csv").toString();

        logger.info("Salvataggio Report Finale in: {}", filePath);

        List<String> headers = Arrays.asList("Method_Signature", "Risk_Level", "Bug_Probability");
        List<List<String>> rows = new ArrayList<>();

        for (PredictionResult res : results) {
            String risk = (res.bugProbability() > 0.5) ? "HIGH" : "LOW";
            String probString = String.format(Locale.US, "%.2f%%", res.bugProbability() * 100);
            rows.add(Arrays.asList(res.signature(), risk, probString));
        }

        CsvUtils.writeCsv(filePath, headers, rows);
    }

    // =========================================================================
    // LOGICA DI BUILD DELLE RIGHE (Simulazione Commit)
    // =========================================================================

    private Map<String, String> buildModifiedRow(List<String> headers, CSVRecord history,
                                                 MethodStaticMetrics stats,
                                                 double addedCurrent, double deletedCurrent) {
        // 1. Copia storia
        Map<String, String> row = new HashMap<>(history.toMap());

        // 2. Aggiorna metriche statiche
        updateStaticFields(row, stats);

        // 3. Aggiorna metriche di processo (Added vs Deleted)
        updateProcessMetrics(row, addedCurrent, deletedCurrent, false); // Local
        updateProcessMetrics(row, addedCurrent, deletedCurrent, true);  // Global

        setTargetUnknown(row);
        return row;
    }

    private Map<String, String> buildNewRow(List<String> headers, CSVRecord context,
                                            MethodStaticMetrics stats) {
        Map<String, String> row = new HashMap<>();

        for (String h : headers) {
            if (isMetadata(h)) row.put(h, context.get(h));
            else row.put(h, "0");
        }

        updateStaticFields(row, stats);

        // Per un nuovo metodo, tutto è "Added"
        double size = getVal(stats, "LOC");
        initializeProcessMetrics(row, size);

        setTargetUnknown(row);
        return row;
    }

    // --- Calcoli Matematici Process Metrics ---

    private void updateProcessMetrics(Map<String, String> row, double added, double deleted, boolean isGlobal) {
        String prefix = isGlobal ? "Global_" : "";

        // 1. Recupero valori storici
        double nr = getDouble(row, prefix + "NR");

        // Added Stats
        double locAdded = getDouble(row, prefix + "LOC_Added");
        double maxLocAdded = getDouble(row, prefix + "MAX_LOC_Added");

        // Deleted Stats
        double locDeleted = getDouble(row, prefix + "LOC_Deleted");
        double maxLocDeleted = getDouble(row, prefix + "MAX_LOC_Deleted");

        // Churn Stats
        double churn = getDouble(row, prefix + "Churn");
        double maxChurn = getDouble(row, prefix + "MAX_Churn");

        // 2. Calcolo Nuovi Valori
        double currentChurn = added + deleted; // Churn di questo commit specifico
        double newNR = nr + 1;

        // Aggiornamento Totali
        double newChurnTotal = churn + currentChurn;
        double newLocAddedTotal = locAdded + added;
        double newLocDeletedTotal = locDeleted + deleted;

        // Aggiornamento MAX
        double newMaxChurn = Math.max(maxChurn, currentChurn);
        double newMaxLocAdded = Math.max(maxLocAdded, added);
        double newMaxLocDeleted = Math.max(maxLocDeleted, deleted);

        // Aggiornamento AVG (Media su NR aggiornato)
        double newAvgChurn = (newNR > 0) ? (newChurnTotal / newNR) : currentChurn;
        double newAvgLocAdded = (newNR > 0) ? (newLocAddedTotal / newNR) : added;
        double newAvgLocDeleted = (newNR > 0) ? (newLocDeletedTotal / newNR) : deleted;

        // 3. Scrittura
        row.put(prefix + "NR", String.valueOf(newNR));

        row.put(prefix + "Churn", String.valueOf(newChurnTotal));
        row.put(prefix + "MAX_Churn", String.valueOf(newMaxChurn));
        row.put(prefix + "AVG_Churn", String.valueOf(newAvgChurn));

        row.put(prefix + "LOC_Added", String.valueOf(newLocAddedTotal));
        row.put(prefix + "MAX_LOC_Added", String.valueOf(newMaxLocAdded));
        row.put(prefix + "AVG_LOC_Added", String.valueOf(newAvgLocAdded));

        row.put(prefix + "LOC_Deleted", String.valueOf(newLocDeletedTotal));
        row.put(prefix + "MAX_LOC_Deleted", String.valueOf(newMaxLocDeleted));
        row.put(prefix + "AVG_LOC_Deleted", String.valueOf(newAvgLocDeleted));
    }

    private void initializeProcessMetrics(Map<String, String> row, double size) {
        String sSize = String.valueOf(size);
        String[] prefixes = {"", "Global_"};

        for (String prefix : prefixes) {
            row.put(prefix + "NR", "1");
            row.put(prefix + "NAuth", "1");

            // Tutto conta come Added e Churn
            row.put(prefix + "Churn", sSize);
            row.put(prefix + "MAX_Churn", sSize);
            row.put(prefix + "AVG_Churn", sSize);

            row.put(prefix + "LOC_Added", sSize);
            row.put(prefix + "MAX_LOC_Added", sSize);
            row.put(prefix + "AVG_LOC_Added", sSize);

            // Deleted è 0 per i nuovi file
            row.put(prefix + "LOC_Deleted", "0");
            row.put(prefix + "MAX_LOC_Deleted", "0");
            row.put(prefix + "AVG_LOC_Deleted", "0");
        }
    }

    private void updateStaticFields(Map<String, String> row, MethodStaticMetrics stats) {
        row.put("LOC", String.valueOf(getVal(stats, "LOC")));
        row.put("Cyclomatic", String.valueOf(getVal(stats, "Cyclomatic")));
        row.put("NumParams", String.valueOf(getVal(stats, "NumParams")));
        row.put("NumStatements", String.valueOf(getVal(stats, "NumStatements")));
        row.put("NumLocalVars", String.valueOf(getVal(stats, "NumLocalVars")));
        row.put("NestingDepth", String.valueOf(getVal(stats, "NestingDepth")));
        row.put("CognitiveComplexity", String.valueOf(getVal(stats, "CognitiveComplexity")));
        row.put("ReturnTypeComplexity", String.valueOf(getVal(stats, "ReturnTypeComplexity")));
        row.put("CodeSmells", String.valueOf(getVal(stats, "CodeSmells")));
    }

    // =========================================================================
    // UTILITIES
    // =========================================================================

    private void setTargetUnknown(Map<String, String> row) {
        if (row.containsKey("isBuggy")) row.put("isBuggy", "?");
        if (row.containsKey("Buggy")) row.put("Buggy", "?");
    }

    private double getVal(MethodStaticMetrics stats, String key) {
        if (stats == null) return 0.0;
        Double val = stats.getMetric(key);
        return (val != null) ? val : 0.0;
    }

    private double getDouble(Map<String, String> row, String key) {
        if (!row.containsKey(key)) return 0.0;
        try {
            return Double.parseDouble(row.get(key));
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    private boolean isMetadata(String header) {
        return List.of("Version", "ReleaseIndex", "ReleaseData", "Date", "File", "Class", "Signature", "Project").contains(header);
    }

    private Iterable<Object> mapToRecord(List<String> headers, Map<String, String> data) {
        List<Object> record = new ArrayList<>();
        for (String h : headers) {
            record.add(data.getOrDefault(h, "0"));
        }
        return record;
    }

    private CSVRecord findRecordBySignature(String path, String signature) throws IOException {
        try (Reader reader = Files.newBufferedReader(Paths.get(path));
             CSVParser parser = CSVFormat.DEFAULT.builder()
                     .setHeader()
                     .setSkipHeaderRecord(true)
                     .setIgnoreHeaderCase(true)
                     .setTrim(true)
                     .build()
                     .parse(reader)) {

            CSVRecord latestRecord = null;
            int maxReleaseIndex = Integer.MIN_VALUE;

            for (CSVRecord csvRecord : parser) {
                if (signature.equals(csvRecord.get("Signature"))) {
                    String releaseVal = csvRecord.get("ReleaseIndex");
                    if (releaseVal != null && !releaseVal.isEmpty()) {
                        try {
                            int currentIdx = Integer.parseInt(releaseVal);
                            if (currentIdx > maxReleaseIndex) {
                                maxReleaseIndex = currentIdx;
                                latestRecord = csvRecord;
                            }
                        } catch (NumberFormatException _) {
                            // ignore
                        }
                    }
                }
            }
            return latestRecord;
        }
    }
}
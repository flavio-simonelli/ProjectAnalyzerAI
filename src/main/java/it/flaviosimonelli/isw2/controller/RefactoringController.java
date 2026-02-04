package it.flaviosimonelli.isw2.controller;

import it.flaviosimonelli.isw2.metrics.StaticAnalysisService;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RefactoringController {

    private static final Logger logger = LoggerFactory.getLogger(RefactoringController.class);
    private final StaticAnalysisService staticService;

    public RefactoringController(StaticAnalysisService staticService) {
        this.staticService = staticService;
    }

    public void generateRefactoringCsv(String originalDatasetPath, String outputCsvPath,
                                       String originalFile, String refactoredFile,
                                       String targetMethodSignature) {
        logger.info(">>> Generazione CSV Refactoring Experiment (Versione Corretta)...");

        try {
            List<String> headers = CsvUtils.getHeaders(originalDatasetPath);
            CSVRecord historicalRecord = findRecordBySignature(originalDatasetPath, targetMethodSignature);

            if (historicalRecord == null) {
                logger.error("ERRORE: Metodo '{}' non trovato nel dataset.", targetMethodSignature);
                return;
            }

            Map<String, MethodStaticMetrics> oldFileMetrics = staticService.analyzeLocalFile(new File(originalFile));
            Map<String, MethodStaticMetrics> newFileMetrics = staticService.analyzeLocalFile(new File(refactoredFile));

            try (CSVPrinter printer = CsvUtils.createPrinter(outputCsvPath, false, headers.toArray(new String[0]))) {

                for (Map.Entry<String, MethodStaticMetrics> entry : newFileMetrics.entrySet()) {
                    String newSig = entry.getKey(); // Es: "reportResults(...)"
                    MethodStaticMetrics newStats = entry.getValue();
                    Map<String, String> rowData;

                    if (oldFileMetrics.containsKey(newSig)) {
                        // CASO A: Il metodo esisteva già (es. il main stesso, o metodi helper non toccati)
                        double oldLoc = getVal(oldFileMetrics.get(newSig), "LOC");
                        double newLoc = getVal(newStats, "LOC");
                        double churnDelta = Math.abs(newLoc - oldLoc);

                        rowData = buildModifiedRow(headers, historicalRecord, newStats, churnDelta);
                        logger.info("Modified: {} | DeltaLOC: {}", newSig, churnDelta);
                    } else {
                        // CASO B: Nuovo metodo estratto (es. setupTimeout)
                        rowData = buildNewRow(headers, historicalRecord, newStats);
                        logger.info("Extracted: {}", newSig);
                    }

                    // === FIX CRITICO: Sovrascriviamo la Signature! ===
                    // Prima copiavamo la signature del 'main' ovunque. Ora mettiamo quella vera.
                    rowData.put("Signature", newSig);

                    printer.printRecord(mapToRecord(headers, rowData));
                }
            }
            logger.info("CSV Refactoring generato correttamente in: {}", outputCsvPath);

        } catch (Exception e) {
            logger.error("Errore generazione CSV", e);
        }
    }

    // --- BUILDERS ---

    private Map<String, String> buildModifiedRow(List<String> headers, CSVRecord history,
                                                 MethodStaticMetrics stats, double delta) {
        // 1. Copia base storica (che contiene la Signature VECCHIA, ma la sovrascriveremo dopo)
        Map<String, String> row = new HashMap<>(history.toMap());

        // 2. Aggiorna Metriche Statiche
        updateStaticFields(row, stats);

        // 3. Aggiorna Metriche di Processo
        updateProcessMetrics(row, delta, false); // Local
        updateProcessMetrics(row, delta, true);  // Global

        // 4. Target
        if(row.containsKey("isBuggy")) row.put("isBuggy", "?");
        if(row.containsKey("Buggy")) row.put("Buggy", "?");

        return row;
    }

    private Map<String, String> buildNewRow(List<String> headers, CSVRecord context,
                                            MethodStaticMetrics stats) {
        Map<String, String> row = new HashMap<>();

        // 1. Copia metadati dal padre (inclusa la Signature vecchia, che sovrascriveremo)
        for (String h : headers) {
            if (isMetadata(h)) row.put(h, context.get(h));
            else row.put(h, "0");
        }

        // 2. Statiche
        updateStaticFields(row, stats);

        // 3. Processo (Nascita)
        double size = getVal(stats, "LOC");
        initializeProcessMetrics(row, size);

        // 4. Target
        if(row.containsKey("isBuggy")) row.put("isBuggy", "?");
        if(row.containsKey("Buggy")) row.put("Buggy", "?");

        return row;
    }

    // --- LOGICA MATEMATICA PROCESSO ---

    private void updateProcessMetrics(Map<String, String> row, double delta, boolean isGlobal) {
        String prefix = isGlobal ? "Global_" : "";

        String colNR = prefix + "NR";
        String colChurn = prefix + "Churn";
        String colMaxChurn = prefix + "MAX_Churn";
        String colAvgChurn = prefix + "AVG_Churn";

        String colLocAdded = prefix + "LOC_Added";
        String colMaxLocAdded = prefix + "MAX_LOC_Added";
        String colAvgLocAdded = prefix + "AVG_LOC_Added";

        double nr = getDouble(row, colNR);
        double churn = getDouble(row, colChurn);
        double maxChurn = getDouble(row, colMaxChurn);

        double locAdded = getDouble(row, colLocAdded);
        double maxLocAdded = getDouble(row, colMaxLocAdded);

        // Calcoli
        double newNR = nr + 1;
        double newChurn = churn + delta;
        double newMaxChurn = Math.max(maxChurn, delta);
        double newAvgChurn = (newNR > 0) ? (newChurn / newNR) : delta;

        double newLocAdded = locAdded + delta;
        double newMaxLocAdded = Math.max(maxLocAdded, delta);
        double newAvgLocAdded = (newNR > 0) ? (newLocAdded / newNR) : delta;

        // Salvataggio
        row.put(colNR, String.valueOf(newNR));
        row.put(colChurn, String.valueOf(newChurn));
        row.put(colMaxChurn, String.valueOf(newMaxChurn));
        row.put(colAvgChurn, String.valueOf(newAvgChurn));
        row.put(colLocAdded, String.valueOf(newLocAdded));
        row.put(colMaxLocAdded, String.valueOf(newMaxLocAdded));
        row.put(colAvgLocAdded, String.valueOf(newAvgLocAdded));
    }

    private void initializeProcessMetrics(Map<String, String> row, double size) {
        String sSize = String.valueOf(size);
        // Init Local
        row.put("NR", "1");
        row.put("NAuth", "1");
        row.put("Churn", sSize);
        row.put("MAX_Churn", sSize);
        row.put("AVG_Churn", sSize);
        row.put("LOC_Added", sSize);
        row.put("MAX_LOC_Added", sSize);
        row.put("AVG_LOC_Added", sSize);

        // Init Global
        row.put("Global_NR", "1");
        row.put("Global_NAuth", "1");
        row.put("Global_Churn", sSize);
        row.put("Global_MAX_Churn", sSize);
        row.put("Global_AVG_Churn", sSize);
        row.put("Global_LOC_Added", sSize);
        row.put("Global_MAX_LOC_Added", sSize);
        row.put("Global_AVG_LOC_Added", sSize);
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

    // --- UTILITIES ---

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
        // "Signature" è considerato metadati, quindi veniva copiato dal padre.
        // La sovrascrittura esplicita nel loop principale risolve il problema.
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

            for (CSVRecord record : parser) {
                // Controllo tollerante per evitare problemi di spazi o package
                String currentSig = record.get("Signature");
                if (signature.equals(currentSig)) {
                    String releaseVal = record.get("ReleaseIndex");
                    if (releaseVal != null && !releaseVal.isEmpty()) {
                        try {
                            int currentIdx = Integer.parseInt(releaseVal);
                            if (currentIdx > maxReleaseIndex) {
                                maxReleaseIndex = currentIdx;
                                latestRecord = record;
                            }
                        } catch (NumberFormatException e) {
                            // ignore
                        }
                    }
                }
            }
            return latestRecord;
        }
    }
}
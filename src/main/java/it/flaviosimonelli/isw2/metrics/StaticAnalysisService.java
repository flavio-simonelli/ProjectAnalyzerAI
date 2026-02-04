package it.flaviosimonelli.isw2.metrics;

import it.flaviosimonelli.isw2.git.bean.GitCommit;
import it.flaviosimonelli.isw2.git.service.GitService;
import it.flaviosimonelli.isw2.model.MethodIdentity;
import it.flaviosimonelli.isw2.model.MethodStaticMetrics;
import net.sourceforge.pmd.reporting.RuleViolation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class StaticAnalysisService {
    private static final Logger logger = LoggerFactory.getLogger(StaticAnalysisService.class);

    private final GitService gitService;
    private final MetricsCalculator metricsCalculator;
    private final PmdAnalysisService pmdService;

    public StaticAnalysisService(GitService gitService) {
        this.gitService = gitService;
        this.metricsCalculator = new MetricsCalculator();
        this.pmdService = new PmdAnalysisService();
    }

    /**
     * Analizza l'intero progetto allo stato dello snapshot fornito.
     * Restituisce una mappa UNICA contenente le metriche di TUTTI i metodi di TUTTI i file.
     */
    public Map<MethodIdentity, MethodStaticMetrics> analyzeRelease(GitCommit snapshot) {
        Map<MethodIdentity, MethodStaticMetrics> projectMap = new HashMap<>();

        // 1. Recupera lista file
        Map<String, String> javaFiles = gitService.getJavaFilesContent(snapshot);
        // Log fondamentale per verificare che JGit stia funzionando
        logger.info("Analisi Statica Snapshot {}: trovati {} file .java candidati.", snapshot.getHash(), javaFiles.size());

        if (javaFiles.isEmpty()) {
            logger.warn("Snapshot {}: Nessun file Java trovato.", snapshot.getHash());
            return projectMap;
        }

        // 2. Loop sui file
        int parsedFilesCount = 0;
        for (Map.Entry<String, String> entry : javaFiles.entrySet()) {
            String filePath = entry.getKey();
            String sourceCode = entry.getValue();
            try {
                // Ora sourceCode non può essere null (al massimo vuoto)
                if (sourceCode == null || sourceCode.trim().isEmpty()) {
                    continue;
                }

                // CHECK 2: Log pre-analisi
                logger.debug("Analisi file: {} ({} chars)", filePath, sourceCode.length());

                // 3. ESEGUI PMD SUL FILE
                // Otteniamo la lista di tutte le violazioni nel file corrente
                List<RuleViolation> violations = pmdService.analyze(sourceCode, filePath);

                // 4. PASSA LE VIOLAZIONI AL CALCULATOR
                // (Nota: dobbiamo aggiornare la firma di extractMetrics nel passo successivo)
                Map<MethodIdentity, MethodStaticMetrics> fileMetrics = metricsCalculator.extractMetrics(sourceCode, filePath, violations);

                if (fileMetrics.isEmpty()) {
                    if (!isIgnorableFile(filePath)) {
                        logger.debug("Nessun metodo trovato in: {}", filePath);
                    }
                } else {
                    projectMap.putAll(fileMetrics);
                    parsedFilesCount++;
                }

            } catch (Exception e) {
                logger.error("Errore parsing file {}", filePath, e);
            }
        }
        logger.info("Analisi completata: parsati {} file, estratti {} metodi.", parsedFilesCount, projectMap.size());
        return projectMap;
    }

    /**
     * NUOVO METODO PER IL WHAT-IF: Analizza un singolo file locale (.java o .txt).
     * Ritorna una mappa dove la chiave è la String signature (compatibile col CSV)
     * e il valore sono le metriche.
     */
    public Map<String, MethodStaticMetrics> analyzeLocalFile(File javaFile) {
        Map<String, MethodStaticMetrics> result = new HashMap<>();

        if (!javaFile.exists()) {
            logger.error("File locale non trovato: {}", javaFile.getAbsolutePath());
            return result;
        }

        try {
            // 1. Leggi il contenuto del file
            String sourceCode = Files.readString(javaFile.toPath());
            String fakePath = javaFile.getName();

            // 2. Analisi (Riusiamo la stessa logica di Git!)
            Map<MethodIdentity, MethodStaticMetrics> metricsMap = internalAnalyze(sourceCode, fakePath);

            // 3. Convertiamo MethodIdentity in String Signature per facilitare il matching col CSV
            // La MethodIdentity contiene già la firma formattata corretta
            for (Map.Entry<MethodIdentity, MethodStaticMetrics> entry : metricsMap.entrySet()) {
                // MethodIdentity.toString() o un getter dedicato dovrebbe darti "package.Class.method"
                result.put(entry.getKey().fullSignature(), entry.getValue());
            }

        } catch (Exception e) {
            logger.error("Errore analisi file locale {}", javaFile.getAbsolutePath(), e);
        }
        return result;
    }

    /**
     * Logica Core condivisa: prende codice sorgente e path, restituisce metriche.
     */
    private Map<MethodIdentity, MethodStaticMetrics> internalAnalyze(String sourceCode, String filePath) {
        try {
            // 1. PMD Analysis
            List<RuleViolation> violations = pmdService.analyze(sourceCode, filePath);

            // 2. Metrics Calculation (AST Parsing + Incrocio Violazioni)
            return metricsCalculator.extractMetrics(sourceCode, filePath, violations);

        } catch (Exception e) {
            logger.error("Errore interno analisi statica su {}", filePath, e);
            return new HashMap<>();
        }
    }

    /**
     * Restituisce la lista delle intestazioni delle colonne per le metriche statiche.
     * Usato dal CSVPrinter per generare l'header.
     */
    public java.util.List<String> getHeaderList() {
        return metricsCalculator.getHeaderList();
    }

    /**
     * Restituisce la lista dei valori delle metriche per un dato metodo.
     * Restituisce una lista di Object (Integer/Double) che CSVPrinter formatterà.
     */
    public java.util.List<Object> getValuesAsList(MethodStaticMetrics metrics) {
        return metricsCalculator.getValuesAsList(metrics);
    }

    /**
     * Helper per ridurre il rumore nei log.
     * Ritorna true se è normale che il file non abbia metodi.
     */
    private boolean isIgnorableFile(String filePath) {
        return filePath.endsWith("package-info.java") ||
                filePath.endsWith("Exception.java") ||
                filePath.contains("/test/");
    }
}

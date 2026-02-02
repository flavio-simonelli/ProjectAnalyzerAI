package it.flaviosimonelli.isw2.metrics.process;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import it.flaviosimonelli.isw2.git.bean.GitCommit;
import it.flaviosimonelli.isw2.git.service.GitService;
import it.flaviosimonelli.isw2.metrics.process.impl.*;
import it.flaviosimonelli.isw2.model.MethodIdentity;
import it.flaviosimonelli.isw2.model.MethodProcessMetrics;
import it.flaviosimonelli.isw2.util.JavaParserUtils;
import org.eclipse.jgit.diff.Edit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Analizzatore delle metriche di processo.
 * <p>
 * Questa classe orchestra l'estrazione delle metriche storiche (Churn, Revisioni, Autori, ecc.).
 * Funziona iterando sui commit Git, analizzando i Diff e mappandoli sui metodi Java tramite AST.
 * </p>
 */
public class ProcessMetricAnalyzer {
    private static final Logger logger = LoggerFactory.getLogger(ProcessMetricAnalyzer.class);
    private final GitService gitService;

    // Chain of Responsibility: Lista delle metriche attive
    private final List<IProcessMetric> metricsChain = new ArrayList<>();

    public ProcessMetricAnalyzer(GitService gitService) {
        this.gitService = gitService;
        registerMetrics();
    }

    /**
     * Registra le metriche da calcolare.
     * L'ordine di registrazione determina l'ordine delle colonne nel CSV finale.
     */
    private void registerMetrics() {
        // 1. Metriche Base (Necessarie per i calcoli delle altre)
        register(new MethodHistoriesMetric());   // Calcola NR (Number of Revisions)
        register(new NumberOfAuthorsMetric());   // Calcola NAuth

        // 2. Metriche Configurabili (Generano SUM, MAX, AVG)
        register(new LocAddedMetric());          // LOC_Added
        register(new LocDeletedMetric());        // LOC_Deleted
        register(new ChurnMetric());             // Churn
    }

    public void register(IProcessMetric metric) {
        this.metricsChain.add(metric);
    }

    /**
     * Analizza una lista cronologica di commit per estrarre le metriche di processo
     * per l'intervallo specificato (Analisi Locale).
     *
     * @param commits La lista dei commit da analizzare.
     * @return Mappa (MethodIdentity -> MethodProcessMetrics) con i valori accumulati per l'intervallo.
     */
    public Map<MethodIdentity, MethodProcessMetrics> extractProcessMetrics(List<GitCommit> commits) {
        Map<MethodIdentity, MethodProcessMetrics> metricsMap = new HashMap<>();

        for (GitCommit commit : commits) {
            Map<String, List<Edit>> diffs = gitService.getDiffsWithEdits(commit);

            for (Map.Entry<String, List<Edit>> entry : diffs.entrySet()) {
                String filePath = entry.getKey();
                if (!filePath.endsWith(".java")) continue;

                try {
                    // 1. Analisi Contestuale: Scarichiamo il file com'era IN QUEL COMMIT
                    String sourceCode = gitService.getRawFileContent(commit, filePath);
                    if (sourceCode == null) continue;

                    // 2. Parsing AST
                    CompilationUnit cu = StaticJavaParser.parse(sourceCode);
                    List<MethodDeclaration> methods = cu.findAll(MethodDeclaration.class);
                    List<Edit> edits = entry.getValue();

                    for (Edit edit : edits) {
                        // Conversione indici (JGit 0-based -> JavaParser 1-based)
                        int startLine = edit.getBeginB() + 1;
                        int endLine = edit.getEndB() + 1;

                        // Dati grezzi del cambiamento
                        int added = edit.getLengthB();
                        int deleted = edit.getLengthA();

                        for (MethodDeclaration method : methods) {
                            if (method.getRange().isEmpty()) continue;
                            int mStart = method.getRange().get().begin.line;
                            int mEnd = method.getRange().get().end.line;

                            // 3. Matching Spaziale: L'edit tocca il metodo?
                            if (startLine <= mEnd && endLine >= mStart) {

                                String fullSig = JavaParserUtils.getFullyQualifiedSignature(method, cu);
                                String className = JavaParserUtils.getParentClassName(method);
                                String methodName = method.getNameAsString();

                                MethodIdentity identity = new MethodIdentity(fullSig, className, methodName);

                                metricsMap.putIfAbsent(identity, new MethodProcessMetrics());
                                MethodProcessMetrics data = metricsMap.get(identity);

                                // 4. Aggiornamento Metriche
                                // Passiamo i dati grezzi a tutte le metriche registrate
                                for (IProcessMetric metric : metricsChain) {
                                    metric.update(data, commit, added, deleted);
                                }

                                break; // Metodo trovato, passiamo al prossimo edit
                            }
                        }
                    }
                } catch (Exception e) {
                    logger.debug("Skip file {} on commit {}: {}", filePath, commit.getHash(), e.getMessage());
                }
            }
        }
        return metricsMap;
    }

    /**
     * Fonde le metriche dell'intervallo corrente nel registro globale.
     * Delega a ciascuna metrica la logica di fusione corretta (Sum, Max o Set Union).
     */
    public void mergeToGlobal(Map<MethodIdentity, MethodProcessMetrics> globalRegistry,
                              Map<MethodIdentity, MethodProcessMetrics> currentInterval) {

        for (Map.Entry<MethodIdentity, MethodProcessMetrics> entry : currentInterval.entrySet()) {
            MethodIdentity id = entry.getKey();
            MethodProcessMetrics currentData = entry.getValue();

            // Recupera o crea lo storico
            globalRegistry.putIfAbsent(id, new MethodProcessMetrics());
            MethodProcessMetrics historyData = globalRegistry.get(id);

            // Delega il merge alla catena di metriche
            for (IProcessMetric metric : metricsChain) {
                metric.merge(historyData, currentData);
            }
        }
    }

    // --- GESTIONE HEADER ---

    /**
     * Restituisce gli header per le colonne LOCALI.
     */
    public List<String> getHeaderList() {
        return metricsChain.stream()
                .map(m -> m.getHeaderList(false)) // false = Local Config
                .flatMap(List::stream)
                .toList();
    }

    /**
     * Restituisce gli header per le colonne GLOBALI (con prefisso "Global_").
     */
    public List<String> getGlobalHeaderList() {
        return metricsChain.stream()
                .map(m -> m.getHeaderList(true)) // true = Global Config
                .flatMap(List::stream)
                .map(h -> "Global_" + h)         // Aggiungiamo prefisso
                .toList();
    }

    // --- GESTIONE VALORI ---

    /**
     * Restituisce i valori calcolati per il contesto LOCALE (Release Corrente).
     * Usa la configurazione locale di ogni metrica.
     */
    public List<Object> getLocalValues(MethodProcessMetrics metricsData) {
        return metricsChain.stream()
                .map(m -> m.getValues(metricsData, false)) // false = Local Context
                .flatMap(List::stream)
                .toList();
    }

    /**
     * Restituisce i valori calcolati per il contesto GLOBALE (Storico).
     * Usa la configurazione globale di ogni metrica.
     */
    public List<Object> getGlobalValues(MethodProcessMetrics metricsData) {
        return metricsChain.stream()
                .map(m -> m.getValues(metricsData, true)) // true = Global Context
                .flatMap(List::stream)
                .toList();
    }
}
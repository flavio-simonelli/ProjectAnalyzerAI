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
     * Estrae le metriche di processo analizzando la cronologia dei commit.
     */
    public Map<MethodIdentity, MethodProcessMetrics> extractProcessMetrics(List<GitCommit> commits) {
        Map<MethodIdentity, MethodProcessMetrics> metricsMap = new HashMap<>();

        for (GitCommit commit : commits) {
            analyzeCommitChanges(commit, metricsMap);
        }
        return metricsMap;
    }

    private void analyzeCommitChanges(GitCommit commit, Map<MethodIdentity, MethodProcessMetrics> metricsMap) {
        Map<String, List<Edit>> diffs = gitService.getDiffsWithEdits(commit);

        for (Map.Entry<String, List<Edit>> entry : diffs.entrySet()) {
            String filePath = entry.getKey();

            // Filtro file: solo Java e non test (utilizziamo logica centralizzata)
            if (filePath.endsWith(".java")) {
                processFileDiff(commit, filePath, entry.getValue(), metricsMap);
            }
        }
    }

    private void processFileDiff(GitCommit commit, String filePath, List<Edit> edits,
                                 Map<MethodIdentity, MethodProcessMetrics> metricsMap) {
        try {
            String sourceCode = gitService.getRawFileContent(commit, filePath);
            if (sourceCode == null || sourceCode.isEmpty()) return;

            CompilationUnit cu = StaticJavaParser.parse(sourceCode);
            List<MethodDeclaration> methods = cu.findAll(MethodDeclaration.class);

            for (Edit edit : edits) {
                mapEditToMethods(edit, methods, cu, commit, metricsMap);
            }
        } catch (Exception e) {
            logger.debug("Skip file {} on commit {}: {}", filePath, commit.getHash(), e.getMessage());
        }
    }

    private void mapEditToMethods(Edit edit, List<MethodDeclaration> methods, CompilationUnit cu,
                                  GitCommit commit, Map<MethodIdentity, MethodProcessMetrics> metricsMap) {
        // Conversione indici JGit -> JavaParser
        int editStart = edit.getBeginB() + 1;
        int editEnd = edit.getEndB() + 1;

        for (MethodDeclaration method : methods) {
            if (isEditInsideMethod(editStart, editEnd, method)) {
                updateMethodMetrics(method, cu, commit, edit, metricsMap);
                // Trovato il metodo per questo edit, passiamo al prossimo edit
                break;
            }
        }
    }

    private boolean isEditInsideMethod(int editStart, int editEnd, MethodDeclaration method) {
        return method.getRange().map(range ->
                editStart <= range.end.line && editEnd >= range.begin.line
        ).orElse(false);
    }

    private void updateMethodMetrics(MethodDeclaration method, CompilationUnit cu, GitCommit commit,
                                     Edit edit, Map<MethodIdentity, MethodProcessMetrics> metricsMap) {

        MethodIdentity identity = new MethodIdentity(
                JavaParserUtils.getFullyQualifiedSignature(method, cu),
                JavaParserUtils.getParentClassName(method),
                method.getNameAsString()
        );

        MethodProcessMetrics data = metricsMap.computeIfAbsent(identity, k -> new MethodProcessMetrics());

        // Dati grezzi del cambiamento
        int added = edit.getLengthB();
        int deleted = edit.getLengthA();

        for (IProcessMetric metric : metricsChain) {
            metric.update(data, commit, added, deleted);
        }
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
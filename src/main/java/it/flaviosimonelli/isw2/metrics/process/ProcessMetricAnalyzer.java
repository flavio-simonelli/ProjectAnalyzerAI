package it.flaviosimonelli.isw2.metrics.process;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import it.flaviosimonelli.isw2.git.bean.GitCommit;
import it.flaviosimonelli.isw2.git.service.GitService;
import it.flaviosimonelli.isw2.metrics.process.impl.MethodHistoriesMetric;
import it.flaviosimonelli.isw2.model.MethodIdentity;
import it.flaviosimonelli.isw2.model.MethodProcessMetrics;
import it.flaviosimonelli.isw2.util.JavaParserUtils;
import org.eclipse.jgit.diff.Edit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class ProcessMetricAnalyzer {
    private static final Logger logger = LoggerFactory.getLogger(ProcessMetricAnalyzer.class);
    private final GitService gitService;

    // Lista delle strategie attive
    private final List<IProcessMetric> metricsChain = new ArrayList<>();

    public ProcessMetricAnalyzer(GitService gitService) {
        this.gitService = gitService;
        registerMetrics();
    }

    private void registerMetrics() {
        register(new MethodHistoriesMetric());
        // register(new ChurnMetric());
    }

    public void register(IProcessMetric metric) {
        this.metricsChain.add(metric);
    }

    /**
     * Analizza una lista cronologica di commit per estrarre le metriche di processo (storiche)
     * dei metodi modificati in quell'intervallo temporale.
     *
     * <p>Il metodo esegue i seguenti passaggi per ogni commit:
     * <ol>
     * <li>Recupera i file modificati (Diff).</li>
     * <li>Scarica il codice sorgente del file <i>nello stato di quel commit</i>.</li>
     * <li>Parsa il codice (AST) per localizzare i metodi.</li>
     * <li>Interseca le righe modificate (Git Edit) con le righe dei metodi (AST Range).</li>
     * <li>Se c'è intersezione, aggiorna le metriche (es. incrementa il contatore revisioni).</li>
     * </ol>
     *
     * @param commits La lista dei commit da analizzare (solitamente tra due release).
     * @return Una Mappa che associa l'Identità del Metodo (Firma) alle sue Metriche di Processo accumulate.
     */
    public Map<MethodIdentity, MethodProcessMetrics> extractProcessMetrics(List<GitCommit> commits) {
        Map<MethodIdentity, MethodProcessMetrics> metricsMap = new HashMap<>();

        for (GitCommit commit : commits) {
            // Otteniamo la lista delle modifiche (Edit) per ogni file in questo commit
            Map<String, List<Edit>> diffs = gitService.getDiffsWithEdits(commit);

            for (Map.Entry<String, List<Edit>> entry : diffs.entrySet()) {
                String filePath = entry.getKey();
                // Analizziamo solo file Java, ignoriamo XML, build files, ecc.
                if (!filePath.endsWith(".java")) continue;

                try {
                    // 2. Scarichiamo il contenuto del file com'era in QUEL commit (Analisi Contestuale)
                    // Necessario perché le righe dei metodi cambiano nel tempo.
                    String sourceCode = gitService.getRawFileContent(commit, filePath);
                    if (sourceCode == null) continue; // File cancellato o illeggibile

                    // 3. Costruiamo l'AST per trovare la posizione dei metodi in questa versione
                    CompilationUnit cu = StaticJavaParser.parse(sourceCode);
                    List<MethodDeclaration> methods = cu.findAll(MethodDeclaration.class);
                    List<Edit> edits = entry.getValue();

                    for (Edit edit : edits) {
                        // JGit usa indici 0-based, JavaParser 1-based. Convertiamo per uniformità.
                        int startLine = edit.getBeginB() + 1;
                        int endLine = edit.getEndB() + 1;
                        // Dati grezzi del cambiamento
                        int added = edit.getLengthB();
                        int deleted = edit.getLengthA();

                        for (MethodDeclaration method : methods) {
                            // Ignoriamo metodi senza corpo o malformati
                            if (method.getRange().isEmpty()) continue;
                            int mStart = method.getRange().get().begin.line;
                            int mEnd = method.getRange().get().end.line;

                            // 4. MATCHING SPAZIALE: L'edit tocca le righe di questo metodo?
                            if (startLine <= mEnd && endLine >= mStart) {

                                // 5. COSTRUZIONE IDENTITÀ ROBUSTA
                                // Usiamo JavaParserUtils per garantire che la firma generata qui (Processo)
                                // sia IDENTICA byte-per-byte a quella generata nell'Analisi Statica.
                                String fullSig = JavaParserUtils.getFullyQualifiedSignature(method, cu);
                                String className = JavaParserUtils.getParentClassName(method);
                                String methodName = method.getNameAsString();

                                MethodIdentity identity = new MethodIdentity(fullSig, className, methodName);

                                // 6. RECUPERO O CREAZIONE ACCUMULATORE
                                // Grazie all'equals() di MethodIdentity, troviamo la entry anche se l'oggetto è nuovo.
                                metricsMap.putIfAbsent(identity, new MethodProcessMetrics());
                                MethodProcessMetrics data = metricsMap.get(identity);

                                // 7. AGGIORNAMENTO STRATEGICO
                                // Deleghiamo alle singole metriche (Strategy Pattern) il calcolo del nuovo valore.
                                for (IProcessMetric metric : metricsChain) {
                                    metric.update(data, commit, added, deleted);
                                }

                                // Ottimizzazione: Assumiamo che un blocco di modifica contiguo (Edit)
                                // appartenga a un solo metodo prevalente.
                                break;
                            }
                        }
                    }
                } catch (Exception e) {
                    // Logghiamo a livello debug per non sporcare la console in produzione,
                    // ma teniamo traccia di eventuali file problematici.
                    logger.debug("Skip file {} on commit {}: {}", filePath, commit.getHash(), e.getMessage());
                }
            }
        }
        return metricsMap;
    }

    /**
     * Fonde le metriche dell'intervallo corrente nel registro globale.
     * Modifica 'globalRegistry' in-place sommando i nuovi valori.
     *
     * @param globalRegistry La mappa che mantiene la storia dall'inizio dei tempi.
     * @param currentInterval La mappa appena calcolata per la release corrente.
     */
    public void mergeToGlobal(Map<MethodIdentity, MethodProcessMetrics> globalRegistry,
                              Map<MethodIdentity, MethodProcessMetrics> currentInterval) {

        for (Map.Entry<MethodIdentity, MethodProcessMetrics> entry : currentInterval.entrySet()) {
            MethodIdentity id = entry.getKey();
            MethodProcessMetrics intervalMetrics = entry.getValue();

            // 1. Recupera o crea la entry nel registro globale
            globalRegistry.putIfAbsent(id, new MethodProcessMetrics());
            MethodProcessMetrics globalMetrics = globalRegistry.get(id);

            // 2. Somma ogni singola metrica
            // (Assumiamo che le metriche di processo siano sommabili: Revisioni, Churn, ecc.)
            Map<String, Double> intervalValues = intervalMetrics.getMetrics(); // Usa getter che espone mappa

            for (Map.Entry<String, Double> metricEntry : intervalValues.entrySet()) {
                String metricName = metricEntry.getKey();
                Double addedValue = metricEntry.getValue();

                Double oldValue = globalMetrics.getMetric(metricName);
                if (oldValue == null) oldValue = 0.0;

                globalMetrics.addMetric(metricName, oldValue + addedValue);
            }
        }
    }

    /**
     * Restituisce la stringa CSV dell'header per le metriche di processo.
     * Es: "MethodHistories,Churn,Authors"
     * L'ordine dipende dall'ordine di registrazione nel costruttore.
     */
    public String getCsvHeader() {
        return metricsChain.stream()
                .map(IProcessMetric::getName)
                .collect(Collectors.joining(","));
    }

    /**
     * Genera i valori CSV gestendo i casi null e i default.
     * @param metricsData Può essere NULL se il metodo non è mai stato toccato.
     */
    public String getCsvValues(MethodProcessMetrics metricsData) {
        return metricsChain.stream()
                .map(metric -> {
                    // CASO 1: Nessuna storia per questo metodo -> Default Assoluto (es. "0")
                    if (metricsData == null) {
                        return metric.getDefaultValue();
                    }

                    // CASO 2: Metodo toccato, cerchiamo questa specifica metrica
                    Double val = metricsData.getMetric(metric.getName());

                    // CASO 3: Metrica specifica mancante (es. bug logico) -> Default
                    if (val == null) {
                        return metric.getDefaultValue();
                    }

                    // Formattazione pulita
                    if (val % 1 == 0) return String.valueOf(val.intValue());
                    return String.valueOf(val);
                })
                .collect(Collectors.joining(","));
    }

    /**
     * Genera un header CSV con prefisso (es. "Total_N-Revisions,Total_Churn").
     */
    public String getGlobalCsvHeader() {
        return metricsChain.stream()
                .map(m -> "Total_" + m.getName())
                .collect(Collectors.joining(","));
    }
}
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
        register(new NumberOfAuthorsMetric());
        register(new ChurnMetric());
        register(new StmtAddedMetric());
        register(new StmtDeletedMetric());
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
    public void mergeToGlobal(Map<MethodIdentity, MethodProcessMetrics> globalRegistry, Map<MethodIdentity, MethodProcessMetrics> currentInterval) {

        for (Map.Entry<MethodIdentity, MethodProcessMetrics> entry : currentInterval.entrySet()) {
            MethodIdentity id = entry.getKey();
            MethodProcessMetrics intervalMetrics = entry.getValue();

            // 1. Recupera o crea la entry nel registro globale
            globalRegistry.putIfAbsent(id, new MethodProcessMetrics());
            MethodProcessMetrics globalMetrics = globalRegistry.get(id);

            // 2. MERGE NUMERICO (Somma semplice per Revisioni, Churn, etc.)
            // Iteriamo sulle metriche numeriche che NON hanno un backing set complesso
            // (Per semplicità, qui sommiamo tutto, poi sovrascriviamo i complessi)
            Map<String, Double> intervalValues = intervalMetrics.getMetrics();

            for (Map.Entry<String, Double> metricEntry : intervalValues.entrySet()) {
                String metricName = metricEntry.getKey();

                // Se è una metrica basata su Set (come N-Authors), gestiamo il merge complesso
                Set<String> intervalSet = intervalMetrics.getSet(metricName);

                if (!intervalSet.isEmpty()) {
                    // LOGICA DISTINCT: Aggiungiamo tutti gli autori dell'intervallo al set globale
                    for (String item : intervalSet) {
                        globalMetrics.addToSet(metricName, item);
                    }
                } else {
                    // LOGICA SOMMATIVA (History, Churn): Somma aritmetica
                    Double addedValue = metricEntry.getValue();
                    Double oldValue = globalMetrics.getMetric(metricName);
                    if (oldValue == null) oldValue = 0.0;

                    // Evitiamo di sommare N-Authors due volte (è già gestito sopra dall'addToSet)
                    // Usiamo un check semplice: se il DTO globale ha un set per questa chiave, non sommare i double.
                    if (globalMetrics.getSet(metricName).isEmpty()) {
                        globalMetrics.addMetric(metricName, oldValue + addedValue);
                    }
                }
            }
        }
    }

    /**
     * Header per le metriche di processo LOCALI (Intervallo).
     */
    public List<String> getHeaderList() {
        return metricsChain.stream()
                .map(IProcessMetric::getName)
                .collect(Collectors.toList());
    }

    /**
     * Header per le metriche di processo GLOBALI (Totali).
     * Aggiunge il prefisso "Total_" per distinguerle.
     */
    public List<String> getGlobalHeaderList() {
        return metricsChain.stream()
                .map(m -> "Total_" + m.getName())
                .collect(Collectors.toList());
    }

    /**
     * Restituisce i valori (Raw Objects) per un dato metodo.
     * Funziona sia per dati Locali che Globali (basta passare il DTO giusto).
     * @param metricsData Il DTO delle metriche (può essere null).
     */
    public List<Object> getValuesAsList(MethodProcessMetrics metricsData) {
        // Se il metodo non ha storia (null), restituiamo una lista di zeri/default
        // per mantenere l'allineamento delle colonne nel CSV.
        if (metricsData == null) {
            return metricsChain.stream()
                    .map(m -> parseDefault(m.getDefaultValue()))
                    .collect(Collectors.toList());
        }

        return metricsChain.stream()
                .map(metric -> {
                    Double val = metricsData.getMetric(metric.getName());

                    // Se manca il valore specifico, usiamo il default della metrica
                    if (val == null) {
                        return parseDefault(metric.getDefaultValue());
                    }

                    // Formattazione: Interi come Integer, Decimali come Double
                    if (val % 1 == 0) {
                        return val.intValue();
                    } else {
                        return val;
                    }
                })
                .collect(Collectors.toList());
    }

    /**
     * Helper interno per convertire il valore di default (spesso "0") in numero.
     */
    private Object parseDefault(String def) {
        try {
            if (def.contains(".")) return Double.parseDouble(def);
            return Integer.parseInt(def);
        } catch (NumberFormatException e) {
            return 0; // Fallback sicuro
        }
    }
}
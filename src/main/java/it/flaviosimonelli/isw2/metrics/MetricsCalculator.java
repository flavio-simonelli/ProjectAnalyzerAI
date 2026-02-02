package it.flaviosimonelli.isw2.metrics;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import it.flaviosimonelli.isw2.metrics.impl.*; // I tuoi pacchetti implementazioni
import it.flaviosimonelli.isw2.model.MethodIdentity;
import it.flaviosimonelli.isw2.model.MethodStaticMetrics;
import it.flaviosimonelli.isw2.util.JavaParserUtils;
import net.sourceforge.pmd.reporting.RuleViolation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class MetricsCalculator {
    private static final Logger logger = LoggerFactory.getLogger(MetricsCalculator.class);

    // Lista ordinata delle strategie di calcolo
    private final List<IMetric> metricsChain;
    // Manteniamo un riferimento specifico per poter settare il contesto
    private final PmdCodeSmellsMetric pmdMetric;

    public MetricsCalculator() {
        this.metricsChain = new ArrayList<>();
        this.pmdMetric = new PmdCodeSmellsMetric();
        registerMetrics();
    }

    private void registerMetrics() {
        // --- 1. Metriche di Dimensione ---
        register(new LocMetric()); // Linee di codice totali
        register(new StatementCountMetric()); // Istruzioni effettive (escluse righe vuote/commenti)
        register(new ParameterCountMetric()); // Numero di parametri in input
        register(new LocalVariableCountMetric()); // Variabili locali dichiarate (stato interno)

        // --- 2. Metriche di Complessità ---
        register(new CyclomaticComplexityMetric()); // Complessità di McCabe (Standard testabilità)
        register(new CognitiveComplexityMetric()); // Complessità Cognitiva (Leggibilità umana)
        register(new NestingDepthMetric()); // Profondità massima di annidamento (If dentro For dentro While...)
        register(new ReturnTypeComplexityMetric()); // Complessità del tipo di ritorno (es. List<Map<String,A>>)

        // --- 3. Metriche di Qualità ---
        register(this.pmdMetric);
    }

    private void register(IMetric metric) {
        this.metricsChain.add(metric);
    }

    /**
     * Analizza il codice sorgente ed estrae le metriche per tutti i metodi trovati.
     * * @param sourceCode Il contenuto grezzo del file .java
     * @param filePath Il percorso del file (usato solo per logging o debug se necessario)
     * @return Una Mappa ordinata: Identità Metodo -> Metriche Calcolate
     */
    public Map<MethodIdentity, MethodStaticMetrics> extractMetrics(String sourceCode, String filePath, List<RuleViolation> pmdViolations) {
        // LinkedHashMap preserva l'ordine di apparizione dei metodi nel file
        Map<MethodIdentity, MethodStaticMetrics> extractedData = new LinkedHashMap<>();

        try {
            this.pmdMetric.setContext(pmdViolations);

            CompilationUnit cu = StaticJavaParser.parse(sourceCode);
            // Visitiamo tutti i metodi
            List<MethodDeclaration> methodDeclarations = cu.findAll(MethodDeclaration.class);

            if (methodDeclarations.isEmpty()) {
                logger.debug("File parsato correttamente ma 0 metodi trovati: {}", filePath);
                return extractedData;
            }

            for (MethodDeclaration methodDecl : methodDeclarations) {
                // 1. Costruzione Identità Robusta (delegata alla Utility)
                MethodIdentity identity = new MethodIdentity(
                        JavaParserUtils.getFullyQualifiedSignature(methodDecl, cu),
                        JavaParserUtils.getParentClassName(methodDecl),
                        methodDecl.getNameAsString()
                );
                // 2. Calcolo Metriche
                MethodStaticMetrics metrics = calculateAllMetrics(methodDecl, identity.fullSignature());
                extractedData.put(identity, metrics);
            }
        } catch (Exception e) {
            // Se il file non è parsabile (es. errori di sintassi Java), logghiamo e saltiamo
            logger.error("Impossibile parsare il file: {}", filePath, e);
        } finally {
            // 5. Pulizia
            this.pmdMetric.setContext(null);
        }

        return extractedData;
    }

    /**
     * Calcola l'intera catena di metriche per un singolo metodo.
     * Metodo estratto per evitare try-catch annidati (SonarCloud S1141).
     */
    private MethodStaticMetrics calculateAllMetrics(MethodDeclaration methodDecl, String fullSignature) {
        MethodStaticMetrics metrics = new MethodStaticMetrics();

        for (IMetric metric : metricsChain) {
            try {
                double value = metric.calculate(methodDecl);
                metrics.addMetric(metric.getName(), value);
            } catch (Exception e) {
                // Se una singola metrica fallisce, non vogliamo bloccare le altre
                logger.warn("Errore calcolo metrica {} su metodo {}: {}",
                        metric.getName(), fullSignature, e.getMessage());
                metrics.addMetric(metric.getName(), 0.0);
            }
        }
        return metrics;
    }

    /**
     * Restituisce la lista delle intestazioni (Header) per le metriche statiche.
     * Usato da CsvUtils/CSVPrinter per definire le colonne.
     */
    public List<String> getHeaderList() {
        return metricsChain.stream()
                .map(IMetric::getName)
                .toList();
    }

    /**
     * Restituisce la lista dei valori raw (Object) per un dato metodo.
     * Usato da CsvUtils/CSVPrinter per scrivere la riga.
     * Gestisce automaticamente la conversione Double -> Integer se il numero è intero.
     */
    public List<Object> getValuesAsList(MethodStaticMetrics metrics) {
        // Caso limite: se non ci sono metriche (metodo nullo), restituiamo una lista di zeri
        // della lunghezza corretta per non far slittare le colonne del CSV.
        if (metrics == null) {
            return metricsChain.stream()
                    .map(m -> 0)
                    .collect(Collectors.toList());
        }

        return metricsChain.stream()
                .map(metric -> {
                    Double val = metrics.getMetric(metric.getName());

                    // Safety check: se manca il valore, default a 0
                    if (val == null) return 0;

                    // Formattazione per pulizia CSV:
                    // Se è un numero intero (es. 5.0), restituiscilo come Integer (5).
                    // Altrimenti tienilo come Double (5.12).
                    if (val % 1 == 0) {
                        return val.intValue();
                    } else {
                        return val;
                    }
                })
                .collect(Collectors.toList());
    }
}
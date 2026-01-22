package it.flaviosimonelli.isw2.metrics;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import it.flaviosimonelli.isw2.metrics.impl.*; // I tuoi pacchetti implementazioni
import it.flaviosimonelli.isw2.model.MethodIdentity;
import it.flaviosimonelli.isw2.model.MethodStaticMetrics;
import it.flaviosimonelli.isw2.util.JavaParserUtils;
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

    public MetricsCalculator() {
        this.metricsChain = new ArrayList<>();
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
        register(new CodeSmellsMetric());
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
    public Map<MethodIdentity, MethodStaticMetrics> extractMetrics(String sourceCode, String filePath) {
        // LinkedHashMap preserva l'ordine di apparizione dei metodi nel file
        Map<MethodIdentity, MethodStaticMetrics> extractedData = new LinkedHashMap<>();

        try {
            CompilationUnit cu = StaticJavaParser.parse(sourceCode);
            // Visitiamo tutti i metodi
            List<MethodDeclaration> methodDeclarations = cu.findAll(MethodDeclaration.class);

            if (methodDeclarations.isEmpty()) {
                logger.debug("File parsato correttamente ma 0 metodi trovati: {}", filePath);
                return extractedData;
            }

            for (MethodDeclaration methodDecl : methodDeclarations) {
                // 1. Costruzione Identità Robusta (delegata alla Utility)
                String fullSignature = JavaParserUtils.getFullyQualifiedSignature(methodDecl, cu);
                String className = JavaParserUtils.getParentClassName(methodDecl);
                String methodName = methodDecl.getNameAsString();

                // Creazione della chiave univoca
                MethodIdentity identity = new MethodIdentity(fullSignature, className, methodName);

                // 2. Calcolo Metriche
                MethodStaticMetrics metrics = new MethodStaticMetrics();

                for (IMetric metric : metricsChain) {
                    try {
                        double value = metric.calculate(methodDecl);
                        metrics.addMetric(metric.getName(), value);
                    } catch (Exception e) {
                        logger.warn("Errore calcolo metrica {} su metodo {}: {}",
                                metric.getName(), fullSignature, e.getMessage());
                        // Fallback a 0 o NaN in caso di errore della singola metrica
                        metrics.addMetric(metric.getName(), 0.0);
                    }
                }

                extractedData.put(identity, metrics);

            }
        } catch (Exception e) {
            // Se il file non è parsabile (es. errori di sintassi Java), logghiamo e saltiamo
            logger.error("Impossibile parsare il file: {}", filePath, e);
        }

        return extractedData;
    }

    /**
     * Restituisce l'header CSV per la sezione statica.
     * Ordine garantito dall'ordine di registrazione nel costruttore.
     */
    public String getCsvHeader() {
        return metricsChain.stream()
                .map(IMetric::getName)
                .collect(Collectors.joining(","));
    }

    /**
     * Converte l'oggetto Metriche in una stringa CSV formattata.
     * Si occupa della formattazione numerica (Interi vs Double).
     */
    public String getCsvValues(MethodStaticMetrics metrics) {
        return metricsChain.stream()
                .map(metric -> {
                    Double val = metrics.getMetric(metric.getName());

                    // Safety check: se per qualche motivo la metrica manca, mettiamo 0
                    if (val == null) return "0";

                    // Formattazione pulita: 5.0 -> "5", 5.12 -> "5.12"
                    if (val % 1 == 0) {
                        return String.valueOf(val.intValue());
                    } else {
                        return String.valueOf(val);
                    }
                })
                .collect(Collectors.joining(","));
    }
}
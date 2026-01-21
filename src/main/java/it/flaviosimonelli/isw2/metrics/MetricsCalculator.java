package it.flaviosimonelli.isw2.metrics;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import it.flaviosimonelli.isw2.metrics.IMetric;
import it.flaviosimonelli.isw2.metrics.impl.*; // I tuoi pacchetti implementazioni
import it.flaviosimonelli.isw2.model.Method;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class MetricsCalculator {
    private static final Logger logger = LoggerFactory.getLogger(MetricsCalculator.class);
    private final List<IMetric> metricsChain;

    public MetricsCalculator() {
        this.metricsChain = new ArrayList<>();

        // --- 1. Metriche di Dimensione (Size) ---
        // Indicano la "massa" fisica e logica del metodo.
        register(new LocMetric());                  // Linee di codice totali
        register(new StatementCountMetric());       // Istruzioni effettive (escluse righe vuote/commenti)
        register(new ParameterCountMetric());       // Numero di parametri in input
        register(new LocalVariableCountMetric());   // Variabili locali dichiarate (stato interno)

        // --- 2. Metriche di Complessità (Complexity) ---
        // Indicano la difficoltà del flusso di controllo.
        register(new CyclomaticComplexityMetric()); // Complessità di McCabe (Standard testabilità)
        register(new CognitiveComplexityMetric());  // Complessità Cognitiva (Leggibilità umana)
        register(new NestingDepthMetric());         // Profondità massima di annidamento (If dentro For dentro While...)
        register(new ReturnTypeComplexityMetric()); // Complessità del tipo di ritorno (es. List<Map<String,A>>)

        // --- 3. Metriche di Qualità (Code Smells) ---
        // Indicano cattive pratiche specifiche (Catch vuoti, PrintStackTrace, ecc.)
        register(new CodeSmellsMetric());
    }

    private void register(IMetric metric) {
        this.metricsChain.add(metric);
    }

    public List<Method> extractMetrics(String sourceCode, String className) {
        List<Method> results = new ArrayList<>();

        try {
            CompilationUnit cu = StaticJavaParser.parse(sourceCode);
            List<MethodDeclaration> methods = cu.findAll(MethodDeclaration.class);

            for (MethodDeclaration method : methods) {
                // Creiamo il contenitore per questo metodo
                String methodSignature = method.getSignature().asString();
                Method data = new Method(className, methodSignature);

                // Applichiamo TUTTE le metriche registrate
                for (IMetric metric : metricsChain) {
                    try {
                        double value = metric.calculate(method);
                        data.addMetric(metric.getName(), value);
                    } catch (Exception e) {
                        logger.warn("Errore calcolo metrica {} su {}: {}", metric.getName(), method.getNameAsString(), e.getMessage());
                        data.addMetric(metric.getName(), -1.0); // Valore errore
                    }
                }
                results.add(data);
            }

        } catch (Exception e) {
            // Errori di parsing globali (es. enum o interfacce)
            logger.debug("Parsing fallito per classe {}: {}", className, e.getMessage());
        }
        return results;
    }

    // Metodo utile per ottenere l'header del CSV dal DatasetGenerator
    public String getCsvHeader() {
        StringBuilder sb = new StringBuilder("Version,ClassName,Signature");
        for (IMetric m : metricsChain) {
            sb.append(",").append(m.getName());
        }
        sb.append(",Buggy");
        return sb.toString();
    }
}
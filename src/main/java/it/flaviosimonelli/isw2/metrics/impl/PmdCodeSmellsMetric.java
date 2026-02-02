package it.flaviosimonelli.isw2.metrics.impl;

import com.github.javaparser.ast.body.MethodDeclaration;
import it.flaviosimonelli.isw2.metrics.IMetric;
import net.sourceforge.pmd.reporting.RuleViolation;

import java.util.Collections;
import java.util.List;

public class PmdCodeSmellsMetric implements IMetric {

    // Stato interno: le violazioni del file corrente
    private List<RuleViolation> currentFileViolations;

    public PmdCodeSmellsMetric() {
        this.currentFileViolations = Collections.emptyList();
    }

    /**
     * Imposta il contesto per il file corrente.
     * Da chiamare PRIMA di iterare sui metodi del file.
     * @param violations La lista di violazioni PMD trovate nell'intero file.
     */
    public void setContext(List<RuleViolation> violations) {
        this.currentFileViolations = (violations != null) ? violations : Collections.emptyList();
    }

    @Override
    public String getName() {
        return "CodeSmells";
    }

    @Override
    public double calculate(MethodDeclaration method) {
        if (currentFileViolations.isEmpty()) return 0.0;

        // Se JavaParser non ha info sulle righe, non possiamo mappare
        if (!method.getBegin().isPresent() || !method.getEnd().isPresent()) return 0.0;

        int startLine = method.getBegin().get().line;
        int endLine = method.getEnd().get().line;

        // Conta le violazioni che cadono nel range del metodo
        return currentFileViolations.stream()
                .filter(v -> v.getBeginLine() >= startLine && v.getBeginLine() <= endLine)
                .count();
    }
}
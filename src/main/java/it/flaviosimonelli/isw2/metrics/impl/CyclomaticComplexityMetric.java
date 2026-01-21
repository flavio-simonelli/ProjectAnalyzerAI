package it.flaviosimonelli.isw2.metrics.impl;

import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.ConditionalExpr;
import com.github.javaparser.ast.stmt.*;
import it.flaviosimonelli.isw2.metrics.IMetric;

/**
 * Calcola la <b>Complessità Ciclomatica (McCabe)</b>.
 * <p>
 * Misura il numero di percorsi linearmente indipendenti attraverso il codice sorgente.
 * Il calcolo parte da 1 e aggiunge +1 per ogni punto di biforcazione:
 * if, for, while, do-while, case, catch, e operatori ternari.
 * </p>
 * <b>Significato per il Bug Prediction:</b>
 * È lo standard industriale per misurare la testabilità. Un metodo con complessità 10
 * richiede almeno 10 Test Case unitari per coprire tutti i percorsi.
 * Alta complessità = Alta probabilità di bug residui non testati.
 */
public class CyclomaticComplexityMetric implements IMetric {
    @Override
    public String getName() {
        return "Cyclomatic";
    }

    @Override
    public double calculate(MethodDeclaration method) {
        int complexity = 1; // Base

        complexity += method.findAll(IfStmt.class).size();
        complexity += method.findAll(ForStmt.class).size();
        complexity += method.findAll(ForEachStmt.class).size();
        complexity += method.findAll(WhileStmt.class).size();
        complexity += method.findAll(DoStmt.class).size();
        complexity += method.findAll(CatchClause.class).size();
        complexity += method.findAll(ConditionalExpr.class).size(); // Ternari (a ? b : c)

        // Per i case dello switch, contiamo solo quelli con label (ignoriamo default)
        complexity += method.findAll(SwitchEntry.class).stream()
                .filter(e -> !e.getLabels().isEmpty())
                .count();

        return (double) complexity;
    }
}
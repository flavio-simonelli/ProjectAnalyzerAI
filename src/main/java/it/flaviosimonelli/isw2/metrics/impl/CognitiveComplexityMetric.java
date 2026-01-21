package it.flaviosimonelli.isw2.metrics.impl;

import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.ConditionalExpr;
import com.github.javaparser.ast.stmt.*;
import it.flaviosimonelli.isw2.metrics.IMetric;

/**
 * Calcola la <b>Complessità Cognitiva</b> (definita da SonarSource).
 * <p>
 * A differenza della ciclomarica (che misura la difficoltà matematica di test),
 * questa metrica misura quanto è difficile per un <b>umano</b> comprendere il flusso.
 * <br>
 * Logica:
 * <ul>
 * <li>Incrementa per ogni struttura di controllo (if, loop, switch).</li>
 * <li>Aggiunge una <b>penalità di nesting</b>: un 'if' dentro un 'for' pesa di più di un 'if' esterno.</li>
 * </ul>
 * </p>
 * <b>Significato per il Bug Prediction:</b>
 * Metodi difficili da leggere ("Arrow Code") inducono il programmatore in errore
 * durante le modifiche di manutenzione.
 */
public class CognitiveComplexityMetric implements IMetric {
    @Override
    public String getName() {
        return "CognitiveComplexity";
    }

    @Override
    public double calculate(MethodDeclaration method) {
        int complexity = 0;

        // Somma costrutti logici
        complexity += method.findAll(IfStmt.class).size();
        complexity += method.findAll(ForStmt.class).size();
        complexity += method.findAll(ForEachStmt.class).size();
        complexity += method.findAll(WhileStmt.class).size();
        complexity += method.findAll(DoStmt.class).size();
        complexity += method.findAll(SwitchStmt.class).size();
        complexity += method.findAll(CatchClause.class).size();
        complexity += method.findAll(ConditionalExpr.class).size();

        // Calcolo penalità nesting
        int maxDepth = calculateNestingDepthRecursive(method, 0);
        int nestingPenalty = Math.max(0, maxDepth - 1);

        return (double) (complexity + nestingPenalty);
    }

    // Copia helper per isolamento
    private int calculateNestingDepthRecursive(Node node, int currentDepth) {
        int maxDepth = currentDepth;
        if (node instanceof IfStmt || node instanceof ForStmt || node instanceof ForEachStmt ||
                node instanceof WhileStmt || node instanceof DoStmt || node instanceof SwitchStmt ||
                node instanceof TryStmt || node instanceof CatchClause) {
            currentDepth++;
        }
        for (Node child : node.getChildNodes()) {
            maxDepth = Math.max(maxDepth, calculateNestingDepthRecursive(child, currentDepth));
        }
        return maxDepth;
    }
}
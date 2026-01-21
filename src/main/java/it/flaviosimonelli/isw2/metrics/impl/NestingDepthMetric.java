package it.flaviosimonelli.isw2.metrics.impl;

import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.stmt.*;
import it.flaviosimonelli.isw2.metrics.IMetric;

/**
 * Calcola la <b>Massima Profondità di Nesting</b> (Annidamento).
 * <p>
 * Misura quanto in profondità si spingono le strutture di controllo.
 * Es: un 'if' dentro un 'for' dentro un 'while' ha profondità 3.
 * </p>
 * <b>Significato per il Bug Prediction:</b>
 * Un nesting profondo eccede la memoria a breve termine del programmatore ("Stack mentale"),
 * rendendo difficile prevedere lo stato delle variabili nel punto più interno.
 * Spesso correla con i "Code Smells".
 */
public class NestingDepthMetric implements IMetric {
    @Override
    public String getName() {
        return "NestingDepth";
    }

    @Override
    public double calculate(MethodDeclaration method) {
        return calculateNestingDepthRecursive(method, 0);
    }

    private int calculateNestingDepthRecursive(Node node, int currentDepth) {
        int maxDepth = currentDepth;

        if (isControlStructure(node)) {
            currentDepth++;
        }

        for (Node child : node.getChildNodes()) {
            int childDepth = calculateNestingDepthRecursive(child, currentDepth);
            if (childDepth > maxDepth) {
                maxDepth = childDepth;
            }
        }
        return maxDepth;
    }

    private boolean isControlStructure(Node node) {
        return node instanceof IfStmt ||
                node instanceof ForStmt ||
                node instanceof ForEachStmt ||
                node instanceof WhileStmt ||
                node instanceof DoStmt ||
                node instanceof SwitchStmt ||
                node instanceof TryStmt ||
                node instanceof CatchClause;
    }
}
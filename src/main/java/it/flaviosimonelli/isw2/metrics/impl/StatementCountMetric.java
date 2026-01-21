package it.flaviosimonelli.isw2.metrics.impl;

import com.github.javaparser.ast.body.MethodDeclaration;
import it.flaviosimonelli.isw2.metrics.IMetric;

/**
 * Calcola il <b>Numero di Statement</b> (istruzioni) all'interno del metodo.
 * <p>
 * A differenza delle LOC (che dipendono dalla formattazione), questa metrica conta
 * le effettive istruzioni logiche (es. assegnazioni, chiamate a metodi, break, return).
 * </p>
 * <b>Significato per il Bug Prediction:</b>
 * Fornisce una stima della "densità" di logica del metodo, epurata da
 * commenti o spazi bianchi.
 */
public class StatementCountMetric implements IMetric {
    @Override
    public String getName() {
        return "NumStatements";
    }

    @Override
    public double calculate(MethodDeclaration method) {
        return method.getBody()
                .map(b -> (double) b.getStatements().size())
                .orElse(0.0);
    }
}
package it.flaviosimonelli.isw2.metrics.impl;

import com.github.javaparser.ast.body.MethodDeclaration;
import it.flaviosimonelli.isw2.metrics.IMetric;

/**
 * Calcola le <b>Lines of Code (LOC)</b> del metodo.
 * <p>
 * Questa metrica misura la dimensione fisica del metodo contando le righe
 * testuali occupate dal corpo del metodo.
 * </p>
 * <b>Significato per il Bug Prediction:</b>
 * C'è una correlazione storica molto forte tra dimensioni e difetti.
 * Metodi più lunghi tendono statistiamente ad avere più bug semplicemente
 * perché contengono più logica e offrono più superficie di errore.
 */
public class LocMetric implements IMetric {
    @Override
    public String getName() {
        return "LOC";
    }

    @Override
    public double calculate(MethodDeclaration method) {
        // Conta le righe del body, se presente
        return method.getBody()
                .map(body -> (double) body.toString().split("\n").length)
                .orElse(0.0);
    }
}
package it.flaviosimonelli.isw2.metrics.impl;

import com.github.javaparser.ast.body.MethodDeclaration;
import it.flaviosimonelli.isw2.metrics.IMetric;

/**
 * Calcola il <b>Numero di Parametri</b> della firma del metodo.
 * <p>
 * Conta quanti argomenti vengono passati in input al metodo.
 * Corrisponde alla regola PMD "ExcessiveParameterList".
 * </p>
 * <b>Significato per il Bug Prediction:</b>
 * Un alto numero di parametri (> 4-5) indica spesso un alto accoppiamento (Coupling)
 * e bassa coesione. Tali metodi sono difficili da testare e da chiamare correttamente,
 * aumentando la probabilità di bug di integrazione.
 */
public class ParameterCountMetric implements IMetric {

    @Override
    public String getName() {
        return "NumParams";
    }

    @Override
    public double calculate(MethodDeclaration method) {
        // method.getParameters() restituisce una NodeList, .size() ci dà il conteggio diretto.
        return (double) method.getParameters().size();
    }
}
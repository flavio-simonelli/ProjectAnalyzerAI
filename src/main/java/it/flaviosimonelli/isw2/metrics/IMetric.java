package it.flaviosimonelli.isw2.metrics;

import com.github.javaparser.ast.body.MethodDeclaration;

public interface IMetric {
    /**
     * Il nome della metrica che apparirà nell'header del CSV.
     * Es: "LOC", "WMC", "Cyclomatic"
     */
    String getName();

    /**
     * Esegue il calcolo sul metodo passato.
     * Restituisce double perché la maggior parte delle metriche sono numeriche.
     */
    double calculate(MethodDeclaration method);
}
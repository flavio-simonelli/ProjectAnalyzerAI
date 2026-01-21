package it.flaviosimonelli.isw2.metrics.impl;

import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.VariableDeclarator;
import it.flaviosimonelli.isw2.metrics.IMetric;

/**
 * Calcola il <b>Numero di Variabili Locali</b> dichiarate nel corpo del metodo.
 * <p>
 * Esclude i parametri del metodo e conta solo le variabili temporanee create
 * per calcoli intermedi.
 * </p>
 * <b>Significato per il Bug Prediction:</b>
 * Un alto numero di variabili locali suggerisce che il metodo sta gestendo
 * troppi stati interni. Più variabili ci sono, più è difficile per il programmatore
 * tracciare mentalmente il valore di ognuna, portando a errori logici.
 */
public class LocalVariableCountMetric implements IMetric {
    @Override
    public String getName() {
        return "NumLocalVars";
    }

    @Override
    public double calculate(MethodDeclaration method) {
        return method.findAll(VariableDeclarator.class).stream()
                .filter(v -> v.getParentNode().isPresent() &&
                        !(v.getParentNode().get() instanceof Parameter))
                .count();
    }
}
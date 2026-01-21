package it.flaviosimonelli.isw2.metrics.impl;

import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.Type;
import it.flaviosimonelli.isw2.metrics.IMetric;

/**
 * Calcola la <b>Complessità del Tipo di Ritorno</b>.
 * <p>
 * È una metrica euristica che assegna un peso alla struttura dati restituita.
 * <br>
 * - Tipi primitivi (int, void) = 1
 * - Array o Generics aggiungono peso ricorsivamente.
 * <br>
 * Esempio: <code>List&lt;String&gt;</code> ha complessità 2 (List + String).
 * </p>
 * <b>Significato per il Bug Prediction:</b>
 * Restituire strutture dati complesse (es. Map di List) aumenta la complessità
 * per il chiamante (Client Code), aumentando il rischio di errori nell'uso del metodo.
 */
public class ReturnTypeComplexityMetric implements IMetric {
    @Override
    public String getName() {
        return "ReturnTypeComplexity";
    }

    @Override
    public double calculate(MethodDeclaration method) {
        return computeTypeComplexity(method.getType());
    }

    private int computeTypeComplexity(Type type) {
        if (type.isPrimitiveType()) return 1;
        if (type.isArrayType()) return 1 + computeTypeComplexity(type.asArrayType().getComponentType());

        if (type.isClassOrInterfaceType()) {
            ClassOrInterfaceType cit = type.asClassOrInterfaceType();
            int complexity = 1; // Base complexity

            if (cit.getTypeArguments().isPresent()) {
                for (Type arg : cit.getTypeArguments().get()) {
                    complexity += computeTypeComplexity(arg);
                }
            }
            return complexity;
        }
        return 1; // Fallback (void, var, unknown)
    }
}